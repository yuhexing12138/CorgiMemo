package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.data.stats.ChartRange
import com.corgimemo.app.data.stats.DailyWordCount
import com.corgimemo.app.data.stats.WordCountChartData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar
import javax.inject.Inject

/**
 * 完成统计数据
 *
 * @property monthCount 本月完成数
 * @property totalCompleted 累计完成数
 * @property weeklyTrend 最近7天的完成趋势（日期 -> 完成数）
 * @property categoryStats 各分类完成率统计
 */
data class CompletionStats(
    val monthCount: Int = 0,
    val totalCompleted: Int = 0,
    val weeklyTrend: List<Pair<String, Int>> = emptyList(),
    val categoryStats: List<CategoryStat> = emptyList(),
    /** 30 天分类完成率统计 */
    val categoryStats30d: List<CategoryStat> = emptyList(),
    /** 30 天完成率 */
    val completionRate30d: Float = 0f
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

    /** 7 天累计完成数（折线图） */
    val todoLineChartData: StateFlow<WordCountChartData> = _stats
        .map { stats -> computeTodoLineChartData(stats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            WordCountChartData(ChartRange.SEVEN_DAYS, emptyList()))

    /** 7 天每日完成数（柱状图） */
    val todoBarChartData: StateFlow<WordCountChartData> = _stats
        .map { stats -> computeTodoBarChartData(stats) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            WordCountChartData(ChartRange.SEVEN_DAYS, emptyList()))

    /** 本月完成率 */
    val monthCompletionRate: StateFlow<Float> = _stats
        .map { stats ->
            val monthTotal = stats.categoryStats.sumOf { it.totalCount }
            if (monthTotal > 0) stats.monthCount.toFloat() / monthTotal else 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    /** 累计完成数 */
    val totalCompleted: StateFlow<Int> = _stats
        .map { it.totalCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 是否完全无数据 */
    val isEmpty: StateFlow<Boolean> = _stats
        .map { it.totalCompleted == 0 && it.categoryStats.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 30 天分类完成率统计 */
    val categoryStats30d: StateFlow<List<CategoryStat>> = _stats
        .map { it.categoryStats30d }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 30 天完成率 */
    val completionRate30d: StateFlow<Float> = _stats
        .map { it.completionRate30d }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0f)

    init {
        loadStats()
    }

    /**
     * 加载统计数据
     */
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentTime = System.currentTimeMillis()
                val calendar = Calendar.getInstance()

                // 本月开始时间
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = calendar.timeInMillis

                val allTodos = todoRepository.getAllTodos().first()
                val categories = categoryRepository.getAllCategoriesList()
                val completedTodos = allTodos.filter { it.status == 1 && it.completedAt != null }

                val monthCount = completedTodos.count { it.completedAt!! >= monthStart }
                val totalCompleted = completedTodos.size
                val weeklyTrend = calculateWeeklyTrend(completedTodos, currentTime)
                val categoryStats = calculateCategoryStats(allTodos, categories)

                // 30 天分类统计：仅统计最近 30 天内完成的待办
                val thirtyDaysAgo = currentTime - 30L * 24 * 60 * 60 * 1000
                val categoryStats30d = calculateCategoryStats30d(allTodos, categories, thirtyDaysAgo)

                // 30 天完成率
                val todosLast30d = allTodos.filter { it.createdAt >= thirtyDaysAgo }
                val completedLast30d = todosLast30d.count { it.status == 1 }
                val totalLast30d = todosLast30d.size
                val completionRate30d = if (totalLast30d > 0) completedLast30d.toFloat() / totalLast30d else 0f

                _stats.value = CompletionStats(
                    monthCount = monthCount,
                    totalCompleted = totalCompleted,
                    weeklyTrend = weeklyTrend,
                    categoryStats = categoryStats,
                    categoryStats30d = categoryStats30d,
                    completionRate30d = completionRate30d
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 计算最近7天的完成趋势
     *
     * @param completedTodos 已完成的待办列表
     * @param currentTime 当前时间
     * @return 日期和完成数的列表（从6天前到今天）
     */
    private fun calculateWeeklyTrend(
        completedTodos: List<TodoItem>,
        currentTime: Long
    ): List<Pair<String, Int>> {
        val calendar = Calendar.getInstance()
        val oneDay = 24L * 60 * 60 * 1000

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
     * 计算 30 天内各分类完成率
     *
     * @param allTodos 所有待办任务
     * @param categories 所有分类列表
     * @param thirtyDaysAgo 30 天前的时间戳
     * @return 各分类统计数据列表（仅统计最近 30 天的待办）
     */
    private suspend fun calculateCategoryStats30d(
        allTodos: List<TodoItem>,
        categories: List<Category>,
        thirtyDaysAgo: Long
    ): List<CategoryStat> {
        return categories.map { category ->
            val categoryTodos = allTodos.filter { it.categoryId == category.id && it.createdAt >= thirtyDaysAgo }
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
     * 将 weeklyTrend 转换为 7 天累计完成数折线图数据
     */
    private fun computeTodoLineChartData(stats: CompletionStats): WordCountChartData {
        val range = ChartRange.SEVEN_DAYS
        val today = LocalDate.now()
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        var cumulative = 0
        val points = days.mapIndexed { index, date ->
            val daily = stats.weeklyTrend.getOrNull(index)?.second ?: 0
            cumulative += daily
            DailyWordCount(date, daily, cumulative)
        }
        return WordCountChartData(range, points)
    }

    /**
     * 将 weeklyTrend 转换为 7 天每日完成数柱状图数据
     */
    private fun computeTodoBarChartData(stats: CompletionStats): WordCountChartData {
        val range = ChartRange.SEVEN_DAYS
        val today = LocalDate.now()
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        val points = days.mapIndexed { index, date ->
            val daily = stats.weeklyTrend.getOrNull(index)?.second ?: 0
            DailyWordCount(date, daily, daily)
        }
        return WordCountChartData(range, points)
    }
}
