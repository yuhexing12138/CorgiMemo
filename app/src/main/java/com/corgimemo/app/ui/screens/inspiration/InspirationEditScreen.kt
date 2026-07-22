package com.corgimemo.app.ui.screens.inspiration

import android.net.Uri
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.corgimemo.app.util.toPxFloat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.components.LinkedCardsRow /** v2026-07-22 新增：关联卡片 Chip 流展示组件 */
import com.corgimemo.app.ui.components.LinkedCardPreviewDialog /** v2026-07-22 新增：关联卡片预览弹窗 */
import com.corgimemo.app.ui.components.RelationPickerBottomSheet /** v2026-07-22 新增：多选关联选择 BottomSheet */
import com.corgimemo.app.ui.components.LocationPicker
import com.corgimemo.app.ui.components.VoiceRecordBottomSheet
import com.corgimemo.app.ui.components.DeleteConfirmDialog /** 删除确认对话框（防误触）*/
import com.corgimemo.app.ui.components.DeleteDialogMode /** 删除/放弃确认对话框模式枚举（v2026-07-22 新增）*/
import com.corgimemo.app.ui.components.safeAreaForTopBar /** 安全区域内边距：顶栏状态栏*/
import com.corgimemo.app.ui.components.safeAreaForEditBar /** 安全区域内边距：编辑栏导航栏+软键盘*/
import com.corgimemo.app.ui.components.EditToolbar
import com.corgimemo.app.ui.components.ImagePickerDialog /** 图片选择对话框 */
import com.corgimemo.app.ui.components.checkAndRequestCameraPermission /** 检查并请求相机权限 */
import com.corgimemo.app.ui.components.ColorPickerBottomSheet /** 背景色选择器 */
import com.corgimemo.app.util.ImageUtils /** 图片工具类（相机 URI + 复制到内部存储）*/
import com.corgimemo.app.ui.components.RecordAudioPermissionChecker
import com.corgimemo.app.ui.components.RecordAudioPermissionState
import com.corgimemo.app.ui.components.openAppSettingsIntent
import com.corgimemo.app.util.VoiceRecorder
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.SpeechViewModel
import com.corgimemo.app.viewmodel.InspirationEditViewModel
import com.corgimemo.app.ui.screens.inspiration.components.InspirationEditBottomBar /** 灵感编辑页底部栏（5 按钮 + 可折叠格式工具栏）*/
import com.corgimemo.app.ui.screens.inspiration.components.TagPickerSheet /** 标签选择弹窗组件（灵感独有功能）*/
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.launch
import java.io.File

/** 内容块定义已提取至 com.corgimemo.app.ui.model.ContentBlock（公共模块），通过 import 复用 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
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

    // 地理围栏相关状态
    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    // 子任务相关状态
    val subTasks by viewModel.subTasks.collectAsState()

    // 语音备注相关状态
    val voiceNotePath by viewModel.voiceNotePath.collectAsState()
    val voiceDuration by viewModel.voiceDuration.collectAsState()

    /** 图片路径列表状态 */
    val imagePaths by viewModel.imagePaths.collectAsState()

    /** ★★★ 标签列表状态（灵感独有功能）★★★ */
    val tags by viewModel.tags.collectAsState()
    /** ★ 历史标签列表（从所有灵感聚合去重，用于 TagPickerSheet 快速选择）★ */
    val savedTags by viewModel.savedTags.collectAsState()

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

    /**
     * V2.8.4 新增：保存进行中标志
     *
     * 防止用户连续点击"完成"按钮触发多次保存：
     * - onClick 入口检查 isSaving=true → 直接 return
     * - Button 的 enabled 参数也禁用按钮（视觉反馈）
     * - 保存成功（navigateBack）或异常（snackbar）后 isSaving=false
     */
    var isSaving by remember { mutableStateOf(false) }

    /** 删除确认对话框显示状态（防止误触删除灵感） */
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    /**
     * 删除确认弹窗的当前模式（v2026-07-22 同步 TodoEditScreen 改造）
     *
     * 与 [showDeleteConfirmDialog] 配对使用：开弹窗前先 set 模式，再 show=true。
     * 模式决定弹窗文案和确认后的行为：
     * - [DeleteDialogMode.Delete]：编辑模式弹窗，确认后执行 viewModel.deleteInspiration + navigateBack
     * - [DeleteDialogMode.Discard]：新建模式弹窗，确认后仅 navigateBack（无 DB 数据可删）
     */
    var deleteDialogMode by remember { mutableStateOf(DeleteDialogMode.Delete) }

    /**
     * 返回时"未保存"确认弹窗状态（v2026-07-22 新增）
     *
     * 当用户点击顶部 ← 或触发系统返回键时，若 viewModel.isDirty == true，
     * 则拦截返回并弹 DeleteConfirmDialog (Discard 模式) 询问用户是否真的要放弃未保存内容。
     *
     * 触发链路：
     * 1. 用户点 ← 或按系统返回键 → attemptBack
     * 2. 检查 viewModel.isDirty：
     *    - false → 直接 navigateBack（无内容丢失）
     *    - true → showDiscardConfirm = true（拦截）
     * 3. DeleteConfirmDialog (Discard 模式) 弹窗显示
     * 4. 用户选择：
     *    - 确认放弃 → navigateBack
     *    - 取消 → 仅关闭弹窗
     */
    var showDiscardConfirm by remember { mutableStateOf(false) }

    /**
     * ViewModel 未保存状态（v2026-07-22 新增）
     *
     * 从 viewModel.isDirty StateFlow 派生，UI 层用于判断是否拦截返回。
     * 注意：不直接 read isDirty.value（避免每次重组都查询），用 collectAsState 转 Composable state。
     */
    val isDirty by viewModel.isDirty.collectAsState()

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
     * - 默认状态（未选颜色）→ 透明，不使用主题暖米色背景
     * - 用户主动选择颜色后 → 使用用户选择的颜色
     */
    val contentBackgroundColor = if (backgroundColorInt == -1 || rawBackgroundColor == Color.White) {
        Color.Transparent
    } else {
        rawBackgroundColor
    }

    /**
     * 富文本编辑器状态（compose-rich-editor 库）
     *
     * 使用 rememberRichTextState() 创建，支持：
     * - toggleSpanStyle/toggleCodeSpan/toggleUnorderedList/toggleOrderedList
     * - addLink/setMarkdown/toMarkdown
     *
     * 通过 ViewModel.setRichTextState() 注入到 ViewModel，
     * 以便 ViewModel 调用 setMarkdown()/toMarkdown() 进行持久化。
     */
    val richTextState = rememberRichTextState()

    /** 注入到 ViewModel（LaunchedEffect 确保只注入一次） */
    androidx.compose.runtime.LaunchedEffect(richTextState) {
        viewModel.setRichTextState(richTextState)
    }

    /** 格式工具栏展开/折叠状态（由底部栏 ⋮ 按钮切换） */
    var isFormatExpanded by remember { mutableStateOf(false) }

    /**
     * 编辑器内容初始化：监听 contentFormat 变化，在数据到达时同步到 RichTextState
     *
     * 使用库的 setMarkdown() 恢复完整格式（粗体/斜体/列表/代码块/链接）。
     */
    var hasInitializedWithData by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(contentFormat) {
        try {
            if (contentFormat.isNotBlank() && !hasInitializedWithData) {
                /** 使用库的 setMarkdown 恢复格式 */
                richTextState.setMarkdown(contentFormat)
                hasInitializedWithData = true
            } else if (contentFormat.isBlank() && !hasInitializedWithData) {
                /** 新建灵感：初始化为空 */
                richTextState.setMarkdown("")
                hasInitializedWithData = true
            }
        } catch (e: Exception) {
            Log.e("InspirationEditScreen", "编辑器初始化异常（已捕获）", e)
            hasInitializedWithData = true
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    /**
     * 返回上一页辅助函数（无未保存检查的纯退出）
     *
     * 在 popBackStack 之前设置 savedStateHandle["targetTab"] = "INSPIRE"，
     * 让 MainScreen 接收到返回事件后切换到灵感 tab，
     * 确保从灵感编辑页退出后始终回到灵感页（而非待办页等其他 tab）。
     *
     * 命名说明：navigateBack 是"无脑退出"，不带任何确认；
     * 涉及未保存拦截的"安全返回"请使用 [attemptBack]。
     */
    val navigateBack: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", "INSPIRE")
        navController.popBackStack()
    }

    /**
     * 拦截系统返回事件（侧滑返回 / 系统返回键）
     *
     * v2026-07-22 改造：从直接调用 navigateBack 改为 attemptBack
     * 统一所有退出方式（应用内 ← 按钮、系统返回键）都经过未保存检查
     *
     * 注意：BackHandler 必须在 attemptBack 定义之后调用，
     * 否则 Kotlin 编译器会报 "Unresolved reference 'attemptBack'"。
     * 实际 BackHandler 代码已移至 attemptBack 定义之后。
     */

    /**
     * "安全返回"：检查 viewModel.isDirty，若有未保存修改则弹"放弃编辑"确认框（v2026-07-22 新增）
     *
     * 调用场景：
     * - 顶部 ← 按钮 onClick
     * - BackHandler（系统返回键 / 手势返回）
     * - 完成按钮保存失败后保留在编辑页，用户再点返回时
     *
     * 行为：
     * - isDirty == false → 直接 navigateBack（无内容丢失，无需确认）
     * - isDirty == true → 弹 DeleteConfirmDialog (Discard 模式) 询问，确认后 navigateBack
     *
     * 设计要点：
     * - 不阻塞 UI 线程（isDirty 是 StateFlow 同步读取）
     * - 与 DeleteConfirmDialog 复用同一组件（Discard 模式），保持 UI 一致性
     */
    val attemptBack: () -> Unit = {
        if (isDirty) {
            // 有未保存修改：拦截返回，弹"放弃编辑"确认框
            showDiscardConfirm = true
        } else {
            // 无未保存：直接退出
            navigateBack()
        }
    }

    /**
     * 拦截系统返回事件（实际定义放在 attemptBack 之后以满足 Kotlin val 顺序敏感）
     *
     * v2026-07-22 改造：从直接调用 navigateBack 改为 attemptBack
     * 统一所有退出方式（应用内 ← 按钮、系统返回键）都经过未保存检查
     */
    BackHandler { attemptBack() }
    /** 标签选择弹窗显示状态（由顶部标签按钮触发） */
    var showTagPicker by remember { mutableStateOf(false) }
    /** 待删除的标签（长按标签后弹出确认对话框，null=不显示） */
    var pendingDeleteTag by remember { mutableStateOf<String?>(null) }

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（按当前灵感 id 加载） */
    val relations by viewModel.relations.collectAsState()
    /** 关联ID → 标题映射（由 ViewModel 异步加载并缓存） */
    val relationTitles by viewModel.relationTitles.collectAsState()
    /** 当前预览卡片的详情（供 LinkedCardPreviewDialog 展示） */
    val cardDetail by viewModel.cardDetail.collectAsState()
    /** 卡片详情加载中标志 */
    val cardDetailLoading by viewModel.cardDetailLoading.collectAsState()
    /** 关联预览 Dialog 状态（null=关闭，非null=显示该关联的预览） */
    var previewingRelation by remember { mutableStateOf<CardRelation?>(null) }
    /** 关联选择 BottomSheet 状态 */
    var showRelationPicker by remember { mutableStateOf(false) }

    /**
     * v2026-07-22 新增：监听 previewingRelation 变化，自动加载/清空卡片详情
     *
     * - 非null：用户点击 Chip 弹出 Dialog → 调用 loadCardDetail 异步加载详情
     * - null：用户关闭 Dialog → 调用 clearCardDetail 清空状态
     */
    LaunchedEffect(previewingRelation) {
        val relation = previewingRelation
        if (relation != null) {
            viewModel.loadCardDetail(relation.targetType, relation.targetId)
        } else {
            viewModel.clearCardDetail()
        }
    }
    /**
     * 位置提醒弹窗状态（v2026-07-22 改造）：
     * - 入口从"输入 # 触发"迁移到"点击工具栏 📍 位置按钮"
     * - 不再需要 locationQuery 状态（弹窗打开时无需预填搜索词）
     * - 保留 showLocationPopup 状态即可
     */
    var showLocationPopup by remember { mutableStateOf(false) }
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

    /**
     * 加载已有灵感的标记（防止重复加载）
     *
     * **V2.8.4 关键修复**：原本 `viewModel.loadInspiration(inspirationId)` 是在 Composable
     * 函数体中直接调用的，**每次重组都会重新执行**，触发 `loadInspiration()` 内部协程
     * 用数据库的原始数据**覆盖用户已修改的 `_title.value`/`_content.value`/`_tags.value` 等字段**，
     * 导致用户输入后点击"完成"保存的仍是旧值（看起来"修改不生效"）。
     *
     * 修复方案：
     * 1. 把 loadInspiration 调用从 Composable 函数体移到 LaunchedEffect(inspirationId)
     * 2. 用 hasLoadedInspiration 标志保证仅在编辑模式首次进入时加载一次
     * 3. LaunchedEffect 的 key 用 inspirationId + hasLoadedInspiration，
     *    避免 inspirationId 变化但 hasLoaded 已为 true 时重新加载
     */
    var hasLoadedInspiration by remember(inspirationId) { mutableStateOf(false) }

    LaunchedEffect(inspirationId) {
        if (inspirationId != null && inspirationId > 0 && !hasLoadedInspiration) {
            viewModel.loadInspiration(inspirationId)
            hasLoadedInspiration = true
        }
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

            /** 将恢复的文本填充到 RichTextState（纯文本作为 Markdown 设置） */
            richTextState.setMarkdown(restoredAnnotatedString.text)
            /** 一次性消费：清除 savedStateHandle 中的值，避免重复触发 */
            navController.currentBackStackEntry
                ?.savedStateHandle
                ?.remove<String>("restore_text")
        }
    }

    if (speechResult.isNotEmpty()) {
        viewModel.setTitle(speechResult)
        speechViewModel.startListening()
    }

    if (speechError.isNotEmpty()) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(speechError)
            speechViewModel.resetError()
        }
    }

    Scaffold(
        /** 编辑器区默认透明，不使用主题暖米色背景；
         *  用户可通过背景色选择器自选颜色 */
        containerColor = Color.Transparent,
        topBar = {
            /** 顶部工具栏：返回 | 撤销/重做 | 画板/分享/删除 | 锁定 | 完成 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeAreaForTopBar()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /**
                 * 返回按钮：颜色与尺寸与 TodoEditScreen / EnhancedTopBar 统一
                 *
                 * v2026-07-22 改造：onClick 从 navigateBack 改为 attemptBack
                 * 拦截未保存编辑，避免用户误触 ← 按钮导致草稿丢失
                 */
                IconButton(
                    onClick = attemptBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                /** 撤销 + 重做（紧凑组） */
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            /** 推送当前状态后执行库的 undo */
                            val markdownBefore = richTextState.toMarkdown()
                            viewModel.pushRichTextSnapshot(markdownBefore)
                            richTextState.history.undo()
                        },
                        enabled = canUndo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "撤销",
                            tint = if (canUndo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            /** 执行库的 redo */
                            richTextState.history.redo()
                        },
                        enabled = canRedo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "重做",
                            tint = if (canRedo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                /** ===== 从底部工具栏移入的 3 个按钮（锁按钮左侧，大小与撤销/重做/锁定一致）===== */

                /** 画板按钮：触发背景色选择器 */
                IconButton(
                    onClick = { showColorPicker = true },
                    enabled = !isLocked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = "背景色",
                        tint = if (!isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                /** 分享按钮：添加子任务（复用分享图标位） */
                IconButton(
                    onClick = { viewModel.addSubTask("新子任务") },
                    enabled = !isLocked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "添加子任务",
                        tint = if (!isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                /**
                 * 删除按钮：弹出删除/放弃确认对话框
                 *
                 * v2026-07-22 同步 TodoEditScreen 改造：
                 * - 旧行为：if (inspirationId != null && inspirationId > 0) 才执行，
                 *   新建模式（inspirationId == null）下点击垃圾桶完全无反应
                 * - 新行为：去掉 if 条件，新建模式点击也开弹窗（走 Discard 模式），
                 *   弹窗提示"放弃编辑？未保存内容将永久丢失"
                 * - 二次确认：先 set deleteDialogMode，再 showDeleteConfirmDialog = true
                 */
                IconButton(
                    onClick = {
                        // 根据当前是否有持久化的 inspirationId 决定弹窗模式
                        val isEditMode = inspirationId != null && inspirationId > 0
                        deleteDialogMode = if (isEditMode) {
                            DeleteDialogMode.Delete
                        } else {
                            DeleteDialogMode.Discard
                        }
                        showDeleteConfirmDialog = true
                    },
                    enabled = !isLocked,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = if (!isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                /** 锁定按钮 */
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

                Spacer(modifier = Modifier.width(8.dp))

                /** 完成按钮 */
                Button(
                    onClick = {
                        /**
                         * V2.8.4 关键修复：coroutineScope.launch 等待 saveInspiration() 真正完成
                         *
                         * 之前 saveInspiration() 是 fire-and-forget：
                         * 1. 同步返回 true
                         * 2. navigateBack() 立即执行
                         * 3. ViewModel.onCleared() 可能取消 viewModelScope
                         * 4. performSave 协程被中途取消 → 数据丢失
                         *
                         * 现在 saveInspiration() 是 suspend 函数，UI 层必须用 launch 启动并 await，
                         * 确保数据库 update/insert 全部完成后再返回。
                         *
                         * 防重复点击：保存期间禁用按钮（isSaving=true），
                         * 防止用户连续点击触发多次保存。
                         */
                        if (isSaving) return@Button
                        isSaving = true
                        coroutineScope.launch {
                            try {
                                if (viewModel.saveInspiration()) {
                                    homeViewModel.setPoseForLoading()
                                    homeViewModel.refreshSubTaskProgress()
                                    navigateBack()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("InspirationEditScreen", "保存失败", e)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("保存失败：${e.message ?: "未知错误"}")
                                }
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving,
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
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            /** 灵感编辑页底部导航栏（6 按钮 + 可折叠格式工具栏） */
            InspirationEditBottomBar(
                isFormatExpanded = isFormatExpanded,
                richTextState = richTextState,
                onPhotoClick = {
                    showImagePicker = true
                },
                onVoiceClick = {
                    showVoiceRecordSheet = true
                },
                /**
                 * v2026-07-22 改造：原"位置"按钮改为"标签"按钮
                 * - onLocationClick 拆分为 onTagClick
                 * - 触发灵感独有功能 TagPickerSheet（添加/编辑标签）
                 * - 复用 showTagPicker 状态
                 */
                onTagClick = {
                    showTagPicker = true
                },
                /**
                 * v2026-07-22 改造：@按钮由 MentionTriggerPopup 升级为 RelationPickerBottomSheet
                 * - 与待办编辑页 @ 按钮行为完全一致
                 * - 复用 showRelationPicker 状态（顶部"关联"按钮也使用此状态）
                 */
                onMentionClick = {
                    showRelationPicker = true
                },
                /**
                 * v2026-07-22 新增：独立的位置按钮
                 * - 触发位置提醒弹窗（LocationPicker + Geofence）
                 * - 复用 showLocationPopup 状态
                 */
                onLocationClick = {
                    showLocationPopup = true
                },
                onFormatToggleClick = {
                    /** 只有 ⋮ 按钮切换工具栏展开/折叠 */
                    isFormatExpanded = !isFormatExpanded
                },
                onToggleBold = {
                    /** 推送快照后应用加粗（支持撤销） */
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold))
                },
                onToggleItalic = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic))
                },
                onToggleUnderline = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                },
                onToggleStrikethrough = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                },
                onInsertUnorderedList = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleUnorderedList()
                },
                onInsertOrderedList = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleOrderedList()
                },
                onAlignLeft = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleParagraphStyle(
                        androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Start)
                    )
                },
                onAlignCenter = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleParagraphStyle(
                        androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Center)
                    )
                },
                onAlignRight = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleParagraphStyle(
                        androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.End)
                    )
                },
                onInsertLink = {
                    /** 简化实现：为当前选区插入示例链接，后续可扩展为弹窗输入 */
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.addLinkToSelection(url = "https://example.com")
                },
                onToggleCodeSpan = {
                    val markdownBefore = richTextState.toMarkdown()
                    viewModel.pushRichTextSnapshot(markdownBefore)
                    richTextState.toggleCodeSpan()
                },
                modifier = Modifier.safeAreaForEditBar()
            )
        }
    ) { innerPadding ->
        /**
         * 内容区布局：单层Column，Modifier顺序决定背景范围。
         * - background 在 horizontal padding 之前 → 用户自选背景色铺满全宽无空隙
         * - 默认透明（Color.Transparent），仅用户主动选择颜色时显示背景
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    cursorColor = Color(0xFFFF9A5C)
                ),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                enabled = !isLocked
            )

            /** ===== 标签区（标题下方：标签按钮 + 关联按钮 + 已添加标签展示 + 关联 Chip 流）===== */
            Column(modifier = Modifier.padding(top = 8.dp)) {
                /** 按钮行：标签按钮 + 关联按钮（v2026-07-22 新增 +关联 按钮） */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 标签按钮：点击触发标签选择弹窗 */
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        enabled = !isLocked,
                        onClick = { showTagPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加标签",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "标签",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    /** v2026-07-22 新增：关联按钮，点击弹出关联选择 BottomSheet */
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        enabled = !isLocked,
                        onClick = { showRelationPicker = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加关联",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "关联",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                /** 已添加标签展示（FlowRow 流式布局；点击进入弹窗，长按删除） */
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tags.forEach { tag ->
                            Box(
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = { showTagPicker = true },
                                        onLongClick = { pendingDeleteTag = tag }
                                    )
                                    .background(
                                        color = Color(0xFFFFF3E0),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFF9A5C)
                                )
                            }
                        }
                    }
                }

                /** v2026-07-22 新增：关联卡片 Chip 流展示（位于标签 FlowRow 下方） */
                LinkedCardsRow(
                    relations = relations,
                    groupId = 0,
                    relationTitles = relationTitles,
                    onAddClick = { showRelationPicker = true },
                    onChipClick = { relation -> previewingRelation = relation },
                    onChipDelete = { relationId, _ -> viewModel.deleteRelation(relationId) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

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
                 *
                 * V2.8 调整：图片块（InlineImagePreview）已移除懒加载避免占位符问题，
                 * 可见性追踪当前仅服务于语音块（VoicePlayerComponent）。
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
                            isHighlighted = index == highlightedIndex
                            /** V2.8: 移除 isVisible 参数，图片始终渲染避免滚动时变成占位符 */
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

            /** 富文本编辑器（使用 compose-rich-editor 库） */
            RichTextEditor(
                state = richTextState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .onPreviewKeyEvent { keyEvent ->
                        /** 仅处理按下事件 */
                        if (keyEvent.nativeKeyEvent.action != AndroidKeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }

                        val isBackspace = keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DEL
                        val isDeleteKey = keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_FORWARD_DEL

                        if (!isBackspace && !isDeleteKey) {
                            return@onPreviewKeyEvent false
                        }

                        /** 两步删除逻辑（保留原有内容块删除能力） */
                        val selection = richTextState.selection
                        val textLength = richTextState.annotatedString.length
                        val cursorAtStart = selection.start == 0 && selection.end == 0
                        val cursorAtEnd = selection.start == textLength && selection.end == textLength
                        val editorEmpty = textLength == 0
                        val hasNonTextBlocks = contentBlocks.any { it !is ContentBlock.Text }

                        if (!hasNonTextBlocks) {
                            return@onPreviewKeyEvent false
                        }

                        val shouldTrigger = editorEmpty ||
                            (cursorAtStart && isBackspace) ||
                            (cursorAtEnd && isDeleteKey)

                        if (!shouldTrigger) {
                            return@onPreviewKeyEvent false
                        }

                        /** 已有高亮项 → 第二次按键：确认删除 */
                        if (highlightedIndex >= 0) {
                            val deletedBlock = contentBlocks[highlightedIndex]
                            viewModel.setContentBlockOperating(true)
                            viewModel.pushBlockDeletedOperation(listOf(deletedBlock), highlightedIndex)
                            contentBlocks.removeAt(highlightedIndex)
                            highlightedIndex = -1
                            viewModel.syncContentBlocks(contentBlocks.toList())
                            viewModel.setContentBlockOperating(false)
                            return@onPreviewKeyEvent true
                        }

                        /** 无高亮项 → 第一次按键：高亮目标块 */
                        val targetIndex = when {
                            isBackspace -> contentBlocks.indexOfLast { it !is ContentBlock.Text }
                            isDeleteKey -> contentBlocks.indexOfFirst { it !is ContentBlock.Text }
                            else -> -1
                        }

                        if (targetIndex >= 0) {
                            highlightedIndex = targetIndex
                            return@onPreviewKeyEvent true
                        }

                        false
                    },
                placeholder = {
                    Text(
                        text = "请在这里输入内容...",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                readOnly = isLocked,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = RichTextEditorDefaults.richTextEditorColors(
                    /** 容器背景透明，跟随全局主题色 */
                    containerColor = Color.Transparent,
                    /** 移除底部指示线（边界线） */
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                )
            )

            /** 监听 RichTextState 文本变化：同步到 ViewModel + 触发防抖导出 */
            androidx.compose.runtime.LaunchedEffect(richTextState.annotatedString) {
                if (hasInitializedWithData) {
                    val currentText = richTextState.annotatedString.text
                    viewModel.setContent(currentText)
                    viewModel.scheduleFormatExport(richTextState.annotatedString)

                    /** 清除高亮状态 */
                    if (highlightedIndex >= 0) {
                        highlightedIndex = -1
                    }

                    /**
                     * v2026-07-22 改造：移除 @ 和 # 输入触发弹窗的逻辑
                     * - @ 关联功能迁移到工具栏 @ 按钮（RelationPickerBottomSheet 多选弹窗）
                     * - # 位置功能迁移到工具栏 📍 位置按钮（LocationPicker 弹窗）
                     * - 标签功能由工具栏 # 按钮（TagPickerSheet）触发
                     * - 输入 @ 或 # 字符不再自动弹窗，避免与"普通文本中的 @ #"语义冲突
                     */
                }
            }

            /**
             * 位置提醒弹窗（v2026-07-22 改造）
             * - 入口从"输入 # 触发"迁移到"点击工具栏 📍 位置按钮"
             * - 弹窗打开时不再预填搜索词（已移除 locationQuery 状态）
             */
            if (showLocationPopup) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLocationPopup = false },
                    title = { Text("位置提醒") },
                    text = {
                        Column {
                            Text(
                                text = "开启后将在到达/离开指定位置时提醒此灵感",
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

    /** 标签选择弹窗（类似待办编辑页的分组弹窗） */
    if (showTagPicker) {
        val tagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        TagPickerSheet(
            sheetState = tagSheetState,
            tags = tags,
            savedTags = savedTags,
            onTagsChange = { newTags -> viewModel.updateTags(newTags) },
            onDismiss = { showTagPicker = false }
        )
    }

    // v2026-07-22 新增：关联选择 BottomSheet（多选模式，跨待办/灵感/日期三种类型）
    if (showRelationPicker) {
        // 排除已关联的卡片，避免重复添加
        val excludeIds = relations
            .map { it.targetType to it.targetId }
            .toSet()
        RelationPickerBottomSheet(
            visible = true,
            excludeIds = excludeIds,
            onDismiss = { showRelationPicker = false },
            onConfirm = { selectedCards ->
                // 批量添加选中的关联（编辑页 ViewModel 已知当前 inspirationId）
                val cards = selectedCards.map { it.cardType to it.cardId }
                viewModel.addRelations(cards)
                showRelationPicker = false
            },
            searchCards = { query, callback -> viewModel.searchCards(query, callback) }
        )
    }

    // v2026-07-22 新增：关联预览 Dialog（点击 Chip 后弹出，按类型差异化展示详情）
    previewingRelation?.let { relation ->
        LinkedCardPreviewDialog(
            relation = relation,
            cardDetail = cardDetail,
            isLoading = cardDetailLoading,
            onDismiss = { previewingRelation = null },
            onUnlink = { relationId ->
                viewModel.deleteRelation(relationId)
                previewingRelation = null
            },
            onJumpToDetail = { cardType, cardId ->
                // 根据卡片类型路由到对应详情/编辑页（压栈跳转，返回时回到灵感编辑页）
                when (cardType) {
                    "todo" -> navController.navigate("todo_edit/$cardId")
                    "inspiration" -> navController.navigate("inspiration_edit/$cardId")
                    "date" -> navController.navigate("date_detail/$cardId")
                }
                previewingRelation = null
            }
        )
    }

    /** 标签长按删除确认对话框（防误触） */
    pendingDeleteTag?.let { targetTag ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTag = null },
            title = { Text("删除标签", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    text = "确定要删除标签「#$targetTag」吗？",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateTags(tags - targetTag)
                        pendingDeleteTag = null
                    }
                ) {
                    Text(
                        text = "删除",
                        color = Color(0xFFDC2626)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTag = null }) {
                    Text("取消")
                }
            }
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
     * 删除/放弃确认对话框
     *
     * v2026-07-22 首次新增：垃圾桶二次确认，防止误删
     * v2026-07-22 同步 TodoEditScreen 升级：支持 [DeleteDialogMode.Discard] 模式，
     *     覆盖新建灵感（inspirationId == null）的"放弃编辑"场景
     *
     * 触发链路：
     * 1. 用户点击顶部垃圾桶 → onClick：
     *    - 编辑模式（inspirationId != null）→ deleteDialogMode = Delete
     *    - 新建模式（inspirationId == null）→ deleteDialogMode = Discard
     *    → 然后 showDeleteConfirmDialog = true
     * 2. 弹窗根据 deleteDialogMode 渲染不同文案
     * 3. 用户选择：
     *    - Delete 模式确认 → viewModel.deleteInspiration(inspirationId) + navigateBack
     *    - Discard 模式确认 → 仅 navigateBack（无 DB 操作）
     *    - 取消/遮罩/返回键 → 仅关闭弹窗
     */
    DeleteConfirmDialog(
        showDialog = showDeleteConfirmDialog,
        itemTitle = title.ifBlank { "无标题灵感" },
        mode = deleteDialogMode,
        onConfirm = {
            // 1. 先关闭弹窗（避免 navigateBack 时弹窗仍在屏幕上闪烁）
            showDeleteConfirmDialog = false
            // 2. 根据 mode 走不同分支
            when (deleteDialogMode) {
                DeleteDialogMode.Delete -> {
                    // 删除模式：二次校验 inspirationId 有效性后真正删除 + 返回
                    val targetId = inspirationId
                    if (targetId != null && targetId > 0) {
                        viewModel.deleteInspiration(targetId)
                        navigateBack()
                    }
                }
                DeleteDialogMode.Discard -> {
                    // 放弃编辑模式：直接关闭页面，丢弃未保存草稿
                    // 不调用任何 viewModel 方法，因为新建灵感尚未持久化到 DB
                    navigateBack()
                }
            }
        },
        onDismiss = {
            // 取消路径（点遮罩/返回键/取消按钮）：仅关闭弹窗，不修改数据
            showDeleteConfirmDialog = false
        }
    )

    /**
     * 放弃编辑确认弹窗（v2026-07-22 新增）
     *
     * 当用户从灵感编辑页触发返回（顶部 ← / 系统返回键 / 手势返回）时，
     * 若 viewModel.isDirty == true，弹此弹窗询问用户是否真的要放弃未保存内容。
     *
     * 复用 DeleteConfirmDialog 的 Discard 模式：
     * - 弹窗标题"放弃编辑"，按钮"放弃编辑"
     * - 警告"未保存的内容将永久丢失，无法恢复"
     * - 不显示 itemTitle 高亮（因为未保存内容没有"标题"概念）
     *
     * onConfirm 行为：仅 navigateBack（无 DB 操作）
     * onDismiss 行为：仅关闭弹窗，留在编辑页
     */
    DeleteConfirmDialog(
        showDialog = showDiscardConfirm,
        itemTitle = "",
        mode = DeleteDialogMode.Discard,
        onConfirm = {
            // 1. 先关闭弹窗
            showDiscardConfirm = false
            // 2. 执行返回（无 DB 操作，直接关闭页面）
            navigateBack()
        },
        onDismiss = {
            // 取消路径：仅关闭弹窗，留在编辑页
            showDiscardConfirm = false
        }
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
