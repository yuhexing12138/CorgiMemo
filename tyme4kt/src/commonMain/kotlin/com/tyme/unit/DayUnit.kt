package com.tyme.unit

/**
 * 日
 *
 * @author 6tail
 */
abstract class DayUnit(
    /** 年 */
    year: Int,
    /** 月 */
    month: Int,
    /** 日 */
    val day: Int,
) : MonthUnit(year, month)
