package com.corgimemo.app.domain

import com.corgimemo.app.data.model.CategoryType
import java.util.Calendar

/**
 * 提醒规则密封类
 * 定义推荐提醒时间的计算方式
 */
sealed class ReminderRule {
    /** 相对于开始时间提前 N 分钟 */
    data class Advance(val minutes: Int) : ReminderRule()
    /** 开始时间当天指定时刻（如 9:00） */
    data class SameDayTime(val hour: Int, val minute: Int) : ReminderRule()
}

/**
 * 提醒时间推荐引擎
 * 根据任务分类和开始时间，自动计算推荐的提醒时间
 */
class ReminderRecommender {

    companion object {
        /** 一分钟的毫秒数 */
        private const val MINUTE_MS = 60 * 1000L

        /** 未分类/自定义分类的默认提前量（30 分钟） */
        private const val DEFAULT_ADVANCE = 30

        /** 推荐时间早于当前时间时的缓冲分钟数 */
        private const val FALLBACK_BUFFER_MINUTES = 5

        /**
         * 默认推荐规则表（按分类类型硬编码）
         */
        private val DEFAULT_RULES: Map<Int, ReminderRule> = mapOf(
            CategoryType.WORK to ReminderRule.Advance(15),
            CategoryType.STUDY to ReminderRule.Advance(60),
            CategoryType.LIFE to ReminderRule.SameDayTime(9, 0),
            CategoryType.SPORT to ReminderRule.Advance(30)
        )
    }

    /**
     * 计算推荐提醒时间
     *
     * @param categoryType 分类类型（0=学习, 1=工作, 2=生活, 3=运动, 4=自定义）
     * @param startDate 开始时间（毫秒时间戳），null 表示无开始时间
     * @return 推荐提醒时间（绝对毫秒时间戳），null 表示不推荐
     */
    fun recommend(
        categoryType: Int?,
        startDate: Long?
    ): Long? {
        if (startDate == null) return null

        if (isExpired(startDate)) return null

        val rule = getRule(categoryType)
        val recommended = calculateTime(rule, startDate)

        val now = System.currentTimeMillis()
        return if (recommended <= now) {
            now + FALLBACK_BUFFER_MINUTES * MINUTE_MS
        } else {
            recommended
        }
    }

    /**
     * 检查开始日期是否已过期（在今天 0:00 之前）
     */
    private fun isExpired(startDate: Long): Boolean {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return startDate < todayStart
    }

    /**
     * 获取推荐规则
     */
    private fun getRule(categoryType: Int?): ReminderRule {
        return DEFAULT_RULES[categoryType]
            ?: ReminderRule.Advance(DEFAULT_ADVANCE)
    }

    /**
     * 根据规则计算推荐提醒时间
     */
    private fun calculateTime(rule: ReminderRule, startDate: Long): Long {
        return when (rule) {
            is ReminderRule.Advance -> {
                startDate - (rule.minutes.toLong() * MINUTE_MS)
            }
            is ReminderRule.SameDayTime -> {
                Calendar.getInstance().apply {
                    timeInMillis = startDate
                    set(Calendar.HOUR_OF_DAY, rule.hour)
                    set(Calendar.MINUTE, rule.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
        }
    }
}