package com.aichat.client.data.remote.provider

/**
 * 厂商标识与能力矩阵。
 *
 * 只保留本机已验证过 API Key 的官方厂商;中转站、聚合网关统一走「自定义」,
 * 由用户自己填 Base URL 与 Key,再从接口拉取模型。
 */
enum class ProviderId(val key: String, val label: String, val defaultBaseUrl: String) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com"),
    QWEN("qwen", "阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ZHIPU("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
    MOONSHOT("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1"),
    MINIMAX("minimax", "MiniMax", "https://api.minimaxi.com/v1"),
    MIMO("mimo", "小米 MiMo", "https://api.xiaomimimo.com/v1"),
    CUSTOM("custom", "自定义 / 中转站", "");

    companion object {
        fun fromKey(key: String?): ProviderId =
            entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** 厂商 / 模型能力矩阵:UI 据此灰显入口,不做「点了才报错」 */
data class Capabilities(
    val modelsList: Boolean = false,
    val streamUsage: Boolean = false,
    val reasoning: Boolean = false,
    val vision: Boolean = false,
    val fileInput: Boolean = false,
    val nativeSearch: Boolean = false,
    val citations: Boolean = false
)

/** 一个可用模型及其限制 */
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val provider: ProviderId = ProviderId.CUSTOM,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsNativeSearch: Boolean = false,
    val source: String = "builtin"
) {
    val subtitle: String
        get() = buildList {
            if (contextWindow != null) add("上下文 ${formatTokens(contextWindow)}")
            if (maxOutputTokens != null) add("输出 ${formatTokens(maxOutputTokens)}")
            if (supportsVision) add("识图")
            if (supportsReasoning) add("思考")
            if (supportsNativeSearch) add("联网")
        }.joinToString(" · ")
}

internal fun formatTokens(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000.0f}".trimEnd('0').trimEnd('.') + "M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

data class ProviderProfile(
    val id: ProviderId,
    val baseUrl: String,
    val capabilities: Capabilities
)

object ProviderCatalog {

    private val deepSeekModels = listOf(
        ModelInfo(
            id = "deepseek-flash", displayName = "DeepSeek-V4.1-Flash",
            provider = ProviderId.DEEPSEEK,
            contextWindow = 1_048_576, maxOutputTokens = 393_216,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true
        ),
        ModelInfo(
            id = "deepseek-v4-pro", displayName = "DeepSeek-V4-Pro",
            provider = ProviderId.DEEPSEEK,
            contextWindow = 1_048_576, maxOutputTokens = 393_216,
            supportsReasoning = true, supportsNativeSearch = true
        )
    )

    private val qwenModels = listOf(
        ModelInfo("qwen4-max", "Qwen4 Max", ProviderId.QWEN, 1_000_000, 131_072, supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("qwen3.8-max", "Qwen3.8 Max", ProviderId.QWEN, 1_000_000, 131_072, supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("qwen3.8-flash", "Qwen3.8 Flash", ProviderId.QWEN, 1_000_000, 131_072, supportsVision = true, supportsReasoning = true, supportsNativeSearch = true)
    )

    private val zhipuModels = listOf(
        ModelInfo("glm-5.3", "GLM-5.3", ProviderId.ZHIPU, 1_048_576, 131_072, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-5.3-flash", "GLM-5.3-Flash", ProviderId.ZHIPU, 1_048_576, 131_072, supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-5.2", "GLM-5.2", ProviderId.ZHIPU, 1_048_576, 131_072, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-4.7", "GLM-4.7", ProviderId.ZHIPU, 204_800, 131_072, supportsReasoning = true, supportsNativeSearch = true)
    )

    private val kimiModels = listOf(
        ModelInfo("kimi-k3.1-pro", "Kimi K3.1 Pro", ProviderId.MOONSHOT, 1_048_576, 131_072, supportsVision = true, supportsReasoning = true),
        ModelInfo("kimi-k3", "Kimi K3", ProviderId.MOONSHOT, 1_048_576, 131_072, supportsVision = true, supportsReasoning = true),
        ModelInfo("kimi-k2.6", "Kimi K2.6", ProviderId.MOONSHOT, 262_144, 131_072, supportsVision = true, supportsReasoning = true)
    )

    private val minimaxModels = listOf(
        ModelInfo("MiniMax-M3.1-Flash", "MiniMax M3.1 Flash", ProviderId.MINIMAX),
        ModelInfo("MiniMax-M3", "MiniMax M3", ProviderId.MINIMAX)
    )

    private val mimoModels = listOf(
        ModelInfo("mimo-v2.6-flash", "MiMo v2.6 Flash", ProviderId.MIMO),
        ModelInfo("mimo-v2.6-pro", "MiMo v2.6 Pro", ProviderId.MIMO)
    )

    private val verifiedModels: Map<ProviderId, List<ModelInfo>> = mapOf(
        ProviderId.DEEPSEEK to deepSeekModels,
        ProviderId.QWEN to qwenModels,
        ProviderId.ZHIPU to zhipuModels,
        ProviderId.MOONSHOT to kimiModels,
        ProviderId.MINIMAX to minimaxModels,
        ProviderId.MIMO to mimoModels
    )

    val profiles: Map<ProviderId, ProviderProfile> = mapOf(
        ProviderId.DEEPSEEK to ProviderProfile(
            ProviderId.DEEPSEEK, "https://api.deepseek.com",
            Capabilities(modelsList = true, streamUsage = true, reasoning = true, vision = true, fileInput = true, nativeSearch = true, citations = true)
        ),
        ProviderId.QWEN to ProviderProfile(
            ProviderId.QWEN, "https://dashscope.aliyuncs.com/compatible-mode/v1",
            Capabilities(modelsList = true, streamUsage = true, reasoning = true, vision = true, nativeSearch = true)
        ),
        ProviderId.ZHIPU to ProviderProfile(
            ProviderId.ZHIPU, "https://open.bigmodel.cn/api/paas/v4",
            Capabilities(modelsList = true, streamUsage = true, reasoning = true, vision = true, nativeSearch = true)
        ),
        ProviderId.MOONSHOT to ProviderProfile(
            ProviderId.MOONSHOT, "https://api.moonshot.cn/v1",
            Capabilities(modelsList = true, streamUsage = true, reasoning = true, vision = true)
        ),
        ProviderId.MINIMAX to ProviderProfile(
            ProviderId.MINIMAX, "https://api.minimaxi.com/v1",
            Capabilities(modelsList = true, streamUsage = true)
        ),
        ProviderId.MIMO to ProviderProfile(
            ProviderId.MIMO, "https://api.xiaomimimo.com/v1",
            Capabilities(modelsList = true, streamUsage = true)
        ),
        ProviderId.CUSTOM to ProviderProfile(ProviderId.CUSTOM, "", Capabilities())
    )

    fun capabilitiesOf(providerId: ProviderId): Capabilities =
        profiles[providerId]?.capabilities ?: Capabilities()

    fun findModel(providerId: ProviderId, modelId: String): ModelInfo? =
        verifiedModels[providerId].orEmpty().firstOrNull { it.id == modelId }

    fun builtinModels(providerId: ProviderId): List<ModelInfo> =
        verifiedModels[providerId].orEmpty()

    fun detectProvider(baseUrl: String): ProviderId {
        val url = baseUrl.trim().lowercase()
        if (url.isBlank()) return ProviderId.CUSTOM
        return ProviderId.entries.firstOrNull { p ->
            p != ProviderId.CUSTOM && p.defaultBaseUrl.isNotBlank() &&
                url.contains(hostOf(p.defaultBaseUrl))
        } ?: ProviderId.CUSTOM
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore("/")
}
