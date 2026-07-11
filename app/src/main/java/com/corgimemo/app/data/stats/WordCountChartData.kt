package com.corgimemo.app.data.stats

/**
 * 字数统计图表数据集
 *
 * @property range 时间范围
 * @property points 数据点列表（长度 == range.days，从最早日期到今天）
 */
data class WordCountChartData(
    val range: ChartRange,
    val points: List<DailyWordCount>
) {
    /**
     * 当前累计字数（最后一点累计值，无数据时返回 0）
     */
    val currentCumulativeChars: Int
        get() = points.lastOrNull()?.cumulativeChars ?: 0
}
