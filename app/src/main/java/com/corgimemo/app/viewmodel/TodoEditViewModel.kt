package com.corgimemo.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.event.TodoEvent
import com.corgimemo.app.data.event.TodoEventBus
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CardDetail
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map /** v2026-07-22 新增：hasAnyUnsavedChanges 派生需要 .map 扩展函数 */
import kotlinx.coroutines.flow.stateIn /** v2026-07-22 新增：hasAnyUnsavedChanges 派生需要 .stateIn 共享 StateFlow */
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
 * @property editStateHash 保存时的完整编辑状态指纹（v2026-07-21 新增）
 *
 * editStateHash 覆盖以下元素的变化检测：
 * - 文本内容（todoLines 的 text 拼接）
 * - 附件（每行的 imagePaths / voiceAttachments）
 * - 提醒时间（reminderTime）
 * - 分类（categoryId）
 * - 优先级（priority）
 * - 关联（该分组的关联数量）
 *
 * 撤销/恢复操作会改变 todoLines 的 text 或附件，自动反映在 editStateHash 中。
 * 分组本身的增删会触发 groupSaveStates 整体重置。
 */
data class GroupSaveState(
    val groupId: Int,
    val isSaved: Boolean = false,
    val savedTodoId: Long? = null,
    val savedAt: Long? = null,
    /** 保存时该组所有行的文本拼接（用于比较是否发生变化） */
    val contentSnapshot: String = "",
    /** 保存时的完整编辑状态指纹（覆盖文字/附件/提醒/分类/优先级/关联） */
    val editStateHash: String = ""
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

    /**
     * 优先级状态（0=无, 1=低, 2=中, 3=高）
     *
     * v2026-07-21 修复：默认值从 1（低优先级）改为 0（无优先级）。
     * 新建待办时按钮应该显示「无优先级」而不是「低优先级」，
     * 避免用户每次新建都误以为已经设置了优先级。
     *
     * v2026-07-21 二次修复：保存路径（[saveActiveTodo] 内的 [TodoItem.priority]）
     * 已统一为读取 [_groupPriorities]，[_priority] 仅作为旧 setPriority() 入口的
     * 兼容状态保留。CheckboxEditText 实际只读 [_groupPriorities]，
     * 故 [_priority] 已不影响任何 UI 显示与最终落库。
     */
    private val _priority = MutableStateFlow(0)
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
     * 编辑状态全量快照，用于撤销/恢复功能
     * 记录编辑页某一时刻的所有可编辑字段状态（排除背景色）
     */
    data class EditSnapshot(
        // 文本内容
        val title: String,
        val content: String,
        val contentFormat: String,
        val subTasks: List<SubTask>,
        val contentBlocks: List<ContentBlock>,

        // 单值元数据
        val startDate: Long?,
        val dueDate: Long?,

        // 位置
        val geofenceLat: Double?,
        val geofenceLng: Double?,
        val geofenceRadius: Float?,
        val geofenceType: Int,
        val geofenceEnabled: Boolean,
        val geofenceAddress: String?,

        // 附件
        val imagePaths: List<String>,
        val voiceNotePath: String?,
        val voiceDuration: Int?,
        val lineAttachmentsSnapshot: String?,

        // 多分组字段
        val groupCategoryIds: Map<Int, Long>,
        val groupPriorities: Map<Int, Int>,
        val groupReminders: Map<Int, Long?>,
        val groupRepeatTypes: Map<Int, Int>,
        val groupRelations: Map<Int, List<CardRelation>>,
    )

    /**
     * 撤销/恢复栈管理器
     * 维护两个 ArrayDeque，深度限制 MAX_SNAPSHOT_DEPTH = 50
     */
    class UndoRedoStack(
        private val maxDepth: Int = MAX_SNAPSHOT_DEPTH
    ) {
        private val undoStack = ArrayDeque<EditSnapshot>(maxDepth)
        private val redoStack = ArrayDeque<EditSnapshot>(maxDepth)

        val canUndo: Boolean get() = undoStack.isNotEmpty()
        val canRedo: Boolean get() = redoStack.isNotEmpty()

        /** 推入快照到撤销栈，清空恢复栈 */
        fun push(snapshot: EditSnapshot) {
            if (undoStack.size >= maxDepth) {
                undoStack.removeFirst()
            }
            undoStack.addLast(snapshot)
            redoStack.clear()
        }

        /** 撤销：当前状态入恢复栈，弹出撤销栈顶 */
        fun undo(current: EditSnapshot): EditSnapshot? {
            if (undoStack.isEmpty()) return null
            redoStack.addLast(current)
            return undoStack.removeLast()
        }

        /** 恢复：当前状态入撤销栈，弹出恢复栈顶 */
        fun redo(current: EditSnapshot): EditSnapshot? {
            if (redoStack.isEmpty()) return null
            undoStack.addLast(current)
            return redoStack.removeLast()
        }

        /** 清空所有栈 */
        fun clear() {
            undoStack.clear()
            redoStack.clear()
        }

        companion object {
            const val MAX_SNAPSHOT_DEPTH = 50
        }
    }

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
     * 当前编辑页是否有任何未保存的分组（v2026-07-22 新增）
     *
     * 用途：编辑页 UI 拦截"返回"操作（顶部 ← 按钮 / 系统返回键）时判断是否需要弹"放弃编辑"确认框，
     * 避免用户误触返回导致未保存草稿被静默丢失。
     *
     * 实现：从 [_groupSaveStates] 派生（map 内任意分组的 isSaved == false 即视为有未保存）。
     * - 编辑模式（已 loadTodo）：所有分组初始 isSaved=true，用户修改后会重置为 false
     * - 新建模式（loadTodo 未调用）：所有分组初始 isSaved=false → 此值直接为 true（视为有未保存）
     * - 保存后：所有分组 isSaved=true → 此值为 false
     *
     * 复用现有 groupSaveStates 机制（不需要新加 _isDirty StateFlow），保持单一数据源。
     */
    val hasAnyUnsavedChanges: StateFlow<Boolean> = _groupSaveStates
        .map { states -> states.values.any { !it.isSaved } }
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = true  // 默认视为有未保存（保守策略，避免新建模式漏拦）
        )

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
            _contentFormat.value = format
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

    /** 统一撤销/恢复栈管理器 */
    private val undoRedoStack = UndoRedoStack()

    /** 防循环标志：恢复快照时为 true，阻止 LaunchedEffect 推入新快照 */
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    /** 恢复事件：通知 UI 层重建 todoLines */
    private val _restoreEvent = MutableStateFlow<EditSnapshot?>(null)
    val restoreEvent: StateFlow<EditSnapshot?> = _restoreEvent.asStateFlow()

    /** 从当前 StateFlow 值构建全量快照 */
    private val currentSnapshot: EditSnapshot
        get() = EditSnapshot(
            title = _title.value,
            content = _content.value,
            contentFormat = _contentFormat.value,
            subTasks = _subTasks.value,
            contentBlocks = _currentContentBlocks.value,
            startDate = _startDate.value,
            dueDate = _dueDate.value,
            geofenceLat = _geofenceLat.value,
            geofenceLng = _geofenceLng.value,
            geofenceRadius = _geofenceRadius.value,
            geofenceType = _geofenceType.value,
            geofenceEnabled = _geofenceEnabled.value,
            geofenceAddress = _geofenceAddress.value,
            imagePaths = _imagePaths.value,
            voiceNotePath = _voiceNotePath.value,
            voiceDuration = _voiceDuration.value,
            lineAttachmentsSnapshot = _lineAttachmentsSnapshot.value,
            groupCategoryIds = _groupCategoryIds.value,
            groupPriorities = _groupPriorities.value,
            groupReminders = _groupReminders.value,
            groupRepeatTypes = _groupRepeatTypes.value,
            groupRelations = _groupRelations.value,
        )

    /** 是否可以撤销（Undo 栈非空时为 true） */
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** 是否可以重做（Redo 栈非空时为 true） */
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var snapshotDebounceJob: Job? = null
    private var isDebouncing = false

    /** 带防抖的快照推入（用于文本编辑） */
    fun pushSnapshotDebounced() {
        if (isDebouncing) return  // 冷却期内，不推入
        if (_isRestoring.value) return  // 恢复中，不推入
        isDebouncing = true
        pushSnapshot()  // 立即推入当前快照
        snapshotDebounceJob?.cancel()
        snapshotDebounceJob = viewModelScope.launch {
            delay(500)
            isDebouncing = false  // 冷却期结束
        }
    }

    /** 即时推入当前快照（用于元数据变更） */
    fun pushSnapshot() {
        if (_isRestoring.value) return  // 恢复中，不推入
        undoRedoStack.push(currentSnapshot)
        _canUndo.value = undoRedoStack.canUndo
        _canRedo.value = undoRedoStack.canRedo
    }

    /** 从快照恢复编辑状态，将所有字段值写回 StateFlow */
    private fun restoreFromSnapshot(snapshot: EditSnapshot) {
        _title.value = snapshot.title
        _content.value = snapshot.content
        _contentFormat.value = snapshot.contentFormat
        _subTasks.value = snapshot.subTasks
        _currentContentBlocks.value = snapshot.contentBlocks
        _startDate.value = snapshot.startDate
        _dueDate.value = snapshot.dueDate
        _geofenceLat.value = snapshot.geofenceLat
        _geofenceLng.value = snapshot.geofenceLng
        _geofenceRadius.value = snapshot.geofenceRadius
        _geofenceType.value = snapshot.geofenceType
        _geofenceEnabled.value = snapshot.geofenceEnabled
        _geofenceAddress.value = snapshot.geofenceAddress
        _imagePaths.value = snapshot.imagePaths
        _voiceNotePath.value = snapshot.voiceNotePath
        _voiceDuration.value = snapshot.voiceDuration
        _lineAttachmentsSnapshot.value = snapshot.lineAttachmentsSnapshot
        _groupCategoryIds.value = snapshot.groupCategoryIds
        _groupPriorities.value = snapshot.groupPriorities
        _groupReminders.value = snapshot.groupReminders
        _groupRepeatTypes.value = snapshot.groupRepeatTypes
        _groupRelations.value = snapshot.groupRelations

        // 通知 UI 层重建 todoLines
        _restoreEvent.value = snapshot
    }

    /** 撤销上一次操作 */
    fun undo() {
        val current = currentSnapshot
        val previous = undoRedoStack.undo(current) ?: return
        _isRestoring.value = true
        restoreFromSnapshot(previous)
        _isRestoring.value = false
        _canUndo.value = undoRedoStack.canUndo
        _canRedo.value = undoRedoStack.canRedo
    }

    /** 恢复上一次撤销的操作 */
    fun redo() {
        val current = currentSnapshot
        val next = undoRedoStack.redo(current) ?: return
        _isRestoring.value = true
        restoreFromSnapshot(next)
        _isRestoring.value = false
        _canUndo.value = undoRedoStack.canUndo
        _canRedo.value = undoRedoStack.canRedo
    }

    /** 各分组的关联映射（key=groupId, value=该分组的关联列表） */
    private val _groupRelations = MutableStateFlow<Map<Int, List<CardRelation>>>(emptyMap())
    val groupRelations: StateFlow<Map<Int, List<CardRelation>>> = _groupRelations.asStateFlow()

    /** 关联ID → 标题的缓存映射（用于 LinkedCardsRow 显示 Chip 标题） */
    private val _relationTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val relationTitles: StateFlow<Map<Long, String>> = _relationTitles.asStateFlow()

    /**
     * 当前预览的卡片详情（用于 LinkedCardPreviewDialog 按类型差异化展示）
     *
     * 用户点击 Chip 时由 [loadCardDetail] 异步加载，Dialog 关闭时由 [clearCardDetail] 清空。
     * 加载期间 [_cardDetailLoading] 为 true，UI 显示 CircularProgressIndicator。
     */
    private val _cardDetail = MutableStateFlow<CardDetail?>(null)
    val cardDetail: StateFlow<CardDetail?> = _cardDetail.asStateFlow()

    /** 卡片详情加载中标志（用于 Dialog 显示进度指示器） */
    private val _cardDetailLoading = MutableStateFlow(false)
    val cardDetailLoading: StateFlow<Boolean> = _cardDetailLoading.asStateFlow()

    /** 关联操作错误事件（用于 Snackbar 提示） */
    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent: SharedFlow<String> = _errorEvent.asSharedFlow()

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

    /**
     * 关联数据加载完成标志（v2026-07-21 新增）
     *
     * 用于解决 editStateHash 初始化时序问题：
     * - false：关联尚未加载（_groupRelations 为空 map）
     * - true：loadGroupRelations() 已完成，_groupRelations 已填充实际数据
     *
     * UI 层应在 isRelationsLoaded=true 且 todoLines 初始化完成后，
     * 调用 [markGroupSaveStateBaseline] 建立基线指纹，避免 checkAndResetGroupSavedState
     * 因 editStateHash 仍为 "loading" 占位而错误重置保存状态。
     *
     * 重置时机：loadTodo() 启动时重置为 false，确保切换 todo 时基线重建。
     */
    private val _isRelationsLoaded = MutableStateFlow(false)
    val isRelationsLoaded: StateFlow<Boolean> = _isRelationsLoaded.asStateFlow()

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
        pushSnapshot()
        _groupCategoryIds.value = _groupCategoryIds.value + (groupId to categoryId)
    }

    /**
     * 清除指定分组的分类（置为 0L 未分类）
     *
     * @param groupId 分组 ID
     */
    fun clearGroupCategory(groupId: Int) {
        pushSnapshot()
        _groupCategoryIds.value = _groupCategoryIds.value + (groupId to 0L)
    }

    fun setPriority(priority: Int) {
        pushSnapshot()
        _priority.value = priority
    }

    fun setStartDate(startDate: Long?) {
        pushSnapshot()
        _startDate.value = startDate
    }

    /**
     * 设置截止时间
     * 用户在时间选择器中确认后调用
     *
     * @param dueDate 截止时间（毫秒时间戳）
     */
    fun setDueDate(dueDate: Long?) {
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
        _geofenceLat.value = lat
    }

    fun setGeofenceLng(lng: Double?) {
        pushSnapshot()
        _geofenceLng.value = lng
    }

    fun setGeofenceRadius(radius: Float) {
        pushSnapshot()
        _geofenceRadius.value = radius
    }

    fun setGeofenceType(type: Int) {
        pushSnapshot()
        _geofenceType.value = type
    }

    fun setGeofenceEnabled(enabled: Boolean) {
        pushSnapshot()
        _geofenceEnabled.value = enabled
    }

    fun setGeofenceAddress(address: String?) {
        pushSnapshot()
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
            // v2026-07-21：重置关联加载标志，确保切换 todo 时基线重建
            _isRelationsLoaded.value = false

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
                _contentFormat.value = todo.contentFormat

                /** 加载行级附件快照（从 contentFormat 中提取） */
                loadLineAttachmentsSnapshot(todo)

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

                // 加载完成后推入初始快照，作为撤销的基准点
                undoRedoStack.clear()
                pushSnapshot()

                /**
                 * 【多卡片修复】初始化 groupId=0 的保存状态
                 *
                 * 从列表点击卡片进入编辑页时，existingTodo 已有数据库 ID。
                 * 必须将此 ID 记录到 _groupSaveStates 中，
                 * 这样后续 saveGroup() 才能执行 UPDATE 而非 INSERT，
                 * 避免创建重复卡片。
                 *
                 * v2026-07-21：editStateHash 初始化为 "loading" 占位，
                 * 等 loadGroupRelations 完成 + UI 层构造 todoLines 后，
                 * 由 UI 层调用 [markGroupSaveStateBaseline] 用真实 hash 覆盖。
                 * 这避免了"loadTodo 时 _groupRelations 还为空 → relationsCount=0"
                 * 与"后续 _groupRelations 加载完成 → relationsCount=N"的指纹差异
                 * 错误触发 [checkAndResetGroupSavedState] 重置保存状态。
                 */
                val contentSnapshot = buildString {
                    appendLine(todo.title)
                    subTasks.forEach { appendLine(it.title) }
                }.trim()

                _groupSaveStates.value = mapOf(
                    0 to GroupSaveState(
                        groupId = 0,
                        isSaved = true,
                        savedTodoId = todo.id,  // 关键：记录已有 ID
                        savedAt = System.currentTimeMillis(),
                        contentSnapshot = contentSnapshot,
                        editStateHash = "loading"  // 占位，等基线建立后覆盖
                    )
                )

                /** 初始化 groupId=0 的优先级 */
                _groupPriorities.value = mapOf(0 to todo.priority)

                /**
                 * 加载关联关系（v2026-07-21 修复时序 bug）
                 *
                 * 原实现：在 _groupSaveStates 初始化前调用 loadGroupRelations，
                 *   传入 groupIds = _groupSaveStates.value.keys.toList() = []，导致关联从不被加载。
                 * 现实现：在 _groupSaveStates 初始化后调用，传入正确的 listOf(0)。
                 *
                 * 其他分组的关联由 UI 层初始化 todoLines 后主动调用 loadGroupRelations 加载。
                 */
                loadGroupRelations(todoId, listOf(0))

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
                    /**
                     * v2026-07-21 修复：保存时改用分组独立优先级 [_groupPriorities]，
                     * 与下方 [buildTodoItemForGroup] 保持一致。
                     * 原 [setPriority] / [_priority] 是早期单容器模式残留，
                     * [CheckboxEditText] 实际只读 [_groupPriorities]，导致旧逻辑保存
                     * 的是未变化的 [_priority]，与 UI 显示的优先级不一致。
                     */
                    priority = _groupPriorities.value[0] ?: 0,
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
                    /**
                     * v2026-07-21 修复：保存时改用分组独立优先级 [_groupPriorities]，
                     * 与 [buildTodoItemForGroup] / 已有 todo 更新路径保持一致。
                     * 新建模式下 _groupPriorities[0] 为 null 时回退 0（无优先级），
                     * 后续用户修改 groupId=0 的优先级时已通过 [setGroupPriority] 写入。
                     */
                    priority = _groupPriorities.value[0] ?: 0,
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
                // v2026-07-22 新增：修复"新建模式提前 addRelation"导致的 sourceId=0 脏数据。
                // 同步迁移 card_relations 表中所有 sourceType=todo AND sourceId=0 的占位关联，
                // 以及对应的反向记录 (targetType=todo AND targetId=0)。
                // 注：当前 if/else 表达式要求最后一句为 Long，所以这里接住 insertTodo 返回值再显式 return。
                val insertedId = todoRepository.insertTodo(todo)
                cardRelationRepository.fixupZeroSourceRelations("todo", insertedId)
                insertedId
            }

            saveSubTasks(todoId)

            /** 保存内容块到独立表（图片/语音等混合内容） */
            if (_currentContentBlocks.value.isNotEmpty()) {
                saveContentBlocks(todoId, _currentContentBlocks.value)
            }

            // 保存关联关系（新建时将各分组的临时关联绑定到新ID）
            if (existingTodo == null) {
                _groupRelations.value.values.flatten().forEach { relation ->
                    cardRelationRepository.addRelation(relation.copy(sourceId = todoId))
                }
            }
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

                // 9. 更新保存状态（包含内容快照和完整编辑状态指纹，用于后续变化检测）
                val contentSnapshot = groupLines.joinToString("\n") { it.text }
                val editStateHash = computeGroupEditStateHash(targetGroupId, allLines)
                val newState = GroupSaveState(
                    groupId = targetGroupId,
                    isSaved = true,
                    savedTodoId = newTodoId,
                    savedAt = System.currentTimeMillis(),
                    contentSnapshot = contentSnapshot,  // 记录保存时的内容（向后兼容）
                    editStateHash = editStateHash      // 记录保存时的完整编辑状态指纹
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
        pushSnapshot()
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
     * @param todoLines 当前所有 todoLines（用于提取该分组的文本和附件状态）
     */
    fun checkAndResetGroupSavedState(groupId: Int, todoLines: List<com.corgimemo.app.ui.model.TodoLine>) {
        val current = _groupSaveStates.value[groupId]
        if (current != null && current.isSaved) {
            // v2026-07-21：基线尚未建立（"loading" 占位）时跳过比较，
            // 避免 loadTodo 完成但 loadGroupRelations 尚未完成的时序窗口内错误重置。
            // UI 层会在 isRelationsLoaded=true 后调用 markGroupSaveStateBaseline 建立基线。
            if (current.editStateHash == "loading") return
            // 计算当前完整编辑状态指纹（覆盖文字/附件/提醒/分类/优先级/关联）
            val currentHash = computeGroupEditStateHash(groupId, todoLines)
            // 只有当任意元素真正发生变化时才重置
            if (current.editStateHash != currentHash) {
                val newState = current.copy(isSaved = false)
                _groupSaveStates.value = _groupSaveStates.value + (groupId to newState)
                android.util.Log.w("TodoEditVM", "分组 $groupId 编辑状态已变化，重置为未保存状态")
            }
            // 状态未变化则保持 isSaved=true
        }
    }

    /**
     * 标记分组的保存状态基线（v2026-07-21 新增）
     *
     * 在数据加载完成（todoLines 初始化 + 关联加载完成）后调用，
     * 用当前的完整编辑状态指纹覆盖 [GroupSaveState.editStateHash]，
     * **不触发**"未保存"重置。
     *
     * 解决的时序问题：
     * 1. loadTodo 完成 → _groupSaveStates[0].editStateHash = "loading"（占位）
     * 2. UI 层基于 _isLoaded=true 构造 todoLines（首次）
     * 3. loadGroupRelations 完成 → _isRelationsLoaded=true
     * 4. UI 层观察到 isRelationsLoaded=true → 调用本方法建立基线
     * 5. 之后用户编辑 → checkAndResetGroupSavedState 正确比较 hash
     *
     * 如果当前 editStateHash 已是真实指纹（非 "loading"），则跳过更新，
     * 避免用户编辑后的实时变化被基线覆盖。
     *
     * @param groupId 分组 ID
     * @param todoLines 当前所有 todoLines
     */
    fun markGroupSaveStateBaseline(groupId: Int, todoLines: List<com.corgimemo.app.ui.model.TodoLine>) {
        val current = _groupSaveStates.value[groupId] ?: return
        // 仅在 "loading" 占位阶段建立基线，避免覆盖已建立的真实指纹
        if (current.editStateHash != "loading") return
        val currentHash = computeGroupEditStateHash(groupId, todoLines)
        val newState = current.copy(editStateHash = currentHash)
        _groupSaveStates.value = _groupSaveStates.value + (groupId to newState)
        android.util.Log.w("TodoEditVM", "分组 $groupId 基线已建立: editStateHash 长度=${currentHash.length}")
    }

    /**
     * 计算指定分组的完整编辑状态指纹
     *
     * 将以下元素序列化为字符串，用于检测任意变化：
     * - 文本内容（todoLines 中该 groupId 的所有行 text 拼接）
     * - 勾选状态（todoLines 中该 groupId 的所有行 isChecked 拼接）
     * - 图片附件（todoLines 中该 groupId 的所有行 imagePaths 拼接）
     * - 语音附件（todoLines 中该 groupId 的所有行 voiceAttachments 拼接）
     * - 提醒时间（_groupReminders[groupId]）
     * - 分类（_groupCategoryIds[groupId]）
     * - 优先级（_groupPriorities[groupId]）
     * - 关联数量（_groupRelations[groupId]?.size）
     *
     * 撤销/恢复操作会改变 todoLines 的 text/imagePaths/voiceAttachments，自动反映在指纹中。
     *
     * @param groupId 分组 ID
     * @param todoLines 当前所有 todoLines
     * @return 状态指纹字符串
     */
    fun computeGroupEditStateHash(
        groupId: Int,
        todoLines: List<com.corgimemo.app.ui.model.TodoLine>
    ): String {
        val groupLines = todoLines.filter { it.groupId == groupId }
        val text = groupLines.joinToString("\n") { it.text }
        val checked = groupLines.joinToString(",") { it.isChecked.toString() }
        val images = groupLines.flatMap { it.imagePaths }.joinToString(",")
        val voices = groupLines.flatMap { it.voiceAttachments }.joinToString(",") { it.path }
        val reminder = _groupReminders.value[groupId]
        val category = _groupCategoryIds.value[groupId]
        val priority = _groupPriorities.value[groupId]
        val relationsCount = _groupRelations.value[groupId]?.size ?: 0

        return buildString {
            append("text=").append(text).append("|")
            append("checked=").append(checked).append("|")
            append("images=").append(images).append("|")
            append("voices=").append(voices).append("|")
            append("reminder=").append(reminder).append("|")
            append("category=").append(category).append("|")
            append("priority=").append(priority).append("|")
            append("relations=").append(relationsCount)
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
        pushSnapshot()
        _voiceNotePath.value = path
        _voiceDuration.value = duration
    }

    /**
     * 清除语音备注
     */
    fun clearVoiceNote() {
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
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
        pushSnapshot()
        _voiceNotePath.value = path
    }

    /**
     * 清空所有图片路径
     * 同时清理内部存储中的所有对应文件
     */
    fun clearImagePaths() {
        pushSnapshot()
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
        pushSnapshot()
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
     * 添加关联关系（分组级别）
     *
     * 业务规则：
     * - todoId 必须已保存（> 0），否则提示用户先保存
     * - 同分组内同 target 不能重复（Repository 去重）
     * - 单分组关联上限 10 张（Repository 限制）
     *
     * ⚠️ 注意：此方法每次调用都会启动新协程，多次连续调用会并发执行，
     * 可能出现 _groupRelations 读改写覆盖问题。批量添加场景请使用 [addRelations]。
     *
     * @param targetType 目标类型 ("todo" | "inspiration" | "date")
     * @param targetId 目标ID
     * @param groupId 当前分组ID
     */
    fun addRelation(targetType: String, targetId: Long, groupId: Int) {
        viewModelScope.launch {
            val todoId = existingTodo?.id ?: 0L
            if (todoId == 0L) {
                _errorEvent.emit("请先保存待办再添加关联")
                return@launch
            }
            val relation = CardRelation(
                sourceType = "todo",
                sourceId = todoId,
                groupId = groupId,
                targetType = targetType,
                targetId = targetId
            )
            val result = cardRelationRepository.addRelation(relation)
            when (result) {
                -1L -> _errorEvent.emit("该卡片已关联")
                -2L -> _errorEvent.emit("每组最多关联 10 张卡片")
                else -> {
                    if (result > 0) {
                        val currentList = _groupRelations.value[groupId] ?: emptyList()
                        val updatedList = (currentList + relation.copy(id = result))
                            .distinctBy { "${it.targetType}_${it.targetId}" }
                        _groupRelations.value = _groupRelations.value + (groupId to updatedList)
                        // 新增关联后立即加载其标题到缓存
                        val title = cardRelationRepository.getCardTitle(targetType, targetId)
                        if (title != null) {
                            _relationTitles.value = _relationTitles.value + (result to title)
                        } else {
                            _relationTitles.value = _relationTitles.value + (result to "已删除")
                        }
                    }
                }
            }
        }
    }

    /**
     * 批量添加关联关系（分组级别，串行处理）
     *
     * 解决 [addRelation] 在循环调用时的并发覆盖问题：
     * 单次 launch 内串行处理所有卡片，避免多个协程同时读改写 [_groupRelations]。
     *
     * 业务规则：
     * - todoId 必须已保存（> 0），否则提示用户先保存
     * - 同分组内同 target 不能重复（Repository 去重，重复时静默跳过）
     * - 单分组关联上限 10 张（达到上限时停止添加并提示）
     * - 一次性更新 [_groupRelations] 和 [_relationTitles]，避免多次 emit 导致 UI 抖动
     *
     * @param cards 待添加的卡片列表 (targetType, targetId)
     * @param groupId 当前分组ID
     */
    fun addRelations(cards: List<Pair<String, Long>>, groupId: Int) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val todoId = existingTodo?.id ?: 0L
            if (todoId == 0L) {
                _errorEvent.emit("请先保存待办再添加关联")
                return@launch
            }
            // 串行处理所有卡片，累积结果后一次性更新
            val currentList = (_groupRelations.value[groupId] ?: emptyList()).toMutableList()
            val newTitles = mutableMapOf<Long, String>()
            var reachedLimit = false
            var addedCount = 0

            cards.forEach { (targetType, targetId) ->
                if (reachedLimit) return@forEach
                // 跳过已在内存列表中的（避免无谓 DB 调用）
                val existsInMemory = currentList.any {
                    it.targetType == targetType && it.targetId == targetId
                }
                if (existsInMemory) return@forEach

                val relation = CardRelation(
                    sourceType = "todo",
                    sourceId = todoId,
                    groupId = groupId,
                    targetType = targetType,
                    targetId = targetId
                )
                val result = cardRelationRepository.addRelation(relation)
                when (result) {
                    -1L -> { /* 已关联，静默跳过 */ }
                    -2L -> {
                        _errorEvent.emit("每组最多关联 10 张卡片")
                        reachedLimit = true
                    }
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
                _groupRelations.value = _groupRelations.value + (groupId to distinctList)
                if (newTitles.isNotEmpty()) {
                    _relationTitles.value = _relationTitles.value + newTitles
                }
            }
        }
    }

    /**
     * 删除关联关系
     *
     * 同时更新内存中的 _groupRelations Map，移除指定 relationId。
     *
     * @param relationId 关联ID
     * @param groupId 关联所属的分组ID（用于定位 Map 中的列表）
     */
    fun deleteRelation(relationId: Long, groupId: Int) {
        viewModelScope.launch {
            cardRelationRepository.removeRelationById(relationId)
            val currentList = _groupRelations.value[groupId] ?: emptyList()
            val updatedList = currentList.filter { it.id != relationId }
            _groupRelations.value = _groupRelations.value + (groupId to updatedList)
        }
    }

    /**
     * 加载某 todo 的所有分组的关联
     *
     * 在 loadTodo() 完成后调用，初始化 _groupRelations Map。
     * 对于新建的 todo（todoId=0），Map 为空。
     *
     * @param todoId 待办ID
     * @param groupIds 所有分组ID列表（从 _groupSaveStates 等 Map 的 keys 提取）
     */
    fun loadGroupRelations(todoId: Long, groupIds: List<Int>) {
        viewModelScope.launch {
            if (todoId == 0L) {
                _groupRelations.value = emptyMap()
                _isRelationsLoaded.value = true  // v2026-07-21：标记加载完成（即使为空）
                return@launch
            }
            val relationsMap = mutableMapOf<Int, List<CardRelation>>()
            groupIds.forEach { gid ->
                relationsMap[gid] = cardRelationRepository.getRelationsBlocking("todo", todoId, gid)
            }
            _groupRelations.value = relationsMap
            _isRelationsLoaded.value = true  // v2026-07-21：标记加载完成，触发 UI 层建立基线
            // 异步加载关联标题缓存
            refreshRelationTitles()
        }
    }

    /**
     * 刷新关联标题缓存
     *
     * 在 _groupRelations 变化时调用，异步加载每个关联目标卡片的标题。
     * 已缓存的标题不会重复加载（增量更新）。
     * 标题为 null 的卡片（已删除）不会写入缓存，UI 会显示"已删除"。
     */
    private fun refreshRelationTitles() {
        viewModelScope.launch {
            val allRelations = _groupRelations.value.values.flatten()
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

    /**
     * 加载卡片详情（用于关联预览 Dialog 按类型差异化展示）
     *
     * 调用时机：用户点击 LinkedCardsRow 中的 Chip，Dialog 弹出前。
     * 并发保护：每次调用重置 [_cardDetail] 为 null，[_cardDetailLoading] 为 true，
     * 然后异步加载。UI 层观察这两个 StateFlow 切换显示状态。
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
     * 清空卡片详情状态
     *
     * 调用时机：用户关闭预览 Dialog（点击 × / 关闭按钮 / 取消关联 / 跳转详情）。
     * 防止下次打开 Dialog 时短暂显示旧数据。
     */
    fun clearCardDetail() {
        _cardDetail.value = null
        _cardDetailLoading.value = false
        undoRedoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
    }
}
