package com.corgimemo.app.animation

import java.util.Calendar
import java.util.TimeZone

/**
 * 节日日期类
 *
 * @property month 月份（1-12）
 * @property day 日期（1-31）
 * @property isLunar 是否为农历
 */
data class HolidayDate(
    val month: Int,
    val day: Int,
    val isLunar: Boolean = false
)

/**
 * 节日数据类
 *
 * @property id 节日 ID
 * @property name 节日名称（英文标识）
 * @property displayName 节日显示名称（中文）
 * @property date 节日日期
 * @property greetingMessages 问候语列表（随机选择一条显示）
 * @property outfitId 节日装扮 ID（可选，为 null 表示无特殊装扮）
 * @property emoji 节日表情
 */
data class Holiday(
    val id: String,
    val name: String,
    val displayName: String,
    val date: HolidayDate,
    val greetingMessages: List<String>,
    val outfitId: String? = null,
    val emoji: String
) {
    /**
     * 随机获取一条问候语
     *
     * @param corgiName 柯基名字
     * @return 问候语字符串
     */
    fun getRandomGreeting(corgiName: String? = null): String {
        val greeting = greetingMessages.random()
        return if (corgiName != null) {
            greeting.replace("柯基", corgiName)
        } else {
            greeting
        }
    }
}

/**
 * 节日 ID 常量
 */
object HolidayId {
    const val NEW_YEAR = "holiday_new_year"           // 元旦
    const val SPRING_FESTIVAL = "holiday_spring"       // 春节
    const val LANTERN = "holiday_lantern"              // 元宵节
    const val LABOR = "holiday_labor"                  // 劳动节
    const val DRAGON_BOAT = "holiday_dragon_boat"      // 端午节
    const val NATIONAL = "holiday_national"            // 国庆节
    const val MID_AUTUMN = "holiday_mid_autumn"         // 中秋节
    const val WINTER_SOLSTICE = "holiday_winter_solstice" // 冬至
    const val CHRISTMAS = "holiday_christmas"          // 圣诞节
}

/**
 * 节日装扮 ID 常量
 */
object HolidayOutfitId {
    const val NEW_YEAR_HAT = "holiday_new_year_hat"     // 派对帽（元旦/新年）
    const val RED_SCARF = "holiday_red_scarf"           // 红色围巾（春节）
    const val LANTERN = "holiday_lantern"               // 灯笼（元宵节）
    const val LABOR_HAT = "holiday_labor_hat"           // 工作帽（劳动节）
    const val DRAGON_HAT = "holiday_dragon_hat"         // 龙舟帽（端午节）
    const val FLAG = "holiday_flag"                     // 国旗（国庆节）
    const val MOON_DECOR = "holiday_moon"               // 月亮装饰（中秋节）
    const val SCARF = "holiday_scarf"                   // 围巾（冬至）
    const val CHRISTMAS_HAT = "holiday_christmas_hat"   // 圣诞帽（圣诞节）
}

/**
 * 节日管理器
 * 负责节日数据定义、节日判断逻辑
 *
 * 支持的节日：
 * - 元旦（1月1日）
 * - 春节（农历正月初一，使用农历算法计算）
 * - 元宵节（农历正月十五，使用农历算法计算）
 * - 劳动节（5月1日）
 * - 端午节（农历五月初五，使用农历算法计算）
 * - 国庆节（10月1日）
 * - 中秋节（农历八月十五，使用农历算法计算）
 * - 冬至（12月21-23日）
 * - 圣诞节（12月25日）
 *
 * 农历算法支持范围：1900-2100年
 */
object HolidayManager {

    /**
     * 所有节日定义列表
     */
    val allHolidays: List<Holiday> = listOf(
        // 元旦
        Holiday(
            id = HolidayId.NEW_YEAR,
            name = "NewYear",
            displayName = "元旦",
            date = HolidayDate(1, 1),
            greetingMessages = listOf(
                "新年快乐！柯基陪你开启新篇章 🎆",
                "元旦快乐！新的一年，柯基会一直陪着你 🎉",
                "新年新气象！柯基祝你心想事成 ✨",
                "元旦到啦！柯基给你送祝福 💫"
            ),
            outfitId = HolidayOutfitId.NEW_YEAR_HAT,
            emoji = "🎆"
        ),
        // 劳动节
        Holiday(
            id = HolidayId.LABOR,
            name = "LaborDay",
            displayName = "劳动节",
            date = HolidayDate(5, 1),
            greetingMessages = listOf(
                "五一快乐！劳动最光荣 💪",
                "劳动节快乐！好好休息一下吧 🛋️",
                "劳动人民最光荣！柯基为你点赞 👍",
                "辛苦了！劳动节愉快！🌻"
            ),
            outfitId = HolidayOutfitId.LABOR_HAT,
            emoji = "💪"
        ),
        // 国庆节
        Holiday(
            id = HolidayId.NATIONAL,
            name = "NationalDay",
            displayName = "国庆节",
            date = HolidayDate(10, 1),
            greetingMessages = listOf(
                "国庆快乐！举国同庆 🇨🇳",
                "国庆节快乐！祝福祖国繁荣昌盛 🌟",
                "十一黄金周！柯基陪你度过愉快假期 🏖️",
                "祝祖国生日快乐！🎉🎊"
            ),
            outfitId = HolidayOutfitId.FLAG,
            emoji = "🇨🇳"
        ),
        // 冬至
        Holiday(
            id = HolidayId.WINTER_SOLSTICE,
            name = "WinterSolstice",
            displayName = "冬至",
            date = HolidayDate(12, 21),
            greetingMessages = listOf(
                "冬至快乐！记得吃饺子 ❄️",
                "冬至到啦！吃碗汤圆暖暖身 🫕",
                "冬至大如年！柯基陪你过冬至 ☃️",
                "数九寒天，注意保暖哦！🧣"
            ),
            outfitId = HolidayOutfitId.SCARF,
            emoji = "❄️"
        ),
        // 圣诞节
        Holiday(
            id = HolidayId.CHRISTMAS,
            name = "Christmas",
            displayName = "圣诞节",
            date = HolidayDate(12, 25),
            greetingMessages = listOf(
                "圣诞快乐！柯基给你送礼物 🎄",
                "Merry Christmas! 柯基陪你过圣诞 🎅",
                "圣诞节到啦！许个愿吧 🎁",
                "平安喜乐！圣诞快乐！✨🎄"
            ),
            outfitId = HolidayOutfitId.CHRISTMAS_HAT,
            emoji = "🎄"
        ),
        // 春节（农历正月初一）
        Holiday(
            id = HolidayId.SPRING_FESTIVAL,
            name = "SpringFestival",
            displayName = "春节",
            date = HolidayDate(1, 1, isLunar = true),
            greetingMessages = listOf(
                "恭喜发财！柯基给你拜年啦 🧧",
                "春节快乐！大吉大利 🎊",
                "新年快乐！万事如意 🐉",
                "新春大吉！柯基祝你财源广进 💰"
            ),
            outfitId = HolidayOutfitId.RED_SCARF,
            emoji = "🧧"
        ),
        // 元宵节（农历正月十五）
        Holiday(
            id = HolidayId.LANTERN,
            name = "LanternFestival",
            displayName = "元宵节",
            date = HolidayDate(1, 15, isLunar = true),
            greetingMessages = listOf(
                "元宵节快乐！吃汤圆，团团圆圆 🏮",
                "元宵佳节！赏花灯，猜灯谜 🏮",
                "月圆人团圆！元宵节快乐 🌕",
                "吃汤圆啦！甜甜蜜蜜 🫕"
            ),
            outfitId = HolidayOutfitId.LANTERN,
            emoji = "🏮"
        ),
        // 端午节（农历五月初五）
        Holiday(
            id = HolidayId.DRAGON_BOAT,
            name = "DragonBoatFestival",
            displayName = "端午节",
            date = HolidayDate(5, 5, isLunar = true),
            greetingMessages = listOf(
                "端午节快乐！吃粽子，赛龙舟 🐉",
                "端午安康！记得吃粽子哦 🍙",
                "龙舟竞渡！端午节快乐 🚣",
                "粽叶飘香！端午佳节 🌿"
            ),
            outfitId = HolidayOutfitId.DRAGON_HAT,
            emoji = "🐉"
        ),
        // 中秋节（农历八月十五）
        Holiday(
            id = HolidayId.MID_AUTUMN,
            name = "MidAutumnFestival",
            displayName = "中秋节",
            date = HolidayDate(8, 15, isLunar = true),
            greetingMessages = listOf(
                "中秋快乐！月圆人团圆 🥮",
                "花好月圆！中秋节快乐 🌕",
                "赏月吃月饼！中秋快乐 🎑",
                "天涯共此时！中秋节快乐 🌙"
            ),
            outfitId = HolidayOutfitId.MOON_DECOR,
            emoji = "🥮"
        )
    )

    /**
     * 获取当前节日
     * 如果启用了测试模式，则返回强制设置的节日
     *
     * @param currentTime 当前时间戳（默认为系统时间）
     * @return 当前节日对象，如果不是节日则返回 null
     */
    fun getCurrentHoliday(currentTime: Long = System.currentTimeMillis()): Holiday? {
        // 测试模式：优先返回强制设置的节日
        if (testModeEnabled) {
            val forced = getForcedHoliday()
            if (forced != null) {
                return forced
            }
            // 如果没有指定节日 ID，返回最近的节日
            return getNearestHoliday(currentTime)
        }

        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = currentTime
        }
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val year = calendar.get(Calendar.YEAR)

        // 先检查公历节日
        val solarHoliday = getSolarHoliday(month, day)
        if (solarHoliday != null) {
            return solarHoliday
        }

        // 再检查农历节日
        val lunarHoliday = getLunarHoliday(year, month, day)
        if (lunarHoliday != null) {
            return lunarHoliday
        }

        return null
    }

    /**
     * 获取公历节日
     *
     * @param month 月份（1-12）
     * @param day 日期（1-31）
     * @return 节日对象，如果不是节日则返回 null
     */
    fun getSolarHoliday(month: Int, day: Int): Holiday? {
        // 先检查冬至（12月21-23日）
        if (month == 12 && (day in 21..23)) {
            return allHolidays.find { it.id == HolidayId.WINTER_SOLSTICE }
        }

        // 检查其他公历节日
        return allHolidays.find { holiday ->
            !holiday.date.isLunar &&
                    holiday.date.month == month &&
                    holiday.date.day == day
        }
    }

    /**
     * 获取农历节日
     * 使用农历算法判断指定公历日期是否为农历节日
     *
     * @param year 公历年份
     * @param month 公历月份（1-12）
     * @param day 公历日期（1-31）
     * @return 节日对象，如果不是农历节日则返回 null
     */
    fun getLunarHoliday(year: Int, month: Int, day: Int): Holiday? {
        // 先检查年份是否在支持范围内
        if (!LunarCalendar.isYearSupported(year)) {
            return null
        }

        val lunarHolidays = allHolidays.filter { it.date.isLunar }

        for (holiday in lunarHolidays) {
            // 使用农历算法判断
            val isMatch = LunarCalendar.isLunarMonthDay(
                year, month, day,
                holiday.date.month, holiday.date.day
            )
            if (isMatch) {
                return holiday
            }
        }

        return null
    }

    /**
     * 判断某天是否为某个节日
     *
     * @param holiday 要检查的节日
     * @param year 公历年份
     * @param month 公历月份（1-12）
     * @param day 公历日期（1-31）
     * @return 是否为该节日
     */
    fun isHoliday(holiday: Holiday, year: Int, month: Int, day: Int): Boolean {
        if (holiday.date.isLunar) {
            return getLunarHoliday(year, month, day)?.id == holiday.id
        } else {
            // 冬至特殊处理（12月21-23日）
            if (holiday.id == HolidayId.WINTER_SOLSTICE) {
                return month == 12 && day in 21..23
            }
            return holiday.date.month == month && holiday.date.day == day
        }
    }

    /**
     * 根据节日 ID 获取节日对象
     *
     * @param id 节日 ID
     * @return 节日对象，如果不存在返回 null
     */
    fun getHolidayById(id: String): Holiday? {
        return allHolidays.find { it.id == id }
    }

    /**
     * 获取下一个即将到来的节日
     *
     * @param currentTime 当前时间戳
     * @return 下一个节日和距离天数的 Pair，如果没有则返回 null
     */
    fun getNextHoliday(currentTime: Long = System.currentTimeMillis()): Pair<Holiday, Int>? {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = currentTime
        }
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH) + 1
        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
        val currentDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        var nextHoliday: Holiday? = null
        var minDaysDiff = Int.MAX_VALUE

        // 检查今年的所有节日
        for (holiday in allHolidays) {
            val holidayCalendar = Calendar.getInstance(TimeZone.getDefault())

            if (holiday.date.isLunar) {
                // 农历节日：使用农历算法计算公历日期
                val lunarDate = getSolarDateForLunar(
                    currentYear,
                    holiday.date.month,
                    holiday.date.day
                )
                if (lunarDate != null) {
                    holidayCalendar.set(currentYear, lunarDate.first - 1, lunarDate.second)
                } else {
                    continue
                }
            } else {
                holidayCalendar.set(currentYear, holiday.date.month - 1, holiday.date.day)
            }

            val holidayDayOfYear = holidayCalendar.get(Calendar.DAY_OF_YEAR)
            var daysDiff = holidayDayOfYear - currentDayOfYear

            // 如果今年的节日已经过了，计算明年的
            if (daysDiff < 0) {
                if (holiday.date.isLunar) {
                    val nextYearLunarDate = getSolarDateForLunar(
                        currentYear + 1,
                        holiday.date.month,
                        holiday.date.day
                    )
                    if (nextYearLunarDate != null) {
                        holidayCalendar.set(currentYear + 1, nextYearLunarDate.first - 1, nextYearLunarDate.second)
                        val nextYearDayOfYear = holidayCalendar.get(Calendar.DAY_OF_YEAR)
                        daysDiff = (365 - currentDayOfYear) + nextYearDayOfYear
                    } else {
                        continue
                    }
                } else {
                    holidayCalendar.add(Calendar.YEAR, 1)
                    val nextYearDayOfYear = holidayCalendar.get(Calendar.DAY_OF_YEAR)
                    daysDiff = (365 - currentDayOfYear) + nextYearDayOfYear
                }
            }

            if (daysDiff > 0 && daysDiff < minDaysDiff) {
                minDaysDiff = daysDiff
                nextHoliday = holiday
            }
        }

        return if (nextHoliday != null) {
            Pair(nextHoliday, minDaysDiff)
        } else {
            null
        }
    }

    /**
     * 获取农历月日对应的公历日期
     *
     * @param year 公历年
     * @param lunarMonth 农历月（1-12）
     * @param lunarDay 农历日（1-30）
     * @return 公历月日 Pair（月, 日），如果计算失败返回 null
     */
    private fun getSolarDateForLunar(year: Int, lunarMonth: Int, lunarDay: Int): Pair<Int, Int>? {
        if (!LunarCalendar.isYearSupported(year)) {
            return null
        }

        // 通过遍历找到该农历月日对应的公历日期
        val startCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
        startCalendar.set(year, 0, 1) // 从1月1日开始

        val endCalendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
        endCalendar.set(year + 1, 0, 1) // 到明年1月1日

        while (startCalendar.before(endCalendar)) {
            val solarMonth = startCalendar.get(Calendar.MONTH) + 1
            val solarDay = startCalendar.get(Calendar.DAY_OF_MONTH)

            if (LunarCalendar.isLunarMonthDay(year, solarMonth, solarDay, lunarMonth, lunarDay)) {
                return Pair(solarMonth, solarDay)
            }

            startCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }

    // ========== 测试模式功能 ==========

    /**
     * 测试模式：强制显示指定节日
     * 用于在真机上测试节日效果
     */
    private var testModeEnabled = false

    /**
     * 测试模式下强制显示的节日 ID
     */
    private var forcedHolidayId: String? = null

    /**
     * 启用测试模式，强制显示指定节日
     *
     * @param holidayId 节日 ID（使用 HolidayId 常量），传 null 则使用最近的节日
     */
    fun enableTestMode(holidayId: String? = null) {
        testModeEnabled = true
        forcedHolidayId = holidayId
    }

    /**
     * 禁用测试模式，恢复正常判断
     */
    fun disableTestMode() {
        testModeEnabled = false
        forcedHolidayId = null
    }

    /**
     * 检查是否启用了测试模式
     */
    fun isTestModeEnabled(): Boolean {
        return testModeEnabled
    }

    /**
     * 获取测试模式下强制显示的节日
     */
    fun getForcedHoliday(): Holiday? {
        return forcedHolidayId?.let { getHolidayById(it) }
    }

    /**
     * 获取指定年份的某个节日的公历日期
     * 用于测试和调试
     *
     * @param holidayId 节日 ID
     * @param year 公历年份
     * @return 公历月日 Pair（月, 日），如果失败返回 null
     */
    fun getHolidayDateForYear(holidayId: String, year: Int): Pair<Int, Int>? {
        val holiday = getHolidayById(holidayId) ?: return null

        if (holiday.date.isLunar) {
            return getSolarDateForLunar(year, holiday.date.month, holiday.date.day)
        } else {
            if (holiday.id == HolidayId.WINTER_SOLSTICE) {
                return Pair(12, 21)
            }
            return Pair(holiday.date.month, holiday.date.day)
        }
    }

    /**
     * 获取今天最近的节日
     * 用于测试模式快速切换
     *
     * @param currentTime 当前时间戳
     * @return 最近的节日对象
     */
    fun getNearestHoliday(currentTime: Long = System.currentTimeMillis()): Holiday? {
        return getNextHoliday(currentTime)?.first
    }

    /**
     * 获取指定公历日期的农历日期
     * 用于测试和调试
     *
     * @param year 公历年
     * @param month 公历月
     * @param day 公历日
     * @return 农历日期对象
     */
    fun getLunarDate(year: Int, month: Int, day: Int): LunarDate? {
        return LunarCalendar.solarToLunar(year, month, day)
    }
}
