package com.corgimemo.app.viewmodel

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 灵感字数统计 ViewModel
 *
 * 集中管理「累计总字数折线图」与「每日输入字数柱状图」两个图表的数据与范围状态。
 * 数据来源：InspirationRepository.getAllInspirations()，按 createdAt 日期分组。
 *
 * 字数口径：title + content + tags 拼接后去除所有空白字符（中英文均按 1 字符）。
 * 包含已归档的灵感。
 */
@HiltViewModel
class InspirationStatsViewModel @Inject constructor(
    private val inspirationRepository: InspirationRepository
) : ViewModel() {

    /** 全部灵感数据 */
    private val _allInspirations = MutableStateFlow<List<Inspiration>>(emptyList())

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 折线图范围（独立） */
    private val _lineRange = MutableStateFlow(ChartRange.SEVEN_DAYS)
    val lineRange: StateFlow<ChartRange> = _lineRange.asStateFlow()

    /** 柱状图范围（独立） */
    private val _barRange = MutableStateFlow(ChartRange.SEVEN_DAYS)
    val barRange: StateFlow<ChartRange> = _barRange.asStateFlow()

    /** 折线图数据（累计总字数） */
    val lineChartData: StateFlow<WordCountChartData> =
        combine(_allInspirations, _lineRange) { ins, range ->
            computeChartData(range, ins)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WordCountChartData(ChartRange.SEVEN_DAYS, emptyList())
        )

    /** 柱状图数据（每日输入字数） */
    val barChartData: StateFlow<WordCountChartData> =
        combine(_allInspirations, _barRange) { ins, range ->
            computeChartData(range, ins)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WordCountChartData(ChartRange.SEVEN_DAYS, emptyList())
        )

    /** 当前累计字数（最近 7 天的累计最后值） */
    val currentCumulativeChars: StateFlow<Int> = lineChartData
        .map { it.currentCumulativeChars }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 是否完全无数据 */
    val isEmpty: StateFlow<Boolean> = _allInspirations
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

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
                // 通过阻塞方式一次性加载全量灵感，供图表统计使用
                _allInspirations.value = inspirationRepository.getAllInspirationsBlocking()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换折线图范围（7 ↔ 30）
     */
    fun toggleLineRange() {
        _lineRange.value = if (_lineRange.value == ChartRange.SEVEN_DAYS) {
            ChartRange.THIRTY_DAYS
        } else {
            ChartRange.SEVEN_DAYS
        }
    }

    /**
     * 切换柱状图范围（7 ↔ 30）
     */
    fun toggleBarRange() {
        _barRange.value = if (_barRange.value == ChartRange.SEVEN_DAYS) {
            ChartRange.THIRTY_DAYS
        } else {
            ChartRange.SEVEN_DAYS
        }
    }

    /**
     * 计算图表数据集
     *
     * @param range 时间范围
     * @param inspirations 全部灵感
     * @param today 锚定日期（默认今天，便于测试）
     * @return 时间范围对应的每日字数与累计字数
     */
    private fun computeChartData(
        range: ChartRange,
        inspirations: List<Inspiration>,
        today: LocalDate = LocalDate.now()
    ): WordCountChartData {
        // 按范围生成日期序列（从最早到今天）
        val days = (0 until range.days).map { offset ->
            today.minusDays((range.days - 1 - offset).toLong())
        }
        // 将灵感按 createdAt 对应的本地日期分组
        val groupedByDay = inspirations.groupBy { ins ->
            Instant.ofEpochMilli(ins.createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
        // 累计统计每日字数
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
