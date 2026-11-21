package com.aichat.client.data.remote

import android.os.Handler
import android.os.Looper
import com.aichat.client.data.remote.provider.EndpointResolver
import com.aichat.client.data.remote.provider.ProviderCatalog
import com.aichat.client.data.remote.provider.ProviderId
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

/** 上下文消息(发给 API 的通用格式;image 非空时为多模态消息) */
data class ChatMessage(
    val role: String,
    val content: String,
    /** 本轮携带的图片(多图);历史轮次不带,避免每轮重复计费 */
    val images: List<ChatImage> = emptyList(),
    /** 历史轮次的思考内容:带 tools 的请求必须回传,否则 DeepSeek 会 400 */
    val reasoning: String? = null
) {
    val hasImage: Boolean get() = images.isNotEmpty()
}

/** 待发送的图片(base64 编码) */
data class ChatImage(val base64: String, val mime: String)

/** 一次请求的 token 用量(各家字段名不同,统一到这里) */
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val cachedTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val totalTokens: Int = 0
) {
    val isEmpty: Boolean get() = promptTokens == 0 && completionTokens == 0 && totalTokens == 0
}

/** 一次问答的完整结果 */
data class ChatResult(
    val content: String,
    val reasoning: String? = null,
    val usage: TokenUsage? = null
)

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
    private fun clientWithTimeout(config: ModelConfig, streaming: Boolean = false): OkHttpClient {
        val timeout = config.timeoutSeconds.coerceIn(10, 600)
        // 流式读超时下限 300 秒:推理模型思考停顿久,短超时会掐断流式输出
        val readTimeout = if (streaming) timeout.coerceAtLeast(300) else timeout
        return baseClient.newBuilder()
            .readTimeout(readTimeout.toLong(), TimeUnit.SECONDS)
            .build()
    }

    // ---------- 通用请求模板 ----------

    /** 组装 OpenAI 兼容请求体:model / messages / temperature / top_p / max_tokens / stream */
    private fun buildRequestBody(
        config: ModelConfig,
        messages: List<ChatMessage>,
        stream: Boolean
    ): String {
        val root = JsonObject()
        root.addProperty("model", config.modelName)
        val provider = ProviderId.fromKey(config.providerId)
        val caps = ProviderCatalog.capabilitiesOf(provider)
        val hasServerTools = config.customBody.isBlank() &&
            config.webSearchEnabled && caps.nativeSearch
        val msgs = JsonArray()
        messages.forEach { m ->
            val o = JsonObject()
            o.addProperty("role", m.role)
            if (m.hasImage) {
                // 多模态:OpenAI 风格 content 数组(text + 多张 image_url)
                val content = JsonArray()
                val textPart = JsonObject()
                textPart.addProperty("type", "text")
                textPart.addProperty("text", m.content)
                content.add(textPart)
                m.images.forEach { img ->
                    val imagePart = JsonObject()
                    imagePart.addProperty("type", "image_url")
                    val imageUrl = JsonObject()
                    imageUrl.addProperty("url", "data:${img.mime};base64,${img.base64}")
                    imagePart.add("image_url", imageUrl)
                    content.add(imagePart)
                }
                o.add("content", content)
            } else {
                o.addProperty("content", m.content)
            }
            if (m.role == "assistant") {
                // DeepSeek 规定:声明了服务端工具后,历史 assistant 必须回传
                // reasoning_content,缺失会直接 400;空串也要显式带上。
                if (provider == ProviderId.DEEPSEEK && hasServerTools) {
                    o.addProperty("reasoning_content", m.reasoning ?: "")
                } else if (!m.reasoning.isNullOrBlank()) {
                    o.addProperty("reasoning_content", m.reasoning)
                }
            }
            msgs.add(o)
        }
        root.add("messages", msgs)
        root.addProperty("temperature", config.temperature)
        root.addProperty("top_p", config.topP)
        root.addProperty("max_tokens", config.maxTokens)
        root.addProperty("stream", stream)

        // 流式用量:OpenAI 兼容厂商需显式开启,DeepSeek 默认带 usage
        if (stream) {
            val provider = ProviderId.fromKey(config.providerId)
            if (provider in setOf(
                    ProviderId.QWEN, ProviderId.ZHIPU, ProviderId.MOONSHOT,
                    ProviderId.MINIMAX, ProviderId.MIMO
                )
            ) {
                root.add(
                    "stream_options",
                    JsonObject().apply { addProperty("include_usage", true) }
                )
            }
        }

        applyProviderExtras(root, config)

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

    /**
     * 厂商差异参数:
     * 思考开关与联网搜索各家用不同字段,这里按厂商标识下发;自定义请求体模板存在时不追加,
     * 避免模板与工具字段冲突导致 400。
     */
    private fun applyProviderExtras(root: JsonObject, config: ModelConfig) {
        if (config.customBody.isNotBlank()) return
        val provider = ProviderId.fromKey(config.providerId)
        val caps = ProviderCatalog.capabilitiesOf(provider)
        val thinking = JsonObject().apply {
            addProperty("type", if (config.thinkingEnabled) "enabled" else "disabled")
        }
        when (provider) {
            ProviderId.DEEPSEEK -> {
                root.add("thinking", thinking)
                if (config.webSearchEnabled && caps.nativeSearch) {
                    root.add("tools", JsonArray().apply {
                        add(JsonObject().apply { addProperty("type", "deepseek_web_search") })
                        add(JsonObject().apply { addProperty("type", "deepseek_read_url") })
                    })
                }
            }
            ProviderId.QWEN -> {
                root.addProperty("enable_thinking", config.thinkingEnabled)
                if (config.webSearchEnabled) root.addProperty("enable_search", true)
            }
            ProviderId.ZHIPU -> {
                root.add("thinking", thinking)
                if (config.webSearchEnabled) {
                    root.add("tools", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "web_search")
                            add("web_search", JsonObject().apply {
                                addProperty("enable", true)
                                addProperty("search_engine", "search_std")
                            })
                        })
                    })
                }
            }
            else -> Unit
        }
    }

    /** 组装请求:自动携带 Authorization(API Key)与 Content-Type */
    private fun buildRequest(config: ModelConfig, body: String, stream: Boolean): Request {
        val builder = Request.Builder()
            .url(EndpointResolver.chatUrl(config.baseUrl))
            .header("Content-Type", "application/json")
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .post(body.toRequestBody(jsonMediaType))
        if (config.apiKey.isNotBlank()) {
            builder.header("Authorization", "Bearer ${config.apiKey}")
            builder.header("User-Agent", EndpointResolver.BROWSER_UA)
        }
        return builder.build()
    }

    // ---------- 流式(SSE) ----------

    /**
     * 流式对话:SSE 实时输出,所有回调在主线程执行。
     * onReasoning 输出思考过程(reasoning_content),onComplete 同时带回完整回答与思考。
     * onInterrupted:流式进行中被中断(网络抖动/超时),已生成的部分内容带回,由调用方决定处理。
     * 返回 EventSource,调用方可通过 cancel() 主动终止(页面销毁自动取消)。
     */
    fun streamChat(
        config: ModelConfig,
        messages: List<ChatMessage>,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onUsage: (TokenUsage) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onInterrupted: (fullText: String, reasoning: String, message: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val request = buildRequest(config, buildRequestBody(config, messages, true), stream = true)
        return EventSources.createFactory(clientWithTimeout(config, streaming = true))
            .newEventSource(request, object : EventSourceListener() {
                private val fullText = StringBuilder()
                private val fullReasoning = StringBuilder()

                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val (delta, reasoning, usage) = parseStreamEvent(data)
                    mainHandler.post {
                        if (!delta.isNullOrEmpty()) {
                            fullText.append(delta)
                            onDelta(delta)
                        }
                        if (!reasoning.isNullOrEmpty()) {
                            fullReasoning.append(reasoning)
                            onReasoning(reasoning)
                        }
                        if (usage != null && !usage.isEmpty) onUsage(usage)
                    }
                }

                override fun onClosed(eventSource: EventSource) {
                    mainHandler.post { onComplete(fullText.toString(), fullReasoning.toString()) }
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    // 服务端 4xx 的具体原因在响应体里(如 model not exist / context length),
                    // 必须读出来,否则只剩一句 Bad Request 无法定位
                    val bodyText = runCatching { response?.body?.string() }.getOrNull()
                    val detail = parseErrorBody(bodyText)
                    val message = when {
                        response != null -> "接口错误 HTTP ${response.code}: " +
                            (detail ?: response.message)
                        t != null -> "网络异常: ${t.message ?: t.javaClass.simpleName}"
                        else -> "连接中断"
                    }
                    mainHandler.post {
                        // 有部分内容:不算正常结束,明确上报中断;无内容才报错
                        if (fullText.isEmpty() && fullReasoning.isEmpty()) onError(message)
                        else onInterrupted(fullText.toString(), fullReasoning.toString(), message)
                    }
                }
            })
    }

    /** 解析 SSE 事件数据:返回(正文增量, 思考增量, 用量);兼容 [DONE]、多行 data、各厂商差异 */
    private fun parseStreamEvent(data: String): Triple<String?, String?, TokenUsage?> {
        if (data.isBlank()) return Triple(null, null, null)
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var usage: TokenUsage? = null
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
            runCatching { root.getAsJsonObject("usage") }.getOrNull()?.let { usage = parseUsage(it) }
        }
        return Triple(
            text.toString().ifEmpty { null },
            reasoning.toString().ifEmpty { null },
            usage
        )
    }

    /** 统一解析 usage:兼容 OpenAI / DeepSeek 缓存字段 / 思考 token */
    private fun parseUsage(o: JsonObject): TokenUsage? {
        fun int(vararg keys: String): Int =
            keys.firstNotNullOfOrNull { k ->
                o.get(k)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asInt }.getOrNull() }
            } ?: 0
        val prompt = int("prompt_tokens", "input_tokens")
        val completion = int("completion_tokens", "output_tokens")
        if (prompt == 0 && completion == 0) return null
        val cached = runCatching {
            o.getAsJsonObject("prompt_tokens_details")?.get("cached_tokens")
                ?.takeIf { it.isJsonPrimitive }?.asInt
        }.getOrNull()
            ?: int("prompt_cache_hit_tokens", "cached_tokens")
        val reasoning = runCatching {
            o.getAsJsonObject("completion_tokens_details")?.get("reasoning_tokens")
                ?.takeIf { it.isJsonPrimitive }?.asInt
        }.getOrNull() ?: 0
        return TokenUsage(
            promptTokens = prompt,
            completionTokens = completion,
            cachedTokens = cached,
            reasoningTokens = reasoning,
            totalTokens = int("total_tokens").takeIf { it > 0 } ?: (prompt + completion)
        )
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
                                ChatResult(content, parseFullReasoning(body), parseFullUsage(body))
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

    /** 非流式用量:usage */
    private fun parseFullUsage(json: String): TokenUsage? = runCatching {
        parseUsage(JsonParser.parseString(json).asJsonObject.getAsJsonObject("usage") ?: return null)
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
