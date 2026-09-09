package com.aichat.client.data.settings

/** 预设国产/海外大模型模板:一键填充 URL 与模型名,API Key 仍需用户自行填写 */
object ModelPresets {

    data class Preset(
        val name: String,
        val baseUrl: String,
        val model: String
    )

    val list: List<Preset> = listOf(
        Preset("DeepSeek(深度求索)", "https://api.deepseek.com/v1", "deepseek-chat"),
        Preset("阿里通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
        Preset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus"),
        Preset("百度文心一言", "https://qianfan.baidubce.com/v2", "ernie-4.0-8k"),
        Preset("讯飞星火", "https://spark-api-open.xf-yun.com/v1", "generalv3.5"),
        Preset("Moonshot Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        Preset("百川智能", "https://api.baichuan-ai.com/v1", "Baichuan4"),
        Preset("豆包/火山方舟", "https://ark.cn-beijing.volces.com/api/v3", ""),
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o"),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", "")
    )
}
