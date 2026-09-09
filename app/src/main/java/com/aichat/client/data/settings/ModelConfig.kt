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
    val maxTokens: Int = 2048,
    val timeoutSeconds: Int = 60,
    val contextRounds: Int = 10
) {
    /** 核心三项必填才算配置完整 */
    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && modelName.isNotBlank()
}
