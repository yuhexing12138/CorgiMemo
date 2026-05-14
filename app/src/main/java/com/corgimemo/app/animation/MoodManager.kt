package com.corgimemo.app.animation

import java.util.Calendar
import java.util.TimeZone

/**
 * 情绪管理器
 * 负责情绪值计算和情绪状态管理
 */
object MoodManager {

    // 情绪值范围
    const val MIN_MOOD = 0
    const val MAX_MOOD = 100
    const val DEFAULT_MOOD = 50

    // 情绪计算公式常量
    private const val BASE_MOOD = 50
    private const val COMPLETION_RATE_WEIGHT = 30
    private const val CONSECUTIVE_DAYS_WEIGHT = 5
    private const val OVERDUE_TASKS_PENALTY = -10

    /**
     * 根据用户指定的公式计算情绪值
     * 公式: 50 + 今日完成率×30 + 连续活跃天数×5 + 超期任务数×(-10)
     *
     * @param todayCompletionRate 今日完成率 (0.0 - 1.0)
     * @param consecutiveDays 连续活跃天数
     * @param overdueTasksCount 超期任务数
     * @return 计算后的情绪值 (0-100)
     */
    fun calculateMoodValue(
        todayCompletionRate: Float,
        consecutiveDays: Int,
        overdueTasksCount: Int
    ): Int {
        val base = BASE_MOOD.toFloat()
        val completionBonus = todayCompletionRate * COMPLETION_RATE_WEIGHT
        val consecutiveBonus = consecutiveDays * CONSECUTIVE_DAYS_WEIGHT.toFloat()
        val overduePenalty = overdueTasksCount * OVERDUE_TASKS_PENALTY.toFloat()

        val total = base + completionBonus + consecutiveBonus + overduePenalty
        return clampMood(total.toInt())
    }

    /**
     * 计算今日完成率
     *
     * @param completedToday 今日已完成任务数
     * @param totalToday 今日总任务数
     * @return 完成率 (0.0 - 1.0)，无任务时返回 0.5（中性值）
     */
    fun calculateCompletionRate(completedToday: Int, totalToday: Int): Float {
        if (totalToday == 0) return 0.5f
        return (completedToday.toFloat() / totalToday.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * 判断任务是否超期
     *
     * @param dueDate 截止日期时间戳（毫秒）
     * @param currentTime 当前时间戳
     * @return 是否超期（dueDate 为 null 时不算超期）
     */
    fun isOverdue(dueDate: Long?, currentTime: Long = System.currentTimeMillis()): Boolean {
        return dueDate != null && dueDate < currentTime
    }

    /**
     * 判断时间戳是否属于今天
     *
     * @param timestamp 时间戳（毫秒）
     * @param currentTime 当前时间戳
     * @return 是否属于今天
     */
    fun isToday(timestamp: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        val todayCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCalendar.timeInMillis
        val tomorrowStart = todayStart + 24 * 60 * 60 * 1000

        return timestamp in todayStart until tomorrowStart
    }

    /**
     * 计算两个时间戳之间的天数差
     *
     * @param earlierTime 较早的时间戳
     * @param laterTime 较晚的时间戳
     * @return 天数差
     */
    fun calculateDaysBetween(earlierTime: Long, laterTime: Long): Int {
        val earlierCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = earlierTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val laterCalendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = laterTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val diffMs = laterCalendar.timeInMillis - earlierCalendar.timeInMillis
        return (diffMs / (24 * 60 * 60 * 1000)).toInt()
    }

    /**
     * 将情绪值限制在有效范围内
     *
     * @param mood 情绪值
     * @return 限制后的情绪值
     */
    fun clampMood(mood: Int): Int {
        return mood.coerceIn(MIN_MOOD, MAX_MOOD)
    }

    /**
     * 根据情绪值获取情绪状态
     *
     * @param moodValue 情绪值（0-100）
     * @return 情绪状态
     */
    fun getMoodFromValue(moodValue: Int): CorgiMood {
        return when {
            moodValue > 80 -> CorgiMood.EXCITED
            moodValue in 60..80 -> CorgiMood.HAPPY
            moodValue in 40..59 -> CorgiMood.NORMAL
            moodValue in 20..39 -> CorgiMood.WORRIED
            else -> CorgiMood.SAD
        }
    }

    // ==================== 兼容旧接口（保持向后兼容） ====================

    // 情绪变化值
    const val MOOD_CHANGE_COMPLETE_TODO = 10
    const val MOOD_CHANGE_CONSECUTIVE_BONUS = 5
    const val MOOD_CHANGE_INACTIVE_DAY = -5

    /**
     * 完成待办任务时增加情绪值（旧接口，用于简单场景）
     *
     * @param currentMood 当前情绪值
     * @param consecutiveDays 连续完成天数
     * @return 新的情绪值
     */
    fun onTodoComplete(currentMood: Int, consecutiveDays: Int = 0): Int {
        var newMood = currentMood + MOOD_CHANGE_COMPLETE_TODO

        // 连续3天以上额外奖励
        if (consecutiveDays >= 3) {
            newMood += MOOD_CHANGE_CONSECUTIVE_BONUS
        }

        return clampMood(newMood)
    }

    /**
     * 长时间未操作时减少情绪值（旧接口，用于简单场景）
     *
     * @param currentMood 当前情绪值
     * @param inactiveDays 未操作天数
     * @return 新的情绪值
     */
    fun onInactive(currentMood: Int, inactiveDays: Int = 1): Int {
        val newMood = currentMood + (MOOD_CHANGE_INACTIVE_DAY * inactiveDays)
        return clampMood(newMood)
    }
}

/**
 * 问候语管理器
 * 根据情绪和时间生成不同的问候语
 */
object GreetingManager {

    /**
     * 根据情绪获取问候语
     *
     * @param mood 情绪状态
     * @param name 柯基名字
     * @return 问候语字符串
     */
    fun getGreeting(mood: CorgiMood, name: String? = null): String {
        val corgiName = name ?: "柯基"

        return when (mood) {
            CorgiMood.EXCITED -> "${corgiName}开心得蹦蹦跳跳！今天也要元气满满哦 🎉"
            CorgiMood.HAPPY -> "${corgiName}摇着尾巴迎接你！今天心情不错呢 😊"
            CorgiMood.NORMAL -> "${corgiName}在等你呢，来看看待办列表吧 🐾"
            CorgiMood.EXPECTING -> "${corgiName}歪着头期待你的指令~ 🤔"
            CorgiMood.WORRIED -> "${corgiName}有点担心你哦，是不是太忙了？ 😟"
            CorgiMood.SLEEPY -> "${corgiName}有点困了，但还是会陪着你 💤"
            CorgiMood.SAD -> "${corgiName}有点低落...陪陪它好吗？ 🥺"
        }
    }

    /**
     * 获取庆祝消息（完成任务时）
     *
     * @param mood 情绪状态
     * @return 庆祝消息
     */
    fun getCelebrationMessage(mood: CorgiMood): String {
        return when (mood) {
            CorgiMood.EXCITED -> "太棒了！${mood.emoji}"
            CorgiMood.HAPPY -> "做得好！继续加油！${mood.emoji}"
            CorgiMood.NORMAL -> "完成啦！${mood.emoji}"
            else -> "又完成一个任务啦！${mood.emoji}"
        }
    }
}

/**
 * 情绪状态扩展属性
 */
val CorgiMood.emoji: String
    get() = when (this) {
        CorgiMood.EXCITED -> "🎉"
        CorgiMood.HAPPY -> "😊"
        CorgiMood.NORMAL -> "🐾"
        CorgiMood.EXPECTING -> "🤔"
        CorgiMood.WORRIED -> "😟"
        CorgiMood.SLEEPY -> "💤"
        CorgiMood.SAD -> "🥺"
    }

/**
 * 情绪状态描述
 */
val CorgiMood.description: String
    get() = when (this) {
        CorgiMood.EXCITED -> "非常兴奋"
        CorgiMood.HAPPY -> "开心"
        CorgiMood.NORMAL -> "普通"
        CorgiMood.EXPECTING -> "期待"
        CorgiMood.WORRIED -> "担心"
        CorgiMood.SLEEPY -> "困倦"
        CorgiMood.SAD -> "失落"
    }
