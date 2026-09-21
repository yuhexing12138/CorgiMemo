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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.corgimemo.app.ui.theme.LocalContentTypography
import com.corgimemo.app.ui.theme.UiDimensions
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
import androidx.compose.ui.text.TextRange /** 标题单行化后重算光标 / 选区位置（v2026-09-15）*/
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.input.key.Key /** 标题回车拦截：Key.Enter / Key.NumPadEnter（v2026-09-15）*/
import androidx.compose.ui.input.key.KeyEventType /** 标题回车拦截：只处理 KeyDown（v2026-09-15）*/
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
/** 面板展开期间消费标题点击所需（v2026-09-21）：指定 Initial 阶段 */
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.screens.inspiration.components.DEFAULT_BODY_SP
/** 底部内联面板标识（v2026-09-21）：T / H / A 三面板互斥所用的单一状态类型 */
import com.corgimemo.app.ui.screens.inspiration.components.EditBottomPanel
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
import com.corgimemo.app.ui.screens.inspiration.components.InspirationEditBottomBar /** 灵感编辑页底部栏（5 按钮 + 可折叠格式工具栏 + 字体选择面板）*/
import com.corgimemo.app.ui.theme.ContentFontManager /** 内容字体（每条灵感单独记录；boldTiers 清除集合与工具栏探测共用同一字体，保证选档/取消语义一致）*/
import com.corgimemo.app.ui.screens.inspiration.components.InspirationImageGallery /** 灵感专用的沉浸式全屏图片画廊（编辑态预览复用） */
import com.corgimemo.app.ui.screens.inspiration.InspirationTextUtils /** v2026-07-31 新增：标题与正文之间"时间戳+字数"行所需的字数统计工具 */
import com.corgimemo.app.ui.model.ContentBlock /** 内容块：公共定义（文本/图片/语音）*/
import com.mohamedrejeb.richeditor.annotation.ExperimentalRichTextApi
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.mohamedrejeb.richeditor.model.RichTextState
import com.corgimemo.app.ui.screens.probe.BlockNoteBridgeController
import com.corgimemo.app.ui.screens.probe.BlockNoteEditorWebView
import com.corgimemo.app.ui.components.LongPressRepeatIconButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** 内容块定义已提取至 com.corgimemo.app.ui.model.ContentBlock（公共模块），通过 import 复用 */

/**
 * v2026-09-01 路线 4：图片已改为**块级**（BodyBlocksController.insertImageAtFocused）。
 * 本注释块原本挂在已删除的 `insertBlockImage`（路线 2 内联图片方案）上方，
 * 路线 4 不再涉及内联渲染与 ▢ 占位字符，故整段删除。
 */

/**
 * ⚠️ v2026-09-21：原 `composeColorToHex`（Compose Color → "#RRGGBB"）已删除——
 * 它只服务于 Aa 面板的预设色下行；行内色的「色名 → hex」转换现由
 * [com.corgimemo.app.ui.screens.inspiration.components.blockColorHexOf] 承担（与色板同源）。
 */

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

    /**
     * 正文编辑区 = BlockNote WebView 编辑器（跨块选择 + JS 格式工具栏）。
     * 页面其余 UI（标题/标签/底部栏/位置等）保持不变。
     * 编辑器装载由下方 LaunchedEffect 经 Bridge 下发 init{markdown}。
     */
    val contentLoaded by viewModel.contentLoaded.collectAsState()
    val blockNoteController = remember { BlockNoteBridgeController() }
    var blockNoteLoadStarted by remember { mutableStateOf(false) }
    /** BlockNote 模式链接对话框（P1.5 浮层桥接：🔗 按钮 → URL 输入 → format createLink） */
    var showLinkDialog by remember { mutableStateOf(false) }
    var linkDialogUrl by remember { mutableStateOf("https://") }
    /**
     * 当前展开的底部内联面板（v2026-09-21 收敛为单一状态，取代原先三个 boolean）
     *
     * T / H / A 三个按钮各自展开一个面板（[EditBottomPanel]），三者**互斥且共用同一槽位**。
     * 原先用 `isFontPanelExpanded` / `isSizeColorPanelExpanded` / `isColorPanelExpanded`
     * 三个 boolean 表达，互斥只能靠"记得把另两个一并置 false"维持——真漏过一次
     * （从 A 切到 T / Aa 时两个面板同时可见、把按钮行顶下去）。
     *
     * 收敛为可空枚举后，**互斥由状态本身保证**：任何时刻只可能有一个值，
     * `openPanel != null` 即"有面板展开"（见 [isFormatPanelOpen]，驱动键盘抑制）。
     */
    var openPanel by remember { mutableStateOf<EditBottomPanel?>(null) }

    // 内容就绪（编辑模式 loadInspiration 完成 / 新建模式立即）→ 装载 WebView 编辑器（仅一次）
    androidx.compose.runtime.LaunchedEffect(contentLoaded) {
        if (contentLoaded && !blockNoteLoadStarted) {
            blockNoteLoadStarted = true
            /**
             * v2026-09-21 修复：init fontFamily 原硬编码 "system_default"，已保存字体的
             * 灵感在正文永远回显系统默认。此处 ContentFontManager 已装载本条字体
             * （编辑模式 loadInspiration 内 setFonts 先于 contentLoaded 置位；新建模式
             * VM 构造 resetToDefault）——直接读单例当前值，避免捕获下方才声明的局部态。
             * 后续字体变化（面板「应用」）由下方 LaunchedEffect(contentFontEntry.id) 响应式下发。
             */
            blockNoteController.load(
                if (inspirationId == null) "" else viewModel.contentFormat.value,
                ContentFontManager.currentEntry.value.id,
                ContentFontManager.currentLatinId.value
            )
        }
    }
    // 新建模式：无 loadInspiration 调用，直接标记内容就绪（空文档）
    androidx.compose.runtime.LaunchedEffect(inspirationId) {
        if (inspirationId == null) {
            viewModel.markContentLoaded()
        }
    }
    /**
     * Undo/Redo 状态说明（v2026-09-02 方案A：两套历史隔离）：
     *
     * 正文的撤销/重做由 **两套互相隔离的历史** 驱动，统一入口
     * [BodyBlocksController.undo] / [redo]（焦点判断是核心）：
     * - **全局命令栈**（bodyBlocks 内部）：只存操作增量 Command——管块的增删、
     *   拖拽排序、图片块属性编辑。controller 由 ViewModel 持有，**屏幕旋转不丢历史**；
     * - **块内富文本 history**（compose-rich-editor 库自带 `RichTextState.history`）：
     *   聚焦块的打字 / 加粗 / 样式自己管自己，不进全局栈。
     * 按撤销时：聚焦块库内 history 非空 → 先回退块内文字；空则走全局命令栈。
     *
     * 按钮启用状态读 `bodyBlocks.canUndo / canRedo`（见下方撤销/重做按钮），
     * 二者是可观察的快照状态（含聚焦块 history 的感知）。
     */

    // 地理围栏相关状态
    val geofenceLat by viewModel.geofenceLat.collectAsState()
    val geofenceLng by viewModel.geofenceLng.collectAsState()
    val geofenceRadius by viewModel.geofenceRadius.collectAsState()
    val geofenceType by viewModel.geofenceType.collectAsState()
    val geofenceEnabled by viewModel.geofenceEnabled.collectAsState()
    val geofenceAddress by viewModel.geofenceAddress.collectAsState()

    /** 图片路径列表状态 */
    val imagePaths by viewModel.imagePaths.collectAsState()

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
     *
     * v2026-09-09 再收窄：**块级图片点击不再打开本查看器**（用户要求移除该入口，
     * 改为块内选中高亮，见 BodyBlocksController.onImageBlockTapped）——全屏画廊
     * 仅剩旧数据 trigger:image token 一条入口。
     */
    var inlineImageViewerPath by remember { mutableStateOf<String?>(null) }

    // 是否有录音权限（用于显示录制面板）
    var hasRecordPermission by remember { mutableStateOf(false) }

    /** 图片选择相关状态 */
    var showImagePicker by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var pendingPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }

    /**
     * ⚠️ 待清理（BlockNote 迁移遗留）：正文块数据控制器（Text/Image 交错块）
     *
     * 正文 UI 已由 BlockNote WebView 渲染；本 controller 的渲染方已删除
     * （见 components/BodyBlocksController.kt 文件头说明），仅数据链路仍在依赖。
     */

    /** 锁定编辑状态 */
    var isLocked by remember { mutableStateOf(false) }

    /**
     * 锁定态 → 正文只读（v1.11.1）
     *
     * 补齐一条「协议早已定义、JS 早已实现、但 Kotlin 侧从未下发」的链路：
     * `setReadOnly` 自桥协议 v1 起就在文档里，`EditorApp` 也一直处理它
     * （`setReadOnly(msg.readOnly)` → 编辑器 `editable`），但 controller 此前
     * 没有对应方法 —— 于是锁定后正文其实**仍然可编辑**，只是没人发现。
     *
     * ⚠️ 只读只挡**用户输入**，挡不住 `removeBlocks` / `updateBlock` 这类程序化 API，
     * 所以底部工具栏在锁定态另有 `toolbarEnabled = !isLocked` 一层防护（见其调用处）。
     */
    LaunchedEffect(isLocked) {
        blockNoteController.setReadOnly(isLocked)
    }

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
     * ⚠️ 待清理（BlockNote 迁移遗留）：块级正文编辑器控制器（Text/Image 交错块）。
     *
     * 正文编辑器已切换为 BlockNote WebView（见下方 [BlockNoteEditorWebView]），
     * 本 controller 的 **UI 渲染方（BodyBlocksEditor）已下线**，但以下数据链路仍在依赖它，
     * 故暂时保留，待后续专项清理：
     * - **图片备注 / 缩放属性持久化**：`applyImageProps` / `blocks`（保存时按 path 收集）
     * - **旧数据媒体迁移**：`appendMediaMarkdown`（content_blocks → 正文内联）
     * - **语音 token 插入**：`insertVoiceToken`
     * - **图片删除**：`deleteImageByPath`
     * - **格式工具栏激活态**：[richTextState] 的 `currentSpanStyle` 回显
     *
     * 清理这些链路时，需同步确认上述功能是否已改由 BlockNote 侧承担。
     * 详见 components/BodyBlocksController.kt（原 UI 部分备份见「弃用文件/Compose编辑器-弃用备份/」）
     */
    @OptIn(ExperimentalRichTextApi::class)
    val bodyBlocks = viewModel.bodyBlocks

    /**
     * 兼容层：块编辑器「聚焦文本块」的富文本状态（未聚焦时回退第一个文本块）。
     *
     * BlockNote 接管正文后，本状态**仅用于格式工具栏的激活态高亮**
     * （粗体/斜体/列表等按钮的 isActive）与 `#`/`@` 插入等宿主持有逻辑；
     * 正文内容本身不再经此状态读写（读走 WebView 的 markdown 上行）。
     *
     * 注意：这是**组合期求值**（Kotlin 局部变量不支持自定义 getter）——
     * 聚焦块变化时 focusedBlockId 快照状态变化 → 重组 → 重新求值拿到新聚焦块。
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
                    /** 拍照结果作为块级节点插入光标处：经 Bridge 下发到 BlockNote WebView 编辑器 */
                    blockNoteController.insertImage(path)
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
                /** 多张图片批量插入：经 Bridge 逐张下发
                 *  （JS 侧 Yjs 事务天然合并为一步，整个 picker 动作即单步撤销单位）*/
                paths.forEach { path -> blockNoteController.insertImage(path) }
            }
            viewModel.notifyInlineMediaChanged()
        }
    }

    /**
     * BlockNote 迁移（P1.5）：视频/音频/文件选择 Launcher——
     * 选后拷贝到内部存储，经 Bridge 插入对应媒体块（file:// URL 由 JS 侧生成）。
     */
    val mediaVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                ImageUtils.copyUriToInternalStorage(context, it)?.let { path ->
                    blockNoteController.insertVideo(path)
                }
            }
        }
    }
    val mediaAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                ImageUtils.copyUriToInternalStorage(context, it)?.let { path ->
                    blockNoteController.insertAudio(path)
                }
            }
        }
    }
    val mediaFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                ImageUtils.copyUriToInternalStorage(context, it)?.let { path ->
                    blockNoteController.insertFile(path)
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
     * 内容区「自选背景色」：
     * - 默认状态（未选颜色 / 恰好是纯白）→ `Color.Transparent`，表示"不铺自选色"
     * - 用户主动选择颜色后 → 使用用户选择的颜色
     *
     * 注意：本值只回答"用户选了什么"，**不回答"内容区最终显示什么颜色"**——
     * 后者见 [contentBackgroundColor]。
     */
    val userPickedBackgroundColor =
        if (backgroundColorInt == -1 || rawBackgroundColor == Color.White) {
            Color.Transparent
        } else {
            rawBackgroundColor
        }

    /**
     * 主题背景色（先读出为普通局部变量，供下方 `remember` 使用）。
     *
     * ⚠️ 必须在此处（**组合上下文内**）求值，不能写进 `remember { ... }` 的 calculation lambda：
     * `MaterialTheme.colorScheme` 是 `@Composable` 属性读取，而 `remember(key) { calc }` 的
     * `calc` 是**普通 lambda**（非 `@Composable`，在组合之外执行，用于缓存计算），
     * 在里面读会报 `@Composable invocations can only happen from the context of a @Composable function`。
     * 而 `remember` 的 **key 参数本身在组合期求值**，所以 key 位置可以直接写 `MaterialTheme...`——
     * 「key 合法、lambda 体内非法」正是这个错误容易被忽略的原因。
     */
    val themeBackgroundColor = MaterialTheme.colorScheme.background

    /**
     * 内容区**实际生效**背景色（唯一真值，v2026-09-17 收敛）
     *
     * 由 [userPickedBackgroundColor] 做 `Transparent → 主题 background` 的回落得到，
     * 是「内容区在屏幕上真正呈现的那一层色」。
     *
     * 之所以收敛成一个值：本值原先在两处各自求值——
     * ① 宿主 `Column` 的 `.background(...)`（铺底色）
     * ② 下行给 BlockNote WebView 的 `backgroundColor`（消除画中画）
     * 两者逻辑等价但物理独立，后续任一处改动都会悄悄漂移（例如只改了 Column
     * 的回落逻辑，WebView 仍按旧口径着色，重新出现色差）。现统一由此处产出，
     * 两处共用同一个快照态，不可能再不一致。
     *
     * ⚠️ 用 `remember` 缓存：该值在重组中反复参与 `Color` 相等比较与参数传递，
     * 且 key（用户自选色 + 主题背景）任一变化才需重算。
     */
    val contentBackgroundColor = remember(userPickedBackgroundColor, themeBackgroundColor) {
        if (userPickedBackgroundColor == Color.Transparent) {
            themeBackgroundColor
        } else {
            userPickedBackgroundColor
        }
    }

    /**
     * 内容区**自选色**（绘制层真值，v2026-09-17 收敛）
     *
     * 语义 = "要不要真的铺一层色"：
     * - 用户未自选背景色 → `Color.Transparent`，不绘制，让页面主题背景透出（保持原视觉）
     * - 用户已自选背景色 → 该颜色本身，铺满全宽
     *
     * 与 [contentBackgroundColor] 的区别：后者是"实际生效色"（已把 Transparent 回落成
     * 主题 background），用于**告知子组件**（BlockNote WebView 需要知道具体色值）；
     * 本值用于**宿主自己绘制**。两个语义显式分开，避免"一个变量兼两种含义"再次成为漂移源。
     */
    val contentBackgroundPaint = userPickedBackgroundColor

    /** 格式工具栏展开/折叠状态（由底部栏 ⋮ 按钮切换） */
    var isFormatExpanded by remember { mutableStateOf(false) }

    /**
     * 是否存在任一面板展开（v2026-09-21 新增；同日收敛为单一状态派生）
     *
     * 三个面板（T 字体 / Aa 字号颜色 / A 颜色）共用底部栏同一槽位，由**唯一**的
     * [openPanel] 状态表达，故此处只需判空——不再存在"两个 boolean 同时为 true
     * 导致面板叠加"的可能（旧写法正是三处互斥漏关一处而出的 bug）。
     *
     * 本页面用它统一表达「键盘让位给面板」这一中间态：
     * - 正文 WebView → `suppressIme`，面板展开期间不响应 IME（否则键盘顶走面板、
     *   并把 WebView 视口压缩，见 [BlockNoteEditorWebView] 的 v1.11.9 记录）；
     * - 顶部标题输入框 → 面板展开期间消费指针事件，点击不聚焦、自然不弹键盘。
     *
     * 光标与选区能力**不受影响**：正文仍可点定位光标、长按选词、拖手柄多选，
     * 只是不再唤起软键盘（真机已验证）。
     */
    val isFormatPanelOpen = openPanel != null

    /** 软键盘控制器：展开任一面板前收起键盘（面板高度 = 键盘高度，二者不同屏共存） */
    val keyboardController = LocalSoftwareKeyboardController.current

    /**
     * 切换底部内联面板（v2026-09-21 抽取）
     *
     * 收敛了三处按钮回调里重复的「同值置 null，否则置该值」样板，并统一承载
     * **所有面板完全一致**的两件事：
     * 1. **互斥切换**：点已展开的面板 → 收起（置 null）；点别的面板 → 直接替换。
     *    因为 [openPanel] 是单一状态，"替换"本身就完成了互斥，无需手动关另两个面板
     *    （收敛前正是漏关导致过"从 A 切到 T / Aa 面板叠加"）。
     * 2. **键盘让位**：**仅在展开分支**收起软键盘——面板高度 = 键盘高度，二者不同屏共存。
     *    收起分支**不主动弹回**键盘，沿用既定约定：用户再点正文 / 标题才恢复输入。
     *
     * ⚠️ 各面板的**专属副作用**不放在这里，由调用方在调用本函数**之前**执行
     * （目前只有字体面板：展开前要把 pending 重置为当前内容字体，见 onFontPickerClick）。
     *
     * ⚠️ 必须是局部函数，且**定义在三个按钮回调之前**——Kotlin 局部函数的声明顺序
     * 即可见性，放在下方会报 Unresolved reference。
     *
     * @param panel 目标面板；若与当前展开的面板相同则收起，否则切换到它
     */
    fun togglePanel(panel: EditBottomPanel) {
        if (openPanel == panel) {
            /** 收起：面板消失后键盘由输入框焦点决定，不主动弹回 */
            openPanel = null
        } else {
            keyboardController?.hide()
            openPanel = panel
        }
    }

    /**
     * 当前内容字体（[ContentFontManager]「当前」状态 = 正在编辑的这条灵感的字体）。
     * 装载时机：VM 构造复位默认 → loadInspiration 按灵感覆盖（见 InspirationEditViewModel）。
     * 选择即时生效：编辑内容排版（LocalContentTypography）与工具栏字重探测自动跟随。
     */
    val contentFontEntry by ContentFontManager.currentEntry.collectAsState()
    val contentLatinFontId by ContentFontManager.currentLatinId.collectAsState()

    /**
     * 正文 WebView 字体响应式跟随（v2026-09-21 修复「T 面板调整字体正文不生效」）：
     * [ContentFontManager] 是字体的**单一真相源**——它一变（面板「应用」经 VM 回调写入、
     * loadInspiration 装载他条灵感、新建模式复位默认），此处随 key 变化重新执行，
     * 把新字体 id 下发 WebView（JS 侧切换 `--content-font`；未 ready 时由桥缓存、ready 后补发）。
     *
     * 初次组合也会执行一次：与 [load] init 携带的字体 id 相同，重复下发幂等无害。
     */
    LaunchedEffect(contentFontEntry.id) {
        blockNoteController.setFontFamily(contentFontEntry.id)
    }

    /**
     * 正文 WebView 拉丁字体响应式跟随（v2026-09-21：补「英文/数字字体」下行通道）：
     * [ContentFontManager.currentLatinId] 变化（面板「应用」经 VM 回调写入、
     * loadInspiration 装载、新建复位）即下发；空串 = 跟随中文字体，与
     * `inspirations.latinFontId` 的默认语义一致。初次组合与 [load] init 携带值
     * 相同，重复下发幂等无害。
     */
    LaunchedEffect(contentLatinFontId) {
        blockNoteController.setLatinFontFamily(contentLatinFontId)
    }

    /**
     * v2026-09-04 分离式预览：字体面板「pending」本地态（取代旧的「点选即预览」位图复刻）。
     *
     * 平台约束（已核实 compose-ui 1.11.2 源码）：FontFamilyResolver 的 typeface 缓存是
     * **进程级全局单例**（createFontFamilyResolver 各实例共享），按 (族,字重) 长期持有
     * 14~19MB 的 CJK Typeface，反复切字体必然 OOM（设置页+编辑页均复现）。故采用
     * **分离式预览 + Theme 层硬约束**（见 Theme.kt 的 FontResolverPolicy 注入）：
     * - 点选字体**只更新 pending**（面板高亮走 pending），正文区保持原字体、不做任何预览；
     * - 点面板右上角按钮（「应用」/「完成」见 [hasPendingFontChange]）才一次性把 pending
     *   写入 VM → 内容字体真正切换，正文换字、格式工具栏字重按钮（B1/B2/B3 档位与可用态）
     *   随新字体同步更新；**应用后保持面板展开**，便于连续点选多款对比；
     * - 面板预览走 [FontPreviewEngine] 位图（预览池容量 2、预渲染后即清空，常态 0 常驻），
     *   提交后再清一次池，杜绝预览字体与应用字体共存。
     */
    var pendingCjkFontId by remember(contentFontEntry.id) { mutableStateOf(contentFontEntry.id) }
    var pendingLatinFontId by remember(contentLatinFontId) { mutableStateOf(contentLatinFontId) }

    /** 是否存在「已点选但尚未应用」的字体改动（决定面板头按钮显示「应用」还是「完成」）。 */
    val hasPendingFontChange =
        pendingCjkFontId != contentFontEntry.id || pendingLatinFontId != contentLatinFontId

    /**
     * 「H」面板里「正文字号」档位的回显（v2026-09-04 引入，v2026-09-21 随字号迁入 H 面板）：
     * 直接从 [richTextState.currentSpanStyle] 派生（真实来源 = 光标/选区的 SpanStyle），
     * **不另持双份状态**，档位高亮天然跟随正文；未指定时回落 [DEFAULT_BODY_SP]（正文默认 16sp）。
     *
     * ⚠️ 原先同处派生的「颜色回显」（currentColorIdx / customColorHex）已随 Aa 面板删除：
     * 行内色入口统一收敛到「A」面板，其回显依赖 JS 上行、不使用 richTextState。
     */
    val currentFontSizeSp = richTextState.currentSpanStyle.fontSize
        .takeIf { it.isSpecified }?.value?.roundToInt() ?: DEFAULT_BODY_SP

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
    /**
     * 编辑器内容初始化：把整篇 markdown 解析为 Text/Image 交错块
     *
     * - trigger 注册已移入 ViewModel 的 bodyBlocks.registerTriggers（每个新建 Text 块都会注册）
     * - 旧数据迁移已由 ViewModel.loadInspiration 统一处理（contentFormat 已含 token）
     * - **初始化守卫用 bodyBlocks.hasInitialized（controller 持有，随 ViewModel 存活）**：
     *   v2026-09-02 方案A——旋转后 remember 全丢，若守卫也丢失会重跑 initialize，
     *   把 ViewModel 里辛苦保住的块列表与命令栈（撤销历史）一起清空。
     */
    /**
     * 编辑器内容初始化（v2026-09-02 修复「重新进入编辑页正文丢失」回归）
     *
     * **旧实现的问题**：原先是 `LaunchedEffect(contentFormat)` 驱动
     * `bodyBlocks.initialize(contentFormat)`，并用 `bodyBlocks.hasInitialized` 守卫。
     * 但 `contentFormat` 初始值为 ""，首帧会先以 "" 触发 `initialize("")` 并把
     * `hasInitialized` 置 `true`；随后 `loadInspiration` 异步写入真实正文使
     * `contentFormat` 变化、再次触发该效果，却被守卫 `return` 掉 → 真实正文永远
     * 不回填到块列表，表现为「仅标题保留、正文全空」。
     *
     * **新方案（职责拆分）**：
     * - **已有灵感**：初始化改由 `InspirationEditViewModel.loadInspiration()` 在数据库
     *   读取（含旧标签/关联迁移）完成后，用最终 `_contentFormat` 直接驱动，
     *   且用 `!hasInitialized` 守卫避免屏幕旋转重跑（旋转时 ViewModel 存活、不丢编辑）。
     * - **新建灵感**（inspirationId 为 null，没有 loadInspiration 调用）：本效果负责
     *   以 `""` 初始化出一个空文本块，且仅在尚未初始化时执行一次。
     *
     * 因此此处不再监听 `contentFormat`，只处理新建模式的空块初始化。
     */
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (inspirationId == null && !bodyBlocks.hasInitialized) {
            try {
                // triggerDocChanged=false：新建灵感的空块初始化属于载入，不是用户编辑，
                // 不应把 _isDirty 置脏，否则未输入就按返回会误弹"放弃修改？"
                bodyBlocks.initialize("", triggerDocChanged = false)
            } catch (e: Exception) {
                Log.e("InspirationEditScreen", "编辑器初始化异常（已捕获）", e)
            }
        }
    }

    /** 旧 content_blocks 是否已迁移为正文内联媒体（避免重复迁移；迁移按 path 去重、幂等） */
    var hasMigratedBlocks by remember { mutableStateOf(false) }

    /**
     * 路线 4：把旧的 content_blocks（图片/语音）迁移为交错块。
     *
     * 按"markdown 中是否已含该路径"去重——8-30 内联化轮已迁移过的数据，
     * 其 markdown 已包含图片/语音，重复插入会产生双份。
     */
    LaunchedEffect(bodyBlocks.hasInitialized) {
        if (!bodyBlocks.hasInitialized || hasMigratedBlocks) return@LaunchedEffect
        hasMigratedBlocks = true
        /**
         * 进入/重载时先关掉「缩小/恢复」平滑动画（见 [BodyBlocksController.resetImagePropsRestore]）：
         * 本次 [applyImageProps] 回填造成的 shrunk 翻转走 [snap] 瞬时定格，不播动画。
         */
        bodyBlocks.resetImagePropsRestore()
        if (inspirationId == null) {
            /**
             * 新建灵感：无 DB 属性回填，[applyImageProps] 不会翻转 shrunk；
             * 直接放开「缩小/恢复」平滑动画（见 [BodyBlocksController.loadRestoreComplete]）。
             */
            bodyBlocks.markImagePropsRestored()
            return@LaunchedEffect
        }

        try {
            val dbBlocks = viewModel.loadContentBlocks(inspirationId)
            /**
             * 回填图片块持久化属性（备注 / 缩放态，v2026-09-09 启用 v57 预留列）：
             * initialize 只能从 markdown 还原 path，属性按 path 匹配回填；
             * 须在旧数据迁移 append 之前执行（迁移插入的旧附件无属性）。
             */
            bodyBlocks.applyImageProps(
                dbBlocks.filterIsInstance<ContentBlock.Image>().associateBy { it.path }
            )
            /**
             * 属性回填结束：放开「缩小/恢复」平滑动画（见 [BodyBlocksController.loadRestoreComplete]）。
             * 必须在 [applyImageProps] 之后置位——回填造成的 shrunk 翻转走 [snap] 瞬时定格，
             * 不播动画；此后用户主动点「缩小/恢复」才走 [tween] 平滑缩放。
             */
            bodyBlocks.markImagePropsRestored()
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
    /**
     * v2026-08-01 Phase 3：以下状态已移除（关联改为 @ Trigger 内联插入）
     * - cardDetail / cardDetailLoading（LinkedCardPreviewDialog 已移除）
     * - previewingRelation（关联预览 Dialog 已移除）
     * - showRelationPicker（RelationPickerBottomSheet 已移除）
     */

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
                 * 撤销/重做经 Bridge 下发到 JS 编辑器。
                 *
                 * v2026-09-17：JS 侧自绘的「↺ 撤销 / ↻ 重做」胶囊按钮已移除，
                 * 此处为唯一入口；可用态经 `undoState` 上行驱动置灰。
                 *
                 * v2026-09-17 追加：改用 [LongPressRepeatIconButton] 恢复原 JS 按钮的
                 * 长按连发手感（按下即执行 → 450ms 后每 150ms）；
                 * 连发途中若历史栈见底（canUndo/canRedo 翻 false）立即停发。
                 */
                val noteCanUndo = blockNoteController.canUndo && !isLocked
                val noteCanRedo = blockNoteController.canRedo && !isLocked
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LongPressRepeatIconButton(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤销",
                        onAction = { blockNoteController.undo() },
                        enabled = noteCanUndo,
                        canRepeat = noteCanUndo,
                    )
                    LongPressRepeatIconButton(
                        icon = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "重做",
                        onAction = { blockNoteController.redo() },
                        enabled = noteCanRedo,
                        canRepeat = noteCanRedo,
                    )
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
                /** 单一状态直传：三个面板的展开/互斥与按钮激活态都由它派生（v2026-09-21 收敛） */
                openPanel = openPanel,
                currentCjkId = pendingCjkFontId,
                currentLatinId = pendingLatinFontId,
                hasPendingChange = hasPendingFontChange,
                currentFontSize = currentFontSizeSp,
                richTextState = richTextState,
                onPhotoClick = {
                    showImagePicker = true
                },
                onVoiceClick = {
                    showVoiceRecordSheet = true
                },
                /**
                 * ⚠️ 已知边界（BlockNote 迁移遗留，UI 保留但行为待桥接）
                 *
                 * 原实现是在光标处插入 `#` 触发 hashtag 建议弹窗；正文切换到
                 * BlockNote WebView 后，`richTextState` 已不承载正文内容，
                 * 此处写入不会反映到编辑区。后续需经 Bridge 下发 `insertText` 命令，
                 * 或改由 JS 侧编辑器自带 trigger 菜单承担。
                 */
                onTagClick = {
                    if (!isLocked) {
                        richTextState.addTextAfterSelection("#")
                    }
                },
                /**
                 * ⚠️ 已知边界（同上）：@ 提及按钮待桥接到 BlockNote 侧。
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
                /**
                 * 字体选择按钮（工具栏 T，v2026-09-03 新增）：
                 * 切换字体面板展开/收起；展开时先收起软键盘——键盘与面板不同屏共存，
                 * 面板高度 = 键盘高度（BottomBar 内 WindowInsets.ime 记录），展开即占据原键盘位。
                 *
                 * 展开后由 [isFormatPanelOpen] → `suppressIme` 继续压住键盘（v2026-09-21）：
                 * 用户此时在正文聚焦光标 / 多选也不会把键盘唤回来。
                 *
                 * 互斥切换与收键盘都交给统一的 [togglePanel]（v2026-09-21 抽取）；
                 * 此处只保留字体面板**专属**的副作用：展开前把 pending 重置为当前内容字体
                 * （面板高亮须与正文实际字体一致；收起分支不需要重置）。
                 */
                onFontPickerClick = {
                    if (openPanel != EditBottomPanel.FONT) {
                        pendingCjkFontId = contentFontEntry.id
                        pendingLatinFontId = contentLatinFontId
                    }
                    togglePanel(EditBottomPanel.FONT)
                },
                /**
                 * 字体面板头按钮（「应用」/「完成」，语义见 [hasPendingFontChange]）：
                 * **有改动 = 应用但不收起**——把 pending 经 VM 回调写入 [ContentFontManager]
                 * （v2026-09-21 修复：原实现只给正文 WebView 单发 setFontFamily、完全绕过
                 * 字体状态链，导致标题排版不变、字重探测不跟随、字体不持久化、重开面板回显
                 * 旧字体）。VM 回调内部会：更新 ContentFontManager（标题
                 * LocalContentTypography 与 B1/B2/B3 字重探测即时跟随）、置脏（保存时写回
                 * `inspirations.fontId/latinFontId`）；正文 WebView 换字由上方
                 * LaunchedEffect(contentFontEntry.id) 响应式下发，不再此处单发。
                 *
                 * 应用后 ContentFontManager 与 pending 一致 → remember key 变化自动重置
                 * pending → [hasPendingFontChange] 归 false → 按钮变回「完成」，面板保持
                 * 展开便于连续对比；**无改动 = 「完成」= 收起面板**（键盘不自动弹回，由
                 * 输入框焦点决定）。
                 *
                 * 面板收起即解除键盘抑制（[isFormatPanelOpen] → false，v2026-09-21）：
                 * 仅恢复"可唤起"能力，不主动弹回键盘——用户再点一次正文/标题即恢复输入。
                 */
                onFontPanelDismiss = {
                    val cjkChanged = pendingCjkFontId != contentFontEntry.id
                    val latinChanged = pendingLatinFontId != contentLatinFontId
                    if (cjkChanged || latinChanged) {
                        /** 「应用」：pending 写入字体状态链（标题排版/字重探测/持久化随动） */
                        viewModel.onCjkFontSelected(pendingCjkFontId)
                        viewModel.onLatinFontSelected(pendingLatinFontId)
                    } else {
                        /** 「完成」：无待应用改动，收起面板 */
                        openPanel = null
                    }
                },
                /**
                 * ⚠️ v2026-09-21：原「Aa 字号与颜色」按钮回调（onSizeColorPanelClick /
                 * onSizeColorPanelDismiss）已随按钮与面板一并删除——字号迁入「H」面板（见下
                 * onFontSizeSelect），颜色与新「A」面板的「选中文字色」重叠。
                 */
                /**
                 * 标题按钮（H，v2026-09-21 新增）→ 切换**内联「标题与字号」面板**
                 *
                 * 面板内容分三类：「正文字号」（8 档，原 Aa 面板迁来）、「普通标题 H1–H6」、
                 * 「可折叠标题」；标题键复用既有的块类型转换回调 [onTransform] 下发
                 * `heading1`–`heading6` / `toggleHeading` 系列，字号复用 [onFontSizeSelect]，
                 * 因此这里只负责开关面板，无新增下行通道。
                 */
                onHeadingPanelClick = {
                    togglePanel(EditBottomPanel.HEADING)
                },
                /** 标题与字号面板头「完成」：收起面板（点选即生效，无 pending 两段式） */
                onHeadingPanelDismiss = {
                    openPanel = null
                },
                /**
                 * 正文字号点选（v2026-09-21 由 Aa 面板迁入「H」面板；即时生效）：
                 * 经 Bridge 下发 `format("fontSize", <css 值>)`，JS 侧
                 * `addStyles({fontSize})` / `removeStyles({fontSize:"16px"})`；
                 * 点默认档（[DEFAULT_BODY_SP] = 16sp）只清除不写入（回落正文默认）。
                 *
                 * ⚠️ 值必须是 **Int 直接插值的 `"${sp}px"`**（v2026-09-21 修复）：
                 * 原写法 `"${sp.sp}px"` 中 `sp.sp` 是 [TextUnit]，插值调用其
                 * `toString()` 得到 `"18.0.sp"`，拼出 `"18.0.sppx"` 非法 CSS 值——
                 * 浏览器忽略 font-size，表现为「所有非默认档字号全部不生效」。
                 */
                onFontSizeSelect = { sp ->
                    // 正文字号档位点选 → fontSize 下行（默认档 = 清除，回落正文默认）
                    blockNoteController.format(
                        "fontSize",
                        if (sp == DEFAULT_BODY_SP) "default" else "${sp}px"
                    )
                },
                /**
                 * 中文字体选择：只更新 pending 高亮（分离式预览，正文此时不换字）；
                 * 真正应用发生在点面板头「应用」时（见 onFontPanelDismiss）。
                 */
                onCjkFontSelect = { fontId ->
                    pendingCjkFontId = fontId
                },
                /** 英文/数字字体选择：同上，只更新 pending（再点已选项回调空串 = 跟随中文） */
                onLatinFontSelect = { fontId ->
                    pendingLatinFontId = fontId
                },
                /**
                 * 格式化操作全部经 Bridge 下发到 BlockNote（JS 侧 Yjs 历史自行记录，
                 * 与宿主撤销/重做按钮共用同一历史栈，无需宿主侧手动快照）。
                 *
                 * 字重档位桥接为加粗 toggle（HTML 无多档字重概念）。
                 */
                onSetFontWeight = { weight ->
                    blockNoteController.format("bold")
                },
                onToggleItalic = {
                    blockNoteController.format("italic")
                },
                onToggleUnderline = {
                    blockNoteController.format("underline")
                },
                onToggleStrikethrough = {
                    blockNoteController.format("strike")
                },
                onInsertUnorderedList = {
                    blockNoteController.format("bulletList")
                },
                onInsertOrderedList = {
                    blockNoteController.format("numberedList")
                },
                onIncreaseIndent = {
                    /** 增加缩进：对聚焦正文块做列表层级 +1（普通文本行自动转列表） */
                    blockNoteController.format("indent")
                },
                onDecreaseIndent = {
                    /** 减少缩进：对聚焦正文块做列表层级 -1（一级再减退出列表） */
                    blockNoteController.format("outdent")
                },
                onInsertDivider = {
                    /** 插入分割线：经 Bridge 插入（点击分割线可弹样式工具条切换） */
                    if (!isLocked) {
                        blockNoteController.insertDivider()
                    }
                },
                onToggleCheckbox = {
                    /** 复选框：聚焦块在 复选框块 ↔ 普通块 间切换，可撤销；
                     *  markdown 以 `- [ ] `/`- [x] ` 持久化 */
                    if (!isLocked) {
                        blockNoteController.format("checkList")
                    }
                },
                isCheckboxActive = bodyBlocks.isFocusedBlockCheckbox,
                canIncreaseIndent = bodyBlocks.canIncreaseIndent,
                canDecreaseIndent = bodyBlocks.canDecreaseIndent,
                onTransform = { action ->
                    /** 块类型转换：经 Bridge 下发到 JS 编辑器 */
                    blockNoteController.format("transform", action)
                },
                onTransformParagraph = {
                    blockNoteController.format("transform", "paragraph")
                },
                onTransformEnabled = true,
                boldSingleTier = true,
                onInsertMedia = { kind ->
                    /** Media 组 → 宿主选择器（图片复用相册选择器） */
                    when (kind) {
                        "image" -> showImagePicker = true
                        "video" -> mediaVideoLauncher.launch("video/*")
                        "audio" -> mediaAudioLauncher.launch("audio/*")
                        "file" -> mediaFileLauncher.launch("*/*")
                    }
                },
                /**
                 * 颜色按钮（A）→ 切换**内联颜色面板**（v2026-09-21：原为打开 AlertDialog 弹窗）
                 *
                 * 互斥、收键盘、收起都交给统一的 [togglePanel]（本面板无专属副作用）；
                 * 展开后由 [isFormatPanelOpen] 继续抑制正文/标题唤起键盘。
                 */
                onColorPanelClick = {
                    togglePanel(EditBottomPanel.COLOR)
                },
                /** 颜色面板头「完成」：收起面板（颜色点选即时生效，无 pending 两段式） */
                onColorPanelDismiss = {
                    openPanel = null
                },
                /**
                 * 四组颜色点选（v2026-09-21）
                 *
                 * ⚠️ 与弹窗时代的行为差异：点选后**不再关闭**面板——与 H 面板的
                 * "点选即时生效、面板保持展开"一致，便于连续微调；收起由「完成」或
                 * 再点一次 A 按钮触发。
                 *
                 * 通道差异（易错点）：行内两组走 `format(...)`（hex 自由值，作用于选区文字），
                 * 块级两组走 `setBlockColor(...)`（BlockNote 色名，作用于整段）。
                 */
                onInlineTextColorSelect = { hex ->
                    blockNoteController.format("textColor", hex ?: "default")
                },
                onInlineBackgroundColorSelect = { hex ->
                    blockNoteController.format("backgroundColor", hex ?: "default")
                },
                /** 段落文字色：只传 textColor 维度（backgroundColor 传 null = 不改动该维度） */
                onBlockTextColorSelect = { name ->
                    blockNoteController.setBlockColor(textColor = name ?: "default")
                },
                /** 段落背景色：只传 backgroundColor 维度（textColor 传 null = 不改动该维度） */
                onBlockBackgroundColorSelect = { name ->
                    blockNoteController.setBlockColor(backgroundColor = name ?: "default")
                },
                onOpenEmojiPicker = {
                    if (!isLocked) blockNoteController.openEmojiPicker()
                },
                onAlignLeft = {
                    blockNoteController.format("alignLeft")
                },
                onAlignCenter = {
                    blockNoteController.format("alignCenter")
                },
                onAlignRight = {
                    blockNoteController.format("alignRight")
                },
                onInsertLink = {
                    /** 弹 URL 输入对话框 → format createLink 下发 */
                    showLinkDialog = true
                },
                onToggleCodeSpan = {
                    blockNoteController.format("codeSpan")
                },
                /**
                 * 块操作（v1.11）
                 *
                 * 来源：原 BlockNote 侧边菜单（⋮⋮ 手柄）的**点击菜单**。按用户决策，
                 * 其中「删除块 / 表头行 / 表头列」移入格式工具栏的「块操作」入口，
                 * 手柄本身只保留拖拽重排（原生手势，无法按钮化）。
                 *
                 * ⚠️ v2026-09-21：原「块颜色」项（onSetBlockColor）已从该菜单移出，
                 * 改为底部工具栏「A」按钮的**颜色面板**里的「段落文字色 / 段落背景色」
                 * （见 [com.corgimemo.app.ui.screens.inspiration.components.ColorStylePanel]），
                 * 故此处不再传 onSetBlockColor。
                 *
                 * 可用态与回显（是否表头）由 JS 侧判定后经 `blockState` 上行，
                 * 见 [com.corgimemo.app.ui.screens.probe.BlockState]。
                 * 此处与 onToggleCodeSpan 等块内操作一致，不再单独检查 isLocked
                 * （锁定态由 onTransformEnabled 统一承担）。
                 */
                onDeleteBlock = {
                    blockNoteController.deleteBlock()
                },
                onSetTableHeader = { target, enabled ->
                    blockNoteController.setTableHeader(target, enabled)
                },
                /**
                 * 块上移 / 下移（v1.11.5）
                 *
                 * 这是原 ⋮⋮ 手柄「拖拽重排」的替代实现：实测该手柄的拖拽纯用
                 * HTML5 原生 Drag & Drop，在 Android WebView 的触摸下不触发
                 * （真机"按住无反应"），故手柄已整体删除（`sideMenu={false}`）。
                 */
                onMoveBlockUp = {
                    blockNoteController.moveBlockUp()
                },
                onMoveBlockDown = {
                    blockNoteController.moveBlockDown()
                },
                blockState = blockNoteController.blockState,
                /**
                 * 锁定态整条格式工具栏禁用（v1.11.1）
                 *
                 * ⚠️ 不能只依赖上面的 `setReadOnly`：块操作走的是 `removeBlocks` /
                 * `updateBlock` 这类**程序化 API，不受编辑器只读状态限制**——
                 * 只读挡得住用户打字，挡不住"点一下删除块"。故工具栏必须自己再拦一道。
                 */
                toolbarEnabled = !isLocked,
                modifier = Modifier.safeAreaForEditBar()
            )
        }
    ) { innerPadding ->
        /**
         * ⚠️ v1.11.9 诊断埋点（**临时**，定位"键盘弹出 WebView 不收缩"后移除）：
         * 打印键盘 insets 与 Scaffold innerPadding 的实时值。
         */
        val diagImeBottomPx = WindowInsets.ime.getBottom(density)
        /**
         * 键盘是否收起（v1.11.9 修复）：**仅在键盘收起时才允许更新编辑区 min-height**。
         *
         * 真机日志证实了失控循环：键盘弹出 → BottomBar 的"面板"接管键盘位 →
         * WebView（weight）变矮 → `onSizeChanged` 把变矮后的值经 `setEditorMinHeight`
         * 写进 `.bn-editor { min-height }` → Android WebView 为避让键盘进一步压缩
         * 自己的视口（innerHeight 实测从 620 一路坍缩到 28px）→ 触发新的
         * `onSizeChanged` → min-height 再变小 → …… 正反馈直到视口只剩一行高。
         *
         * 修法：min-height 只在 **ime = 0** 时更新。键盘弹出期间冻结上一次的
         * 稳定值——长文档不受影响（内容高 > min-height），空文档在压缩后的
         * 视口里变为"可滚动"，光标始终可见；键盘收起后自然恢复。
         * 必须用 state 存 ime 值：`onSizeChanged` 是普通回调（非 Composable），
         * 捕获普通局部变量会是旧值，读 state 才拿得到最新值。
         */
        val imeBottomState = remember { mutableStateOf(0) }
        LaunchedEffect(diagImeBottomPx) {
            imeBottomState.value = diagImeBottomPx
            Log.d(
                "BlockNoteEditor",
                "diag | ime bottom=${diagImeBottomPx}px" +
                    " (${with(density) { diagImeBottomPx.toDp() }})"
            )
        }
        LaunchedEffect(innerPadding) {
            Log.d("BlockNoteEditor", "diag | innerPadding: $innerPadding")
        }

        /**
         * 内容区布局：单层Column，Modifier顺序决定背景范围。
         * - background 在 horizontal padding 之前 → 用户自选背景色铺满全宽无空隙
         * - 默认不绘制（[contentBackgroundPaint] = Transparent），让页面主题背景透出
         * - 不使用 Box 包裹 → 避免触摸事件被外层拦截导致编辑器无法输入
         *
         * **v1.11.8（方案 A）**：本 Column **不再使用 `verticalScroll`** ——
         * 改为"标题/日期行固定 + 正文 WebView 用 `weight(1f)` 占满剩余空间、滚动由
         * WebView 内部承担"。这样编辑区高度 = **视口真实剩余高度**，
         * 取代原先 `屏高 × 62%` 的经验值，且软键盘弹出时随 `innerPadding` 自动收缩。
         * 详见 `docs/编辑区高度方案A评估.md`。
         *
         * ⚠️ 此处铺的是 [contentBackgroundPaint]（"要不要真铺"），**不是**
         * [contentBackgroundColor]（"实际生效色"）——后者已把 Transparent 回落成主题
         * background，若拿它来铺，未自选背景色时会被强行盖上一层同色（视觉上等价但会
         * 阻断未来可能的毛玻璃/渐变背景透出）。两个语义显式分开，见其 KDoc。
         */
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                /** 背景色铺满全宽（在内容padding之前设置） */
                .background(contentBackgroundPaint)
                /** 内容区内边距在背景之后，不影响背景范围 */
                .padding(horizontal = 8.dp),
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
             * 双向同步策略（v2026-09-09 修订，修复"标题无法全选"）：
             * 1. viewModel.title → state：loadInspiration / 外部 setTitle 时同步，用 setText() 纯文本设置；
             *    仅在「文本不一致」且「选区已折叠」时写入，避免把用户正在框选的选区折叠到行尾。
             * 2. state → viewModel.title：用户输入时同步，用 annotatedString.text 读取纯文本；
             *    本方向只上送、绝不触碰选区，框选过程零干扰。
             * 3. 防循环：用 if (currentText != title) 判断避免重复触发。
             */
            /**
             * 系统浮动工具栏句柄（长按弹出的"全选 / 复制 / 粘贴"菜单）。
             * 供下方"按下即收起工具栏"的被动观察使用。
             */
            val textToolbar = LocalTextToolbar.current

            val titleRichTextState = rememberRichTextState()
            /**
             * 标题为纯文本（无列表），此处同步关闭列表缩进仅作一致性兜底，
             * 与编辑页正文块、详情页正文保持一致（RichTextConfig.listIndent=0）。
             *
             * ⚠️ 必须包 remember 只执行一次（v2026-09-09 全选塌缩修复）：
             * config 的每个 setter 都会无条件触发 updateRichParagraphList 全量重建，
             * 而重建把 selection 折叠为单光标（TextRange(selection.min)）。此前本行
             * 在每次重组都执行——点「全选」写入 (0,len) 后下一帧重组又跑到这里，
             * 选区被打回 (0,0)，表现为"全选后整段不高亮、光标跑到最左侧"。
             * 库侧 RichTextConfig setter 已同步加值守卫双保险。
             */
            remember(titleRichTextState) { titleRichTextState.config.listIndent = 0 }

            /**
             * 上一次观察到的标题文本（v2026-09-15）：用于区分「键盘换行」与「多行粘贴」——
             * 新文本剥掉换行后与它完全一致 ⇒ 本次变化只多了一个换行 ⇒ 键盘换行（跳正文）；
             * 否则（同时还多了别的字符）⇒ 粘贴，只做单行化、焦点留在标题。
             */
            val lastObservedTitleText = remember { mutableStateOf("") }

            /**
             * 标题**单行化**（v2026-09-15）：摘掉 [rawText] 中所有换行符后写回标题，
             * 并把光标 / 选区按「摘掉换行后其前面还剩多少字符」重算，尽量停在用户原编辑位置。
             *
             * 写回用 setText（纯文本，不解析 markdown），随后立刻上送 ViewModel——
             * 调用方在换行分支里 `return` 掉了常规的 state→VM 同步，这条不能漏，
             * 否则（如粘贴）新文本不会落库。setText 默认把选区折叠到行尾，故写完要补写 selection。
             */
            fun collapseTitleNewlines(rawText: String) {
                val cleaned = rawText.replace("\n", "")
                /** 摘掉换行后，原索引之前还剩多少字符 */
                fun mapIndex(index: Int): Int {
                    val clamped = index.coerceIn(0, rawText.length)
                    return rawText.substring(0, clamped).count { it != '\n' }
                }
                val oldSelection = titleRichTextState.selection
                /** 用 minOf/maxOf（kotlin.comparisons 默认导入），不依赖 TextRange.min/max 的导入形态 */
                val mappedStart = mapIndex(minOf(oldSelection.start, oldSelection.end))
                    .coerceIn(0, cleaned.length)
                val mappedEnd = mapIndex(maxOf(oldSelection.start, oldSelection.end))
                    .coerceIn(0, cleaned.length)
                titleRichTextState.setText(cleaned)
                titleRichTextState.selection = TextRange(mappedStart, mappedEnd)
                if (cleaned != title) viewModel.setTitleWithRecommendation(cleaned)
            }

            /**
             * 换行落点（v2026-09-15）：标题只允许单行，换行即「进入正文书写」——
             * 焦点交到正文首块的首行最前面（首块是图片 / 分割线时由 controller
             * 在文档最前懒插入一个载体空块后再落焦，见 [BodyBlocksController.focusBodyFirstLine]）。
             *
             * 三条路径共用本函数：硬键盘回车（onPreviewKeyEvent 拦截）、
             * 软键盘换行（IME commitText("\n")，靠文本变更检测兜住）、多行粘贴。
             */
            fun moveCaretToBodyHead() {
                bodyBlocks.focusBodyFirstLine()
            }

            /**
             * 单向同步：viewModel.title → state（loadInspiration / 外部 setTitle 时回填）。
             * 仅在「文本真的不一致」且「用户当前没有正在选择的选区」时才 setText：
             * - loadInspiration / 语音回填时选区是折叠的，正常写入；
             * - 用户正在框选标题时即使 title 因外部原因重发，也绝不调用 setText 把选区折叠到行尾
             *   （setText 默认 selection=TextRange(text.length)，见 RichTextState.kt:5418）。
             * 仍保留 currentText != title 守卫，避免自我回环。
             */
            LaunchedEffect(title) {
                val currentText = titleRichTextState.annotatedString.text
                if (currentText != title && titleRichTextState.selection.collapsed) {
                    /** 用 setText 设置纯文本，不解析任何 markdown/html 格式 */
                    titleRichTextState.setText(title)
                }
            }

            /**
             * 单向同步：state → ViewModel（用户输入时）。
             * 仅文本变化才上送，且本方向只调用 setTitleWithRecommendation、绝不触碰选区，
             * 因此选区伸缩不会触发、也不会破坏用户正在进行的框选（选区零干扰）。
             * 标题为纯文本无背景色 span，选区变化不会重建 annotatedString，本 effect 不会在框选时重跑。
             *
             * v2026-09-15 追加「换行拦截」，与正文块 `\n → 拆块` 同一范式：
             * 标题只允许单行，硬键盘回车已被 onPreviewKeyEvent 拦掉（`\n` 根本不进文本），
             * 这里兜住另外两条会产生 `\n` 的路径——软键盘换行（IME 直接 commitText("\n")）与
             * 多行粘贴。剥掉换行后按「本次是否只多了一个换行」区分二者：
             * - 键盘换行 ⇒ 焦点交到正文首块首行最前（[moveCaretToBodyHead]）；
             * - 多行粘贴 ⇒ 仅单行化，焦点留在标题（用户仍在标题里编辑）。
             *
             * 注意：本分支必须先于常规同步 return，绝不能把带换行的文本写进 ViewModel.title。
             */
            LaunchedEffect(titleRichTextState.annotatedString) {
                val newText = titleRichTextState.annotatedString.text
                val previousText = lastObservedTitleText.value
                lastObservedTitleText.value = newText
                if (!isLocked && newText.contains('\n')) {
                    /** 只多了一个换行（其余字符与上一次完全一致）⇒ 判定为键盘换行 */
                    val isKeyboardNewline = newText.replace("\n", "") == previousText
                    collapseTitleNewlines(newText)
                    if (isKeyboardNewline) moveCaretToBodyHead()
                    return@LaunchedEffect
                }
                if (newText != title && !isLocked) {
                    viewModel.setTitleWithRecommendation(newText)
                }
            }

            RichTextEditor(
                state = titleRichTextState,
                modifier = Modifier
                    .fillMaxWidth()
                    /**
                     * 回车即进正文（v2026-09-15）：标题是单行输入，硬键盘回车 / 小键盘回车
                     * 直接吞掉（换行符根本不进标题文本），并把焦点交到正文首块首行最前面。
                     *
                     * 与块级编辑器同一范式：onPreviewKeyEvent 在按键下发阶段**先于**输入框被调用，
                     * 返回 true 即消费事件、阻止输入框插入换行。软键盘换行走 IME commitText("\n")，
                     * 拿不到按键事件，由上方 annotatedString 观察者的换行拦截兜住——
                     * 两条通道最终都汇到 [moveCaretToBodyHead]，行为一致。
                     */
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (isLocked) return@onPreviewKeyEvent false
                        when (keyEvent.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                moveCaretToBodyHead()
                                true
                            }
                            else -> false
                        }
                    }
                    /**
                     * 按下即收起系统浮动工具栏（v2026-09-09）：
                     * 长按标题弹出含"全选"的浮动工具栏后，再次轻点标题行即隐藏工具栏。
                     *
                     * `awaitFirstDown(requireUnconsumed = false)` 只**观察**按下、不消费事件，
                     * 故 TextField 自身的点击定位光标 / 长按选择 / 双击选词手势完全不受影响；
                     * 按下时刻工具栏尚未弹出，长按时 hide() 为空操作，不会误伤长按。
                     */
                    .pointerInput(textToolbar) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitFirstDown(requireUnconsumed = false)
                                textToolbar.hide()
                            }
                        }
                    }
                    /**
                     * 面板展开期间禁用标题点击（v2026-09-21）：
                     * 「T / H / A」面板以键盘高度占据键盘位，此时若点标题重新聚焦，
                     * 软键盘会把面板顶走（与正文 [suppressIme] 同一诉求）。
                     *
                     * 在 `PointerEventPass.Initial` 阶段（父→子）消费指针事件即可，
                     * BasicTextField 的点击定位手势走 Main 阶段，拿不到事件自然不聚焦。
                     * **不采用 `enabled = false`**：那会连带把文字/占位符切成禁用色、
                     * 并强行清掉已有焦点；本写法视觉零变化，展开/收起各一个状态切换。
                     */
                    .then(
                        if (isFormatPanelOpen) {
                            Modifier.pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Initial)
                                            .changes.forEach { it.consume() }
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    ),
                placeholder = {
                    Text(
                        "标题",
                        style = LocalContentTypography.current.headlineMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                },
                readOnly = isLocked,
                textStyle = LocalContentTypography.current.headlineMedium.copy(
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
                 *
                 * v2026-09-18 修订（三段垂直可见空白精确 6.dp）：
                 * 标题编辑框下沿 → 日期行上沿 的可见空白需 = 6.dp。
                 * - 标题框自身 contentPadding 拆成 top=8.dp（保留顶部呼吸空间，与顶栏间距不变）、
                 *   bottom=0.dp（去掉底部内边距，避免叠加 Spacer 后超标）；
                 * - 紧跟其下的 `Spacer(UiDimensions.inspirationTitleToMetaGap)`
                 *   （6.dp 盒间隙）承担标题→时间行间距。
                 */
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 8.dp,
                    bottom = 0.dp,
                    start = 0.dp,
                    end = 0.dp
                ),
                /**
                 * v2026-09-18 二次修订（真机截图逐像素实测「标题↔时间行」可见间距 ≈30dp，
                 * 远大于设计的 6dp）：根因是 RichTextEditor 的 minHeight 默认
                 * Material3 TextField 标准 56dp —— 标题框被强撑到 56dp，而文字行盒只有
                 * 36dp（contentPadding top8 + 36 + 剩余 12dp），多出的 12dp 幽灵空隙
                 * 全部堆在标题文字下方。传 0.dp 关闭强制撑高，标题框高度回归
                 * contentPadding(8+0) + 行盒 36 = 44dp。
                 */
                minHeight = 0.dp
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
             * v2026-09-18 修订：标题↔日期行↔WebView 三段垂直「可见空白」精确相等，均为 6.dp。
             * - 标题框 contentPadding 拆为 top=8.dp / bottom=0.dp；其后 `Spacer(6.dp)` 承担 6.dp
             *   → 标题文本下沿到日期行上沿 = 0 + 6 = 6.dp。
             * - 日期行之后新增 `Spacer(6.dp)`，WebView 编辑器首部留白（editor.css 已压到 0）不再额外撑高
             *   → 日期行文本下沿到 WebView 首块文本上沿 = 6.dp。
             *
             * v2026-09-18 二次修订（真机截图逐像素复测推翻「视觉等距」结论）：
             * 6.dp 等距只是**布局盒间距**；肉眼看到的是**文字墨迹间距**，二者之间还隔着
             * 各级行盒的死空间。实测墨迹间距 ≈30dp / ≈17dp，根因三个：
             * ① 标题框被 RichTextEditor 默认 minHeight=56dp 撑高（+12dp，见上方 minHeight 注释）；
             * ② 时间行 Text 未显式 lineHeight，merge 了 bodyLarge 的 24.sp 行盒（上下各 ~5dp）；
             * ③ 标题 36.sp 行盒的字形下沉空间 ~7dp（字体固有，只能靠 Spacer 补偿）。
             * ①② 已修；③ 的补偿留待复测后微调两个 Spacer（第二步，数据驱动）。
             *
             * v2026-09-18 三次修订（第二步定版，复测数据驱动）：
             * 修复①②后真机复测（密度 2.2，由正文行距 53px=16px×1.5 与屏高 873dp 双锚点验证）：
             * 墨迹间距 ≈15.5dp / ≈13.6dp，残差 1.8dp = 正文首行 1.5 倍行距的行盒上方死空间
             * (~4.3dp) − 时间行上方(~1.8dp)。补偿：时间行→正文 Spacer 6→8.dp，
             * 两段墨迹间距均 ≈15.5dp（差 0.17dp，亚像素级）。editor.css 首块归零规则实测生效
             * （若未生效 G2 还会再大 3dp，与实测不符）。
             * 间距常量收敛至 UiDimensions.inspirationTitleToMetaGap / inspirationMetaToBodyGap。
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

            /** 标题 → 时间行 盒间隙（常量收敛至 UiDimensions，墨迹间距见其 KDoc） */
            Spacer(modifier = Modifier.height(UiDimensions.inspirationTitleToMetaGap))

            /** 时间戳 + 字数行（中间用竖线分隔，与参考图一致） */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timestampText,
                    fontSize = 12.sp,
                    /**
                     * v2026-09-18（真机实测间距修复）：必须显式给 lineHeight=16.sp
                     * （对齐 bodySmall 12/16 规格）。不显式给会 merge 主题 bodyLarge 的
                     * 24.sp 行高 → 12sp 文字套 24dp 行盒，墨迹上下各多 ~5dp 行距空间，
                     * 把「标题↔时间行↔正文」两段可见间距都撑大（实测 30dp/17dp）。
                     */
                    lineHeight = 16.sp,
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
                    /** 与左侧时间戳同规格：显式压掉 bodyLarge 继承的 24.sp 行高（见上注释） */
                    lineHeight = 16.sp,
                    color = Color(0xFF999999),
                    letterSpacing = 0.5.sp
                )
            }

            /** 时间行 → 正文 盒间隙：比上段多 2dp 墨迹补偿（正文首行行距上方死空间 ~4.3dp），
             *  两段墨迹间距均 ≈15.5dp（v2026-09-18 截图逐像素定版，见上方三次修订注释）。
             *  编辑器首部留白已在 editor.css 压到 0（已实测生效），此处 Spacer 即最终盒间隙。 */
            Spacer(modifier = Modifier.height(UiDimensions.inspirationMetaToBodyGap))

            // v2026-09-18 修订：日期行↔WebView 的间距改由上方宿主 Compose 的 6.dp Spacer 承担，
            // 不再依赖 WebView 编辑器自身首部留白（已在 editor.css 归零），避免两段距离来源不一致。

            /** ===== 正文内容编辑器区域（BlockNote WebView） ===== */

            /**
             * 正文编辑区 = BlockNote WebView。
             * - 页面其余 UI（标题/标签/底部栏/位置提醒等）保持不变；
             * - 数据链路：changed 防抖 markdown → viewModel.setContentFormat（isDirty 置脏，
             *   复用原保存流程）；载入由 load()（contentLoaded 门控，见顶部 LaunchedEffect）；
             * - #标签 / @提及 建议弹层由 JS 侧编辑器的 trigger 菜单承担（宿主侧不再订阅）。
             *
             * v2026-09-17 修复（占满编辑区 + 背景色对齐主题）：
             * 1. **背景色**：`backgroundColor` 直接传 [contentBackgroundColor]——
             *    该值已在顶部收敛为「内容区实际生效色」的**唯一真值**（未自选时回落主题
             *    `background`，暖米色 #FFFBF5 / 暗色 #1A0F08），此处不再重复求值。
             *    这样 WebView 内部 `.bn-editor` 与 body 与页面必然同色，消除"画中画"
             *    白底圆角框；也杜绝了"两处独立回落"日后漂移的可能。
             * 2. **高度（v2026-09-17 用 `heightIn(min = 屏高 × 62%)` 保底，v1.11.8 已改为方案 A）**：
             *    原先外层 Column 带 `verticalScroll`，子项高度约束变成 Infinity，
             *    WebView 只能按内容高撑开（内容少时塌成几行），故用 62% 屏高保底。
             *    但那是**经验值**，与真实可用空间脱钩，且键盘弹出时不会收缩。
             *
             *    **v1.11.8 改为方案 A**：去掉外层 `verticalScroll`，WebView 用
             *    `weight(1f)` 占满剩余空间 → 编辑区高度 = **视口真实剩余高度**
             *    （视口 − 状态栏 − 顶栏 − 底栏 − 标题 − 日期行），并随 `innerPadding`
             *    （含软键盘）自动收缩。滚动改由 WebView 内部承担。
             *
             *    当初搁置方案 A 的理由是"会波及滚动清除选中态与图片画廊 inset"，
             *    经核查两条链路均已是死代码（详见 `docs/编辑区高度方案A评估.md`），
             *    故本轮实施。
             */

            BlockNoteEditorWebView(
                controller = blockNoteController,
                onMarkdownChanged = { md ->
                    // v2026-09-18 修复「编辑页统计不到字数」：BlockNote 只回写 contentFormat，
                    // 需同步把 Markdown 转为纯文本写入 content，编辑页字数行才能实时更新、
                    // 且保存时写入数据库的 content 才与正文一致。
                    viewModel.setContentFormat(md)
                    viewModel.setContent(InspirationTextUtils.markdownToPlainText(md))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    /**
                     * 方案 A（v1.11.8）：占满 Column 剩余空间 → 编辑区高度 = **视口真实剩余高度**。
                     * 取代原先 `heightIn(min = 屏高 × 62%)` 的经验值，并随 `innerPadding`
                     * （含软键盘弹出）自动收缩。滚动改由 WebView 内部承担。
                     */
                    .weight(1f)
                    /**
                     * 实测高度 → 下发给 JS 作为 `.bn-editor` 的 min-height（复用 v1.11.6 通道）。
                     *
                     * 为什么用 [onSizeChanged] 而不是再算一次屏幕比例：`weight(1f)` 之后 WebView
                     * 的高度是**系统布局出来的真实值**，比任何估算都准；且键盘弹出/收起、旋转屏
                     * 都会重新回调，不需要手动维护依赖列表。
                     *
                     * ⚠️ 该回调发生在布局之后、**远早于 JS ready**，所以命令会先进入
                     * `BlockNoteBridgeController` 的 pendingCommands，待 init 后按序发出
                     * ——因此不存在"首帧编辑区矮一下再变高"的闪烁。
                     */
                    .onSizeChanged { size ->
                        val heightDp = with(density) { size.height.toDp() }
                        // v1.11.9 诊断埋点：WebView 实际被分配的高度（键盘弹出前后对比的关键值）
                        Log.d(
                            "BlockNoteEditor",
                            "diag | webview onSizeChanged: ${size.width}x${size.height}px" +
                                " = ${heightDp.value}dp"
                        )
                        /**
                         * ⚠️ 仅在键盘收起时下发（v1.11.9 修复失控循环）：
                         * 键盘弹出期间 BottomBar 面板接管键盘位、WebView 被压缩，
                         * 此时若跟随下发只会让 `.bn-editor` 的 min-height 一路变小，
                         * 与 Android WebView 的键盘避让互相激发，视口坍缩到一行高
                         * （真机实测 innerHeight 620 → 28）。冻结为键盘收起时的稳定值，
                         * 键盘收起后自然恢复。
                         */
                        if (imeBottomState.value == 0) {
                            blockNoteController.setEditorMinHeight(heightDp.value)
                        }
                    },
                /** 唯一真值：内容区实际生效背景色（Transparent 已在源头回落为主题 background） */
                backgroundColor = contentBackgroundColor,
                /**
                 * 面板展开期间抑制软键盘（v2026-09-21）：
                 * 用户在正文中聚焦光标 / 多选时不再唤起 IME，键盘不会把「T / H / A」面板
                 * 顶走、也不会压缩 WebView 视口；**光标与选区能力完全保留**。
                 * 收起面板后本参数回 false，键盘不主动弹回（用户再点正文即恢复）。
                 */
                suppressIme = isFormatPanelOpen
            )

            /**
             * v2026-08-01 Phase 3：关联已内联为正文中的 atomic token（@ Trigger），
             * 移除原 LinkedCardsRow 独立展示区。
             * 关联的真相源仍是 card_relations 表（通过 viewModel.addRelation 即时入库）。
             */

            /**
             * 正文内容变更 → ViewModel 的链路已由 WebView 承担：
             * JS 侧 changed（防抖 800ms）上行 markdown → onMarkdownChanged → setContentFormat。
             * 宿主侧不再需要 SideEffect 挂 onDocChanged 回调。
             */

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

            /**
             * BlockNote 模式（P1.5）：链接插入对话框——
             * 底部工具栏 🔗 按钮触发，输入 URL 后经 format createLink 下发到 WebView 编辑器。
             */
            if (showLinkDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showLinkDialog = false },
                    title = { Text("插入链接") },
                    text = {
                        androidx.compose.material3.OutlinedTextField(
                            value = linkDialogUrl,
                            onValueChange = { linkDialogUrl = it },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (linkDialogUrl.isNotBlank()) {
                                    blockNoteController.format("createLink", linkDialogUrl.trim())
                                }
                                showLinkDialog = false
                            },
                            enabled = linkDialogUrl.isNotBlank()
                        ) {
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showLinkDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }

            /**
             * ⚠️ v2026-09-21：此处原有的「颜色」AlertDialog 弹窗接线已删除。
             *
             * 按需求，「A」按钮的展示形态改为与 T / H 一致的**内联面板**
             * （[com.corgimemo.app.ui.screens.inspiration.components.ColorStylePanel]），
             * 由 [InspirationEditBottomBar] 在"上行二c"槽位承载，无需在页面里再声明弹窗。
             *
             * 四组色板的回调与回显一并前移到上面 BottomBar 的调用参数里
             * （onInlineTextColorSelect / onInlineBackgroundColorSelect /
             *  onBlockTextColorSelect / onBlockBackgroundColorSelect，回显取 blockState），
             * 故此处只留本说明不再有 UI。
             */

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
                         *   见 InspirationEditViewModel.bodyBlocks 的 registerTriggers
                         *   （v2026-09-02 随 controller 一并移入 ViewModel）。
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
     * **v2026-09-10**：入口增至两处——① 原有内联图片点击；
     * ② 路线 4 图片块选中后，工具栏「图片附件页」按钮
     * （[BodyBlocksEditor] 的 onOpenImageGallery，先清选中再置本状态）。
     * 两者都只置路径，索引与图片列表在此统一按 markdown 重算。
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
