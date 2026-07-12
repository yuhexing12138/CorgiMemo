package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.corgimemo.app.util.TagUtils
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SpecialDateViewModel @Inject constructor(
    private val repository: SpecialDateRepository,
    private val cardRelationRepository: CardRelationRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _editingDate = MutableStateFlow<SpecialDate?>(null)
    val editingDate: StateFlow<SpecialDate?> = _editingDate

    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** 数据是否已初始化完成（用于避免冷启动时从空列表闪烁到有数据） */
    private val _isDataInitialized = MutableStateFlow(false)
    val isDataInitialized: StateFlow<Boolean> = _isDataInitialized

    /** 左滑展开互斥（同时仅一张卡片展开） */
    private val _expandedDateId = MutableStateFlow<Long?>(null)
    val expandedDateId: StateFlow<Long?> = _expandedDateId

    /** 当前已置顶日期 id（仅一个） */
    private val _pinnedDateId = MutableStateFlow<Long?>(null)
    val pinnedDateId: StateFlow<Long?> = _pinnedDateId

    /** Snackbar 撤回缓存（归档时的 SpecialDate 快照） */
    private val _pendingArchive = MutableStateFlow<SpecialDate?>(null)
    val pendingArchive: StateFlow<SpecialDate?> = _pendingArchive

    /** 原始数据流（在 stateIn 之前通过 onEach 监听初始化状态） */
    val specialDates: StateFlow<List<SpecialDate>> = repository.allDates
        .onEach {
            if (!_isDataInitialized.value) {
                _isDataInitialized.value = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 三组分类后的展示数据 */
    val groupedDates: StateFlow<Map<DateGroup, List<DisplayDate>>> =
        combine(specialDates, _searchQuery) { dates, query ->
            // 1. 只取未归档
            val active = dates.filter { !it.isArchived }
            // 2. 搜索过滤
            if (query.isBlank()) {
                active
            } else {
                active.filter { it.title.contains(query, ignoreCase = true) }
            }
        }.map { dates ->
            groupByDisplayDates(dates)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setEditingDate(date: SpecialDate?) {
        _editingDate.value = date
        if (date != null) {
            viewModelScope.launch {
                _relations.value = cardRelationRepository.getRelationsBlocking("date", date.id)
            }
        } else {
            _relations.value = emptyList()
        }
    }

    /**
     * 根据ID获取特殊日期（用于编辑模式加载）
     * @param id 日期ID
     * @return 日期实体，不存在返回null
     */
    suspend fun getDateById(id: Long): SpecialDate? =
        repository.getById(id)

    fun saveDate(
        id: Long?,
        title: String,
        targetDate: Long,
        category: String,
        countMode: Int,
        repeatType: Int,
        reminderDays: Int,
        content: String,
        tags: List<String>,
        imagePaths: List<String>
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val tagsJson = encodeTags(tags)
            val pathsJson = encodePaths(imagePaths)
            if (id == null) {
                val newDate = SpecialDate(
                    title = title.trim(),
                    targetDate = targetDate,
                    category = category,
                    countMode = countMode,
                    repeatType = repeatType,
                    reminderDays = reminderDays,
                    content = content.trim(),
                    tags = tagsJson,
                    imagePaths = pathsJson,
                    createdAt = now,
                    updatedAt = now
                )
                val newId = repository.insert(newDate)
                _relations.value.forEach { relation ->
                    cardRelationRepository.addRelation(relation.copy(sourceId = newId))
                }
            } else {
                val existing = repository.getById(id) ?: return@launch
                val updated = existing.copy(
                    title = title.trim(),
                    targetDate = targetDate,
                    category = category,
                    countMode = countMode,
                    repeatType = repeatType,
                    reminderDays = reminderDays,
                    content = content.trim(),
                    tags = tagsJson,
                    imagePaths = pathsJson,
                    updatedAt = now
                )
                repository.update(updated)
            }
        }
    }

    fun deleteDate(id: Long) {
        viewModelScope.launch {
            repository.getById(id)?.let { repository.delete(it) }
        }
    }

    fun togglePin(id: Long) {
        viewModelScope.launch {
            repository.togglePin(id)
        }
    }

    /**
     * 设置左滑展开卡片 id（同时仅一张卡片展开）
     * 传入 null 表示全部收起
     */
    fun setExpandedDateId(id: Long?) {
        _expandedDateId.value = id
    }

    /**
     * 归档特殊日期
     * 1. 调用 repository.archive(id)
     * 2. 缓存 SpecialDate 快照到 _pendingArchive（供撤回用）
     * 3. 若归档的是置顶卡，同步清空 _pinnedDateId
     */
    fun archiveDate(id: Long) {
        viewModelScope.launch {
            try {
                // 1. 先取快照（在 archive 之前），getById 是 suspend
                val snapshot = repository.getById(id)
                // 2. 归档
                repository.archive(id)
                // 3. 缓存快照
                _pendingArchive.value = snapshot
                // 4. 若归档的是置顶卡，清空 pinnedDateId
                if (_pinnedDateId.value == id) {
                    _pinnedDateId.value = null
                }
            } catch (e: Exception) {
                // 走项目内统一的全局错误处理流程，UI 弹简短 Toast
                android.util.Log.e("SpecialDateVM", "归档失败: id=$id", e)
            }
        }
    }

    /**
     * 仅清空 pendingArchive 缓存（不调用 unarchive）
     * 用途：Snackbar 3s 后无操作时调用，避免重复展示
     */
    fun clearPendingArchive() {
        _pendingArchive.value = null
    }

    /**
     * 撤回归档（恢复 SpecialDate 到 isArchived=false）
     * 必须在 Snackbar 显示的 3 秒内由 UI 触发
     */
    fun undoArchive() {
        val snapshot = _pendingArchive.value ?: return
        viewModelScope.launch {
            try {
                repository.unarchive(snapshot.id)
                _pendingArchive.value = null
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "撤回归档失败: id=${snapshot.id}", e)
                // 失败：清空缓存，让 UI 弹 Toast
                _pendingArchive.value = null
            }
        }
    }

    /**
     * 置顶特殊日期（单选：自动取消其它卡片的置顶）
     */
    fun pinDate(id: Long) {
        viewModelScope.launch {
            try {
                repository.pinDate(id)
                _pinnedDateId.value = id
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "置顶失败: id=$id", e)
            }
        }
    }

    /** 取消置顶 */
    fun unpinDate(id: Long) {
        viewModelScope.launch {
            try {
                repository.unpinDate(id)
                _pinnedDateId.value = null
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "取消置顶失败: id=$id", e)
            }
        }
    }

    fun addRelation(targetType: String, targetId: Long) {
        viewModelScope.launch {
            val dateId = _editingDate.value?.id ?: 0L
            val relation = CardRelation(
                sourceType = "date",
                sourceId = dateId,
                targetType = targetType,
                targetId = targetId
            )
            val result = cardRelationRepository.addRelation(relation)
            if (result > 0) {
                _relations.value = (_relations.value + relation.copy(id = result)).distinctBy { "${it.targetType}_${it.targetId}" }
            }
        }
    }

    fun removeRelation(targetType: String, targetId: Long) {
        viewModelScope.launch {
            val dateId = _editingDate.value?.id ?: return@launch
            cardRelationRepository.removeRelation("date", dateId, targetType, targetId)
            _relations.value = _relations.value.filter { !(it.targetType == targetType && it.targetId == targetId) }
        }
    }

    /** 搜索卡片并回调结果 */
    fun searchCards(query: String, callback: (List<CardSearchResult>) -> Unit) {
        viewModelScope.launch {
            val results = cardRelationRepository.searchCards(query)
            callback(results)
        }
    }

    fun encodeTags(tags: List<String>): String = TagUtils.encodeTags(tags)

    fun decodeTags(json: String): List<String> = TagUtils.decodeTags(json)

    fun encodePaths(paths: List<String>): String = TagUtils.encodePaths(paths)

    fun decodePaths(json: String): List<String> = TagUtils.decodePaths(json)

    companion object {

        fun formatDateKey(timestamp: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val weekDays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val weekDay = weekDays[cal.get(Calendar.DAY_OF_WEEK) - 1]
            return "${year}年${month}月${day}日 周${weekDay}"
        }

        internal fun groupByDisplayDates(dates: List<SpecialDate>): Map<DateGroup, List<DisplayDate>> {
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val msPerDay = 24 * 60 * 60 * 1000L

            return dates.mapNotNull { date ->
                try {
                    val effectiveDate = computeEffectiveDate(date, todayStart)
                    val daysDiff = ((effectiveDate - todayStart) / msPerDay)
                    val daysAbs = kotlin.math.abs(daysDiff)

                    /**
                     * 分组规则（与 plan 5.2 节一致）：
                     * - countMode=0 且 targetDate >= today → COUNTDOWN（倒计时）
                     * - countMode=1 且 targetDate <= today → COUNTUP（正计时）
                     * - countMode=0 且 targetDate <  today → EXPIRED（已过期）
                     * - countMode=1 且 targetDate >  today → COUNTUP（正计时，纪念尚未开始）
                     */
                    val groupType = when {
                        date.countMode == 1 -> DateGroup.COUNTUP
                        daysDiff > 0 -> DateGroup.COUNTDOWN
                        else -> DateGroup.EXPIRED
                    }

                    val dayColor = when (groupType) {
                        DateGroup.COUNTUP -> DayColor.GREEN
                        else -> when {
                            daysAbs <= 3L -> DayColor.RED
                            daysAbs <= 30L -> DayColor.ORANGE
                            else -> DayColor.GRAY
                        }
                    }

                    val displayText = when (groupType) {
                        DateGroup.COUNTDOWN -> "${daysAbs}天后"
                        DateGroup.COUNTUP -> "${daysAbs}天"
                        DateGroup.EXPIRED -> "${daysAbs}天前"
                    }

                    DisplayDate(
                        id = date.id,
                        title = date.title,
                        targetDate = effectiveDate,
                        originalTargetDate = date.targetDate,
                        category = try { DateCategory.valueOf(date.category) } catch (e: Exception) { DateCategory.OTHER },
                        countMode = date.countMode,
                        daysRemaining = daysDiff,
                        daysAbsolute = daysAbs,
                        dayColor = dayColor,
                        groupType = groupType,
                        displayText = displayText,
                        content = date.content,
                        tags = decodeTagsSafe(date.tags),
                        hasImage = decodePathsSafe(date.imagePaths).isNotEmpty(),
                        relationHint = null,
                        isPinned = date.isPinned
                    )
                } catch (e: Exception) {
                    null
                }
            }.groupBy { it.groupType }
        }

        private fun computeEffectiveDate(date: SpecialDate, todayStart: Long): Long {
            val cal = Calendar.getInstance().apply { timeInMillis = date.targetDate }
            return when (date.repeatType) {
                1 -> {
                    var year = Calendar.getInstance().get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH)
                    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                    val candidate = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (candidate < todayStart) {
                        year++
                        Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    } else {
                        candidate
                    }
                }
                2 -> {
                    val now = Calendar.getInstance()
                    var year = now.get(Calendar.YEAR)
                    var month = now.get(Calendar.MONTH)
                    val dayOfMonth = cal.get(Calendar.DAY_OF_MONTH)
                    val maxDayOfMonth = now.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val safeDay = minOf(dayOfMonth, maxDayOfMonth)
                    val candidate = Calendar.getInstance().apply {
                        set(year, month, safeDay, 0, 0, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (candidate < todayStart) {
                        month++
                        if (month >= 12) {
                            month = 0
                            year++
                        }
                        val nextMaxDay = Calendar.getInstance().apply {
                            set(year, month, 1, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val nextSafeDay = minOf(dayOfMonth, nextMaxDay)
                        Calendar.getInstance().apply {
                            set(year, month, nextSafeDay, 0, 0, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    } else {
                        candidate
                    }
                }
                else -> date.targetDate
            }
        }

        private fun decodeTagsSafe(json: String): List<String> = TagUtils.decodeTags(json)

        private fun decodePathsSafe(json: String): List<String> = TagUtils.decodePaths(json)
    }
}

data class DisplayDate(
    val id: Long,
    val title: String,
    val targetDate: Long,
    val originalTargetDate: Long,
    val category: DateCategory,
    val countMode: Int,
    val daysRemaining: Long,
    val daysAbsolute: Long,
    val dayColor: DayColor,
    val groupType: DateGroup,
    val displayText: String,
    val content: String,
    val tags: List<String>,
    val hasImage: Boolean,
    val relationHint: String?,
    val isPinned: Boolean
)

/**
 * 日期分组：按 countMode + 是否过期划分
 * - COUNTDOWN: 倒计时模式（countMode=0）且未过期
 * - COUNTUP:   正计时模式（countMode=1）（无论是否已开始）
 * - EXPIRED:   倒计时模式（countMode=0）但已过期
 *
 * 命名替换自旧版 GroupType（UPCOMING/CELEBRATING/EXPIRED）。
 * 字段名 groupType 暂保留以避免破坏 SpecialDateCard，任务 13 会统一改为 group。
 */
enum class DateGroup { COUNTDOWN, COUNTUP, EXPIRED }

/**
 * 已废弃：保留以避免破坏潜在引用
 * - 旧：GroupType.UPCOMING/Celebrating/Expired
 * - 新：DateGroup.COUNTDOWN/COUNTUP/EXPIRED
 *
 * 任务 13 重写 SpecialDateCard 时会一并清理。
 */
@Deprecated("改用 DateGroup；该枚举仅保留以避免破坏潜在引用")
enum class GroupType { UPCOMING, CELEBRATING, EXPIRED }

enum class DayColor { RED, ORANGE, GRAY, GREEN }

/**
 * 特殊日期类型
 *
 * 注意事项：
 * 1. 新增枚举值时必须放在 OTHER 之前，OTHER 必须保持最末尾
 * 2. 枚举的 ordinal 不保证稳定，数据库存的是 name() 字符串，不是 ordinal
 * 3. 自定义类型在数据库中存为 "CUSTOM:xxx" 格式字符串，不属于本枚举
 */
enum class DateCategory(val displayName: String, val emoji: String) {
    BIRTHDAY("生日", "\uD83C\uDF82"),
    ANNIVERSARY("纪念日", "\uD83D\uDC95"),
    HOLIDAY("节日", "\uD83C\uDF89"),
    LIFE("生活", "🌱"),
    STUDY("学习", "📚"),
    WORK("工作", "💼"),
    ENTERTAINMENT("娱乐", "🎮"),
    OTHER("其他", "\uD83D\uDCC5")
}
