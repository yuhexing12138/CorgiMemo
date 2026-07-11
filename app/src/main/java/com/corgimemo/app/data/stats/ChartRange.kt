package com.corgimemo.app.data.stats

/**
 * 统计图表时间范围
 *
 * @property days 时间范围天数（7 或 30）
 */
enum class ChartRange(val days: Int) {
    /** 最近 7 天 */
    SEVEN_DAYS(7),

    /** 最近 30 天 */
    THIRTY_DAYS(30)
}
