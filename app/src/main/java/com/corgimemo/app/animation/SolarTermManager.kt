package com.corgimemo.app.animation

import java.util.Calendar
import java.util.TimeZone

/**
 * 节气管理器
 * 参考 HolidayManager 设计模式
 * 负责节气数据管理、节气判断逻辑
 *
 * 支持的节气：
 * - 春季：立春、雨水、惊蛰、春分、清明、谷雨
 * - 夏季：立夏、小满、芒种、夏至、小暑、大暑
 * - 秋季：立秋、处暑、白露、秋分、寒露、霜降
 * - 冬季：立冬、小雪、大雪、冬至、小寒、大寒
 */
object SolarTermManager {

    /**
     * 测试模式标志
     * 为 true 时强制显示指定节气
     */
    private var testModeEnabled = false

    /**
     * 测试模式下强制显示的节气 ID
     */
    private var forcedSolarTermId: String? = null

    /**
     * 获取当前节气
     *
     * @param currentTime 当前时间戳（默认为系统时间）
     * @return 当前节气对象，如果不是节气当天返回 null
     */
    fun getCurrentSolarTerm(currentTime: Long = System.currentTimeMillis()): SolarTerm? {
        // 测试模式下，强制显示指定节气
        if (testModeEnabled) {
            val forced = getForcedSolarTerm()
            if (forced != null) {
                return forced
            }
        }

        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        return SolarTermData.allSolarTerms.find { solarTerm ->
            isSolarTerm(solarTerm, month, day)
        }
    }

    /**
     * 判断某天是否为某个节气
     *
     * @param solarTerm 节气
     * @param month 月份（1-12）
     * @param day 日期
     * @return 是否为该节气
     */
    fun isSolarTerm(solarTerm: SolarTerm, month: Int, day: Int): Boolean {
        return solarTerm.date.month == month && day in solarTerm.date.dayRange
    }

    /**
     * 根据 ID 获取节气
     *
     * @param id 节气 ID
     * @return 节气对象，如果不存在返回 null
     */
    fun getSolarTermById(id: String): SolarTerm? {
        return SolarTermData.allSolarTerms.find { it.id == id }
    }

    /**
     * 获取指定公历日期的节气
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param day 日期
     * @return 节气对象，如果不是节气当天返回 null
     */
    fun getSolarTermForDate(year: Int, month: Int, day: Int): SolarTerm? {
        return SolarTermData.allSolarTerms.find { solarTerm ->
            isSolarTerm(solarTerm, month, day)
        }
    }

    /**
     * 获取春季的所有节气
     *
     * @return 春季节气列表（立春到谷雨）
     */
    fun getSpringSolarTerms(): List<SolarTerm> {
        return SolarTermData.allSolarTerms.subList(0, 6)
    }

    /**
     * 获取夏季的所有节气
     *
     * @return 夏季节气列表（立夏到大暑）
     */
    fun getSummerSolarTerms(): List<SolarTerm> {
        return SolarTermData.allSolarTerms.subList(6, 12)
    }

    /**
     * 获取秋季的所有节气
     *
     * @return 秋季节气列表（立秋到霜降）
     */
    fun getAutumnSolarTerms(): List<SolarTerm> {
        return SolarTermData.allSolarTerms.subList(12, 18)
    }

    /**
     * 获取冬季的所有节气
     *
     * @return 冬季节气列表（立冬到大寒）
     */
    fun getWinterSolarTerms(): List<SolarTerm> {
        return SolarTermData.allSolarTerms.subList(18, 24)
    }

    /**
     * 获取下一个即将到来的节气
     *
     * @param currentTime 当前时间戳
     * @return 下一个节气和距离天数的 Pair
     */
    fun getNextSolarTerm(currentTime: Long = System.currentTimeMillis()): Pair<SolarTerm, Int>? {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        var nextSolarTerm: SolarTerm? = null
        var minDaysDiff = Int.MAX_VALUE

        for (solarTerm in SolarTermData.allSolarTerms) {
            val solarTermMonth = solarTerm.date.month
            val solarTermDay = solarTerm.date.dayRange.first +
                (solarTerm.date.dayRange.last - solarTerm.date.dayRange.first) / 2

            val daysDiff = calculateDaysDiff(currentMonth, currentDay, solarTermMonth, solarTermDay)

            if (daysDiff >= 0 && daysDiff < minDaysDiff) {
                minDaysDiff = daysDiff
                nextSolarTerm = solarTerm
            }
        }

        if (nextSolarTerm == null) {
            val firstSolarTerm = SolarTermData.allSolarTerms.first()
            nextSolarTerm = firstSolarTerm
            minDaysDiff = 365 - calculateDaysDiff(12, 31, 1, 3)
        }

        return nextSolarTerm?.let { Pair(it, minDaysDiff) }
    }

    /**
     * 计算两个月日之间的天数差
     * 简化计算，假设每月 30 天
     *
     * @param fromMonth 起始月份
     * @param fromDay 起始日期
     * @param toMonth 目标月份
     * @param toDay 目标日期
     * @return 天数差
     */
    private fun calculateDaysDiff(fromMonth: Int, fromDay: Int, toMonth: Int, toDay: Int): Int {
        return if (toMonth > fromMonth || (toMonth == fromMonth && toDay >= fromDay)) {
            (toMonth - fromMonth) * 30 + (toDay - fromDay)
        } else {
            (12 - fromMonth + toMonth) * 30 + (toDay - fromDay)
        }
    }

    /**
     * 获取节气的季节
     *
     * @param solarTerm 节气
     * @return 季节名称（春/夏/秋/冬）
     */
    fun getSeasonForSolarTerm(solarTerm: SolarTerm): String {
        val index = SolarTermData.allSolarTerms.indexOf(solarTerm)
        return when {
            index < 6 -> "春"
            index < 12 -> "夏"
            index < 18 -> "秋"
            else -> "冬"
        }
    }

    /**
     * 获取当前季节的所有节气
     *
     * @param currentTime 当前时间戳
     * @return 当前季节的节气列表
     */
    fun getCurrentSeasonSolarTerms(currentTime: Long = System.currentTimeMillis()): List<SolarTerm> {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        val month = calendar.get(Calendar.MONTH) + 1

        return when (month) {
            in 2..4 -> getSpringSolarTerms()
            in 5..7 -> getSummerSolarTerms()
            in 8..10 -> getAutumnSolarTerms()
            else -> getWinterSolarTerms()
        }
    }

    /**
     * 启用测试模式，强制显示指定节气
     *
     * @param solarTermId 节气 ID，传 null 则不强制
     */
    fun enableTestMode(solarTermId: String? = null) {
        testModeEnabled = true
        forcedSolarTermId = solarTermId
    }

    /**
     * 禁用测试模式，恢复正常判断
     */
    fun disableTestMode() {
        testModeEnabled = false
        forcedSolarTermId = null
    }

    /**
     * 检查是否启用了测试模式
     */
    fun isTestModeEnabled(): Boolean {
        return testModeEnabled
    }

    /**
     * 获取测试模式下强制显示的节气
     */
    fun getForcedSolarTerm(): SolarTerm? {
        return forcedSolarTermId?.let { getSolarTermById(it) }
    }
}
