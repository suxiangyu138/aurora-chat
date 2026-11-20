package com.aichat.client.data.remote.provider

/**
 * 厂商标识与能力矩阵。
 *
 * 说明:内置模型清单只收录已联网核实的条目,未核实的厂商仅保留接口地址,
 * 模型由用户「从接口拉取」获得或手动填写。核实来源见项目二期方案文档。
 */
enum class ProviderId(val key: String, val label: String, val defaultBaseUrl: String) {
    DEEPSEEK("deepseek", "DeepSeek", "https://api.deepseek.com"),
    QWEN("qwen", "阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1"),
    ZHIPU("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4"),
    MOONSHOT("moonshot", "Moonshot Kimi", "https://api.moonshot.cn/v1"),
    VOLCENGINE("volcengine", "豆包 / 火山方舟", "https://ark.cn-beijing.volces.com/api/v3"),
    QIANFAN("qianfan", "百度文心一言", "https://qianfan.baidubce.com/v2"),
    SPARK("spark", "讯飞星火", "https://spark-api-open.xf-yun.com/v1"),
    BAICHUAN("baichuan", "百川智能", "https://api.baichuan-ai.com/v1"),
    OPENAI("openai", "OpenAI", "https://api.openai.com/v1"),
    OPENROUTER("openrouter", "OpenRouter", "https://openrouter.ai/api/v1"),
    CUSTOM("custom", "自定义接口", "");

    companion object {
        fun fromKey(key: String?): ProviderId =
            entries.firstOrNull { it.key == key } ?: CUSTOM
    }
}

/** 厂商 / 模型能力矩阵:UI 据此灰显入口,不做「点了才报错」 */
data class Capabilities(
    /** 是否提供 GET /models 模型列表 */
    val modelsList: Boolean = false,
    /** 流式响应是否返回 usage */
    val streamUsage: Boolean = false,
    /** 是否支持思考模式参数 */
    val reasoning: Boolean = false,
    /** 是否支持图片输入 */
    val vision: Boolean = false,
    /** 是否支持文件上传解析 */
    val fileInput: Boolean = false,
    /** 是否有服务端原生联网搜索工具 */
    val nativeSearch: Boolean = false,
    /** 回答是否带引用(annotations) */
    val citations: Boolean = false
)

/** 一个可用模型及其限制;来源分内置核实、接口拉取、远程清单 */
data class ModelInfo(
    val id: String,
    val displayName: String = id,
    val provider: ProviderId = ProviderId.CUSTOM,
    /** 上下文窗口(token,输入+输出) */
    val contextWindow: Int? = null,
    /** 单次输出上限(token) */
    val maxOutputTokens: Int? = null,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsNativeSearch: Boolean = false,
    /** 数据来源:builtin / fetched / remote */
    val source: String = "builtin"
) {
    /** 展示用:模型名与上下文规模 */
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

/** 厂商预设:接口地址 + 能力矩阵 */
data class ProviderProfile(
    val id: ProviderId,
    val baseUrl: String,
    val capabilities: Capabilities
)

object ProviderCatalog {

    /**
     * 已联网核实的模型清单(2026-11-20 核对 DeepSeek 官方文档)。
     * 旧模型名 deepseek-chat / deepseek-reasoner 已弃用,不收录。
     */
    private val deepSeekModels = listOf(
        ModelInfo(
            id = "deepseek-flash",
            displayName = "DeepSeek-V4.1-Flash",
            provider = ProviderId.DEEPSEEK,
            contextWindow = 1_048_576,
            maxOutputTokens = 393_216,
            supportsVision = true,
            supportsReasoning = true,
            supportsNativeSearch = true
        ),
        ModelInfo(
            id = "deepseek-v4-pro",
            displayName = "DeepSeek-V4-Pro",
            provider = ProviderId.DEEPSEEK,
            contextWindow = 1_048_576,
            maxOutputTokens = 393_216,
            supportsVision = false,
            supportsReasoning = true,
            supportsNativeSearch = true
        )
    )


    /** 火山方舟(豆包):官方 GET /models 可动态拉取,这里内置常用几款 */
    private val volcengineModels = listOf(
        ModelInfo("doubao-seed-evolving", "Doubao Seed Evolving", ProviderId.VOLCENGINE,
            contextWindow = 1_048_576, maxOutputTokens = 262_144,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("doubao-seed-2-1-pro-260915", "Doubao Seed 2.1 Pro", ProviderId.VOLCENGINE,
            contextWindow = 1_048_576, maxOutputTokens = 262_144,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("doubao-seed-2-1-lite-260915", "Doubao Seed 2.1 Lite", ProviderId.VOLCENGINE,
            contextWindow = 1_048_576, maxOutputTokens = 262_144,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("doubao-seed-2-1-turbo-260628", "Doubao Seed 2.1 Turbo", ProviderId.VOLCENGINE,
            contextWindow = 262_144, maxOutputTokens = 262_144,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** 百度千帆(文心):GET /v2/models 可动态拉取 */
    private val qianfanModels = listOf(
        ModelInfo("ernie-5.5-flash-preview", "ERNIE-5.5-Flash-Preview", ProviderId.QIANFAN,
            contextWindow = 1_048_576, maxOutputTokens = 1_048_576,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("ernie-5.1", "ERNIE 5.1", ProviderId.QIANFAN,
            contextWindow = 131_072, maxOutputTokens = 65_536,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("ernie-light-dt-1.0", "ERNIE-Light-DT-1.0", ProviderId.QIANFAN,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = false),
        ModelInfo("internvl3-38b", "InternVL3-38B(视觉)", ProviderId.QIANFAN,
            contextWindow = 32_768, maxOutputTokens = 8_192,
            supportsVision = true, supportsReasoning = false, supportsNativeSearch = false)
    )

    /** 讯飞星火 MaaS:无 /models 接口,需内置清单 */
    private val sparkModels = listOf(
        ModelInfo("spark-x2.6", "Spark X2.6", ProviderId.SPARK,
            contextWindow = 1_048_576, maxOutputTokens = null,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("spark-x2.6-preview", "Spark X2.6 Preview", ProviderId.SPARK,
            contextWindow = 262_144, maxOutputTokens = null,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("spark-x2.6-4b", "Spark X2.6 4B(限免)", ProviderId.SPARK,
            contextWindow = 1_048_576, maxOutputTokens = null,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** 百川:无 /models 接口,需内置清单 */
    private val baichuanModels = listOf(
        ModelInfo("Baichuan4-Turbo", "Baichuan4-Turbo", ProviderId.BAICHUAN,
            contextWindow = 32_768, maxOutputTokens = 2_048,
            supportsVision = false, supportsReasoning = false, supportsNativeSearch = true),
        ModelInfo("Baichuan4-Air", "Baichuan4-Air", ProviderId.BAICHUAN,
            contextWindow = 32_768, maxOutputTokens = 2_048,
            supportsVision = false, supportsReasoning = false, supportsNativeSearch = true),
        ModelInfo("Baichuan-M3-Plus", "Baichuan-M3-Plus", ProviderId.BAICHUAN,
            contextWindow = 32_768, maxOutputTokens = 32_000,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("Baichuan-M3", "Baichuan-M3", ProviderId.BAICHUAN,
            contextWindow = 32_768, maxOutputTokens = 32_000,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** OpenAI:GET /v1/models 可动态拉取 */
    private val openAiModels = listOf(
        ModelInfo("gpt-6.2-astra", "GPT-6.2 Astra", ProviderId.OPENAI,
            contextWindow = 1_050_000, maxOutputTokens = 128_000,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("gpt-6.2-sol", "GPT-6.2 Sol", ProviderId.OPENAI,
            contextWindow = 1_050_000, maxOutputTokens = 128_000,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("gpt-6.1-terra", "GPT-6.1 Terra", ProviderId.OPENAI,
            contextWindow = 1_050_000, maxOutputTokens = 128_000,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("gpt-6-luna", "GPT-6 Luna", ProviderId.OPENAI,
            contextWindow = 1_050_000, maxOutputTokens = 128_000,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true)
    )


    /** 通义千问:模型 ID 取自百炼官方兼容列表(2026-11) */
    private val qwenModels = listOf(
        ModelInfo("qwen4-max", "Qwen4 Max", ProviderId.QWEN,
            contextWindow = 1_000_000, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("qwen3.8-max", "Qwen3.8 Max", ProviderId.QWEN,
            contextWindow = 1_000_000, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("qwen3.8-flash", "Qwen3.8 Flash", ProviderId.QWEN,
            contextWindow = 1_000_000, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** 智谱 GLM:官方定价页 2026-09 数据 */
    private val zhipuModels = listOf(
        ModelInfo("glm-5.3", "GLM-5.3", ProviderId.ZHIPU,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-5.3-flash", "GLM-5.3-Flash(视觉)", ProviderId.ZHIPU,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-5v-turbo", "GLM-5V-Turbo(视觉)", ProviderId.ZHIPU,
            contextWindow = 204_800, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("glm-4.7", "GLM-4.7", ProviderId.ZHIPU,
            contextWindow = 204_800, maxOutputTokens = 131_072,
            supportsVision = false, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** Moonshot Kimi:当前主力 kimi-k3(1M 上下文) */
    private val kimiModels = listOf(
        ModelInfo("kimi-k3.1-pro", "Kimi K3.1 Pro", ProviderId.MOONSHOT,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("kimi-k3.1-pro-highspeed", "Kimi K3.1 Pro 高速版", ProviderId.MOONSHOT,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true),
        ModelInfo("kimi-k3", "Kimi K3", ProviderId.MOONSHOT,
            contextWindow = 1_048_576, maxOutputTokens = 131_072,
            supportsVision = true, supportsReasoning = true, supportsNativeSearch = true)
    )

    /** 已验证厂商的模型清单;未核实厂商留空,由「从接口拉取」补齐 */
    private val verifiedModels: Map<ProviderId, List<ModelInfo>> = mapOf(
        ProviderId.DEEPSEEK to deepSeekModels,
        ProviderId.VOLCENGINE to volcengineModels,
        ProviderId.QIANFAN to qianfanModels,
        ProviderId.SPARK to sparkModels,
        ProviderId.BAICHUAN to baichuanModels,
        ProviderId.OPENAI to openAiModels,
        ProviderId.QWEN to qwenModels,
        ProviderId.ZHIPU to zhipuModels,
        ProviderId.MOONSHOT to kimiModels
    )

    val profiles: Map<ProviderId, ProviderProfile> = mapOf(
        ProviderId.DEEPSEEK to ProviderProfile(
            ProviderId.DEEPSEEK,
            "https://api.deepseek.com",
            Capabilities(
                modelsList = true,
                streamUsage = true,
                reasoning = true,
                vision = true,
                fileInput = true,
                nativeSearch = true,
                citations = true
            )
        ),
        ProviderId.QWEN to ProviderProfile(
            ProviderId.QWEN, "https://dashscope.aliyuncs.com/compatible-mode/v1",
            Capabilities(
                modelsList = false, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = true, citations = true
            )
        ),
        ProviderId.ZHIPU to ProviderProfile(
            ProviderId.ZHIPU, "https://open.bigmodel.cn/api/paas/v4",
            Capabilities(
                modelsList = false, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = true, citations = true
            )
        ),
        ProviderId.MOONSHOT to ProviderProfile(
            ProviderId.MOONSHOT, "https://api.moonshot.cn/v1",
            Capabilities(
                // Kimi 的联网只在 Responses API / Formula 里,本客户端走 Chat,先置灰
                modelsList = true, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = false, citations = false
            )
        ),
        ProviderId.VOLCENGINE to ProviderProfile(
            ProviderId.VOLCENGINE, "https://ark.cn-beijing.volces.com/api/v3",
            Capabilities(
                modelsList = true, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = true, citations = true
            )
        ),
        ProviderId.QIANFAN to ProviderProfile(
            ProviderId.QIANFAN, "https://qianfan.baidubce.com/v2",
            Capabilities(
                modelsList = true, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = true, citations = true
            )
        ),
        ProviderId.SPARK to ProviderProfile(
            ProviderId.SPARK, "https://maas-api.cn-huabei-1.xf-yun.com/v2",
            Capabilities(
                modelsList = false, streamUsage = true, reasoning = true,
                vision = false, nativeSearch = true, citations = false
            )
        ),
        ProviderId.BAICHUAN to ProviderProfile(
            ProviderId.BAICHUAN, "https://api.baichuan-ai.com/v1",
            Capabilities(
                modelsList = false, streamUsage = true, reasoning = true,
                vision = false, nativeSearch = true, citations = false
            )
        ),
        ProviderId.OPENAI to ProviderProfile(
            ProviderId.OPENAI, "https://api.openai.com/v1",
            Capabilities(
                // Chat Completions 没有服务端联网工具,联网开关置灰
                modelsList = true, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = false, citations = false
            )
        ),
        ProviderId.OPENROUTER to ProviderProfile(
            ProviderId.OPENROUTER, "https://openrouter.ai/api/v1",
            Capabilities(
                modelsList = true, streamUsage = true, reasoning = true,
                vision = true, nativeSearch = false, citations = false
            )
        ),
        ProviderId.CUSTOM to ProviderProfile(ProviderId.CUSTOM, "", Capabilities())
    )

    fun capabilitiesOf(providerId: ProviderId): Capabilities =
        profiles[providerId]?.capabilities ?: Capabilities()

    /** 按厂商与模型 ID 查内置条目(拿上下文窗口等元数据) */
    fun findModel(providerId: ProviderId, modelId: String): ModelInfo? =
        verifiedModels[providerId].orEmpty().firstOrNull { it.id == modelId }

    /** 内置模型;未核实厂商返回空列表 */
    fun builtinModels(providerId: ProviderId): List<ModelInfo> =
        verifiedModels[providerId].orEmpty()

    /** 按接口地址推断厂商标识:用于用户手填 URL 时仍能拿到能力矩阵 */
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
