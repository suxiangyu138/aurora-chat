package com.aichat.client.data.remote.provider

import com.aichat.client.data.settings.ModelConfig
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/** 测试连接的结论分类:UI 按类给不同提示,不再只有「失败」两个字 */
enum class TestKind {
    OK, AUTH, URL_NOT_FOUND, MODEL_NOT_FOUND, RATE_LIMIT,
    SERVER, TIMEOUT, NETWORK, INVALID, UNKNOWN
}

data class TestOutcome(
    val ok: Boolean,
    val kind: TestKind,
    val message: String,
    val models: List<ModelInfo> = emptyList(),
    val latencyMs: Long = 0
)

/** 账户余额:金额 + 币种;note 用于说明额度口径 */
data class Balance(
    val amount: Double,
    val currency: String,
    val note: String = ""
)

data class ModelsFetch(
    val models: List<ModelInfo>,
    /** 实际命中的接口地址,便于用户判断 /v1 是否写对 */
    val endpoint: String
)

/**
 * 模型列表拉取与连接测试。
 *
 * 兼容三类入口:厂商官方 OpenAI 兼容接口、聚合网关/中转站、用户自建的任意兼容服务。
 * 中转站常见两种路径(/models 与 /v1/models),这里依次探测,不做硬编码假设。
 */
class ProviderApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ---------- 模型列表 ----------

    suspend fun fetchModels(config: ModelConfig): Result<ModelsFetch> = withContext(Dispatchers.IO) {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return@withContext Result.failure(IllegalArgumentException("接口 URL 为空"))
        if (config.apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("API Key 为空"))

        val errors = mutableListOf<String>()
        for (candidate in modelEndpoints(base)) {
            val request = Request.Builder()
                .url(candidate)
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", "application/json")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> {
                            val models = parseModels(body, config)
                            if (models.isEmpty()) {
                                errors += "$candidate 返回成功但无法解析出模型"
                            } else {
                                return@withContext Result.success(ModelsFetch(models, candidate))
                            }
                        }
                        resp.code == 401 || resp.code == 403 -> {
                            errors += "$candidate 鉴权失败 HTTP ${resp.code}: ${errorMessage(body)}"
                        }
                        resp.code == 404 -> errors += "$candidate 不存在"
                        else -> errors += "$candidate HTTP ${resp.code}: ${errorMessage(body)}"
                    }
                }
            } catch (e: SocketTimeoutException) {
                errors += "$candidate 超时"
            } catch (e: IOException) {
                errors += "$candidate 网络异常: ${e.message}"
            }
        }
        Result.failure(IOException(errors.joinToString("\n").ifBlank { "未找到可用的模型列表接口" }))
    }

    /** 依次尝试 base/models、base/v1/models(以及去掉 /v1 的 base/models) */
    private fun modelEndpoints(base: String): List<String> {
        val list = linkedSetOf<String>()
        list += "$base/models"
        if (!base.endsWith("/v1")) list += "$base/v1/models"
        if (base.endsWith("/v1")) list += "${base.removeSuffix("/v1")}/models"
        return list.toList()
    }

    private fun parseModels(body: String, config: ModelConfig): List<ModelInfo> {
        val root = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
        val array = when {
            root.isJsonArray -> root.asJsonArray
            root.isJsonObject -> {
                val obj = root.asJsonObject
                obj.get("data")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: obj.get("models")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: obj.get("result")?.takeIf { it.isJsonArray }?.asJsonArray
            }
            else -> null
        } ?: return emptyList()

        val provider = ProviderId.fromKey(config.providerId).let {
            if (it == ProviderId.CUSTOM) ProviderCatalog.detectProvider(config.baseUrl) else it
        }
        return array.mapNotNull { it.asObjectOrNull()?.let { o -> modelOf(o, provider) } }
            .filter { it.id.isNotBlank() }
            .distinctBy { it.id }
    }

    private fun modelOf(o: JsonObject, provider: ProviderId): ModelInfo {
        val id = o.string("id") ?: o.string("model") ?: o.string("name") ?: return ModelInfo("")
        val context = o.int("context_window") ?: o.int("context_length")
        ?: o.int("max_context_length") ?: o.int("contextWindow")
        val maxOut = o.int("max_output_tokens") ?: o.int("max_completion_tokens")
        @Suppress("UNCHECKED_CAST")
        val modalities = (o.get("input_modalities") as? JsonArray)
            ?.mapNotNull { (it as? JsonElement)?.asStringOrNull() }?.toSet().orEmpty()
        val vision = modalities.contains("image") ||
            o.string("type")?.contains("vision", true) == true
        val caps = ProviderCatalog.capabilitiesOf(provider)
        return ModelInfo(
            id = id,
            displayName = o.string("name") ?: id,
            provider = provider,
            contextWindow = context,
            maxOutputTokens = maxOut,
            supportsVision = vision || caps.vision,
            supportsReasoning = caps.reasoning,
            supportsNativeSearch = caps.nativeSearch,
            source = "fetched"
        )
    }

    // ---------- 账户余额 ----------

    /** 当前厂商是否提供「用 API Key 查余额」的官方接口 */
    fun supportsBalance(providerId: ProviderId): Boolean = when (providerId) {
        ProviderId.DEEPSEEK, ProviderId.MOONSHOT, ProviderId.OPENROUTER -> true
        else -> false
    }

    /**
     * 查余额。各家只有这三家有公开的 key 级接口:
     * DeepSeek /user/balance、Kimi /users/me/balance、OpenRouter /credits。
     * 其余厂商的余额在云平台 AK/SK 的 OpenAPI 里,普通 API Key 查不到,UI 不显示入口。
     */
    suspend fun fetchBalance(config: ModelConfig): Result<Balance> = withContext(Dispatchers.IO) {
        val provider = ProviderId.fromKey(config.providerId)
        if (!supportsBalance(provider)) {
            return@withContext Result.failure(IllegalStateException("该厂商没有 API Key 级余额接口"))
        }
        val base = config.baseUrl.trim().trimEnd('/')
        val url = when (provider) {
            ProviderId.DEEPSEEK -> "$base/user/balance"
            ProviderId.MOONSHOT -> "$base/users/me/balance"
            ProviderId.OPENROUTER -> "$base/credits"
            else -> return@withContext Result.failure(IllegalStateException("不支持"))
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val hint = when (resp.code) {
                        401, 403 -> "鉴权失败或无权限(OpenRouter 余额需要管理密钥)"
                        404 -> "该接口不存在,可能不是官方地址"
                        else -> "HTTP ${resp.code}"
                    }
                    return@withContext Result.failure(IOException(hint))
                }
                val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
                    ?: return@withContext Result.failure(IOException("返回格式无法解析"))
                val balance = when (provider) {
                    ProviderId.DEEPSEEK -> {
                        val info = root.getAsJsonArray("balance_infos")
                            ?.firstOrNull()?.asJsonObject
                        val total = info?.string("total_balance")?.toDoubleOrNull()
                        Balance(
                            amount = total ?: 0.0,
                            currency = info?.string("currency") ?: "CNY",
                            note = if (runCatching { root.get("is_available")?.asBoolean }.getOrNull() == false) "余额不足" else ""
                        )
                    }
                    ProviderId.MOONSHOT -> {
                        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                        val amount = data?.double("available_balance")
                            ?: root.double("available_balance") ?: 0.0
                        Balance(amount, "CNY")
                    }
                    ProviderId.OPENROUTER -> {
                        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                        val total = data?.double("total_credits") ?: 0.0
                        val used = data?.double("total_usage") ?: 0.0
                        Balance((total - used).coerceAtLeast(0.0), "USD")
                    }
                    else -> Balance(0.0, "CNY")
                }
                Result.success(balance)
            }
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("查询超时"))
        } catch (e: IOException) {
            Result.failure(IOException(e.message ?: "网络异常"))
        }
    }

    // ---------- 连接测试 ----------

    suspend fun testConnection(config: ModelConfig): TestOutcome = withContext(Dispatchers.IO) {
        if (!config.isComplete) {
            return@withContext TestOutcome(false, TestKind.INVALID, "请先填写接口 URL、API Key 和模型名称")
        }
        val start = System.currentTimeMillis()

        // 第一步:先取模型列表(不消耗 token);接口不支持时再退回最小对话请求
        var models: List<ModelInfo> = emptyList()
        val listResult = fetchModels(config)
        listResult.onSuccess { fetch ->
            models = fetch.models
            val hit = fetch.models.any { it.id == config.modelName }
            val latency = System.currentTimeMillis() - start
            val note = if (hit) "该模型在列表中"
            else "注意:当前模型名不在返回列表里,请确认拼写"
            return@withContext TestOutcome(
                ok = true,
                kind = TestKind.OK,
                message = "连接成功,可获取 ${fetch.models.size} 个模型($note)\n接口:$fetch.endpoint",
                models = fetch.models,
                latencyMs = latency
            )
        }

        // 第二步:最小对话请求,验证模型名与地址是否真的可用
        val request = Request.Builder()
            .url(config.baseUrl.trim().trimEnd('/') + "/chat/completions")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(
                """{"model":"${config.modelName}","messages":[{"role":"user","content":"ping"}],"max_tokens":1,"stream":false}"""
                    .toRequestBody(jsonMediaType)
            )
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val latency = System.currentTimeMillis() - start
                if (resp.isSuccessful) {
                    return@withContext TestOutcome(
                        true, TestKind.OK,
                        "连接成功,模型可用\n延迟:${latency}ms\n(该地址不支持模型列表,已改用最小请求验证)",
                        models, latency
                    )
                }
                val (kind, message) = classify(resp.code, body)
                return@withContext TestOutcome(false, kind, "$message\nHTTP ${resp.code}", models, latency)
            }
        } catch (e: SocketTimeoutException) {
            TestOutcome(false, TestKind.TIMEOUT, "连接超时:服务端 30 秒内没有响应", models)
        } catch (e: IOException) {
            TestOutcome(false, TestKind.NETWORK, "网络异常: ${e.message}", models)
        } catch (e: Exception) {
            TestOutcome(false, TestKind.UNKNOWN, "未知错误: ${e.message}", models)
        }
    }

    private fun classify(code: Int, body: String): Pair<TestKind, String> {
        val detail = errorMessage(body)
        return when (code) {
            401, 403 -> TestKind.AUTH to "鉴权失败:$detail"
            404 -> TestKind.URL_NOT_FOUND to "接口地址不存在,检查 Base URL 是否需要 /v1 或路径写错:$detail"
            429 -> TestKind.RATE_LIMIT to "请求过于频繁或额度不足:$detail"
            400 -> {
                val lower = detail.lowercase()
                if (lower.contains("model") && (
                        lower.contains("not exist") || lower.contains("not found") ||
                            lower.contains("no such") || lower.contains("invalid") ||
                            lower.contains("unsupported")
                        )
                ) {
                    TestKind.MODEL_NOT_FOUND to "模型不存在或当前 Key 无权限:$detail"
                } else {
                    TestKind.INVALID to "请求被拒绝:$detail"
                }
            }
            in 500..599 -> TestKind.SERVER to "服务端错误:$detail"
            else -> TestKind.UNKNOWN to "请求失败:$detail"
        }
    }

    private fun errorMessage(body: String): String {
        if (body.isBlank()) return "无返回内容"
        return runCatching {
            val root = JsonParser.parseString(body)
            if (root.isJsonObject) {
                val o = root.asJsonObject
                o.getAsJsonObject("error")?.string("message")
                    ?: o.string("message") ?: o.string("msg")
            } else null
        }.getOrNull()?.take(200) ?: body.take(200)
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asString }.getOrNull() }

    private fun JsonObject.double(key: String): Double? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asDouble }.getOrNull() }

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull }?.let { runCatching { it.asInt }.getOrNull() }

    private fun JsonElement.asStringOrNull(): String? =
        runCatching { asString }.getOrNull()

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null
}
