package com.corgimemo.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 分组统计项
 *
 * @property group 日期分组（COUNTDOWN / COUNTUP / EXPIRED）
 * @property displayName 显示名称（"倒计时" / "正计时" / "已归档"）
 * @property count 该组日期数量
 * @property percentage 占比 (0.0-1.0)
 */
data class DateGroupStat(
    val group: DateGroup,
    val displayName: String,
    val count: Int,
    val percentage: Float
)

/**
 * 分类统计项
 *
 * @property categoryKey DB原始分类值（如 "BIRTHDAY" 或 "CUSTOM:xxx"）
 * @property displayName 显示名称
 * @property emoji 分类 emoji
 * @property color 分类颜色
 * @property count 该分类日期数量
 * @property percentage 占比 (0.0-1.0)
 */
data class DateCategoryStat(
    val categoryKey: String,
    val displayName: String,
    val emoji: String,
    val color: Color,
    val count: Int,
    val percentage: Float
)

/**
 * 日期统计数据聚合
 *
 * @property totalCount 日期总数
 * @property groupStats 分组统计列表
 * @property categoryStats 分类统计列表（仅含 count > 0 的分类，按数量降序）
 */
data class DateStatsData(
    val totalCount: Int = 0,
    val groupStats: List<DateGroupStat> = emptyList(),
    val categoryStats: List<DateCategoryStat> = emptyList()
)

/**
 * 日期数据统计 ViewModel
 *
 * 一次性加载全部日期（含已归档），计算：
 * - 分组统计：倒计时 / 正计时 / 已归档 的数量和占比
 * - 分类统计：各 DateCategory（含自定义）的数量和占比
 *
 * 分组逻辑复用 [SpecialDateViewModel.groupByDisplayDates]，确保与主页一致。
 */
@HiltViewModel
class DateStatsViewModel @Inject constructor(
    private val specialDateRepository: SpecialDateRepository
) : ViewModel() {

    /** 全部日期数据（含已归档） */
    private val _allDates = MutableStateFlow<List<SpecialDate>>(emptyList())

    /** 加载状态 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 是否完全无数据 */
    val isEmpty: StateFlow<Boolean> = _allDates
        .map { it.isEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 统计数据 */
    val stats: StateFlow<DateStatsData> = _allDates
        .map { dates -> computeStats(dates) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DateStatsData())

    init {
        loadStats()
    }

    /**
     * 加载全部日期数据
     */
    fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _allDates.value = specialDateRepository.getAllSpecialDatesBlocking()
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 计算统计数据
     *
     * @param dates 全部特殊日期
     * @return 分组统计 + 分类统计的聚合结果
     */
    private fun computeStats(dates: List<SpecialDate>): DateStatsData {
        if (dates.isEmpty()) return DateStatsData()

        val totalCount = dates.size

        // ========== 分组统计 ==========
        // 复用 SpecialDateViewModel.groupByDisplayDates 确保 repeatType 规则一致
        val grouped = SpecialDateViewModel.groupByDisplayDates(dates)
        val groupStats = listOf(
            GroupCount(DateGroup.COUNTDOWN, "倒计时", grouped[DateGroup.COUNTDOWN]?.size ?: 0),
            GroupCount(DateGroup.COUNTUP, "正计时", grouped[DateGroup.COUNTUP]?.size ?: 0),
            GroupCount(DateGroup.EXPIRED, "已归档", grouped[DateGroup.EXPIRED]?.size ?: 0)
        ).map { (group, name, count) ->
            DateGroupStat(
                group = group,
                displayName = name,
                count = count,
                percentage = if (totalCount > 0) count.toFloat() / totalCount else 0f
            )
        }

        // ========== 分类统计 ==========
        // 按 category 原始字符串分组计数
        val categoryCounts = dates.groupingBy { it.category }.eachCount()
        val categoryStats = categoryCounts
            .map { (categoryKey, count) ->
                val (displayName, emoji, color) = resolveCategoryDisplay(categoryKey)
                DateCategoryStat(
                    categoryKey = categoryKey,
                    displayName = displayName,
                    emoji = emoji,
                    color = color,
                    count = count,
                    percentage = if (totalCount > 0) count.toFloat() / totalCount else 0f
                )
            }
            .sortedByDescending { it.count }

        return DateStatsData(totalCount, groupStats, categoryStats)
    }

    /**
     * 解析分类的显示信息
     *
     * 处理逻辑：
     * 1. 匹配 DateCategory 枚举 → 使用枚举的 displayName / emoji / color
     * 2. 以 "CUSTOM:" 开头 → 提取名称，使用默认 emoji 和 OTHER 颜色
     * 3. 其他未知值 → 使用原始字符串，默认 emoji 和 OTHER 颜色
     *
     * @param categoryKey DB 中的原始分类字符串
     * @return Triple(displayName, emoji, color)
     */
    private fun resolveCategoryDisplay(categoryKey: String): Triple<String, String, Color> {
        return try {
            val cat = DateCategory.valueOf(categoryKey)
            Triple(cat.displayName, cat.emoji, cat.color)
        } catch (e: IllegalArgumentException) {
            if (categoryKey.startsWith("CUSTOM:")) {
                val name = categoryKey.removePrefix("CUSTOM:")
                Triple(name, "\uD83D\uDCC5", DateCategory.OTHER.color)
            } else {
                Triple(categoryKey, "\uD83D\uDCC5", DateCategory.OTHER.color)
            }
        }
    }

    /** 临时数据类，用于分组统计中间计算 */
    private data class GroupCount(val group: DateGroup, val name: String, val count: Int)
}
