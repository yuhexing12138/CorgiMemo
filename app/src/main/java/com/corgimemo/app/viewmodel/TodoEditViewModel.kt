package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.event.TodoEvent
import com.corgimemo.app.data.event.TodoEventBus
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.domain.ReminderRecommender
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.corgimemo.app.util.TagUtils
import javax.inject.Inject

/**
 * 分组保存状态
 *
 * 跟踪编辑页内每个 groupId 的保存状态，
 * 用于控制 UI 显示（按钮文字/颜色）和决定是 insert 还是 update。
 *
 * @property groupId 分组 ID（对应 TodoLine.groupId）
 * @property isSaved 是否已保存到数据库
 * @property savedTodoId 已保存的 TodoItem 数据库 ID（isSaved=true 时有效）
 * @property savedAt 最后保存时间戳
 * @property contentSnapshot 保存时的内容快照（用于检测是否真正被编辑）
 */
data class GroupSaveState(
    val groupId: Int,
    val isSaved: Boolean = false,
    val savedTodoId: Long? = null,
    val savedAt: Long? = null,
    /** 保存时该组所有行的文本拼接（用于比较是否发生变化） */
    val contentSnapshot: String = ""
)

/**
 * 待办编辑 ViewModel
 */
@HiltViewModel
class TodoEditViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val categoryRepository: CategoryRepository,
    private val corgiPreferences: CorgiPreferences,
    private val cardRelationRepository: CardRelationRepository,
    private val contentBlockDao: ContentBlockDao,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    /** 防抖导出任务引用：用于延迟执行 MarkdownParser.export() */
    private var _debounceJob: Job? = null
    private val reminderRecommender = ReminderRecommender()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    /**
     * 各分组的分类 ID 状态
     *
     * key = groupId (Int), value = categoryId (Long, 0L = 未分类)
     * 每个编辑容器拥有独立的分类，保存时写入对应 TodoItem.categoryId
     *
     * 设计与 _groupReminders / _groupPriorities / _groupRepeatTypes 完全一致，
     * 保证多容器场景下分类状态互不影响。
     */
    private val _groupCategoryIds = MutableStateFlow<Map<Int, Long>>(emptyMap())

    /** 暴露分组分类 ID 供 UI 层收集 */
    val groupCategoryIds: StateFlow<Map<Int, Long>> = _groupCategoryIds.asStateFlow()

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

    private val _geofenceType = MutableStateFlow(0)
    val geofenceType: StateFlow<Int> = _geofenceType.asStateFlow()

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

    /**
     * 注：原"推荐分类 / 关键词选择 / 是否手动选择"等状态已移除。
     * 待办编辑页不再做关键词推荐分类（功能仅在灵感编辑页保留）。
     * 新建模式下 _groupCategoryIds 保持空 map（groupId=0 对应 0L），分类按钮显示"分类"占位符。
     */
    private val _isCategoriesLoaded = MutableStateFlow(false)
    val isCategoriesLoaded: StateFlow<Boolean> = _isCategoriesLoaded.asStateFlow()

    // 提醒时间相关状态（按 groupId 索引：每个编辑容器拥有独立提醒时间）
    /**
     * 各分组的提醒时间状态
     *
     * key = groupId (Int), value = 提醒时间戳（毫秒，null 表示未设置）
     * 每个编辑容器拥有独立的提醒时间，保存时写入对应 TodoItem.reminderTime
     */
    private val _groupReminders = MutableStateFlow<Map<Int, Long?>>(emptyMap())

    /** 暴露分组提醒时间供 UI 层收集 */
    val groupReminders: kotlinx.coroutines.flow.StateFlow<Map<Int, Long?>> = _groupReminders.asStateFlow()

    /**
     * 各分组的重复类型状态
     *
     * key = groupId (Int), value = 重复类型（0=不重复, 1=每天, 2=每周, 3=每月, 4=周一至周五, 5=每年）
     * 每个编辑容器拥有独立的重复类型，保存时写入对应 TodoItem.repeatType
     */
    private val _groupRepeatTypes = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /** 暴露分组重复类型供 UI 层收集 */
    val groupRepeatTypes: kotlinx.coroutines.flow.StateFlow<Map<Int, Int>> = _groupRepeatTypes.asStateFlow()

    // 语音备注相关状态
    private val _voiceNotePath = MutableStateFlow<String?>(null)
    val voiceNotePath: StateFlow<String?> = _voiceNotePath.asStateFlow()

    private val _voiceDuration = MutableStateFlow<Int?>(null)
    val voiceDuration: StateFlow<Int?> = _voiceDuration.asStateFlow()

    /** 图片路径列表状态（存储内部存储中的绝对路径） */
    private val _imagePaths = MutableStateFlow<List<String>>(emptyList())
    val imagePaths: StateFlow<List<String>> = _imagePaths.asStateFlow()

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
     * 行级附件快照数据（JSON 序列化字符串）
     *
     * 存储每行的图片和录音附件信息，用于在重新打开待办时精确恢复到对应行。
     *
     * 数据格式：由 LineSnapshotUtils 序列化的 JSON 字符串，
     * 带有 #LINE_ATTACHMENTS# 前缀标识。
     *
     * 生命周期：
     * - 保存时：从 UI 层的 todoLines 构建快照并序列化存储
     * - 加载时：从数据库读取并反序列化，供 UI 层恢复附件
     */
    private val _lineAttachmentsSnapshot = MutableStateFlow<String?>(null)

    /** 暴露行级附件快照供外部读取 */
    val lineAttachmentsSnapshot: kotlinx.coroutines.flow.StateFlow<String?> = _lineAttachmentsSnapshot.asStateFlow()

    /**
     * 各分组的保存状态映射
     *
     * key = groupId (Int), value = 该组的保存状态
     */
    private val _groupSaveStates = MutableStateFlow<Map<Int, GroupSaveState>>(emptyMap())

    /** 暴露分组保存状态供 UI 层收集 */
    val groupSaveStates: kotlinx.coroutines.flow.StateFlow<Map<Int, GroupSaveState>> = _groupSaveStates.asStateFlow()

    /**
     * 各分组的优先级状态
     *
     * key = groupId (Int), value = 优先级 (0=无, 1=低, 2=中, 3=高)
     * 每个编辑容器拥有独立的优先级，保存时写入 TodoItem.priority
     */
    private val _groupPriorities = MutableStateFlow<Map<Int, Int>>(emptyMap())

    /** 暴露分组优先级供 UI 层收集 */
    val groupPriorities: kotlinx.coroutines.flow.StateFlow<Map<Int, Int>> = _groupPriorities.asStateFlow()

    /**
     * 保存行级附件快照（UI 层调用）
     *
     * 在用户点击保存前，UI 层应调用此方法将当前 todoLines 的完整状态（包括每行附件）序列化。
     * performSave() 会将此数据持久化到数据库的 contentFormat 字段中。
     *
     * @param snapshotJson 序列化后的 JSON 字符串（由 LineSnapshotUtils.serialize() 生成）
     */
    fun saveLineAttachmentsSnapshot(snapshotJson: String) {
        _lineAttachmentsSnapshot.value = snapshotJson
    }

    /**
     * 加载行级附件快照（内部使用）
     *
     * 从 TodoItem 的 contentFormat 字段中提取行级附件快照数据。
     * contentFormat 字段的格式为："{Markdown内容}|||LINE_ATTACHMENTS|||[{JSON}]"
     *
     * @param todo 从数据库加载的 TodoItem 对象
     */
    private fun loadLineAttachmentsSnapshot(todo: TodoItem) {
        val format = todo.contentFormat
        if (!format.isNullOrBlank() && format.contains(com.corgimemo.app.ui.model.LineSnapshotUtils.SEPARATOR)) {
            _lineAttachmentsSnapshot.value = format
            android.util.Log.w("TodoEditVM", "加载行级附件快照成功, 长度=${format.length}")
            // 同时提取纯净的 Markdown 内容用于显示
            _contentFormat.value = com.corgimemo.app.ui.model.LineSnapshotUtils.extractDisplayContent(format)
        } else if (!format.isNullOrBlank() && format.contains("LINE_ATTACHMENTS")) {
            // 兼容旧格式（#LINE_ATTACHMENTS# 前缀）
            _lineAttachmentsSnapshot.value = format
            _contentFormat.value = ""  // 旧格式没有 Markdown 内容
            android.util.Log.w("TodoEditVM", "加载旧格式行级附件快照, 长度=${format.length}")
        } else {
            _lineAttachmentsSnapshot.value = null
            // 正常情况：contentFormat 就是纯 Markdown 内容
            _contentFormat.value = format ?: ""
        }
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

    /**
     * 数据加载完成标志
     *
     * 用于解决 UI 层初始化竞态条件：
     * - false：数据尚未从数据库加载（ViewModel 中的字段为初始空值）
     * - true：loadTodo() 已完成，title/content/subTasks 等字段已填充实际数据
     *
     * UI 层应等待此标志为 true 后再初始化 todoLines 等依赖这些数据的组件。
     */
    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private var existingTodo: TodoItem? = null

    fun setTitle(title: String) {
        _title.value = title
    }

    /**
     * 注：原 setTitleWithRecommendation（设置标题并触发分类推荐）已移除。
     * 待办编辑页不再做关键词推荐分类，UI 层应改用 setTitle()。
     * 该功能仅在灵感编辑页（InspirationEditViewModel）保留。
     */

    fun setContent(content: String) {
        _content.value = content
    }

    /**
     * 设置指定分组的分类 ID
     *
     * @param groupId 分组 ID
     * @param categoryId 分类 ID（0L = 未分类）
     */
    fun setGroupCategory(groupId: Int, categoryId: Long) {
        _groupCategoryIds.value = _groupCategoryIds.value + (groupId to categoryId)
    }

    /**
     * 清除指定分组的分类（置为 0L 未分类）
     *
     * @param groupId 分组 ID
     */
    fun clearGroupCategory(groupId: Int) {
        _groupCategoryIds.value = _groupCategoryIds.value + (groupId to 0L)
    }

    fun setPriority(priority: Int) {
        _priority.value = priority
    }

    fun setStartDate(startDate: Long?) {
        _startDate.value = startDate
    }

    /**
     * 设置截止时间
     * 用户在时间选择器中确认后调用
     *
     * @param dueDate 截止时间（毫秒时间戳）
     */
    fun setDueDate(dueDate: Long?) {
        _dueDate.value = dueDate
        // 注：旧实现里"联动设置 _reminderTime"逻辑已移除，reminderTime 由各分组独立管理
    }

    fun setEstimatedDurationMinutes(minutes: Int?) {
        _estimatedDurationMinutes.value = minutes
    }

    /** 旧 setRepeatType 已废弃，重复类型由各分组独立管理。UI 层应改用 setGroupRepeatType(groupId, type) */
    @Suppress("UnusedParameter")
    fun setRepeatType(repeatType: Int) {
        // 留空：Task 4 会把所有 UI 调用点切到 setGroupRepeatType
    }

    /**
     * 设置指定分组的提醒时间
     *
     * 用户在时间选择器中确认后调用。
     *
     * @param groupId 分组 ID
     * @param time 提醒时间（毫秒时间戳）
     */
    fun setGroupReminder(groupId: Int, time: Long) {
        _groupReminders.value = _groupReminders.value + (groupId to time)
    }

    /**
     * 清除指定分组的提醒时间
     *
     * 用户点击 × 按钮时调用。幂等：groupId 不存在时无副作用。
     *
     * @param groupId 分组 ID
     */
    fun clearGroupReminder(groupId: Int) {
        _groupReminders.value = _groupReminders.value - groupId
    }

    /**
     * 获取指定分组的提醒时间
     *
     * @param groupId 分组 ID
     * @return 提醒时间戳；未设置返回 null
     */
    fun getGroupReminder(groupId: Int): Long? {
        return _groupReminders.value[groupId]
    }

    /**
     * 设置指定分组的重复类型
     *
     * @param groupId 分组 ID
     * @param type 重复类型（0=不重复, 1=每天, 2=每周, 3=每月, 4=周一至周五, 5=每年）
     */
    fun setGroupRepeatType(groupId: Int, type: Int) {
        _groupRepeatTypes.value = _groupRepeatTypes.value + (groupId to type)
    }

    /**
     * 获取指定分组的重复类型
     *
     * @param groupId 分组 ID
     * @return 重复类型；未设置返回 0（不重复）
     */
    fun getGroupRepeatType(groupId: Int): Int {
        return _groupRepeatTypes.value[groupId] ?: 0
    }

    // 地理围栏相关方法
    fun setGeofenceLat(lat: Double?) {
        _geofenceLat.value = lat
    }

    fun setGeofenceLng(lng: Double?) {
        _geofenceLng.value = lng
    }

    fun setGeofenceRadius(radius: Float) {
        _geofenceRadius.value = radius
    }

    fun setGeofenceType(type: Int) {
        _geofenceType.value = type
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        _geofenceEnabled.value = enabled
    }

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
            todoId = existingTodo?.id ?: 0,
            title = title,
            isCompleted = false,
            order = currentList.size + 1
        )
        _subTasks.value = currentList + newSubTask
    }

    /**
     * 整体替换子任务列表（用于 UI 层实时同步）
     *
     * 【关键方法】解决用户输入时子任务重复累积问题
     *
     * 与 addSubTask()（增量添加）不同，此方法是整体替换：
     * - 每次调用都会完全替换 _subTasks 列表
     * - 适用于 LaunchedEffect(todoLines) 中的实时同步场景
     * - 确保 ViewModel 中的子任务列表与 UI 层的 todoLines 保持一致
     *
     * 使用场景：
     * - 用户编辑子任务文本时（从 "测试" 改为 "测试2"）
     * - 用户添加/删除子任务行时
     * - 任何导致 todoLines 变化的操作
     *
     * @param newSubTasks 新的子任务列表（通常从 todoLines 构建）
     */
    fun replaceSubTasks(newSubTasks: List<SubTask>) {
        _subTasks.value = newSubTasks
    }

    /**
     * 获取当前待办的 ID（用于 UI 层构建子任务对象）
     *
     * @return 当前待办 ID，如果新建模式则返回 0
     */
    fun getCurrentTodoId(): Long = existingTodo?.id ?: 0L

    /** 暴露 existingTodo 供外部访问（用于获取 todoId） */
    val currentTodo get() = existingTodo

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
     * 切换子任务完成状态（仅在编辑已有待办时持久化到数据库）
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

        if (existingTodo != null && subTask.id > 0) {
            viewModelScope.launch {
                SubTaskManager.toggleSubTaskCompletion(context, subTask.id)
            }
        }
    }

    /**
     * 加载待办及子任务
     *
     * @param todoId 待办 ID
     */
    fun loadTodo(todoId: Long) {
        viewModelScope.launch {
            todoRepository.getTodoById(todoId)?.let { todo ->
                existingTodo = todo
                _title.value = todo.title
                _content.value = todo.content ?: ""
                // 单容器场景：groupId=0 持有该 todo 的分类
                _groupCategoryIds.value = mapOf(0 to todo.categoryId)
                _priority.value = todo.priority
                _startDate.value = todo.startDate
                _dueDate.value = todo.dueDate
                _estimatedDurationMinutes.value = todo.estimatedDurationMinutes
                // 把"全局 reminderTime/repeatType"映射到"分组 0"，实现向后兼容
                // 历史 todo 在旧实现里 reminderTime/repeatType 只有一份，对应第一个分组
                _groupRepeatTypes.value = mapOf(0 to todo.repeatType)
                _geofenceLat.value = todo.geofenceLat
                _geofenceLng.value = todo.geofenceLng
                _geofenceRadius.value = todo.geofenceRadius
                _geofenceType.value = todo.geofenceType
                _geofenceEnabled.value = todo.geofenceEnabled
                _geofenceAddress.value = todo.geofenceAddress

                _groupReminders.value = mapOf(0 to todo.reminderTime)

                // 加载语音备注
                _voiceNotePath.value = todo.voiceNotePath
                _voiceDuration.value = todo.voiceDuration

                // 加载图片路径列表（从JSON字符串反序列化）
                if (todo.imagePaths.isNotBlank()) {
                    _imagePaths.value = decodePaths(todo.imagePaths)
                }

                /** 加载背景颜色（从 ARGB 整数值恢复为 Compose Color） */
                _backgroundColor.value = todo.backgroundColor

                /** 加载富文本格式化内容（Markdown 字符串） */
                _contentFormat.value = todo.contentFormat ?: ""

                /** 加载行级附件快照（从 contentFormat 中提取） */
                loadLineAttachmentsSnapshot(todo)

                /** 从 DataStore 恢复跨会话的 Undo/Redo 栈（如有） */
                restoreUndoStacks(todoId)

                // 加载关联关系
                _relations.value = cardRelationRepository.getRelationsBlocking("todo", todoId)

                val subTasks = SubTaskManager.getSubTasks(context, todoId)
                _subTasks.value = subTasks

                /** 诊断日志：追踪 loadTodo 数据加载 */
            android.util.Log.w(
                "TodoEditLoad",
                "loadTodo 完成: id=$todoId, title='${todo.title}', " +
                "content='${todo.content}', subTasks=${subTasks.map { it.title }}, " +
                "imagePaths='${todo.imagePaths}', voiceNotePath='${todo.voiceNotePath}', " +
                "_imagePaths=${_imagePaths.value}, _voiceNotePath=${_voiceNotePath.value}"
            )

                /** 标记数据加载完成，通知 UI 层可以开始初始化 */
                _isLoaded.value = true

                /**
                 * 【多卡片修复】初始化 groupId=0 的保存状态
                 *
                 * 从列表点击卡片进入编辑页时，existingTodo 已有数据库 ID。
                 * 必须将此 ID 记录到 _groupSaveStates 中，
                 * 这样后续 saveGroup() 才能执行 UPDATE 而非 INSERT，
                 * 避免创建重复卡片。
                 */
                val contentSnapshot = buildString {
                    appendLine(todo.title ?: "")
                    subTasks.forEach { appendLine(it.title) }
                }.trim()

                _groupSaveStates.value = mapOf(
                    0 to GroupSaveState(
                        groupId = 0,
                        isSaved = true,
                        savedTodoId = todo.id,  // 关键：记录已有 ID
                        savedAt = System.currentTimeMillis(),
                        contentSnapshot = contentSnapshot
                    )
                )

                /** 初始化 groupId=0 的优先级 */
                _groupPriorities.value = mapOf(0 to todo.priority)

                android.util.Log.w("TodoEditVM", "从列表加载: 初始化 groupSaveStates[0], savedTodoId=${todo.id}, priority=${todo.priority}")
            }
        }
    }

    /**
     * 保存待办
     *
     * @return 是否成功保存
     */
    fun saveTodo(): Boolean {
        if (_title.value.isBlank()) {
            return false
        }

        performSave()
        return true
    }

    private fun performSave() {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val hasSubTasks = _subTasks.value.isNotEmpty()

            /** 取消未完成的防抖任务，确保不泄漏协程 */
            _debounceJob?.cancel()

            /** 保存前对 contentFormat 进行校验和修复（防止损坏数据） */
            val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(_contentFormat.value)

            /**
             * 统一生成 content 字段（从权威数据源派生）
             *
             * 关键修复：确保 content 与 title + subTasks 严格一致
             *
             * 数据层次定义：
             * - Layer 1（权威）：title + subTasks → 用户实际输入的数据
             * - Layer 2（派生）：content ← 由 Layer 1 在保存时一次性生成
             *
             * 优势：
             * 1. 消除编辑过程中的中间态不一致
             * 2. content 始终是 title + subTasks 的准确快照
             * 3. 下次打开时，parseFromText(content) 与 fromSubTasks() 结果一致
             */
            val derivedContent = buildContentFromTitleAndSubTasks(
                title = _title.value,
                subTasks = _subTasks.value
            )

            /**
             * 构建最终的 contentFormat 字段值
             *
             * 格式："{Markdown内容}|||LINE_ATTACHMENTS|||[{JSON}]"
             *
             * - 前半部分：原始的富文本格式内容（用于 HomeScreen 显示）
             * - 后半部分：行级附件快照 JSON（用于编辑页恢复附件）
             * - 分隔符：|||LINE_ATTACHMENTS|||
             */
            val lineSnapshot = _lineAttachmentsSnapshot.value
            val finalContentFormat = if (!lineSnapshot.isNullOrBlank()) {
                // 有行级快照数据：直接使用（已包含分隔符和 JSON）
                lineSnapshot
            } else if (safeContentFormat.isNotBlank()) {
                // 无快照但有 Markdown 内容
                safeContentFormat
            } else {
                // 两者都为空
                ""
            }

            val todoId: Long = if (existingTodo != null) {
                val todo = existingTodo!!.copy(
                    title = _title.value,
                    /** 使用派生的 content，确保与 subTasks 一致 */
                    content = derivedContent.ifBlank { null },
                    categoryId = _groupCategoryIds.value[0] ?: 0L,
                    priority = _priority.value,
                    startDate = _startDate.value,
                    dueDate = _dueDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _groupReminders.value[0],
                    repeatType = _groupRepeatTypes.value[0] ?: 0,
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
                    backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                    contentFormat = finalContentFormat /** 持久化：行级快照或富文本内容*/
                )
                todoRepository.updateTodo(todo)
                existingTodo!!.id
            } else {
                val todo = TodoItem(
                    title = _title.value,
                    /** 使用派生的 content，确保与 subTasks 一致 */
                    content = derivedContent.ifBlank { null },
                    categoryId = _groupCategoryIds.value[0] ?: 0L,
                    priority = _priority.value,
                    status = 0,
                    startDate = _startDate.value,
                    dueDate = _dueDate.value,
                    estimatedDurationMinutes = _estimatedDurationMinutes.value,
                    reminderTime = _groupReminders.value[0],
                    repeatType = _groupRepeatTypes.value[0] ?: 0,
                    createdAt = currentTime,
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
                    backgroundColor = _backgroundColor.value, /** 持久化背景颜色 */
                    contentFormat = finalContentFormat /** 持久化：行级快照或富文本内容*/
                )
                todoRepository.insertTodo(todo)
            }

            saveSubTasks(todoId)

            /** 保存内容块到独立表（图片/语音等混合内容） */
            if (_currentContentBlocks.value.isNotEmpty()) {
                saveContentBlocks(todoId, _currentContentBlocks.value)
            }

            // 保存关联关系（新建时将临时关联绑定到新ID）
            if (existingTodo == null) {
                _relations.value.forEach { relation ->
                    cardRelationRepository.addRelation(relation.copy(sourceId = todoId))
                }
            }

            /** 保存成功后清除当前 Todo 的持久化 Undo 栈（V2.6: 按todoId隔离清除） */
            corgiPreferences.clearUndoRedoStacks(existingTodo?.id ?: -1L)
        }
    }

    /**
     * 为单个分组构建 TodoItem 对象（简化版，用于新建插入）
     *
     * @param targetGroupId 目标分组 ID（用于读取该组的优先级）
     */
    private fun buildTodoItemForGroup(
        targetGroupId: Int,
        title: String,
        content: String,
        hasSubTasks: Boolean,
        imagePaths: List<String>,
        voiceNotePath: String?,
        voiceDuration: Int?,
        lineSnapshotJson: String?,
        safeContentFormat: String
    ): TodoItem {
        val currentTime = System.currentTimeMillis()

        val finalContentFormat = if (!lineSnapshotJson.isNullOrBlank()) {
            lineSnapshotJson
        } else if (safeContentFormat.isNotBlank()) {
            safeContentFormat
        } else {
            ""
        }

        return TodoItem(
            title = title,
            content = content.ifBlank { null },
            categoryId = _groupCategoryIds.value[targetGroupId] ?: 0L,
            priority = _groupPriorities.value[targetGroupId] ?: 0,  // 使用分组独立优先级
            status = 0,
            startDate = _startDate.value,
            dueDate = _dueDate.value,
            estimatedDurationMinutes = _estimatedDurationMinutes.value,
            reminderTime = _groupReminders.value[targetGroupId],
            repeatType = _groupRepeatTypes.value[targetGroupId] ?: 0,
            createdAt = currentTime,
            updatedAt = currentTime,
            geofenceLat = _geofenceLat.value,
            geofenceLng = _geofenceLng.value,
            geofenceRadius = if (_geofenceEnabled.value) _geofenceRadius.value else null,
            geofenceType = _geofenceType.value,
            geofenceEnabled = _geofenceEnabled.value,
            geofenceAddress = if (_geofenceEnabled.value) _geofenceAddress.value else null,
            hasSubTasks = hasSubTasks,
            voiceNotePath = voiceNotePath,
            voiceDuration = voiceDuration,
            imagePaths = encodePaths(imagePaths),
            backgroundColor = _backgroundColor.value,
            contentFormat = finalContentFormat
        )
    }

    /**
     * 保存单个分组为独立的 TodoItem
     *
     * 从 todoLines 中提取指定 groupId 的所有行，
     * 构建为独立的 TodoItem 并插入数据库。
     * 更新该分组的保存状态。
     *
     * @param targetGroupId 要保存的分组 ID
     * @param allLines 当前的完整 todoLines 列表
     * @return 保存成功返回 true，失败返回 false
     */
    fun saveGroup(targetGroupId: Int, allLines: List<com.corgimemo.app.ui.model.TodoLine>): Boolean {
        // 1. 提取目标分组的所有行
        val groupLines = allLines.filter { it.groupId == targetGroupId }
        if (groupLines.isEmpty()) return false

        // 2. 获取首行文本作为标题（过滤空文本）
        val title = groupLines.firstOrNull { it.text.isNotBlank() }?.text?.trim()
            ?: return false  // 标题为空则不保存

        // 3. 构建子任务列表（仅子任务行）
        val subTaskLines = groupLines.filter { it.isSubTask && it.text.isNotBlank() }
        val subTasks = subTaskLines.map { line ->
            SubTask(
                id = if (line.subTaskId > 0L) line.subTaskId else 0L,
                todoId = 0L, // 新建的待办，暂时无 ID
                title = line.text.trim(),
                isCompleted = line.isChecked,
                order = line.order,
                // 附件按行分摊到对应 SubTask（每张图/每条语音独立计数）
                imagePaths = encodePaths(line.imagePaths),
                voicePaths = encodePaths(line.voiceAttachments.map { it.path })
            )
        }
        val hasSubTasks = subTasks.isNotEmpty()

        // 4. 收集附件（父行 → TodoItem；子任务行 → 对应 SubTask）
        //    注意：附件不再汇总到父，父只取自身首行的附件
        val firstLine = groupLines.firstOrNull { !it.isSubTask && it.text.isNotBlank() }
        val allImagePaths = firstLine?.imagePaths ?: emptyList()
        val firstVoice = firstLine?.voiceAttachments?.firstOrNull()
        val voicePath = firstVoice?.path
        val voiceDuration = firstVoice?.duration

        // 5. 派生 content 文本（直接使用已构建的 subTasks 列表）
        val derivedContent = buildContentFromTitleAndSubTasks(
            title = title,
            subTasks = subTasks  // subTasks 已经是 List<SubTask> 类型
        )

        // 6. 校验 contentFormat
        val safeContentFormat = com.corgimemo.app.util.MarkdownParser.validateAndSanitize(_contentFormat.value)

        // 7. 构建行级快照（仅目标分组的行）
        val snapshots = com.corgimemo.app.ui.model.LineSnapshotUtils.fromTodoLines(groupLines)
        val snapshotJson = com.corgimemo.app.ui.model.LineSnapshotUtils.serialize(
            snapshots = snapshots,
            originalContent = derivedContent
        )

        // 8. 执行异步保存
        viewModelScope.launch {
            try {
                val todoItem = buildTodoItemForGroup(
                    targetGroupId = targetGroupId,
                    title = title,
                    content = derivedContent,
                    hasSubTasks = hasSubTasks,
                    imagePaths = allImagePaths,
                    voiceNotePath = voicePath,
                    voiceDuration = voiceDuration,
                    lineSnapshotJson = snapshotJson.ifBlank { null },
                    safeContentFormat = safeContentFormat
                )

                // 8.1 根据是否已有 savedTodoId 决定插入或更新
                // 【关键逻辑】每个编辑容器只对应一个待办卡片：
                // - 首次保存 → insertTodo() 创建新记录
                // - 再次保存 → updateTodo() 更新已有记录（避免重复创建）
                val existingSavedId = _groupSaveStates.value[targetGroupId]?.savedTodoId

                val newTodoId: Long = if (existingSavedId != null && existingSavedId > 0) {
                    // ✅ 已有记录：执行 UPDATE（更新内容，ID保持不变）
                    val existingTodo = todoRepository.getTodoById(existingSavedId)
                    if (existingTodo != null) {
                        val updatedTodo = existingTodo.copy(
                            title = todoItem.title,
                            content = todoItem.content,
                            categoryId = todoItem.categoryId,
                            priority = todoItem.priority,
                            startDate = todoItem.startDate,
                            dueDate = todoItem.dueDate,
                            estimatedDurationMinutes = todoItem.estimatedDurationMinutes,
                            reminderTime = todoItem.reminderTime,
                            repeatType = todoItem.repeatType,
                            updatedAt = System.currentTimeMillis(),
                            hasSubTasks = todoItem.hasSubTasks,
                            voiceNotePath = todoItem.voiceNotePath,
                            voiceDuration = todoItem.voiceDuration,
                            imagePaths = todoItem.imagePaths,
                            backgroundColor = todoItem.backgroundColor,
                            contentFormat = todoItem.contentFormat
                        )
                        todoRepository.updateTodo(updatedTodo)
                        existingSavedId  // ID 不变！
                    } else {
                        // 记录被外部删除，回退到 INSERT
                        android.util.Log.w("TodoEditVM", "分组 $targetGroupId 的已保存记录($existingSavedId)不存在，重新创建")
                        todoRepository.insertTodo(todoItem)
                    }
                } else {
                    // ✅ 首次保存：执行 INSERT
                    todoRepository.insertTodo(todoItem)
                }

                // 保存子任务（如果有）
                if (subTasks.isNotEmpty()) {
                    // UPDATE 模式：先删除旧子任务再添加新的，避免重复
                    if (existingSavedId != null && existingSavedId > 0) {
                        SubTaskManager.deleteAllSubTasks(context, newTodoId)
                    }
                    // 使用含完整数据（含附件）的 addSubTasks 重载，保留 imagePaths/voicePaths
                    SubTaskManager.addSubTasks(context, newTodoId, subTasks)
                }

                // 保存内容块（如果有全局附件）
                if (_currentContentBlocks.value.isNotEmpty()) {
                    saveContentBlocks(newTodoId, _currentContentBlocks.value)
                }

                // 设置提醒（如果有）
                if (todoItem.reminderTime != null) {
                    com.corgimemo.app.notification.AlarmScheduler.scheduleReminder(context, todoItem.copy(id = newTodoId))
                }

                // 9. 更新保存状态（包含内容快照，用于后续变化检测）
                val contentSnapshot = groupLines.joinToString("\n") { it.text }
                val newState = GroupSaveState(
                    groupId = targetGroupId,
                    isSaved = true,
                    savedTodoId = newTodoId,
                    savedAt = System.currentTimeMillis(),
                    contentSnapshot = contentSnapshot  // 记录保存时的内容
                )
                _groupSaveStates.value = _groupSaveStates.value + (targetGroupId to newState)

                android.util.Log.w("TodoEditVM", "分组 $targetGroupId 保存成功, todoId=$newTodoId")

                // ✅ 保存真正完成后才 emit 事件（确保首页刷新时 DB 已写入）
                // 注意：必须放在 viewModelScope.launch 内、try 末尾，不能放在 saveAllGroups 循环里
                TodoEventBus.emit(TodoEvent.TodoSaved)
            } catch (e: Exception) {
                android.util.Log.e("TodoEditVM", "分组 $targetGroupId 保存失败", e)
            }
        }

        return true
    }

    /**
     * 获取当前 todo 中所有"已保存"的子 todo 列表
     *
     * 用于分享功能：从 [groupSaveStates] 中筛选 isSaved==true 且 savedTodoId 不为空的子分组，
     * 通过 [TodoRepository] 查询对应的子 todo。
     *
     * 【重要】**排除 groupId=0**（主 todo 卡片），只返回 groupId >= 1 的子分组。
     * 否则在 onShareClick 中 `listOf(mainTodo) + savedSubTodos` 会把主 todo 算两次，
     * 出现"只有 1 条 todo 却显示 2 条待办"的 bug。
     *
     * @return 已保存的子 todo 列表（按 groupId 升序，不含主 todo）
     */
    suspend fun getSavedSubTodos(): List<TodoItem> {
        /** 1. 筛选已保存且有真实 todoId 的子分组（按 groupId 升序），【关键】排除 groupId=0 */
        val savedGroupIds = _groupSaveStates.value
            .filterValues { it.isSaved && it.savedTodoId != null }
            .filterKeys { it != 0 }  // 排除主 todo 卡片
            .keys
            .sorted()

        /** 2. 逐个查询真实 todo 对象，过滤掉已被外部删除的孤儿记录 */
        val result = mutableListOf<TodoItem>()
        for (groupId in savedGroupIds) {
            val savedTodoId = _groupSaveStates.value[groupId]?.savedTodoId ?: continue
            val todo = todoRepository.getTodoById(savedTodoId)
            if (todo != null) {
                result.add(todo)
            }
        }
        return result
    }

    /**
     * 获取"主 todo"用于分享
     *
     * 【多卡片架构】groupId=0 的卡片即"主 todo"卡片（标题卡片）。
     * 优先从 ViewModel 内存 [existingTodo] 取（编辑模式），
     * fallback 到 [groupSaveStates] 中 groupId=0 对应的已保存记录（新建模式但已保存）。
     *
     * 修复背景：原 [onShareClick] 直接读 [currentTodo]，但新建模式下
     * [existingTodo] 始终为 null（loadTodo 不被调用），导致 "全部完成" 后
     * 即便数据库中已有 todo 仍被误判"主 todo 未保存"。
     *
     * @return 主 todo（TodoItem），若 groupId=0 尚未保存则返回 null
     */
    suspend fun getMainTodoForShare(): TodoItem? {
        if (existingTodo != null) return existingTodo
        val mainGroupSavedId = _groupSaveStates.value[0]?.savedTodoId
        return mainGroupSavedId?.let { todoRepository.getTodoById(it) }
    }

    /**
     * 保存所有未保存的分组
     *
     * 遍历所有 groupId，对未保存的分组逐一调用 saveGroup()。
     * 用于右上角"全部完成"按钮的点击事件。
     *
     * @param allLines 当前的完整 todoLines 列表
     * @return 成功保存的分组数量
     */
    fun saveAllGroups(allLines: List<com.corgimemo.app.ui.model.TodoLine>): Int {
        val allGroupIds = allLines.map { it.groupId }.distinct()
        var savedCount = 0

        for (groupId in allGroupIds) {
            val result = saveGroup(groupId, allLines)
            if (result) savedCount++
        }

        android.util.Log.w("TodoEditVM", "saveAllGroups 完成, 共保存 $savedCount 个分组")

        // 不再在这里 emit TodoSaved
        // 事件已移到 saveGroup() 的 viewModelScope.launch 内、try 末尾
        // 确保每个分组 DB 写入完成后再 emit，避免首页读到旧数据

        return savedCount
    }

    /**
     * 设置指定分组的优先级
     *
     * @param groupId 分组 ID
     * @param priority 优先级 (0=无, 1=低, 2=中, 3=高)
     */
    fun setGroupPriority(groupId: Int, priority: Int) {
        _groupPriorities.value = _groupPriorities.value + (groupId to priority)
    }

    /**
     * 获取指定分组的优先级
     *
     * @param groupId 分组 ID
     * @return 优先级值，默认为 0（无优先级）
     */
    fun getGroupPriority(groupId: Int): Int {
        return _groupPriorities.value[groupId] ?: 0
    }

    /**
     * 检查并重置指定分组的保存状态（仅当内容确实发生变化时）
     *
     * 比较当前内容与保存时的快照：
     * - 内容相同 → 不重置（如只是添加了新容器/新行）
     * - 内容不同 → 重置为未保存（用户编辑了该容器的内容）
     *
     * @param groupId 要检查的分组 ID
     * @param currentContent 当前该组的文本内容
     */
    fun checkAndResetGroupSavedState(groupId: Int, currentContent: String) {
        val current = _groupSaveStates.value[groupId]
        if (current != null && current.isSaved) {
            // 只有当内容真正发生变化时才重置
            if (current.contentSnapshot != currentContent) {
                val newState = current.copy(isSaved = false)
                _groupSaveStates.value = _groupSaveStates.value + (groupId to newState)
                android.util.Log.w("TodoEditVM", "分组 $groupId 内容已变化，重置为未保存状态")
            }
            // 内容未变化则保持 isSaved=true
        }
    }

    /**
     * 重置所有分组的保存状态为"未保存"
     */
    fun resetAllGroupSavedStates() {
        val currentStates = _groupSaveStates.value
        if (currentStates.any { it.value.isSaved }) {
            val resetStates = currentStates.mapValues { it.value.copy(isSaved = false) }
            _groupSaveStates.value = resetStates
            android.util.Log.w("TodoEditVM", "重置所有分组为未保存状态")
        }
    }

    /**
     * 确认关键词选择并保存（待办编辑页不再需要此功能，仅在灵感编辑页保留）
     *
     * 注：以下 4 个方法（confirmKeywordSelection / skipKeywordSelection /
     *     cancelKeywordSelection / dismissKeywordSelection）已整体移除。
     *     关键词推荐分类功能仅在灵感编辑页（InspirationEditViewModel）保留。
     */
    // 占位：原方法体已删除

    /**
     * 保存子任务（编辑模式下先删除旧子任务再添加新的）
     * 并同步更新待办的 hasSubTasks 字段
     *
     * @param todoId 待办 ID
     */
    private suspend fun saveSubTasks(todoId: Long) {
        val currentSubTasks = _subTasks.value

        if (existingTodo != null) {
            SubTaskManager.deleteAllSubTasks(context, todoId)
        }

        if (currentSubTasks.isNotEmpty()) {
            SubTaskManager.addSubTasks(context, todoId, currentSubTasks)
        }
    }

    /**
     * 删除子任务并同步到数据库（编辑已有待办时）
     *
     * @param subTask 要删除的子任务
     */
    fun deleteSubTask(subTask: SubTask) {
        removeSubTask(subTask)

        if (existingTodo != null && subTask.id > 0) {
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
                android.util.Log.w("TodoEditVM", "开始加载分类...")
                categoryRepository.initDefaultCategories()

                /** V2.7: 记录最后编辑的 Todo ID（用于设置页入口传递） */
                existingTodo?.id?.let { todoId ->
                    corgiPreferences.saveLastEditedTodoId(todoId)
                }

                val allCategories = categoryRepository.getAllCategoriesList()
                android.util.Log.w("TodoEditVM", "加载到 ${allCategories.size} 个分类: $allCategories")
                _categories.value = allCategories

                /**
                 * 新建模式下不自动设置默认分类
                 *
                 * 历史实现：根据用户身份（打工人/学生）自动选中"工作/学习"作为默认分类。
                 * 问题：用户进入新建待办页时还未编辑，分类按钮就显示了具体分类名（如"学习"），
                 *       与"用户主动选择"的语义不符，干扰用户预期。
                 *
                 * 当前策略：保持 _groupCategoryIds 为空 map（groupId=0 → 0L），分类按钮显示"分类"占位符，
                 *           由用户主动点击按钮选择分类。关键词推荐分类功能仅在灵感编辑页保留。
                 */
            } catch (e: Exception) {
                android.util.Log.e("TodoEditVM", "加载分类失败", e)
                e.printStackTrace()
            } finally {
                _isCategoriesLoaded.value = true
                android.util.Log.w("TodoEditVM", "分类加载完成, isCategoriesLoaded=true, categories数量=${_categories.value.size}")
            }
        }
    }

    /**
     * 创建自定义分类
     *
     * 用于"分类"选择弹窗的"自定义"按钮：用户输入名称后异步写入数据库。
     * 写入成功后通过 [onCreated] 回调返回新分类的 ID，
     * 调用方应立即调用 [setGroupCategory] 切换对应分组到新分类。
     *
     * @param name 自定义分类名称（已 trim 且非空）
     * @param onCreated 创建成功回调，参数为新分类的数据库 ID
     */
    fun createCustomCategory(name: String, onCreated: (Long) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newCategory = Category(
                    name = name.trim(),
                    type = CategoryType.CUSTOM,
                    isDefault = false
                )
                val newId = categoryRepository.insertCategory(newCategory)
                // 刷新内存中的分类列表
                _categories.value = categoryRepository.getAllCategoriesList()
                onCreated(newId)
                android.util.Log.w("TodoEditVM", "创建自定义分类成功: name='$name', id=$newId")
            } catch (e: Exception) {
                android.util.Log.e("TodoEditVM", "创建自定义分类失败", e)
            }
        }
    }

    /**
     * 删除自定义分类
     *
     * 用户在「分类选择弹窗」长按某个自定义分类 Tag 时调用。
     * 行为与侧滑页（HomeViewModel.deleteCategory）保持一致：
     * - 数据库层：调用 [CategoryRepository.deleteCustomCategory] 删除行（带 isDefault=0 过滤）
     * - 内存层：刷新 [_categories] 列表，让弹窗立即移除已删除项
     * - 引用层：调用方需自行处理引用该 categoryId 的 todo（[clearGroupCategory] 或 [setGroupCategory]）
     *
     * 注意：被删除分类下的 todo 不会自动迁移到「未分类」，
     * 这些 todo 的 categoryId 字段会保持原值但在弹窗中不再可见，
     * 与 HomeViewModel 侧滑页的删除行为一致（项目既定行为，避免破坏性变更）。
     *
     * @param category 要删除的自定义分类（必须 isDefault=false，否则 dao 层不会执行）
     */
    fun deleteCustomCategory(category: Category) {
        if (category.isDefault) {
            android.util.Log.w("TodoEditVM", "尝试删除默认分类，已拒绝: id=${category.id}")
            return
        }
        viewModelScope.launch {
            try {
                categoryRepository.deleteCustomCategory(category.id)
                // 刷新内存中的分类列表（弹窗基于此列表渲染）
                _categories.value = categoryRepository.getAllCategoriesList()
                android.util.Log.w("TodoEditVM", "删除自定义分类成功: id=${category.id}, name='${category.name}'")
            } catch (e: Exception) {
                android.util.Log.e("TodoEditVM", "删除自定义分类失败", e)
            }
        }
    }

    /**
     * 注：原 triggerRecommendation() / acceptRecommendation() 方法已整体移除。
     * 分类推荐（基于关键词匹配）功能仅在灵感编辑页（InspirationEditViewModel）保留。
     * 待办编辑页不再做自动推荐，由用户主动选择分类。
     */

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
     * 设置完整的图片路径列表（用于 UI 层同步行级附件）
     *
     * 【关键方法】解决行级图片无法保存的问题
     *
     * 使用场景：
     * - LaunchedEffect(todoLines) 同步时，从 todoLines 收集所有图片路径后调用
     * - 确保 viewModel._imagePaths 与 UI 层的 todoLines[].imagePaths 保持一致
     *
     * @param paths 完整的图片路径列表
     */
    fun setImagePaths(paths: List<String>) {
        _imagePaths.value = paths
    }

    /**
     * 设置语音备注路径（用于 UI 层同步行级录音附件）
     *
     * 【关键方法】解决行级录音无法保存的问题
     *
     * @param path 录音文件路径
     */
    fun setVoiceNotePath(path: String) {
        _voiceNotePath.value = path
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
     * 从数据库加载某待办的所有内容块
     *
     * 在编辑页初始化时调用，将持久化的内容块恢复到内存列表。
     *
     * @param todoId 待办事项 ID
     * @return ContentBlock 列表（按 orderIndex 排序）
     */
    suspend fun loadContentBlocks(todoId: Long): List<ContentBlock> {
        val entities = contentBlockDao.getBlocksByTodoId(todoId)
        return entities.map { entity ->
            when (entity.type) {
                "image" -> ContentBlock.Image(entity.filePath)
                "voice" -> ContentBlock.Voice(entity.filePath, entity.duration)
                else -> ContentBlock.Text("") // 兜底
            }
        }
    }

    /**
     * 从权威数据源（title + subTasks）派生 content 字段
     *
     * 此方法确保 content 字段始终与 title 和 subTasks 严格一致，
     * 消除编辑过程中可能产生的中间态不一致问题。
     *
     * 数据格式规范：
     * - 第一行：父任务（☐ 标题）
     * - 后续行：子任务（  ☐ 标题，带2空格缩进）
     * - 行间用换行符 \n 分隔
     *
     * @param title 父任务标题（来自 _title StateFlow）
     * @param subTasks 子任务列表（来自 _subTasks StateFlow）
     * @return 格式化的纯文本字符串，用于存储到 content 字段
     *
     * 示例：
     * 输入: title="测试1", subTasks=[SubTask("测试2"), SubTask("测试3")]
     * 输出: "☐ 测试1\n  ☐ 测试2\n  ☐ 测试3"
     */
    private fun buildContentFromTitleAndSubTasks(
        title: String,
        subTasks: List<com.corgimemo.app.data.model.SubTask>
    ): String {
        /** 构建父任务行 */
        val lines = mutableListOf("☐ $title")

        /** 追加所有子任务行（带缩进） */
        subTasks.forEach { subTask ->
            lines.add("  ☐ ${subTask.title}")
        }

        val result = lines.joinToString("\n")

        /** 诊断日志：追踪 content 派生过程 */
        android.util.Log.w(
            "TodoEditSave",
            "派生 content: title='$title', subTasks=${subTasks.map { it.title }}, result='$result', " +
            "_imagePaths=${_imagePaths.value}, _voiceNotePath=${_voiceNotePath.value}, " +
            "_currentContentBlocks=${_currentContentBlocks.value.size}个"
        )

        return result
    }

    /**
     * 保存内容块列表到数据库（原子操作：先删后写）
     *
     * 在 performSave() 时调用，确保数据一致性。
     *
     * @param todoId 待办事项 ID
     * @param blocks 当前内存中的 ContentBlock 列表
     */
    suspend fun saveContentBlocks(todoId: Long, blocks: List<ContentBlock>) {
        val entities = blocks.mapIndexed { index, block ->
            when (block) {
                is ContentBlock.Image -> ContentBlockEntity(
                    todoId = todoId, type = "image", filePath = block.path, orderIndex = index
                )
                is ContentBlock.Voice -> ContentBlockEntity(
                    todoId = todoId, type = "voice", filePath = block.path,
                    duration = block.duration, orderIndex = index
                )
                is ContentBlock.Text -> null // 文本块不持久化到独立表
            }
        }.filterNotNull()

        contentBlockDao.replaceBlocksForTodo(todoId, entities)
    }

    /**
     * 删除待办的所有内容块（从数据库和物理存储）
     *
     * 在删除待办时调用，清理关联的文件资源。
     *
     * @param todoId 待办事项 ID
     */
    suspend fun deleteAllContentBlocks(todoId: Long) {
        val entities = contentBlockDao.getBlocksByTodoId(todoId)
        contentBlockDao.deleteByTodoId(todoId)

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
                    android.util.Log.w("TodoEditViewModel",
                        "扩展撤销栈已裁剪：移除 ${removed::class.simpleName}，" +
                        "当前大小 ${newSize / 1024}KB / ${(EXTENDED_STACK_MAX_SIZE_BYTES / 1024)}KB")
                    break
                }
            }
        } catch (e: Exception) {
            /** 序列化失败时仅依赖数量限制（MAX_UNDO_DEPTH），不强制裁剪 */
            android.util.Log.w("TodoEditViewModel", "扩展撤销栈大小检查失败", e)
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
     * 更新待办项的背景颜色状态，
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
     * 更新待办项的 contentFormat 状态，
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
        val currentTodoId = existingTodo?.id ?: return /** 新建待办无 ID，跳过 */

        viewModelScope.launch {
            try {
                /**
                 * V2.6: 使用按 TodoId 隔离的增量持久化
                 * 每个 Todo 独立存储编辑历史，避免多 Todo 并发时数据混淆
                 */
                if (_undoStack.isNotEmpty()) {
                    /** 序列化 undoStack 栈顶的最新一条记录（纯文本操作） */
                    val latestUndoJson = com.corgimemo.app.util.AnnotatedStringSerializer
                        .serialize(_undoStack.last())
                    corgiPreferences.appendUndoLogEntry(currentTodoId, latestUndoJson)
                }

                if (_redoStack.isNotEmpty()) {
                    /** 序列化 redoStack 栈顶的最新一条记录（纯文本操作） */
                    val latestRedoJson = com.corgimemo.app.util.AnnotatedStringSerializer
                        .serialize(_redoStack.last())
                    corgiPreferences.appendRedoLogEntry(currentTodoId, latestRedoJson)
                }

                /**
                 * 扩展栈持久化（内容块操作）
                 * 使用独立的 key 前缀区分于纯文本栈
                 * 序列化格式：JSON 数组，每项为 EditOperation 的 JSON 表示
                 */
                if (_undoStackExtended.isNotEmpty()) {
                    val extendedUndoJson = serializeEditOperations(_undoStackExtended.toList())
                    corgiPreferences.appendExtendedUndoLog(currentTodoId, extendedUndoJson)
                }

                if (_redoStackExtended.isNotEmpty()) {
                    val extendedRedoJson = serializeEditOperations(_redoStackExtended.toList())
                    corgiPreferences.appendExtendedRedoLog(currentTodoId, extendedRedoJson)
                }
            } catch (e: Exception) {
                android.util.Log.w("TodoEditViewModel", "Undo 栈增量持久化失败", e)
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
            android.util.Log.w("TodoEditViewModel", "扩展撤销栈反序列化失败", e)
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
     * 从 DataStore 增量日志恢复 Undo/Redo 栈（V2.6 按TodoId隔离）
     *
     * 在加载已有待办时调用，读取属于该 TodoId 的已保存撤销日志。
     *
     * **V2.6 优化**：使用 `{todoId}_undo_log` / `{todoId}_redo_log` key，
     * 每个 Todo 独立存储编辑历史，不再有全局共享冲突问题。
     *
     * **向后兼容**：首次使用新格式时自动检测并迁移旧的全局格式数据。
     *
     * @param todoId 当前编辑的待办 ID
     */
    private suspend fun restoreUndoStacks(todoId: Long) {
        try {
            /** V2.6: 向后兼容迁移 — 检测旧全局格式数据 */
            corgiPreferences.migrateLegacyUndoLogIfPresent(todoId)

            /** 从按 TodoId 隔离的增量日志读取并反序列化（纯文本操作） */
            val undoLogJson = corgiPreferences.getUndoLog(todoId)
            val redoLogJson = corgiPreferences.getRedoLog(todoId)

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
            val extendedUndoJson = corgiPreferences.getExtendedUndoLog(todoId)
            if (!extendedUndoJson.isNullOrBlank() && extendedUndoJson != "[]") {
                val restoredExtendedUndo = deserializeEditOperations(extendedUndoJson)
                _undoStackExtended.clear()
                _undoStackExtended.addAll(restoredExtendedUndo)
                if (_undoStackExtended.isNotEmpty()) _canUndo.value = true
            }

            val extendedRedoJson = corgiPreferences.getExtendedRedoLog(todoId)
            if (!extendedRedoJson.isNullOrBlank() && extendedRedoJson != "[]") {
                val restoredExtendedRedo = deserializeEditOperations(extendedRedoJson)
                _redoStackExtended.clear()
                _redoStackExtended.addAll(restoredExtendedRedo)
                if (_redoStackExtended.isNotEmpty()) _canRedo.value = true
            }

            android.util.Log.w("TodoEditViewModel",
                "从增量日志恢复 Undo 栈: ${_undoStack.size} 条, Redo 栈: ${_redoStack.size} 条, " +
                "扩展 Undo: ${_undoStackExtended.size} 条, 扩展 Redo: ${_redoStackExtended.size} 条 (todoId=$todoId)")
        } catch (e: Exception) {
            android.util.Log.w("TodoEditViewModel", "Undo 栈增量日志恢复失败，使用空栈", e)
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
     * 添加关联关系
     * @param targetType 目标类型
     * @param targetId 目标ID
     */
    fun addRelation(targetType: String, targetId: Long) {
        viewModelScope.launch {
            val todoId = existingTodo?.id ?: 0L
            val relation = CardRelation(
                sourceType = "todo",
                sourceId = todoId,
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
