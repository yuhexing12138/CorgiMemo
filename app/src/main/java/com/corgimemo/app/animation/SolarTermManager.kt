package com.corgimemo.app.animation

import com.tyme.solar.SolarDay
import java.util.Calendar
import java.util.TimeZone

/**
 * 节气管理器
 *
 * 使用 tyme4kt 库（cn.6tail:tyme4kt）精确计算节气日期。
 *
 * tyme4kt 核心调用链：
 * ```
 * SolarDay.fromYmd(2026, 6, 5)       // 创建公历日期
 *   .getTerm()                        // 获取当天所属的节气 → SolarTerm
 *   .getName()                        // 获取节气中文名 → "芒种"
 * ```
 *
 * SolarTerm.NAMES 数组（24个节气，index 0-23）：
 * ["冬至", "小寒", "大寒", "立春", "雨水", "惊蛰",
 *  "春分", "清明", "谷雨", "立夏", "小满", "芒种",
 *  "夏至", "小暑", "大暑", "立秋", "处暑", "白露",
 *  "秋分", "寒露", "霜降", "立冬", "小雪", "大雪"]
 *
 * 精度：tyme4kt 使用天文算法（ShouXingUtil.calcQi），精确到具体日期
 */
object SolarTermManager {

    /** 测试模式标志 */
    private var testModeEnabled = false
    private var forcedSolarTermId: String? = null

    /**
     * 获取当前节气
     *
     * @param currentTime 当前时间戳
     * @return 当前节气对象，如果不是节气当天返回 null
     */
    fun getCurrentSolarTerm(currentTime: Long = System.currentTimeMillis()): SolarTerm? {
        if (testModeEnabled) {
            val forced = getForcedSolarTerm()
            if (forced != null) return forced
        }

        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        return getSolarTermForDate(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    /**
     * 判断指定公历日期是否为节气当天
     *
     * ✅ 使用 tyme4kt 的 getTerm() 精确判断：
     * - 返回当天所属节气的 SolarTerm 对象
     * - 用 getName() 匹配我们的元数据
     *
     * @param year 公历年
     * @param month 公历月（1-12）
     * @param day 公历日
     * @return 节气元数据对象，如果不是节气当天返回 null
     */
    fun getSolarTermForDate(year: Int, month: Int, day: Int): SolarTerm? {
        if (testModeEnabled) {
            val forced = getForcedSolarTerm()
            if (forced != null) return forced
        }

        return try {
            // ✅ 核心：使用 tyme4kt 的 getTermDay() 方法
            // 注意：getTerm() 返回当天所属的节气周期（整个周期内都返回同一个节气）
            // 必须通过 getTermDay().getDayIndex() 判断是否为节气**当天**
            //   - dayIndex == 0 → 节气当天 ✅
            //   - dayIndex > 0  → 节气周期内的普通日期 ❌
            val solarDay = SolarDay.fromYmd(year, month, day)
            val termDay = solarDay.getTermDay()          // SolarTermDay 对象

            if (termDay.getDayIndex() != 0) {
                // 不是节气当天，返回 null
                return null
            }

            // 是节气当天！提取节气名称并匹配元数据
            val term = termDay.getSolarTerm()
            val termName = term.getName()

            SolarTermData.allSolarTerms.find { it.displayName == termName }
        } catch (e: Exception) {
            // Fallback：使用硬编码范围判断
            getSolarTermByRange(month, day)
        }
    }

    /**
     * 判断某天是否为某个指定节气
     */
    fun isSolarTerm(solarTerm: SolarTerm, month: Int, day: Int): Boolean {
        // 优先使用 tyme4kt 精确判断（仅节气当天返回 true）
        try {
            val solarDay = SolarDay.fromYmd(2000, month, day)
            val termDay = solarDay.getTermDay()
            // 必须是节气当天（dayIndex == 0）且名称匹配
            if (termDay.getDayIndex() == 0 &&
                termDay.getSolarTerm().getName() == solarTerm.displayName) return true
        } catch (_: Exception) {}

        // Fallback：硬编码范围
        return solarTerm.date.month == month && day in solarTerm.date.dayRange
    }

    /**
     * Fallback：使用硬编码范围判断
     */
    private fun getSolarTermByRange(month: Int, day: Int): SolarTerm? {
        return SolarTermData.allSolarTerms.find { it.date.month == month && day in it.date.dayRange }
    }

    /** 根据 ID 获取节气 */
    fun getSolarTermById(id: String): SolarTerm? =
        SolarTermData.allSolarTerms.find { it.id == id }

    /** 获取春季的所有节气（立春~谷雨）*/
    fun getSpringSolarTerms(): List<SolarTerm> = SolarTermData.allSolarTerms.subList(0, 6)

    /** 获取夏季的所有节气（立夏~大暑）*/
    fun getSummerSolarTerms(): List<SolarTerm> = SolarTermData.allSolarTerms.subList(6, 12)

    /** 获取秋季的所有节气（立秋~霜降）*/
    fun getAutumnSolarTerms(): List<SolarTerm> = SolarTermData.allSolarTerms.subList(12, 18)

    /** 获取冬季的所有节气（立冬~大雪）*/
    fun getWinterSolarTerms(): List<SolarTerm> = SolarTermData.allSolarTerms.subList(18, 24)

    /**
     * 获取下一个即将到来的节气
     *
     * @param currentTime 当前时间戳
     * @return 下一个节气和距离天数的 Pair
     */
    fun getNextSolarTerm(currentTime: Long = System.currentTimeMillis()): Pair<SolarTerm, Int>? {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)

        var nextSolarTerm: SolarTerm? = null
        var minDaysDiff = Int.MAX_VALUE

        for (solarTerm in SolarTermData.allSolarTerms) {
            val termDay = solarTerm.date.dayRange.first +
                (solarTerm.date.dayRange.last - solarTerm.date.dayRange.first) / 2
            val daysDiff = calculateDaysDiff(currentMonth, currentDay, solarTerm.date.month, termDay)

            if (daysDiff >= 0 && daysDiff < minDaysDiff) {
                minDaysDiff = daysDiff
                nextSolarTerm = solarTerm
            }
        }

        if (nextSolarTerm == null) {
            nextSolarTerm = SolarTermData.allSolarTerms.first()
            minDaysDiff = 365 - calculateDaysDiff(12, 31, 1, 3)
        }

        return Pair(nextSolarTerm, minDaysDiff)
    }

    /** 计算两个日期之间的天数差（近似值）*/
    private fun calculateDaysDiff(fromMonth: Int, fromDay: Int, toMonth: Int, toDay: Int): Int {
        return if (toMonth > fromMonth || (toMonth == fromMonth && toDay >= fromDay)) {
            (toMonth - fromMonth) * 30 + (toDay - fromDay)
        } else {
            (12 - fromMonth + toMonth) * 30 + (toDay - fromDay)
        }
    }

    /** 获取节气的季节 */
    fun getSeasonForSolarTerm(solarTerm: SolarTerm): String {
        val index = SolarTermData.allSolarTerms.indexOf(solarTerm)
        return when {
            index < 6 -> "春"
            index < 12 -> "夏"
            index < 18 -> "秋"
            else -> "冬"
        }
    }

    /** 获取当前季节的所有节气 */
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

    /** 启用测试模式 */
    fun enableTestMode(solarTermId: String? = null) {
        testModeEnabled = true
        forcedSolarTermId = solarTermId
    }

    /** 禁用测试模式 */
    fun disableTestMode() {
        testModeEnabled = false
        forcedSolarTermId = null
    }

    fun isTestModeEnabled(): Boolean = testModeEnabled
    fun getForcedSolarTerm(): SolarTerm? = forcedSolarTermId?.let { getSolarTermById(it) }
}
