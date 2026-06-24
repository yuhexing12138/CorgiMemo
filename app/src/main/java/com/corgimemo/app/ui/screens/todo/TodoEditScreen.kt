package com.corgimemo.app.ui.screens.todo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.corgimemo.app.util.toPxFloat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.ui.components.*
import com.corgimemo.app.ui.model.ContentBlock
import com.corgimemo.app.ui.model.TodoLine
import com.corgimemo.app.util.ImageUtils
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.util.VoiceRecorder
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.TodoEditViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditScreen(
    navController: NavController,
    todoId: Long? = null,
    viewModel: TodoEditViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val priority by viewModel.priority.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val dueDate by viewModel.dueDate.collectAsState()
    val estimatedDurationMinutes by viewModel.estimatedDurationMinutes.collectAsState()

    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    /**
     * 各分组的提醒时间映射表
     *
     * - key = 分组 id（groupId）
     * - value = 提醒时间戳（毫秒），null 表示该分组未设置提醒
     *
     * 替代旧的单一 reminderTime（整个 todo 共用一个提醒时间）。
     * 提醒 picker 打开时，按 editingReminderGroupId 取对应分组的时间作为初值。
     */
    val groupReminders by viewModel.groupReminders.collectAsState()
    /**
     * 各分组的重复类型映射表
     *
     * - key = 分组 id（groupId）
     * - value = 重复类型枚举（0=不重复, 1=每天, 2=每周, …）
     *
     * 替代旧的单一 repeatType。
     */
    val groupRepeatTypes by viewModel.groupRepeatTypes.collectAsState()

    val voiceNotePath by viewModel.voiceNotePath.collectAsState()
    val voiceDuration by viewModel.voiceDuration.collectAsState()

    /** 各分组的保存状态（用于控制容器视觉反馈） */
    val groupSaveStates by viewModel.groupSaveStates.collectAsState()

    /** 各分组的优先级状态 */
    val groupPriorities by viewModel.groupPriorities.collectAsState()

    /** 优先级弹窗状态：null=关闭，Int=当前编辑的 groupId */
    var showPriorityDialog by remember { mutableStateOf<Int?>(null) }
    /** 分类选择弹窗状态 */
    var showCategoryDialog by remember { mutableStateOf(false) }

    val imagePaths by viewModel.imagePaths.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val currentCategory = categories.find { it.id == categoryId }

    /** 行级附件快照数据（从数据库加载，用于恢复每行的附件） */
    val lineAttachmentsSnapshot by viewModel.lineAttachmentsSnapshot.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current

    val speechViewModel by remember { lazy { com.corgimemo.app.viewmodel.SpeechViewModel(context) } }
    val isListening by speechViewModel.isListening.collectAsState()
    val isProcessing by speechViewModel.isProcessing.collectAsState()
    val speechResult by speechViewModel.resultText.collectAsState()
    val speechError by speechViewModel.errorMessage.collectAsState()

    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer(context) }
    
    var showVoiceRecordSheet by remember { mutableStateOf(false) }

    var showImagePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val contentBlocks = remember { androidx.compose.runtime.mutableStateListOf<ContentBlock>() }
    var highlightedIndex by remember { mutableIntStateOf(-1) }
    val blockVisibilityStates = remember { mutableStateMapOf<Int, Boolean>() }
    var isLocked by remember { mutableStateOf(false) }

    /** 复选框编辑器的行数据列表 */
    var todoLines by remember { mutableStateOf(listOf(TodoLine())) }

    /** 当前聚焦的行索引（用于确定附件插入目标） */
    var focusedLineIndex by remember { mutableIntStateOf(0) }

    /**
     * 跨行拖拽状态管理器实例
     *
     * 协调管理图片/语音附件的拖拽操作，
     * 支持行内排序和跨行移动两种模式。
     * 整个编辑页面共享同一个实例。
     */
    val crossLineDragManager = remember { com.corgimemo.app.ui.components.CrossLineDragManager() }

    /**
     * 行边界矩形缓存（用于精确的目标行检测）
     *
     * key = 行索引 (Int)
     * value = 该行在屏幕上的边界矩形 (Rect: left, top, right, bottom)
     *
     * 由 CheckboxEditText 内部的 onGloballyPositioned 回调更新，
     * 用于 CrossLineDragManager 行边界检测。
     */
    val rowBoundsMap = remember { mutableMapOf<Int, androidx.compose.ui.geometry.Rect>() }

    /**
     * 向当前聚焦行添加图片附件
     *
     * @param imagePath 图片的本地存储路径
     */
    fun addImageToFocusedLine(imagePath: String) {
        val updatedLines = todoLines.toMutableList()
        if (focusedLineIndex in updatedLines.indices) {
            val currentLine = updatedLines[focusedLineIndex]
            updatedLines[focusedLineIndex] = currentLine.copy(
                imagePaths = currentLine.imagePaths + imagePath
            )
            todoLines = updatedLines
        }
    }

    /**
     * 向当前聚焦行添加语音附件
     *
     * @param voicePath 语音文件的本地存储路径
     * @param duration 语音时长（秒）
     */
    fun addVoiceToFocusedLine(voicePath: String, duration: Int?) {
        val updatedLines = todoLines.toMutableList()
        if (focusedLineIndex in updatedLines.indices) {
            val currentLine = updatedLines[focusedLineIndex]
            val newVoiceAttachment = com.corgimemo.app.ui.model.VoiceAttachment(
                path = voicePath,
                duration = duration
            )
            updatedLines[focusedLineIndex] = currentLine.copy(
                voiceAttachments = currentLine.voiceAttachments + newVoiceAttachment
            )
            todoLines = updatedLines
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSuccess: Boolean ->
        if (isSuccess) {
            pendingPhotoUri?.let { uri ->
                coroutineScope.launch {
                    val savedPath = com.corgimemo.app.util.ImageUtils.copyUriToInternalStorage(context, uri)
                    savedPath?.let { path ->
                        viewModel.addImagePath(path)
                        // 将图片添加到当前聚焦行，而非全局 contentBlocks
                        addImageToFocusedLine(path)
                    }
                }
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        coroutineScope.launch {
            uris.forEach { uri ->
                val savedPath = ImageUtils.copyUriToInternalStorage(context, uri)
                savedPath?.let { path ->
                    viewModel.addImagePath(path)
                    // 将图片添加到当前聚焦行，而非全局 contentBlocks
                    addImageToFocusedLine(path)
                }
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val photoUri = com.corgimemo.app.util.ImageUtils.createImageUri(context)
            pendingPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        }
    }

    var showColorPicker by remember { mutableStateOf(false) }
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val rawBackgroundColor = Color(backgroundColorInt)
    val contentBackgroundColor = if (backgroundColorInt == -1 || rawBackgroundColor == Color.White) {
        MaterialTheme.colorScheme.background
    } else {
        rawBackgroundColor
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    val startTimePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0,
        is24Hour = true
    )
    /**
     * 当前正在编辑哪个分组的提醒
     *
     * - null：picker 未打开
     * - 非 null：picker 打开，正在编辑该 groupId 对应分组的提醒
     *
     * 由 CheckboxEditText 的 onReminderClick(groupId) 回调赋值，
     * 关闭 picker 时（onDismiss / onConfirm）置为 null。
     */
    var editingReminderGroupId by remember { mutableStateOf<Int?>(null) }
    /** picker 是否打开：editingReminderGroupId != null 即展示 */
    val showReminderPicker = editingReminderGroupId != null

    var showDueDatePicker by remember { mutableStateOf(false) }
    val dueDatePickerState = rememberDatePickerState()
    var selectedDueDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDueTimePicker by remember { mutableStateOf(false) }
    val dueTimePickerState = rememberTimePickerState(
        initialHour = 18,
        initialMinute = 0,
        is24Hour = true
    )
    val snackbarHostState = remember { SnackbarHostState() }

    var showLocationPopup by remember { mutableStateOf(false) }

    // @触发关联弹窗状态
    var showMentionPopup by remember { mutableStateOf(false) }
    var mentionQuery by remember { mutableStateOf("") }
    /** #搜索关键词状态 */
    var locationQuery by remember { mutableStateOf("") }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            speechViewModel.setPermissionGranted(granted)
            if (granted) {
                speechViewModel.startListening()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("请在设置中开启录音权限")
                }
            }
        }
    )

    /**
     * 加载待办数据（带 ID 时为编辑模式）
     *
     * 使用 LaunchedEffect(todoId) 确保仅在 todoId 变化时触发一次加载，
     * 避免在每次重组时重复调用导致的数据异常。
     */
    LaunchedEffect(todoId) {
        if (todoId != null && todoId > 0) {
            viewModel.loadTodo(todoId)
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.setPoseForCreating()
        /** 确保默认分类已初始化（DB 无分类时插入 5 个默认）；然后加载到内存列表 */
        viewModel.loadCategories()
    }

    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.resetPoseToDefault()
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    var hasInitializedBlocks by remember { mutableStateOf(false) }
    var hasInitializedLines by remember { mutableStateOf(false) }

    /** 从 ViewModel 获取数据加载完成标志 */
    val isLoaded by viewModel.isLoaded.collectAsState()

    /** 从 content 文本和 subTasks 初始化复选框行数据 */
    val subTasks by viewModel.subTasks.collectAsState()

    /**
     * 初始化复选框行数据（todoLines）
     *
     * 关键修复：使用 isLoaded 标志解决竞态条件
     *
     * 问题背景：
     * - loadTodo() 是异步操作，需要时间从数据库读取
     * - 如果在 loadTodo 完成前就初始化 todoLines，会用空数据（title="", content=""）初始化
     * - 之后 hasInitializedLines=true 会阻止重新初始化，导致内容丢失
     *
     * 解决方案：
     * - 新增 key: isLoaded（ViewModel 中的数据加载完成标志）
     * - 仅当 isLoaded=true 且尚未初始化时才执行初始化逻辑
     * - 编辑模式：isLoaded 在 loadTodo 完成后变为 true → 用实际数据初始化
     * - 新建模式：isLoaded 始终为 false（无 todoId）→ 直接用空数据初始化
     */
    LaunchedEffect(isLoaded, todoId, subTasks, hasInitializedLines) {
        /**
         * 初始化条件判断：
         * 1. 编辑模式（todoId != null）：必须等 isLoaded=true 才能确保数据已填充
         * 2. 新建模式（todoId == null）：isLoaded 永远为 false，直接初始化空行
         */
        val shouldInit = if (todoId != null) {
            isLoaded && !hasInitializedLines  // 编辑模式：等加载完成
        } else {
            !hasInitializedLines  // 新建模式：立即初始化
        }

        if (shouldInit) {
            /**
             * 数据源优先级策略：
             * 1. 优先从 subTasks 表恢复结构化数据（最可靠，有独立 ID）
             * 2. 回退到 content 字段纯文本解析（兼容旧数据）
             * 3. 最后使用空行（新建模式）
             *
             * 注意：第一行统一使用 title 字段，而非从 content 解析，
             * 避免 content 中可能存在的陈旧/不一致数据导致显示错误。
             *
             * 添加诊断日志以便追踪数据不一致问题
             */
            android.util.Log.w(
                "TodoEditInit",
                "初始化 todoLines: todoId=$todoId, title='$title', " +
                "content='$content', subTasks.size=${subTasks.size}, " +
                "subTasks=${subTasks.map { it.title }}"
            )

            todoLines = if (subTasks.isNotEmpty()) {
                // 优先从子任务表恢复结构化数据，第一行用已加载的标题填充
                val result = listOf(TodoLine(text = title)) + TodoLine.fromSubTasks(subTasks)
                android.util.Log.w("TodoEditInit", "使用 fromSubTasks: $result")
                result
            } else if (content.isNotBlank()) {
                // 从纯文本解析（回退方案，用于无子任务的旧数据）
                val parsedLines = TodoLine.parseFromText(content)

                /**
                 * 关键修复：确保第一行与 title 字段一致
                 *
                 * 问题背景：
                 * - content 字段可能包含陈旧或不一致的文本
                 * - 例如：title 已被更新为 "测试1"，但 content 还是旧的 "☐ 测试\n  ☐ 测试2"
                 * - 如果直接使用 parseFromText 结果，第一行会是 "测试" 而非 "测试1"
                 * - 这会导致用户看到被截断或过时的标题
                 *
                 * 解决方案：
                 * - 如果解析结果有多行，且第一行文本与当前 title 不一致，
                 *   则用 title 替换第一行的文本，确保显示最新数据
                 */
                if (parsedLines.isNotEmpty() && parsedLines[0].text != title) {
                    listOf(parsedLines[0].copy(text = title)) + parsedLines.drop(1)
                } else {
                    parsedLines
                }.ifEmpty { listOf(TodoLine()) }
            } else {
                listOf(TodoLine())
            }

            /**
             * 【关键修复】恢复行内附件（支持多行）
             *
             * 优先级策略：
             * 1. 行级快照（lineAttachmentsSnapshot）：最精确，记录了每行的完整附件信息
             * 2. 全局回退（imagePaths/voiceNotePath）：兼容旧数据，将所有附件放到第一行
             *
             * 问题背景：
             * - fromSubTasks() 和 parseFromText() 都不恢复 imagePaths/voiceAttachments
             * - 导致重新打开待办时，虽然 _imagePaths/_voiceNotePath 有数据，
             *   但 todoLines 中没有附件数据，UI 显示空白
             *
             * 解决方案（多行支持）：
             * - 如果有行级快照数据，使用 LineSnapshotUtils 精确恢复到对应行
             * - 否则回退到旧逻辑，将全局附件放到第一行
             */
            if (todoLines.isNotEmpty()) {
                // 尝试从行级快照恢复（新方式：支持多行）
                val snapshots = com.corgimemo.app.ui.model.LineSnapshotUtils.deserialize(lineAttachmentsSnapshot)
                if (snapshots.isNotEmpty()) {
                    // 使用行级快照精确恢复每行的附件
                    todoLines = com.corgimemo.app.ui.model.LineSnapshotUtils.restoreAttachmentsToLines(todoLines, snapshots)
                    android.util.Log.w("TodoEditInit", "使用行级快照恢复附件: ${snapshots.size}个行快照")
                } else {
                    // 回退到旧逻辑：将全局附件放到第一行（兼容无快照的旧数据）
                    val firstLine = todoLines[0]
                    var updatedLine = firstLine

                    // 恢复图片到第一行
                    if (imagePaths.isNotEmpty() && firstLine.imagePaths.isEmpty()) {
                        updatedLine = updatedLine.copy(imagePaths = imagePaths.toList())
                        android.util.Log.w("TodoEditInit", "恢复图片到第一行: ${imagePaths}")
                    }

                    // 恢复录音到第一行
                    val voicePath = voiceNotePath
                    if (voicePath != null && firstLine.voiceAttachments.isEmpty()) {
                        val voiceAttachment = com.corgimemo.app.ui.model.VoiceAttachment(
                            path = voicePath,
                            duration = voiceDuration
                        )
                        updatedLine = updatedLine.copy(voiceAttachments = listOf(voiceAttachment))
                        android.util.Log.w("TodoEditInit", "恢复录音到第一行: $voicePath")
                    }

                    // 如果有更新，替换第一行
                    if (updatedLine != firstLine) {
                        todoLines = todoLines.toMutableList().also { it[0] = updatedLine }
                        android.util.Log.w("TodoEditInit", "附件恢复完成: 第一行=$updatedLine")
                    }
                }
            }

            hasInitializedLines = true
        }
    }

    /** 行数据变更时同步到 ViewModel 的 content 和 title 字段（混合存储） */
    LaunchedEffect(todoLines) {
        if (hasInitializedLines) {
            val plainText = todoLines
                .filter { it.text.isNotBlank() || todoLines.size == 1 }
                .joinToString("\n") { it.toPlainText() }

            /** 诊断日志：追踪同步过程 */
            android.util.Log.w(
                "TodoEditSync",
                "todoLines 变化触发同步: lines=$todoLines, plainText='$plainText'"
            )

            viewModel.setContent(plainText)

            /** 将第一行（主任务行）的文本同步为标题，确保 saveTodo() 校验通过 */
            val firstLineText = todoLines.firstOrNull()?.text ?: ""
            viewModel.setTitleWithRecommendation(firstLineText)

            /**
             * 同步子任务数据到 ViewModel（整体替换策略）
             *
             * 【关键 Bug 修复】解决用户输入时子任务重复累积问题
             *
             * 问题背景（已通过日志验证）：
             * - 用户输入 "测试2" 时，会经过 "2" → "23" → "237" → ... → "测试" → "测试2"
             * - 旧逻辑（增量添加）：每个中间状态都调用 addSubTask()，导致累积大量无效子任务
             * - 结果：_subTasks = ["2", "23", "237", ..., "测试", "测试2"] （应该只有 ["测试2"]）
             *
             * 新策略（整体替换）：
             * - 每次 todoLines 变化时，从 UI 层的行数据重新构建完整的子任务列表
             * - 调用 viewModel.replaceSubTasks() 整体替换，而非逐个增量添加
             * - 这样无论用户如何修改文本，最终只保留当前可见的子任务
             */
            val currentSubTaskLines = todoLines.filter { it.isSubTask && it.text.isNotBlank() }

            /**
             * 构建新的子任务列表
             *
             * 策略：
             * - 如果行有 subTaskId > 0（来自数据库），保留其 ID 以便后续更新
             * - 如果行是新建的 (subTaskId == 0)，生成临时子任务对象
             */
            val newSubTasks = currentSubTaskLines.map { line ->
                if (line.subTaskId > 0L) {
                    // 已有数据库 ID 的子任务：保留 ID，更新文本
                    com.corgimemo.app.data.model.SubTask(
                        id = line.subTaskId,
                        todoId = viewModel.currentTodo?.id ?: 0,
                        title = line.text,
                        isCompleted = line.isChecked,
                        order = line.order
                    )
                } else {
                    // 新建子任务：ID 为 0
                    com.corgimemo.app.data.model.SubTask(
                        id = 0,
                        todoId = viewModel.currentTodo?.id ?: 0,
                        title = line.text,
                        isCompleted = line.isChecked,
                        order = line.order
                    )
                }
            }

            /** 整体替换 ViewModel 中的子任务列表 */
            android.util.Log.w(
                "TodoEditSync",
                "整体替换子任务: ${newSubTasks.map { it.title }}"
            )
            viewModel.replaceSubTasks(newSubTasks)

            /**
             * 【多卡片】检测内容变化，智能重置已保存分组的编辑状态
             *
             * 比较每个已保存分组的当前内容与保存时的快照：
             * - 内容相同 → 不重置（如只是添加了新容器"/"或新行）
             * - 内容不同 → 重置为未保存（用户真正编辑了该容器的内容）
             */
            if (groupSaveStates.isNotEmpty()) {
                // 按分组聚合当前行
                val currentGroupContents = todoLines
                    .groupBy { it.groupId }
                    .mapValues { (_, lines) ->
                        lines.joinToString("\n") { it.text }
                    }

                // 只检查已保存的分组
                groupSaveStates.forEach { (groupId, state) ->
                    if (state.isSaved) {
                        val currentContent = currentGroupContents[groupId] ?: ""
                        viewModel.checkAndResetGroupSavedState(groupId, currentContent)
                    }
                }
            }

            /**
             * 【多行附件支持】序列化当前 todoLines 的完整状态（包括每行附件）
             *
             * 将当前的 todoLines 列表（包含每行的 imagePaths/voiceAttachments）
             * 与原始的 Markdown 富文本内容合并序列化。
             *
             * 存储格式："{Markdown内容}|||LINE_ATTACHMENTS|||[{JSON}]"
             *
             * 数据流：
             * todoLines → LineSnapshotUtils.fromTodoLines() → LineSnapshot 列表
             *           + _contentFormat (原始 Markdown)
             *           → LineSnapshotUtils.serialize(snapshots, originalContent) → 合并字符串
             *           → viewModel.saveLineAttachmentsSnapshot() → 存入 StateFlow
             *           → performSave() 读取 → 写入 DB.contentFormat 字段
             */
            val snapshots = com.corgimemo.app.ui.model.LineSnapshotUtils.fromTodoLines(todoLines)
            val snapshotJson = com.corgimemo.app.ui.model.LineSnapshotUtils.serialize(
                snapshots = snapshots,
                originalContent = content  // 使用当前 ViewModel 中的 content 值作为原始内容
            )
            if (snapshotJson.isNotBlank()) {
                viewModel.saveLineAttachmentsSnapshot(snapshotJson)
                android.util.Log.w("TodoEditSync", "序列化行级快照: ${snapshots.size}个行, 长度=${snapshotJson.length}")
            }

            /**
             * 【关键修复】同步行级附件到 ViewModel
             *
             * 问题背景：
             * - 用户通过 addImageToFocusedLine() 添加的图片存储在 todoLines[].imagePaths（行级）
             * - 但保存时使用的是 viewModel._imagePaths（全局）
             * - 如果不同步，行级附件不会被持久化，导致重新打开时丢失
             *
             * 解决方案：
             * - 从所有 todoLines 中收集图片路径和录音附件
             * - 调用 viewModel 的 setImagePaths/setVoiceNotePath 方法更新全局状态
             */
            val allImagePaths = todoLines.flatMap { it.imagePaths }.distinct()
            if (allImagePaths != imagePaths) {
                android.util.Log.w("TodoEditSync", "同步图片: $allImagePaths")
                viewModel.setImagePaths(allImagePaths)
            }

            // 同步行级录音附件（取第一个非空的录音）
            val allVoiceAttachments = todoLines.flatMap { it.voiceAttachments }.distinct()
            if (allVoiceAttachments.isNotEmpty()) {
                val firstVoice = allVoiceAttachments.first()
                if (firstVoice.path != voiceNotePath) {
                    android.util.Log.w("TodoEditSync", "同步录音: ${firstVoice.path}")
                    viewModel.setVoiceNotePath(firstVoice.path)
                }
            }
        }
    }

    /**
     * 初始化内容块列表（contentBlocks）
     *
     * 关键修复：使用 isLoaded 标志解决竞态条件
     *
     * 问题背景：
     * - loadTodo() 是异步操作，imagePaths/voiceNotePath 需要时间从数据库加载
     * - 如果在 loadTodo 完成前就初始化 contentBlocks，会用空的 imagePaths/voiceNotePath
     * - 之后 hasInitializedBlocks=true 会阻止重新初始化，导致附件丢失
     *
     * 解决方案：
     * - 新增 key: isLoaded（ViewModel 中的数据加载完成标志）
     * - 仅当 isLoaded=true 且尚未初始化时才执行初始化逻辑
     * - 编辑模式：isLoaded 在 loadTodo 完成后变为 true → 用实际数据初始化
     */
    LaunchedEffect(isLoaded, todoId, hasInitializedBlocks) {
        /** 判断是否应该初始化：编辑模式必须等 isLoaded=true */
        val shouldInit = if (todoId != null) {
            isLoaded && !hasInitializedBlocks
        } else {
            !hasInitializedBlocks
        }

        if (shouldInit && todoId != null) {
            val dbBlocks = viewModel.loadContentBlocks(todoId)

            /** 诊断日志：追踪 contentBlocks 初始化 */
            android.util.Log.w(
                "TodoEditInit",
                "初始化 contentBlocks: todoId=$todoId, dbBlocks.size=${dbBlocks.size}, " +
                "imagePaths=$imagePaths, voiceNotePath=$voiceNotePath"
            )

            if (dbBlocks.isNotEmpty()) {
                /** 从 ContentBlock 表加载到持久化的内容块 */
                contentBlocks.clear()
                contentBlocks.addAll(dbBlocks)
            } else {
                /**
                 * 回退逻辑：从 ViewModel 的 imagePaths/voiceNotePath 恢复
                 *
                 * 此时 isLoaded=true，确保这些字段已填充实际数据，
                 * 避免用空值初始化导致附件丢失。
                 */
                contentBlocks.clear()
                imagePaths.forEach { path ->
                    contentBlocks.add(ContentBlock.Image(path))
                }
                voiceNotePath?.let { path ->
                    contentBlocks.add(ContentBlock.Voice(path, voiceDuration))
                }
            }
            viewModel.syncContentBlocks(contentBlocks.toList())
            hasInitializedBlocks = true
        }
    }

    LaunchedEffect(startDate, dueDate) {
        val start = startDate
        val due = dueDate
        if (start != null && due != null && due > start) {
            val totalMinutes = (due - start) / 1000 / 60
            viewModel.setEstimatedDurationMinutes(totalMinutes.toInt())
        }
    }

    if (speechError.isNotEmpty()) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(speechError)
            speechViewModel.resetError()
        }
    }

    // 外层 Box：确保弹窗遮罩覆盖 topBar 和 bottomBar（解决层级穿透问题）
    Box(modifier = Modifier.fillMaxSize()) {

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeAreaForTopBar()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "返回",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        /** 全部完成：保存所有分组 + 返回列表页 */
                        val savedCount = viewModel.saveAllGroups(todoLines)
                        homeViewModel.setPoseForLoading()
                        // 注意：homeViewModel.refreshSubTaskProgress() 已被删除
                        // 刷新由 TodoEventBus 在 HomeViewModel.init 订阅中自动完成
                        // （避免 editor 实例调用影响不到 home 实例的问题）
                        navController.popBackStack()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9A5C)
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "全部完成",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            EditToolbar(
                onPhotoClick = {
                    showImagePicker = true
                },
                onVoiceClick = {
                    showVoiceRecordSheet = true
                },
                onBackgroundClick = {
                    showColorPicker = true
                },
                onShareClick = {},
                onDeleteClick = {
                    if (todoId != null && todoId > 0) {
                        homeViewModel.deleteTodo(todoId)
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.safeAreaForEditBar()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(contentBackgroundColor)
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {

            // 【已废弃】全局 contentBlocks 渲染区域
            // 附件现在改为行级存储，在每个 CheckboxEditRow 内部渲染（支持子任务缩进）
            // 以下 ReorderableColumn 已被替换为 TodoLine.imagePaths / voiceAttachments 字段
            /*
            com.corgimemo.app.ui.components.ReorderableColumn(
                items = contentBlocks.filter { it !is ContentBlock.Text },
                onReorder = { fromIndex, toIndex ->
                    val nonTextBlocks = contentBlocks.filter { it !is ContentBlock.Text }.toMutableList()
                    viewModel.pushBlocksReorderedOperation(nonTextBlocks.toList())
                    val moved = nonTextBlocks.removeAt(fromIndex)
                    nonTextBlocks.add(toIndex, moved)

                    val textBlocks = contentBlocks.filter { it is ContentBlock.Text }
                    val newOrder = mutableListOf<ContentBlock>()
                    var nonTextIdx = 0
                    contentBlocks.forEach { block ->
                        if (block is ContentBlock.Text) {
                            newOrder.add(block)
                        } else {
                            newOrder.add(nonTextBlocks[nonTextIdx++])
                        }
                    }
                    contentBlocks.clear()
                    contentBlocks.addAll(newOrder)
                    highlightedIndex = -1
                    viewModel.syncContentBlocks(contentBlocks.toList())
                },
                modifier = Modifier.fillMaxWidth()
            ) { index, block, isDragging ->
                val globalBlockIndex = contentBlocks.indexOf(block)
                val isBlockVisible = blockVisibilityStates.getOrDefault(globalBlockIndex, false)

                val baseModifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .onVisibilityChanged { isVisible ->
                        if (blockVisibilityStates[globalBlockIndex] != isVisible) {
                            blockVisibilityStates[globalBlockIndex] = isVisible
                        }
                    }
                    .then(
                        if (isDragging) {
                            Modifier.graphicsLayer(
                                scaleX = 1.05f,
                                scaleY = 1.05f,
                                shadowElevation = 8f,
                                translationY = (-4).dp.toPxFloat(density)
                            )
                        } else {
                            Modifier
                        }
                    )

                when (block) {
                    is ContentBlock.Image -> {
                        com.corgimemo.app.ui.components.InlineImagePreview(
                            imageUri = block.path,
                            modifier = baseModifier,
                            isHighlighted = index == highlightedIndex,
                            isVisible = isBlockVisible
                        )
                    }
                    is ContentBlock.Voice -> {
                        com.corgimemo.app.ui.components.VoicePlayerComponent(
                            voicePlayer = voicePlayer,
                            filePath = block.path,
                            totalDuration = block.duration,
                            onDelete = {
                                val deleteIdx = contentBlocks.indexOf(block)
                                if (deleteIdx >= 0) {
                                    viewModel.pushBlockDeletedOperation(listOf(block), deleteIdx)
                                    contentBlocks.removeAt(deleteIdx)
                                    if (highlightedIndex == deleteIdx) highlightedIndex = -1
                                    else if (highlightedIndex > deleteIdx) highlightedIndex--
                                    viewModel.syncContentBlocks(contentBlocks.toList())
                                }
                            },
                            isHighlighted = index == highlightedIndex,
                            modifier = baseModifier,
                            isVisible = isBlockVisible
                        )
                    }
                    is ContentBlock.Text -> {}
                }
            }
            */

            /** 复选框文本编辑器（替代原 OutlinedTextField，支持逐行复选框编辑） */
            CheckboxEditText(
                lines = todoLines,
                /** 各分组的保存状态（用于控制容器视觉反馈） */
                groupSaveStates = groupSaveStates,
                groupPriorities = groupPriorities,
                onPriorityButtonClick = { groupId ->
                    /** 打开优先级选择弹窗 */
                    showPriorityDialog = groupId
                },
                onLinesChange = { newLines -> todoLines = newLines },
                onLineCheckToggle = { index, isChecked ->
                    val line = todoLines.getOrNull(index) ?: return@CheckboxEditText
                    if (line.isSubTask && line.subTaskId > 0) {
                        viewModel.toggleSubTaskCompletion(
                            com.corgimemo.app.data.model.SubTask(
                                id = line.subTaskId,
                                todoId = todoId ?: 0,
                                title = line.text,
                                isCompleted = isChecked,
                                order = index
                            )
                        )
                    }
                },
                onSpecialCharDetected = { type, query ->
                    when (type) {
                        "@" -> {
                            if (query != null) {
                                if (!showMentionPopup) showMentionPopup = true
                                mentionQuery = query
                            } else {
                                showMentionPopup = false
                            }
                        }
                        "#" -> {
                            if (query != null) {
                                if (!showLocationPopup) showLocationPopup = true
                                locationQuery = query
                            } else {
                                showLocationPopup = false
                            }
                        }
                    }
                },
                onNewGroupRequested = { currentIndex, currentText ->
                    // 用户输入 "/"：在当前行下方创建新待办容器（新 groupId）
                    val maxGroupId = todoLines.maxOfOrNull { it.groupId } ?: 0
                    val newGroupId = maxGroupId + 1
                    val updatedLines = todoLines.toMutableList()
                    val insertIndex = (currentIndex + 1).coerceAtMost(updatedLines.size)
                    val newLine = TodoLine(groupId = newGroupId, order = insertIndex)
                    updatedLines.add(insertIndex, newLine)
                    todoLines = updatedLines
                },
                onReminderClick = { groupId ->
                    /**
                     * 记录当前正在编辑提醒的分组
                     * showReminderPicker = editingReminderGroupId != null，
                     * 因此赋值即等于"打开 picker"，关闭时（onDismiss / onConfirm）置 null。
                     */
                    editingReminderGroupId = groupId
                },
                /**
                 * 各分组的提醒时间映射：用于 CheckboxEditText 内部渲染提醒徽章/删除按钮
                 * （key=groupId, value=提醒时间戳，null=未设置）
                 */
                groupReminders = groupReminders,
                /**
                 * 删除某分组提醒的回调：直接清空该 groupId 对应的提醒时间
                 * 等价于"清除分组提醒"，与 picker 中"不设置重复"语义不同
                 */
                onReminderDelete = { groupId ->
                    viewModel.clearGroupReminder(groupId)
                },
                /** 当前聚焦行索引变化回调：更新 focusedLineIndex 状态 */
                onFocusedLineChange = { newIndex ->
                    focusedLineIndex = newIndex
                },
                categoryId = categoryId,
                categoryName = currentCategory?.name,
                onCategoryClick = { showCategoryDialog = true },
                onCategoryClear = { viewModel.clearCategory() },
                priority = priority,
                onPriorityChange = { _, newPriority ->
                    viewModel.setPriority(newPriority)
                },
                onSaveClick = { groupId ->
                    /** 单独保存当前分组（不返回列表页） */
                    viewModel.saveGroup(groupId, todoLines)
                },
                /** 图片点击回调（查看大图） */
                onImageClick = { lineIndex, imagePath ->
                    // TODO: 实现图片大图预览功能
                },
                /** 删除某行某张图片的回调 */
                onDeleteImage = { lineIndex, imagePath ->
                    val updatedLines = todoLines.toMutableList()
                    if (lineIndex in updatedLines.indices) {
                        val currentLine = updatedLines[lineIndex]
                        updatedLines[lineIndex] = currentLine.copy(
                            imagePaths = currentLine.imagePaths.filter { it != imagePath }
                        )
                        todoLines = updatedLines
                    }
                },
                /** 删除某行某条语音的回调 */
                onDeleteVoice = { lineIndex, voicePath ->
                    val updatedLines = todoLines.toMutableList()
                    if (lineIndex in updatedLines.indices) {
                        val currentLine = updatedLines[lineIndex]
                        updatedLines[lineIndex] = currentLine.copy(
                            voiceAttachments = currentLine.voiceAttachments.filter { it.path != voicePath }
                        )
                        todoLines = updatedLines
                    }
                },
                /**
                 * 🆕 拖拽状态：传递 CrossLineDragManager 的当前状态给子组件
                 *
                 * 子组件（CheckboxEditRow → DraggableImageAttachment）通过此状态：
                 * - 判断当前图片是否正在被拖拽 (isDragging)
                 * - 判断当前行是否为跨行目标位置 (isDropTarget)
                 * - 渲染对应的视觉反馈（浮层、高亮等）
                 */
                dragState = crossLineDragManager.state,
                /**
                 * 🆕 附件拖拽开始回调
                 *
                 * 当用户长按某张图片触发拖拽时调用，
                 * 通知 CrossLineDragManager 进入拖拽模式。
                 *
                 * @param sourceLineIdx 被拖拽图片所属的行索引
                 * @param sourceImgIdx 被拖拽图片在该行列表中的位置索引
                 */
                onAttachmentDragStart = { sourceLineIdx, sourceImgIdx, imageHeightPx ->
                    /** 传递源行和图片索引到拖拽管理器 */
                    crossLineDragManager.startDrag(sourceLineIdx, sourceImgIdx, imageHeightPx)
                },
                /**
                 * 附件拖拽过程中更新回调
                 *
                 * 当用户拖动手指时持续调用，
                 * 将偏移量同步给 CrossLineDragManager 用于：
                 * 1. 计算当前悬停的目标图片
                 * 2. 判断交换/移动模式
                 * 3. 驱动 UI 的视觉反馈（虚线框/光标）
                 */
                onAttachmentDragUpdate = { dragOffset, fingerX, fingerY, scrollOffsetPx ->
                    /** 获取源行的图片数量 */
                    val sourceLineIdx = crossLineDragManager.state.sourceLineIndex
                    val imageCount = if (sourceLineIdx in todoLines.indices) {
                        todoLines[sourceLineIdx].imagePaths.size
                    } else {
                        0
                    }

                    crossLineDragManager.updateDrag(
                        currentOffset = dragOffset,
                        density = context.resources.displayMetrics.density,
                        imageCount = imageCount,
                        scrollOffsetPx = scrollOffsetPx  // 🆕 滚动偏移补偿
                    )
                },
                /**
                 * 🆕 附件拖拽结束回调
                 *
                 * 当用户释放手指时调用，
                 * 执行以下操作序列：
                 * 1. 调用 CrossLineDragManager.endDrag() 获取拖拽结果
                 * 2. 如果结果有效，调用 applyDragResult() 更新 todoLines 数据
                 * 3. 清理行边界缓存（因为行高可能变化）
                 *
                 * @param sourceLineIdx 源行索引
                 * @param sourceImgIdx 源图片位置索引
                 * @param targetLineIdx 目标行索引（由 DraggableImageAttachment 估算）
                 * @param targetImgIdx 目标图片位置索引（null=追加到末尾）
                 */
                onAttachmentDragEnd = { sourceLineIdx, sourceImgIdx, targetLineIdx, targetImgIdx ->
                    /** 1. 结束拖拽并获取结果 */
                    val result = crossLineDragManager.endDrag()

                    /** 2. 如果拖拽结果有效，执行数据更新 */
                    if (result.isSuccess) {
                        val sourceLine = todoLines.getOrNull(sourceLineIdx)
                        val imagePath = sourceLine?.imagePaths?.getOrNull(sourceImgIdx)

                        if (imagePath != null) {
                            todoLines = crossLineDragManager.applyDragResult(
                                lines = todoLines,
                                result = result,
                                imagePath = imagePath
                            )
                        }
                    }

                    /** 3. 清理行边界缓存 */
                    rowBoundsMap.clear()
                },
                /**
                 * 🆕 行边界更新回调
                 *
                 * CheckboxEditText 内部通过 onGloballyPositioned 捕获每行的 Rect 后，
                 * 通过此回调将边界信息传递给外部，用于精确的目标行检测。
                 *
                 * @param lineIndex 行索引
                 * @param rect 该行在屏幕上的边界矩形
                 */
                onRowBoundsChanged = { lineIndex, rect ->
                    rowBoundsMap[lineIndex] = rect
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                enabled = !isLocked
            )

            if (showLocationPopup) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLocationPopup = false },
                    title = { Text("位置提醒") },
                    text = {
                        Column {
                            Text(
                                text = "输入 # 后可搜索地点设置提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LocationPicker(
                                lat = geofenceLat,
                                lng = geofenceLng,
                                radius = geofenceRadius,
                                type = geofenceType,
                                address = geofenceAddress,
                                enabled = geofenceEnabled,
                                onLocationChange = { lat, lng, address ->
                                    viewModel.setGeofenceLat(lat)
                                    viewModel.setGeofenceLng(lng)
                                    viewModel.setGeofenceAddress(address)
                                },
                                onRadiusChange = { radius ->
                                    viewModel.setGeofenceRadius(radius)
                                },
                                onTypeChange = { type ->
                                    viewModel.setGeofenceType(type)
                                },
                                onEnabledChange = { enabled ->
                                    viewModel.setGeofenceEnabled(enabled)
                                }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLocationPopup = false }) {
                            Text("确定")
                        }
                    }
                )
            }

            // @触发关联选择弹窗
            MentionTriggerPopup(
                visible = showMentionPopup,
                searchQuery = mentionQuery,
                onDismiss = { showMentionPopup = false },
                onCardSelected = { cardType, cardId, cardTitle ->
                    showMentionPopup = false
                    viewModel.addRelation(cardType, cardId)
                },
                searchCards = { query, callback -> viewModel.searchCards(query, callback) },
                sourceType = "todo",
                sourceId = todoId ?: 0L
            )

            val autoDuration = remember(startDate, dueDate) {
                val start = startDate
                val due = dueDate
                if (start != null && due != null && due > start) {
                    val diffMs = due - start
                    val totalMinutes = diffMs / 1000 / 60
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    when {
                        hours > 24 -> "${hours / 24}天${hours % 24}小时"
                        hours > 0 -> "${hours}小时${if (minutes > 0) "${minutes}分" else ""}"
                        minutes > 0 -> "${minutes}分钟"
                        else -> null
                    }
                } else null
            }

            AnimatedVisibility(visible = autoDuration != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱️ 预计时长: $autoDuration",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            /**
             * 推荐提醒时间提示条
             *
             * 【本期禁用】visible 固定为 false，块体不渲染
             *
             * 历史背景：
             * - 旧设计：根据待办标题/截止时间智能推荐提醒时间，显示一个可点击的"接受推荐"横条
             * - 弃用原因：多分组（多卡片）架构下，每个分组独立维护提醒，全局推荐语义不再适用
             *
             * 后续处理：
             * - 如未来重新启用推荐功能，应在分组级 ViewModel 中实现 _recommendedReminderTimes: Map<Int, Long?>
             * - 并在每个 CheckboxEditRow 容器内单独渲染，不再使用全局横条
             * - 本占位块可直接删除，但暂保留以便回退参考
             */
            AnimatedVisibility(visible = false) {
                // 原推荐提示条代码保留作占位，本期不渲染
                // 后续 Task 取消时再删除该区块
                // （原代码引用了 recommendedReminderTime / acceptReminderRecommendation，
                //   因 ViewModel 已移除这些 API，UI 层必须禁用以保持编译通过）
            }
        }

        if (showImagePicker) {
            ImagePickerDialog(
                onCameraSelected = {
                    checkAndRequestCameraPermission(
                        context = context,
                        permissionLauncher = cameraPermissionLauncher,
                        onPermissionGranted = {
                            val photoUri = ImageUtils.createImageUri(context)
                            pendingPhotoUri = photoUri
                            cameraLauncher.launch(photoUri)
                        },
                        onPermissionDenied = {
                            showImagePicker = false
                        }
                    )
                },
                onGallerySelected = {
                    galleryLauncher.launch("image/*")
                },
                onDismiss = { showImagePicker = false }
            )
        }

        if (showColorPicker) {
            ColorPickerBottomSheet(
                sheetState = rememberModalBottomSheetState(),
                selectedColor = rawBackgroundColor,
                onColorSelected = { color ->
                    viewModel.setBackgroundColor(color.toArgb())
                    showColorPicker = false
                },
                onDismiss = { showColorPicker = false }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedDate = datePickerState.selectedDateMillis
                            if (selectedDate != null) {
                                selectedDateMillis = selectedDate
                                showDatePicker = false
                                showStartTimePicker = true
                            }
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("取消")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        if (showStartTimePicker) {
            AlertDialog(
                onDismissRequest = { showStartTimePicker = false },
                title = { Text("选择开始时间") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = selectedDateMillis ?: System.currentTimeMillis()
                            cal.set(Calendar.HOUR_OF_DAY, startTimePickerState.hour)
                            cal.set(Calendar.MINUTE, startTimePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            viewModel.setStartDate(cal.timeInMillis)
                            showStartTimePicker = false
                            selectedDateMillis = null
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showStartTimePicker = false }) {
                        Text("取消")
                    }
                },
                text = {
                    TimePicker(state = startTimePickerState)
                }
            )
        }

        /**
         * 旧"快速设置提醒时间"弹窗（showTimePicker）已整体移除
         *
         * 移除原因：
         * - 该弹窗基于旧"全局 reminderTime"语义（todo 整单只有一个提醒时间），
         *   与多分组（多卡片）架构不兼容
         * - 业务上已经被新的 ReminderPickerBottomSheet（按 groupId 路由）取代
         * - 本文件中没有任何调用点把 showTimePicker 置为 true，整段为死代码
         *
         * 替代入口：
         * - 每个分组的容器内点击"提醒"按钮 → CheckboxEditText.onReminderClick(groupId)
         *   → editingReminderGroupId = groupId → ReminderPickerBottomSheet 自动打开
         */

        if (showDueDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDueDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val selectedDate = dueDatePickerState.selectedDateMillis
                            if (selectedDate != null) {
                                selectedDueDateMillis = selectedDate
                                showDueDatePicker = false
                                showDueTimePicker = true
                            }
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDueDatePicker = false }) {
                        Text("取消")
                    }
                }
            ) {
                DatePicker(state = dueDatePickerState)
            }
        }

        if (showDueTimePicker) {
            AlertDialog(
                onDismissRequest = { showDueTimePicker = false },
                title = { Text("选择截止时间") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = selectedDueDateMillis ?: System.currentTimeMillis()
                            cal.set(Calendar.HOUR_OF_DAY, dueTimePickerState.hour)
                            cal.set(Calendar.MINUTE, dueTimePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            viewModel.setDueDate(cal.timeInMillis)
                            showDueTimePicker = false
                            selectedDueDateMillis = null
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDueTimePicker = false }) {
                        Text("取消")
                    }
                },
                text = {
                    TimePicker(state = dueTimePickerState)
                }
            )
        }

        if (showVoiceRecordSheet) {
            var permissionState by remember { mutableStateOf<RecordAudioPermissionState>(RecordAudioPermissionState.SHOULD_REQUEST) }
            
            RecordAudioPermissionChecker { state ->
                permissionState = state
            }
            
            when (permissionState) {
                RecordAudioPermissionState.GRANTED -> {
                    VoiceRecordBottomSheet(
                        voiceRecorder = voiceRecorder,
                        onSaved = { path, duration ->
                            viewModel.setVoiceNote(path, duration)
                            // 将语音添加到当前聚焦行，而非全局 contentBlocks
                            addVoiceToFocusedLine(path, duration)
                            showVoiceRecordSheet = false
                        },
                        onDismiss = {
                            showVoiceRecordSheet = false
                        }
                    )
                }
                RecordAudioPermissionState.DENIED -> {
                    AlertDialog(
                        onDismissRequest = { showVoiceRecordSheet = false },
                        title = { Text("需要录音权限") },
                        text = { Text("请在系统设置中开启麦克风权限以使用语音备注功能。") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    context.startActivity(openAppSettingsIntent(context))
                                    showVoiceRecordSheet = false
                                }
                            ) {
                                Text("去设置")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showVoiceRecordSheet = false }) {
                                Text("取消")
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }  // end Scaffold content

    // 提醒设置弹窗（位于 Scaffold 外层，遮罩可覆盖 topBar 和 bottomBar）
    // 遮罩无动画，弹窗有淡入缩放动画
    if (showReminderPicker) {
        // 弹窗出现时隐藏键盘
        androidx.compose.ui.platform.LocalFocusManager.current.clearFocus()

        // 全屏半透明遮罩（无动画），点击任意位置关闭
        // 使用 LocalConfiguration 获取屏幕高度 + 自定义 Layout 动态计算弹窗位置，保证 上边缘:下边缘 = 4:1
        // 数学原理：
        //   设屏幕高度=H, 弹窗高度=h, 上方空白=T, 下方空白=B
        //   T/B = 4/1, T + h + B = H → B=(H-h)/5, T=4(H-h)/5
        //   弹窗 y 坐标 = T = 4(H-h)/5
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val screenDensity = androidx.compose.ui.platform.LocalDensity.current
        val screenHeightPx = remember(configuration) {
            with(screenDensity) { configuration.screenHeightDp.dp.toPx().toInt() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 延伸到系统导航栏区域，确保遮罩层完全覆盖底部白条
                .navigationBarsPadding()
                .background(Color(0x99000000))
                // 点击遮罩关闭 picker：清空 editingReminderGroupId，下游 showReminderPicker 自动变 false
                .clickable(onClick = { editingReminderGroupId = null })
        ) {
            // 弹窗容器：使用固定高度 + 自定义 layout 按 4:1 比例定位
            // 关键：弹窗总高度固定为屏幕高度的 80%，三种模式保持一致
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(
                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                        indication = null,
                        onClick = {}
                    )
                    // 自定义布局：固定弹窗高度为屏幕90%，按4:1比例定位
                    .layout { measurable, constraints ->
                        val screenH = screenHeightPx

                        // 固定弹窗高度：屏幕高度的 90%（与参考图一致）
                        val dialogHeight = if (screenH > 0) (screenH * 0.9).toInt() else 900

                        // 按 4:1 比例分配上下空白
                        // T/B = 4/1, T + h + B = H → B=(H-h)/5, T=4(H-h)/5
                        val bottomSpace = (screenH - dialogHeight) / 5
                        val topSpace = bottomSpace * 4

                        // 创建固定高度的约束，让子元素填满这个固定空间
                        val fixedConstraints = constraints.copy(
                            minHeight = dialogHeight,
                            maxHeight = dialogHeight
                        )

                        val placeable = measurable.measure(fixedConstraints)

                        layout(placeable.width, dialogHeight) {
                            placeable.placeRelative(0, topSpace.coerceAtLeast(0))
                        }
                    }
            ) {
                // 弹窗内容：带淡入+缩放动画
                androidx.compose.animation.AnimatedVisibility(
                    visible = true,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                ) {
                    com.corgimemo.app.ui.components.ReminderPickerBottomSheet(
                        /**
                         * 初始日期：取当前正在编辑分组已有的提醒时间戳
                         *  - 已设置：groupReminders[gid] 是该分组的提醒时间
                         *  - 未设置 / 无该 group：fallback 为 null（picker 用今天作为默认日期）
                         */
                        initialDateMillis = editingReminderGroupId?.let { groupReminders[it] },
                        /**
                         * 初始小时：优先取已有提醒的小时部分，没有则用系统当前小时
                         * 用 Calendar 解析时间戳，避免依赖时区敏感的 java.time 转换
                         */
                        initialHour = editingReminderGroupId?.let { gid ->
                            groupReminders[gid]?.let { ts ->
                                Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
                            }
                        } ?: java.time.LocalTime.now().hour,
                        /**
                         * 初始分钟：同上，优先取已有提醒的分钟部分
                         */
                        initialMinute = editingReminderGroupId?.let { gid ->
                            groupReminders[gid]?.let { ts ->
                                Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MINUTE)
                            }
                        } ?: java.time.LocalTime.now().minute,
                        /**
                         * 初始重复类型：从 groupRepeatTypes 取当前分组的值
                         * 没有则 fallback 为 0（不重复）
                         */
                        initialRepeatType = editingReminderGroupId?.let { groupRepeatTypes[it] } ?: 0,
                        /**
                         * 关闭 picker：清空 editingReminderGroupId，
                         * 下游 showReminderPicker 自动变 false
                         */
                        onDismiss = { editingReminderGroupId = null },
                        /**
                         * 确认回调：把 picker 选中的 (date, hour, minute, repeatType) 路由回指定分组
                         *  - gid 来自当前编辑态：picker 打开期间不应被外部改写，但为防御性仍做 null 检查
                         *  - 调用新 API：setGroupReminder / setGroupRepeatType（按 groupId 维度）
                         *  - calendarEnabled 暂不处理（与日历功能相关，本期未接入）
                         */
                        onConfirm = { dateMillis, hour, minute, repeatTypeNew, calendarEnabled ->
                            val gid = editingReminderGroupId ?: return@ReminderPickerBottomSheet
                            viewModel.setGroupReminder(gid, dateMillis ?: System.currentTimeMillis())
                            viewModel.setGroupRepeatType(gid, repeatTypeNew)
                            editingReminderGroupId = null
                        }
                    )
                }
            }
        }
    }

    }  // end outer Box (ensures overlay covers Scaffold topBar/bottomBar)

    /**
     * 优先级选择弹窗
     *
     * 当用户点击容器内的"优先级"按钮时弹出，
     * 提供"无优先级"、"低优先级"、"中优先级"、"高优先级"四个选项。
     * 选择后立即更新对应分组的优先级并关闭弹窗。
     */
    showPriorityDialog?.let { targetGroupId ->
        AlertDialog(
            onDismissRequest = { showPriorityDialog = null },
            title = { Text("选择优先级") },
            text = {
                Column {
                    /** 优先级选项列表 */
                    val options = listOf(
                        Triple(0, "无优先级", androidx.compose.ui.graphics.Color.Gray),
                        Triple(1, "低优先级", com.corgimemo.app.ui.components.PriorityColors.Low),
                        Triple(2, "中优先级", com.corgimemo.app.ui.components.PriorityColors.Medium),
                        Triple(3, "高优先级", com.corgimemo.app.ui.components.PriorityColors.High)
                    )

                    val currentPriority = groupPriorities[targetGroupId] ?: 0

                    options.forEach { (value, label, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setGroupPriority(targetGroupId, value)
                                    showPriorityDialog = null
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 优先级颜色圆点
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (value == 0) androidx.compose.ui.graphics.Color.Transparent else color)
                                    .then(
                                        if (value == 0) {
                                            Modifier.border(
                                                width = 1.dp,
                                                color = androidx.compose.ui.graphics.Color.Gray,
                                                shape = androidx.compose.foundation.shape.CircleShape
                                            )
                                        } else Modifier
                                    )
                            )
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (currentPriority == value) color
                                        else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (currentPriority == value)
                                    androidx.compose.ui.text.font.FontWeight.SemiBold
                                else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // 选中标记
                            if (currentPriority == value) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPriorityDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    /** 分类选择弹窗 */
    if (showCategoryDialog) {
        CategorySelectorDialog(
            categories = categories,
            currentCategoryId = categoryId,
            onDismiss = { showCategoryDialog = false },
            onCategorySelected = { id, name ->
                if (id > 0L) {
                    // 默认/已存在分类
                    viewModel.setCategoryId(id)
                } else {
                    // 自定义分类：异步创建
                    viewModel.createCustomCategory(name) { newId ->
                        viewModel.setCategoryId(newId)
                    }
                }
                showCategoryDialog = false
            }
        )
    }
}

private fun hasRecordAudioPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
}

private fun requestRecordAudioPermission(
    context: Context,
    speechViewModel: SpeechViewModel,
    launcher: androidx.activity.result.ActivityResultLauncher<String>
) {
    if (hasRecordAudioPermission(context)) {
        speechViewModel.startListening()
    } else {
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingIndicator(isListening: Boolean) {
    Icon(
        imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
        contentDescription = "录音中",
        tint = androidx.compose.ui.graphics.Color(0xFFDC2626),
        modifier = Modifier.size(24.dp)
    )
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun RecordingWaveAnimation(isListening: Boolean) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0..4) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(8.dp, if (isListening) (8 + (i * 4) % 24).dp else 8.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun PriorityChip(text: String, priority: Int, isSelected: Boolean, onClick: () -> Unit) {
    val (color, bgColor) = when (priority) {
        2 -> Pair(
            androidx.compose.ui.graphics.Color(0xFFDC2626),
            androidx.compose.ui.graphics.Color(0xFFFFE4E6)
        )
        1 -> Pair(
            androidx.compose.ui.graphics.Color(0xFFD97706),
            androidx.compose.ui.graphics.Color(0xFFFFF3E0)
        )
        else -> Pair(
            androidx.compose.ui.graphics.Color(0xFF16A34A),
            androidx.compose.ui.graphics.Color(0xFFECFDF5)
        )
    }

    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) bgColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(text = text)
    }
}