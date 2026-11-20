package com.aichat.client.data.settings

import com.aichat.client.data.remote.provider.ModelInfo
import com.aichat.client.data.remote.provider.ProviderCatalog
import com.aichat.client.data.remote.provider.ProviderId

/**
 * 预设模板:主流厂商 + 中转站/自定义兼容接口。
 *
 * 内置模型只收录联网核实过的条目;未核实厂商的模型名留空,
 * 保存配置后可在设置页「从接口拉取模型」自动补齐。
 */
object ModelPresets {

    data class Preset(
        val provider: ProviderId,
        val name: String,
        val baseUrl: String,
        val model: String,
        /** 预设说明:告诉用户模型名是否需要自己补 */
        val hint: String = ""
    )

    val list: List<Preset> = buildList {
        ProviderId.entries.filter { it != ProviderId.CUSTOM }.forEach { p ->
            val base = ProviderCatalog.profiles[p]?.baseUrl ?: p.defaultBaseUrl
            val models = ProviderCatalog.builtinModels(p)
            if (models.isEmpty()) {
                add(Preset(p, p.label, base, "", "模型名留空,保存后从接口拉取"))
            } else {
                models.forEach { m ->
                    add(Preset(p, p.label, base, m.id, m.displayName))
                }
            }
        }
        add(
            Preset(
                ProviderId.CUSTOM,
                "中转站 / 自定义兼容接口",
                "",
                "",
                "填 Base URL 与 API Key,可一键拉取该站模型"
            )
        )
    }

    /** 兼容旧入口:仅返回有模型名的模板 */
    val quickFill: List<Preset> = list.filter { it.model.isNotBlank() }

    fun forProvider(provider: ProviderId): List<Preset> = list.filter { it.provider == provider }

    fun modelsOf(provider: ProviderId): List<ModelInfo> = ProviderCatalog.builtinModels(provider)
}
