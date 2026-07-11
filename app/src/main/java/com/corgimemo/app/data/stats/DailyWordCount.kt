package com.corgimemo.app.data.stats

import java.time.LocalDate

/**
 * 单日字数统计点
 *
 * @property date 日期
 * @property dailyChars 当日输入字数（title + content + tags 去空白）
 * @property cumulativeChars 截至当日的累计字数
 */
data class DailyWordCount(
    val date: LocalDate,
    val dailyChars: Int,
    val cumulativeChars: Int
)
