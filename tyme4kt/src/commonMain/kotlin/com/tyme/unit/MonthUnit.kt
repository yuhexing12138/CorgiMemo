package com.tyme.unit

/**
 * 月
 *
 * @author 6tail
 */
abstract class MonthUnit(
    /** 年 */
    year: Int,
    /** 月 */
    val month: Int,
) : YearUnit(year)
