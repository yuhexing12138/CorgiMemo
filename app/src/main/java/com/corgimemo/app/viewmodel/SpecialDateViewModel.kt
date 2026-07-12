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

    /** 原始数据流（在 stateIn 之前通过 onEach 监听初始化状态） */
    val specialDates: StateFlow<List<SpecialDate>> = repository.allDates
        .onEach {
            if (!_isDataInitialized.value) {
                _isDataInitialized.value = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 三组分类后的展示数据 */
    val groupedDates: StateFlow<Map<GroupType, List<DisplayDate>>> =
        combine(specialDates, _searchQuery) { dates, query ->
            if (query.isBlank()) {
                dates
            } else {
                dates.filter { it.title.contains(query, ignoreCase = true) }
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

        internal fun groupByDisplayDates(dates: List<SpecialDate>): Map<GroupType, List<DisplayDate>> {
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

                    val groupType = when {
                        date.countMode == 1 -> GroupType.CELEBRATING
                        daysDiff > 0 -> GroupType.UPCOMING
                        else -> GroupType.EXPIRED
                    }

                    val dayColor = when (groupType) {
                        GroupType.CELEBRATING -> DayColor.GREEN
                        else -> when {
                            daysAbs <= 3L -> DayColor.RED
                            daysAbs <= 30L -> DayColor.ORANGE
                            else -> DayColor.GRAY
                        }
                    }

                    val displayText = when (groupType) {
                        GroupType.UPCOMING -> "${daysAbs}天后"
                        GroupType.CELEBRATING -> "${daysAbs}天"
                        GroupType.EXPIRED -> "${daysAbs}天前"
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
    val groupType: GroupType,
    val displayText: String,
    val content: String,
    val tags: List<String>,
    val hasImage: Boolean,
    val relationHint: String?,
    val isPinned: Boolean
)

enum class GroupType { UPCOMING, CELEBRATING, EXPIRED }
enum class DayColor { RED, ORANGE, GRAY, GREEN }

enum class DateCategory(val displayName: String, val emoji: String) {
    BIRTHDAY("生日", "\uD83C\uDF82"),
    ANNIVERSARY("纪念日", "\uD83D\uDC95"),
    HOLIDAY("节日", "\uD83C\uDF89"),
    OTHER("其他", "\uD83D\uDCC5")
}
