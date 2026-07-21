package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.DeletedInspirationRepository
import com.corgimemo.app.data.repository.InspirationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import javax.inject.Inject

/**
 * 标签筛选模式
 * - OR：并集，显示包含任意选中标签的灵感
 * - AND：交集，显示同时包含所有选中标签的灵感
 * - NOT：非，显示不包含任何选中标签的灵感
 */
enum class TagFilterMode {
    OR,
    AND,
    NOT
}

/**
 * 灵感记录视图模型
 * 管理灵感数据的加载、缓存和业务逻辑
 * 包括：CRUD操作、搜索过滤、时间线分组、关联管理
 */
@HiltViewModel
class InspirationViewModel @Inject constructor(
    private val inspirationRepository: InspirationRepository,
    private val deletedInspirationRepository: DeletedInspirationRepository,
    private val cardRelationRepository: CardRelationRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /**
     * 灵感显示项（用于 UI 渲染的统一数据模型）
     * @param inspiration 灵感实体
     * @param showDate 是否显示左侧日期列（相邻相同日期只显示一次）
     * @param isPinned 是否为置顶项
     */
    data class InspirationDisplayItem(
        val inspiration: Inspiration,
        val showDate: Boolean,
        val isPinned: Boolean
    )

    companion object {
        /**
         * 构建统一显示列表：相邻相同日期只显示一次
         * - 第 1 条始终 showDate=true
         * - 后续仅当与前一条日期（年月日）不同时 showDate=true
         * @param list 已按显示顺序排序的灵感列表（置顶在前 + 非置顶在后，各自按 createdAt 倒序）
         * @return 带 showDate 标记的显示项列表
         */
        fun buildDisplayItems(list: List<Inspiration>): List<InspirationDisplayItem> {
            if (list.isEmpty()) return emptyList()
            return list.mapIndexed { index, inspiration ->
                val showDate = if (index == 0) {
                    true
                } else {
                    !isSameDay(list[index - 1].createdAt, inspiration.createdAt)
                }
                InspirationDisplayItem(
                    inspiration = inspiration,
                    showDate = showDate,
                    isPinned = inspiration.isPinned
                )
            }
        }

        /**
         * 判断两个时间戳是否同一天
         * @param t1 时间戳1（毫秒）
         * @param t2 时间戳2（毫秒）
         * @return true 表示同年同月同日
         */
        fun isSameDay(t1: Long, t2: Long): Boolean {
            val c1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
            val c2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
            return c1.get(java.util.Calendar.YEAR) == c2.get(java.util.Calendar.YEAR) &&
                   c1.get(java.util.Calendar.MONTH) == c2.get(java.util.Calendar.MONTH) &&
                   c1.get(java.util.Calendar.DAY_OF_MONTH) == c2.get(java.util.Calendar.DAY_OF_MONTH)
        }

        /**
         * 聚合多个灵感的标签为去重排序列表
         *
         * 用于 savedTags 流：从所有灵感中收集已使用的标签，
         * 去重后按字典序排序，供 TagPickerSheet 的「历史标签快速添加」区域使用。
         *
         * @param allTagsLists 每个灵感已解码的标签列表
         * @return 去重并按字典序排序的标签列表
         */
        fun aggregateSavedTags(allTagsLists: List<List<String>>): List<String> {
            return allTagsLists
                .flatten()
                .distinct()
                .sorted()
        }
    }

    // ========== 状态定义 ==========

    /** 数据是否已初始化完成（用于避免冷启动时从空列表闪烁到有数据） */
    private val _isDataInitialized = MutableStateFlow(false)
    val isDataInitialized: StateFlow<Boolean> = _isDataInitialized.asStateFlow()

    /** 所有灵感列表（按置顶+时间排序） */
    private val _inspirations = MutableStateFlow<List<Inspiration>>(emptyList())
    val inspirations: StateFlow<List<Inspiration>> = _inspirations.asStateFlow()

    /**
     * 各灵感的关联卡片数量映射（v2026-07-21 新增，供首页卡片显示 🔗×N）
     *
     * - key: inspirationId
     * - value: 该灵感作为 source 的 groupId=0 关联数量
     *
     * 数据来源：[refreshRelationCounts] 在灵感列表加载时批量查询。
     * 仅缓存数量 > 0 的项，减少 UI 层判断。
     */
    private val _relationCountMap = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val relationCountMap: StateFlow<Map<Long, Int>> = _relationCountMap.asStateFlow()

    /** 按日期分组后的灵感列表（用于时间线展示） */
    val groupedInspirations: StateFlow<Map<String, List<Inspiration>>> =
        _inspirations.map { list ->
            list.groupBy { inspiration ->
                formatDateKey(inspiration.createdAt)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** 置顶的灵感列表 */
    val pinnedInspirations: StateFlow<List<Inspiration>> =
        _inspirations.map { list ->
            list.filter { it.isPinned }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 非置顶的普通灵感列表（按年月分组） */
    val normalGroupedInspirations: StateFlow<Map<String, List<Inspiration>>> =
        _inspirations.map { list ->
            list.filter { !it.isPinned }
                .groupBy { inspiration ->
                    formatYearMonthKey(inspiration.createdAt)
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** 统一显示列表（置顶+非置顶，已计算 showDate，相邻相同日期只显示一次） */
    val displayInspirations: StateFlow<List<InspirationDisplayItem>> =
        _inspirations.map { list ->
            buildDisplayItems(list)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 用户自定义标签集合（从 CorgiPreferences 加载，与灵感派生标签合并显示） */
    private val _userDefinedTags = MutableStateFlow<Set<String>>(emptySet())

    // ========== 隐藏详情 ==========

    /** 是否隐藏灵感详情（时分时间、正文、标签、图片），仅保留标题和时间线 */
    private val _hideDetails = MutableStateFlow(false)
    val hideDetails: StateFlow<Boolean> = _hideDetails.asStateFlow()

    // ========== 菜单弹窗 ==========

    /** 三点菜单弹窗展开状态 */
    private val _menuExpanded = MutableStateFlow(false)
    val menuExpanded: StateFlow<Boolean> = _menuExpanded.asStateFlow()

    // ========== 批量选择模式 ==========

    /** 是否处于批量选择模式 */
    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode: StateFlow<Boolean> = _isBatchMode.asStateFlow()

    /** 当前选中的灵感 ID 集合 */
    private val _selectedInspirationIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedInspirationIds: StateFlow<Set<Long>> = _selectedInspirationIds.asStateFlow()

    // ========== 撤销删除相关 ==========

    /**
     * 待删除灵感的临时存储（用于显示 Snackbar 和撤销）
     *
     * 状态：null = 无待删除；非 null = 灵感已软删除到回收站，等待撤销或 5s 倒计时结束
     */
    private val _pendingDeletedInspiration = MutableStateFlow<Inspiration?>(null)
    val pendingDeletedInspiration: StateFlow<Inspiration?> = _pendingDeletedInspiration.asStateFlow()

    /**
     * 待批量删除的灵感列表临时存储（用于显示 Snackbar 和撤销）
     *
     * 状态：null = 无批量待删除；非 null = 已软删除到回收站的灵感列表
     */
    private val _pendingBatchDeletedInspirations = MutableStateFlow<List<Inspiration>?>(null)
    val pendingBatchDeletedInspirations: StateFlow<List<Inspiration>?> = _pendingBatchDeletedInspirations.asStateFlow()

    /** 删除倒计时任务（可取消） */
    private var deleteInspirationTimerJob: Job? = null

    /** 删除倒计时时长（5秒），与 HomeViewModel.UNDO_DELETE_DELAY_MS 保持一致 */
    private val UNDO_DELETE_INSPIRATION_DELAY_MS = 5000L

    /** 历史标签列表（从所有灵感聚合去重排序 + 用户自定义标签合并，用于侧边栏和 TagPickerSheet） */
    val savedTags: StateFlow<List<String>> =
        combine(_inspirations, _userDefinedTags) { list, userTags ->
            (aggregateSavedTags(list.map { decodeTags(it.tags) }) + userTags)
                .distinct()
                .sorted()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 选中的标签集合（多选） */
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    /** 当前标签筛选模式（默认 OR） */
    private val _tagFilterMode = MutableStateFlow(TagFilterMode.OR)
    val tagFilterMode: StateFlow<TagFilterMode> = _tagFilterMode.asStateFlow()

    /** 每个标签对应的灵感数量 */
    val tagCounts: StateFlow<Map<String, Int>> =
        _inspirations.map { list ->
            list.flatMap { decodeTags(it.tags) }
                .groupingBy { it }
                .eachCount()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    /** 灵感总数（用于"全部灵感"项的计数显示，避免 tagCounts.values.sum() 重复计算） */
    val totalInspirationCount: StateFlow<Int> =
        _inspirations.map { it.size }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    /** 应用标签筛选后的统一显示列表（供 InspirationScreen 消费） */
    val filteredDisplayInspirations: StateFlow<List<InspirationDisplayItem>> =
        combine(_inspirations, _selectedTags, _tagFilterMode) { list, tags, mode ->
            val filtered = filterInspirationsByTags(list, tags, mode)
            buildDisplayItems(filtered)
        }.onEach {
            // 数据首次通过筛选逻辑后标记为已初始化，确保 UI 不会在 isDataInitialized=true
            // 但 displayItems 仍为空时闪现空页面
            if (!_isDataInitialized.value) {
                _isDataInitialized.value = true
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** 搜索关键词 */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 当前编辑中的灵感 */
    private val _editingInspiration = MutableStateFlow<Inspiration?>(null)
    val editingInspiration: StateFlow<Inspiration?> = _editingInspiration.asStateFlow()

    /** 关联列表 */
    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations.asStateFlow()

    /**
     * 关联ID → 标题的映射（v2026-07-22 新增，供详情页/编辑页 Chip 显示）
     *
     * - key: CardRelation.id
     * - value: 目标卡片的标题（异步加载并缓存，已删除卡片显示"已删除"）
     *
     * 数据来源：[refreshRelationTitles] 在关联列表变化时增量加载。
     */
    private val _relationTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val relationTitles: StateFlow<Map<Long, String>> = _relationTitles.asStateFlow()

    /**
     * 当前预览卡片的详情（v2026-07-22 新增，供 LinkedCardPreviewDialog 按类型差异化展示）
     *
     * - null：未加载或已清空
     * - 非null：已加载完成，UI 显示详情内容
     *
     * 加载期间 [_cardDetailLoading] 为 true。
     */
    private val _cardDetail = MutableStateFlow<CardDetail?>(null)
    val cardDetail: StateFlow<CardDetail?> = _cardDetail.asStateFlow()

    /** 卡片详情加载中标志（控制预览 Dialog 内 CircularProgressIndicator） */
    private val _cardDetailLoading = MutableStateFlow(false)
    val cardDetailLoading: StateFlow<Boolean> = _cardDetailLoading.asStateFlow()

    // ========== 下拉刷新相关 ==========

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * 下拉刷新
     * 重新加载灵感列表和用户自定义标签
     */
    fun onRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadInspirations()
            loadUserDefinedTags()
            kotlinx.coroutines.delay(800) // 确保柯基动画至少显示 800ms
            _isRefreshing.value = false
        }
    }

    // ========== 初始化 ==========

    init {
        loadInspirations()
        loadUserDefinedTags()
    }

    /**
     * 从 CorgiPreferences 加载用户自定义标签
     */
    private fun loadUserDefinedTags() {
        viewModelScope.launch {
            _userDefinedTags.value = CorgiPreferences.getInstance(context).getUserDefinedTags()
        }
    }

    // ========== 核心方法 ==========

    /**
     * 根据选中标签和筛选模式过滤灵感列表
     * - 选中标签为空时返回全部灵感
     * - OR 模式：包含任意选中标签
     * - AND 模式：同时包含所有选中标签
     * - NOT 模式：不包含任何选中标签
     *
     * @param inspirations 原始灵感列表
     * @param selectedTags 选中的标签集合
     * @param mode 筛选模式
     * @return 过滤后的灵感列表
     */
    private fun filterInspirationsByTags(
        inspirations: List<Inspiration>,
        selectedTags: Set<String>,
        mode: TagFilterMode
    ): List<Inspiration> {
        if (selectedTags.isEmpty()) return inspirations
        return when (mode) {
            TagFilterMode.OR -> inspirations.filter { insp ->
                val tags = decodeTags(insp.tags)
                tags.any { it in selectedTags }
            }
            TagFilterMode.AND -> inspirations.filter { insp ->
                val tags = decodeTags(insp.tags)
                selectedTags.all { it in tags }
            }
            TagFilterMode.NOT -> inspirations.filter { insp ->
                val tags = decodeTags(insp.tags)
                selectedTags.none { it in tags }
            }
        }
    }

    /**
     * 切换标签选中状态（已选中则取消，未选中则加入）
     * @param tag 标签名
     */
    fun toggleTagSelection(tag: String) {
        _selectedTags.value = _selectedTags.value.toMutableSet().apply {
            if (contains(tag)) remove(tag) else add(tag)
        }
    }

    /**
     * 设置标签筛选模式
     * @param mode 筛选模式（OR/AND/NOT）
     */
    fun setTagFilterMode(mode: TagFilterMode) {
        _tagFilterMode.value = mode
    }

    /**
     * 清空所有已选中的标签（恢复"全部灵感"状态）
     */
    fun clearTagSelection() {
        _selectedTags.value = emptySet()
    }

    /**
     * 添加用户自定义标签
     *
     * 将新标签加入 _userDefinedTags 并持久化到 CorgiPreferences。
     * 已存在的标签不会重复添加。
     *
     * @param name 标签名称
     */
    fun addUserTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val updated = _userDefinedTags.value + trimmed
        _userDefinedTags.value = updated
        viewModelScope.launch {
            CorgiPreferences.getInstance(context).saveUserDefinedTags(updated)
        }
    }

    /**
     * 当前正在运行的 collect 协程引用
     *
     * **V2.8.4 修复**：之前 loadInspirations() 每次都启动新的 viewModelScope.launch + collect，
     * 且 collect 是**永久阻塞**的（直到 ViewModel.onCleared 才结束）。
     * 多次调用（ON_RESUME 触发 refresh）会留下多个 collect 协程同时运行，
     * 每次数据库变化都会触发所有 collect 协程更新 _inspirations，浪费 CPU/IO。
     *
     * 现在用 collectJob 持有当前 collect 协程：
     * - 新调用 startCollect() 时先 cancel 旧 collectJob
     * - 确保**始终只有一个 collect 协程在运行**
     * - 协程随 viewModelScope 生命周期自动清理
     */
    private var collectJob: Job? = null

    /**
     * 启动数据收集（统一入口，供 loadInspirations/refresh 共用）
     *
     * 每次调用会取消上一个 collect 协程（如果还在运行），
     * 然后启动新的 collect 协程，确保只有一个 collect 在运行。
     */
    private fun startCollect() {
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            try {
                inspirationRepository.getAllInspirations().collect { list ->
                    _inspirations.value = list
                    // v2026-07-21 新增：刷新关联数量映射（供首页卡片显示 🔗×N）
                    refreshRelationCounts(list)
                }
            } catch (e: Exception) {
                // 异常时仅记录，不再设置 _isLoading（已移除该状态）
                // isDataInitialized 已在 filteredDisplayInspirations.onEach 中维护
            }
        }
    }

    /**
     * 批量刷新灵感的关联数量映射（v2026-07-21 新增）
     *
     * 遍历所有灵感，查询每条灵感作为 source（groupId=0）的关联数量，
     * 仅缓存数量 > 0 的项到 [_relationCountMap]，供 UI 层显示 🔗×N。
     *
     * **调用时机**：灵感列表加载/刷新时（[startCollect] 内）
     *
     * **性能考量**：串行查询避免并发覆盖，灵感数量通常 < 100，可接受。
     * 后续如需优化可改为 DAO 批量查询。
     *
     * @param allInspirations 当前所有灵感列表
     */
    private suspend fun refreshRelationCounts(allInspirations: List<Inspiration>) {
        val relationCountMap = mutableMapOf<Long, Int>()
        for (inspiration in allInspirations) {
            if (inspiration.id <= 0L) continue
            // 查询该灵感作为 source（groupId=0，主分组）的关联数量
            val count = cardRelationRepository.getRelationCount("inspiration", inspiration.id, 0)
            if (count > 0) {
                relationCountMap[inspiration.id] = count
            }
        }
        _relationCountMap.value = relationCountMap
    }

    /**
     * 加载所有灵感
     */
    fun loadInspirations() = startCollect()

    /**
     * 灵感展示页调用：从编辑页返回时重新加载数据
     *
     * **V2.8.4 修复**：不再 viewModelScope.launch 包装 startCollect()，
     * 因为 startCollect 内部已经用 collectJob 管理协程，
     * 外层再 launch 一次会导致每次 refresh 留下一个"空挂起"协程等待 collectJob 完成。
     */
    fun refresh() = startCollect()

    /**
     * 创建新灵感
     * @param title 标题
     * @param content 富文本内容
     * @param tags 标签列表
     * @param imagePaths 本地图片路径列表
     */
    fun createInspiration(
        title: String,
        content: String,
        tags: List<String>,
        imagePaths: List<String>
    ) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val newInspiration = Inspiration(
                title = title,
                content = content,
                tags = encodeTags(tags),
                imagePaths = encodePaths(imagePaths),
                createdAt = currentTime,
                updatedAt = currentTime
            )
            inspirationRepository.insert(newInspiration)
        }
    }

    /**
     * 更新灵感
     * @param inspiration 更新后的灵感实体
     */
    fun updateInspiration(inspiration: Inspiration) {
        viewModelScope.launch {
            inspirationRepository.update(
                inspiration.copy(updatedAt = System.currentTimeMillis())
            )
        }
    }

    /**
     * 删除灵感（软删除 + 设置撤销状态 + 启动 5s 撤销倒计时）
     *
     * 流程：
     * 1. 查询灵感实体
     * 2. 移入回收站（软删除）
     * 3. 从灵感表删除
     * 4. 设置 _pendingDeletedInspiration 状态（用于触发 Snackbar）
     * 5. 启动 5s 倒计时，超时后清除状态（灵感已永久保留在回收站）
     *
     * 撤销：用户点 Snackbar "撤销" → undoDeleteInspiration() 从回收站移回灵感表
     *
     * @param id 灵感ID
     */
    fun deleteInspiration(id: Long) {
        viewModelScope.launch {
            runCatching {
                val inspiration = inspirationRepository.getInspirationById(id)
                if (inspiration != null) {
                    // 1. 移入回收站
                    deletedInspirationRepository.insertDeletedInspiration(inspiration)
                    // 2. 从灵感表删除
                    inspirationRepository.deleteById(id)
                    // 3. 设置撤销状态
                    _pendingDeletedInspiration.value = inspiration
                    // 4. 取消之前的倒计时任务（如果有）
                    deleteInspirationTimerJob?.cancel()
                    // 5. 启动新的倒计时（5s 后清除状态，灵感永久保留在回收站）
                    deleteInspirationTimerJob = launch {
                        delay(UNDO_DELETE_INSPIRATION_DELAY_MS)
                        _pendingDeletedInspiration.value = null
                    }
                }
            }
        }
    }

    /**
     * 删除灵感实体（先插入回收站再删除，软删除流程）
     * @param inspiration 灵感实体
     */
    fun deleteInspiration(inspiration: Inspiration) {
        viewModelScope.launch {
            deletedInspirationRepository.insertDeletedInspiration(inspiration)
            inspirationRepository.delete(inspiration)
        }
    }

    // ========== 菜单与隐藏详情方法 ==========

    /**
     * 设置菜单弹窗展开状态
     * @param expanded 是否展开
     */
    fun setMenuExpanded(expanded: Boolean) {
        _menuExpanded.value = expanded
    }

    /**
     * 切换隐藏详情状态
     */
    fun toggleHideDetails() {
        _hideDetails.value = !_hideDetails.value
    }

    // ========== 批量选择模式方法 ==========

    /**
     * 进入批量选择模式（清空已选）
     */
    fun enterBatchMode() {
        _isBatchMode.value = true
        _selectedInspirationIds.value = emptySet()
    }

    /**
     * 退出批量选择模式（清空已选）
     */
    fun exitBatchMode() {
        _isBatchMode.value = false
        _selectedInspirationIds.value = emptySet()
    }

    /**
     * 切换灵感选中状态
     * @param inspirationId 灵感 ID
     */
    fun toggleSelection(inspirationId: Long) {
        _selectedInspirationIds.value = _selectedInspirationIds.value.let {
            if (it.contains(inspirationId)) it - inspirationId else it + inspirationId
        }
    }

    /**
     * 全选当前所有灵感
     */
    fun selectAllInspirations() {
        _selectedInspirationIds.value = _inspirations.value.map { it.id }.toSet()
    }

    /**
     * 清空选中（不退出批量模式）
     */
    fun clearSelection() {
        _selectedInspirationIds.value = emptySet()
    }

    /**
     * 批量删除选中的灵感（软删除 + 设置撤销状态 + 启动 5s 撤销倒计时）
     *
     * 流程：
     * 1. 遍历选中 ID，逐个软删除到回收站
     * 2. 退出批量模式
     * 3. 设置 _pendingBatchDeletedInspirations 状态（用于触发 Snackbar）
     * 4. 启动 5s 倒计时，超时后清除状态（灵感已永久保留在回收站）
     *
     * 撤销：用户点 Snackbar "全部撤销" → undoBatchDeleteInspiration() 全部从回收站移回
     */
    fun batchDeleteInspirations() {
        val selectedIds = _selectedInspirationIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            // 1. 遍历软删除到回收站
            val deletedList = mutableListOf<Inspiration>()
            selectedIds.forEach { id ->
                val inspiration = inspirationRepository.getInspirationById(id)
                if (inspiration != null) {
                    deletedInspirationRepository.insertDeletedInspiration(inspiration)
                    inspirationRepository.deleteById(id)
                    deletedList.add(inspiration)
                }
            }
            // 2. 退出批量模式
            exitBatchMode()
            // 3. 设置批量撤销状态
            _pendingBatchDeletedInspirations.value = deletedList
            // 4. 取消之前的倒计时任务（如果有）
            deleteInspirationTimerJob?.cancel()
            // 5. 启动新的倒计时
            deleteInspirationTimerJob = launch {
                delay(UNDO_DELETE_INSPIRATION_DELAY_MS)
                _pendingBatchDeletedInspirations.value = null
            }
        }
    }

    /**
     * 撤销单个灵感删除（从回收站移回灵感表）
     *
     * 调用时机：用户点击 Snackbar "撤销" 按钮
     * 关键：必须先从回收站永久删除，再重新插入灵感表（id 保持一致）
     */
    fun undoDeleteInspiration() {
        viewModelScope.launch {
            val inspiration = _pendingDeletedInspiration.value ?: return@launch
            // 1. 取消倒计时任务
            deleteInspirationTimerJob?.cancel()
            deleteInspirationTimerJob = null
            // 2. 从回收站永久删除
            deletedInspirationRepository.permanentlyDelete(inspiration.id)
            // 3. 重新插入灵感表
            inspirationRepository.insert(inspiration)
            // 4. 清除状态
            _pendingDeletedInspiration.value = null
        }
    }

    /**
     * 撤销批量灵感删除
     *
     * 调用时机：用户点击 Snackbar "全部撤销" 按钮
     */
    fun undoBatchDeleteInspiration() {
        viewModelScope.launch {
            val list = _pendingBatchDeletedInspirations.value ?: return@launch
            // 1. 取消倒计时任务
            deleteInspirationTimerJob?.cancel()
            deleteInspirationTimerJob = null
            // 2. 逐个从回收站移回灵感表
            list.forEach { inspiration ->
                deletedInspirationRepository.permanentlyDelete(inspiration.id)
                inspirationRepository.insert(inspiration)
            }
            // 3. 清除状态
            _pendingBatchDeletedInspirations.value = null
        }
    }

    /**
     * 清除单个待撤销状态
     *
     * 调用时机：Snackbar 自动消失（用户未点撤销按钮），清空倒计时和状态
     */
    fun clearPendingDeletedInspiration() {
        deleteInspirationTimerJob?.cancel()
        deleteInspirationTimerJob = null
        _pendingDeletedInspiration.value = null
    }

    /**
     * 清除批量待撤销状态
     *
     * 调用时机：Snackbar 自动消失（用户未点撤销按钮），清空倒计时和状态
     */
    fun clearPendingBatchDeletedInspiration() {
        deleteInspirationTimerJob?.cancel()
        deleteInspirationTimerJob = null
        _pendingBatchDeletedInspirations.value = null
    }

    /**
     * 批量置顶/取消置顶选中的灵感
     * 逻辑：如果所有选中灵感都已置顶则取消置顶，否则全部置顶
     */
    fun batchPinInspirations() {
        val selectedIds = _selectedInspirationIds.value
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            val inspirations = _inspirations.value.filter { it.id in selectedIds }
            val allPinned = inspirations.all { it.isPinned }
            inspirations.forEach { insp ->
                val updated = insp.copy(isPinned = !allPinned)
                inspirationRepository.update(updated)
            }
            exitBatchMode()
        }
    }

    // ========== 搜索方法 ==========

    /**
     * 搜索灵感
     * @param query 搜索关键词
     */
    fun search(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                loadInspirations()
            } else {
                inspirationRepository.searchInspirations(query).collect { list ->
                    _inspirations.value = list
                }
            }
        }
    }

    /**
     * 清空搜索
     */
    fun clearSearch() {
        _searchQuery.value = ""
        loadInspirations()
    }

    // ========== 关联管理方法 ==========

    /**
     * 加载指定灵感的关联关系
     *
     * v2026-07-22 增强：关联列表变化时自动调用 [refreshRelationTitles] 增量加载标题，
     * 供详情页/编辑页的 Chip 显示。
     *
     * @param inspirationId 灵感ID
     */
    fun loadRelations(inspirationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.getRelations("inspiration", inspirationId, 0).collect { relations ->
                _relations.value = relations
                // v2026-07-22 新增：关联列表变化时增量刷新标题缓存
                refreshRelationTitles()
            }
        }
    }

    /**
     * 刷新关联标题缓存（v2026-07-22 新增）
     *
     * 在 [_relations] 变化时调用，异步加载每个关联目标卡片的标题。
     * 已缓存的标题不会重复加载（增量更新）。
     * 标题为 null 的卡片（已删除）显示"已删除"占位。
     */
    private fun refreshRelationTitles() {
        viewModelScope.launch {
            val allRelations = _relations.value
            val existingTitles = _relationTitles.value
            val newTitles = mutableMapOf<Long, String>()
            allRelations.forEach { relation ->
                if (relation.id !in existingTitles) {
                    val title = cardRelationRepository.getCardTitle(relation.targetType, relation.targetId)
                    if (title != null) {
                        newTitles[relation.id] = title
                    } else {
                        // 卡片已删除，用占位文字
                        newTitles[relation.id] = "已删除"
                    }
                }
            }
            if (newTitles.isNotEmpty()) {
                _relationTitles.value = existingTitles + newTitles
            }
        }
    }

    /**
     * 加载卡片详情（v2026-07-22 新增，供 LinkedCardPreviewDialog 按类型差异化展示）
     *
     * 调用时机：用户点击关联 Chip，Dialog 弹出前。
     * 并发保护：每次调用重置 [_cardDetail] 为 null，[_cardDetailLoading] 为 true。
     *
     * @param cardType 卡片类型（"todo" / "inspiration" / "date"）
     * @param cardId 卡片数据库 ID
     */
    fun loadCardDetail(cardType: String, cardId: Long) {
        viewModelScope.launch {
            _cardDetailLoading.value = true
            _cardDetail.value = null
            val detail = cardRelationRepository.loadCardDetail(cardType, cardId)
            _cardDetail.value = detail
            _cardDetailLoading.value = false
        }
    }

    /**
     * 清空卡片详情状态（v2026-07-22 新增）
     *
     * 调用时机：用户关闭预览 Dialog。
     * 防止下次打开 Dialog 时短暂显示旧数据。
     */
    fun clearCardDetail() {
        _cardDetail.value = null
        _cardDetailLoading.value = false
    }

    /**
     * 添加关联关系
     *
     * v2026-07-22 增强：添加成功后立即加载新关联的标题并写入 [_relationTitles] 缓存，
     * 避免 Chip 显示"加载中…"。
     *
     * @param inspirationId 灵感ID
     * @param targetType 目标类型 ("todo" | "date" | "inspiration")
     * @param targetId 目标实体ID
     */
    fun addRelation(inspirationId: Long, targetType: String, targetId: Long) {
        viewModelScope.launch {
            val relation = CardRelation(
                sourceType = "inspiration",
                sourceId = inspirationId,
                targetType = targetType,
                targetId = targetId
            )
            val result = cardRelationRepository.addRelation(relation)
            if (result > 0) {
                val newRelation = relation.copy(id = result)
                _relations.value = _relations.value + newRelation
                // v2026-07-22 新增：立即加载新关联的标题，避免 Chip 显示"加载中…"
                val title = cardRelationRepository.getCardTitle(targetType, targetId)
                _relationTitles.value = _relationTitles.value + (result to (title ?: "已删除"))
            }
        }
    }

    /**
     * 批量添加关联关系（v2026-07-22 新增，供详情页 RelationPickerBottomSheet 使用）
     *
     * 串行处理所有卡片，累积结果后一次性更新 [_relations] 和 [_relationTitles]，
     * 避免多次 emit 导致 UI 抖动。
     *
     * @param inspirationId 灵感ID
     * @param cards 待添加的卡片列表（Pair<targetType, targetId>）
     */
    fun addRelations(inspirationId: Long, cards: List<Pair<String, Long>>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val currentList = _relations.value.toMutableList()
            val newTitles = mutableMapOf<Long, String>()
            var addedCount = 0

            cards.forEach { (targetType, targetId) ->
                // 跳过已在内存列表中的（避免无谓 DB 调用）
                val existsInMemory = currentList.any {
                    it.targetType == targetType && it.targetId == targetId
                }
                if (existsInMemory) return@forEach

                val relation = CardRelation(
                    sourceType = "inspiration",
                    sourceId = inspirationId,
                    groupId = 0,
                    targetType = targetType,
                    targetId = targetId
                )
                val result = cardRelationRepository.addRelation(relation)
                when (result) {
                    -1L -> { /* 已关联，静默跳过 */ }
                    -2L -> { /* 超过上限，静默跳过 */ }
                    else -> {
                        if (result > 0) {
                            currentList.add(relation.copy(id = result))
                            val title = cardRelationRepository.getCardTitle(targetType, targetId)
                            newTitles[result] = title ?: "已删除"
                            addedCount++
                        }
                    }
                }
            }

            // 一次性更新状态（避免多次 emit 导致 UI 抖动）
            if (addedCount > 0) {
                val distinctList = currentList.distinctBy { "${it.targetType}_${it.targetId}" }
                _relations.value = distinctList
                if (newTitles.isNotEmpty()) {
                    _relationTitles.value = _relationTitles.value + newTitles
                }
            }
        }
    }

    /**
     * 删除关联关系
     *
     * v2026-07-22 增强：同步清理 [_relationTitles] 中对应的标题缓存。
     *
     * @param relationId 关联ID
     */
    fun deleteRelation(relationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.removeRelationById(relationId)
            _relations.value = _relations.value.filter { it.id != relationId }
            // v2026-07-22 新增：清理已删除关联的标题缓存
            _relationTitles.value = _relationTitles.value.filter { it.key != relationId }
        }
    }

    /**
     * 删除指定关联
     * @param inspirationId 灵感ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     */
    fun deleteRelation(inspirationId: Long, targetType: String, targetId: Long) {
        viewModelScope.launch {
            cardRelationRepository.removeRelation("inspiration", inspirationId, 0, targetType, targetId)
            _relations.value = _relations.value.filter { !(it.targetType == targetType && it.targetId == targetId) }
        }
    }

    /**
     * 搜索卡片（用于关联选择）
     * @param query 搜索关键词
     * @param callback 搜索结果回调
     */
    fun searchCards(query: String, callback: (List<CardSearchResult>) -> Unit) {
        viewModelScope.launch {
            val results = cardRelationRepository.searchCards(query)
            callback(results)
        }
    }

    // ========== 状态切换方法 ==========

    /**
     * 切换置顶状态
     * @param id 灵感ID
     */
    fun togglePin(id: Long) {
        viewModelScope.launch {
            val currentList = _inspirations.value
            val inspiration = currentList.find { it.id == id } ?: return@launch
            inspirationRepository.togglePin(id, !inspiration.isPinned)
        }
    }

    /**
     * 更新灵感的创建日期时间
     * @param id 灵感ID
     * @param newDateTime 新的日期时间戳（毫秒）
     */
    fun updateInspirationDateTime(id: Long, newDateTime: Long) {
        viewModelScope.launch {
            val currentList = _inspirations.value
            val inspiration = currentList.find { it.id == id } ?: return@launch
            inspirationRepository.update(
                inspiration.copy(
                    createdAt = newDateTime,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 更新指定灵感的标签
     *
     * 修改后的 tags 通过 encodeTags 编码为 JSON 字符串后持久化。
     * 数据库更新后，_inspirations 流自动触发，UI 自动刷新。
     *
     * @param id 灵感ID
     * @param newTags 新标签列表
     */
    fun updateTags(id: Long, newTags: List<String>) {
        viewModelScope.launch {
            val currentList = _inspirations.value
            val inspiration = currentList.find { it.id == id } ?: return@launch
            inspirationRepository.update(
                inspiration.copy(
                    tags = encodeTags(newTags),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * 切换归档状态
     * @param id 灵感ID
     */
    fun toggleArchive(id: Long) {
        viewModelScope.launch {
            val currentList = _inspirations.value
            val inspiration = currentList.find { it.id == id } ?: return@launch
            inspirationRepository.toggleArchive(id, !inspiration.isArchived)
        }
    }

    // ========== 编辑状态管理 ==========

    /**
     * 设置当前编辑的灵感
     * @param inspiration 灵感实体（null表示新建模式）
     */
    fun setEditingInspiration(inspiration: Inspiration?) {
        _editingInspiration.value = inspiration
        if (inspiration != null) {
            loadRelations(inspiration.id)
        } else {
            _relations.value = emptyList()
        }
    }

    /**
     * 根据ID获取灵感（用于编辑模式加载）
     * @param id 灵感ID
     * @return 灵感实体，不存在返回null
     */
    suspend fun getInspirationById(id: Long): Inspiration? =
        inspirationRepository.getInspirationById(id)

    // ========== 辅助方法 ==========

    /**
     * 将时间戳格式化为日期分组 Key
     * 格式："2026年5月27日 周三"
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的日期字符串
     */
    private fun formatDateKey(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
        return "${calendar.get(Calendar.YEAR)}年${calendar.get(Calendar.MONTH) + 1}月${calendar.get(Calendar.DAY_OF_MONTH)}日 周${weekdays[calendar.get(Calendar.DAY_OF_WEEK) - 1]}"
    }

    /**
     * 将时间戳格式化为年月分组 Key
     * 格式："2026.07"
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的年月字符串
     */
    private fun formatYearMonthKey(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%04d.%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
    }

    /**
     * 获取指定日期的灵感列表
     * @param year 年
     * @param month 月（1-12）
     * @param day 日
     * @return 当天的灵感列表
     */
    fun getInspirationsByDate(year: Int, month: Int, day: Int): List<Inspiration> {
        return _inspirations.value.filter { inspiration ->
            val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
            cal.get(Calendar.YEAR) == year &&
            cal.get(Calendar.MONTH) + 1 == month &&
            cal.get(Calendar.DAY_OF_MONTH) == day
        }.sortedByDescending { it.createdAt }
    }

    /**
     * 获取指定月份每天的灵感条数（用于日历显示圆点）
     * @param year 年
     * @param month 月（1-12）
     * @return 日期 -> 条数 的映射
     */
    fun getCalendarInspirationCount(year: Int, month: Int): Map<Int, Int> {
        return _inspirations.value
            .filter { inspiration ->
                val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
                cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) + 1 == month
            }
            .groupBy { inspiration ->
                val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
                cal.get(Calendar.DAY_OF_MONTH)
            }
            .mapValues { it.value.size }
    }

    /**
     * 编码标签列表为JSON字符串
     * @param tags 标签列表
     * @return JSON字符串
     */
    fun encodeTags(tags: List<String>): String = TagUtils.encodeTags(tags)

    /**
     * 编码图片路径列表为JSON字符串
     * @param paths 路径列表
     * @return JSON字符串
     */
    fun encodePaths(paths: List<String>): String = TagUtils.encodePaths(paths)

    /**
     * 解码标签JSON字符串为列表
     * @param tagsJson JSON字符串
     * @return 标签列表
     */
    fun decodeTags(tagsJson: String): List<String> = TagUtils.decodeTags(tagsJson)

    /**
     * 解码图片路径JSON字符串为列表
     * @param pathsJson JSON字符串
     * @return 路径列表
     */
    fun decodePaths(pathsJson: String): List<String> = TagUtils.decodePaths(pathsJson)

    /**
     * 格式化时间显示（HH:mm）
     * @param timestamp 时间戳
     * @return 格式化后的时间字符串
     */
    fun formatTime(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return String.format("%02d:%02d", hour, minute)
    }
}
