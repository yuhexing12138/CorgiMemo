package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.InspirationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

    /** 历史标签列表（从所有灵感聚合去重排序，用于 TagPickerSheet 快速选择） */
    val savedTags: StateFlow<List<String>> =
        _inspirations.map { list ->
            aggregateSavedTags(list.map { decodeTags(it.tags) })
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

    /** 是否正在加载 */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ========== 初始化 ==========

    init {
        loadInspirations()
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
     * 加载所有灵感
     */
    fun loadInspirations() {
        viewModelScope.launch {
            _isLoading.value = true
            inspirationRepository.getAllInspirations().collect { list ->
                _inspirations.value = list
                _isLoading.value = false

                // 标记数据已初始化完成（首次加载后不再重置，避免闪烁）
                if (!_isDataInitialized.value) {
                    _isDataInitialized.value = true
                }
            }
        }
    }

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
     * 删除灵感（级联删除关联）
     * @param id 灵感ID
     */
    fun deleteInspiration(id: Long) {
        viewModelScope.launch {
            inspirationRepository.deleteById(id)
        }
    }

    /**
     * 删除灵感实体
     * @param inspiration 灵感实体
     */
    fun deleteInspiration(inspiration: Inspiration) {
        viewModelScope.launch {
            inspirationRepository.delete(inspiration)
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
     * @param inspirationId 灵感ID
     */
    fun loadRelations(inspirationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.getRelations("inspiration", inspirationId).collect { relations ->
                _relations.value = relations
            }
        }
    }

    /**
     * 添加关联关系
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
                _relations.value = _relations.value + relation.copy(id = result)
            }
        }
    }

    /**
     * 删除关联关系
     * @param relationId 关联ID
     */
    fun deleteRelation(relationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.removeRelationById(relationId)
            _relations.value = _relations.value.filter { it.id != relationId }
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
            cardRelationRepository.removeRelation("inspiration", inspirationId, targetType, targetId)
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
    fun encodeTags(tags: List<String>): String {
        if (tags.isEmpty()) return ""
        return buildString {
            append("[")
            tags.forEachIndexed { index, tag ->
                if (index > 0) append(",")
                append("\"$tag\"")
            }
            append("]")
        }
    }

    /**
     * 编码图片路径列表为JSON字符串
     * @param paths 路径列表
     * @return JSON字符串
     */
    fun encodePaths(paths: List<String>): String {
        if (paths.isEmpty()) return ""
        return buildString {
            append("[")
            paths.forEachIndexed { index, path ->
                if (index > 0) append(",")
                append("\"$path\"")
            }
            append("]")
        }
    }

    /**
     * 解码标签JSON字符串为列表
     * @param tagsJson JSON字符串
     * @return 标签列表
     */
    fun decodeTags(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        return try {
            tagsJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解码图片路径JSON字符串为列表
     * @param pathsJson JSON字符串
     * @return 路径列表
     */
    fun decodePaths(pathsJson: String): List<String> {
        if (pathsJson.isBlank()) return emptyList()
        return try {
            pathsJson
                .removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

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
