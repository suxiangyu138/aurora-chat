package com.aichat.client.data.remote.provider

/**
 * 中转站/自建网关的地址差异处理。
 *
 * 有的站给的是 https://host/api，真正的 OpenAI 兼容路径在 /api/v1；
 * 有的给 /v1；有的直接给完整 /chat/completions。这里统一归一化，
 * 避免"地址填了、模型列表能拉、但聊天一直 404/超时"。
 */
object EndpointResolver {

    /** 部分中转站有 Cloudflare 校验，默认 OkHttp UA 会被 403(1010) 拦下 */
    const val BROWSER_UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    fun chatUrl(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return ""
        if (base.endsWith("/chat/completions")) return base
        return when {
            base.endsWith("/v1") -> "$base/chat/completions"
            base.endsWith("/api") -> "$base/v1/chat/completions"
            Regex("/v\\d+$").containsMatchIn(base) -> "$base/chat/completions"
            else -> "$base/chat/completions"
        }
    }

    fun modelsUrls(baseUrl: String): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return emptyList()
        val list = linkedSetOf<String>()
        list += "$base/models"
        if (base.endsWith("/api")) list += "$base/v1/models"
        if (!base.endsWith("/v1")) list += "$base/v1/models"
        if (base.endsWith("/v1")) list += "${base.removeSuffix("/v1")}/models"
        return list.toList()
    }
}
