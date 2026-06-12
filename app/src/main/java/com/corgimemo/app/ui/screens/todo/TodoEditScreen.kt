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
    val content by viewModel.content.collectAsState()
    val priority by viewModel.priority.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val dueDate by viewModel.dueDate.collectAsState()
    val estimatedDurationMinutes by viewModel.estimatedDurationMinutes.collectAsState()
    val repeatType by viewModel.repeatType.collectAsState()

    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    val reminderTime by viewModel.reminderTime.collectAsState()
    val recommendedReminderTime by viewModel.recommendedReminderTime.collectAsState()
    val showReminderRecommendation by viewModel.showReminderRecommendation.collectAsState()

    val voiceNotePath by viewModel.voiceNotePath.collectAsState()
    val voiceDuration by viewModel.voiceDuration.collectAsState()

    val imagePaths by viewModel.imagePaths.collectAsState()

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
     * 用于 CrossLineDragManager.detectTargetRow() 精确计算目标行。
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
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0,
        is24Hour = true
    )
    var showReminderPicker by remember { mutableStateOf(false) }

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

    if (todoId != null && todoId > 0) {
        viewModel.loadTodo(todoId)
    }

    LaunchedEffect(Unit) {
        homeViewModel.setPoseForCreating()
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

    /** 从 content 文本和 subTasks 初始化复选框行数据 */
    val subTasks by viewModel.subTasks.collectAsState()
    LaunchedEffect(content, subTasks, hasInitializedLines) {
        if (!hasInitializedLines) {
            todoLines = if (subTasks.isNotEmpty()) {
                // 优先从子任务表恢复结构化数据
                listOf(TodoLine()) + TodoLine.fromSubTasks(subTasks)
            } else if (content.isNotBlank()) {
                // 从纯文本解析
                TodoLine.parseFromText(content).ifEmpty { listOf(TodoLine()) }
            } else {
                listOf(TodoLine())
            }
            hasInitializedLines = true
        }
    }

    /** 行数据变更时同步到 ViewModel 的 content 字段（混合存储） */
    LaunchedEffect(todoLines) {
        if (hasInitializedLines) {
            val plainText = todoLines
                .filter { it.text.isNotBlank() || todoLines.size == 1 }
                .joinToString("\n") { it.toPlainText() }
            viewModel.setContent(plainText)

            // 同步子任务数据到 ViewModel（仅同步有实质内容的行）
            val subTaskLines = todoLines.filter { it.text.isNotBlank() && it.isSubTask }
            subTaskLines.forEach { line ->
                if (line.subTaskId == 0L && line.text.isNotBlank()) {
                    viewModel.addSubTask(line.text)
                }
            }
        }
    }

    LaunchedEffect(todoId) {
        if (!hasInitializedBlocks && todoId != null) {
            val dbBlocks = viewModel.loadContentBlocks(todoId)
            if (dbBlocks.isNotEmpty()) {
                contentBlocks.clear()
                contentBlocks.addAll(dbBlocks)
            } else {
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
                        if (viewModel.saveTodo()) {
                            homeViewModel.setPoseForLoading()
                            homeViewModel.refreshSubTaskProgress()
                            navController.popBackStack()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9A5C)
                    ),
                    modifier = Modifier.height(32.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = "完成",
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
                    showReminderPicker = true
                },
                /** 当前聚焦行索引变化回调：更新 focusedLineIndex 状态 */
                onFocusedLineChange = { newIndex ->
                    focusedLineIndex = newIndex
                },
                priority = priority,
                onPriorityChange = { _, newPriority ->
                    viewModel.setPriority(newPriority)
                },
                onSaveClick = {
                    if (viewModel.saveTodo()) {
                        homeViewModel.setPoseForLoading()
                        homeViewModel.refreshSubTaskProgress()
                        navController.popBackStack()
                    }
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
                onAttachmentDragStart = { sourceLineIdx, sourceImgIdx ->
                    crossLineDragManager.startDrag(sourceLineIdx, sourceImgIdx)
                },
                /**
                 * 🆕 附件拖拽过程中更新回调
                 *
                 * 当用户拖动手指时持续调用，
                 * 将偏移量同步给 CrossLineDragManager 用于：
                 * 1. 更新拖拽模式（INLINE_SORT ↔ CROSS_LINE）
                 * 2. 计算当前悬停的目标行
                 * 3. 驱动 UI 的视觉反馈（目标行高亮等）
                 *
                 * @param dragOffset 当前累积的拖拽偏移量（相对于起始点）
                 * @param fingerY 手指当前 Y 坐标（用于检测目标行）
                 */
                onAttachmentDragUpdate = { dragOffset, fingerY ->
                    /** 调用 CrossLineDragManager.updateDrag() 同步状态 */
                    crossLineDragManager.updateDrag(
                        currentOffset = dragOffset,
                        density = context.resources.displayMetrics.density,
                        rowBounds = rowBoundsMap.values.toList(),  // 传递所有行的边界信息
                        fingerY = fingerY
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
                        /** 获取被拖拽的图片路径 */
                        val sourceLine = todoLines.getOrNull(sourceLineIdx)
                        val imagePath = sourceLine?.imagePaths?.getOrNull(sourceImgIdx)

                        if (imagePath != null) {
                            /** 使用 CrossLineDragManager 的 applyDragResult 方法更新列表 */
                            todoLines = crossLineDragManager.applyDragResult(
                                lines = todoLines,
                                result = result,
                                imagePath = imagePath
                            )
                        }
                    }

                    /** 3. 清理行边界缓存（布局可能已变化）*/
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

            AnimatedVisibility(visible = showReminderRecommendation) {
                recommendedReminderTime?.let { time ->
                    val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                    val recTimeText = format.format(Date(time))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                            .clickable { viewModel.acceptReminderRecommendation() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\uD83D\uDCA1 推荐提醒：$recTimeText",
                            fontSize = 14.sp,
                            color = Color(0xFF1565C0)
                        )
                    }
                }
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

        if (showTimePicker) {
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                title = { Text("选择提醒时间") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = startDate ?: System.currentTimeMillis()
                            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                            cal.set(Calendar.MINUTE, timePickerState.minute)
                            cal.set(Calendar.SECOND, 0)
                            cal.set(Calendar.MILLISECOND, 0)
                            viewModel.setReminderTime(cal.timeInMillis)
                            showTimePicker = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("取消")
                    }
                },
                text = {
                    TimePicker(state = timePickerState)
                }
            )
        }

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
                .clickable(onClick = { showReminderPicker = false })
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
                        initialDateMillis = reminderTime,
                        // 默认使用当前时间
                        initialHour = java.time.LocalTime.now().hour,
                        initialMinute = java.time.LocalTime.now().minute,
                        initialRepeatType = repeatType,
                        onDismiss = { showReminderPicker = false },
                        onConfirm = { dateMillis, hour, minute, repeatTypeNew, calendarEnabled ->
                            viewModel.setReminderTime(dateMillis ?: System.currentTimeMillis())
                            viewModel.setRepeatType(repeatTypeNew)
                            showReminderPicker = false
                        }
                    )
                }
            }
        }
    }

    }  // end outer Box (ensures overlay covers Scaffold topBar/bottomBar)
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