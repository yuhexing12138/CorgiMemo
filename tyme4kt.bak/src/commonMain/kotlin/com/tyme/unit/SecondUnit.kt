package com.tyme.unit

import kotlin.jvm.JvmStatic

/**
 * 秒
 *
 * @author 6tail
 */
abstract class SecondUnit(
    /** 年 */
    year: Int,
    /** 月 */
    month: Int,
    /** 日 */
    day: Int,
    /** 时 */
    val hour: Int,
    /** 分 */
    val minute: Int,
    /** 秒 */
    val second: Int,
) : DayUnit(year, month, day) {
    companion object {
        @JvmStatic
        fun validate(hour: Int, minute: Int, second: Int) {
            if (hour !in 0..23) {
                throw IllegalArgumentException("illegal hour: $hour")
            }
            if (minute !in 0..59) {
                throw IllegalArgumentException("illegal minute: $minute")
            }
            if (second !in 0..59) {
                throw IllegalArgumentException("illegal second: $second")
            }
        }
    }
}
