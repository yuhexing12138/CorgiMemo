package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.stats.ChartRange
import com.corgimemo.app.data.stats.DailyWordCount
import com.corgimemo.app.data.stats.WordCountChartData
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 灵感图表横屏全屏 ViewModel
 *
 * 固定展示最近 30 天的字数数据。通过 [chartType] 参数决定渲染折线图（line）
 * 还是柱状图（bar）。
 *
 * 字数口径：title + content + tags 拼接后去除所有空白字符（中英文均按 1 字符）。
 * 包含已归档的灵感。
 *
 * @param savedStateHandle 用于接收路由参数 chartType
 * @param inspirationRepository 灵感仓库，注入获取全量灵感
 */
@HiltViewModel
class ChartFullscreenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inspirationRepository: InspirationRepository
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

    init {
        loadStats()
    }

    /**
     * 加载全部灵感数据
     */
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 阻塞加载全量灵感以计算 30 天累计
                _allInspirations.value = inspirationRepository.getAllInspirationsBlocking()
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
}
