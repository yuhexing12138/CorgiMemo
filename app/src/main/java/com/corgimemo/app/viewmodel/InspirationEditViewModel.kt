package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.CategoryKeywordRepository
import com.corgimemo.app.data.repository.CategoryMatcher
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.domain.ReminderRecommender
import com.corgimemo.app.model.UserType
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val categoryKeywordRepository: CategoryKeywordRepository,
    private val categoryMatcher: CategoryMatcher,
    private val corgiPreferences: CorgiPreferences,
    private val cardRelationRepository: CardRelationRepository,
    private val contentBlockDao: ContentBlockDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var recommendationJob: Job? = null
    /** 防抖导出任务引用：用于延迟执行 MarkdownParser.export() */
    private var _debounceJob: Job? = null
    private val reminderRecommender = ReminderRecommender()

    // ========== 基础字段状态 ==========

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

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

    private val _repeatType = MutableStateFlow(0)
    val repeatType: StateFlow<Int> = _repeatType.asStateFlow()

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

    private val _recommendedCategory = MutableStateFlow<Category?>(null)
    val recommendedCategory: StateFlow<Category?> = _recommendedCategory.asStateFlow()

    private val _hasManuallySelectedCategory = MutableStateFlow(false)
    val hasManuallySelectedCategory: StateFlow<Boolean> = _hasManuallySelectedCategory.asStateFlow()

    private val _showKeywordSelection = MutableStateFlow(false)
    val showKeywordSelection: StateFlow<Boolean> = _showKeywordSelection.asStateFlow()

    private val _extractedKeywords = MutableStateFlow<List<String>>(emptyList())
    val extractedKeywords: StateFlow<List<String>> = _extractedKeywords.asStateFlow()

    private val _isCategoriesLoaded = MutableStateFlow(false)
    val isCategoriesLoaded: StateFlow<Boolean> = _isCategoriesLoaded.asStateFlow()

    // 提醒时间相关状态
    private val _reminderTime = MutableStateFlow<Long?>(null)
    val reminderTime: StateFlow<Long?> = _reminderTime.asStateFlow()

    private val _recommendedReminderTime = MutableStateFlow<Long?>(null)
    val recommendedReminderTime: StateFlow<Long?> = _recommendedReminderTime.asStateFlow()

    private val _showReminderRecommendation = MutableStateFlow(false)
    val showReminderRecommendation: StateFlow<Boolean> = _showReminderRecommendation.asStateFlow()

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
     * 标签列表状态
     * 用于 UI 层展示和管理灵感标签，支持增删改查
     * 保存时自动编码为 JSON 字符串存入 Inspiration.tags 字段
     */
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    // ==================== 内容块统一管理（ContentBlock 系统） ====================

    /**
     * 统一编辑操作类型
     *
     * 封装所有可撤销的编辑操作，扩展原有的纯文本 Undo/Redo 栈，
     * 支持内容块级别的撤销/重做。
     */
    sealed class EditOperation {
        /** 文本变更（格式化、输入等）- 保留原有 AnnotatedString 快照 */
        data class TextChange(val before: androidx.compose.ui.text.AnnotatedString) : EditOperation()
        /** 内容块被删除 - 记录被删块列表和原始位置，撤销时可恢复 */
        data class BlockDeleted(val blocks: List<ContentBlock>, val index: Int) : EditOperation()
        /** 内容块被插入 - 记录插入位置，撤销时移除 */
        data class BlockInserted(val index: Int) : EditOperation()
        /** 内容块排序变更 - 记录旧顺序，撤销时恢复 */
        data class BlocksReordered(val oldOrder: List<ContentBlock>) : EditOperation()
    }

    /** 扩展后的 Undo 栈：支持文本和内容块操作 */
    private val _undoStackExtended = ArrayDeque<EditOperation>()

    /** 扩展后的 Redo 栈 */
    private val _redoStackExtended = ArrayDeque<EditOperation>()

    // ==================== 批量操作合并机制 ====================

    /**
     * 合并窗口期（毫秒）：在此时间窗口内的连续同类型操作自动合并为一条
     *
     * 例如：用户从相册选择3张图片，每张间隔 < 500ms，
     * 则只产生1条 BlockInserted 记录（记录最后插入位置），而非3条。
     */
    private val MERGE_WINDOW_MS = 500L

    /** 上一次操作的时间戳（用于判断是否在合并窗口内） */
    private var lastOperationTime = 0L

    /** 待合并的暂存操作（窗口期内缓存，超时或类型变化时提交） */
    private var pendingMergeOp: EditOperation? = null

    // ==================== 扩展撤销栈大小限制 ====================

    /**
     * 扩展撤销栈最大序列化大小（字节）
     *
     * EditOperation（含完整 ContentBlock 列表）比纯 AnnotatedString 大很多，
     * 单条 BlockDeleted 操作可能包含多张图片路径（每条约 200-500 字节）。
     * 设置 5MB 上限防止内存膨胀。
     */
    private val EXTENDED_STACK_MAX_SIZE_BYTES = 5L * 1024L * 1024L // 5MB

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

    // ==================== Undo/Redo 双栈（编辑器内撤销/重做） ====================

    /** Undo 栈：存储可撤销的历史快照（AnnotatedString） */
    private val _undoStack = ArrayDeque<androidx.compose.ui.text.AnnotatedString>()

    /** Redo 栈：存储可重做的被撤销快照（AnnotatedString） */
    private val _redoStack = ArrayDeque<androidx.compose.ui.text.AnnotatedString>()

    /** 最大历史深度（防止内存溢出） */
    private val MAX_UNDO_DEPTH = 50

    /** 是否可以撤销（Undo 栈非空时为 true） */
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** 是否可以重做（Redo 栈非空时为 true） */
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /** 关联列表 */
    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations.asStateFlow()

    /** 当前编辑中的灵感实体（null 表示新建模式） */
    private var existingInspiration: Inspiration? = null

    // ==================== 基础 Setter 方法 ====================

    /**
     * 设置标题
     * @param title 灵感标题
     */
    fun setTitle(title: String) {
        _title.value = title
    }

    /**
     * 设置标题并触发智能分类推荐（带防抖）
     * @param title 灵感标题
     */
    fun setTitleWithRecommendation(title: String) {
        _title.value = title
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch {
            delay(300)
            triggerRecommendation()
        }
    }

    /**
     * 设置纯文本内容
     * @param content 纯文本内容
     */
    fun setContent(content: String) {
        _content.value = content
    }

    /**
     * 设置分类 ID
     * @param categoryId 分类 ID
     */
    fun setCategoryId(categoryId: Long) {
        _categoryId.value = categoryId
        _hasManuallySelectedCategory.value = true
        updateReminderRecommendation()
    }

    /**
     * 设置优先级
     * @param priority 优先级值（0=低, 1=中, 2=高）
     */
    fun setPriority(priority: Int) {
        _priority.value = priority
    }

    /**
     * 设置开始时间
     * @param startDate 开始时间戳（毫秒）
     */
    fun setStartDate(startDate: Long?) {
        _startDate.value = startDate
        updateReminderRecommendation()
    }

    /**
     * 设置截止时间
     * 用户在时间选择器中确认后调用
     * 同时自动将提醒时间关联为截止时间（若用户未手动设置过提醒时间）
     *
     * @param dueDate 截止时间（毫秒时间戳）
     */
    fun setDueDate(dueDate: Long?) {
        _dueDate.value = dueDate
        /** 联动逻辑：若提醒时间为空，自动将提醒时间设为截止时间 */
        if (dueDate != null && _reminderTime.value == null) {
            _reminderTime.value = dueDate
        }
    }

    /**
     * 设置预估时长（分钟）
     * @param minutes 预估时长
     */
    fun setEstimatedDurationMinutes(minutes: Int?) {
        _estimatedDurationMinutes.value = minutes
        updateReminderRecommendation()
    }

    /**
     * 设置重复类型
     * @param repeatType 重复类型（0=不重复, 1=每天, 2=每周, 3=每月, 4=每年）
     */
    fun setRepeatType(repeatType: Int) {
        _repeatType.value = repeatType
    }

    // 地理围栏相关方法

    /**
     * 设置地理围栏纬度
     * @param lat 纬度值
     */
    fun setGeofenceLat(lat: Double?) {
        _geofenceLat.value = lat
    }

    /**
     * 设置地理围栏经度
     * @param lng 经度值
     */
    fun setGeofenceLng(lng: Double?) {
        _geofenceLng.value = lng
    }

    /**
     * 设置地理围栏半径
     * @param radius 半径（米）
     */
    fun setGeofenceRadius(radius: Float) {
        _geofenceRadius.value = radius
    }

    /**
     * 设置地理围栏类型
     * @param type 类型（0=到达提醒, 1=离开提醒）
     */
    fun setGeofenceType(type: Int) {
        _geofenceType.value = type
    }

    /**
     * 设置地理围栏是否启用
     * @param enabled 是否启用
     */
    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
    }

    /**
     * 设置地理围栏地址描述
     * @param address 地址字符串
     */
    fun setGeofenceAddress(address: String?) {
        _geofenceAddress.value = address
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
    }

    /**
     * 删除子任务
     *
     * @param subTask 要删除的子任务
     */
    fun removeSubTask(subTask: SubTask) {
        val currentList = _subTasks.value
        _subTasks.value = currentList.filter { it.id != subTask.id || it.order != subTask.order }
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

        if (existingInspiration != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.toggleSubTaskCompletion(context, subTask.id)
            }
        }
    }

    // ==================== 标签管理方法 ====================

    /**
     * 更新标签列表
     * 由 UI 层调用，更新当前灵感的标签集合
     *
     * @param newTags 新的标签列表
     */
    fun updateTags(newTags: List<String>) {
        _tags.value = newTags
    }

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
                _content.value = inspiration.content ?: ""
                _categoryId.value = inspiration.categoryId
                _hasManuallySelectedCategory.value = inspiration.categoryId > 0
                _priority.value = inspiration.priority
                _startDate.value = inspiration.startDate
                _dueDate.value = inspiration.dueDate
                _estimatedDurationMinutes.value = inspiration.estimatedDurationMinutes
                _repeatType.value = inspiration.repeatType
                _geofenceLat.value = inspiration.geofenceLat
                _geofenceLng.value = inspiration.geofenceLng
                _geofenceRadius.value = inspiration.geofenceRadius
                _geofenceType.value = inspiration.geofenceType
                _geofenceEnabled.value = inspiration.geofenceEnabled
                _geofenceAddress.value = inspiration.geofenceAddress

                _reminderTime.value = inspiration.reminderTime

                // 加载语音备注
                _voiceNotePath.value = inspiration.voiceNotePath
                _voiceDuration.value = inspiration.voiceDuration

                // 加载图片路径列表（从JSON字符串反序列化）
                if (inspiration.imagePaths.isNotBlank()) {
                    _imagePaths.value = decodePaths(inspiration.imagePaths)
                }

                /** 加载标签列表（从 JSON 字符串解码为 List<String>） */
                if (inspiration.tags.isNotBlank()) {
                    _tags.value = decodeTags(inspiration.tags)
                }

                /** 加载背景颜色（从 ARGB 整数值恢复为 Compose Color） */
                _backgroundColor.value = inspiration.backgroundColor

                /** 加载富文本格式化内容（Markdown 字符串） */
                _contentFormat.value = inspiration.contentFormat ?: ""

                /** 从 DataStore 恢复跨会话的 Undo/Redo 栈（如有） */
                restoreUndoStacks(inspirationId)

                // 加载关联关系（sourceType 为 "inspiration"）
                _relations.value = cardRelationRepository.getRelationsBlocking("inspiration", inspirationId)

                val subTasks = SubTaskManager.getSubTasks(context, inspirationId)
                _subTasks.value = subTasks
            }
        }
    }

    // ==================== 保存方法 ====================

    /**
     * 保存灵感（带分类推荐检查）
     *
     * 保存流程：
     * 1. 校验标题非空
     * 2. 若未手动选择分类，触发智能关键词匹配
     * 3. 通过校验后执行实际保存
     *
     * @return 是否成功保存（false 表示需要用户选择分类）
     */
    fun saveInspiration(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        if (!_hasManuallySelectedCategory.value) {
            val categoriesList = _categories.value
            
            if (categoriesList.isEmpty()) {
                performSave()
                return true
            }

            val keywords = com.corgimemo.app.data.util.KeywordExtractor.extractKeywords(_title.value)
            if (keywords.isNotEmpty()) {
                _extractedKeywords.value = keywords
                _showKeywordSelection.value = true
                return false
            }
        }

        performSave()
        return true
    }

    /**
     * 执行实际的保存操作（私有方法）
     *
     * 保存逻辑：
     * 1. 构建或更新 Inspiration 对象
     * 2. 处理标签编码（List<String> → JSON 字符串）
     * 3. 持久化到数据库
     * 4. 保存子任务
     * 5. 保存内容块（图片/语音等混合内容）
     * 6. 保存关联关系（新建时绑定临时关联到新 ID）
     * 7. 清理 Undo 栈持久化数据
     */
    private fun performSave() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val hasSubTasks = _subTasks.value.isNotEmpty()

            /** 取消未完成的防抖任务，确保不泄漏协程 */
            _debounceJob?.cancel()

            /** 保存前对 contentFormat 进行校验和修复（防止损坏数据） */
            val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(_contentFormat.value)

            val inspirationId: Long = if (existingInspiration != null) {
                // 更新已有灵感
                val inspiration = existingInspiration!!.copy(
                    title = _title.value,
                    content = if (_content.value.isBlank()) "" else _content.value,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    startDate = _startDate.value,
                    dueDate = _dueDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _reminderTime.value,
                    repeatType = _repeatType.value,
                    updatedAt = currentTime,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks,
                    voiceNotePath = _voiceNotePath.value,
                    voiceDuration = _voiceDuration.value,
                    imagePaths = encodePaths(_imagePaths.value),
                    tags = encodeTags(_tags.value), /** 编码标签列表为 JSON 字符串 */
                    backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                    contentFormat = safeContentFormat /** 持久化校验后的富文本格式内容（Markdown）*/
                )
                inspirationRepository.update(inspiration)
                existingInspiration!!.id
            } else {
                // 创建新灵感
                val inspiration = Inspiration(
                    title = _title.value,
                    content = if (_content.value.isBlank()) "" else _content.value,
                    tags = encodeTags(_tags.value), /** 编码标签列表为 JSON 字符串 */
                    imagePaths = encodePaths(_imagePaths.value),
                    createdAt = currentTime,
                    updatedAt = currentTime,
                    categoryId = _categoryId.value,
                    priority = _priority.value,
                    status = 0,
                    startDate = _startDate.value,
                    dueDate = _dueDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _reminderTime.value,
                    repeatType = _repeatType.value,
                    geofenceLat = _geofenceLat.value,
                    geofenceLng = _geofenceLng.value,
                    geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
                    geofenceType = _geofenceType.value,
                    geofenceEnabled = _geofenceEnabled.value,
                    geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
                    hasSubTasks = hasSubTasks,
                    voiceNotePath = _voiceNotePath.value,
                    voiceDuration = _voiceDuration.value,
                    backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                    contentFormat = safeContentFormat /** 持久化校验后的富文本格式内容（Markdown）*/
                )
                inspirationRepository.insert(inspiration)
            }

            saveSubTasks(inspirationId)

            /** 保存内容块到独立表（图片/语音等混合内容） */
            if (_currentContentBlocks.value.isNotEmpty()) {
                saveContentBlocks(inspirationId, _currentContentBlocks.value)
            }

            // 保存关联关系（新建时将临时关联绑定到新ID）
            if (existingInspiration == null) {
                _relations.value.forEach { relation ->
                    cardRelationRepository.addRelation(relation.copy(sourceId = inspirationId))
                }
            }

            /** 保存成功后清除当前灵感的持久化 Undo 栈（按 inspirationId 隔离清除） */
            corgiPreferences.clearUndoRedoStacks(existingInspiration?.id ?: -1L)
        }
    }

    /**
     * 确认关键词选择并保存
     *
     * @param selectedKeyword 用户选择的关键词
     * @param selectedCategoryId 用户选择的分类 ID
     * @return 是否成功
     */
    fun confirmKeywordSelection(selectedKeyword: String, selectedCategoryId: Long): Boolean {
        if (selectedKeyword.isBlank() || selectedCategoryId <= 0) {
            return false
        }

        viewModelScope.launch {
            val selectedCategory = _categories.value.find { it.id == selectedCategoryId }
            selectedCategory?.let { category ->
                categoryKeywordRepository.addUserKeyword(
                    keyword = selectedKeyword,
                    categoryType = category.type
                )
            }

            _categoryId.value = selectedCategoryId
            _hasManuallySelectedCategory.value = true
            _showKeywordSelection.value = false

            performSave()
        }

        return true
    }

    /**
     * 跳过关键词添加，直接保存
     */
    fun skipKeywordSelection() {
        _showKeywordSelection.value = false
        _hasManuallySelectedCategory.value = true
        performSave()
    }

    /**
     * 取消关键词选择
     */
    fun cancelKeywordSelection() {
        _showKeywordSelection.value = false
    }

    /**
     * 关闭关键词选择对话框
     */
    fun dismissKeywordSelection() {
        _showKeywordSelection.value = false
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
                        else -> allCategories.firstOrNull()
                    }
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

    /**
     * 触发分类推荐
     */
    fun triggerRecommendation() {
        viewModelScope.launch {
            android.util.Log.d("InspirationEditVM", "触发推荐, title='${_title.value}', hasManuallySelectedCategory=${_hasManuallySelectedCategory.value}")
            
            val recommendation = categoryMatcher.recommendCategory(
                title = _title.value,
                content = _content.value.takeIf { it.isNotBlank() }
            )

            android.util.Log.d("InspirationEditVM", "推荐结果: $recommendation")

            if (recommendation != null) {
                val category = _categories.value.find { it.type == recommendation.categoryType }
                android.util.Log.d("InspirationEditVM", "匹配到分类: $category")
                _recommendedCategory.value = category
            } else {
                _recommendedCategory.value = null
                android.util.Log.d("InspirationEditVM", "无匹配推荐, title=${_title.value}, recommendedCategory=null, hasManuallySelected=${_hasManuallySelectedCategory.value}")
            }
        }
    }

    /**
     * 接受推荐的分类
     */
    fun acceptRecommendation() {
        _recommendedCategory.value?.let { category ->
            _categoryId.value = category.id
            _hasManuallySelectedCategory.value = true
            _recommendedCategory.value = null
        }
    }

    // ==================== 提醒时间推荐相关方法 ====================

    /**
     * 设置提醒时间
     * 用户在 TimePicker 中手动确认时间后调用
     *
     * @param time 用户选择的提醒时间（毫秒时间戳）
     */
    fun setReminderTime(time: Long) {
        _reminderTime.value = time
        _showReminderRecommendation.value = false
    }

    /**
     * 接受推荐的提醒时间
     * 用户点击推荐标签后调用
     */
    fun acceptReminderRecommendation() {
        val recommended = _recommendedReminderTime.value ?: return
        _reminderTime.value = recommended
        _showReminderRecommendation.value = false
    }

    /**
     * 更新提醒时间推荐
     * 当开始时间或分类变化时自动触发
     */
    private fun updateReminderRecommendation() {
        val startDate = _startDate.value
        val categoryId = _categoryId.value
        val category = _categories.value.find { it.id == categoryId }

        val recommended = reminderRecommender.recommend(
            categoryType = category?.type,
            startDate = startDate
        )

        _recommendedReminderTime.value = recommended

        val isShow = recommended != null &&
                (_reminderTime.value == null || _reminderTime.value != recommended)
        _showReminderRecommendation.value = isShow
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
    }

    /**
     * 清除语音备注
     */
    fun clearVoiceNote() {
        _voiceNotePath.value = null
        _voiceDuration.value = null
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
    }

    /**
     * 清空所有图片路径
     * 同时清理内部存储中的所有对应文件
     */
    fun clearImagePaths() {
        val pathsToRemove = _imagePaths.value.toList()
        _imagePaths.value = emptyList()

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
        val entities = contentBlockDao.getBlocksByTodoId(inspirationId)
        return entities.map { entity ->
            when (entity.type) {
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
     * @param inspirationId 灵感事项 ID
     * @param blocks 当前内存中的 ContentBlock 列表
     */
    suspend fun saveContentBlocks(inspirationId: Long, blocks: List<ContentBlock>) {
        val entities = blocks.mapIndexed { index, block ->
            when (block) {
                is ContentBlock.Image -> ContentBlockEntity(
                    todoId = inspirationId, type = "image", filePath = block.path, orderIndex = index
                )
                is ContentBlock.Voice -> ContentBlockEntity(
                    todoId = inspirationId, type = "voice", filePath = block.path,
                    duration = block.duration, orderIndex = index
                )
                is ContentBlock.Text -> null // 文本块不持久化到独立表
            }
        }.filterNotNull()

        contentBlockDao.replaceBlocksForTodo(inspirationId, entities)
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
        val entities = contentBlockDao.getBlocksByTodoId(inspirationId)
        contentBlockDao.deleteByTodoId(inspirationId)

        /** 异步删除物理文件 */
        entities.forEach { entity ->
            val file = java.io.File(entity.filePath)
            if (file.exists()) file.delete()
        }
    }

    // ==================== 扩展撤销/重做方法（支持内容块操作 + 批量合并） ====================

    /**
     * 提交待合并的暂存操作到撤销栈
     *
     * 在以下时机调用：
     * - 窗口期内出现不同类型的操作
     * - 超过合并窗口期（> 500ms）
     * - undo/redo 操作前（确保所有暂存操作已提交）
     */
    private fun commitPendingMerge() {
        pendingMergeOp?.let { op ->
            _undoStackExtended.addLast(op)
            /** 数量限制：超过最大深度时移除最旧记录 */
            if (_undoStackExtended.size > MAX_UNDO_DEPTH) _undoStackExtended.removeFirst()
            _redoStackExtended.clear()
            _canUndo.value = true
            _canRedo.value = false
            /** 大小限制：序列化后超过 5MB 时从栈底裁剪 */
            trimExtendedStackIfNeeded()
            /** 持久化扩展栈 */
            persistUndoRedoStacksAsync()
        }
        pendingMergeOp = null
    }

    /**
     * 检查并裁剪扩展撤销栈大小
     *
     * 当扩展栈的序列化 JSON 大小超过 EXTENDED_STACK_MAX_SIZE_BYTES（5MB）时，
     * 从栈底（最旧记录）逐条移除直到总大小在限制内。
     *
     * **触发时机**: 每次 commitPendingMerge() 提交新操作后自动调用。
     */
    private fun trimExtendedStackIfNeeded() {
        if (_undoStackExtended.isEmpty()) return

        try {
            val currentSize = serializeEditOperations(_undoStackExtended.toList())
                .toByteArray(Charsets.UTF_8).size.toLong()

            if (currentSize <= EXTENDED_STACK_MAX_SIZE_BYTES) return

            /** 逐步从栈底移除旧记录，直到总大小降至限制内 */
            while (_undoStackExtended.isNotEmpty()) {
                /** 移除栈底最旧的一条记录 */
                val removed = _undoStackExtended.removeFirst()
                if (_undoStackExtended.isEmpty()) break

                /** 重新计算剩余栈的大小 */
                val newSize = serializeEditOperations(_undoStackExtended.toList())
                    .toByteArray(Charsets.UTF_8).size.toLong()
                if (newSize <= EXTENDED_STACK_MAX_SIZE_BYTES) {
                    android.util.Log.d("InspirationEditViewModel",
                        "扩展撤销栈已裁剪：移除 ${removed::class.simpleName}，" +
                        "当前大小 ${newSize / 1024}KB / ${(EXTENDED_STACK_MAX_SIZE_BYTES / 1024)}KB")
                    break
                }
            }
        } catch (e: Exception) {
            /** 序列化失败时仅依赖数量限制（MAX_UNDO_DEPTH），不强制裁剪 */
            android.util.Log.w("InspirationEditViewModel", "扩展撤销栈大小检查失败", e)
        }
    }

    /**
     * 推送内容块删除操作到扩展撤销栈（支持批量合并）
     *
     * 连续删除操作在窗口期内会自动合并。
     */
    fun pushBlockDeletedOperation(
        blocks: List<ContentBlock>,
        index: Int
    ) {
        val newOp = EditOperation.BlockDeleted(blocks, index)
        val now = System.currentTimeMillis()

        if (pendingMergeOp is EditOperation.BlockDeleted
            && now - lastOperationTime < MERGE_WINDOW_MS
        ) {
            /** 窗口内同类型 → 合并（用新的替换旧的） */
            pendingMergeOp = newOp
        } else {
            /** 窗口外或类型不同 → 先提交旧缓存，再缓存新操作 */
            commitPendingMerge()
            pendingMergeOp = newOp
        }
        lastOperationTime = now
    }

    /**
     * 推送内容块插入操作到扩展撤销栈（支持批量合并）
     *
     * 连续插入（如相册选多张图）在窗口期内合并为一条记录。
     */
    fun pushBlockInsertedOperation(index: Int) {
        val newOp = EditOperation.BlockInserted(index)
        val now = System.currentTimeMillis()

        if (pendingMergeOp is EditOperation.BlockInserted
            && now - lastOperationTime < MERGE_WINDOW_MS
        ) {
            /** 窗口内同类型 → 合并（记录最后一次插入位置） */
            pendingMergeOp = newOp
        } else {
            commitPendingMerge()
            pendingMergeOp = newOp
        }
        lastOperationTime = now
    }

    /**
     * 推送内容块排序操作到扩展撤销栈
     *
     * 排序操作不参与合并（每次排序都是独立的用户意图），
     * 但需要先提交任何待合并的暂存操作。
     */
    fun pushBlocksReorderedOperation(oldOrder: List<ContentBlock>) {
        /** 先提交可能存在的暂存操作 */
        commitPendingMerge()
        lastOperationTime = System.currentTimeMillis()

        _undoStackExtended.addLast(EditOperation.BlocksReordered(oldOrder))
        if (_undoStackExtended.size > MAX_UNDO_DEPTH) _undoStackExtended.removeFirst()
        _redoStackExtended.clear()
        _canUndo.value = true
        _canRedo.value = false
        persistUndoRedoStacksAsync()
    }

    /**
     * 执行扩展撤销（支持文本和内容块操作）
     *
     * 优先检查扩展栈，为空时回退到原有纯文本栈。
     *
     * @return 撤销结果：
     *   - TextChange → 返回 AnnotatedString（UI 恢复编辑器文本）
     *   - BlockDeleted → 返回 Pair(被删块列表, 位置)（UI 重新插入被删块）
     *   - BlockInserted → 返回 Int 位置（UI 移除该位置的块）
     *   - BlocksReordered → 返回旧顺序列表（UI 恢复原排列）
     *   - null → 无可撤销操作
     */
    fun undoExtended(): Any? {
        /** 先提交任何待合并的暂存操作 */
        commitPendingMerge()

        /** 优先使用扩展撤销栈 */
        if (_undoStackExtended.isNotEmpty()) {
            val operation = _undoStackExtended.removeLast()
            _canUndo.value = _undoStackExtended.isNotEmpty()

            /** 将当前操作推入 Redo 栈 */
            _redoStackExtended.addLast(operation)
            _canRedo.value = true

            /** 持久化扩展栈状态变更 */
            persistUndoRedoStacksAsync()

            /** 根据操作类型返回对应数据供 UI 处理 */
            return when (operation) {
                is EditOperation.TextChange -> operation.before
                is EditOperation.BlockDeleted -> Pair(operation.blocks, operation.index)
                is EditOperation.BlockInserted -> operation.index
                is EditOperation.BlocksReordered -> operation.oldOrder
            }
        }

        /** 回退到原有的纯文本撤销栈 */
        if (_undoStack.isNotEmpty()) {
            val previous = _undoStack.removeLast()
            _canUndo.value = _undoStack.isNotEmpty()
            _redoStack.addLast(previous as androidx.compose.ui.text.AnnotatedString)
            _canRedo.value = true
            persistUndoRedoStacksAsync()
            return previous
        }

        return null
    }

    /**
     * 执行扩展重做（支持文本和内容块操作）
     *
     * @return 重做结果，格式同 undoExtended()
     */
    fun redoExtended(): Any? {
        /** 先提交任何待合并的暂存操作 */
        commitPendingMerge()

        /** 优先使用扩展 Redo 栈 */
        if (_redoStackExtended.isNotEmpty()) {
            val operation = _redoStackExtended.removeLast()
            _canRedo.value = _redoStackExtended.isNotEmpty()

            /** 将操作推回 Undo 栈 */
            _undoStackExtended.addLast(operation)
            _canUndo.value = true

            /** 持久化扩展栈状态变更 */
            persistUndoRedoStacksAsync()

            /** Redo 需要返回"反向"操作的数据 */
            return when (operation) {
                is EditOperation.TextChange -> operation.before
                is EditOperation.BlockDeleted -> Pair(operation.blocks, operation.index)
                is EditOperation.BlockInserted -> operation.index
                is EditOperation.BlocksReordered -> operation.oldOrder
            }
        }

        /** 回退到原有的纯文本 Redo 栈 */
        if (_redoStack.isNotEmpty()) {
            val restored = _redoStack.removeLast()
            _canRedo.value = _redoStack.isNotEmpty()
            _undoStack.addLast(restored as androidx.compose.ui.text.AnnotatedString)
            _canUndo.value = true
            persistUndoRedoStacksAsync()
            return restored
        }

        return null
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
    }

    /**
     * 防抖调度：延迟 300ms 后将 AnnotatedString 导出为 Markdown 格式
     *
     * 每次调用会取消上一次未完成的防抖任务（cancel-and-restart 模式），
     * 确保只有用户停止输入后的最终状态会被导出，减少高频编辑时的解析开销。
     *
     * **数据流**:
     * 用户按键 → onValueChange → setContent(text) [立即]
     *                              → scheduleFormatExport() [防抖 300ms]
     *                                  → delay 结束 → export() → _contentFormat 更新
     *
     * @param annotatedString 当前的富文本内容（AnnotatedString）
     */
    fun scheduleFormatExport(annotatedString: androidx.compose.ui.text.AnnotatedString) {
        /** 取消上一个未完成的防抖任务 */
        _debounceJob?.cancel()
        /** 启动新的防抖任务：等待 300ms 无新输入后执行导出 */
        _debounceJob = viewModelScope.launch {
            delay(300L)
            val markdown = com.corgimemo.app.util.MarkdownParser.export(annotatedString)
            _contentFormat.value = markdown
        }
    }

    // ==================== Undo/Redo 操作方法 ====================

    /**
     * V2.4 去重压缩阈值：文本重叠率超过此值时视为"高度相似"
     *
     * 当新快照与栈顶快照的文本重叠率 > SIMILARITY_THRESHOLD 时，
     * 用新快照替换栈顶而非追加，避免连续相似操作产生冗余记录。
     *
     * 例如：用户连续点击 3 次加粗按钮（每次只改变一个字符的样式），
     * 栈中只需保留最后一次操作前的状态，中间状态可被压缩。
     */
    private val SIMILARITY_THRESHOLD = 0.9f

    /**
     * 推送当前编辑器状态快照到 Undo 栈
     *
     * 在执行格式变更操作（加粗/斜体/删除线/列表插入等）前调用，
     * 用于支持后续撤销。超过最大深度时自动丢弃最旧的记录。
     *
     * **调用时机**（由 UI 层在以下操作前调用）:
     * - applyBoldFormat / applyItalicFormat / applyStrikethroughFormat
     * - insertUnorderedList / insertOrderedList / insertTodoItem
     * - clearAllFormats
     *
     * **不在文本输入时调用**：避免每字一条记录导致栈爆炸。
     *
     * @param currentText 当前的 AnnotatedString 状态
     */
    fun pushSnapshot(currentText: androidx.compose.ui.text.AnnotatedString) {
        /**
         * V2.4 去重压缩：检查栈顶是否与新快照高度相似
         *
         * 如果栈非空且文本重叠率超过阈值，替换栈顶而非追加。
         * 这显著减少了连续格式微调操作产生的冗余快照数量。
         */
        if (_undoStack.isNotEmpty()) {
            val topSnapshot = _undoStack.last()
            if (calculateTextSimilarity(topSnapshot.text, currentText.text) > SIMILARITY_THRESHOLD) {
                /** 高度相似 → 替换栈顶（压缩冗余） */
                _undoStack[_undoStack.lastIndex] = currentText
                _redoStack.clear()
                _canRedo.value = false
                /** 异步持久化更新后的日志 */
                persistUndoRedoStacksAsync()
                return /** 提前返回，不追加 */
            }
        }

        _undoStack.addLast(currentText)
        /** 超过最大深度时丢弃最旧的记录 */
        if (_undoStack.size > MAX_UNDO_DEPTH) {
            _undoStack.removeFirst()
        }
        _canUndo.value = _undoStack.isNotEmpty()
        /** 新操作使 Redo 栈失效（被覆盖的历史无法重做） */
        _redoStack.clear()
        _canRedo.value = false

        /** 异步持久化 Undo 栈到 DataStore（跨会话保留撤销历史） */
        persistUndoRedoStacksAsync()
    }

    /**
     * 计算两个文本之间的相似度（基于最长公共子序列比例）
     *
     * 用于 Undo 栈去重压缩：当新快照与栈顶快照的相似度超过阈值时，
     * 认为是冗余操作，替换而非追加。
     *
     * **算法**：使用简化的编辑距离（Levenshtein 距离）归一化到 [0, 1] 区间。
     * - 1.0 = 完全相同
     * - 0.0 = 完全不同
     * - >0.9 = 高度相似（触发压缩）
     *
     * @param text1 第一个文本
     * @param text2 第二个文本
     * @return 相似度值（0.0 ~ 1.0）
     */
    private fun calculateTextSimilarity(text1: String, text2: String): Float {
        if (text1 == text2) return 1.0f
        if (text1.isBlank() || text2.isBlank()) return 0.0f

        /** 使用最长公共子序列 (LCS) 长度作为相似度指标 */
        val lcsLength = longestCommonSubsequenceLength(text1, text2)
        val maxLen = maxOf(text1.length, text2.length)

        return lcsLength.toFloat() / maxLen.toFloat()
    }

    /**
     * 计算两个字符串的最长公共子序列 (LCS) 长度
     *
     * 使用动态规划算法，时间复杂度 O(m*n)。
     * 对于短文本（<500 字符），性能完全可接受。
     *
     * @param s1 字符串 1
     * @param s2 字符串 2
     * @return LCS 长度
     */
    private fun longestCommonSubsequenceLength(s1: String, s2: String): Int {
        /** 短文本优化：直接使用标准 DP */
        val m = s1.length
        val n = s2.length

        /** 使用滚动数组优化空间复杂度为 O(min(m,n)) */
        val prev = IntArray(n + 1)
        val curr = IntArray(n + 1)

        for (i in 1..m) {
            for (j in 1..n) {
                curr[j] = if (s1[i - 1] == s2[j - 1]) {
                    prev[j - 1] + 1
                } else {
                    maxOf(curr[j - 1], prev[j])
                }
            }
            /** 滚动：prev ← curr */
            val temp = prev
            prev.forEachIndexed { idx, _ -> prev[idx] = curr[idx] }
            curr.forEachIndexed { idx, _ -> curr[idx] = temp[idx] }
        }

        return prev[n]
    }

    /**
     * 撤销上一操作
     *
     * 从 Undo 栈弹出上一个状态并返回，
     * 同时将当前状态推入 Redo 栈以支持重做。
     *
     * @return 上一状态的 AnnotatedString；无历史记录时返回 null
     */
    fun undo(): androidx.compose.ui.text.AnnotatedString? {
        if (_undoStack.isEmpty()) return null

        val previous = _undoStack.removeLast()
        _canUndo.value = _undoStack.isNotEmpty()

        /** 异步持久化更新后的双栈 */
        persistUndoRedoStacksAsync()

        return previous
    }

    /**
     * 将当前状态推入 Redo 栈（undo 操作时由 UI 层调用）
     *
     * @param currentText 撤销前的当前状态
     */
    fun pushToRedo(currentText: androidx.compose.ui.text.AnnotatedString) {
        _redoStack.addLast(currentText)
        _canRedo.value = _redoStack.isNotEmpty()
    }

    /**
     * 重做被撤销的操作
     *
     * 从 Redo 栈弹出被撤销的状态并返回，
     * 同时将当前状态推回 Undo 栈以支持再次撤销。
     *
     * @return 被恢复状态的 AnnotatedString；无重做记录时返回 null
     */
    fun redo(): androidx.compose.ui.text.AnnotatedString? {
        if (_redoStack.isEmpty()) return null

        val restored = _redoStack.removeLast()
        _canRedo.value = _redoStack.isNotEmpty()

        /** 异步持久化更新后的双栈 */
        persistUndoRedoStacksAsync()

        return restored
    }

    /**
     * 获取 Undo 历史快照描述列表（用于长按菜单显示）
     *
     * 返回栈中每个快照的可读摘要（截取前 30 个字符），
     * 配合索引用于精确恢复到指定历史状态。
     *
     * **返回格式**: List<Pair<index, description>>
     * - index: 快照在栈中的位置（从栈顶=0 到栈底）
     * - description: 文本摘要（如 "加粗: Hello **World**"）
     *
     * @return 描述列表（最新的在前），空栈时返回空列表
     */
    fun getUndoHistoryDescriptions(): List<Pair<Int, String>> {
        return _undoStack.reversed().mapIndexed { index, annotatedString ->
            val text = annotatedString.text
            /** 截取前 30 个字符作为摘要，超出显示省略号 */
            val preview = if (text.length > 30) "${text.take(30)}..." else text
            Pair(index, preview)
        }
    }

    /**
     * 批量撤销到指定历史状态
     *
     * 从 Undo 栈中弹出指定数量的快照，
     * 将中间状态推入 Redo 栈（支持逐步重做）。
     *
     * @param steps 要撤销的步数（1 = 撤销一次，等同于 undo()）
     * @return 目标状态的 AnnotatedString；步数不足时返回 null
     */
    fun undoToHistoryStep(steps: Int): androidx.compose.ui.text.AnnotatedString? {
        if (steps <= 0 || _undoStack.size < steps) return null

        var targetState: androidx.compose.ui.text.AnnotatedString? = null

        repeat(steps) {
            val previous = _undoStack.removeLast()
            /** 将弹出的中间状态推入 Redo 栈 */
            _redoStack.addLast(targetState ?: previous)
            targetState = previous
        }

        _canUndo.value = _undoStack.isNotEmpty()
        _canRedo.value = _redoStack.isNotEmpty()

        persistUndoRedoStacksAsync()

        return targetState
    }

    /**
     * 获取 Redo 历史快照描述列表（用于长按菜单显示）
     *
     * 与 getUndoHistoryDescriptions() 对称：
     * - 返回 Redo 栈中每个快照的可读摘要（截取前 30 个字符）
     * - **倒序排列**：最早被重做的记录在前（索引0 = 最先可重做的）
     * - 配合索引用于精确恢复到指定历史状态
     *
     * @return List<Pair<index, description>> — index 用于 redoToHistoryStep()
     */
    fun getRedoHistoryDescriptions(): List<Pair<Int, String>> {
        return _redoStack.mapIndexed { index, annotatedString ->
            val text = annotatedString.text
            /** 截取前 30 个字符作为摘要，超出显示省略号 */
            val preview = if (text.length > 30) "${text.take(30)}..." else text
            Pair(index, preview)
        }
    }

    /**
     * 批量重做到指定历史状态
     *
     * 与 undoToHistoryStep() 对称但方向相反：
     * - 从 Redo 栈**头部**（最早的重做记录）开始弹出指定数量的快照
     * - 将中间状态推入 Undo 栈（支持再次撤销）
     * - 这意味着索引 0 对应「最早可重做」的状态
     *
     * @param steps 要重做的步数（1 = 重做一次，等同于 redo()）
     * @return 目标状态的 AnnotatedString；步数不足时返回 null
     */
    fun redoToHistoryStep(steps: Int): androidx.compose.ui.text.AnnotatedString? {
        if (steps <= 0 || _redoStack.size < steps) return null

        var targetState: androidx.compose.ui.text.AnnotatedString? = null

        repeat(steps) {
            /** 从头部取出（最早的 redo 记录） */
            val next = _redoStack.removeFirst()
            /** 将弹出的中间状态推入 Undo 栈 */
            _undoStack.addLast(targetState ?: next)
            targetState = next
        }

        _canUndo.value = _undoStack.isNotEmpty()
        _canRedo.value = _redoStack.isNotEmpty()

        persistUndoRedoStacksAsync()

        return targetState
    }

    // ==================== Undo/Redo 栈持久化方法 ====================

    /**
     * 异步增量持久化 Undo/Redo 栈到 DataStore
     *
     * 采用 Append-Only 日志模式（V2.3 优化）：
     * - pushSnapshot 时仅追加最新一条快照到日志尾部（~100B/次）
     * - undo/redo 时同样追加操作后的当前状态
     * - 相比 V2.2 的全量序列化模式（~2KB/次），写入量减少 ~95%
     *
     * **恢复时**从日志中读取全部记录并反序列化填充内存栈。
     *
     * **调用时机**:
     * - pushSnapshot() 后（每次新增快照）
     * - undo() 后（撤销操作改变栈状态）
     * - redo() 后（重做操作改变栈状态）
     */
    private fun persistUndoRedoStacksAsync() {
        val currentInspirationId = existingInspiration?.id ?: return /** 新建灵感无 ID，跳过 */

        viewModelScope.launch {
            try {
                /**
                 * 使用按 InspirationId 隔离的增量持久化
                 * 每个 Inspiration 独立存储编辑历史，避免多 Inspiration 并发时数据混淆
                 */
                if (_undoStack.isNotEmpty()) {
                    /** 序列化 undoStack 栈顶的最新一条记录（纯文本操作） */
                    val latestUndoJson = com.corgimemo.app.util.AnnotatedStringSerializer
                        .serialize(_undoStack.last())
                    corgiPreferences.appendUndoLogEntry(currentInspirationId, latestUndoJson)
                }

                if (_redoStack.isNotEmpty()) {
                    /** 序列化 redoStack 栈顶的最新一条记录（纯文本操作） */
                    val latestRedoJson = com.corgimemo.app.util.AnnotatedStringSerializer
                        .serialize(_redoStack.last())
                    corgiPreferences.appendRedoLogEntry(currentInspirationId, latestRedoJson)
                }

                /**
                 * 扩展栈持久化（内容块操作）
                 * 使用独立的 key 前缀区分于纯文本栈
                 * 序列化格式：JSON 数组，每项为 EditOperation 的 JSON 表示
                 */
                if (_undoStackExtended.isNotEmpty()) {
                    val extendedUndoJson = serializeEditOperations(_undoStackExtended.toList())
                    corgiPreferences.appendExtendedUndoLog(currentInspirationId, extendedUndoJson)
                }

                if (_redoStackExtended.isNotEmpty()) {
                    val extendedRedoJson = serializeEditOperations(_redoStackExtended.toList())
                    corgiPreferences.appendExtendedRedoLog(currentInspirationId, extendedRedoJson)
                }
            } catch (e: Exception) {
                android.util.Log.w("InspirationEditViewModel", "Undo 栈增量持久化失败", e)
            }
        }
    }

    /**
     * 将 EditOperation 列表序列化为 JSON 字符串
     *
     * 每个操作序列化为一个 JSON 对象，包含类型标识和具体数据：
     * - TextChange: {"t":"tx","d":"<AnnotatedString JSON>"}
     * - BlockDeleted: {"t":"bd","i":<index>,"b":[<blocks JSON>]}
     * - BlockInserted: {"t":"bi","i":<index>}
     * - BlocksReordered: {"t":"br","o":[<order JSON>]}
     */
    private fun serializeEditOperations(operations: List<EditOperation>): String {
        val jsonArray = org.json.JSONArray()
        operations.forEach { op ->
            val json = org.json.JSONObject()
            when (op) {
                is EditOperation.TextChange -> {
                    json.put("t", "tx")
                    json.put("d", com.corgimemo.app.util.AnnotatedStringSerializer.serialize(op.before))
                }
                is EditOperation.BlockDeleted -> {
                    json.put("t", "bd")
                    json.put("i", op.index)
                    val blocksArray = org.json.JSONArray()
                    op.blocks.forEach { block ->
                        blocksArray.put(serializeContentBlock(block))
                    }
                    json.put("b", blocksArray)
                }
                is EditOperation.BlockInserted -> {
                    json.put("t", "bi")
                    json.put("i", op.index)
                }
                is EditOperation.BlocksReordered -> {
                    json.put("t", "br")
                    val orderArray = org.json.JSONArray()
                    op.oldOrder.forEach { block ->
                        orderArray.put(serializeContentBlock(block))
                    }
                    json.put("o", orderArray)
                }
            }
            jsonArray.put(json)
        }
        return jsonArray.toString()
    }

    /**
     * 将单个 ContentBlock 序列化为 JSON 对象
     */
    private fun serializeContentBlock(block: ContentBlock): org.json.JSONObject {
        return when (block) {
            is ContentBlock.Image -> {
                org.json.JSONObject().apply {
                    put("type", "image")
                    put("path", block.path)
                }
            }
            is ContentBlock.Voice -> {
                org.json.JSONObject().apply {
                    put("type", "voice")
                    put("path", block.path)
                    put("duration", block.duration ?: 0)
                }
            }
            is ContentBlock.Text -> {
                org.json.JSONObject().apply {
                    put("type", "text")
                    put("content", block.content)
                }
            }
        }
    }

    /**
     * 从 JSON 字符串反序列化 EditOperation 列表
     */
    private fun deserializeEditOperations(json: String): List<EditOperation> {
        val operations = mutableListOf<EditOperation>()
        try {
            val jsonArray = org.json.JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val type = obj.getString("t")
                when (type) {
                    "tx" -> {
                        val data = obj.getString("d")
                        val annotatedStr = com.corgimemo.app.util.AnnotatedStringSerializer.deserialize(data)
                        operations.add(EditOperation.TextChange(annotatedStr))
                    }
                    "bd" -> {
                        val index = obj.getInt("i")
                        val blocksArray = obj.getJSONArray("b")
                        val blocks = mutableListOf<ContentBlock>()
                        for (j in 0 until blocksArray.length()) {
                            blocks.add(deserializeContentBlock(blocksArray.getJSONObject(j)))
                        }
                        operations.add(EditOperation.BlockDeleted(blocks, index))
                    }
                    "bi" -> {
                        val index = obj.getInt("i")
                        operations.add(EditOperation.BlockInserted(index))
                    }
                    "br" -> {
                        val orderArray = obj.getJSONArray("o")
                        val order = mutableListOf<ContentBlock>()
                        for (j in 0 until orderArray.length()) {
                            order.add(deserializeContentBlock(orderArray.getJSONObject(j)))
                        }
                        operations.add(EditOperation.BlocksReordered(order))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("InspirationEditViewModel", "扩展撤销栈反序列化失败", e)
        }
        return operations
    }

    /**
     * 从 JSON 对象反序列化为 ContentBlock
     */
    private fun deserializeContentBlock(obj: org.json.JSONObject): ContentBlock {
        return when (obj.getString("type")) {
            "image" -> ContentBlock.Image(obj.getString("path"))
            "voice" -> ContentBlock.Voice(
                obj.getString("path"),
                if (obj.has("duration") && !obj.isNull("duration")) obj.getInt("duration") else null
            )
            else -> ContentBlock.Text(obj.optString("content", ""))
        }
    }

    /**
     * 从 DataStore 增量日志恢复 Undo/Redo 栈（按 InspirationId 隔离）
     *
     * 在加载已有灵感时调用，读取属于该 InspirationId 的已保存撤销日志。
     *
     * **隔离策略**：使用 `{inspirationId}_undo_log` / `{inspirationId}_redo_log` key，
     * 每个 Inspiration 独立存储编辑历史，不再有全局共享冲突问题。
     *
     * **向后兼容**：首次使用新格式时自动检测并迁移旧的全局格式数据。
     *
     * @param inspirationId 当前编辑的灵感 ID
     */
    private suspend fun restoreUndoStacks(inspirationId: Long) {
        try {
            /** 向后兼容迁移 — 检测旧全局格式数据 */
            corgiPreferences.migrateLegacyUndoLogIfPresent(inspirationId)

            /** 从按 InspirationId 隔离的增量日志读取并反序列化（纯文本操作） */
            val undoLogJson = corgiPreferences.getUndoLog(inspirationId)
            val redoLogJson = corgiPreferences.getRedoLog(inspirationId)

            if (!undoLogJson.isNullOrBlank() && undoLogJson != "[]") {
                val restoredUndo = com.corgimemo.app.util.AnnotatedStringSerializer
                    .deserializeList(undoLogJson)
                _undoStack.clear()
                _undoStack.addAll(restoredUndo)
                _canUndo.value = _undoStack.isNotEmpty()
            }

            if (!redoLogJson.isNullOrBlank() && redoLogJson != "[]") {
                val restoredRedo = com.corgimemo.app.util.AnnotatedStringSerializer
                    .deserializeList(redoLogJson)
                _redoStack.clear()
                _redoStack.addAll(restoredRedo)
                _canRedo.value = _redoStack.isNotEmpty()
            }

            /**
             * 恢复扩展撤销栈（内容块操作）
             * 使用独立的 key 前缀，与纯文本栈分开存储
             */
            val extendedUndoJson = corgiPreferences.getExtendedUndoLog(inspirationId)
            if (!extendedUndoJson.isNullOrBlank() && extendedUndoJson != "[]") {
                val restoredExtendedUndo = deserializeEditOperations(extendedUndoJson)
                _undoStackExtended.clear()
                _undoStackExtended.addAll(restoredExtendedUndo)
                if (_undoStackExtended.isNotEmpty()) _canUndo.value = true
            }

            val extendedRedoJson = corgiPreferences.getExtendedRedoLog(inspirationId)
            if (!extendedRedoJson.isNullOrBlank() && extendedRedoJson != "[]") {
                val restoredExtendedRedo = deserializeEditOperations(extendedRedoJson)
                _redoStackExtended.clear()
                _redoStackExtended.addAll(restoredExtendedRedo)
                if (_redoStackExtended.isNotEmpty()) _canRedo.value = true
            }

            android.util.Log.d("InspirationEditViewModel",
                "从增量日志恢复 Undo 栈: ${_undoStack.size} 条, Redo 栈: ${_redoStack.size} 条, " +
                "扩展 Undo: ${_undoStackExtended.size} 条, 扩展 Redo: ${_redoStackExtended.size} 条 (inspirationId=$inspirationId)")
        } catch (e: Exception) {
            android.util.Log.w("InspirationEditViewModel", "Undo 栈增量日志恢复失败，使用空栈", e)
            /** 恢复失败时清空，确保一致性 */
            _undoStack.clear()
            _redoStack.clear()
            _undoStackExtended.clear()
            _redoStackExtended.clear()
            _canUndo.value = false
            _canRedo.value = false
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
     * 解码标签JSON字符串为列表
     * 用于从 Inspiration.tags 字段读取时反序列化
     *
     * @param tagsJson JSON字符串
     * @return 标签列表，解析失败返回空列表
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
     * 将图片路径列表编码为JSON字符串
     * 用于持久化存储到数据库
     *
     * @param paths 图片路径列表
     * @return JSON格式的字符串（如 ["path1","path2"]）
     */
    private fun encodePaths(paths: List<String>): String {
        return try {
            org.json.JSONArray(paths).toString()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 从JSON字符串解码图片路径列表
     * 用于从数据库读取时反序列化
     *
     * @param json JSON格式字符串
     * @return 解析后的路径列表，解析失败返回空列表
     */
    private fun decodePaths(json: String): List<String> {
        return try {
            val jsonArray = org.json.JSONArray(json)
            (0 until jsonArray.length()).map { i -> jsonArray.getString(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== 关联管理方法 ==========

    /**
     * 添加关联关系
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
