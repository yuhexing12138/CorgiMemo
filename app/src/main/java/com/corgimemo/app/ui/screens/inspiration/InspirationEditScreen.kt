package com.corgimemo.app.ui.screens.inspiration

import android.net.Uri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.repository.RepeatTaskManager
import com.corgimemo.app.ui.components.KeywordSelectionDialog
import com.corgimemo.app.ui.components.LocationPicker
import com.corgimemo.app.ui.components.MentionTriggerPopup
import com.corgimemo.app.ui.components.RecommendationChip
import com.corgimemo.app.ui.components.VoiceRecordBottomSheet
import com.corgimemo.app.ui.components.DeleteConfirmDialog /** 删除确认对话框（防误触）*/
import com.corgimemo.app.ui.components.safeAreaForTopBar /** 安全区域内边距：顶栏状态栏*/
import com.corgimemo.app.ui.components.safeAreaForEditBar /** 安全区域内边距：编辑栏导航栏+软键盘*/
import com.corgimemo.app.ui.components.EditToolbar
import com.corgimemo.app.ui.components.ImagePickerDialog /** 图片选择对话框 */
import com.corgimemo.app.ui.components.checkAndRequestCameraPermission /** 检查并请求相机权限 */
import com.corgimemo.app.ui.components.ColorPickerBottomSheet /** 背景色选择器 */
import com.corgimemo.app.util.ImageUtils /** 图片工具类（相机 URI + 复制到内部存储）*/
import com.corgimemo.app.ui.components.RichTextEditor /** 富文本编辑器 */
import com.corgimemo.app.ui.components.TextFormatToolbar /** 格式化工具栏 */
import com.corgimemo.app.ui.components.RecordAudioPermissionChecker
import com.corgimemo.app.ui.components.RecordAudioPermissionState
import com.corgimemo.app.ui.components.openAppSettingsIntent
import com.corgimemo.app.util.VoiceRecorder
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.InspirationEditViewModel
import com.corgimemo.app.ui.screens.inspiration.components.TagInputField /** 标签输入组件（灵感独有功能）*/
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 内容块定义已提取至 com.corgimemo.app.ui.model.ContentBlock（公共模块），通过 import 复用 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationEditScreen(
    navController: NavController,
    inspirationId: Long? = null,
    viewModel: InspirationEditViewModel = hiltViewModel(),
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

    /** ★★★ 标签列表状态（灵感独有功能）★★★ */
    val tags by viewModel.tags.collectAsState()

    val context = LocalContext.current
    /** 屏幕密度实例，用于 dp→px 精确转换 */
    val density = LocalDensity.current
    /**
     * 语音识别 ViewModel（延迟初始化）
     *
     * 使用 Lazy 避免在组合阶段直接构造 SpeechViewModel，
     * 因为其内部会创建 SpeechRecognizer，在某些设备上可能因
     * 语音识别服务不可用而抛出异常导致闪退。
     * 仅在用户实际触发语音输入时才创建实例。
     */
    val speechViewModel by remember { lazy { com.corgimemo.app.viewmodel.SpeechViewModel(context) } }
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
    var showImagePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    /** 动态内容块列表（文本/图片/语音混合流） */
    val contentBlocks = remember { androidx.compose.runtime.mutableStateListOf<ContentBlock>() }

    /** 两步删除：高亮索引 (-1=无高亮, >=0=对应块高亮待删除) */
    var highlightedIndex by remember { mutableIntStateOf(-1) }

    /**
     * 内容块可见性追踪（Compose 1.9 onVisibilityChanged 懒加载）
     *
     * key = 非Text内容块在 contentBlocks 中的全局索引
     * value = 是否当前在屏幕可见区域内
     *
     * 用途：
     * - 图片块：仅在可见时渲染 AsyncImage（离开视口时显示占位符，减少内存）
     * - 语音块：进入视口时预初始化播放器，离开时暂停释放资源
     */
    val blockVisibilityStates = remember { mutableStateMapOf<Int, Boolean>() }

    /** 锁定编辑状态 */
    var isLocked by remember { mutableStateOf(false) }

    /** 删除确认对话框显示状态（防止误触删除灵感） */
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

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
                coroutineScope.launch {
                    val savedPath = com.corgimemo.app.util.ImageUtils.copyUriToInternalStorage(context, uri)
                    savedPath?.let { path ->
                        viewModel.addImagePath(path)
                        val insertIndex = contentBlocks.size
                        contentBlocks.add(ContentBlock.Image(path))
                        /** 推送插入操作到撤销栈 + 同步 ViewModel */
                        viewModel.pushBlockInsertedOperation(insertIndex)
                        viewModel.syncContentBlocks(contentBlocks.toList())
                    }
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
        coroutineScope.launch {
            uris.forEach { uri ->
                val savedPath = ImageUtils.copyUriToInternalStorage(context, uri)
                savedPath?.let { path ->
                    viewModel.addImagePath(path)
                    val insertIndex = contentBlocks.size
                    contentBlocks.add(ContentBlock.Image(path))
                    /** 推送插入操作到撤销栈 + 同步 ViewModel */
                    viewModel.pushBlockInsertedOperation(insertIndex)
                    viewModel.syncContentBlocks(contentBlocks.toList())
                }
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

    /** 背景颜色选择相关状态 */
    var showColorPicker by remember { mutableStateOf(false) } /** 控制背景色选择器显示 */

    /** 背景颜色：从 ViewModel 获取持久化的 ARGB 整数值，转换为 Compose Color */
    val backgroundColorInt by viewModel.backgroundColor.collectAsState()
    val rawBackgroundColor = Color(backgroundColorInt) /** 从数据库加载或使用默认白色 */

    /**
     * 内容区实际背景色：
     * - 默认白色时 → 使用主题背景色（暖米色），与新建日期/新建灵感页一致
     * - 用户主动选择颜色后 → 使用用户选择的颜色
     */
    val contentBackgroundColor = if (backgroundColorInt == -1 || rawBackgroundColor == Color.White) {
        MaterialTheme.colorScheme.background
    } else {
        rawBackgroundColor
    }

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
     * 此逻辑确保用户之前保存的富文本格式在重新打开灵感时能正确恢复，
     * 即使数据库中存储了损坏的 Markdown 数据也不会崩溃。
     */
    /**
     * 编辑器内容初始化：监听 contentFormat 变化，在数据到达时同步到编辑器
     *
     * 使用 LaunchedEffect(contentFormat) 替代 LaunchedEffect(Unit)，
     * 解决编辑已有灵感时的竞态条件问题：
     *
     * **旧逻辑（有 Bug）**：
     *   LaunchedEffect(Unit) 在页面首次组合时立即执行，
     *   此时 loadInspiration() 尚未完成，contentFormat 仍为空字符串 ""。
     *   编辑器被初始化为空，hasInitialized 被永久锁定为 true。
     *   之后 loadInspiration 完成并更新 contentFormat，但 LaunchedEffect 不再触发，
     *   导致编辑器永远为空！
     *
     * **新逻辑（修复后）**：
     *   LaunchedEffect(contentFormat) 在 contentFormat 值变化时重新执行。
     *   - 新建灵感：contentFormat 始终为 ""，只初始化一次空编辑器
     *   - 编辑灵感：contentFormat 从 "" 变为实际 Markdown 数据时触发初始化
     *   - 通过 hasInitializedWithData 标志确保只同步一次数据库内容
     */
    var hasInitializedWithData by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(contentFormat) {
        try {
            /** 编辑已有灵感：等待 contentFormat 数据到达后再初始化编辑器 */
            if (contentFormat.isNotBlank()) {
                if (!hasInitializedWithData) {
                    val targetText = com.corgimemo.app.util.MarkdownParser.safeParse(contentFormat)

                    /** 防御性检查：确保 editorState.textFieldValue 已正确初始化 */
                    val currentValue = editorState.textFieldValue.value
                    if (currentValue.annotatedString != targetText) {
                        /** 数据库内容加载完毕：同步富文本到 MutableState，光标置于文本末尾 */
                        val textEndPosition = targetText.length
                        editorState.textFieldValue.value = androidx.compose.ui.text.input.TextFieldValue(
                            annotatedString = targetText,
                            selection = androidx.compose.ui.text.TextRange(textEndPosition)
                        )
                    }
                    /** 标记已完成数据初始化（防止用户输入后被数据库旧数据覆盖） */
                    hasInitializedWithData = true
                }
            } else {
                /**
                 * 新建灵感（contentFormat 为空）或数据尚未加载：
                 * 仅在尚未用任何数据初始化过时，使用纯文本 content 初始化空编辑器
                 */
                if (!hasInitializedWithData) {
                    val currentValue = editorState.textFieldValue.value
                    if (currentValue.annotatedString.isEmpty()) {
                        val plainText = androidx.compose.ui.text.AnnotatedString(content)
                        editorState.textFieldValue.value = androidx.compose.ui.text.input.TextFieldValue(
                            annotatedString = plainText,
                            selection = androidx.compose.ui.text.TextRange(plainText.length)
                        )
                    }
                }
            }
        } catch (e: Exception) {
            /** 捕获所有异常，防止编辑器初始化失败导致页面闪退 */
            Log.e("InspirationEditScreen", "编辑器初始化异常（已捕获）", e)
            /** 降级为空编辑器，确保页面可正常显示 */
            hasInitializedWithData = true
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

    /** @触发关联弹窗状态 */
    var showMentionPopup by remember { mutableStateOf(false) }
    /** @搜索关键词状态 */
    var mentionQuery by remember { mutableStateOf("") }
    /** #触发位置提醒弹窗状态 */
    var showLocationPopup by remember { mutableStateOf(false) }
    /** #搜索关键词状态 */
    var locationQuery by remember { mutableStateOf("") }
    /** 添加子任务弹窗状态 */
    var showAddSubtaskDialog by remember { mutableStateOf(false) }

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

    if (inspirationId != null && inspirationId > 0) {
        viewModel.loadInspiration(inspirationId)
    }

    LaunchedEffect(Unit) {
        homeViewModel.setPoseForCreating()
        viewModel.loadCategories()
    }

    DisposableEffect(Unit) {
        onDispose {
            homeViewModel.resetPoseToDefault()
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    /** 初始化已有内容块（优先从 content_blocks 表加载，回退到旧字段） */
    var hasInitializedBlocks by remember { mutableStateOf(false) }
    LaunchedEffect(inspirationId) {
        if (!hasInitializedBlocks && inspirationId != null) {
            /** 优先从独立表加载内容块（新方案） */
            val dbBlocks = viewModel.loadContentBlocks(inspirationId)
            if (dbBlocks.isNotEmpty()) {
                contentBlocks.clear()
                contentBlocks.addAll(dbBlocks)
            } else {
                /** 回退：从旧字段加载（向后兼容） */
                contentBlocks.clear()
                imagePaths.forEach { path ->
                    contentBlocks.add(ContentBlock.Image(path))
                }
                voiceNotePath?.let { path ->
                    contentBlocks.add(ContentBlock.Voice(path, voiceDuration))
                }
            }
            /** 同步到 ViewModel */
            viewModel.syncContentBlocks(contentBlocks.toList())
            hasInitializedBlocks = true
        }
    }

    /** 自动计算预计时长（当开始日期和截止时间都设置后） */
    LaunchedEffect(startDate, dueDate) {
        if (startDate != null && dueDate != null && dueDate!! > startDate!!) {
            val totalMinutes = (dueDate!! - startDate!!) / 1000 / 60
            viewModel.setEstimatedDurationMinutes(totalMinutes.toInt())
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

            /** 将目标历史状态填充到 MutableState */
            editorState.textFieldValue.value = androidx.compose.ui.text.input.TextFieldValue(
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
        /** 初始背景使用 background 暖米色，与新建日期/新建灵感页保持一致 */
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            /** 顶部工具栏：返回 | 撤销/重做 | 通知/锁定 | 完成 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeAreaForTopBar()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /** 返回按钮 */
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

                Spacer(modifier = Modifier.width(4.dp))

                /** 撤销 + 重做（紧凑组） */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val previousState = viewModel.undo()
                            if (previousState != null) {
                                viewModel.pushToRedo(editorState.textFieldValue.value.annotatedString)
                                editorState.textFieldValue.value = androidx.compose.ui.text.input.TextFieldValue(previousState)
                            }
                        },
                        enabled = canUndo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "撤销",
                            tint = if (canUndo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            val restoredState = viewModel.redo()
                            if (restoredState != null) {
                                viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                                editorState.textFieldValue.value = androidx.compose.ui.text.input.TextFieldValue(restoredState)
                            }
                        },
                        enabled = canRedo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "重做",
                            tint = if (canRedo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                /** 通知 + 锁定（紧凑组） */
                Row(verticalAlignment = Alignment.CenterVertically) {
                /** 通知/时间设置（下拉菜单：开始日期 | 截止时间 | 提醒时间） */
                var showTimeMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showTimeMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "时间设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showTimeMenu,
                        onDismissRequest = { showTimeMenu = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("开始日期") },
                            onClick = {
                                showTimeMenu = false
                                showDatePicker = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("截止时间") },
                            onClick = {
                                showTimeMenu = false
                                showDueDatePicker = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("提醒时间") },
                            onClick = {
                                showTimeMenu = false
                                showTimePicker = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )

                        // 分隔线
                        androidx.compose.material3.HorizontalDivider()

                        /** 重复类型选项 */
                        val repeatOptions = RepeatTaskManager.getRepeatTypeOptions()
                        repeatOptions.forEach { (type, name) ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(name)
                                        if (repeatType == type) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                "✓",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setRepeatType(type)
                                    showTimeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.DateRange,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                    IconButton(
                        onClick = { isLocked = !isLocked },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = if (isLocked) "解锁" else "锁定",
                            tint = if (isLocked) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                /** 完成按钮 */
                Button(
                    onClick = {
                        if (viewModel.saveInspiration()) {
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
            /** 编辑页底部工具栏（横排图标版）- 添加imePadding自适应软键盘高度 */
            EditToolbar(
                onFontClick = {
                    /** 切换格式工具栏的显示/隐藏（A/A） */
                    showFormatToolbar = !showFormatToolbar
                },
                onListClick = {
                    /** 插入无序列表 */
                    viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                    com.corgimemo.app.ui.components.insertUnorderedList(editorState)
                },
                onPhotoClick = {
                    /** 触发图片选择对话框 */
                    showImagePicker = true
                },
                onVoiceClick = {
                    /** 打开语音录制面板 */
                    showVoiceRecordSheet = true
                },
                onBackgroundClick = {
                    /** 触发背景色选择器 */
                    showColorPicker = true
                },
                onShareClick = {
                    /** 添加子任务（复用分享图标位） */
                    viewModel.addSubTask("新子任务")
                },
                onDeleteClick = {
                    /** 弹出删除确认对话框（防止误触） */
                    if (inspirationId != null && inspirationId > 0) {
                        showDeleteConfirmDialog = true
                    }
                },
                wordCount = content.length,
                /** 使用 safeAreaForEditBar 适配不同导航模式（手势导航/三键导航）+ 软键盘 */
                modifier = Modifier.safeAreaForEditBar()
            )
        }
    ) { innerPadding ->
        /**
         * 内容区布局：单层Column，Modifier顺序决定背景范围。
         * - background 在 horizontal padding 之前 → 背景铺满全宽无空隙
         * - 不使用 Box 包裹 → 避免触摸事件被外层拦截导致编辑器无法输入
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                /** 背景色铺满全宽（在内容padding之前设置） */
                .background(contentBackgroundColor)
                /** 内容区内边距在背景之后，不影响背景范围 */
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            /** ===== 标题区（独立大标题）===== */
            OutlinedTextField(
                value = title,
                onValueChange = { if (!isLocked) viewModel.setTitleWithRecommendation(it) },
                placeholder = {
                    Text(
                        "标题",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = Color(0xFFFF9A5C)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                enabled = !isLocked
            )

            /** ===== 分类 + 优先级行（标题下方紧凑排列）===== */
            val selectedCategory = categories.find { it.id == categoryId }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showCategoryPicker by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    onClick = { showCategoryPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedCategory?.name ?: "备忘录",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                /** 优先级内联选择 */
                listOf(Pair(0, "低"), Pair(1, "中"), Pair(2, "高")).forEach { (value, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .clickable(enabled = !isLocked) { viewModel.setPriority(value) }
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                        color = if (priority == value) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (priority == value) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                    if (value < 2) Spacer(modifier = Modifier.width(2.dp))
                }

                /** 分类选择弹窗 */
                if (showCategoryPicker && categories.isNotEmpty()) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    com.corgimemo.app.ui.components.CategoryPickerSheet(
                        sheetState = sheetState,
                        categories = categories,
                        currentCategoryId = categoryId,
                        onDismiss = { showCategoryPicker = false },
                        onCategorySelected = { newId ->
                            viewModel.setCategoryId(newId)
                            showCategoryPicker = false
                        }
                    )
                }
            }

            // ★★★ 标签系统（灵感独有功能，保留）★★★
            TagInputField(
                tags = tags,
                onTagsChange = { newTags -> viewModel.updateTags(newTags) },
                modifier = Modifier.fillMaxWidth()
            )

            /** ===== 动态内容流编辑器区域（支持拖拽排序 + 两步删除） ===== */

            /**
             * 使用 ReorderableColumn 包裹内容块列表
             * 支持长按拖拽排序（无可见 DragHandle 图标）
             */
            com.corgimemo.app.ui.components.ReorderableColumn(
                items = contentBlocks.filter { it !is ContentBlock.Text },
                onReorder = { fromIndex, toIndex ->
                    /**
                     * 拖拽排序回调：
                     * 1. 推送旧顺序到撤销栈（支持 Ctrl+Z 恢复）
                     * 2. 更新 contentBlocks 列表顺序
                     * 3. 同步到 ViewModel
                     */
                    val nonTextBlocks = contentBlocks.filter { it !is ContentBlock.Text }.toMutableList()
                    viewModel.pushBlocksReorderedOperation(nonTextBlocks.toList())
                    val moved = nonTextBlocks.removeAt(fromIndex)
                    nonTextBlocks.add(toIndex, moved)

                    /** 重建完整列表（保持 Text 块位置不变） */
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
                /**
                 * Compose 1.9 onVisibilityChanged 懒加载：
                 * 追踪每个非Text块是否在屏幕可见区域内。
                 *
                 * 可见性变化时更新 blockVisibilityStates，
                 * 子组件根据 isVisible 决定是否加载实际资源。
                 */
                val globalBlockIndex = contentBlocks.indexOf(block)
                val isBlockVisible = blockVisibilityStates.getOrDefault(globalBlockIndex, false)

                /** 基础 Modifier：包含可见性追踪 + 拖拽效果 */
                val baseModifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    /** Compose 1.9 onVisibilityChanged：回调直接返回 Boolean（非 VisibilityInfo 对象） */
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
                            /** 仅在可见时加载图片资源；不可见时显示轻量占位符 */
                            isVisible = isBlockVisible
                        )
                    }
                    is ContentBlock.Voice -> {
                        com.corgimemo.app.ui.components.VoicePlayerComponent(
                            voicePlayer = voicePlayer,
                            filePath = block.path,
                            totalDuration = block.duration,
                            onDelete = {
                                /** 通过删除按钮删除时也推入撤销栈 */
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
                            /** 语音块：进入视口时允许播放，离开视口时自动暂停释放资源 */
                            isVisible = isBlockVisible
                        )
                    }
                    is ContentBlock.Text -> { /* 不应进入此分支 */ }
                }
            }

            /** 富文本编辑器（增强版：光标位置感知两步删除 + 扩展撤销） */
            com.corgimemo.app.ui.components.RichTextEditor(
                state = editorState,
                onValueChange = { newValue ->
                    if (!isLocked) {
                        viewModel.setContent(newValue.text)
                        editorState.textFieldValue.value = newValue
                        viewModel.scheduleFormatExport(newValue.annotatedString)

                        /** 任何文本输入都清除高亮状态 */
                        if (highlightedIndex >= 0) {
                            highlightedIndex = -1
                        }

                        /** @触发关联选择弹窗 */
                        val atIndex = newValue.text.lastIndexOf('@')
                        if (atIndex >= 0) {
                            val afterAt = newValue.text.substring(atIndex + 1)
                            if (!afterAt.contains(' ') && !afterAt.contains('\n')) {
                                if (!showMentionPopup) showMentionPopup = true
                                mentionQuery = afterAt
                            } else {
                                showMentionPopup = false
                            }
                        } else {
                            showMentionPopup = false
                        }

                        /** #触发位置提醒弹窗 */
                        val hashIndex = maxOf(
                            newValue.text.lastIndexOf('#'),
                            newValue.text.lastIndexOf('＃')
                        )
                        if (hashIndex >= 0) {
                            val afterHash = newValue.text.substring(hashIndex + 1)
                            if (!afterHash.contains(' ') && !afterHash.contains('\n')) {
                                if (!showLocationPopup) showLocationPopup = true
                                locationQuery = afterHash
                            } else {
                                showLocationPopup = false
                            }
                        } else {
                            showLocationPopup = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        /** 仅处理按下事件，避免重复触发 */
                        if (keyEvent.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }

                        /** 检测键盘删除键（Backspace / Delete） */
                        val isBackspace = keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DEL
                        val isDeleteKey = keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_FORWARD_DEL

                        if (!isBackspace && !isDeleteKey) {
                            return@onPreviewKeyEvent false
                        }

                        /**
                         * 增强两步删除逻辑（光标位置感知）：
                         *
                         * ┌──────────────────────────────────────────────────────┐
                         * │ 条件                      │ 行为                      │
                         * ├──────────────────────────────────────────────────────┤
                         * │ 光标在开头 + Backspace    │ 高亮/删除最后一个非Text块 │
                         * │ 光标在末尾 + Delete      │ 高亮/删除第一个非Text块   │
                         * │ 编辑器为空 + 有块        │ 忽略光标位置直接走删除    │
                         * │ 已高亮 + 再次按键        │ 确认删除                  │
                         * └──────────────────────────────────────────────────────┘
                         */
                        val selection = editorState.textFieldValue.value.selection
                        val textLength = editorState.textFieldValue.value.text.length
                        val cursorAtStart = selection.start == 0 && selection.end == 0
                        val cursorAtEnd = selection.start == textLength && selection.end == textLength
                        val editorEmpty = textLength == 0
                        val hasNonTextBlocks = contentBlocks.any { it !is ContentBlock.Text }

                        /** 无内容块或不符合触发条件时不拦截 */
                        if (!hasNonTextBlocks) {
                            return@onPreviewKeyEvent false
                        }

                        /** 判断是否应该触发删除逻辑 */
                        val shouldTrigger = editorEmpty ||
                            (cursorAtStart && isBackspace) ||
                            (cursorAtEnd && isDeleteKey)

                        if (!shouldTrigger) {
                            return@onPreviewKeyEvent false
                        }

                        /** 已有高亮项 → 第二次按键：确认删除并推入撤销栈 */
                        if (highlightedIndex >= 0) {
                            val deletedBlock = contentBlocks[highlightedIndex]
                            viewModel.pushBlockDeletedOperation(listOf(deletedBlock), highlightedIndex)
                            contentBlocks.removeAt(highlightedIndex)
                            highlightedIndex = -1
                            viewModel.syncContentBlocks(contentBlocks.toList())
                            return@onPreviewKeyEvent true
                        }

                        /** 无高亮项 → 第一次按键：根据按键方向选择目标块 */
                        val targetIndex = when {
                            isBackspace -> {
                                /** Backspace: 从后往前找最后一个非 Text 块 */
                                contentBlocks.indexOfLast { it !is ContentBlock.Text }
                            }
                            isDeleteKey -> {
                                /** Delete: 从前往后找第一个非 Text 块 */
                                contentBlocks.indexOfFirst { it !is ContentBlock.Text }
                            }
                            else -> -1
                        }

                        if (targetIndex >= 0) {
                            highlightedIndex = targetIndex
                            return@onPreviewKeyEvent true
                        }

                        false
                    },
                placeholder = "请在这里输入内容...",
                enabled = !isLocked,
                onUndo = {
                    /** 使用扩展撤销方法（优先处理内容块操作） */
                    val result = viewModel.undoExtended()
                    when (result) {
                        is androidx.compose.ui.text.AnnotatedString -> {
                            /** 文本撤销：恢复编辑器文本 */
                            viewModel.pushToRedo(editorState.textFieldValue.value.annotatedString)
                            editorState.textFieldValue.value =
                                androidx.compose.ui.text.input.TextFieldValue(result)
                        }
                        is Pair<*, *> -> {
                            /** 内容块撤销：重新插入被删的块 */
                            @Suppress("UNCHECKED_CAST")
                            val castResult = result as Pair<List<ContentBlock>, Int>
                            val (blocks, idx) = castResult
                            blocks.forEachIndexed { i, block ->
                                contentBlocks.add(idx + i, block)
                            }
                            viewModel.syncContentBlocks(contentBlocks.toList())
                        }
                        is Int -> {
                            /** 插入撤销：移除该位置的块 */
                            val idx = result as Int
                            if (idx in contentBlocks.indices) {
                                contentBlocks.removeAt(idx)
                                viewModel.syncContentBlocks(contentBlocks.toList())
                            }
                        }
                        is List<*> -> {
                            /** 排序撤销：恢复原排列顺序 */
                            @Suppress("UNCHECKED_CAST")
                            val oldOrder = result as List<ContentBlock>
                            contentBlocks.clear()
                            contentBlocks.addAll(oldOrder)
                            highlightedIndex = -1
                            viewModel.syncContentBlocks(contentBlocks.toList())
                        }
                    }
                },
                onRedo = {
                    /** 使用扩展重做方法 */
                    val result = viewModel.redoExtended()
                    when (result) {
                        is androidx.compose.ui.text.AnnotatedString -> {
                            viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                            editorState.textFieldValue.value =
                                androidx.compose.ui.text.input.TextFieldValue(result)
                        }
                        is Pair<*, *> -> {
                            /** 重做删除：再次删除该块 */
                            @Suppress("UNCHECKED_CAST")
                            val castResult = result as Pair<List<ContentBlock>, Int>
                            val (blocks, idx) = castResult
                            blocks.forEach { block ->
                                val currentIdx = contentBlocks.indexOf(block)
                                if (currentIdx >= 0) contentBlocks.removeAt(currentIdx)
                            }
                            viewModel.syncContentBlocks(contentBlocks.toList())
                        }
                        is Int -> {
                            /** 重做插入：需要从 redo 栈获取被插入的块信息 */
                            // 此处由 pushBlockInsertedOperation 记录的位置辅助处理
                        }
                        is List<*> -> {
                            /** 重做排序：应用新顺序 */
                            // 由 BlocksReordered 的 oldOrder 辅助处理
                        }
                    }
                }
            )

            /** 格式化工具栏（条件显示）
             * 当用户点击 EditToolbar 的"📝文本"按钮时切换显示/隐藏
             * 提供完整的富文本格式控制：字体样式、列表、对齐、插入等
             */
            if (showFormatToolbar) {
                com.corgimemo.app.ui.components.TextFormatToolbar(
                    state = editorState,
                    modifier = Modifier.padding(top = 8.dp),
                    onToggleBold = {
                        /** 推送快照后应用加粗格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                        com.corgimemo.app.ui.components.applyBoldFormat(editorState)
                    },
                    onToggleItalic = {
                        /** 推送快照后应用斜体格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                        com.corgimemo.app.ui.components.applyItalicFormat(editorState)
                    },
                    onToggleUnderline = {
                        /** 推送快照后应用下划线格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                        com.corgimemo.app.ui.components.applyUnderlineFormat(editorState)
                    },
                    onToggleStrikethrough = {
                        /** 推送快照后应用删除线格式（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                        com.corgimemo.app.ui.components.applyStrikethroughFormat(editorState)
                    },
                    onInsertUnorderedList = {
                        /** 推送快照后插入无序列表（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
                        com.corgimemo.app.ui.components.insertUnorderedList(editorState)
                    },
                    onInsertOrderedList = {
                        /** 推送快照后插入有序列表（支持撤销） */
                        viewModel.pushSnapshot(editorState.textFieldValue.value.annotatedString)
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
                sourceType = "inspiration",
                sourceId = inspirationId ?: 0L
            )

            /** #触发位置提醒弹窗 */
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
                                type = geofenceType ?: 0,
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

            /** 添加子任务弹窗 */
            if (showAddSubtaskDialog) {
                var newSubtaskText by remember { mutableStateOf("") }
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showAddSubtaskDialog = false },
                    title = { Text("添加子任务") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = newSubtaskText,
                            onValueChange = { newSubtaskText = it },
                            placeholder = { Text("输入子任务内容...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val text = newSubtaskText.trim()
                                if (text.isNotEmpty()) {
                                    viewModel.addSubTask(text)
                                }
                                showAddSubtaskDialog = false
                            },
                            enabled = newSubtaskText.trim().isNotEmpty()
                        ) {
                            Text("添加")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddSubtaskDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            AnimatedVisibility(visible = recommendedCategory != null) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
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
                                    text = "请为这条灵感选择一个分类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
            }

            /** 自动计算时长显示（根据开始日期和截止时间自动计算） */
            val autoDuration = remember(startDate, dueDate) {
                if (startDate != null && dueDate != null && dueDate!! > startDate!!) {
                    val diffMs = dueDate!! - startDate!!
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

            // 推荐提醒时间标签
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

            } /** 主内容 Column 结束 */

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
            selectedColor = rawBackgroundColor,
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
                        val insertIndex = contentBlocks.size
                        contentBlocks.add(ContentBlock.Voice(path, duration))
                        /** 推送插入操作到撤销栈 + 同步 ViewModel */
                        viewModel.pushBlockInsertedOperation(insertIndex)
                        viewModel.syncContentBlocks(contentBlocks.toList())
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
    } // showVoiceRecordSheet

    /**
     * 删除灵感确认对话框
     *
     * 使用已有的 DeleteConfirmDialog 组件，
     * 用户确认后才执行删除操作并返回上一页。
     */
    DeleteConfirmDialog(
        showDialog = showDeleteConfirmDialog,
        itemTitle = title.ifBlank { "无标题灵感" },
        onConfirm = {
            /** 用户确认删除：执行删除并返回 */
            if (inspirationId != null && inspirationId > 0) {
                viewModel.deleteInspiration(inspirationId)
                navController.popBackStack()
            }
        },
        onDismiss = { showDeleteConfirmDialog = false }
    )
} // main content Column
} // InspirationEditScreen

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
