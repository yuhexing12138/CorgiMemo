package com.corgimemo.app.animation

import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.weather.WeatherInfo
import com.corgimemo.app.data.weather.WeatherManager
import java.util.Calendar

/**
 * 问候语上下文数据类
 * 包含生成动态问候语所需的所有信息
 *
 * @property hour 当前小时（0-23）
 * @property isWeekend 是否为周末
 * @property pendingTodoCount 今日待办数量
 * @property urgentTodoTitle 最紧急任务名称（可为 null）
 * @property userName 用户名（可为 null，使用柯基名字）
 * @property weatherInfo 天气信息（可为 null）
 */
data class GreetingContext(
    val hour: Int,
    val isWeekend: Boolean,
    val pendingTodoCount: Int,
    val urgentTodoTitle: String? = null,
    val userName: String? = null,
    val weatherInfo: WeatherInfo? = null
)

/**
 * 时间段索引枚举
 * 用于缓存判断时间段是否变化
 */
enum class TimeSlot {
    EARLY_MORNING,   // 6:00 - 7:00
    MORNING,         // 7:00 - 9:00
    LATE_MORNING,    // 9:00 - 12:00
    NOON,            // 12:00 - 14:00
    AFTERNOON,       // 14:00 - 18:00
    EVENING,         // 18:00 - 22:00
    LATE_NIGHT       // 22:00 - 6:00
}

/**
 * 动态问候语生成器
 * 根据时间段、待办状态、天气等信息生成个性化问候语
 */
object DynamicGreetingManager {

    /**
     * 根据小时获取时间段问候前缀
     *
     * @param hour 当前小时（0-23）
     * @return 问候前缀字符串
     */
    fun getTimeGreeting(hour: Int): String {
        return when (hour) {
            in 6..7 -> "早安 🌅"
            in 7..9 -> "上午好 ☀️"
            in 9..12 -> "中午好 🌤️"
            in 12..14 -> "下午好 🌞"
            in 14..18 -> "傍晚好 🌇"
            in 18..22 -> "晚上好 🌙"
            else -> "夜深了 🌚"
        }
    }

    /**
     * 根据小时获取时间段索引
     * 用于缓存判断
     *
     * @param hour 当前小时（0-23）
     * @return 时间段枚举
     */
    fun getTimeSlot(hour: Int): TimeSlot {
        return when (hour) {
            in 6..7 -> TimeSlot.EARLY_MORNING
            in 7..9 -> TimeSlot.MORNING
            in 9..12 -> TimeSlot.LATE_MORNING
            in 12..14 -> TimeSlot.NOON
            in 14..18 -> TimeSlot.AFTERNOON
            in 18..22 -> TimeSlot.EVENING
            else -> TimeSlot.LATE_NIGHT
        }
    }

    /**
     * 判断给定时间戳是否为今天
     *
     * @param timestamp 要检查的时间戳
     * @param currentTime 当前时间戳
     * @return 是否为今天
     */
    fun isToday(timestamp: Long, currentTime: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp }
        val cal2 = Calendar.getInstance().apply { timeInMillis = currentTime }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * 获取今天开始的时间戳（00:00:00）
     *
     * @param currentTime 当前时间戳
     * @return 今天开始的时间戳
     */
    fun getTodayStart(currentTime: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * 获取今天结束的时间戳（23:59:59）
     *
     * @param currentTime 当前时间戳
     * @return 今天结束的时间戳
     */
    fun getTodayEnd(currentTime: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    /**
     * 从待办列表中找出最紧急的任务
     * 判断优先级（从高到低）：
     * 1. 截止日期今天且高优先级（priority=3）
     * 2. 截止日期今天
     * 3. 高优先级（priority=3）
     * 4. 中优先级（priority=2）
     * 5. 创建时间最早的待办
     *
     * @param todos 所有待办列表
     * @param currentTime 当前时间戳
     * @return 最紧急的待办项，如果没有待办返回 null
     */
    fun getMostUrgentTodo(todos: List<TodoItem>, currentTime: Long): TodoItem? {
        val pendingTodos = todos.filter { it.status == 0 }
        if (pendingTodos.isEmpty()) return null

        val todayStart = getTodayStart(currentTime)
        val todayEnd = getTodayEnd(currentTime)

        return pendingTodos
            .sortedWith(
                compareByDescending<TodoItem> { todo ->
                    val dueDate = todo.dueDate
                    val isDueToday = dueDate != null && dueDate >= todayStart && dueDate <= todayEnd

                    when {
                        isDueToday && todo.priority == 3 -> 4
                        isDueToday -> 3
                        todo.priority == 3 -> 2
                        todo.priority == 2 -> 1
                        else -> 0
                    }
                }.thenBy { it.createdAt }
            )
            .firstOrNull()
    }

    /**
     * 生成动态问候语
     * 根据上下文信息组合生成个性化问候语
     *
     * @param context 问候语上下文
     * @return 生成的问候语字符串
     */
    fun generateGreeting(context: GreetingContext): String {
        val timeGreeting = getTimeGreeting(context.hour)

        // 没有待办的情况
        if (context.pendingTodoCount == 0) {
            return buildNoTodoGreeting(timeGreeting, context)
        }

        // 有代待办的情况
        return buildWithTodoGreeting(timeGreeting, context)
    }

    /**
     * 构建没有待办时的问候语
     */
    private fun buildNoTodoGreeting(timeGreeting: String, context: GreetingContext): String {
        val weatherMessage = context.weatherInfo?.let { WeatherManager.getWeatherMessage(it) } ?: ""

        return if (context.isWeekend) {
            if (weatherMessage.isNotEmpty()) {
                "$timeGreeting！周末愉快🎉 今天没有待办，享受轻松时光吧！\n$weatherMessage"
            } else {
                "$timeGreeting！周末愉快🎉 今天没有待办，享受轻松时光吧！"
            }
        } else {
            if (weatherMessage.isNotEmpty()) {
                "$timeGreeting！今天没有待办，享受轻松时光吧！\n$weatherMessage"
            } else {
                "$timeGreeting！今天没有待办，享受轻松时光吧！"
            }
        }
    }

    /**
     * 构建有待办时的问候语
     */
    private fun buildWithTodoGreeting(timeGreeting: String, context: GreetingContext): String {
        val weatherMessage = context.weatherInfo?.let { WeatherManager.getWeatherMessage(it) } ?: ""
        val todoCount = context.pendingTodoCount
        val urgentTitle = context.urgentTodoTitle

        return if (context.isWeekend) {
            // 周末模板
            val mainMessage = if (urgentTitle != null) {
                "$timeGreeting！周末愉快🎉 今天有 $todoCount 个待办，最紧急的是「$urgentTitle」，不用太紧张~"
            } else {
                "$timeGreeting！周末愉快🎉 今天有 $todoCount 个待办，不用太紧张~"
            }

            if (weatherMessage.isNotEmpty()) {
                "$mainMessage\n$weatherMessage"
            } else {
                mainMessage
            }
        } else {
            // 工作日模板
            val mainMessage = if (urgentTitle != null) {
                "$timeGreeting！今天有 $todoCount 个待办，最紧急的是「$urgentTitle」，加油💪"
            } else {
                "$timeGreeting！今天有 $todoCount 个待办，加油💪"
            }

            if (weatherMessage.isNotEmpty()) {
                "$mainMessage\n$weatherMessage"
            } else {
                mainMessage
            }
        }
    }

    /**
     * 缓存数据结构
     *
     * @property greeting 缓存的问候语
     * @property timeSlot 缓存时的时间段
     * @property timestamp 缓存时间戳
     */
    data class CachedGreeting(
        val greeting: String,
        val timeSlot: TimeSlot,
        val timestamp: Long
    )

    /** 缓存时长：30 分钟（毫秒） */
    const val CACHE_DURATION_MS = 30 * 60 * 1000L

    /**
     * 判断是否需要刷新问候语
     * 刷新条件：
     * 1. 没有缓存
     * 2. 时间段变化
     * 3. 缓存超过 30 分钟
     *
     * @param cached 缓存的问候语（可为 null）
     * @param currentTime 当前时间戳
     * @return 是否需要刷新
     */
    fun shouldRefreshGreeting(cached: CachedGreeting?, currentTime: Long): Boolean {
        if (cached == null) return true

        val calendar = Calendar.getInstance().apply { timeInMillis = currentTime }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentTimeSlot = getTimeSlot(hour)

        if (currentTimeSlot != cached.timeSlot) return true

        if (currentTime - cached.timestamp > CACHE_DURATION_MS) return true

        return false
    }
}
