package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * 完成统计数据
 *
 * @property todayCount 今日完成数
 * @property weekCount 本周完成数
 * @property monthCount 本月完成数
 * @property consecutiveDays 连续完成天数
 * @property totalCompleted 累计完成数
 * @property weeklyTrend 最近7天的完成趋势（日期 -> 完成数）
 * @property categoryStats 各分类完成率统计
 * @property timePeriodStats 各时段完成数量统计
 * @property bestTimePeriod 最佳效率时段
 * @property predictedCompletion 预测本月完成数
 * @property predictedTotal 预测本月总任务数
 * @property predictionRate 预测完成率 (0.0-1.0)
 * @property needsEncouragement 是否需要鼓励提示（<80%）
 */
data class CompletionStats(
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val monthCount: Int = 0,
    val consecutiveDays: Int = 0,
    val totalCompleted: Int = 0,
    val weeklyTrend: List<Pair<String, Int>> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    val timePeriodStats: List<TimePeriodStat> = emptyList(),
    val bestTimePeriod: TimePeriodStat? = null,
    val predictedCompletion: Int = 0,
    val predictedTotal: Int = 0,
    val predictionRate: Float = 0f,
    val needsEncouragement: Boolean = false
)

/**
 * 分类统计数据
 *
 * @property categoryId 分类 ID
 * @property categoryName 分类名称
 * @property categoryType 分类类型（0=学习,1=工作,2=生活,3=运动,4=自定义）
 * @property completedCount 已完成任务数
 * @property totalCount 总任务数
 * @property completionRate 完成率 (0.0-1.0)
 */
data class CategoryStat(
    val categoryId: Long,
    val categoryName: String,
    val categoryType: Int,
    val completedCount: Int,
    val totalCount: Int,
    val completionRate: Float
)

/**
 * 时段统计数据
 *
 * @property periodId 时段 ID (0-5)
 * @property periodLabel 时段标签（如"9-12点"）
 * @property startTime 开始时间（小时，6-22）
 * @property endTime 结束时间（小时，9-24）
 * @property completedCount 该时段完成任务数
 * @property isBest 是否为最佳效率时段
 */
data class TimePeriodStat(
    val periodId: Int,
    val periodLabel: String,
    val startTime: Int,
    val endTime: Int,
    val completedCount: Int,
    val isBest: Boolean = false
)

/**
 * 统计页面 ViewModel
 * 管理完成统计数据的加载和计算
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository
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
            // 使用 try/finally 保证 _isLoading 一定被关闭，
            // 避免业务计算中途抛异常时 loading 状态永远卡在 true
            // （与灵感页 InspirationViewModel 同类 bug 的预防性修复）
            try {
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

                // 获取所有待办和分类
                val allTodos = todoRepository.getAllTodos().first()
                val categories = categoryRepository.getAllCategoriesList()
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

                // 分类统计
                val categoryStats = calculateCategoryStats(allTodos, categories)

                // 时段统计
                val (timePeriodStats, bestTimePeriod) = calculateTimePeriodStats(completedTodos)

                // 趋势预测
                val (predictedCompletion, predictedTotal, predictionRate) =
                    calculatePrediction(completedTodos, monthCount)
                val needsEncouragement = predictionRate < 0.8f

                _stats.value = CompletionStats(
                    todayCount = todayCount,
                    weekCount = weekCount,
                    monthCount = monthCount,
                    consecutiveDays = consecutiveDays,
                    totalCompleted = totalCompleted,
                    weeklyTrend = weeklyTrend,
                    categoryStats = categoryStats,
                    timePeriodStats = timePeriodStats,
                    bestTimePeriod = bestTimePeriod,
                    predictedCompletion = predictedCompletion,
                    predictedTotal = predictedTotal,
                    predictionRate = predictionRate,
                    needsEncouragement = needsEncouragement
                )
            } finally {
                _isLoading.value = false
            }
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

    /**
     * 计算各分类完成率
     *
     * @param allTodos 所有待办任务
     * @param categories 所有分类列表
     * @return 各分类统计数据列表
     */
    private suspend fun calculateCategoryStats(
        allTodos: List<TodoItem>,
        categories: List<Category>
    ): List<CategoryStat> {
        return categories.map { category ->
            val categoryTodos = allTodos.filter { it.categoryId == category.id }
            val completedCount = categoryTodos.count { it.status == 1 }
            val totalCount = categoryTodos.size
            val rate = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f

            CategoryStat(
                categoryId = category.id,
                categoryName = category.name,
                categoryType = category.type,
                completedCount = completedCount,
                totalCount = totalCount,
                completionRate = rate
            )
        }.sortedByDescending { it.completionRate }
    }

    /**
     * 计算各时段完成数量并找出最佳时段
     *
     * @param completedTodos 已完成的待办列表
     * @return Pair(时段统计列表, 最佳时段)
     */
    private fun calculateTimePeriodStats(
        completedTodos: List<TodoItem>
    ): Pair<List<TimePeriodStat>, TimePeriodStat?> {
        // 定义 6 个时段
        val periods = listOf(
            TimePeriodStat(0, "6-9点", 6, 9, 0),
            TimePeriodStat(1, "9-12点", 9, 12, 0),
            TimePeriodStat(2, "12-14点", 12, 14, 0),
            TimePeriodStat(3, "14-18点", 14, 18, 0),
            TimePeriodStat(4, "18-22点", 18, 22, 0),
            TimePeriodStat(5, "22-24点", 22, 24, 0)
        )

        // 统计每个时段的完成数量
        val periodCounts = completedTodos.mapNotNull { todo ->
            val completedAt = todo.completedAt ?: return@mapNotNull null
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = completedAt
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            when (hour) {
                in 6..8 -> 0
                in 9..11 -> 1
                in 12..13 -> 2
                in 14..17 -> 3
                in 18..21 -> 4
                in 22..23 -> 5
                else -> null
            }
        }.groupingBy { it }.eachCount()

        // 更新每个时段的完成数量
        val statsWithCounts = periods.map { period ->
            period.copy(completedCount = periodCounts[period.periodId] ?: 0)
        }

        // 找出最佳时段（完成数最多）
        val bestPeriod = statsWithCounts.maxByOrNull { it.completedCount }
            ?.takeIf { it.completedCount > 0 }?.copy(isBest = true)

        // 返回所有时段（最佳时段标记 isBest=true）
        val finalStats = statsWithCounts.map { stat ->
            if (stat.periodId == bestPeriod?.periodId) stat.copy(isBest = true) else stat
        }

        return Pair(finalStats, bestPeriod)
    }

    /**
     * 计算月度趋势预测
     *
     * @param completedTodos 已完成的待办列表
     * @param currentMonthCount 当前月已完成数
     * @return Triple(预测完成数, 预测总数, 预测率)
     */
    private fun calculatePrediction(
        completedTodos: List<TodoItem>,
        currentMonthCount: Int
    ): Triple<Int, Int, Float> {
        val calendar = Calendar.getInstance()
        val today = calendar.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val remainingDays = daysInMonth - today

        // 计算过去 4 周（28天）的日均完成数
        val fourWeeksAgo = System.currentTimeMillis() - 28L * 24 * 60 * 60 * 1000
        val recentCompleted = completedTodos.filter {
            (it.completedAt ?: 0) >= fourWeeksAgo
        }
        val dailyAverage = if (recentCompleted.isNotEmpty()) {
            recentCompleted.size / 28f
        } else {
            0f
        }

        // 预测值
        val predictedCompletion = (dailyAverage * remainingDays).toInt() + currentMonthCount
        val predictedTotal = (dailyAverage * daysInMonth).toInt().coerceAtLeast(1)
        val rate = predictedCompletion.toFloat() / predictedTotal

        return Triple(predictedCompletion.coerceAtMost(predictedTotal), predictedTotal, rate.coerceIn(0f, 1f))
    }

    /**
     * 根据小时获取时段 ID
     *
     * @param hour 小时 (0-23)
     * @return 时段 ID (0-5)，不在统计范围内返回 -1
     */
    private fun getTimePeriod(hour: Int): Int {
        return when (hour) {
            in 6..8 -> 0   // 6-9点
            in 9..11 -> 1  // 9-12点
            in 12..13 -> 2 // 12-14点
            in 14..17 -> 3 // 14-18点
            in 18..21 -> 4 // 18-22点
            in 22..23 -> 5 // 22-24点
            else -> -1     // 不计入统计
        }
    }
}
