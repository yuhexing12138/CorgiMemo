package com.corgimemo.app.animation

import java.util.Calendar
import java.util.TimeZone

/**
 * 柯基行为类型枚举
 * 定义柯基所有可能的自主行为
 */
enum class BehaviorType {
    /** 无特殊行为 */
    NONE,
    /** 打哈欠（空闲时） */
    YAWNING,
    /** 深夜入睡 */
    SLEEPING_NIGHT,
    /** 待办堆积担心 */
    WORRIED,
    /** 被忽略想念 */
    MISSED_YOU,
    /** 连续完成开心 */
    HAPPY_STREAK
}

/**
 * 柯基行为状态数据类
 *
 * @param type 行为类型
 * @param message 行为显示的消息
 * @param startTime 行为开始时间（毫秒）
 */
data class BehaviorState(
    val type: BehaviorType = BehaviorType.NONE,
    val message: String = "",
    val startTime: Long = 0L
)

/**
 * 柯基行为管理器
 * 统一管理所有自主行为的检测逻辑和配置
 */
object CorgiBehaviorManager {

    // ==================== 时间常量 ====================

    /** 空闲打哈欠阈值：10秒无操作 */
    const val IDLE_YAWN_THRESHOLD_MS = 10_000L

    /** 打哈欠冷却时间：2分钟内不再触发 */
    const val YAWN_COOLDOWN_MS = 120_000L

    /** 打哈欠持续时间：约2秒 */
    const val YAWN_DURATION_MS = 2_000L

    /** 深夜入睡起始小时：23点 */
    const val NIGHT_SLEEP_HOUR = 23

    /** 被忽略想念阈值：3天未打开 */
    const val MISSED_DAYS_THRESHOLD = 3

    /** 连续完成开心时间窗口：30秒 */
    const val HAPPY_STREAK_WINDOW_MS = 30_000L

    /** 连续完成开心所需数量：3个任务 */
    const val HAPPY_STREAK_COUNT = 3

    /** 连续完成开心持续时间：30秒 */
    const val HAPPY_STREAK_DURATION_MS = 30_000L

    /** 待办堆积阈值：超过10个待办 */
    const val WORRIED_TODO_THRESHOLD = 10

    /** 待办堆积超期比例阈值：超过50%超期 */
    const val WORRIED_OVERDUE_RATIO = 0.5f

    // ==================== 行为优先级 ====================

    /**
     * 获取行为优先级
     * 数字越大优先级越高
     *
     * @param type 行为类型
     * @return 优先级值
     */
    fun getBehaviorPriority(type: BehaviorType): Int {
        return when (type) {
            BehaviorType.MISSED_YOU -> 50      // 最高优先级
            BehaviorType.SLEEPING_NIGHT -> 40  // 次高
            BehaviorType.WORRIED -> 30         // 状态相关
            BehaviorType.HAPPY_STREAK -> 20    // 近期行为
            BehaviorType.YAWNING -> 10         // 最低优先级
            BehaviorType.NONE -> 0
        }
    }

    /**
     * 比较两个行为的优先级
     *
     * @param current 当前行为
     * @param new 新行为
     * @return 新行为是否应该覆盖当前行为
     */
    fun shouldOverrideBehavior(current: BehaviorType, new: BehaviorType): Boolean {
        return getBehaviorPriority(new) > getBehaviorPriority(current)
    }

    // ==================== 检测函数 ====================

    /**
     * 判断是否为深夜时间
     *
     * @param hour 当前小时（0-23）
     * @return 是否为 23 点后
     */
    fun isNightTime(hour: Int): Boolean {
        return hour >= NIGHT_SLEEP_HOUR
    }

    /**
     * 获取当前小时
     *
     * @param currentTime 当前时间戳
     * @return 小时（0-23）
     */
    fun getCurrentHour(currentTime: Long = System.currentTimeMillis()): Int {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    /**
     * 获取当前日期字符串
     *
     * @param currentTime 当前时间戳
     * @return 日期字符串（yyyy-MM-dd）
     */
    fun getCurrentDateString(currentTime: Long = System.currentTimeMillis()): String {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = currentTime
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    /**
     * 判断是否应该担心（待办堆积）
     *
     * @param totalTodoCount 总待办数（未完成的）
     * @param overdueCount 超期待办数
     * @return 是否应该显示担心状态
     */
    fun shouldWorry(totalTodoCount: Int, overdueCount: Int): Boolean {
        if (totalTodoCount <= WORRIED_TODO_THRESHOLD) return false
        if (totalTodoCount == 0) return false
        val overdueRatio = overdueCount.toFloat() / totalTodoCount.toFloat()
        return overdueRatio > WORRIED_OVERDUE_RATIO
    }

    /**
     * 判断是否被忽略（多天未打开）
     *
     * @param daysDiff 未打开的天数
     * @return 是否达到被忽略阈值
     */
    fun wasMissed(daysDiff: Int): Boolean {
        return daysDiff >= MISSED_DAYS_THRESHOLD
    }

    /**
     * 判断是否达成连续完成开心
     *
     * @param recentTimes 最近完成任务的时间戳列表
     * @param currentTime 当前时间戳
     * @return 是否在时间窗口内完成了足够数量的任务
     */
    fun hasHappyStreak(
        recentTimes: List<Long>,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        if (recentTimes.size < HAPPY_STREAK_COUNT) return false

        // 检查最近 N 个任务是否在时间窗口内
        val windowStart = currentTime - HAPPY_STREAK_WINDOW_MS
        val recentInWindow = recentTimes.takeLast(HAPPY_STREAK_COUNT)
            .all { it >= windowStart }

        return recentInWindow
    }

    /**
     * 判断是否可以打哈欠（考虑冷却时间）
     *
     * @param lastYawnTime 上次打哈欠时间
     * @param currentTime 当前时间
     * @return 是否可以打哈欠
     */
    fun canYawn(lastYawnTime: Long, currentTime: Long = System.currentTimeMillis()): Boolean {
        return (currentTime - lastYawnTime) >= YAWN_COOLDOWN_MS
    }

    /**
     * 判断是否达到空闲打哈欠条件
     *
     * @param lastInteractionTime 上次用户交互时间
     * @param lastYawnTime 上次打哈欠时间
     * @param currentTime 当前时间
     * @return 是否应该触发打哈欠
     */
    fun shouldYawn(
        lastInteractionTime: Long,
        lastYawnTime: Long,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        val idleDuration = currentTime - lastInteractionTime
        return idleDuration >= IDLE_YAWN_THRESHOLD_MS && canYawn(lastYawnTime, currentTime)
    }

    // ==================== 行为消息生成 ====================

    /**
     * 获取打哈欠时的显示消息
     *
     * @param corgiName 柯基名字
     * @return 消息字符串
     */
    fun getYawnMessage(corgiName: String? = null): String {
        val name = corgiName ?: "柯基"
        return "$name 打了个哈欠... Zzz 💤"
    }

    /**
     * 获取被忽略想念的欢迎回来消息
     *
     * @param corgiName 柯基名字
     * @param daysAway 离开的天数
     * @return 消息字符串
     */
    fun getMissedYouMessage(corgiName: String? = null, daysAway: Int): String {
        val name = corgiName ?: "柯基"
        return "柯基想你了！终于回来啦 🥺\n已经 $daysAway 天没见到你了..."
    }

    /**
     * 获取深夜入睡的问候语
     *
     * @param corgiName 柯基名字
     * @return 消息字符串
     */
    fun getSleepMessage(corgiName: String? = null): String {
        val name = corgiName ?: "柯基"
        return "$name 困了...但还是会陪着你 💤"
    }

    /**
     * 获取待办堆积担心的问候语
     *
     * @param corgiName 柯基名字
     * @param todoCount 待办数
     * @param overdueCount 超期数
     * @return 消息字符串
     */
    fun getWorriedMessage(corgiName: String? = null, todoCount: Int, overdueCount: Int): String {
        val name = corgiName ?: "柯基"
        return "$name 有点担心你哦，还有 $todoCount 个任务，$overdueCount 个超期了 😟"
    }

    /**
     * 获取连续完成开心的问候语
     *
     * @param corgiName 柯基名字
     * @return 消息字符串
     */
    fun getHappyStreakMessage(corgiName: String? = null): String {
        val name = corgiName ?: "柯基"
        return "太厉害了！$name 为你骄傲 🎉"
    }
}
