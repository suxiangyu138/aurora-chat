package com.aichat.client.data.settings

import java.time.ZonedDateTime

/**
 * 价格表(人民币 / 百万 tokens)。
 *
 * 只收录已联网核实到人民币价格的模型;美元报价、第三方报价的模型不参与计费,
 * 避免算出一个看似精确的错数字。DeepSeek 有峰谷价:高峰为工作日 9-12、14-18(北京时间)。
 */
object Pricing {

    data class Price(
        val input: Double,
        val output: Double,
        val cacheHit: Double,
        /** true 表示这是高峰价,空闲时段按 5 折 */
        val peakPriced: Boolean = false
    )

    private val table: Map<String, Price> = mapOf(
        // DeepSeek(元/百万 tokens,高峰价)
        "deepseek-flash" to Price(2.0, 8.0, 0.04, peakPriced = true),
        "deepseek-v4-pro" to Price(9.0, 27.0, 0.30, peakPriced = true),
        // 智谱
        "glm-5.3" to Price(8.0, 28.0, 2.0),
        "glm-5.3-flash" to Price(0.8, 2.8, 0.23),
        "glm-5v-turbo" to Price(5.0, 22.0, 1.2),
        "glm-4.7" to Price(3.0, 14.0, 0.6),
        // Kimi(官方正文价)
        "kimi-k3" to Price(20.0, 100.0, 2.0),
        // 火山方舟(元/百万 tokens)
        "doubao-seed-evolving" to Price(6.0, 30.0, 0.0),
        "doubao-seed-2-1-pro-260915" to Price(6.0, 30.0, 0.0),
        "doubao-seed-2-1-lite-260915" to Price(0.8, 2.7, 0.0),
        "doubao-seed-2-1-turbo-260628" to Price(3.0, 15.0, 0.0),
        // 百度千帆
        "ernie-5.5-flash-preview" to Price(2.0, 8.0, 0.0),
        "ernie-5.1" to Price(4.0, 18.0, 0.0),
        "ernie-light-dt-1.0" to Price(8.0, 28.0, 0.0),
        // 讯飞星火
        "spark-x2.6" to Price(0.9, 3.6, 0.08),
        "spark-x2.6-preview" to Price(0.9, 3.6, 0.08),
        // 百川
        "Baichuan4-Turbo" to Price(15.0, 15.0, 0.0),
        "Baichuan4-Air" to Price(0.98, 0.98, 0.0),
        "Baichuan-M3-Plus" to Price(5.0, 9.0, 0.0),
        "Baichuan-M3" to Price(10.0, 30.0, 0.0)
    )

    fun priceOf(modelId: String): Price? = table[modelId]

    /** 当前是否 DeepSeek 高峰时段(北京时间工作日 9-12、14-18) */
    fun isPeak(at: ZonedDateTime = ZonedDateTime.now()): Boolean {
        val cn = at.withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
        val dow = cn.dayOfWeek.value
        if (dow > 5) return false
        val hour = cn.hour
        return hour in 9..11 || hour in 14..17
    }

    /**
     * 一轮对话的费用(元);模型不在价格表内返回 null,UI 只显示 token。
     * 缓存命中部分按缓存价,其余输入按输入价,输出按输出价。
     */
    fun costCny(
        modelId: String,
        promptTokens: Int,
        cachedTokens: Int,
        completionTokens: Int,
        at: ZonedDateTime = ZonedDateTime.now()
    ): Double? {
        val price = priceOf(modelId) ?: return null
        val cached = cachedTokens.coerceIn(0, promptTokens)
        val uncached = promptTokens - cached
        val factor = if (price.peakPriced && !isPeak(at)) 0.5 else 1.0
        val cost = (uncached * price.input + cached * price.cacheHit + completionTokens * price.output) / 1_000_000.0
        return cost * factor
    }
}
