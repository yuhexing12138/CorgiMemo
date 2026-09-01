package com.corgimemo.app.ui.screens.inspiration

import android.net.Uri
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.mutableStateOf
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
import com.corgimemo.app.data.model.CardSearchResult /** v2026-08-01 Phase 3：@ Trigger 搜索结果数据类 */
import com.corgimemo.app.ui.components.AppSnackbarHost
/**
 * v2026-08-01 Phase 3：以下 import 已移除（关联改为 @ Trigger 内联插入）
 * - LinkedCardsRow（关联 Chip 流展示，改用 @ atomic token）
 * - LinkedCardPreviewDialog（关联预览弹窗，已移除）
 * - RelationPickerBottomSheet（多选关联 BottomSheet，改用 TriggerSuggestions）
 */
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
import com.corgimemo.app.ui.screens.inspiration.components.InspirationImageGallery /** 灵感专用的沉浸式全屏图片画廊（编辑态预览复用） */
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils /** v2026-07-31 新增：标题与正文之间"时间戳+字数"行所需的字数统计工具 */
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.RichSpanStyle
import com.mohamedrejeb.richeditor.model.trigger.Trigger
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.ui.material3.TriggerSuggestions
import com.mohamedrejeb.richeditor.model.LocalImageLoader
import com.mohamedrejeb.richeditor.model.LocalTokenClickHandler
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.TokenClickHandler
import com.corgimemo.app.ui.screens.inspiration.components.BodyBlocksEditor
import com.corgimemo.app.ui.screens.inspiration.components.rememberBodyBlocksController
import com.corgimemo.app.ui.components.CoilRichTextImageLoader
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 内容块定义已提取至 com.corgimemo.app.ui.model.ContentBlock（公共模块），通过 import 复用 */

/**
 * v2026-09-01 路线 4：图片已改为**块级**（BodyBlocksController.insertImageAtFocused）。
 * 本注释块原本挂在已删除的 `insertBlockImage`（路线 2 内联图片方案）上方，
 * 路线 4 不再涉及内联渲染与 ▢ 占位字符，故整段删除。
 */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class, ExperimentalRichTextApi::class)
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
    /**
     * Undo/Redo 状态：控制撤销/重做按钮的启用状态
     *
     * v2026-09-01 路线 4：改用**当前聚焦块**的库内历史（VM 级 Undo 栈不再作用于正文），
     * 因此不再从 viewModel.canUndo/canRedo 取值，按钮处直接读
     * `richTextState.history.canUndo / canRedo`（快照状态，可观察）。
     */

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
    /** ★ 历史标签列表（从所有灵感聚合去重，用于 TriggerSuggestions 快速选择）★ */
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

    /**
     * v2026-08-30 内联媒体：token 点击处理（图片查看 / 语音播放）
     *
     * 刻意声明在函数体顶层，而非 Scaffold 内容 lambda 内部：
     * 底部的内联图片查看器 Dialog 位于同一顶层作用域，需要读写该状态；
     * 若声明在嵌套 lambda 内，Dialog 处会因作用域不可见而报 Unresolved reference。
     *
     * v2026-08-31 起职责收窄：
     * - "image" 分支仅兼容旧数据（历史 markdown 中可能残留 trigger:image token）；
     *   新插入的图片是 RichSpanStyle.Image（覆盖层绘制），点击走 LocalImageClickHandler。
     * - "voice" 分支仍然有效：语音是 trigger:voice 的 atomic token。
     */
    var inlineImageViewerPath by remember { mutableStateOf<String?>(null) }
    val mediaTokenClickHandler = TokenClickHandler { token, _ ->
        when (token.triggerId) {
            "image" -> inlineImageViewerPath = token.id
            "voice" -> {
                val filePath = token.id.substringBefore("|")
                voicePlayer.prepare(filePath).onSuccess { voicePlayer.play() }
            }
        }
    }
    // 是否有录音权限（用于显示录制面板）
    var hasRecordPermission by remember { mutableStateOf(false) }

    /** 图片选择相关状态 */
    var showImagePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    /**
     * v2026-09-01 路线 4：正文改为 Text/Image 交错块列表
     *
     * 旧的 contentBlocks（SnapshotStateList）+ highlightedIndex（两步删除索引）
     * + blockVisibilityStates（可见性追踪）三组状态已由块控制器
     * [bodyBlocks]（见 components/BodyBlocksEditor.kt）统一取代：
     * - 块列表 / 两步删除高亮：BodyBlocksController 内部状态
     * - 可见性懒加载：块级 Composable 直接渲染，不再追踪
     */

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
     * 路线 4：块级正文编辑器控制器（Text/Image 交错块）
     *
     * - 每个 Text 块一个 RichTextEditor；图片块是独立 Composable
     * - Enter 拆块 / 块首退格合并 / 图片块两步删除 / 拖拽手柄排序
     * - 语音 / 话题 / 关联 token 仍内联在 Text 块内（用户要求不变）
     * 详见 components/BodyBlocksEditor.kt
     */
    @OptIn(ExperimentalRichTextApi::class)
    val bodyBlocks = rememberBodyBlocksController(
        registerTriggers = { state ->
            // # 标签 trigger：暖橙色，与原 FlowRow Chip 颜色一致
            state.registerTrigger(
                Trigger(
                    id = "hashtag",
                    char = '#',
                    style = { SpanStyle(color = Color(0xFFFF9A5C), fontWeight = FontWeight.Medium) }
                )
            )
            // @ 关联 trigger：蓝色
            state.registerTrigger(
                Trigger(
                    id = "mention",
                    char = '@',
                    style = { SpanStyle(color = Color(0xFF1976D2), fontWeight = FontWeight.Medium) }
                )
            )
            // 🎤 语音 trigger：橙红色，用于 markdown 注入的 [🎤mm:ss](trigger:voice:...) token 解析
            // char 选 '/' 只为满足 registerTrigger 的非空 char 要求；真正入口在 setMarkdown 解析路径
            state.registerTrigger(
                Trigger(
                    id = "voice",
                    char = '/',
                    style = { SpanStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Medium) }
                )
            )
        },
    )

    /**
     * 兼容层：原"单编辑器富文本状态" → 当前聚焦文本块的状态。
     *
     * 工具栏 / 触发弹窗 / 复制 / 撤销重做 / 语音插入等既有代码继续以
     * `richTextState` 命名工作，实际作用于"聚焦块"（未聚焦时回退第一个文本块）。
     *
     * 注意：这是**组合期求值**（Kotlin 局部变量不支持自定义 getter）——
     * 聚焦块变化时 focusedBlockId 快照状态变化 → 重组 → 重新求值拿到新聚焦块，
     * 因此与"点击时取当前聚焦块"在行为上等价。
     */
    val richTextState: RichTextState = bodyBlocks.focusedOrFirstTextState()

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
                    /** 路线 4：图片作为块级节点插入聚焦块光标处（自动拆块） */
                    bodyBlocks.insertImageAtFocused(path)
                    viewModel.notifyInlineMediaChanged()
                }
                }
            }
        }
    }

    /**
     * 相册多选 Launcher
     *
     * 使用 GetMultipleContents() 契约支持一次选择多张图片，
     * 每张图片均复制到内部存储，最后**一次性**批量插入正文——保证多选相册
     * 是一次"用户操作"（单步撤销，回到图一的状态）。
     * 逐张插入走 cameraLauncher / 单图 picker，仍是每张一快照（逐步撤销）。
     */
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        coroutineScope.launch {
            val paths = uris.mapNotNull { uri ->
                ImageUtils.copyUriToInternalStorage(context, uri)
            }
            if (paths.isNotEmpty()) {
                /** 路线 4：多张图片批量插入，整个 picker 动作作为单步撤销单位 */
                bodyBlocks.insertImagesAtFocused(paths)
            }
            viewModel.notifyInlineMediaChanged()
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

    /** 格式工具栏展开/折叠状态（由底部栏 ⋮ 按钮切换） */
    var isFormatExpanded by remember { mutableStateOf(false) }

    /**
     * v2026-08-01 Phase 2：注册 # hashtag trigger + 编辑器内容初始化
     *
     * 重构要点：
     * 1. 注册 # trigger（必须在 setMarkdown 之前，否则 token 无法被解析）
     *    - trigger id = "hashtag"，char = '#'
     *    - style = 暖橙 SpanStyle（与原 FlowRow Chip 颜色一致）
     *
     * 2. 旧数据兼容：由 ViewModel.loadInspiration 统一处理
     *    - ViewModel 检测 inspiration.tags 非空但 contentFormat 无 token 时，
     *      自动追加 `[#标签](trigger:hashtag:标签)` 到 markdown 末尾
     *    - UI 层只需直接 setMarkdown(contentFormat)，无需重复迁移逻辑
     *
     * 3. 新数据（已含 token）：直接 setMarkdown，token 自动恢复。
     */
    var hasInitializedWithData by remember { mutableStateOf(false) }

    /**
     * 编辑器内容初始化：把整篇 markdown 解析为 Text/Image 交错块
     *
     * - trigger 注册已移入 bodyBlocks 的 registerTriggers（每个新建 Text 块都会注册）
     * - 旧数据迁移已由 ViewModel.loadInspiration 统一处理（contentFormat 已含 token）
     */
    androidx.compose.runtime.LaunchedEffect(contentFormat) {
        if (hasInitializedWithData) return@LaunchedEffect
        try {
            bodyBlocks.initialize(contentFormat)
        } catch (e: Exception) {
            Log.e("InspirationEditScreen", "编辑器初始化异常（已捕获）", e)
        }
        hasInitializedWithData = true
    }

    /** 旧 content_blocks 是否已迁移为正文内联媒体（避免重复迁移） */
    var hasMigratedBlocks by remember { mutableStateOf(false) }

    /**
     * 路线 4：把旧的 content_blocks（图片/语音）迁移为交错块。
     *
     * 按"markdown 中是否已含该路径"去重——8-30 内联化轮已迁移过的数据，
     * 其 markdown 已包含图片/语音，重复插入会产生双份。
     */
    LaunchedEffect(hasInitializedWithData) {
        if (!hasInitializedWithData || hasMigratedBlocks) return@LaunchedEffect
        hasMigratedBlocks = true
        if (inspirationId == null) return@LaunchedEffect

        try {
            val dbBlocks = viewModel.loadContentBlocks(inspirationId)
            val existingMd = bodyBlocks.toMarkdown()
            dbBlocks.forEach { block ->
                when (block) {
                    is ContentBlock.Image -> {
                        if (!existingMd.contains(block.path)) {
                            bodyBlocks.appendMediaMarkdown("![img](${block.path})")
                        }
                    }
                    is ContentBlock.Voice -> {
                        if (!existingMd.contains(block.path)) {
                            val dur = block.duration ?: 0
                            val tokenId =
                                "${block.path}|$dur|${System.currentTimeMillis()}"
                            bodyBlocks.appendMediaMarkdown(
                                "[🎤%02d:%02d](trigger:voice:$tokenId)".format(dur / 60, dur % 60)
                            )
                        }
                    }
                    is ContentBlock.Text -> { /* 不处理 */ }
                }
            }
            /** 迁移是真实内容变更，需置脏以便用户保存 */
            viewModel.notifyInlineMediaChanged()
        } catch (e: Exception) {
            Log.e("InspirationEditScreen", "块级媒体迁移异常（已捕获）", e)
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
     * v2026-08-01 新增：复制到剪贴板功能
     *
     * 行为：
     * - 若正文有选区（selection.start != selection.end）→ 复制选区文本
     * - 若无选区 → 复制正文全文
     * - 复制后通过 SnackbarHostState 显示"已复制到剪贴板"提示（遵循项目规则：禁用系统 Toast）
     *
     * 实现要点：
     * - 使用 RichTextState.annotatedString.text 获取纯文本（去除富文本格式标记）
     * - 用 Android 系统 ClipboardManager 写入 ClipData
     * - 复制操作不推入撤销栈（不属于内容编辑，是只读操作的派生）
     */
    val copyToClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val selection = richTextState.selection
        val fullText = richTextState.annotatedString.text
        /** 有选区时复制选区文本，无选区时复制全文 */
        val textToCopy = if (selection.start != selection.end) {
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            fullText.substring(start, end)
        } else {
            fullText
        }
        val clip = ClipData.newPlainText("灵感内容", textToCopy)
        clipboard.setPrimaryClip(clip)
        coroutineScope.launch {
            snackbarHostState.showSnackbar("已复制到剪贴板")
        }
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
    /** v2026-08-01 Phase 2：showTagPicker / pendingDeleteTag 已移除，标签改用 # Trigger 内联插入 */

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（按当前灵感 id 加载） */
    val relations by viewModel.relations.collectAsState()
    /**
     * 关联ID → 标题映射（由 ViewModel 异步加载并缓存）
     *
     * v2026-08-01 Phase 3 后：关联以 @ token 内联在正文中，标题映射仍保留用于：
     * - mentionSuggestions 排除已关联卡片时的过滤（通过 relations 直接判断）
     * - 未来可能的长按 token 删除关联功能
     */
    val relationTitles by viewModel.relationTitles.collectAsState()
    /**
     * v2026-08-01 Phase 3：以下状态已移除（关联改为 @ Trigger 内联插入）
     * - cardDetail / cardDetailLoading（LinkedCardPreviewDialog 已移除）
     * - previewingRelation（关联预览 Dialog 已移除）
     * - showRelationPicker（RelationPickerBottomSheet 已移除）
     */

    /**
     * @ 关联建议列表状态（v2026-08-01 Phase 3 新增）
     *
     * TriggerSuggestions 的 suggestions 函数是同步的 `(query: String) -> List<T>`，
     * 但 viewModel.searchCards 是异步的。因此用此状态作为桥梁：
     * 1. LaunchedEffect 监听 activeTriggerQuery 变化，异步调用 searchCards
     * 2. 搜索结果更新到此状态
     * 3. TriggerSuggestions 的 suggestions 函数直接返回此列表
     *
     * **生命周期**：
     * - 当 activeTriggerQuery 的 triggerId == "mention" 时触发搜索
     * - 当 trigger 失效（选中/取消）时清空列表
     */
    var mentionSuggestions by remember { mutableStateOf<List<CardSearchResult>>(emptyList()) }

    /**
     * 监听 activeTriggerQuery 变化，异步搜索卡片
     *
     * **防抖策略**：
     * - 每次 query 变化取消上一次搜索任务（LaunchedEffect 自动 cancel-and-restart）
     * - 延迟 200ms 后触发搜索（避免快速输入时过多 DB 查询）
     *
     * **搜索结果处理**：
     * - 排除已关联的卡片（避免重复添加，因为 addRelation 会拒绝重复）
     * - 限制最多 50 条（与原 RelationPickerBottomSheet 一致）
     */
    @OptIn(ExperimentalRichTextApi::class)
    androidx.compose.runtime.LaunchedEffect(richTextState.activeTriggerQuery) {
        val query = richTextState.activeTriggerQuery
        if (query == null || query.triggerId != "mention") {
            mentionSuggestions = emptyList()
            return@LaunchedEffect
        }
        // 防抖：延迟 200ms 后搜索
        delay(200L)
        viewModel.searchCards(query.query) { results ->
            // 排除已关联的卡片
            val excludeIds = relations.map { it.targetType to it.targetId }.toSet()
            mentionSuggestions = results
                .filter { (it.cardType to it.cardId) !in excludeIds }
                .take(50)
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

    /**
     * 初始化已有内容块
     *
     * v2026-07-25 三写存储重构：仅从 content_blocks 表加载附件
     * - 旧的回退逻辑（从 imagePaths/voiceNotePath 恢复）已删除
     * - Migration 46→47 已将旧数据迁移到 content_blocks 表并清空旧字段
     * - 保存时已不再写入 imagePaths/voiceNotePath（置空）
     *
     * v2026-09-01 路线 4：内容块列表由 BodyBlocksController 管理，
     * 本效果（清空旧列表）已无必要，直接移除。
     */

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

            /** 将恢复的文本重建为块列表（纯文本作为 Markdown 设置） */
            bodyBlocks.initialize(restoredAnnotatedString.text)
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

                /**
                 * 撤销 + 重做（紧凑组）
                 *
                 * v2026-09-01 统一时间线：文本编辑（逐字）与结构变更（插图拆块/Enter/合并/
                 * 重排/删图）共用 [BodyBlocksController] 内的同一个快照栈，天然按时间倒序
                 * 串联，撤销 = pop 栈顶恢复 + 光标还原。不再依赖库 `state.history`——
                 * 块编辑器的 `undoBehavior` 已设 Disabled，避免物理键盘 Ctrl+Z 走库内
                 * history 与统一栈并存错乱。
                 */
                val bodyCanUndo = bodyBlocks.canUndoTimeline
                val bodyCanRedo = bodyBlocks.canRedoTimeline
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { bodyBlocks.undoTimeline() },
                        enabled = bodyCanUndo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "撤销",
                            tint = if (bodyCanUndo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = { bodyBlocks.redoTimeline() },
                        enabled = bodyCanRedo && !isLocked,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "重做",
                            tint = if (bodyCanRedo && !isLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                /**
                 * v2026-08-01 新增：复制按钮
                 *
                 * 行为：有选区复制选区文本，无选区复制正文全文
                 * 详见 [copyToClipboard] 函数实现
                 */
                IconButton(
                    onClick = copyToClipboard,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
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
                 * v2026-08-01 Phase 2 改造：# 按钮改为在光标处插入 # 字符
                 *
                 * - 点击后在正文当前光标位置插入 # 字符
                 * - # 字符触发 hashtag trigger 检测，弹出 TriggerSuggestions 建议弹窗
                 * - 用户可继续输入标签名或从建议中选择
                 * - 移除原 TagPickerSheet 弹窗（标签已内联为正文 atomic token）
                 */
                onTagClick = {
                    if (!isLocked) {
                        richTextState.addTextAfterSelection("#")
                    }
                },
                /**
                 * v2026-08-01 Phase 3 改造：@按钮改为插入 @ 字符触发 TriggerSuggestions
                 *
                 * - 旧：弹出 RelationPickerBottomSheet（多选弹窗）
                 * - 新：在光标位置插入 @ 字符，触发 mention trigger 弹出建议列表
                 * - 与 # 标签按钮行为一致（统一的内联插入体验）
                 *
                 * 选中建议后：
                 * 1. 插入 RichSpanStyle.Token（atomic span）
                 * 2. 调用 viewModel.addRelation() 即时入库
                 */
                onMentionClick = {
                    if (!isLocked) {
                        richTextState.addTextAfterSelection("@")
                    }
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
            /**
             * v2026-08-01 标题区改用 RichTextEditor 单段落（compose-rich-editor 库）
             *
             * 重构原因：统一编辑器组件，标题与正文都使用 RichTextEditor，
             * 便于后续 Phase 2/3 在标题中也支持 trigger（如 #标签、@关联）。
             *
             * 关键点：
             * - 使用独立的 titleRichTextState（与正文 richTextState 隔离）
             * - 用 setText() 设置纯文本（不解析 markdown/html 格式，避免 # 被误解析为标题样式）
             * - 禁用富文本格式（无格式工具栏入口，用户无法对标题加粗等）
             * - 光标颜色与正文统一为暖橙 Color(0xFFFF9A5C)
             * - contentPadding 水平 0.dp 保证与正文左对齐（起点 8dp）
             *
             * 双向同步策略：
             * 1. viewModel.title → state：loadInspiration / 外部 setTitle 时同步，用 setText() 纯文本设置
             * 2. state → viewModel.title：用户输入时同步，用 annotatedString.text 读取纯文本
             * 3. 防循环：用 if (currentText != title) 判断避免重复触发
             */
            val titleRichTextState = rememberRichTextState()

            /** 单向同步：viewModel.title 变化时（loadInspiration / 外部调用 setTitle）→ state */
            LaunchedEffect(title) {
                val currentText = titleRichTextState.annotatedString.text
                if (currentText != title) {
                    /** 用 setText 设置纯文本，不解析任何 markdown/html 格式 */
                    titleRichTextState.setText(title)
                }
            }

            /** 单向同步：state 文本变化时（用户输入）→ viewModel.title */
            LaunchedEffect(titleRichTextState.annotatedString) {
                val newText = titleRichTextState.annotatedString.text
                if (newText != title && !isLocked) {
                    viewModel.setTitleWithRecommendation(newText)
                }
            }

            RichTextEditor(
                state = titleRichTextState,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        "标题",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                readOnly = isLocked,
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                colors = RichTextEditorDefaults.richTextEditorColors(
                    /** 容器背景透明，跟随全局主题色 */
                    containerColor = Color.Transparent,
                    /** 光标颜色：暖橙，与正文统一 */
                    cursorColor = Color(0xFFFF9A5C),
                    /** 移除底部指示线（边界线） */
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent
                ),
                /**
                 * 水平 padding 设为 0.dp，让标题文字起点 = Column padding(8dp) + 0 = 8dp，
                 * 与下方时间戳+字数 Row 和 RichTextEditor 正文完全左对齐。
                 * vertical 保持 8dp，标题上下仍有合适的呼吸空间。
                 */
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 0.dp,
                    vertical = 8.dp
                )
            )

            /**
             * v2026-07-31 新增：标题与正文之间的"时间戳 + 字数"行
             *
             * 排版规则：
             * - 时间戳格式：`yyyy.MM.dd HH:mm`（如 `2026.07.15 10:49`），与灵感详情页卡片时间戳格式一致
             * - 字数统计规则：只统计正文字符数（去除空白），**不包含标题、标签、关联卡片**
             * - 时间戳来源：ViewModel.createdAt（新建模式 = 进入页面时记录；编辑模式 = 数据库 createdAt）
             * - 视觉样式：12sp 浅灰（Color(0xFF999999)），与详情页 InspirationViewCard 时间戳样式一致
             *
             * v2026-09-01 修订：标题↔时间↔内容三段空白距离严格相等（以"标题→时间"为基准）。
             * - 标题 editor 自带 contentPadding(vertical=8.dp) → 标题文本下沿到时间文本上沿 = 8（title padding）+ 8（上方 Spacer）= 16dp
             * - 下方 Spacer 改为 16dp 凑齐 16dp，让"时间→内容"等于 16dp
             * - 不要把 BodyBlocksEditor 的 contentPadding top 改 8dp 来"抵消"——它是 per-block 的，会把每个块都加上 8dp 顶距，破坏图片与文字的紧贴关系
             */
            val createdAt by viewModel.createdAt.collectAsState()
            val timestampText = remember(createdAt) {
                SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
                    .format(Date(createdAt))
            }
            /** 字数：实时响应 content 变化，只统计正文字符数（去除空白） */
            val contentCharCount = remember(content) {
                InspirationTextUtils.countInspirationContentChars(content)
            }

            Spacer(modifier = Modifier.height(8.dp))

            /** 时间戳 + 字数行（中间用竖线分隔，与参考图一致） */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestampText,
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                /** 竖线分隔符（颜色比文字略浅，宽度 1dp） */
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(12.dp)
                        .background(Color(0xFFCCCCCC))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${contentCharCount}字",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            /** ===== 块级内容编辑器区域（Text/Image 交错 + 手柄拖拽排序 + 两步删除） ===== */

            /**
             * v2026-09-01 路线 4：正文改为 Text/Image 交错块列表。
             *
             * - 图片从"正文内联 + 覆盖层绘制"（重叠根因）改为块级 Composable
             * - 每个 Text 块一个独立 RichTextEditor；语音 token 仍内联在块内
             * - Enter 拆块 / 块首退格合并 / 图片块两步删除 / 手柄拖拽排序
             * 详见 components/BodyBlocksEditor.kt
             */
            Box(modifier = Modifier.fillMaxWidth()) {
            CompositionLocalProvider(
                LocalTokenClickHandler provides mediaTokenClickHandler,
                LocalImageLoader provides CoilRichTextImageLoader,
            ) {
                BodyBlocksEditor(
                    controller = bodyBlocks,
                    isLocked = isLocked,
                    onImageTap = { path -> inlineImageViewerPath = path },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            /**
             * v2026-08-01 Phase 2：# 标签触发建议弹窗
             *
             * 当用户在正文中输入 # 后触发 hashtag trigger，
             * 此弹窗显示匹配的历史标签（savedTags）供快速选择。
             *
             * 选中后插入 RichSpanStyle.Token（atomic span，backspace 整体删除）。
             */
            @OptIn(ExperimentalRichTextApi::class)
            TriggerSuggestions(
                state = richTextState,
                triggerId = "hashtag",
                suggestions = { query ->
                    savedTags.filter { it.contains(query, ignoreCase = true) }
                },
                onSelect = { tag ->
                    RichSpanStyle.Token(
                        triggerId = "hashtag",
                        id = tag,
                        label = "#$tag"
                    )
                },
                item = { tag ->
                    Text(
                        text = "#$tag",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 14.sp,
                        color = Color(0xFFFF9A5C),
                        fontWeight = FontWeight.Medium
                    )
                }
            )

            /**
             * v2026-08-01 Phase 3：@ 关联触发建议弹窗
             *
             * 当用户在正文中输入 @ 后触发 mention trigger，
             * 此弹窗显示匹配的卡片（待办/灵感/日期）供快速选择。
             *
             * 选中后：
             * 1. 插入 RichSpanStyle.Token（atomic span，backspace 整体删除）
             *    - token id 格式：`类型:ID`（如 `todo:123`），用于序列化
             *    - token label 格式：`@标题`（如 `@买菜`），用于显示
             * 2. 调用 viewModel.addRelation() 即时入库（双向插入 + 数量上限检查）
             *
             * **混合方案**：token 仅作视觉展示，关联的真相源是 card_relations 表。
             * 删除 token 不会自动删除关联（需通过其他入口，如长按 token）。
             */
            @OptIn(ExperimentalRichTextApi::class)
            TriggerSuggestions(
                state = richTextState,
                triggerId = "mention",
                suggestions = { _ ->
                    // 直接返回预加载的 mentionSuggestions（由 LaunchedEffect 异步更新）
                    mentionSuggestions
                },
                onSelect = { card ->
                    // 即时入库：调用 addRelation（内部 launch 协程，不阻塞 UI）
                    viewModel.addRelation(card.cardType, card.cardId)
                    // 返回 Token 用于视觉展示
                    RichSpanStyle.Token(
                        triggerId = "mention",
                        id = "${card.cardType}:${card.cardId}",
                        label = "@${card.title}"
                    )
                },
                item = { card ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 类型 emoji 图标
                        Text(
                            text = card.typeIcon,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 卡片标题
                        Text(
                            text = card.title,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // 类型标签
                        Text(
                            text = card.typeName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            } /** Box 结束 */

            /**
             * v2026-08-01 Phase 3：关联已内联为正文中的 atomic token（@ Trigger），
             * 移除原 LinkedCardsRow 独立展示区。
             * 关联的真相源仍是 card_relations 表（通过 viewModel.addRelation 即时入库）。
             */

            /**
             * 路线 4：块内容变化 → 组装整篇 markdown 同步到 ViewModel。
             *
             * 保存链路：VM 的 _richTextState 已不再注入（bodyBlocks 管理各块状态），
             * saveInspiration 会走 `_contentFormat` 回退分支，因此这里必须实时
             * 用 setContentFormat 维护整篇 markdown；setContent 维护纯文本。
             *
             * 用 SideEffect 而不是 LaunchedEffect 赋值 onDocChanged：SideEffect 在
             * 每次组合生效后同步执行，保证任何 LaunchedEffect（初始化 / 媒体迁移 /
             * 块内容观察者）触发变更时回调一定已就位，避免迁移内容漏同步。
             */
            androidx.compose.runtime.SideEffect {
                bodyBlocks.onDocChanged = {
                    if (hasInitializedWithData) {
                        viewModel.setContent(bodyBlocks.plainText())
                        viewModel.setContentFormat(bodyBlocks.toMarkdown())
                    }
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

    /** v2026-08-01 Phase 2：TagPickerSheet 已移除，标签改用 # Trigger 内联插入 */

    /**
     * v2026-08-01 Phase 3：RelationPickerBottomSheet 和 LinkedCardPreviewDialog 已移除
     *
     * - 关联选择：改用 @ Trigger + TriggerSuggestions 内联插入（与 # 标签一致）
     * - 关联展示：改用正文中的 @ atomic token（RichSpanStyle.Token）
     * - 关联删除：通过 viewModel.deleteRelation() 入口（如长按 token 或其他入口）
     * - 关联真相源：仍是 card_relations 表（通过 viewModel.addRelation 即时入库）
     */

    /** v2026-08-01 Phase 2：pendingDeleteTag 删除确认对话框已移除，标签删除改为光标定位后 backspace */

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
                    voicePlayer = voicePlayer,
                    onSaved = { path, duration ->
                        val mm = duration / 60
                        val ss = duration % 60
                        val label = "🎤%02d:%02d".format(mm, ss)
                        /**
                         * v2026-08-30 内联（修复 v2026-08-31）：语音作为正文内联 atomic token 插入。
                         *
                         * v2026-08-31 修复说明：
                         * 旧的 `addRichSpan(RichSpanStyle.Token(...))` 是只对"已有选区"生效的样式附加 API，
                         * 当 selection collapsed（光标闪烁）时它什么也不做 → 录音结束后正文里看不到任何标识。
                         *
                         * 改用库提供的公共 insertMarkdownAfterSelection：
                         * - 把 token 序列化为标准的 Markdown 伪链接语法
                         *   `[🎤mm:ss](trigger:voice:<path>|<duration>)`
                         * - 库内部走 setMarkdown 的 Markdown→RichSpan 解析路径，
                         *   会创建 RichSpan(text = "🎤mm:ss", richSpanStyle = Token(...))，
                         *   label 自动作为 raw text 进入正文（rawText.append(richSpan.text) 路径），
                         *   编辑态就能看见 🎤 + 时长了。
                         * - 不依赖 activeTriggerQuery 是否激活（录音时不可能处于 voice trigger 状态，
                         *   因此之前那种"用 insertToken"路径走不通）。
                         * - voice trigger 仍需注册（用于 token 渲染颜色与解析时的样式查表），
                         *   见文件上方 richTextState.registerTrigger(Trigger(id="voice", ...))。
                         */
                        /**
                         * v2026-08-31 新增：token id 追加时间戳，保证多次录音 id 全局唯一。
                         *
                         * 不追加时 id = "<路径>|<时长>"；若同一文件路径录制两次且时长恰好相同，
                         * markdown 解析出的两个 RichSpanStyle.Token 会因 equals 相等被库
                         * 合并成一个 span（相邻同 style 合并），编辑时删除一个会误删两个。
                         * 追加毫秒时间戳后 id 永不相同，每个录音都是独立原子 span。
                         */
                        val tokenId =
                            "$path|$duration|${System.currentTimeMillis()}"
                        val voiceMarkdownLink =
                            "[$label](trigger:voice:$tokenId)"
                        bodyBlocks.insertVoiceToken(voiceMarkdownLink)
                        viewModel.notifyInlineMediaChanged()
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
     * v2026-08-30 内联图片查看器（v2026-08-31 修复）：
     * 点击编辑态正文中的图片 → 进入项目已有的「图片附件」全屏预览模式。
     *
     * **v2026-08-31 修复动机**：
     * 旧的实现是 Dialog + Box + InlineImagePreview 单图预览，
     * 与项目内 InspirationDetailImageStack 的多图横向堆叠体验不一致，
     * 也缺失缩放/左右切换/删除/下载等标准动作。
     *
     * **新方案**：
     * 复用 [InspirationImageGallery]（灵感专用的沉浸式全屏画廊），支持：
     * - HorizontalPager 左右滑动切换所有图片
     * - 双指缩放 + 双击放大还原（基于 ZoomableImage）
     * - 顶部"图片附件"标题、关闭按钮、页码指示器 "n/m"
     * - 左下角删除按钮（二次确认 AlertDialog，已自带）
     * - 右下角下载按钮（保存到相册 + Snackbar 反馈）
     *
     * **图片路径来源**：
     * 编辑态下图片以 RichSpanStyle.Image span（Markdown 序列化 `![](path)`）形式
     * 散落在 RichTextState 中，并不直接维护在 contentBlocks 列表。
     * 因此从 `richTextState.toMarkdown()` 中用项目已有的 Markdown 图片正则
     * 扫描出所有图片路径，与 `inlineImageViewerPath` 取的初始索引对齐。
     */
    inlineImageViewerPath?.let { clickedPath ->
        val bodyMarkdown = bodyBlocks.toMarkdown()
        val imagePathsInBody = Regex("""!\[[^\]]*\]\(([^)]+)\)""")
            .findAll(bodyMarkdown)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        // 若被点击的图片不在正文图列表里（极端情况下被外部 setMarkdown 改过），
        // 退化为 0 索引，避免 IllegalArgumentException
        val initialIndex = imagePathsInBody.indexOf(clickedPath).coerceAtLeast(0)

        InspirationImageGallery(
            imagePaths = imagePathsInBody,
            initialIndex = initialIndex,
            /** 删除按钮：复用编辑页已有的图片删除路径（与 ReorderableColumn 走同一套） */
            onDeleteClick = { idx ->
                val targetPath = imagePathsInBody.getOrNull(idx) ?: return@InspirationImageGallery
                /**
                 * v2026-09-01 路线 4：图片是块级节点，直接按路径删除对应 Image 块。
                 * （仅移出块列表，不物理删文件——孤儿文件清理仍处于停用状态）
                 */
                val removed = bodyBlocks.deleteImageByPath(targetPath)
                if (removed) {
                    viewModel.notifyInlineMediaChanged()
                }
                inlineImageViewerPath = null
            },
            onDismiss = { inlineImageViewerPath = null }
        )
    }

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
