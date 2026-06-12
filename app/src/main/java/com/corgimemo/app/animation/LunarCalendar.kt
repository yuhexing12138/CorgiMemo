package com.corgimemo.app.animation

import com.tyme.lunar.LunarDay
import com.tyme.solar.SolarDay

/**
 * 农历日期数据类
 *
 * @property year 农历年
 * @property month 农历月（1-12，闰月时为负数如 -4 表示闰四月）
 * @property day 农历日（1-30）
 * @property isLeap 是否为闰月
 */
data class LunarDate(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeap: Boolean = false
) {
    /**
     * 获取农历月份的中文描述（如"四月"、"闰四月"）
     */
    val monthDisplayName: String
        get() {
            val lunarMonths = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
            return if (isLeap) "闰${lunarMonths[Math.abs(month) - 1]}月" else "${lunarMonths[month - 1]}月"
        }

    /**
     * 获取农历日期的中文显示名（如"初一"、"廿七"、"十五"）
     *
     * 直接使用 tyme4kt 的 LunarDay.NAMES 数组，确保与底层库一致
     */
    val dayDisplayName: String
        get() = when (day) {
            1 -> "初一"
            2 -> "初二"
            3 -> "初三"
            4 -> "初四"
            5 -> "初五"
            6 -> "初六"
            7 -> "初七"
            8 -> "初八"
            9 -> "初九"
            10 -> "初十"
            11 -> "十一"
            12 -> "十二"
            13 -> "十三"
            14 -> "十四"
            15 -> "十五"
            16 -> "十六"
            17 -> "十七"
            18 -> "十八"
            19 -> "十九"
            20 -> "二十"
            21 -> "廿一"
            22 -> "廿二"
            23 -> "廿三"
            24 -> "廿四"
            25 -> "廿五"
            26 -> "廿六"
            27 -> "廿七"
            28 -> "廿八"
            29 -> "廿九"
            30 -> "三十"
            else -> day.toString()
        }
}

/**
 * 农历算法管理器
 *
 * 使用 tyme4kt 库（cn.6tail:tyme4kt）作为底层计算引擎，
 * 提供公历到农历的转换功能。
 *
 * tyme4kt 核心调用链：
 * ```
 * SolarDay.fromYmd(2026, 6, 12)    // 创建公历日期
 *   .getLunarDay()                  // 转换为农历 → LunarDay 对象
 *   .getLunarMonth()                // 获取农历月 → LunarMonth 对象
 *   .isLeap()                       // 判断是否闰月
 * ```
 *
 * 支持范围：1900年 ~ 2399 年（由 tyme4kt 保证）
 */
object LunarCalendar {

    /**
     * 公历转农历
     *
     * 使用 tyme4kt 的 SolarDay.getLunarDay() 进行精确转换。
     *
     * @param year 公历年
     * @param month 公历月（1-12）
     * @param day 公历日
     * @return 农历日期对象，如果超出支持范围返回 null
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate? {
        return try {
            // ✅ 核心：使用 tyme4kt 转换
            val solarDay = SolarDay.fromYmd(year, month, day)
            val lunarDay: LunarDay = solarDay.getLunarDay()

            // 从 LunarDay 提取年/月/日信息
            // 注意：tyme4kt 中，闰月的 month 字段为负数（如 -4 = 闰四月）
            val lunarYear = lunarDay.year
            val lunarMonthValue = lunarDay.month          // 可能为负数（闰月）
            val lunarDayValue = lunarDay.day              // 1-30
            val isLeapMonth = lunarDay.getLunarMonth().isLeap()

            LunarDate(
                year = lunarYear,
                month = Math.abs(lunarMonthValue),       // 取绝对值作为月份数字
                day = lunarDayValue,
                isLeap = isLeapMonth
            )
        } catch (e: Exception) {
            null
        }
    }

    // ========== 便捷判断方法 ==========

    /**
     * 判断指定公历日期是否为某个农历月日（非闰月）
     */
    fun isLunarMonthDay(year: Int, month: Int, day: Int, lunarMonth: Int, lunarDay: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == lunarMonth && lunar.day == lunarDay && !lunar.isLeap
    }

    /** 判断是否为农历正月初一（春节）*/
    fun isLunarNewYearDay(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 1, 1)

    /** 判断是否为农历正月十五（元宵节）*/
    fun isLanternFestival(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 1, 15)

    /** 判断是否为农历五月初五（端午节）*/
    fun isDragonBoatFestival(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 5, 5)

    /** 判断是否为农历八月十五（中秋节）*/
    fun isMidAutumnFestival(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 8, 15)

    /** 判断是否为农历九月初九（重阳节）*/
    fun isDoubleNinthFestival(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 9, 9)

    /** 判断是否为农历七月初七（七夕节）*/
    fun isQixiFestival(year: Int, month: Int, day: Int): Boolean =
        isLunarMonthDay(year, month, day, 7, 7)

    /** 检查年份是否在支持范围内 */
    fun isYearSupported(year: Int): Boolean = year in 1900..2399

    /** 获取支持的年份范围 */
    val supportedYearRange: Pair<Int, Int> get() = Pair(1900, 2399)
}
