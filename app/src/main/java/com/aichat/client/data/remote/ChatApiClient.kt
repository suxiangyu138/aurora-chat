package com.aichat.client.data.remote

import android.os.Handler
import android.os.Looper
import com.aichat.client.data.settings.ModelConfig
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** 上下文消息(发给 API 的通用格式) */
data class ChatMessage(val role: String, val content: String)

/** 一次问答的完整结果 */
data class ChatResult(val content: String, val reasoning: String? = null)

/** API 错误(带 HTTP 状态码) */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 通用 AI API 客户端:不绑定任何固定模型协议。
 * 按 OpenAI 兼容的 chat/completions 通用 JSON 模板组装请求,适配绝大多数国产/海外大模型 HTTP 接口。
 * 流式:SSE 协议逐字输出;非流式:普通 JSON 一次性返回,双向兼容。
 */
class ChatApiClient {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 按配置自定义超时(解决部分模型接口响应慢的问题) */
    private fun clientWithTimeout(config: ModelConfig): OkHttpClient =
        baseClient.newBuilder()
            .readTimeout(config.timeoutSeconds.coerceIn(10, 600).toLong(), TimeUnit.SECONDS)
            .build()

    // ---------- 通用请求模板 ----------

    /** 组装 OpenAI 兼容请求体:model / messages / temperature / top_p / max_tokens / stream */
    private fun buildRequestBody(
        config: ModelConfig,
        messages: List<ChatMessage>,
        stream: Boolean
    ): String {
        val root = JsonObject()
        root.addProperty("model", config.modelName)
        val msgs = JsonArray()
        messages.forEach { m ->
            val o = JsonObject()
            o.addProperty("role", m.role)
            o.addProperty("content", m.content)
            msgs.add(o)
        }
        root.add("messages", msgs)
        root.addProperty("temperature", config.temperature)
        root.addProperty("top_p", config.topP)
        root.addProperty("max_tokens", config.maxTokens)
        root.addProperty("stream", stream)

        // 自定义请求体模板:占位符替换,适配特殊模型接口
        val template = config.customBody.trim()
        if (template.isNotEmpty()) {
            return template
                .replace("{model}", config.modelName)
                .replace("{messages}", msgs.toString())
                .replace("{temperature}", config.temperature.toString())
                .replace("{top_p}", config.topP.toString())
                .replace("{max_tokens}", config.maxTokens.toString())
                .replace("{stream}", stream.toString())
        }
        return root.toString()
    }

    /** 组装请求:自动携带 Authorization(API Key)与 Content-Type */
    private fun buildRequest(config: ModelConfig, body: String, stream: Boolean): Request {
        val builder = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(body.toRequestBody(jsonMediaType))
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
        }
        return builder.build()
    }

    // ---------- 流式(SSE) ----------

    /**
     * 流式对话:SSE 实时输出,所有回调在主线程执行。
     * onReasoning 输出思考过程(reasoning_content),onComplete 同时带回完整回答与思考。
     * 返回 EventSource,调用方可通过 cancel() 主动终止(页面销毁自动取消)。
     */
    fun streamChat(
        config: ModelConfig,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val request = buildRequest(config, buildRequestBody(config, messages, true), stream = true)
        return EventSources.createFactory(clientWithTimeout(config))
            .newEventSource(request, object : EventSourceListener() {
                private val fullText = StringBuilder()
                private val fullReasoning = StringBuilder()

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val (delta, reasoning) = parseStreamEvent(data)
                    mainHandler.post {
                        if (!delta.isNullOrEmpty()) {
                            fullText.append(delta)
                            onDelta(delta)
                        }
                        if (!reasoning.isNullOrEmpty()) {
                            fullReasoning.append(reasoning)
                            onReasoning(reasoning)
                        }
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    mainHandler.post { onComplete(fullText.toString(), fullReasoning.toString()) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val message = when {
                        response != null -> "接口错误 HTTP ${response.code}: ${response.message}"
                        t != null -> "网络异常: ${t.message ?: t.javaClass.simpleName}"
                        else -> "未知错误"
                    }
                    mainHandler.post {
                        // 已有部分内容输出时视为正常结束,否则报错
                        if (fullText.isEmpty() && fullReasoning.isEmpty()) onError(message)
                        else onComplete(fullText.toString(), fullReasoning.toString())
                    }
                }
            })
    }

    /** 解析 SSE 事件数据:返回(正文增量, 思考增量);兼容 [DONE]、多行 data、各厂商差异 */
    private fun parseStreamEvent(data: String): Pair<String?, String?> {
        if (data.isBlank()) return null to null
        val text = StringBuilder()
        val reasoning = StringBuilder()
        for (line in data.lines()) {
            val trimmed = line.trim().removePrefix("data:").trim()
            if (trimmed.isEmpty() || trimmed == "[DONE]") continue
            val root = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull() ?: continue
            val choice = runCatching { root.getAsJsonArray("choices")?.get(0)?.asJsonObject }.getOrNull()
            val delta = choice?.getAsJsonObject("delta")
            val message = choice?.getAsJsonObject("message")
            // 正文:delta.content / message.content
            delta?.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.let { text.append(it) }
            message?.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.let { text.append(it) }
            // 思考:delta.reasoning_content / message.reasoning_content(DeepSeek-R1、GLM 等)
            delta?.get("reasoning_content")?.takeIf { it.isJsonPrimitive }?.asString?.let { reasoning.append(it) }
            message?.get("reasoning_content")?.takeIf { it.isJsonPrimitive }?.asString?.let { reasoning.append(it) }
        }
        return text.toString().ifEmpty { null } to reasoning.toString().ifEmpty { null }
    }

    // ---------- 非流式 ----------

    /** 非流式单次问答(返回完整回答与思考过程) */
    suspend fun chatOnce(config: ModelConfig, messages: List<ChatMessage>): Result<ChatResult> =
        suspendCancellableCoroutine { cont ->
            val request = buildRequest(config, buildRequestBody(config, messages, false), stream = false)
            clientWithTimeout(config).newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string()
                    response.close()
                    val result = when {
                        response.isSuccessful && body != null -> {
                            val content = parseFullContent(body)
                            if (content != null) Result.success(
                                ChatResult(content, parseFullReasoning(body))
                            )
                            else Result.failure(ApiException(response.code, "响应格式无法解析"))
                        }
                        else -> {
                            val err = parseErrorBody(body)
                            Result.failure(
                                ApiException(response.code, err ?: "接口返回错误 HTTP ${response.code}")
                            )
                        }
                    }
                    if (cont.isActive) cont.resume(result)
                }
            })
        }

    // ---------- 解析工具 ----------

    /** 非流式完整回复:choices[0].message.content */
    private fun parseFullContent(json: String): String? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("content")?.asString
    }.getOrNull()

    /** 非流式思考过程:choices[0].message.reasoning_content */
    private fun parseFullReasoning(json: String): String? = runCatching {
        val root = JsonParser.parseString(json).asJsonObject
        root.getAsJsonArray("choices")?.get(0)?.asJsonObject
            ?.getAsJsonObject("message")?.get("reasoning_content")?.asString
    }.getOrNull()

    /** 错误信息:error.message(密钥错误、限流等统一提示) */
    private fun parseErrorBody(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            JsonParser.parseString(body).asJsonObject
                .getAsJsonObject("error")?.get("message")?.asString
        }.getOrNull()
    }
}
