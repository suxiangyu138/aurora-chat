package com.aichat.client.data.settings

/**
 * 模型接口配置:用户自定义 URL / API Key / 参数,完全开放不绑定固定模型。
 */
data class ModelConfig(
    val id: String = "",
    val name: String = "默认配置",
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 8192,
    val timeoutSeconds: Int = 300,
    val contextRounds: Int = 10,
    /** 流式输出开关(关闭后走非流式单次问答) */
    val streamEnabled: Boolean = true,
    /** 自定义请求体模板:支持 {model} {messages} {temperature} {top_p} {max_tokens} {stream} 占位符,留空用默认模板 */
    val customBody: String = ""
) {
    /** 核心三项必填才算配置完整 */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()

    /** 参数清洗:NaN/越界值归位(修复滑块快速拖动产生 NaN 导致接口报参数错误的问题) */
    fun sanitized(): ModelConfig = copy(
        temperature = temperature.takeIf { it.isFinite() }?.coerceIn(0f, 2f) ?: 0.7f,
        topP = topP.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1.0f,
        maxTokens = maxTokens.coerceIn(1, 32768),
        timeoutSeconds = timeoutSeconds.coerceIn(10, 600),
        contextRounds = contextRounds.coerceIn(0, 50)
    )
}
