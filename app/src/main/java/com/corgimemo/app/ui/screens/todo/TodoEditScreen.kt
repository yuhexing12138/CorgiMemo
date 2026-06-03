package com.corgimemo.app.ui.screens.todo

import android.net.Uri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.ui.components.CategorySelector
import com.corgimemo.app.ui.components.KeywordSelectionDialog
import com.corgimemo.app.ui.components.LocationPicker
import com.corgimemo.app.ui.components.MentionTriggerPopup
import com.corgimemo.app.ui.components.RecommendationChip
import com.corgimemo.app.ui.components.SubTaskList
import com.corgimemo.app.ui.components.VoicePlayerComponent
import com.corgimemo.app.ui.components.VoiceRecordBottomSheet
import com.corgimemo.app.ui.components.CornerDecoration
import com.corgimemo.app.ui.components.DecoratedContentBox
import com.corgimemo.app.ui.components.EditToolbar
import com.corgimemo.app.ui.components.ImagePickerDialog /** 图片选择对话框 */
import com.corgimemo.app.ui.components.checkAndRequestCameraPermission /** 检查并请求相机权限 */
import com.corgimemo.app.ui.components.InlineImagePreview /** 内联图片预览 */
import com.corgimemo.app.ui.components.ImagePreviewCarousel /** 多图轮播组件 */
import com.corgimemo.app.ui.components.ColorPickerBottomSheet /** 背景色选择器 */
import com.corgimemo.app.util.ImageUtils /** 图片工具类（相机 URI + 复制到内部存储）*/
import com.corgimemo.app.ui.components.RichTextEditor /** 富文本编辑器 */
import com.corgimemo.app.ui.components.TextFormatToolbar /** 格式化工具栏 */
import com.corgimemo.app.ui.components.RecordAudioPermissionChecker
import com.corgimemo.app.ui.components.RecordAudioPermissionState
import com.corgimemo.app.ui.components.openAppSettingsIntent
import com.corgimemo.app.ui.screens.inspiration.components.ImagePicker
import com.corgimemo.app.ui.screens.inspiration.components.RelationSelector
import com.corgimemo.app.util.VoiceRecorder
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.TodoEditViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val LAZY_MENU_THRESHOLD = 15

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TodoEditScreen(
    navController: NavController,
    todoId: Long? = null,
    viewModel: TodoEditViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    /** 富文本格式化内容（Markdown 字符串），用于恢复编辑器的格式化显示 */
    val contentFormat by viewModel.contentFormat.collectAsState()
    /** Undo/Redo 状态：控制撤销/重做按钮的启用状态 */
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val priority by viewModel.priority.collectAsState()
    val categoryId by viewModel.categoryId.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val recommendedCategory by viewModel.recommendedCategory.collectAsState()
    val hasManuallySelectedCategory by viewModel.hasManuallySelectedCategory.collectAsState()
    val showKeywordSelection by viewModel.showKeywordSelection.collectAsState()
    val extractedKeywords by viewModel.extractedKeywords.collectAsState()
    val isCategoriesLoaded by viewModel.isCategoriesLoaded.collectAsState()
    val startDate by viewModel.startDate.collectAsState()
    val dueDate by viewModel.dueDate.collectAsState()
    val estimatedDurationMinutes by viewModel.estimatedDurationMinutes.collectAsState()
    val repeatType by viewModel.repeatType.collectAsState()

    // 地理围栏相关状态
    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    // 提醒时间相关状态
    val reminderTime by viewModel.reminderTime.collectAsState()
    val recommendedReminderTime by viewModel.recommendedReminderTime.collectAsState()
    val showReminderRecommendation by viewModel.showReminderRecommendation.collectAsState()

    // 子任务相关状态
    val subTasks by viewModel.subTasks.collectAsState()

    // 语音备注相关状态
    val voiceNotePath by viewModel.voiceNotePath.collectAsState()
    val voiceDuration by viewModel.voiceDuration.collectAsState()

    /** 图片路径列表状态 */
    val imagePaths by viewModel.imagePaths.collectAsState()

    val context = LocalContext.current
    val speechViewModel = remember { SpeechViewModel(context) }
    val isListening by speechViewModel.isListening.collectAsState()
    val isProcessing by speechViewModel.isProcessing.collectAsState()
    val speechResult by speechViewModel.resultText.collectAsState()
    val speechError by speechViewModel.errorMessage.collectAsState()

    // 语音录制器和播放器实例
    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer(context) }
    
    // 是否显示语音录制面板
    var showVoiceRecordSheet by remember { mutableStateOf(false) }
    // 是否有录音权限（用于显示录制面板）
    var hasRecordPermission by remember { mutableStateOf(false) }

    /** 图片选择相关状态 */
    var showImagePicker by remember { mutableStateOf(false) } /** 控制图片选择对话框显示 */
    val selectedImageUris by viewModel.imagePaths.collectAsState() /** 从 ViewModel 获取已选图片列表 */
    val coroutineScope = rememberCoroutineScope() /** 协程作用域（用于 launcher 回调中的 suspend 调用） */
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) } /** 临时保存相机拍照的输出 URI（TakePicture 回调只返回 Boolean，不返回 URI） */

    /**
     * 相机拍照 Launcher
     *
     * 使用 ActivityResultContracts.TakePicture() 契约，
     * 拍照成功后将照片 URI 复制到应用内部存储并添加到 ViewModel。
     * 使用 FileProvider URI 兼容 Android 7.0+ 的安全策略。
     *
     * 注意：TakePicture() 的回调参数为 Boolean（表示是否成功），
     * 实际照片 URI 通过 launch() 时传入的 input 参数（即 pendingPhotoUri）获取。
     */
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { isSuccess: Boolean ->
        if (isSuccess) {
            pendingPhotoUri?.let { uri ->
                /** 将拍摄的照片复制到内部存储（压缩+持久化） */
                coroutineScope.launch {
                    val savedPath = com.corgimemo.app.util.ImageUtils.copyUriToInternalStorage(context, uri)
                    savedPath?.let { path -> viewModel.addImagePath(path) }
                }
            }
        }
    }

    /**
     * 相册多选 Launcher
     *
     * 使用 GetMultipleContents() 契约支持一次选择多张图片，
     * 每张图片均复制到内部存储并添加到 ViewModel。
     */
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        /** 异步处理所有选中图片 */
        coroutineScope.launch {
            uris.forEach { uri ->
                val savedPath = ImageUtils.copyUriToInternalStorage(context, uri)
                savedPath?.let { path -> viewModel.addImagePath(path) }
            }
        }
    }

    /**
     * 相机权限请求 Launcher
     *
     * 在启动相机前先请求 CAMERA 权限，
     * 权限授予后才调用 cameraLauncher 启动相机拍照。
     */
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            /** 权限已授予，创建临时 URI 并启动相机 */
            val photoUri = com.corgimemo.app.util.ImageUtils.createImageUri(context)
            pendingPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        } else {
            /** 权限被拒绝，可通过 Snackbar 提示用户（可选）*/
            // TODO: 可在此处显示 Snackbar 引导用户去设置中开启权限
        }
    }

    /** 背景色选择相关状态 */
    var showColorPicker by remember { mutableStateOf(false) } /** 控制背景色选择器显示 */

    /** 背景颜色：从 ViewModel 获取持久化的 ARGB 整数值，转换为 Compose Color */
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val backgroundColor = Color(backgroundColorInt) /** 从数据库加载或使用默认白色 */

    /**
     * 富文本编辑器状态
     * 管理 AnnotatedString 内容和格式状态（粗体/斜体/删除线等）
     * 用于替代原有的普通 OutlinedTextField，支持富文本编辑功能
     */
    val editorState = remember { com.corgimemo.app.ui.components.RichTextEditorState() }

    /** 格式化工具栏显示/隐藏控制 */
    var showFormatToolbar by remember { mutableStateOf(false) }

    /**
     * 初始化富文本编辑器内容
     *
     * 恢复策略（按优先级）：
     * 1. 如果 contentFormat 不为空 → 使用 MarkdownParser.safeParse() 安全解析 Markdown 为 AnnotatedString
     *    （保留完整的粗体/斜体/删除线等格式信息，带异常容错）
     * 2. 如果 contentFormat 为空 → 回退到纯文本 content（无格式）
     *
     * 此逻辑确保用户之前保存的富文本格式在重新打开待办时能正确恢复，
     * 即使数据库中存储了损坏的 Markdown 数据也不会崩溃。
     */
    androidx.compose.runtime.LaunchedEffect(content, contentFormat) {
        /** 避免在用户正在编辑时被外部状态覆盖（仅当编辑器内容与预期不符时才同步） */
        val targetText = if (contentFormat.isNotBlank()) {
            /** 使用安全解析（含校验+异常容错），防止损坏数据导致崩溃 */
            com.corgimemo.app.util.MarkdownParser.safeParse(contentFormat)
        } else {
            /** 回退：使用纯文本内容 */
            androidx.compose.ui.text.AnnotatedString(content)
        }

        if (editorState.textFieldValue.annotatedString != targetText) {
            editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(targetText)
        }
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
    var estimatedHours by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = 9,
        initialMinute = 0,
        is24Hour = true
    )

    /** 截止时间选择器相关状态 */
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

    /** @触发弹窗状态 */
    var showMentionPopup by remember { mutableStateOf(false) }
    /** @搜索关键词状态 */
    var mentionQuery by remember { mutableStateOf("") }
    /** @Undo历史菜单显示状态（长按撤销按钮触发） */
    var showUndoHistoryMenu by remember { mutableStateOf(false) }
    /** @Redo历史菜单显示状态（长按重做按钮触发） */
    var showRedoHistoryMenu by remember { mutableStateOf(false) }
    /** @Undo FAB 长按缩放状态（用于脉冲动画反馈） */
    var isUndoPressing by remember { mutableStateOf(false) }
    /** @Redo FAB 长按缩放状态（用于脉冲动画反馈） */
    var isRedoPressing by remember { mutableStateOf(false) }

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
        viewModel.loadCategories()
    }

    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.resetPoseToDefault()
            // 释放语音录制器和播放器资源
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    LaunchedEffect(estimatedDurationMinutes) {
        val duration = estimatedDurationMinutes
        if (duration != null) {
            val hours = duration / 60
            val minutes = duration % 60
            estimatedHours = if (hours > 0) hours.toString() else ""
            estimatedMinutes = minutes.toString()
        }
    }

    /**
     * V2.7: 监听编辑历史时间线的恢复请求（NavResult API + 完整格式恢复）
     *
     * 当用户在 EditHistoryScreen 点击某个历史条目时：
     * 1. SavedStateHandle["restore_text"] 被写入目标数据（AnnotatedString JSON 或纯文本）
     * 2. 导航返回到本页面（popBackStack）
     * 3. 此 LaunchedEffect 检测到值变化 → 反序列化并填充到编辑器
     * 4. 消费后立即清除 savedStateHandle 中的值（一次性消费）
     *
     * **V2.7 增强**: restore_text 现在包含完整的 AnnotatedString 序列化 JSON，
     * 恢复时保留粗体/斜体/删除线等 SpanStyle 格式信息。
     * 对于旧版本数据（纯文本），自动降级为无格式文本。
     */
    /** 从 NavBackStackEntry 恢复文本内容（跨页面导航保持编辑状态）
     *
     * 使用 remember + LaunchedEffect 模式替代 collectAsState/collectAsStateWithLifecycle，
     * 避免不同 Compose/lifecycle 版本间的 initialValue/initial 参数名兼容性问题 */
    var restoreText by remember { mutableStateOf<String?>(null) }

    /** 订阅 SavedStateHandle 的状态变化 */
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry
            ?.savedStateHandle
            ?.getStateFlow<String?>("restore_text", null)
            ?.collect { data -> restoreText = data }
    }

    /** 当恢复数据到达时，反序列化并填充到编辑器（一次性消费） */
    LaunchedEffect(restoreText) {
        val data = restoreText ?: return@LaunchedEffect
        if (data.isNotBlank()) {
            /**
             * V2.7: 尝试将恢复数据反序列化为完整 AnnotatedString（含 SpanStyle）
             * - 如果数据是有效的 AnnotatedString JSON → 完整还原格式
             * - 如果是纯文本或反序列化失败 → 降级为无格式 AnnotatedString
             */
            val restoredAnnotatedString = try {
                /** 尝试以 JSON 格式解析（含完整 SpanStyle 信息） */
                com.corgimemo.app.util.AnnotatedStringSerializer.deserialize(data)
            } catch (e: Exception) {
                /** 解析失败：可能是旧版本的纯文本数据，直接包装 */
                androidx.compose.ui.text.AnnotatedString(data)
            }

            /** 将目标历史状态填充到编辑器 */
            editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(
                restoredAnnotatedString
            )
            /** 一次性消费：清除 savedStateHandle 中的值，避免重复触发 */
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.remove<String>("restore_text")
        }
    }

    if (speechResult.isNotEmpty()) {
        viewModel.setTitle(speechResult)
        viewModel.triggerRecommendation()
        speechViewModel.startListening()
    }

    if (speechError.isNotEmpty()) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(speechError)
            speechViewModel.resetError()
        }
    }

    Scaffold(
        topBar = {
            Column {
                /** 增强的标题栏 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 返回按钮 */
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = Color(0xFFFF9A5C) // 暖橙色
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    /** 可编辑标题输入框 */
                    OutlinedTextField(
                        value = title,
                        onValueChange = { viewModel.setTitleWithRecommendation(it) },
                        placeholder = { Text("输入待办标题...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9A5C), // 暖橙色聚焦边框
                            unfocusedBorderColor = Color.Transparent, // 未聚焦时无边框
                            cursorColor = Color(0xFFFF9A5C)
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    /** 完成按钮 */
                    Button(
                        onClick = {
                            if (viewModel.saveTodo()) {
                                homeViewModel.setPoseForLoading()
                                homeViewModel.refreshSubTaskProgress()
                                navController.popBackStack()
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9A5C) // 暖橙色
                        ),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "完成",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    /**
                     * 编辑历史时间线入口按钮（V2.5 新增）
                     *
                     * 时钟图标按钮，点击跳转到编辑历史时间线页面。
                     * 提供当前 Todo 的完整编辑历史可视化视图。
                     */
                    IconButton(
                        onClick = {
                            /** V2.6: 传递当前 todoId 到编辑历史页面（按 TodoId 隔离加载） */
                            if (todoId != null && todoId > 0) {
                                navController.navigate(
                                    com.corgimemo.app.ui.navigation.Screen.EditHistory.createRoute(todoId)
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "编辑历史",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                /** 元信息行：时间戳 + 字数统计 */
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 创建时间显示 */
                    todoId?.let { id ->
                        if (id > 0) {
                            val createdAtText = remember(id) {
                                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                // 从 ViewModel 获取创建时间（如果可用）
                                "🕐 创建于 ${sdf.format(Date())}"
                            }
                            Text(
                                text = createdAtText,
                                fontSize = 12.sp,
                                color = Color(0xFF999999)
                            )

                            Spacer(modifier = Modifier.width(16.dp))
                        }
                    }

                    /** 字数统计 */
                    Text(
                        text = "📝 ${content.length} 字",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }

                /** 分割线 */
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            /** 编辑页底部工具栏（含 Undo/Redo 支持） */
            EditToolbar(
                onPhotoClick = {
                    /** 触发图片选择对话框 */
                    showImagePicker = true
                },
                onTextFormatClick = {
                    /** 切换格式工具栏的显示/隐藏 */
                    showFormatToolbar = !showFormatToolbar
                },
                onBoldClick = {
                    /** 推送快照后应用加粗格式（支持撤销） */
                    viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                    com.corgimemo.app.ui.components.applyBoldFormat(editorState)
                },
                onListClick = {
                    /** 推送快照后插入无序列表（支持撤销） */
                    viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                    com.corgimemo.app.ui.components.insertUnorderedList(editorState)
                },
                onTodoClick = {
                    viewModel.addSubTask("新子任务")
                },
                onBackgroundClick = {
                    /** 触发背景色选择器 */
                    showColorPicker = true
                },
                /** Undo/Redo 按钮回调 */
                onUndoClick = {
                    /** 执行撤销：从 Undo 栈恢复上一状态 */
                    val previousState = viewModel.undo()
                    if (previousState != null) {
                        /** 将当前状态推入 Redo 栈（支持重做） */
                        viewModel.pushToRedo(editorState.textFieldValue.annotatedString)
                        /** 恢复编辑器到上一状态 */
                        editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(previousState)
                    }
                },
                onRedoClick = {
                    /** 执行重做：从 Redo 栈恢复被撤销的状态 */
                    val restoredState = viewModel.redo()
                    if (restoredState != null) {
                        /** 将当前状态推回 Undo 栈（支持再次撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        /** 恢复编辑器到被撤销的状态 */
                        editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(restoredState)
                    }
                },
                canUndo = canUndo,
                canRedo = canRedo
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            /** 带装饰角标的内容区域 */
            DecoratedContentBox(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFF97316), // 暖橙色
                size = 24.dp,
                strokeWidth = 3.dp,
                backgroundColor = backgroundColor /** 应用用户选择的背景色 */
            ) {
                /**
                 * 内联图片预览组件
                 * 显示已选择的图片缩略图，支持删除操作
                 * 使用新建的 InlineImagePreview 组件替代原有的 ImagePicker
                 */
                if (selectedImageUris.isNotEmpty()) {
                    ImagePreviewCarousel(
                        imageUris = selectedImageUris,
                        onDelete = { index ->
                            /** 从 ViewModel 移除指定索引的图片 */
                            val updatedList = selectedImageUris.toMutableList()
                            updatedList.removeAt(index)
                            viewModel.reorderImagePaths(updatedList)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                }

                /**
                 * 富文本编辑器区域
                 * 支持 Markdown 格式编辑（粗体、斜体、删除线等）
                 */
                com.corgimemo.app.ui.components.RichTextEditor(
                    state = editorState,
                    onValueChange = { newValue ->
                        /** 同步纯文本内容到 ViewModel（立即，用于实时显示和保存兜底） */
                        viewModel.setContent(newValue.text)
                        editorState.textFieldValue = newValue

                        /**
                         * 防抖同步富文本格式内容到 ViewModel（Markdown 格式）
                         *
                         * 使用 300ms 防抖策略：每次按键取消上一次未完成的导出任务，
                         * 仅在用户停止输入 300ms 后才执行 MarkdownParser.export()，
                         * 显著减少高频编辑时的正则解析开销。
                         */
                        viewModel.scheduleFormatExport(newValue.annotatedString)

                        /** 检测 @ 字符输入，触发关联选择弹窗（保留原有逻辑） */
                        if ("@" in newValue.text && !showMentionPopup) {
                            showMentionPopup = true
                            mentionQuery = ""
                        }
                        if (showMentionPopup) {
                            val atIndex = newValue.text.lastIndexOf("@")
                            if (atIndex >= 0) {
                                mentionQuery = newValue.text.substring(atIndex + 1)
                            } else {
                                showMentionPopup = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp), /** 固定高度，避免高度跳动 */
                    placeholder = "输入详细描述（支持 **粗体**、*斜体*、~~删除线~~ 等格式）",
                    enabled = true,
                    /** 键盘快捷键绑定：Ctrl+Z 撤销 / Ctrl+Y 重做 */
                    onUndo = {
                        val previousState = viewModel.undo()
                        if (previousState != null) {
                            viewModel.pushToRedo(editorState.textFieldValue.annotatedString)
                            editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(previousState)
                        }
                    },
                    onRedo = {
                        val restoredState = viewModel.redo()
                        if (restoredState != null) {
                            viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                            editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(restoredState)
                        }
                    }
                )
            }

            /**
             * 浮动 Undo/Redo 快捷按钮区域
             * 位于编辑器上方，方便用户在编辑过程中快速撤销/重做
             */
            if (canUndo || canRedo) {
                /** FAB 缩放脉冲动画状态（长按时 1.0x → 1.15x 弹性缩放） */
                val undoFabScale by animateFloatAsState(
                    targetValue = if (isUndoPressing) 1.15f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "undoFabScale"
                )
                val redoFabScale by animateFloatAsState(
                    targetValue = if (isRedoPressing) 1.15f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "redoFabScale"
                )

                /** 下拉菜单虚拟化阈值：超过此条目数时使用 LazyColumn */
                val LAZY_MENU_THRESHOLD = 15

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    /** ↩️ 撤销浮动按钮（支持长按显示历史菜单 + 缩放脉冲动画） */
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            /** 单击：执行一次撤销操作 */
                            val previousState = viewModel.undo()
                            if (previousState != null) {
                                viewModel.pushToRedo(editorState.textFieldValue.annotatedString)
                                editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(previousState)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (canUndo) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = undoFabScale
                                scaleY = undoFabScale
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        /** 按下：开始缩放动画 */
                                        isUndoPressing = true
                                        /** 等待释放或长按触发 */
                                        val success = tryAwaitRelease()
                                        isUndoPressing = false
                                        if (!success) {
                                            /** 长按成功：显示撤销历史菜单 */
                                            showUndoHistoryMenu = true
                                        }
                                    }
                                )
                            },
                        content = {
                            androidx.compose.material3.Text(
                                text = "↩",
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    /** ↪️ 重做浮动按钮（支持长按显示历史菜单 + 缩放脉冲动画，与Undo对称） */
                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            /** 单击：执行一次重做操作 */
                            val restoredState = viewModel.redo()
                            if (restoredState != null) {
                                viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                                editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(restoredState)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        contentColor = if (canRedo) MaterialTheme.colorScheme.primary
                                      else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier
                            .size(32.dp)
                            .graphicsLayer {
                                scaleX = redoFabScale
                                scaleY = redoFabScale
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        /** 按下：开始缩放动画 */
                                        isRedoPressing = true
                                        /** 等待释放或长按触发 */
                                        val success = tryAwaitRelease()
                                        isRedoPressing = false
                                        if (!success) {
                                            /** 长按成功：显示重做历史菜单 */
                                            showRedoHistoryMenu = true
                                        }
                                    }
                                )
                            },
                        content = {
                            androidx.compose.material3.Text(
                                text = "↪",
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    )
                }
            }

            /**
             * Undo 历史下拉菜单（支持 LazyColumn 虚拟化）
             * 长按撤销按钮时弹出，显示最近的撤销历史条目
             */
            DropdownMenu(
                expanded = showUndoHistoryMenu,
                onDismissRequest = { showUndoHistoryMenu = false }
            ) {
                /** 获取撤销历史描述列表（倒序：最新的在前） */
                val undoHistory = viewModel.getUndoHistoryDescriptions()
                if (undoHistory.isEmpty()) {
                    /** 空状态提示 */
                    DropdownMenuItem(
                        text = { Text("暂无撤销历史", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { showUndoHistoryMenu = false },
                        enabled = false
                    )
                } else if (undoHistory.size <= LAZY_MENU_THRESHOLD) {
                    /** 小数据量：直接遍历渲染（性能足够） */
                    undoHistory.forEachIndexed { index, (stepCount, preview) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "撤回 ${stepCount + 1} 步",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = preview.ifEmpty { "(空内容)" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                val targetState = viewModel.undoToHistoryStep(index + 1)
                                if (targetState != null) {
                                    viewModel.pushToRedo(editorState.textFieldValue.annotatedString)
                                    editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(targetState)
                                }
                                showUndoHistoryMenu = false
                            }
                        )
                        if (index < undoHistory.lastIndex) {
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                } else {
                    /** 大数据量：使用 LazyColumn 虚拟化渲染 */
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(
                            count = undoHistory.size,
                            key = { index -> "undo_$index" }
                        ) { index ->
                            val (stepCount, preview) = undoHistory[index]
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "撤回 ${stepCount + 1} 步",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = preview.ifEmpty { "(空内容)" },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    val targetState = viewModel.undoToHistoryStep(index + 1)
                                    if (targetState != null) {
                                        viewModel.pushToRedo(editorState.textFieldValue.annotatedString)
                                        editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(targetState)
                                    }
                                    showUndoHistoryMenu = false
                                }
                            )
                            if (index < undoHistory.lastIndex) {
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                }
            }

            /**
             * Redo 历史下拉菜单（支持 LazyColumn 虚拟化，与 Undo 对称）
             * 长按重做按钮时弹出，显示可重做的历史条目
             */
            DropdownMenu(
                expanded = showRedoHistoryMenu,
                onDismissRequest = { showRedoHistoryMenu = false }
            ) {
                /** 获取重做历史描述列表（正序：最早重做的在前） */
                val redoHistory = viewModel.getRedoHistoryDescriptions()
                if (redoHistory.isEmpty()) {
                    /** 空状态提示 */
                    DropdownMenuItem(
                        text = { Text("暂无重做历史", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = { showRedoHistoryMenu = false },
                        enabled = false
                    )
                } else if (redoHistory.size <= LAZY_MENU_THRESHOLD) {
                    /** 小数据量：直接遍历渲染 */
                    redoHistory.forEachIndexed { index, (stepCount, preview) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "重做 ${stepCount + 1} 步",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = preview.ifEmpty { "(空内容)" },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                val targetState = viewModel.redoToHistoryStep(index + 1)
                                if (targetState != null) {
                                    viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                                    editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(targetState)
                                }
                                showRedoHistoryMenu = false
                            }
                        )
                        if (index < redoHistory.lastIndex) {
                            androidx.compose.material3.HorizontalDivider()
                        }
                    }
                } else {
                    /** 大数据量：使用 LazyColumn 虚拟化渲染 */
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(
                            count = redoHistory.size,
                            key = { index -> "redo_$index" }
                        ) { index ->
                            val (stepCount, preview) = redoHistory[index]
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "重做 ${stepCount + 1} 步",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = preview.ifEmpty { "(空内容)" },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                onClick = {
                                    val targetState = viewModel.redoToHistoryStep(index + 1)
                                    if (targetState != null) {
                                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                                        editorState.textFieldValue = androidx.compose.ui.text.input.TextFieldValue(targetState)
                                    }
                                    showRedoHistoryMenu = false
                                }
                            )
                            if (index < redoHistory.lastIndex) {
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    }
                }
            }

            /**
             * 格式化工具栏（条件显示）
             * 当用户点击 EditToolbar 的"📝文本"按钮时切换显示/隐藏
             * 提供完整的富文本格式控制：字体样式、列表、对齐、插入等
             */
            if (showFormatToolbar) {
                com.corgimemo.app.ui.components.TextFormatToolbar(
                    state = editorState,
                    modifier = Modifier.padding(top = 8.dp),
                    onToggleBold = {
                        /** 推送快照后应用加粗格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.applyBoldFormat(editorState)
                    },
                    onToggleItalic = {
                        /** 推送快照后应用斜体格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.applyItalicFormat(editorState)
                    },
                    onToggleUnderline = {
                        /** 推送快照后应用下划线格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.applyUnderlineFormat(editorState)
                    },
                    onToggleStrikethrough = {
                        /** 推送快照后应用删除线格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.applyStrikethroughFormat(editorState)
                    },
                    onInsertUnorderedList = {
                        /** 推送快照后插入无序列表（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.insertUnorderedList(editorState)
                    },
                    onInsertOrderedList = {
                        /** 推送快照后插入有序列表（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.annotatedString)
                        com.corgimemo.app.ui.components.insertOrderedList(editorState)
                    },
                    onInsertTodoItem = { viewModel.addSubTask("新子任务") }
                )
            }

            /** @触发关联选择弹窗 */
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

            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "优先级",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    PriorityChip(
                        text = "低",
                        priority = 0,
                        isSelected = priority == 0,
                        onClick = { viewModel.setPriority(0) }
                    )
                    PriorityChip(
                        text = "中",
                        priority = 1,
                        isSelected = priority == 1,
                        onClick = { viewModel.setPriority(1) }
                    )
                    PriorityChip(
                        text = "高",
                        priority = 2,
                        isSelected = priority == 2,
                        onClick = { viewModel.setPriority(2) }
                    )
                }
            }

            AnimatedVisibility(visible = recommendedCategory != null) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    recommendedCategory?.let { category ->
                        RecommendationChip(
                            category = category,
                            onClick = { viewModel.acceptRecommendation() }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = recommendedCategory == null && title.isNotBlank() && !hasManuallySelectedCategory) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "需要分类",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "请为这条待办选择一个分类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            CategorySelector(
                categories = categories,
                selectedCategoryId = categoryId,
                onCategorySelected = { viewModel.setCategoryId(it) },
                modifier = Modifier.padding(top = 8.dp)
            )

            Button(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                val dateText = if (startDate != null) {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    format.format(Date(startDate!!))
                } else {
                    "选择开始日期时间（可选）"
                }
                Text(text = dateText)
            }

            // 预计完成时长选择
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "预计完成时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = estimatedHours,
                        onValueChange = {
                            estimatedHours = it.filter { char -> char.isDigit() }.take(3)
                            val hours = estimatedHours.toIntOrNull() ?: 0
                            val minutes = estimatedMinutes.toIntOrNull() ?: 0
                            val totalMinutes = hours * 60 + minutes
                            viewModel.setEstimatedDurationMinutes(if (totalMinutes > 0) totalMinutes else null)
                        },
                        label = { Text("小时") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = estimatedMinutes,
                        onValueChange = {
                            val newMinutes = it.filter { char -> char.isDigit() }.take(2)
                            val minutes = newMinutes.toIntOrNull() ?: 0
                            estimatedMinutes = if (minutes < 60) newMinutes else estimatedMinutes
                            val hours = estimatedHours.toIntOrNull() ?: 0
                            val finalMinutes = estimatedMinutes.toIntOrNull() ?: 0
                            val totalMinutes = hours * 60 + finalMinutes
                            viewModel.setEstimatedDurationMinutes(if (totalMinutes > 0) totalMinutes else null)
                        },
                        label = { Text("分钟") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }

            // 截止时间选择区域
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "截止时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showDueDatePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val dueDateText = if (dueDate != null) {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        format.format(Date(dueDate ?: 0L))
                    } else {
                        "点击选择截止时间（可选）"
                    }
                    Text(text = dueDateText)
                }
            }

            // 提醒时间选择区域
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "提醒时间",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    val timeText = if (reminderTime != null) {
                        val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        format.format(Date(reminderTime ?: 0L))
                    } else {
                        "点击选择提醒时间（可选）"
                    }
                    Text(text = timeText)
                }

                // 推荐提醒时间标签
                AnimatedVisibility(visible = showReminderRecommendation) {
                    recommendedReminderTime?.let { time ->
                        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        val timeText = format.format(Date(time))

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
                                text = "\uD83D\uDCA1 推荐提醒：$timeText",
                                fontSize = 14.sp,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }
                }
            }

            // 重复类型选择
            Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                Text(
                    text = "重复",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    val repeatOptions = RepeatTaskManager.getRepeatTypeOptions()
                    repeatOptions.forEach { (type, name) ->
                        RepeatTypeChip(
                            text = name,
                            type = type,
                            isSelected = repeatType == type,
                            onClick = { viewModel.setRepeatType(type) }
                        )
                    }
                }
            }

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

            // 子任务列表
            SubTaskList(
                subTasks = subTasks,
                onAddSubTask = { viewModel.addSubTask(it) },
                onToggleSubTask = { viewModel.toggleSubTaskCompletion(it) },
                onDeleteSubTask = { viewModel.deleteSubTask(it) },
                modifier = Modifier.padding(top = 16.dp)
            )

            // 语音备注区域
            voiceNotePath?.let { path ->
                VoicePlayerComponent(
                    voicePlayer = voicePlayer,
                    filePath = path,
                    totalDuration = voiceDuration,
                    onDelete = {
                        // 删除语音文件
                        File(path).delete()
                        viewModel.clearVoiceNote()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )
            }

            /**
             * 图片选择器组件
             * 用于在待办事项中添加和管理图片
             * 支持拍照、相册选择、点击预览、删除等操作
             */
            com.corgimemo.app.ui.screens.inspiration.components.ImagePicker(
                imagePaths = imagePaths,
                onImagesChange = { newPaths ->
                    /** 图片列表变更时同步到 ViewModel */
                    viewModel.reorderImagePaths(newPaths)
                },
                onImageClick = { index ->
                    /** 点击图片打开全屏预览（TODO: 导航到 ImagePreviewScreen）*/
                    // TODO: 实现导航到图片预览页面
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            /** 关联选择器组件 */
            RelationSelector(
                relations = viewModel.relations.collectAsState().value,
                onRelationAdd = { targetType, targetId -> viewModel.addRelation(targetType, targetId) },
                onRelationDelete = { relationId -> viewModel.deleteRelation(relationId) },
                searchCards = { query, callback -> viewModel.searchCards(query, callback) },
                sourceType = "todo",
                sourceId = todoId ?: 0L,
                cardRelationRepository = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            // 语音备注按钮
            OutlinedButton(
                onClick = { showVoiceRecordSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音备注",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (voiceNotePath != null) "重新录制语音备注" else "添加语音备注"
                )
            }

            Button(
                onClick = {
                    if (viewModel.saveTodo()) {
                        homeViewModel.setPoseForLoading()
                        homeViewModel.refreshSubTaskProgress()
                        navController.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = "保存")
            }
        }
    }

    /**
     * 图片选择对话框
     * 当用户点击工具栏的"📷照片"按钮时显示
     * 提供拍照和从相册选择两种图片来源
     */
    if (showImagePicker) {
        ImagePickerDialog(
            onCameraSelected = {
                /**
                 * 检查并请求相机权限
                 * 权限授予后自动启动相机拍照，
                 * 拍摄结果由 cameraLauncher 回调处理（复制到内部存储 + 添加到 ViewModel）
                 */
                checkAndRequestCameraPermission(
                    context = context,
                    permissionLauncher = cameraPermissionLauncher,
                    onPermissionGranted = {
                        /** 权限已授予，创建临时 URI 并启动相机 */
                        val photoUri = ImageUtils.createImageUri(context)
                        pendingPhotoUri = photoUri
                        cameraLauncher.launch(photoUri)
                    },
                    onPermissionDenied = {
                        /** 权限被拒绝，关闭选择对话框 */
                        showImagePicker = false
                        // TODO: 可在此处显示 Snackbar 提示用户去设置中开启权限
                    }
                )
            },
            onGallerySelected = {
                /**
                 * 打开系统相册选择器（支持多选）
                 * 选择结果由 galleryLauncher 回调处理（逐张复制到内部存储 + 添加到 ViewModel）
                 */
                galleryLauncher.launch("image/*")
            },
            onDismiss = { showImagePicker = false }
        )
    }

    /**
     * 背景色选择器底部面板
     * 当用户点击工具栏的"🎨背景"按钮时显示
     * 提供 12 种预设背景色供用户选择
     */
    if (showColorPicker) {
        ColorPickerBottomSheet(
            sheetState = rememberModalBottomSheetState(),
            selectedColor = backgroundColor,
            onColorSelected = { color ->
                /** 转换为 ARGB Int 并保存到 ViewModel（持久化到数据库） */
                viewModel.setBackgroundColor(color.toArgb())
                showColorPicker = false /** 选择后自动关闭面板 */
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

    /** 截止日期选择对话框 */
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

    /** 截止时间选择对话框 */
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

    if (showKeywordSelection) {
        KeywordSelectionDialog(
            title = title,
            keywords = extractedKeywords,
            categories = categories,
            onConfirm = { keyword, categoryId ->
                if (viewModel.confirmKeywordSelection(keyword, categoryId)) {
                    homeViewModel.setPoseForLoading()
                    homeViewModel.refreshSubTaskProgress()
                    navController.popBackStack()
                }
            },
            onSkip = {
                viewModel.skipKeywordSelection()
                homeViewModel.setPoseForLoading()
                homeViewModel.refreshSubTaskProgress()
                navController.popBackStack()
            },
            onDismiss = { viewModel.cancelKeywordSelection() }
        )
    }

    // 语音录制面板
    if (showVoiceRecordSheet) {
        // 检查权限状态
        var permissionState by remember { mutableStateOf<RecordAudioPermissionState>(RecordAudioPermissionState.SHOULD_REQUEST) }
        
        RecordAudioPermissionChecker { state ->
            permissionState = state
        }
        
        when (permissionState) {
            RecordAudioPermissionState.GRANTED -> {
                // 权限已授予，显示录制面板
                VoiceRecordBottomSheet(
                    voiceRecorder = voiceRecorder,
                    onSaved = { path, duration ->
                        viewModel.setVoiceNote(path, duration)
                        showVoiceRecordSheet = false
                    },
                    onDismiss = {
                        showVoiceRecordSheet = false
                    }
                )
            }
            RecordAudioPermissionState.DENIED -> {
                // 权限被拒绝，引导用户去设置
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
            else -> {
                // 正在请求权限或显示说明，不显示录制面板
            }
        }
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

/**
 * 重复类型选择芯片组件
 *
 * @param text 显示文本
 * @param type 重复类型值
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun RepeatTypeChip(text: String, type: Int, isSelected: Boolean, onClick: () -> Unit) {
    val (color, bgColor) = when (type) {
        com.corgimemo.app.data.repository.RepeatType.DAILY -> Pair(
            androidx.compose.ui.graphics.Color(0xFF2563EB),
            androidx.compose.ui.graphics.Color(0xFFDBEAFE)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKLY -> Pair(
            androidx.compose.ui.graphics.Color(0xFF7C3AED),
            androidx.compose.ui.graphics.Color(0xFFEDE9FE)
        )
        com.corgimemo.app.data.repository.RepeatType.MONTHLY -> Pair(
            androidx.compose.ui.graphics.Color(0xFFDB2777),
            androidx.compose.ui.graphics.Color(0xFFFCE7F3)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKDAYS -> Pair(
            androidx.compose.ui.graphics.Color(0xFF0891B2),
            androidx.compose.ui.graphics.Color(0xFFCFFAFE)
        )
        com.corgimemo.app.data.repository.RepeatType.WEEKENDS -> Pair(
            androidx.compose.ui.graphics.Color(0xFF65A30D),
            androidx.compose.ui.graphics.Color(0xFFECFCCB)
        )
        else -> Pair(
            androidx.compose.ui.graphics.Color(0xFF6B7280),
            androidx.compose.ui.graphics.Color(0xFFF3F4F6)
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