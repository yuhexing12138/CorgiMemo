package com.corgimemo.app.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.DeletedSpecialDateRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val cardRelationRepository: CardRelationRepository,
    private val deletedSpecialDateRepository: DeletedSpecialDateRepository,
    private val corgiPreferences: CorgiPreferences
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

    // ========== 下拉刷新相关（2026-07-14 新增，参考 InspirationViewModel）==========

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /**
     * 下拉刷新
     *
     * 特殊日期数据源是 Room Flow（响应式），理论上不需要重新订阅。
     * 此方法主要用于：
     * 1. 触发柯基刷新动画至少显示 800ms
     * 2. 业务上对外暴露"下拉可刷新"的语义入口
     * 3. 后续如需添加远程同步等耗时操作可在此扩展
     */
    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            kotlinx.coroutines.delay(800) // 确保柯基动画至少显示 800ms
            _isRefreshing.value = false
        }
    }

    /** 菜单弹窗展开状态 */
    private val _menuExpanded = MutableStateFlow(false)
    val menuExpanded: StateFlow<Boolean> = _menuExpanded.asStateFlow()

    /** 隐藏详情（持久化到 DataStore） */
    private val _hideDetails = MutableStateFlow(false)
    val hideDetails: StateFlow<Boolean> = _hideDetails.asStateFlow()

    /** 隐藏已归档（持久化到 DataStore） */
    private val _hideArchivedItems = MutableStateFlow(false)
    val hideArchivedItems: StateFlow<Boolean> = _hideArchivedItems.asStateFlow()

    /** 批量选择模式 */
    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    /** 选中的日期 ID 集合 */
    private val _selectedDateIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDateIds: StateFlow<Set<Long>> = _selectedDateIds.asStateFlow()

    /** 单条删除撤回缓存（软删除快照，供 Snackbar 撤回） */
    private val _pendingDeletedDate = MutableStateFlow<SpecialDate?>(null)
    val pendingDeletedDate: StateFlow<SpecialDate?> = _pendingDeletedDate.asStateFlow()

    /** 批量删除撤回缓存 */
    private val _pendingBatchDeletes = MutableStateFlow<List<SpecialDate>>(emptyList())
    val pendingBatchDeletes: StateFlow<List<SpecialDate>> = _pendingBatchDeletes.asStateFlow()

    // ==================== 自定义日期类型管理 ====================

    /** 自定义日期类型列表（响应式） */
    val customDateTypes: StateFlow<List<CustomDateType>> = repository.allCustomDateTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前选中的类型筛选（null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义） */
    private val _selectedDateCategory = MutableStateFlow<String?>(null)
    val selectedDateCategory: StateFlow<String?> = _selectedDateCategory

    /** 添加自定义类型 */
    fun addCustomType(name: String, emoji: String) {
        viewModelScope.launch {
            repository.insertCustomDateType(name, emoji)
        }
    }

    /** 重命名自定义类型 */
    fun renameCustomType(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renameCustomDateType(id, newName)
        }
    }

    /** 删除自定义类型（关联日期回退为 OTHER） */
    fun deleteCustomType(id: Long) {
        viewModelScope.launch {
            repository.deleteCustomDateType(id)
            // 如果当前选中的是被删除的类型，重置筛选
            if (_selectedDateCategory.value == "CUSTOM:$id") {
                _selectedDateCategory.value = null
            }
        }
    }

    /** 设置类型筛选 */
    fun filterByDateCategory(category: String?) {
        _selectedDateCategory.value = category
    }

    init {
        // 订阅持久化的隐藏详情状态
        viewModelScope.launch {
            corgiPreferences.hideSpecialDateDetails.collect { hide ->
                _hideDetails.value = hide
            }
        }
        // 订阅持久化的隐藏已归档状态
        viewModelScope.launch {
            corgiPreferences.hideArchivedDateItems.collect { hide ->
                _hideArchivedItems.value = hide
            }
        }
    }

    /** 原始数据流（在 stateIn 之前通过 onEach 监听初始化状态） */
    val specialDates: StateFlow<List<SpecialDate>> = repository.allDates
        .onEach {
            if (!_isDataInitialized.value) {
                _isDataInitialized.value = true
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 当前置顶的日期（2026-07-14 新增；2026-07-14 二次调整）
     *
     * 数据层分离方案：独立 StateFlow 跟踪唯一置顶卡
     * - 已有置顶卡：返回其 DisplayDate
     * - 无置顶卡：返回 null
     * - UI 在所有 DateSectionHeader 之上单独渲染 PinnedDateCard
     *
     * 2026-07-14 二次调整：移除 `!isArchived` 过滤条件
     * - 让"已归档 + 置顶"的卡也与"未归档 + 置顶"行为完全一致
     * - 互斥性、显示在最顶部、不在原 EXPIRED 分组显示
     * - 取消置顶后根据 isArchived 字段返回 EXPIRED/COUNTDOWN/COUNTUP 分组
     *
     * 实现说明：复用 `groupByDisplayDates` 的转换逻辑避免代码重复
     */
    val pinnedDate: StateFlow<DisplayDate?> = specialDates
        .map { dates ->
            val candidates = groupByDisplayDates(dates.filter { it.isPinned })
            candidates.values.flatten().firstOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * 三组分类后的展示数据
     *
     * 分组规则（2026-07-13 重构，参考用户最新需求）：
     * - 已归档（isArchived=true）           → EXPIRED（已归档）
     * - 未归档 且 未来日期（targetDate>today）→ COUNTDOWN（倒计时）
     * - 未归档 且 过去日期（targetDate<=today）→ COUNTUP（正计时）
     *
     * 字段 countMode 不再影响分组归属，分组完全由"是否归档"+"日期与今天的大小关系"决定。
     * 这样"用户设置的日期是未来/过去"即可直接决定分区，符合用户对日期页的预期。
     */
    val groupedDates: StateFlow<Map<DateGroup, List<DisplayDate>>> =
        combine(specialDates, _searchQuery, _hideArchivedItems) { dates, query, hideArchived ->
            // 1. 隐藏已归档过滤
            val afterHideFilter = if (hideArchived) dates.filter { !it.isArchived } else dates
            // 2. 搜索过滤（已归档与未归档都允许搜索）
            val filtered = if (query.isBlank()) {
                afterHideFilter
            } else {
                afterHideFilter.filter { it.title.contains(query, ignoreCase = true) }
            }
            // 2. 2026-07-14 二次调整：过滤掉所有置顶卡（包括已归档+置顶的）
            //    与"未归档+置顶"行为完全一致：
            //    - 互斥性
            //    - 显示在 pinnedDate 顶部
            //    - 不在原分组（EXPIRED/COUNTDOWN/COUNTUP）显示，避免重复
            //    取消置顶后根据 isArchived 字段返回对应分组
            val withoutPinned = filtered.filter { !it.isPinned }
            groupByDisplayDates(withoutPinned)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * 获取指定月份各日期的圆点颜色映射（用于日期页日历弹窗）
     *
     * 匹配规则：SpecialDate.targetDate 的 (year, month) == 入参
     * 同一日期有多张卡时，按 targetDate 升序排序后取首张 category.color
     * （保证相同数据下圆点色稳定）
     *
     * 注意：与 groupedDates 不同，此处按原始 targetDate 匹配（不考虑 repeatType 重复规则），
     *      严格遵循设计文档"完全匹配年月日"决策。
     *
     * @param year 年
     * @param month 月（1-12）
     * @return Map<dayOfMonth, Color>，无数据时返回空 Map
     */
    fun getCalendarDateColor(year: Int, month: Int): Map<Int, Color> {
        return specialDates.value
            .filter { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.targetDate }
                cal.get(Calendar.YEAR) == year &&
                (cal.get(Calendar.MONTH) + 1) == month
            }
            .groupBy { item ->
                Calendar.getInstance().apply { timeInMillis = item.targetDate }
                    .get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { (_, list) ->
                list.sortedBy { it.targetDate }.first().category.let { categoryName ->
                    try { DateCategory.valueOf(categoryName).color } catch (e: Exception) { DateCategory.OTHER.color }
                }
            }
    }

    /**
     * 获取完全匹配年月日的纪念日列表（用于日期页日历弹窗卡片区）
     *
     * 匹配规则：SpecialDate.targetDate 的 (year, month, day) == 入参（完全相等，不考虑 repeatType）
     * 返回数据已通过 toDisplayDateOrNull 转换为 DisplayDate，
     * 保证字段（effectiveDate / daysDiff / dayColor / displayText 等）与 SpecialDateScreen 列表一致。
     *
     * @param year 年
     * @param month 月（1-12）
     * @param day 日
     * @return List<DisplayDate>，按 originalTargetDate 升序排序，转换失败项被过滤
     */
    fun getDatesByDate(year: Int, month: Int, day: Int): List<DisplayDate> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return specialDates.value
            .filter { item ->
                val cal = Calendar.getInstance().apply { timeInMillis = item.targetDate }
                cal.get(Calendar.YEAR) == year &&
                (cal.get(Calendar.MONTH) + 1) == month &&
                cal.get(Calendar.DAY_OF_MONTH) == day
            }
            .sortedBy { it.targetDate }
            .mapNotNull { item -> toDisplayDateOrNull(item, todayStart) }
    }

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

    /**
     * 删除日期（软删除到回收站）
     *
     * 流程：
     * 1. 从 special_dates 读取完整数据
     * 2. 插入 deleted_special_dates 表
     * 3. 从 special_dates 删除
     * 4. 缓存到 _pendingDeletedDate 供 Snackbar 撤回
     *
     * @param id 待删除日期 ID
     */
    fun deleteDate(id: Long) {
        viewModelScope.launch {
            try {
                val date = repository.getById(id) ?: return@launch
                deletedSpecialDateRepository.insertDeletedDate(date)
                repository.delete(date)
                _pendingDeletedDate.value = date
                // 若删除的是置顶卡，同步清空 _pinnedDateId
                if (_pinnedDateId.value == id) {
                    _pinnedDateId.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "软删除失败: id=$id", e)
            }
        }
    }

    /**
     * 撤回删除（从回收站恢复）
     * 必须在 Snackbar 显示的 3 秒内由 UI 触发
     */
    fun undoDelete() {
        val snapshot = _pendingDeletedDate.value ?: return
        viewModelScope.launch {
            try {
                deletedSpecialDateRepository.permanentlyDelete(snapshot.id)
                repository.insert(snapshot)
                _pendingDeletedDate.value = null
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "撤回删除失败: id=${snapshot.id}", e)
                _pendingDeletedDate.value = null
            }
        }
    }

    /**
     * 仅清空 pendingDeletedDate 缓存（不调用恢复）
     * 用途：Snackbar 3s 后无操作时调用
     */
    fun clearPendingDeletedDate() {
        _pendingDeletedDate.value = null
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

    /** 设置菜单展开状态 */
    fun setMenuExpanded(expanded: Boolean) {
        _menuExpanded.value = expanded
    }

    /** 切换隐藏详情（持久化到 DataStore） */
    fun toggleHideDetails() {
        val newVal = !_hideDetails.value
        _hideDetails.value = newVal
        viewModelScope.launch {
            corgiPreferences.setHideSpecialDateDetails(newVal)
        }
    }

    /** 切换隐藏已归档（持久化到 DataStore） */
    fun toggleHideArchivedItems() {
        val newVal = !_hideArchivedItems.value
        _hideArchivedItems.value = newVal
        viewModelScope.launch {
            corgiPreferences.setHideArchivedDateItems(newVal)
        }
    }

    /** 进入批量选择模式 */
    fun enterBatchMode() {
        _isBatchMode.value = true
        _selectedDateIds.value = emptySet()
    }

    /** 退出批量选择模式 */
    fun exitBatchMode() {
        _isBatchMode.value = false
        _selectedDateIds.value = emptySet()
    }

    /** 切换选中状态 */
    fun toggleSelection(id: Long) {
        val current = _selectedDateIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedDateIds.value = current
    }

    /** 全选（当前可见的所有日期） */
    fun selectAll() {
        _selectedDateIds.value = specialDates.value.map { it.id }.toSet()
    }

    /** 取消全选 */
    fun clearSelection() {
        _selectedDateIds.value = emptySet()
    }

    /**
     * 批量删除（软删除到回收站）
     * 流程：取快照 → 批量插入回收站 → 逐条从主表删除 → 缓存快照 → 退出批量模式
     */
    fun batchDelete() {
        viewModelScope.launch {
            try {
                val ids = _selectedDateIds.value.toList()
                val dates = ids.mapNotNull { id ->
                    repository.getById(id)
                }
                if (dates.isEmpty()) return@launch
                deletedSpecialDateRepository.insertDeletedDates(dates)
                dates.forEach { date ->
                    repository.delete(date)
                }
                _pendingBatchDeletes.value = dates
                // 若批量删除包含置顶卡，同步清空 _pinnedDateId
                if (_pinnedDateId.value in ids) {
                    _pinnedDateId.value = null
                }
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "批量删除失败", e)
            }
            exitBatchMode()
        }
    }

    /**
     * 撤回批量删除
     * 必须在 Snackbar 显示的 3 秒内由 UI 触发
     */
    fun undoBatchDelete() {
        val dates = _pendingBatchDeletes.value
        if (dates.isEmpty()) return
        viewModelScope.launch {
            try {
                dates.forEach { date ->
                    deletedSpecialDateRepository.permanentlyDelete(date.id)
                    repository.insert(date)
                }
                _pendingBatchDeletes.value = emptyList()
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "撤回批量删除失败", e)
                _pendingBatchDeletes.value = emptyList()
            }
        }
    }

    /** 仅清空 pendingBatchDeletes 缓存 */
    fun clearPendingBatchDeletes() {
        _pendingBatchDeletes.value = emptyList()
    }

    /**
     * 批量归档
     */
    fun batchArchive() {
        viewModelScope.launch {
            try {
                _selectedDateIds.value.forEach { id ->
                    repository.archive(id)
                }
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "批量归档失败", e)
            }
            exitBatchMode()
        }
    }

    /**
     * 批量创建副本
     * 复制选中的日期卡片，新副本标题后缀"(副本)"，重置置顶和归档状态
     */
    fun batchDuplicate() {
        viewModelScope.launch {
            try {
                _selectedDateIds.value.forEach { id ->
                    val original = repository.getById(id) ?: return@forEach
                    val copy = original.copy(
                        id = 0,  // 让自增主键生成新 ID
                        title = "${original.title}(副本)",
                        isPinned = false,
                        isArchived = false,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.insert(copy)
                }
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "批量创建副本失败", e)
            }
            exitBatchMode()
        }
    }

    /**
     * 取消归档（2026-07-13 新增）
     *
     * 与 archiveDate 的区别：
     * - archiveDate 会缓存 SpecialDate 快照到 _pendingArchive（用于 Snackbar 撤回）
     * - 本函数不缓存快照，不触发"已归档" Snackbar（因为是反向操作）
     *
     * 副作用：
     * 1. repository.unarchive(id)         将 isArchived 设为 false
     * 2. 清空 _pendingArchive             防止归档时遗留的快照误触 Snackbar
     * 3. 恢复 _pinnedDateId               重新读库 isPinned 字段：
     *                                      - 若该卡原本是置顶卡，archiveDate 时已被清掉，现在恢复
     *                                      - 若该卡原本未置顶，保持 _pinnedDateId 不变
     *
     * @param id 特殊日期 id
     */
    fun unarchiveDate(id: Long) {
        viewModelScope.launch {
            try {
                // 1. 先读当前 SpecialDate（用于恢复 pinned 状态）
                val current = repository.getById(id)
                // 2. 取消归档
                repository.unarchive(id)
                // 3. 清空 pendingArchive（防止旧快照触发 Snackbar）
                _pendingArchive.value = null
                // 4. 恢复 pinnedDateId 状态
                //    - archiveDate 时若该卡是置顶卡会清掉 _pinnedDateId
                //    - 取消归档时根据数据库 isPinned 字段恢复（如果其他置顶操作未改变状态）
                if (current?.isPinned == true && _pinnedDateId.value == null) {
                    _pinnedDateId.value = id
                }
            } catch (e: Exception) {
                android.util.Log.e("SpecialDateVM", "取消归档失败: id=$id", e)
                _pendingArchive.value = null
            }
        }
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
     *
     * 2026-07-14 业务规则：置顶不改变归档状态
     * - 已归档 + 置顶 → 仍在 EXPIRED 分组显示（groupedDates 不再过滤 isPinned && !isArchived）
     * - 已归档卡片置顶后取消置顶 → 仍保持已归档状态
     * - pinDate 仅设置 isPinned 字段，不动 isArchived 字段
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

            // 2026-07-14 Task 2：复用 toDisplayDateOrNull，避免转换逻辑重复（DRY）
            return dates.mapNotNull { date -> toDisplayDateOrNull(date, todayStart) }
                .groupBy { it.groupType }
        }

        /**
         * 将 SpecialDate 转换为 DisplayDate（DRY 抽取，2026-07-14 Task 2）
         *
         * 抽取自 groupByDisplayDates 原 mapNotNull lambda 体，供：
         * - [groupByDisplayDates]：按 groupType 分组返回 Map
         * - [getDatesByDate]：按精确年月日匹配返回扁平 List
         * 共享转换逻辑，保证 effectiveDate / daysDiff / dayColor / displayText 等字段一致。
         *
         * @param date 原始 SpecialDate
         * @param todayStart 今日 0 点的时间戳（由调用方计算并传入，避免每条数据重复计算）
         * @return 转换后的 DisplayDate；转换失败返回 null（被 mapNotNull 过滤）
         */
        internal fun toDisplayDateOrNull(date: SpecialDate, todayStart: Long): DisplayDate? {
            return try {
                val msPerDay = 24 * 60 * 60 * 1000L
                val effectiveDate = computeEffectiveDate(date, todayStart)
                val daysDiff = ((effectiveDate - todayStart) / msPerDay)
                val daysAbs = kotlin.math.abs(daysDiff)

                /**
                 * 分组规则（2026-07-13 重构，参考用户最新需求）：
                 * - isArchived = true                    → EXPIRED（已归档，最优先）
                 * - 未归档 且 targetDate >  today         → COUNTDOWN（倒计时）
                 * - 未归档 且 targetDate <= today         → COUNTUP（正计时）
                 *
                 * 字段 countMode 不再影响分组归属（已移除旧逻辑）。
                 */
                val groupType = when {
                    date.isArchived -> DateGroup.EXPIRED
                    daysDiff > 0 -> DateGroup.COUNTDOWN
                    else -> DateGroup.COUNTUP
                }

                val dayColor = when (groupType) {
                    DateGroup.COUNTUP -> DayColor.GREEN
                    DateGroup.EXPIRED -> DayColor.GRAY
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
                    isPinned = date.isPinned,
                    // 透传 isArchived 字段，供 UI 层（SpecialDateCard）独立判断降权显示
                    isArchived = date.isArchived
                )
            } catch (e: Exception) {
                null
            }
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
    val isPinned: Boolean,
    /**
     * 是否已归档（2026-07-13 新增字段）
     *
     * 来源：SpecialDate.isArchived
     * 用途：SpecialDateCard 根据该字段独立决定是否降权显示（alpha 0.6f），
     *      避免将 alpha 应用于整个 Card 导致左滑按钮区域被半透明。
     * 取值：true → 已归档（显示在 EXPIRED 分组），false → 正常显示
     */
    val isArchived: Boolean = false
)

/**
 * 日期分组（2026-07-13 重构，参考用户最新需求）
 *
 * 分组完全由"是否归档"+"日期与今天的大小关系"决定，countMode 字段不再影响分组：
 * - COUNTDOWN: 未归档 且 未来日期（targetDate > today）→ 倒计时
 * - COUNTUP:   未归档 且 过去或当天日期（targetDate <= today）→ 正计时
 * - EXPIRED:   已归档（isArchived = true）→ 已归档
 *
 * 字段名 groupType 暂保留以避免破坏 SpecialDateCard 内部对字段名的引用。
 *
 * 枚举值命名 EXPIRED 暂保留以避免破坏外部引用（如 AppDrawer 的 onDateTypeClick(DateGroup.EXPIRED)），
 * UI 层 DateSectionHeader 会将该枚举映射为"已归档"显示文本。
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
 * 4. color 字段用于日历弹窗圆点配色（2026-07-14 Task 2 新增）：
 *    - 取色参考 DateScreen.kt 占位符版本 + 项目主题色调
 *    - 同一 category 在日历圆点和卡片图标处保持视觉一致
 */
enum class DateCategory(val displayName: String, val emoji: String, val color: Color) {
    BIRTHDAY("生日", "\uD83C\uDF82", Color(0xFFFF6B9D)),
    ANNIVERSARY("纪念日", "\uD83D\uDC95", Color(0xFFFF9A5C)),
    HOLIDAY("节日", "\uD83C\uDF89", Color(0xFF4ECDC4)),
    LIFE("生活", "🌱", Color(0xFF66BB6A)),
    STUDY("学习", "📚", Color(0xFF42A5F5)),
    WORK("工作", "💼", Color(0xFF8D6E63)),
    ENTERTAINMENT("娱乐", "🎮", Color(0xFFAB47BC)),
    OTHER("其他", "\uD83D\uDCC5", Color(0xFF95E1D3))
}
