package com.corgimemo.app.animation

import java.time.LocalDate
import java.time.Month

/**
 * 装扮推荐数据类
 *
 * @property outfitId 推荐的装扮 ID
 * @property outfitName 装扮名称
 * @property outfitIcon 装扮图标 emoji
 * @property reason 推荐原因（如 "圣诞节快乐"）
 * @property badge 徽章图标（如 🎄）
 */
data class OutfitRecommendation(
    val outfitId: String,
    val outfitName: String,
    val outfitIcon: String,
    val reason: String,
    val badge: String
)

/**
 * 季节/节日装扮推荐器
 * 根据当前日期自动推荐适合的装扮
 *
 * 优先级：特殊节日 > 季节推荐
 */
object SeasonalOutfitRecommender {

    /**
     * 装扮图标映射
     */
    private val outfitIcons: Map<String, String> = mapOf(
        OutfitId.DEFAULT to "🐕",
        OutfitId.SCHOLAR_HAT to "🎓",
        OutfitId.TIE to "👔",
        OutfitId.CROWN to "👑",
        OutfitId.ANGEL_WINGS to "🪽",
        OutfitId.CAPE to "🧥"
    )

    /**
     * 特殊节日配置
     * 优先级高于季节推荐
     */
    private data class Holiday(
        val name: String,
        val badge: String,
        val startMonth: Month,
        val startDay: Int,
        val endMonth: Month,
        val endDay: Int,
        val recommendedOutfitId: String
    )

    private val holidays = listOf(
        Holiday(
            name = "圣诞节",
            badge = "🎄",
            startMonth = Month.DECEMBER,
            startDay = 15,
            endMonth = Month.DECEMBER,
            endDay = 31,
            recommendedOutfitId = OutfitId.CROWN
        ),
        Holiday(
            name = "春节",
            badge = "🧧",
            startMonth = Month.JANUARY,
            startDay = 20,
            endMonth = Month.FEBRUARY,
            endDay = 20,
            recommendedOutfitId = OutfitId.TIE
        ),
        Holiday(
            name = "万圣节",
            badge = "🎃",
            startMonth = Month.OCTOBER,
            startDay = 25,
            endMonth = Month.NOVEMBER,
            endDay = 5,
            recommendedOutfitId = OutfitId.CAPE
        ),
        Holiday(
            name = "儿童节",
            badge = "🎈",
            startMonth = Month.JUNE,
            startDay = 1,
            endMonth = Month.JUNE,
            endDay = 7,
            recommendedOutfitId = OutfitId.ANGEL_WINGS
        )
    )

    /**
     * 季节配置
     */
    private data class Season(
        val name: String,
        val badge: String,
        val startMonth: Month,
        val startDay: Int,
        val endMonth: Month,
        val endDay: Int,
        val recommendedOutfitId: String
    )

    private val seasons = listOf(
        Season(
            name = "春天",
            badge = "🌸",
            startMonth = Month.MARCH,
            startDay = 1,
            endMonth = Month.MAY,
            endDay = 31,
            recommendedOutfitId = OutfitId.ANGEL_WINGS
        ),
        Season(
            name = "夏天",
            badge = "☀️",
            startMonth = Month.JUNE,
            startDay = 1,
            endMonth = Month.AUGUST,
            endDay = 31,
            recommendedOutfitId = OutfitId.DEFAULT
        ),
        Season(
            name = "秋天",
            badge = "🍂",
            startMonth = Month.SEPTEMBER,
            startDay = 1,
            endMonth = Month.NOVEMBER,
            endDay = 30,
            recommendedOutfitId = OutfitId.SCHOLAR_HAT
        ),
        Season(
            name = "冬天",
            badge = "❄️",
            startMonth = Month.DECEMBER,
            startDay = 1,
            endMonth = Month.FEBRUARY,
            endDay = 29,
            recommendedOutfitId = OutfitId.CROWN
        )
    )

    /**
     * 获取当前推荐的装扮
     * 优先级：特殊节日 > 季节推荐
     * 如果推荐的装扮未解锁，则返回 null
     *
     * @param currentDate 当前日期
     * @param unlockedOutfitsJson 已解锁装扮 JSON
     * @return 装扮推荐对象，如果没有合适推荐或推荐的装扮未解锁则返回 null
     */
    fun getCurrentRecommendation(
        currentDate: LocalDate = LocalDate.now(),
        unlockedOutfitsJson: String
    ): OutfitRecommendation? {
        // 1. 优先检查特殊节日
        val holiday = holidays.firstOrNull { isDateInRange(currentDate, it) }
        if (holiday != null) {
            val outfit = OutfitManager.getOutfitById(holiday.recommendedOutfitId)
            if (outfit != null && OutfitManager.isOutfitUnlocked(holiday.recommendedOutfitId, unlockedOutfitsJson)) {
                return OutfitRecommendation(
                    outfitId = outfit.id,
                    outfitName = outfit.name,
                    outfitIcon = outfitIcons[outfit.id] ?: "🎁",
                    reason = "${holiday.badge} ${holiday.name}到了！试试${outfit.name}吧~",
                    badge = holiday.badge
                )
            }
        }

        // 2. 检查季节推荐
        val season = seasons.firstOrNull { isDateInRange(currentDate, it) }
        if (season != null) {
            // 季节推荐的 DEFAULT 不需要检查是否解锁
            if (season.recommendedOutfitId == OutfitId.DEFAULT) {
                val outfit = OutfitManager.defaultOutfit
                return OutfitRecommendation(
                    outfitId = outfit.id,
                    outfitName = outfit.name,
                    outfitIcon = outfitIcons[outfit.id] ?: "🐕",
                    reason = "${season.badge} ${season.name}适合清爽的${outfit.name}",
                    badge = season.badge
                )
            }

            val outfit = OutfitManager.getOutfitById(season.recommendedOutfitId)
            if (outfit != null && OutfitManager.isOutfitUnlocked(season.recommendedOutfitId, unlockedOutfitsJson)) {
                return OutfitRecommendation(
                    outfitId = outfit.id,
                    outfitName = outfit.name,
                    outfitIcon = outfitIcons[outfit.id] ?: "🎁",
                    reason = "${season.badge} ${season.name}推荐装扮：${outfit.name}",
                    badge = season.badge
                )
            }
        }

        return null
    }

    /**
     * 检查日期是否在指定范围内
     * 支持跨年（如 12.1-2.29）
     */
    private fun <T> isDateInRange(date: LocalDate, range: T): Boolean {
        val (startMonth, startDay, endMonth, endDay) = when (range) {
            is Holiday -> Quadruple(range.startMonth, range.startDay, range.endMonth, range.endDay)
            is Season -> Quadruple(range.startMonth, range.startDay, range.endMonth, range.endDay)
            else -> return false
        }

        val currentMonth = date.month
        val currentDay = date.dayOfMonth

        // 处理不跨年的情况（如 3.1-5.31）
        if (startMonth <= endMonth) {
            return if (currentMonth > startMonth && currentMonth < endMonth) {
                true
            } else if (currentMonth == startMonth) {
                currentDay >= startDay
            } else if (currentMonth == endMonth) {
                currentDay <= endDay
            } else {
                false
            }
        }

        // 处理跨年的情况（如 12.1-2.29）
        return if (currentMonth > startMonth || currentMonth < endMonth) {
            true
        } else if (currentMonth == startMonth) {
            currentDay >= startDay
        } else if (currentMonth == endMonth) {
            currentDay <= endDay
        } else {
            false
        }
    }

    /**
     * 辅助数据类，用于元组传递
     */
    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
