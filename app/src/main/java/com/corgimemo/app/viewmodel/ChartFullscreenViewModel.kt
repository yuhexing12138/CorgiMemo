package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.data.stats.ChartRange
import com.corgimemo.app.data.stats.DailyWordCount
import com.corgimemo.app.data.stats.WordCountChartData
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 图表横屏全屏 ViewModel
 *
 * 固定展示最近 30 天的数据。通过 [chartType] 参数决定：
 * - 灵感图表（line / bar）：渲染字数折线图或柱状图
 * - 待办图表（todo_line / todo_bar）：渲染完成数折线图或柱状图
 *
 * 灵感字数口径：title + content + tags 拼接后去除所有空白字符（中英文均按 1 字符）。
 * 包含已归档的灵感。
 *
 * @param savedStateHandle 用于接收路由参数 chartType
 * @param inspirationRepository 灵感仓库，注入获取全量灵感
 * @param todoRepository 待办仓库，注入获取全量待办
 * @param categoryRepository 分类仓库，注入获取分类列表
 */
@HiltViewModel
class ChartFullscreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inspirationRepository: InspirationRepository,
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    /** 图表类型（line / bar），从路由参数读取 */
    val chartType: String = savedStateHandle.get<String>("chartType") ?: "line"

    /** 全部灵感数据 */
    private val _allInspirations = MutableStateFlow<List<Inspiration>>(emptyList())

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 30 天图表数据 */
    val chartData: StateFlow<WordCountChartData> = _allInspirations
        .map { inspirations -> computeChartData(inspirations) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WordCountChartData(ChartRange.THIRTY_DAYS, emptyList())
        )

    /** 当前累计字数（30 天累计最后值） */
    val currentCumulativeChars: StateFlow<Int> = chartData
        .map { it.currentCumulativeChars }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 待办 30 天图表数据（折线图/柱状图） */
    private val _todoChartData = MutableStateFlow(WordCountChartData(ChartRange.THIRTY_DAYS, emptyList()))
    val todoChartData: StateFlow<WordCountChartData> = _todoChartData.asStateFlow()

    /** 待办 30 天分类统计 */
    private val _todoCategoryStats = MutableStateFlow<List<CategoryStat>>(emptyList())
    val todoCategoryStats: StateFlow<List<CategoryStat>> = _todoCategoryStats.asStateFlow()

    /** 待办 30 天完成率 */
    private val _todoMonthRate = MutableStateFlow(0f)
    val todoMonthRate: StateFlow<Float> = _todoMonthRate.asStateFlow()

    /** 待办累计完成数 */
    private val _todoTotalCompleted = MutableStateFlow(0)
    val todoTotalCompleted: StateFlow<Int> = _todoTotalCompleted.asStateFlow()

    init {
        loadStats()
    }

    /**
     * 加载统计数据，根据图表类型分发到灵感或待办
     */
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (chartType.startsWith("todo_")) {
                    loadTodoStats()
                } else {
                    // 阻塞加载全量灵感以计算 30 天累计
                    _allInspirations.value = inspirationRepository.getAllInspirationsBlocking()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 计算 30 天图表数据集
     *
     * @param inspirations 全部灵感
     * @param today 锚定日期（默认今天）
     */
    private fun computeChartData(
        inspirations: List<Inspiration>,
        today: LocalDate = LocalDate.now()
    ): WordCountChartData {
        val range = ChartRange.THIRTY_DAYS
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        val groupedByDay = inspirations.groupBy { ins ->
            Instant.ofEpochMilli(ins.createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        var cumulative = 0
        val points = days.map { date ->
            val daily = groupedByDay[date]?.sumOf { ins ->
                InspirationTextUtils.countInspirationChars(ins)
            } ?: 0
            cumulative += daily
            DailyWordCount(date, daily, cumulative)
        }
        return WordCountChartData(range, points)
    }

    /**
     * 加载待办统计数据（30 天）
     */
    private suspend fun loadTodoStats() {
        val allTodos = todoRepository.getAllTodos().first()
        val categories = categoryRepository.getAllCategoriesList()
        val completedTodos = allTodos.filter { it.status == 1 && it.completedAt != null }

        // 30 天趋势
        val range = ChartRange.THIRTY_DAYS
        val today = LocalDate.now()
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        val oneDay = 24L * 60 * 60 * 1000

        var cumulative = 0
        val points = days.map { date ->
            val dayStartMs = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEndMs = dayStartMs + oneDay
            val daily = completedTodos.count {
                it.completedAt != null && it.completedAt >= dayStartMs && it.completedAt < dayEndMs
            }
            cumulative += daily
            DailyWordCount(date, daily, cumulative)
        }
        _todoChartData.value = WordCountChartData(range, points)

        // 分类统计
        val catStats = categories.map { category ->
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
        _todoCategoryStats.value = catStats

        // 本月完成率
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val monthStart = calendar.timeInMillis
        val monthCount = completedTodos.count { it.completedAt!! >= monthStart }
        val monthTotal = allTodos.size
        _todoMonthRate.value = if (monthTotal > 0) monthCount.toFloat() / monthTotal else 0f

        // 累计完成数
        _todoTotalCompleted.value = completedTodos.size
    }
}
