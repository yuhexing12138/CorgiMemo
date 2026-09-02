package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.model.UserType
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import com.mohamedrejeb.richeditor.model.RichTextState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.corgimemo.app.util.TagUtils
import javax.inject.Inject

/**
 * 灵感编辑 ViewModel
 * 管理灵感记录的编辑状态、保存/加载、撤销/重做等操作
 * 支持内容块（图片/语音）、子任务、标签、地理围栏等高级功能
 */
@HiltViewModel
class InspirationEditViewModel @Inject constructor(
    private val inspirationRepository: InspirationRepository,
    private val categoryRepository: CategoryRepository,
    private val corgiPreferences: CorgiPreferences,
    private val cardRelationRepository: CardRelationRepository,
    private val contentBlockDao: ContentBlockDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /** 防抖导出任务引用：用于延迟执行 MarkdownParser.export() */
    private var _debounceJob: Job? = null

    // ========== 基础字段状态 ==========

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    /**
     * 当前编辑页是否有未保存的修改（v2026-07-22 新增）
     *
     * 用途：编辑页 UI 拦截"返回"操作（顶部 ← 按钮 / 系统返回键）时判断是否需要弹"放弃编辑"确认框，
     * 避免用户误触返回导致未保存草稿被静默丢失。
     *
     * 同步规则：
     * - 任何 setXxx() 调用（除内部初始化）→ `_isDirty.value = true`
     * - loadInspiration() 成功加载已保存数据后 → `_isDirty.value = false`（重置为干净基线）
     * - saveInspiration() 成功持久化后 → `_isDirty.value = false`（已保存）
     *
     * 不需要追踪"原始加载值 vs 当前值"的精确对比——只要 setXxx 触发过就认为脏，
     * 这是性能与精度的折中（用户也可能把内容改回去，但只要改过就认为有未保存意图）。
     */
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    /**
     * 当前编辑灵感的"创建时间戳"（v2026-07-31 新增）
     *
     * 用途：编辑页"标题和正文之间"的时间戳行显示。
     *
     * 值来源：
     * - **新建模式**：进入编辑页时记录 `System.currentTimeMillis()`，
     *   整个编辑过程保持不变（不会每秒刷新）。
     * - **编辑模式**：[loadInspiration] 加载已有灵感时，从 `existingInspiration.createdAt`
     *   同步过来，显示灵感首次创建的时间。
     *
     * 设计原因：用户期望时间戳是"灵感创建时间"，而非"编辑当前时间"。
     * 新建时还没有真实时间戳，但需要在编辑页立即显示一个稳定时间戳，
     * 因此使用 ViewModel 初始化时记录的 `currentTimeMillis()` 占位，
     * 保存到数据库后正式成为 createdAt。
     */
    private val _createdAt = MutableStateFlow(System.currentTimeMillis())
    val createdAt: StateFlow<Long> = _createdAt.asStateFlow()

    private val _categoryId = MutableStateFlow(0L)
    val categoryId: StateFlow<Long> = _categoryId.asStateFlow()

    private val _priority = MutableStateFlow(1)
    val priority: StateFlow<Int> = _priority.asStateFlow()

    private val _startDate = MutableStateFlow<Long?>(null)
    val startDate: StateFlow<Long?> = _startDate.asStateFlow()

    /** 截止时间状态（时间戳，毫秒） */
    private val _dueDate = MutableStateFlow<Long?>(null)
    val dueDate: StateFlow<Long?> = _dueDate.asStateFlow()

    private val _estimatedDurationMinutes = MutableStateFlow<Int?>(null)
    val estimatedDurationMinutes: StateFlow<Int?> = _estimatedDurationMinutes.asStateFlow()

    // 地理围栏相关字段
    private val _geofenceLat = MutableStateFlow<Double?>(null)
    val geofenceLat: StateFlow<Double?> = _geofenceLat.asStateFlow()

    private val _geofenceLng = MutableStateFlow<Double?>(null)
    val geofenceLng: StateFlow<Double?> = _geofenceLng.asStateFlow()

    private val _geofenceRadius = MutableStateFlow<Float?>(100f)
    val geofenceRadius: StateFlow<Float?> = _geofenceRadius.asStateFlow()

    private val _geofenceType = MutableStateFlow<Int?>(null)
    val geofenceType: StateFlow<Int?> = _geofenceType.asStateFlow()

    private val _geofenceEnabled = MutableStateFlow(false)
    val geofenceEnabled: StateFlow<Boolean> = _geofenceEnabled.asStateFlow()

    private val _geofenceAddress = MutableStateFlow<String?>(null)
    val geofenceAddress: StateFlow<String?> = _geofenceAddress.asStateFlow()

    // 子任务相关
    private val _subTasks = MutableStateFlow<List<SubTask>>(emptyList())
    val subTasks: StateFlow<List<SubTask>> = _subTasks.asStateFlow()

    // 分类相关
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _isCategoriesLoaded = MutableStateFlow(false)
    val isCategoriesLoaded: StateFlow<Boolean> = _isCategoriesLoaded.asStateFlow()

    // 语音备注相关状态
    private val _voiceNotePath = MutableStateFlow<String?>(null)
    val voiceNotePath: StateFlow<String?> = _voiceNotePath.asStateFlow()

    private val _voiceDuration = MutableStateFlow<Int?>(null)
    val voiceDuration: StateFlow<Int?> = _voiceDuration.asStateFlow()

    /** 图片路径列表状态（存储内部存储中的绝对路径） */
    private val _imagePaths = MutableStateFlow<List<String>>(emptyList())
    val imagePaths: StateFlow<List<String>> = _imagePaths.asStateFlow()

    // ========== 标签相关状态 ==========

    /**
     * 标签列表状态（v2026-07-31 重构：实时派生自 Markdown 内容）
     *
     * **设计变更背景**：
     * 原本 `_tags` 是独立的 MutableStateFlow，由 UI 层通过 `updateTags()` 同步维护，
     * 与正文内容分离存储。Phase 2 重构引入 compose-rich-editor 的 # Trigger 后，
     * 标签以 atomic token 形式内联在正文中（如 `[#工作](trigger:hashtag:工作)`），
     * 用户在正文里直接增删标签 token，独立维护 `_tags` 会与正文不同步。
     *
     * **新方案（实时派生）**：
     * - 删除 `_tags` 可变状态
     * - `tags` 改为 `_contentFormat` 的 map 派生 StateFlow（定义在 `_contentFormat` 之后）
     * - 每当 `_contentFormat` 变化（用户输入触发 `scheduleFormatExport` 防抖更新，
     *   或 `performSave` 同步更新）时，自动从 Markdown 提取标签 token
     * - 保存时 `encodeTags(tags.value)` 即可，无需额外同步逻辑
     *
     * **兼容性**：
     * - UI 层 `viewModel.tags.collectAsState()` 调用不变
     * - 旧数据（contentFormat 中无 token 但 inspiration.tags 非空）由
     *   [loadInspiration] 中的迁移逻辑处理：把旧标签追加为 token 到 markdown
     */

    /**
     * 从 Markdown 文本中提取所有 # Trigger 产生的标签 token
     *
     * compose-rich-editor 库将 # Trigger 选中的标签序列化为 Markdown 链接形式：
     * ```
     * [#标签名](trigger:hashtag:标签ID)
     * ```
     * 本方法用正则匹配所有该模式的 token，提取 `标签名`（去掉 # 前缀）并去重。
     *
     * **为何用正则而非解析 Markdown AST**：
     * - Markdown 导出后是纯字符串，无现成 AST 工具
     * - 正则匹配简单高效，且 token 格式由库固定，不会变化
     * - 即使 Markdown 中有其他普通 `[]()` 链接，由于 URL 部分以 `trigger:hashtag:` 开头，
     *   不会被误匹配
     *
     * @param markdown Markdown 格式的字符串（由 RichTextState.toMarkdown() 导出）
     * @return 去重后的标签名列表（不含 # 前缀），保持首次出现顺序
     */
    private fun extractTagsFromMarkdown(markdown: String): List<String> {
        if (markdown.isBlank()) return emptyList()
        /**
         * 正则解释：
         * - \[#        匹配字面量 "[#"
         * - ([^\]]+)   捕获组 1：标签名（不含 # 前缀），匹配到 "]" 为止
         * - \]         匹配字面量 "]"
         * - \(trigger:hashtag:[^)]*\)  匹配 "(trigger:hashtag:任意ID)"
         *
         * 例：[#工作](trigger:hashtag:工作) → 捕获 "工作"
         */
        val pattern = Regex("""\[#([^\]]+)\]\(trigger:hashtag:[^)]*\)""")
        val tagSet = linkedSetOf<String>()
        pattern.findAll(markdown).forEach { match ->
            val tagName = match.groupValues[1].trim()
            if (tagName.isNotEmpty()) {
                tagSet.add(tagName)
            }
        }
        return tagSet.toList()
    }

    /**
     * 历史标签列表状态
     *
     * 从数据库中所有灵感的 tags 字段聚合、去重后得到的历史标签集合。
     * 用于 TagPickerSheet 展示"曾经使用过的标签"，让用户快速选择而无需重新输入。
     *
     * 加载时机：ViewModel 初始化时一次性加载（first()），不持续监听变化。
     * 这是因为历史标签仅用于辅助选择，不需要实时同步。
     */
    private val _savedTags = MutableStateFlow<List<String>>(emptyList())
    val savedTags: StateFlow<List<String>> = _savedTags.asStateFlow()

    init {
        /** 加载历史标签：从所有灵感的 tags 字段聚合去重 */
        loadSavedTags()
    }

    /**
     * 加载历史标签
     *
     * 从数据库读取所有灵感，解析各自的 tags JSON 字段，
     * 聚合所有标签并去重后更新 _savedTags 状态。
     */
    private fun loadSavedTags() {
        viewModelScope.launch {
            try {
                val allInspirations = inspirationRepository.getAllInspirations().first()
                val tagSet = linkedSetOf<String>()
                allInspirations.forEach { inspiration ->
                    decodeTags(inspiration.tags).forEach { tag ->
                        tagSet.add(tag)
                    }
                }
                _savedTags.value = tagSet.toList()
            } catch (e: Exception) {
                // 加载失败时保持空列表，不影响正常编辑功能
                _savedTags.value = emptyList()
            }
        }
    }

    // ==================== 内容块统一管理（ContentBlock 系统） ====================

    /**
     * 当前内容块列表（由 UI 层同步）
     *
     * UI 层在 contentBlocks 变化时调用 syncContentBlocks() 更新此状态，
     * performSave() 时读取此状态持久化到数据库。
     */
    private val _currentContentBlocks = MutableStateFlow<List<ContentBlock>>(emptyList())

    /**
     * 同步当前内容块列表（UI 层调用）
     *
     * @param blocks 当前 Composable 中的 contentBlocks 列表
     */
    fun syncContentBlocks(blocks: List<ContentBlock>) {
        _currentContentBlocks.value = blocks
    }

    /**
     * 背景颜色状态（ARGB 整数值）
     *
     * 用于持久化用户在编辑页选择的卡片背景色。
     * 默认值为 0xFFFFFFFF（白色/透明背景）。
     *
     * **转换方式**:
     * - 保存到数据库：Color.toArgb() → Int
     * - 从数据库读取：Int → Color(Int)
     */
    private val _backgroundColor = MutableStateFlow(-1) // 默认白色（-1 = 0xFFFFFFFF 作为有符号Int）
    val backgroundColor: StateFlow<Int> = _backgroundColor.asStateFlow()

    /**
     * 富文本格式化内容状态（Markdown 字符串）
     *
     * 存储完整的 Markdown 格式文本，保留 **粗体**、*斜体*、~~删除线~~、
     * 列表等格式信息。用于在编辑页恢复富文本显示。
     *
     * **与 content（纯文本）的关系**:
     * - `content`: 纯文本版本 → 用于搜索、字数统计、列表摘要显示
     * - `contentFormat`: 完整格式版本 → 用于编辑器恢复 AnnotatedString 显示
     *
     * **转换方式**:
     * - 保存时: AnnotatedString → MarkdownParser.export() → 此字段
     * - 加载时: 此字段 → MarkdownParser.parse() → AnnotatedString → 编辑器
     */
    private val _contentFormat = MutableStateFlow("") // 默认空字符串（无格式）
    val contentFormat: StateFlow<String> = _contentFormat.asStateFlow()

    /**
     * 标签列表 StateFlow（v2026-07-31 重构：派生自 `_contentFormat`）
     *
     * **依赖关系**：必须在 `_contentFormat` 定义之后声明，因为 Kotlin 属性初始化按声明顺序执行。
     *
     * **工作原理**：
     * - `_contentFormat` 变化 → `map { extractTagsFromMarkdown(it) }` 自动重新提取
     * - `stateIn(WhileSubscribed(5_000L))`：有订阅者时启动，无订阅者 5 秒后停止以节省资源
     * - `initialValue = emptyList()`：首次订阅前的初始值
     *
     * **触发时机**：
     * - 用户输入触发 [scheduleFormatExport] 防抖更新 `_contentFormat` → 标签自动同步
     * - [performSave] 同步更新 `_contentFormat` → 保存时标签已是最新
     * - [loadInspiration] 加载旧数据后调用 `setMarkdown()` 并更新 `_contentFormat` → 标签恢复
     */
    val tags: StateFlow<List<String>> = _contentFormat
        .map { extractTagsFromMarkdown(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /**
     * 富文本编辑器状态（compose-rich-editor 库）
     *
     * 替代原有的自定义 RichTextEditorState，提供完整的富文本编辑能力：
     * - toggleSpanStyle（粗体/斜体/下划线/删除线）
     * - toggleUnorderedList / toggleOrderedList
     * - toggleCodeSpan（代码块）
     * - addLink（超链接）
     * - setMarkdown / toMarkdown（Markdown 导入导出）
     *
     * **撤销/重做**：本项目的统一时间线撤销栈由 BodyBlocksController 在内存中维护
     * （块级快照 + 光标位置），UI 层读取 bodyBlocks.canUndoTimeline / canRedoTimeline。
     * 此处不再保留独立的 VM 级 Undo/Redo 栈，RichTextState 库自带 history 也已禁用。
     *
     * **UI 层使用**：
     * - 编辑器组件：rememberRichTextState() 初始化，通过此字段访问
     * - 格式工具栏：直接调用 state.toggleSpanStyle() 等方法
     */
    private var _richTextState: RichTextState? = null

    /** 富文本编辑器状态（只读暴露） */
    val richTextState: RichTextState? get() = _richTextState

    /** 关联列表 */
    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations.asStateFlow()

    /**
     * 关联ID → 标题的映射（v2026-07-22 新增，供编辑页 Chip 显示）
     *
     * - key: CardRelation.id
     * - value: 目标卡片的标题（异步加载并缓存，已删除卡片显示"已删除"）
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

    /** 当前编辑中的灵感实体（null 表示新建模式） */
    private var existingInspiration: Inspiration? = null

    // ==================== 基础 Setter 方法 ====================

    /**
     * 设置标题
     * @param title 灵感标题
     */
    fun setTitle(title: String) {
        _title.value = title
        _isDirty.value = true
    }

    /**
     * 设置标题
     * @param title 灵感标题
     */
    fun setTitleWithRecommendation(title: String) {
        _title.value = title
        _isDirty.value = true
    }

    /**
     * 设置纯文本内容
     * @param content 纯文本内容
     */
    fun setContent(content: String) {
        _content.value = content
        _isDirty.value = true
    }

    /**
     * 设置分类 ID
     * @param categoryId 分类 ID
     */
    fun setCategoryId(categoryId: Long) {
        _categoryId.value = categoryId
        _isDirty.value = true
    }

    /**
     * 设置优先级
     * @param priority 优先级值（0=低, 1=中, 2=高）
     */
    fun setPriority(priority: Int) {
        _priority.value = priority
        _isDirty.value = true
    }

    /**
     * 设置开始时间
     * @param startDate 开始时间戳（毫秒）
     */
    fun setStartDate(startDate: Long?) {
        _startDate.value = startDate
        _isDirty.value = true
    }

    /**
     * 设置截止时间
     * 用户在时间选择器中确认后调用
     *
     * @param dueDate 截止时间（毫秒时间戳）
     */
    fun setDueDate(dueDate: Long?) {
        _dueDate.value = dueDate
        _isDirty.value = true
    }

    /**
     * 设置预估时长（分钟）
     * @param minutes 预估时长
     */
    fun setEstimatedDurationMinutes(minutes: Int?) {
        _estimatedDurationMinutes.value = minutes
        _isDirty.value = true
    }

    // 地理围栏相关方法

    /**
     * 设置地理围栏纬度
     * @param lat 纬度值
     */
    fun setGeofenceLat(lat: Double?) {
        _geofenceLat.value = lat
        _isDirty.value = true
    }

    /**
     * 设置地理围栏经度
     * @param lng 经度值
     */
    fun setGeofenceLng(lng: Double?) {
        _geofenceLng.value = lng
        _isDirty.value = true
    }

    /**
     * 设置地理围栏半径
     * @param radius 半径（米）
     */
    fun setGeofenceRadius(radius: Float) {
        _geofenceRadius.value = radius
        _isDirty.value = true
    }

    /**
     * 设置地理围栏类型
     * @param type 类型（0=到达提醒, 1=离开提醒）
     */
    fun setGeofenceType(type: Int) {
        _geofenceType.value = type
        _isDirty.value = true
    }

    /**
     * 设置地理围栏是否启用
     * @param enabled 是否启用
     */
    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
        _isDirty.value = true
    }

    /**
     * 设置地理围栏地址描述
     * @param address 地址字符串
     */
    fun setGeofenceAddress(address: String?) {
        _geofenceAddress.value = address
        _isDirty.value = true
    }

    // 子任务相关方法

    /**
     * 添加子任务
     *
     * @param title 子任务标题
     */
    fun addSubTask(title: String) {
        if (title.isBlank()) return
        val currentList = _subTasks.value
        val newSubTask = SubTask(
            id = 0,
            todoId = existingInspiration?.id ?: 0,
            title = title,
            isCompleted = false,
            order = currentList.size + 1
        )
        _subTasks.value = currentList + newSubTask
        _isDirty.value = true
    }

    /**
     * 删除子任务
     *
     * @param subTask 要删除的子任务
     */
    fun removeSubTask(subTask: SubTask) {
        val currentList = _subTasks.value
        _subTasks.value = currentList.filter { it.id != subTask.id || it.order != subTask.order }
        _isDirty.value = true
    }

    /**
     * 切换子任务完成状态（仅在编辑已有灵感时持久化到数据库）
     * 如果所有子任务完成，会自动完成父任务
     *
     * @param subTask 子任务
     */
    fun toggleSubTaskCompletion(subTask: SubTask) {
        val currentList = _subTasks.value
        val updatedList = currentList.map {
            if (it.id == subTask.id || (it.id == 0L && it.order == subTask.order)) {
                it.copy(isCompleted = !it.isCompleted)
            } else {
                it
            }
        }
        _subTasks.value = updatedList
        _isDirty.value = true

        if (existingInspiration != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.toggleSubTaskCompletion(context, subTask.id)
            }
        }
    }

    // ==================== 标签管理方法 ====================

    /**
     * 标签管理说明（v2026-07-31 Phase 2 重构后）
     *
     * **已删除的方法**：
     * - `updateTags(newTags: List<String>)` —— 标签不再由 UI 层独立维护
     *
     * **新方案**：
     * - 标签以 atomic token 内联在正文中（[#标签名](trigger:hashtag:标签ID)）
     * - UI 层通过 # Trigger + TriggerSuggestions 直接在正文中插入/删除标签 token
     * - `tags` StateFlow 派生自 `_contentFormat`，自动同步，无需手动 update
     * - 保存时 `encodeTags(tags.value)` 自动从 markdown 提取最新标签列表
     *
     * **UI 层迁移要点**：
     * - 删除 TagPickerSheet 弹窗调用
     * - 删除 FlowRow 标签展示区
     * - 底部 # 按钮改为 `richTextState.addTextAfterSelection("#")` 触发建议弹窗
     */

    // ==================== 加载方法 ====================

    /**
     * 加载灵感及关联数据
     *
     * 从数据库加载指定 ID 的灵感记录，并恢复所有编辑状态：
     * - 基础字段（标题、内容、分类等）
     * - 时间字段（开始/截止/提醒时间）
     * - 地理围栏配置
     * - 子任务列表
     * - 图片/语音附件
     * - 背景颜色和富文本格式
     * - 标签列表（从 JSON 解码）
     * - 关联关系
     * - Undo/Redo 撤销栈
     *
     * @param inspirationId 灵感 ID
     */
    fun loadInspiration(inspirationId: Long) {
        viewModelScope.launch {
            inspirationRepository.getInspirationById(inspirationId)?.let { inspiration ->
                existingInspiration = inspiration
                _title.value = inspiration.title
                _content.value = inspiration.content
                /**
                 * v2026-07-31 同步：编辑模式下，从已有灵感恢复 createdAt，
                 * 让"标题和正文之间"的时间戳行显示灵感首次创建时间，而非 ViewModel 初始化时记录的占位时间。
                 */
                _createdAt.value = inspiration.createdAt
                _categoryId.value = inspiration.categoryId
                _priority.value = inspiration.priority
                _startDate.value = inspiration.startDate
                _dueDate.value = inspiration.dueDate
                _estimatedDurationMinutes.value = inspiration.estimatedDurationMinutes
                _geofenceLat.value = inspiration.geofenceLat
                _geofenceLng.value = inspiration.geofenceLng
                _geofenceRadius.value = inspiration.geofenceRadius
                _geofenceType.value = inspiration.geofenceType
                _geofenceEnabled.value = inspiration.geofenceEnabled
                _geofenceAddress.value = inspiration.geofenceAddress

                /**
                 * v2026-07-25 三写存储重构：附件信息不再从 inspiration 字段加载
                 *
                 * 旧逻辑：
                 * - _voiceNotePath / _voiceDuration 从 inspiration.voiceNotePath / voiceDuration 加载
                 * - _imagePaths 从 inspiration.imagePaths（JSON 数组）解析
                 *
                 * 新逻辑：
                 * - 附件信息已迁移到 content_blocks 表（单一数据源）
                 * - 这些 StateFlow 保持默认空值
                 * - UI 层（InspirationEditScreen）从 content_blocks 表加载附件到 contentBlocks
                 * - Migration 46→47 已清空 inspiration.imagePaths/voiceNotePath 等旧字段
                 */

                /**
                 * v2026-07-31 Phase 2 重构：标签数据迁移逻辑
                 *
                 * **旧行为**：从 inspiration.tags（JSON）解码后赋给 _tags MutableStateFlow
                 * **新行为**：标签从 markdown 派生，不再独立存储
                 *
                 * **迁移场景**：
                 * - **新数据**（Phase 2 之后保存的灵感）：contentFormat 已包含
                 *   [#xxx](trigger:hashtag:xxx) 形式的 token，setMarkdown 后自动恢复
                 * - **旧数据**（Phase 2 之前保存的灵感）：contentFormat 中无 token，
                 *   但 inspiration.tags 字段非空。需要把旧标签以 token 形式追加到 markdown 末尾，
                 *   让用户在编辑器中看到旧的标签，并能在保存时正确序列化。
                 *
                 * **迁移策略**：
                 * 1. 先加载原始 contentFormat 到 _contentFormat 和 RichTextState
                 * 2. 从 markdown 中提取现有标签 token
                 * 3. 若 inspiration.tags 中的标签未在 markdown 中出现，则追加为 token 到 markdown 末尾
                 * 4. 更新 _contentFormat 并重新 setMarkdown
                 */
                val legacyTags: List<String> = if (inspiration.tags.isNotBlank()) {
                    decodeTags(inspiration.tags)
                } else {
                    emptyList()
                }

                /** 加载背景颜色（从 ARGB 整数值恢复为 Compose Color） */
                _backgroundColor.value = inspiration.backgroundColor

                /** 加载富文本格式化内容（Markdown 字符串） */
                _contentFormat.value = inspiration.contentFormat
                /** 使用库的 setMarkdown 恢复 RichTextState 格式（若已注入） */
                val markdownToRestore = inspiration.contentFormat
                _richTextState?.let { state ->
                    if (markdownToRestore.isNotBlank()) {
                        state.setMarkdown(markdownToRestore)
                        _content.value = state.annotatedString.text
                    }
                }

                /**
                 * 旧数据迁移：把 inspiration.tags 中未在 markdown 出现的标签追加为 token
                 *
                 * 触发条件：
                 * - legacyTags 非空（旧数据有标签）
                 * - markdown 中已有的标签集合不含某个旧标签
                 *
                 * 追加格式：在 markdown 末尾追加 ` #标签名` 然后用 token 链接包裹：
                 *   ` [#标签名](trigger:hashtag:标签名)`
                 * 注意：需要先在 RichTextState 中注册 # Trigger（UI 层负责），
                 * 但此处仅操作 markdown 字符串，setMarkdown 时库会自动解析 token。
                 */
                if (legacyTags.isNotEmpty()) {
                    val existingTags = extractTagsFromMarkdown(_contentFormat.value).toSet()
                    val tagsToMigrate = legacyTags.filter { it !in existingTags }
                    if (tagsToMigrate.isNotEmpty()) {
                        /**
                         * 构造迁移后的 markdown：在原 markdown 末尾追加标签 token
                         *
                         * 格式说明：
                         * - 每个标签 token 之间用空格分隔
                         * - 若原 markdown 非空且不以空格结尾，则先追加一个空格
                         * - token 格式：[#标签名](trigger:hashtag:标签名)
                         *   （id 与 label 相同，因为本项目标签无独立 id 体系）
                         */
                        val migratedMarkdown = buildString {
                            append(_contentFormat.value)
                            if (_contentFormat.value.isNotEmpty() && !_contentFormat.value.endsWith(" ")) {
                                append(" ")
                            }
                            tagsToMigrate.forEach { tag ->
                                append("[#$tag](trigger:hashtag:$tag) ")
                            }
                            /** 移除末尾多余的空格 */
                            val result = toString().trimEnd()
                            clear()
                            append(result)
                        }
                        _contentFormat.value = migratedMarkdown
                        /** 重新 setMarkdown 以让 RichTextState 解析新追加的 token */
                        _richTextState?.let { state ->
                            state.setMarkdown(migratedMarkdown)
                            _content.value = state.annotatedString.text
                        }
                    }
                }

                // v2026-09-02：原「从 DataStore 恢复跨会话 Undo/Redo 栈」已移除（VM 旧撤销栈废弃）

                // 加载关联关系（sourceType 为 "inspiration"，groupId=0 主分组）
                _relations.value = cardRelationRepository.getRelationsBlocking("inspiration", inspirationId, 0)
                // v2026-07-22 新增：加载关联后增量刷新标题缓存
                refreshRelationTitles()

                /**
                 * v2026-08-01 Phase 3：关联数据迁移逻辑
                 *
                 * 把已有 _relations 转换为 @ token 追加到 markdown 末尾，
                 * 让用户在编辑器中看到已关联的卡片（以 atomic token 形式内联）。
                 *
                 * **迁移策略**：
                 * 1. 检测 contentFormat 中是否已含 @ mention token
                 * 2. 若无但 _relations 非空，则把每个关联转换为 token 追加到 markdown 末尾
                 * 3. token 格式：[@标题](trigger:mention:类型:ID)
                 *    - id 格式：`类型:ID`（如 `todo:123`），用于序列化和反序列化
                 *    - label 格式：`@标题`（如 `@买菜`），用于显示
                 *
                 * **注意**：
                 * - 关联的真相源仍是 card_relations 表，token 仅作视觉展示
                 * - 若用户删除 token，关联不会自动删除（需通过其他入口）
                 * - 若 contentFormat 已含 @ token（新数据），跳过迁移避免重复
                 */
                val hasMentionToken = _contentFormat.value.contains("trigger:mention")
                if (!hasMentionToken && _relations.value.isNotEmpty()) {
                    val mentionTokens = _relations.value.mapNotNull { relation ->
                        val title = _relationTitles.value[relation.id] ?: return@mapNotNull null
                        val tokenId = "${relation.targetType}:${relation.targetId}"
                        "[@$title](trigger:mention:$tokenId)"
                    }
                    if (mentionTokens.isNotEmpty()) {
                        val migratedMarkdown = buildString {
                            append(_contentFormat.value)
                            if (_contentFormat.value.isNotEmpty() && !_contentFormat.value.endsWith(" ")) {
                                append(" ")
                            }
                            mentionTokens.forEach { token ->
                                append("$token ")
                            }
                            val result = toString().trimEnd()
                            clear()
                            append(result)
                        }
                        _contentFormat.value = migratedMarkdown
                        /** 重新 setMarkdown 以让 RichTextState 解析新追加的 token */
                        _richTextState?.let { state ->
                            state.setMarkdown(migratedMarkdown)
                            _content.value = state.annotatedString.text
                        }
                    }
                }

                /**
                 * v2026-08-01 Phase 4 回退：图片不再迁移为 Markdown 内联语法。
                 * 图片作为独立 ContentBlock.Image 块加载，由 UI 层 contentBlocks 渲染。
                 * _imagePaths 从数据库的 Image 块同步（用于文件清理追踪）。
                 */
                val dbBlocks = contentBlockDao.getBlocksByTodoId(inspirationId, ownerType = "inspiration")
                val imageBlocks = dbBlocks.filter { it.type == "image" }
                _imagePaths.value = imageBlocks.map { it.filePath }

                val subTasks = SubTaskManager.getSubTasks(context, inspirationId)
                _subTasks.value = subTasks
            }
        }

        /**
         * 加载完成 → 重置 _isDirty 为 false（v2026-07-22 新增）
         *
         * 必须在所有 _xxx.value 赋值结束后设置，确保 setXxx 函数体内的
         * _isDirty=true 不会污染基线状态。这里用 viewModelScope.launch 异步置 false
         * 即可，因为 _isDirty 本身就是个简单的 MutableStateFlow，无需精确同步。
         */
        _isDirty.value = false
    }

    // ==================== 保存方法 ====================

    /**
     * 保存灵感（带分类推荐检查 + 真正等待完成）
     *
     * **V2.8.4 关键改进**：将 saveInspiration() 改为 suspend 函数，
     * 调用方（UI 层）必须通过 coroutineScope.launch 启动并 await，
     * 确保 performSave() 中的数据库 IO 操作全部完成后再返回。
     *
     * 之前的 fire-and-forget 模式存在两个隐患：
     * 1. navigateBack() 立即触发，ViewModel.onCleared() 可能取消 viewModelScope，
     *    导致 performSave 协程被中途取消 → 数据丢失
     * 2. 用户快速点击"完成"时，防抖任务被取消但 _contentFormat 仍为旧值，
     *    导致 contentFormat 字段保存为旧值（卡片页不影响但重新进入编辑页会丢格式）
     *
     * 修复后，UI 层 onClick 会 await 整个保存过程（包括同步导出 contentFormat），
     * 杜绝以上两个问题。
     *
     * 保存流程：
     * 1. 校验标题非空
     * 2. 若未手动选择分类，触发智能关键词匹配
     * 3. 同步导出最新 contentFormat（不等防抖）
     * 4. 持久化到数据库
     * 5. 保存子任务
     * 6. 保存内容块
     * 7. 清理 Undo 栈
     *
     * @return 是否成功保存（false 表示需要用户选择分类）
     */
    suspend fun saveInspiration(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        performSave()
        return true
    }

    /**
     * 执行实际的保存操作（私有方法）
     *
     * **V2.8.4 改动**：
     * 1. 改为 suspend 函数，由 saveInspiration() 直接 await
     * 2. 不再 viewModelScope.launch，避免 fire-and-forget 导致 ViewModel.onCleared 取消协程
     * 3. 在持久化前**同步**从 RichTextState 导出最新 contentFormat 和 content，
     *    防止防抖任务被取消防控丢失
     *
     * 保存逻辑：
     * 1. 同步刷新 contentFormat / content（从 RichTextState 直接导出）
     * 2. 构建或更新 Inspiration 对象
     * 3. 处理标签编码（List<String> → JSON 字符串）
     * 4. 持久化到数据库
     * 5. 保存子任务
     * 6. 保存内容块（图片/语音等混合内容）
     * 7. 保存关联关系（新建时绑定临时关联到新 ID）
     * 8. 清理 Undo 栈持久化数据
     */
    private suspend fun performSave() {
        val currentTime = System.currentTimeMillis()
        val hasSubTasks = _subTasks.value.isNotEmpty()

        /** 取消未完成的防抖任务，确保不泄漏协程 */
        _debounceJob?.cancel()
        _debounceJob = null

        /**
         * V2.8.4 关键修复：同步导出最新 contentFormat
         *
         * 之前 _contentFormat.value 依赖 scheduleFormatExport 的 300ms 防抖更新，
         * 用户快速点"完成"时防抖任务被 cancel，导致保存的是旧值。
         *
         * 现在 performSave 开头直接调用 _richTextState.toMarkdown() 获取最新 Markdown，
         * 不依赖防抖，确保保存的内容永远是最新的。
         */
        val liveText: String
        val richText = _richTextState

        /**
         * V2.8.4 关键修复：使用 try as expression 同步导出最新 Markdown
         *
         * 之前用 val 声明 + 在 try-catch 两个分支赋值的写法会导致
         * "'val' cannot be reassigned" 编译错误——
         * Kotlin 编译器认为 try 和 catch 是两个互斥分支，
         * 每个分支都需要赋值，val 无法处理这种"分支赋值"语义。
         *
         * 现在改为 try as expression（Kotlin 1.5+ 支持）：
         * - try-catch 整体作为表达式
         * - 直接赋值给 val liveMarkdown，无需先声明再赋值
         * - 编译通过且语义清晰
         */
        val liveMarkdown: String = if (richText != null) {
            try {
                richText.toMarkdown()
            } catch (e: Exception) {
                android.util.Log.w("InspirationEditViewModel", "toMarkdown() 失败，回退旧值", e)
                _contentFormat.value
            }
        } else {
            _contentFormat.value
        }

        if (richText != null) {
            liveText = richText.annotatedString.text
            /** 同步更新 _contentFormat 和 _content（不等防抖） */
            _contentFormat.value = liveMarkdown
            _content.value = liveText
            /** v2026-08-01 Phase 4 回退：_imagePaths 不再从 Markdown 解析，
             *  由 addImagePath() 和 contentBlocks 中的 Image 块维护 */
        } else {
            liveText = _content.value
        }

        /** 保存前对 contentFormat 进行校验和修复（防止损坏数据） */
        val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(liveMarkdown)

        val inspirationId: Long = if (existingInspiration != null) {
            // 更新已有灵感
            val inspiration = existingInspiration!!.copy(
                title = _title.value,
                content = if (liveText.isBlank()) "" else liveText,
                categoryId = _categoryId.value,
                priority = _priority.value,
                startDate = _startDate.value,
                dueDate = _dueDate.value,
                estimatedDurationMinutes = _estimatedDurationMinutes.value,
                updatedAt = currentTime,
                geofenceLat = _geofenceLat.value,
                geofenceLng = _geofenceLng.value,
                geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                geofenceType = _geofenceType.value,
                geofenceEnabled = _geofenceEnabled.value,
                geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                hasSubTasks = hasSubTasks,
                /**
                 * v2026-07-25 三写存储重构：附件信息已迁移到 content_blocks 表（单一数据源）
                 * 字段保留但不再写入，避免破坏数据库 schema
                 * （与 TodoEditViewModel 保持一致）
                 */
                voiceNotePath = null,
                voiceDuration = null,
                imagePaths = "",
                /**
                 * v2026-07-31 Phase 2 重构：tags 从 markdown 实时派生
                 * - 旧：encodeTags(_tags.value)，_tags 是独立 MutableStateFlow
                 * - 新：encodeTags(extractTagsFromMarkdown(liveMarkdown))，直接从当前 markdown 提取
                 * - 同步派生流 tags.value 在 _contentFormat 更新后也会更新，但保存时
                 *   直接用 liveMarkdown 提取更精确（避免派流还没传播）
                 */
                tags = encodeTags(extractTagsFromMarkdown(liveMarkdown)),
                backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                contentFormat = safeContentFormat /** 持久化同步导出的最新富文本内容（Markdown）*/
            )
            inspirationRepository.update(inspiration)
            existingInspiration!!.id
        } else {
            // 创建新灵感
            val inspiration = Inspiration(
                title = _title.value,
                content = if (liveText.isBlank()) "" else liveText,
                tags = encodeTags(extractTagsFromMarkdown(liveMarkdown)), /** 同上：从 markdown 实时派生 */
                /**
                 * v2026-07-25 三写存储重构：附件信息已迁移到 content_blocks 表（单一数据源）
                 * 字段保留但不再写入，避免破坏数据库 schema
                 */
                imagePaths = "",
                /**
                 * v2026-07-31 同步：新建模式下 createdAt 使用 _createdAt.value
                 * （即进入编辑页时记录的时间戳），而非 currentTime（保存瞬间的时间）。
                 * 这样保证：
                 * 1. 编辑页"标题和正文之间"显示的时间戳 == 数据库 createdAt
                 * 2. 重新进入编辑页时，时间戳行不变（与首次保存值一致）
                 */
                createdAt = _createdAt.value,
                updatedAt = currentTime,
                categoryId = _categoryId.value,
                priority = _priority.value,
                status = 0,
                startDate = _startDate.value,
                dueDate = _dueDate.value,
                estimatedDurationMinutes = _estimatedDurationMinutes.value,
                geofenceLat = _geofenceLat.value,
                geofenceLng = _geofenceLng.value,
                geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                geofenceType = _geofenceType.value,
                geofenceEnabled = _geofenceEnabled.value,
                geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                hasSubTasks = hasSubTasks,
                voiceNotePath = null,
                voiceDuration = null,
                backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                contentFormat = safeContentFormat /** 持久化同步导出的最新富文本内容（Markdown）*/
            )
            inspirationRepository.insert(inspiration)
        }

        saveSubTasks(inspirationId)

        /**
         * 保存成功 → 重置 _isDirty 为 false（v2026-07-22 新增）
         *
         * 必须放在 saveSubTasks 之后，因为子任务的 saveSubTasks 内部可能调用
         * toggleSubTaskCompletion → _subTasks.value = updatedList → 内部 _isDirty=true
         * （实际上 saveSubTasks 不会走 UI 的 toggleSubTaskCompletion，但这里保险起见放在最后）
         */
        _isDirty.value = false

        /**
         * v2026-07-25 三写存储重构：保存内容块到 content_blocks 表（单一数据源）
         *
         * 无论 _currentContentBlocks 是否为空都调用：
         * - 非空时写入新数据
         * - 空时清空旧的 content_blocks 记录（replaceBlocksForTodo 是先删后写）
         *
         * 灵感模块的子任务目前不支持附件，所有附件均属于父灵感本身
         * （subTaskId=null, lineIndex=0）
         */
        /**
         * v2026-08-30 内联媒体：以编辑器原始 Markdown（liveMarkdown）解析内联 token，
         * 而非 sanitize 后的版本——避免校验/清洗逻辑万一剥离 token 链接时，
         * 误判为"媒体已删除"而清理掉用户的物理文件。
         */
        saveInlineMediaBlocks(inspirationId, liveMarkdown)

        // 保存关联关系（新建时将临时关联绑定到新ID）
        if (existingInspiration == null) {
            _relations.value.forEach { relation ->
                cardRelationRepository.addRelation(relation.copy(sourceId = inspirationId))
            }
            // v2026-07-22 新增：修复"新建模式提前 addRelation"导致的 sourceId=0 脏数据。
            // 同步迁移 card_relations 表中所有 sourceType=inspiration AND sourceId=0 的占位关联，
            // 以及对应的反向记录 (targetType=inspiration AND targetId=0)。
            cardRelationRepository.fixupZeroSourceRelations("inspiration", inspirationId)
        }

        /** 保存成功后清除当前灵感的持久化 Undo 栈（按 inspirationId 隔离清除） */
        corgiPreferences.clearUndoRedoStacks(existingInspiration?.id ?: -1L)
    }

    /**
     * 保存子任务（编辑模式下先删除旧子任务再添加新的）
     * 并同步更新灵感的 hasSubTasks 字段
     *
     * @param inspirationId 灵感 ID
     */
    private suspend fun saveSubTasks(inspirationId: Long) {
        val currentSubTasks = _subTasks.value

        if (existingInspiration != null) {
            SubTaskManager.deleteAllSubTasks(context, inspirationId)
        }

        if (currentSubTasks.isNotEmpty()) {
            SubTaskManager.addSubTasks(context, inspirationId, currentSubTasks)
        }
    }

    /**
     * 删除子任务并同步到数据库（编辑已有灵感时）
     *
     * @param subTask 要删除的子任务
     */
    fun deleteSubTask(subTask: SubTask) {
        removeSubTask(subTask)

        if (existingInspiration != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.deleteSubTask(context, subTask.id)
            }
        }
    }

    /**
     * 加载分类列表并设置默认分类
     */
    fun loadCategories() {
        viewModelScope.launch {
            try {
                android.util.Log.d("InspirationEditVM", "开始加载分类...")
                categoryRepository.initDefaultCategories()

                /** 记录最后编辑的灵感 ID（用于设置页入口传递） */
                existingInspiration?.id?.let { inspirationId ->
                    corgiPreferences.saveLastEditedTodoId(inspirationId)
                }

                val allCategories = categoryRepository.getAllCategoriesList()
                android.util.Log.d("InspirationEditVM", "加载到 ${allCategories.size} 个分类: $allCategories")
                _categories.value = allCategories

                if (existingInspiration == null && _categoryId.value == 0L) {
                    val userTypeValue = corgiPreferences.userType.first()
                    val userType = UserType.fromValue(userTypeValue)
                    val defaultCategory = when (userType) {
                        UserType.WORKER -> allCategories.find { it.type == CategoryType.WORK }
                        UserType.STUDENT -> allCategories.find { it.type == CategoryType.STUDY }
                    } ?: allCategories.firstOrNull()
                    defaultCategory?.let {
                        _categoryId.value = it.id
                        android.util.Log.d("InspirationEditVM", "设置默认分类: ${it.name} (ID=${it.id})")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("InspirationEditVM", "加载分类失败", e)
                e.printStackTrace()
            } finally {
                _isCategoriesLoaded.value = true
                android.util.Log.d("InspirationEditVM", "分类加载完成, isCategoriesLoaded=true, categories数量=${_categories.value.size}")
            }
        }
    }

    // ==================== 语音备注相关方法 ====================

    /**
     * 设置语音备注
     *
     * @param path 语音文件路径
     * @param duration 语音时长（秒）
     */
    fun setVoiceNote(path: String?, duration: Int?) {
        _voiceNotePath.value = path
        _voiceDuration.value = duration
        _isDirty.value = true
    }

    /**
     * 清除语音备注
     */
    fun clearVoiceNote() {
        _voiceNotePath.value = null
        _voiceDuration.value = null
        _isDirty.value = true
    }

    /**
     * v2026-08-30 内联媒体：插入图片/语音内联 token 后标记内容已变更
     *
     * 图片/语音不再走 ContentBlock 列表，而是作为正文内联 atomic token 存在，
     * 插入动作不会触发 BasicTextField 的 onValueChange，因此需手动置脏。
     */
    fun notifyInlineMediaChanged() {
        _isDirty.value = true
    }

    // ==================== 图片管理相关方法 ====================

    /**
     * 添加单张图片路径到列表
     *
     * @param path 图片在内部存储中的绝对路径
     */
    fun addImagePath(path: String) {
        val currentList = _imagePaths.value.toMutableList()
        if (path !in currentList) {
            currentList.add(path)
            _imagePaths.value = currentList
            _isDirty.value = true
        }
    }

    /**
     * 移除指定路径的图片
     * 同时从内部存储删除对应的文件
     *
     * @param path 要移除的图片路径
     */
    fun removeImagePath(path: String) {
        val currentList = _imagePaths.value.toMutableList()
        currentList.remove(path)
        _imagePaths.value = currentList
        _isDirty.value = true

        /** 异步删除物理文件 */
        viewModelScope.launch {
            com.corgimemo.app.util.ImageUtils.deleteImageFromInternalStorage(context, path)
        }
    }

    /**
     * 重新排序图片列表（拖拽排序后调用）
     *
     * @param newPaths 排序后的新路径列表
     */
    fun reorderImagePaths(newPaths: List<String>) {
        _imagePaths.value = newPaths
        _isDirty.value = true
    }

    /**
     * 清空所有图片路径
     * 同时清理内部存储中的所有对应文件
     */
    fun clearImagePaths() {
        val pathsToRemove = _imagePaths.value.toList()
        _imagePaths.value = emptyList()
        _isDirty.value = true

        /** 批量删除物理文件 */
        viewModelScope.launch {
            com.corgimemo.app.util.ImageUtils.batchDeleteImages(context, pathsToRemove)
        }
    }

    // ==================== 内容块 CRUD 方法（ContentBlock 系统） ====================

    /**
     * 从数据库加载某灵感的所有内容块
     *
     * 在编辑页初始化时调用，将持久化的内容块恢复到内存列表。
     * 注意：使用 todoId 字段存储 inspirationId（复用 ContentBlockEntity 结构）
     *
     * @param inspirationId 灵感事项 ID
     * @return ContentBlock 列表（按 orderIndex 排序）
     */
    suspend fun loadContentBlocks(inspirationId: Long): List<ContentBlock> {
        // v2026-07-25 ownerType 过滤：灵感查询传 "inspiration"，避免与待办 ID 冲突
        val entities = contentBlockDao.getBlocksByTodoId(inspirationId, ownerType = "inspiration")
        return entities.map { entity ->
            when (entity.type) {
                /**
                 * v2026-08-01 Phase 4 回退：Image 块作为独立 ContentBlock 返回。
                 * 原因：编辑模式下 BasicTextField 不支持 inlineContent，无法渲染内联图片，
                 * 改回非内联方案，图片作为独立块在编辑器下方/之间显示。
                 */
                "image" -> ContentBlock.Image(entity.filePath)
                "voice" -> ContentBlock.Voice(entity.filePath, entity.duration)
                else -> ContentBlock.Text("") // 兜底
            }
        }
    }

    /**
     * 保存内容块列表到数据库（原子操作：先删后写）
     *
     * 在 performSave() 时调用，确保数据一致性。
     * 注意：使用 todoId 字段存储 inspirationId（复用 ContentBlockEntity 结构）
     *
     * v2026-07-25 三写存储重构：新增 subTaskId 和 lineIndex 参数
     * 与 [com.corgimemo.app.viewmodel.TodoEditViewModel.saveContentBlocks] 保持一致。
     * 灵感模块的子任务目前不支持附件，调用方使用默认值（subTaskId=null, lineIndex=0）即可。
     *
     * @param inspirationId 灵感事项 ID
     * @param blocks 当前内存中的 ContentBlock 列表
     * @param subTaskId 子任务 ID（null 表示属于父灵感本身）
     * @param lineIndex 行号索引（0 表示父灵感标题行）
     */
    suspend fun saveContentBlocks(
        inspirationId: Long,
        blocks: List<ContentBlock>,
        subTaskId: Long? = null,
        lineIndex: Int = 0
    ) {
        val entities = blocks.mapIndexed { index, block ->
            when (block) {
                is ContentBlock.Image -> ContentBlockEntity(
                    todoId = inspirationId, ownerType = "inspiration",
                    type = "image", filePath = block.path,
                    orderIndex = index, subTaskId = subTaskId, lineIndex = lineIndex
                )
                is ContentBlock.Voice -> ContentBlockEntity(
                    todoId = inspirationId, ownerType = "inspiration",
                    type = "voice", filePath = block.path,
                    duration = block.duration, orderIndex = index,
                    subTaskId = subTaskId, lineIndex = lineIndex
                )
                is ContentBlock.Text -> null // 文本块不持久化到独立表
            }
        }.filterNotNull()

        contentBlockDao.replaceBlocksForTodo(inspirationId, entities, ownerType = "inspiration")
    }

    /**
     * v2026-08-30 内联媒体：从正文 Markdown 解析内联图片/语音 token，
     * 重建 content_blocks 表（单一数据源），并清理已被删除的孤立物理文件。
     *
     * 替代原 saveContentBlocks(inspirationId, _currentContentBlocks.value)：
     * 现在图片/语音以内联 atomic token 形式存在于正文 Markdown，
     * 不再依赖独立的内容块列表。
     *
     * 正文 Markdown 中两种内联媒体的序列化格式：
     * - 图片（现方案，块级）：`![alt](<绝对文件路径>)` —— RichSpanStyle.Image 的标准 Markdown 形式
     * - 图片（旧数据兼容）：`[🖼️](trigger:image:<路径>)` —— 曾用 atomic token 承载
     * - 语音：`[🎤00:12](trigger:voice:<路径>|<时长秒>)` —— 与 # 标签 / @ 关联同源的 token
     */
    private suspend fun saveInlineMediaBlocks(inspirationId: Long, markdown: String) {
        val blocks = mutableListOf<ContentBlock>()
        /** 按"类型:路径"去重，避免新旧两种格式同时命中同一文件 */
        val seen = mutableSetOf<String>()

        /** 1) 块级图片：标准 Markdown 图片语法 */
        Regex("""!\[[^\]]*\]\(([^)]+)\)""")
            .findAll(markdown)
            .forEach { m ->
                val path = m.groupValues[1].trim()
                if (path.isNotBlank() && seen.add("image:$path")) {
                    blocks.add(ContentBlock.Image(path))
                }
            }

        /** 2) 兼容旧数据：以 trigger:image token 内联的图片 */
        Regex("""\]\(trigger:image:([^)]+)\)""")
            .findAll(markdown)
            .forEach { m ->
                val path = m.groupValues[1].trim()
                if (path.isNotBlank() && seen.add("image:$path")) {
                    blocks.add(ContentBlock.Image(path))
                }
            }

        /**
         * 3) 语音：trigger:voice token，id 为 <路径>|<时长秒>|<时间戳>
         *
         * v2026-08-31 扩展：id 追加了毫秒时间戳（保证多次录音 id 唯一，避免
         * markdown 解析时相邻同 style token 被合并）。这里用 split("|") 全拆分，
         * 只取前两段：parts[0]=路径、parts[1]=时长，末尾时间戳忽略。
         * 兼容旧数据（无时间戳，仅 <路径>|<时长> 两段）。
         */
        Regex("""\]\(trigger:voice:([^)]+)\)""")
            .findAll(markdown)
            .forEach { m ->
                val parts = m.groupValues[1].split("|")
                val filePath = parts[0].trim()
                val duration = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (filePath.isNotBlank() && seen.add("voice:$filePath")) {
                    blocks.add(ContentBlock.Voice(filePath, duration))
                }
            }

        /**
         * v2026-08-30：暂不清理物理文件。
         *
         * 原因（真实风险）：旧灵感的图片原本只存在于 content_blocks 表，
         * 需要打开编辑页后由迁移逻辑插入正文、才会出现在 Markdown 中。
         * 若保存发生在迁移完成之前，Markdown 里没有图片，
         * 此时按"孤儿文件"清理会**误删用户图片**。
         * 待迁移与渲染在真机验证稳定后，再重新启用清理。
         */
        val old = contentBlockDao.getBlocksByTodoId(inspirationId, ownerType = "inspiration")
        val keptPaths = blocks.map { block ->
            when (block) {
                is ContentBlock.Image -> block.path
                is ContentBlock.Voice -> block.path
                is ContentBlock.Text -> ""
            }
        }

        saveContentBlocks(inspirationId, blocks)
    }

    /**
     * 删除灵感的所有内容块（从数据库和物理存储）
     *
     * 在删除灵感时调用，清理关联的文件资源。
     * 注意：使用 todoId 字段存储 inspirationId（复用 ContentBlockEntity 结构）
     *
     * @param inspirationId 灵感事项 ID
     */
    suspend fun deleteAllContentBlocks(inspirationId: Long) {
        // v2026-07-25 ownerType 过滤：灵感查询/删除传 "inspiration"
        val entities = contentBlockDao.getBlocksByTodoId(inspirationId, ownerType = "inspiration")
        contentBlockDao.deleteByTodoId(inspirationId, ownerType = "inspiration")

        /** 异步删除物理文件 */
        entities.forEach { entity ->
            val file = java.io.File(entity.filePath)
            if (file.exists()) file.delete()
        }
    }

    /**
     * 设置背景颜色
     *
     * 更新灵感项的背景颜色状态，
     * 在保存时将此值持久化到数据库的 backgroundColor 字段。
     *
     * @param colorInt ARGB 整数值（通过 Compose Color.toArgb() 获取）
     */
    fun setBackgroundColor(colorInt: Int) {
        _backgroundColor.value = colorInt
        _isDirty.value = true
    }

    /**
     * 设置富文本格式化内容（Markdown 字符串）
     *
     * 更新灵感项的 contentFormat 状态，
     * 在保存时将此 Markdown 文本持久化到数据库的 contentFormat 字段。
     *
     * @param markdown Markdown 格式的字符串（由 MarkdownParser.export() 生成）
     */
    fun setContentFormat(markdown: String) {
        _contentFormat.value = markdown
        /** v2026-08-01 Phase 4 回退：_imagePaths 不再从 Markdown 解析，
         *  由 addImagePath() 和 contentBlocks 中的 Image 块维护 */
        _isDirty.value = true
    }

    /**
     * 设置 RichTextState 实例（由 UI 层在 rememberRichTextState() 后调用）
     *
     * UI 层创建 RichTextState 后通过此方法注入到 ViewModel，
     * 以便 ViewModel 调用 setMarkdown()/toMarkdown() 等方法。
     *
     * @param state 由 rememberRichTextState() 创建的 RichTextState 实例
     */
    fun setRichTextState(state: RichTextState) {
        // 注意：setRichTextState 只在 UI 初始化时调用一次（rememberRichTextState 注入），
        // 不视为用户编辑，**不**标记 _isDirty=true，避免编辑页进入时误判脏
        _richTextState = state
    }

    /**
     * 防抖调度：延迟 300ms 后将 RichTextState 导出为 Markdown 格式
     *
     * 每次调用会取消上一次未完成的防抖任务（cancel-and-restart 模式），
     * 确保只有用户停止输入后的最终状态会被导出。
     *
     * **优先使用库的 toMarkdown()**：
     * - 若 _richTextState 非空 → 使用库原生导出（支持列表/代码块/链接等完整格式）
     * - 若 _richTextState 为空（降级）→ 回退到 MarkdownParser.export()
     *
     * @param annotatedString 当前的富文本内容（AnnotatedString，兼容旧调用）
     */
    fun scheduleFormatExport(annotatedString: androidx.compose.ui.text.AnnotatedString) {
        _debounceJob?.cancel()
        _debounceJob = viewModelScope.launch {
            delay(300L)
            val markdown = _richTextState?.toMarkdown()
                ?: com.corgimemo.app.util.MarkdownParser.export(annotatedString)
            _contentFormat.value = markdown
            /** v2026-08-01 Phase 4 回退：_imagePaths 不再从 Markdown 解析，
             *  由 addImagePath() 和 contentBlocks 中的 Image 块维护 */
            /**
             * v2026-07-31 Phase 2 重构：tags 派生流自动同步
             *
             * 此处更新 _contentFormat.value 后，tags StateFlow（派生自 _contentFormat）
             * 会自动通过 map { extractTagsFromMarkdown(it) } 重新计算标签列表。
             * 无需显式调用 extractTagsFromMarkdown，避免双重维护。
             */
            /** 同步纯文本内容（用于搜索/字数统计） */
            _content.value = annotatedString.text
        }
    }

    /**
     * 删除当前灵感（含内容块和物理文件清理）
     *
     * 从数据库删除灵感记录、关联的内容块文件，
     * 确保无残留数据。
     *
     * @param inspirationId 要删除的灵感 ID
     */
    fun deleteInspiration(inspirationId: Long) {
        viewModelScope.launch {
            /** 删除关联的内容块（含物理文件） */
            deleteAllContentBlocks(inspirationId)
            /** 从数据库删除灵感记录 */
            inspirationRepository.deleteById(inspirationId)
        }
    }

    // ==================== 编解码辅助方法 ====================

    /**
     * 将标签列表编码为JSON字符串
     * 用于持久化存储到 Inspiration.tags 字段
     *
     * @param tags 标签列表
     * @return JSON格式的字符串（如 ["产品","设计"]）
     */
    fun encodeTags(tags: List<String>): String = TagUtils.encodeTags(tags)

    /**
     * 解码标签JSON字符串为列表
     * 用于从 Inspiration.tags 字段读取时反序列化
     *
     * @param tagsJson JSON字符串
     * @return 标签列表，解析失败返回空列表
     */
    fun decodeTags(tagsJson: String): List<String> = TagUtils.decodeTags(tagsJson)

    /**
     * 将图片路径列表编码为JSON字符串
     * 用于持久化存储到数据库
     *
     * @param paths 图片路径列表
     * @return JSON格式的字符串（如 ["path1","path2"]）
     */
    private fun encodePaths(paths: List<String>): String = TagUtils.encodePaths(paths)

    /**
     * 从JSON字符串解码图片路径列表
     * 用于从数据库读取时反序列化
     *
     * @param json JSON格式字符串
     * @return 解析后的路径列表，解析失败返回空列表
     */
    private fun decodePaths(json: String): List<String> = TagUtils.decodePaths(json)

    // ========== 关联管理方法 ==========

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
     * v2026-07-22 增强：添加成功后立即加载新关联的标题并写入 [_relationTitles] 缓存。
     *
     * @param targetType 目标类型 ("todo" | "inspiration" | "date")
     * @param targetId 目标ID
     */
    fun addRelation(targetType: String, targetId: Long) {
        viewModelScope.launch {
            val inspirationId = existingInspiration?.id ?: 0L
            val relation = CardRelation(
                sourceType = "inspiration", /** 灵感作为关联发起方 */
                sourceId = inspirationId,
                targetType = targetType,
                targetId = targetId
            )
            val result = cardRelationRepository.addRelation(relation)
            if (result > 0) {
                _relations.value = (_relations.value + relation.copy(id = result)).distinctBy { "${it.targetType}_${it.targetId}" }
                // v2026-07-22 新增：立即加载新关联的标题，避免 Chip 显示"加载中…"
                val title = cardRelationRepository.getCardTitle(targetType, targetId)
                _relationTitles.value = _relationTitles.value + (result to (title ?: "已删除"))
            }
        }
    }

    /**
     * 批量添加关联关系（v2026-07-22 新增，供编辑页 RelationPickerBottomSheet 使用）
     *
     * 串行处理所有卡片，累积结果后一次性更新 [_relations] 和 [_relationTitles]，
     * 避免多次 emit 导致 UI 抖动。
     *
     * @param cards 待添加的卡片列表（Pair<targetType, targetId>）
     */
    fun addRelations(cards: List<Pair<String, Long>>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val inspirationId = existingInspiration?.id ?: 0L
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
     * 搜索卡片（用于关联选择器）
     * @param query 搜索关键词
     * @param callback 结果回调
     */
    fun searchCards(query: String, callback: (List<CardSearchResult>) -> Unit) {
        viewModelScope.launch {
            val results = cardRelationRepository.searchCards(query)
            callback(results)
        }
    }
}
