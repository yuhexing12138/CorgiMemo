package com.corgimemo.app.animation

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * 农历日期数据类
 *
 * @property year 农历年
 * @property month 农历月（1-12）
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
     * 获取农历月份的中文描述
     */
    val monthDisplayName: String
        get() {
            val lunarMonths = arrayOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
            return if (isLeap) "闰${lunarMonths[month - 1]}月" else "${lunarMonths[month - 1]}月"
        }

    /**
     * 获取农历日期的中文描述
     */
    val dayDisplayName: String
        get() {
            return when (day) {
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
}

/**
 * 农历算法管理器
 * 实现公历到农历的转换
 * 支持范围：1900年1月31日 ~ 2100年12月31日
 *
 * 算法说明：
 * 1. 农历一年有12或13个月（闰月）
 * 2. 大月30天，小月29天
 * 3. 闰月的位置根据节气确定
 *
 * 数据结构说明：
 * lunarInfo数组：每行一个整数（16进制），表示对应年份的农历信息
 * - 高4位（0xF0000）：闰月月份（0表示无闰月）
 * - 中间12位（0x0FFF0）：1-12月的大小月（1表示大月30天，0表示小月29天）
 * - 低4位（0x0000F）：闰月的大小（1表示大月，0表示小月）
 */
object LunarCalendar {

    /**
     * 农历信息表（1900-2100年）
     * 格式：0xYYYYYMDD
     * - YYYY: 年份
     * - M: 闰月月份（0-F，0表示无闰月）
     * - DD: 闰月大小和各月大小
     */
    private val lunarInfo = intArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2, // 1900-1909
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977, // 1910-1919
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970, // 1920-1929
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950, // 1930-1939
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557, // 1940-1949
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5d0, 0x14573, 0x052d0, 0x0a9a8, 0x0e950, 0x06aa0, // 1950-1959
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0, // 1960-1969
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6, // 1970-1979
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570, // 1980-1989
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0, // 1990-1999
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5, // 2000-2009
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930, // 2010-2019
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530, // 2020-2029
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45, // 2030-2039
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0, // 2040-2049
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0, // 2050-2059
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4, // 2060-2069
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0, // 2070-2079
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160, // 2080-2089
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252  // 2090-2099
    )

    /**
     * 起始日期：1900年1月31日（农历1900年正月初一）
     */
    private const val BASE_YEAR = 1900

    /**
     * 起始日期的毫秒数
     */
    private val baseCalendar: Calendar
        get() {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
            cal.set(BASE_YEAR, 0, 31, 0, 0, 0) // 1900年1月31日
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }

    /**
     * 公历转农历
     *
     * @param year 公历年
     * @param month 公历月（1-12）
     * @param day 公历日
     * @return 农历日期对象，如果超出支持范围返回 null
     */
    fun solarToLunar(year: Int, month: Int, day: Int): LunarDate? {
        if (year < 1900 || year > 2099) return null

        var offset = calculateOffset(year, month, day)
        if (offset < 0) return null

        var lunarYear = BASE_YEAR
        var daysOfYear: Int
        var leapMonth: Int
        var isLeap: Boolean
        var lunarMonth: Int
        var lunarDay: Int

        // 计算农历年份
        while (lunarYear < 2100 && offset > 0) {
            daysOfYear = getYearDays(lunarYear)
            if (offset < daysOfYear) break
            offset -= daysOfYear
            lunarYear++
        }

        leapMonth = getLeapMonth(lunarYear)
        isLeap = false
        lunarMonth = 1

        // 计算农历月份
        while (lunarMonth < 13 && offset > 0) {
            if (leapMonth > 0 && lunarMonth == leapMonth + 1 && !isLeap) {
                lunarMonth--
                isLeap = true
                daysOfYear = getLeapMonthDays(lunarYear)
            } else {
                daysOfYear = getMonthDays(lunarYear, lunarMonth)
            }

            if (offset < daysOfYear) break
            offset -= daysOfYear

            if (isLeap && lunarMonth == leapMonth + 1) {
                isLeap = false
            }
            lunarMonth++
        }

        lunarDay = offset + 1

        return LunarDate(
            year = lunarYear,
            month = lunarMonth,
            day = lunarDay,
            isLeap = isLeap
        )
    }

    /**
     * 公历转农历
     *
     * @param date 公历日期
     * @return 农历日期对象
     */
    fun solarToLunar(date: Date): LunarDate? {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
        cal.time = date
        return solarToLunar(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 公历转农历
     *
     * @param timeInMillis 时间戳（毫秒）
     * @return 农历日期对象
     */
    fun solarToLunar(timeInMillis: Long): LunarDate? {
        return solarToLunar(Date(timeInMillis))
    }

    /**
     * 计算从基准日期到指定日期的天数偏移
     */
    private fun calculateOffset(year: Int, month: Int, day: Int): Int {
        val targetCal = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
        targetCal.set(year, month - 1, day, 0, 0, 0)
        targetCal.set(Calendar.MILLISECOND, 0)

        val baseCal = baseCalendar

        val diffMillis = targetCal.timeInMillis - baseCal.timeInMillis
        return (diffMillis / (24L * 60L * 60L * 1000L)).toInt()
    }

    /**
     * 获取农历年的总天数
     *
     * @param year 农历年
     * @return 该年的总天数
     */
    fun getYearDays(year: Int): Int {
        if (year < 1900 || year > 2099) return 0

        var sum = 348 // 12个小月的总天数：12 * 29 = 348

        for (i in 0x8000 downTo 0x8 step 2) {
            if ((lunarInfo[year - BASE_YEAR] and i) != 0) sum++
        }

        if (getLeapMonth(year) != 0) {
            sum += getLeapMonthDays(year)
        }

        return sum
    }

    /**
     * 获取农历年的闰月月份
     *
     * @param year 农历年
     * @return 闰月月份（0表示无闰月）
     */
    fun getLeapMonth(year: Int): Int {
        if (year < 1900 || year > 2099) return 0
        return (lunarInfo[year - BASE_YEAR] and 0xF0000).ushr(16)
    }

    /**
     * 获取农历年闰月的天数
     *
     * @param year 农历年
     * @return 闰月天数（0表示无闰月）
     */
    fun getLeapMonthDays(year: Int): Int {
        if (getLeapMonth(year) == 0) return 0
        return if ((lunarInfo[year - BASE_YEAR] and 0x10000) != 0) 30 else 29
    }

    /**
     * 获取农历月的天数
     *
     * @param year 农历年
     * @param month 农历月（1-12）
     * @return 该月的天数（29或30）
     */
    fun getMonthDays(year: Int, month: Int): Int {
        if (year < 1900 || year > 2099 || month < 1 || month > 12) return 0
        return if ((lunarInfo[year - BASE_YEAR] and (0x10000 shr month)) != 0) 30 else 29
    }

    /**
     * 判断是否为农历正月初一
     * 用于判断春节
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为农历正月初一
     */
    fun isLunarNewYearDay(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 1 && lunar.day == 1 && !lunar.isLeap
    }

    /**
     * 判断是否为农历正月十五（元宵节）
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为元宵节
     */
    fun isLanternFestival(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 1 && lunar.day == 15 && !lunar.isLeap
    }

    /**
     * 判断是否为农历五月初五（端午节）
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为端午节
     */
    fun isDragonBoatFestival(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 5 && lunar.day == 5 && !lunar.isLeap
    }

    /**
     * 判断是否为农历八月十五（中秋节）
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为中秋节
     */
    fun isMidAutumnFestival(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 8 && lunar.day == 15 && !lunar.isLeap
    }

    /**
     * 判断是否为农历九月初九（重阳节）
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为重阳节
     */
    fun isDoubleNinthFestival(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 9 && lunar.day == 9 && !lunar.isLeap
    }

    /**
     * 判断是否为农历七月初七（七夕节）
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 是否为七夕节
     */
    fun isQixiFestival(year: Int, month: Int, day: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == 7 && lunar.day == 7 && !lunar.isLeap
    }

    /**
     * 判断是否为农历特定月日
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @param lunarMonth 目标农历月
     * @param lunarDay 目标农历日
     * @return 是否匹配
     */
    fun isLunarMonthDay(year: Int, month: Int, day: Int, lunarMonth: Int, lunarDay: Int): Boolean {
        val lunar = solarToLunar(year, month, day) ?: return false
        return lunar.month == lunarMonth && lunar.day == lunarDay && !lunar.isLeap
    }

    /**
     * 获取支持的年份范围
     *
     * @return 年份范围 Pair（起始年，结束年）
     */
    val supportedYearRange: Pair<Int, Int>
        get() = Pair(1900, 2099)

    /**
     * 检查年份是否在支持范围内
     *
     * @param year 年份
     * @return 是否支持
     */
    fun isYearSupported(year: Int): Boolean {
        return year in 1900..2099
    }
}
