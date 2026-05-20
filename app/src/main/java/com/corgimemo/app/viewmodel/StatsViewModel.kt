package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 完成统计数据
 *
 * @property todayCount 今日完成数
 * @property weekCount 本周完成数
 * @property monthCount 本月完成数
 * @property consecutiveDays 连续完成天数
 * @property totalCompleted 累计完成数
 * @property weeklyTrend 最近7天的完成趋势（日期 -> 完成数）
 */
data class CompletionStats(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0,
    val consecutiveDays: Int = 0,
    val totalCompleted: Int = 0,
    val weeklyTrend: List<Pair<String, Int>> = emptyList()
)

/**
 * 统计页面 ViewModel
 * 管理完成统计数据的加载和计算
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    private val _stats = MutableStateFlow(CompletionStats())
    val stats: StateFlow<CompletionStats> = _stats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStats()
    }

    /**
     * 加载统计数据
     */
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true

            val currentTime = System.currentTimeMillis()
            val calendar = Calendar.getInstance()

            // 今日开始时间
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val todayStart = calendar.timeInMillis

            // 本周开始时间（周一）
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            val weekStart = calendar.timeInMillis

            // 本月开始时间
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = calendar.timeInMillis

            // 获取所有已完成的待办
            val allTodos = todoRepository.getAllTodos().first()
            val completedTodos = allTodos.filter { it.status == 1 && it.completedAt != null }

            // 今日完成数
            val todayCount = completedTodos.count { it.completedAt!! >= todayStart }

            // 本周完成数
            val weekCount = completedTodos.count { it.completedAt!! >= weekStart }

            // 本月完成数
            val monthCount = completedTodos.count { it.completedAt!! >= monthStart }

            // 累计完成数
            val totalCompleted = completedTodos.size

            // 连续完成天数
            val consecutiveDays = calculateConsecutiveDays(completedTodos, currentTime)

            // 最近7天的趋势
            val weeklyTrend = calculateWeeklyTrend(completedTodos, currentTime)

            _stats.value = CompletionStats(
                todayCount = todayCount,
                weekCount = weekCount,
                monthCount = monthCount,
                consecutiveDays = consecutiveDays,
                totalCompleted = totalCompleted,
                weeklyTrend = weeklyTrend
            )

            _isLoading.value = false
        }
    }

    /**
     * 计算连续完成天数
     *
     * @param completedTodos 已完成的待办列表
     * @param currentTime 当前时间
     * @return 连续完成天数
     */
    private fun calculateConsecutiveDays(
        completedTodos: List<com.corgimemo.app.data.model.TodoItem>,
        currentTime: Long
    ): Int {
        if (completedTodos.isEmpty()) return 0

        // 按完成日期去重
        val completedDates = completedTodos
            .mapNotNull { it.completedAt }
            .map { getStartOfDay(it) }
            .toSortedSet()
            .reversed()

        if (completedDates.isEmpty()) return 0

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        var todayStart = calendar.timeInMillis

        var consecutiveDays = 0
        val oneDay = TimeUnit.DAYS.toMillis(1)

        // 从今天开始往前数
        for (i in 0 until 365) {
            val targetDate = todayStart - i * oneDay
            if (completedDates.contains(targetDate)) {
                consecutiveDays++
            } else if (i > 0) {
                // 如果不是今天且没有完成，中断
                break
            }
        }

        return consecutiveDays
    }

    /**
     * 计算最近7天的完成趋势
     *
     * @param completedTodos 已完成的待办列表
     * @param currentTime 当前时间
     * @return 日期和完成数的列表（从6天前到今天）
     */
    private fun calculateWeeklyTrend(
        completedTodos: List<com.corgimemo.app.data.model.TodoItem>,
        currentTime: Long
    ): List<Pair<String, Int>> {
        val calendar = Calendar.getInstance()
        val oneDay = TimeUnit.DAYS.toMillis(1)

        calendar.timeInMillis = currentTime
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val todayStart = calendar.timeInMillis

        val trend = mutableListOf<Pair<String, Int>>()
        val dayNames = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

        // 从6天前到今天
        for (i in 6 downTo 0) {
            val dayStart = todayStart - i * oneDay
            val dayEnd = dayStart + oneDay

            val count = completedTodos.count {
                val completedAt = it.completedAt ?: return@count false
                completedAt >= dayStart && completedAt < dayEnd
            }

            calendar.timeInMillis = dayStart
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
            val label = if (i == 0) "今天" else dayNames[dayOfWeek]

            trend.add(label to count)
        }

        return trend
    }

    /**
     * 获取某一天的开始时间
     *
     * @param timestamp 时间戳
     * @return 当天开始的时间戳
     */
    private fun getStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
