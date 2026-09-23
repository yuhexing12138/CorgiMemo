package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
/** 软键盘抑制（v2026-09-21）：WebView 子类拦截输入连接所需 */
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.corgimemo.app.ui.theme.FontCatalog
import com.corgimemo.app.ui.theme.ThemeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private const val TAG = "BlockNoteEditor"
private const val FONT_HOST = "corgimemo.local"
private const val FONT_PATH_PREFIX = "/fonts/"
private const val EDITOR_URL = "file:///android_asset/blocknote-web/editor/editor.html"

/**
 * BlockNote WebView 编辑器的桥接控制器（迁移 P1.5）
 *
 * 持有 WebView 引用与就绪状态，向宿主页面暴露命令方法：
 * [undo] / [redo] / [requestSave] / [setTheme] / [setFontFamily]。
 * ready 之前的命令会被缓存，ready 后按序补发（init 缓存同理）。
 *
 * v1.7：撤销/重做的可用态经 `undoState` 上行，暴露为 [canUndo] / [canRedo]（Compose 快照态）。
 * 界面不再由 JS 自绘撤销/重做按钮，统一由宿主顶栏那对图标按钮承担。
 *
 * 数据流纪律（docs/bridge-protocol.md）：单向数据流——
 * Kotlin 只下行「配置与命令」，内容以 markdown 快照经 `changed` 上行；
 * 历史栈本身始终留在 JS 侧，上行仅是可撤销/可重做的布尔态。
 */
/**
 * 当前光标块状态（v1.11）
 *
 * 原 BlockNote 侧边菜单（⋮⋮ 手柄）的点击菜单有 4 项，按用户决策全部移入宿主
 * 底部工具栏，手柄本身只保留拖拽重排。宿主因此必须知道「当前块能点什么」，
 * 否则会出现点了没反应的哑按钮——本数据类即承载该判定结果（由 JS 侧 `blockState` 上行）。
 *
 * 判定口径照抄 BlockNote 官方（见 JS 侧 `pushBlockState`）：
 * - 块颜色取决于块 spec 是否声明 textColor / backgroundColor；
 * - 表头取决于是否 table 块（且 `settings.tables.headers` 为真）。
 *
 * @param blockType 光标块类型（BlockNote 的 block.type，如 paragraph / heading / table / image）
 * @param isCheckboxBlock 当前块是否为「复选框块」（v2026-09-22 新增；工具栏复选框按钮
 *   的激活态）：JS 侧 `blockType == "checkListItem"`。原先读宿主本地镜像
 *   `BodyBlocksController.isFocusedBlockCheckbox`，BlockNote 模式下恒为 false
 *   → 按钮永远不高亮（与 B / I / U / S 同一个坑），故改为由 JS 上行。
 * @param headingLevel 光标块的标题级别（v2026-09-21 新增；供「标题面板」回显选中态）：
 *   `blockType == "heading"` 时为 1–6（普通标题**与**可折叠标题同为 heading 块），
 *   非标题块为 0（不高亮任何格子）。
 *   ⚠️ v2026-09-22 修正：原注释称"可折叠标题是独立块类型 toggleHeading*"，与 BlockNote
 *   真实模型不符——折叠态是 `props.isToggleable`，不是块类型，故级别一律取自 props.level。
 * @param headingToggleable 是否为「可折叠标题」（v2026-09-22 新增）：BlockNote 里折叠标题
 *   = `heading` 块 + `props.isToggleable = true`，级别仍看 [headingLevel]。标题面板据此在
 *   「普通标题 / 可折叠标题」两个分区之间分流——两类都有 1/2/3 级，只靠级别无法区分。
 *   普通标题与非标题块恒为 false。
 * @param canNestBlock 当前光标块是否可**向右缩进**（Nest；v2026-09-22 新增）
 *   JS 侧 `ed.canNestBlock()`：当前块**前面还有块**才能挂到前一块之下 → **首块为 false**。
 *   宿主底部工具栏 Nest 按钮据此视觉降权（规则 1：无法再向右缩进时降权）。
 * @param canUnnestBlock 当前光标块是否可**向左回退缩进**（Unnest；v2026-09-22 新增）
 *   JS 侧 `ed.canUnnestBlock()`：当前块**嵌套深度 > 1**（已被缩进过）才可退回 →
 *   **顶层块为 false**。宿主底部工具栏 Unnest 按钮据此视觉降权（规则 2）。
 *   ⚠️ 修复背景：此前该判定由宿主的 `canDecreaseIndent` 承担（读本地块对象
 *   `indentLevel`），而 BlockNote 模式下真实缩进只存在于 JS 侧文档树、从不上行
 *   → 宿主那份恒为 1 → Unnest **永远置灰不可点**。缩进可用态只能由 JS 判定。
 * @param canSetBlockColor 是否支持块级颜色（决定「块颜色」入口是否可点）
 * @param blockTextColor 当前块文本色（预设色名；空串 = 默认色，用于色板回显）
 * @param blockBackgroundColor 当前块背景色（预设色名；空串 = 无背景色）
 * @param canToggleHeader 是否可切换表头（table 块且 header 特性开启）
 * @param isHeaderRow 表格当前是否有标题行（非表格恒 false）
 * @param isHeaderCol 表格当前是否有标题列（非表格恒 false）
 */
data class BlockState(
    val blockType: String = "",
    /**
     * 当前光标块是否为「复选框块」（v2026-09-22 新增；工具栏复选框按钮的激活态）
     *
     * JS 侧判据即块类型：`blockType == "checkListItem"`（BlockNote 的复选框是独立
     * 块类型，与宿主下发 `format("checkList")` → `toggleBlockType("checkListItem")`
     * 同一口径，故本质上是 [blockType] 的派生值，单独上行只为宿主读起来更直白）。
     *
     * ⚠️ 修复背景（v2026-09-22）：此前该按钮的激活态读宿主侧
     * `BodyBlocksController.isFocusedBlockCheckbox`——判的是**宿主本地块对象**的
     * 段落类型。BlockNote 模式下正文只在 JS 侧 ProseMirror 文档树里，那份镜像恒为
     * false → 复选框按钮**永远不高亮**（与 B / I / U / S 同一个坑）。
     */
    val isCheckboxBlock: Boolean = false,
    /** 标题级别（v2026-09-21 新增）：heading → 1–6（两类标题同块类型）；非标题块 → 0 */
    val headingLevel: Int = 0,
    /**
     * 是否可折叠标题（v2026-09-22 新增）：true = 折叠标题（heading + isToggleable），
     * false = 普通标题 / 非标题块。标题面板据此在「普通 / 可折叠」两个分区之间分流。
     */
    val headingToggleable: Boolean = false,
    /**
     * 当前**选区**的行内文字色 / 行内背景色（v2026-09-22 新增；A 面板两个「选中色」行回显）
     *
     * 值为 JS 侧 `getActiveStyles()` 的原始字符串：宿主下发的自由 hex（`#FF9A5C`），
     * 也可能是粘贴内容带来的色名 / `rgb()`。宿主按当前主题色板反查色名后点亮对应色点；
     * 空串 = 该维度未设置 → 宿主高亮第一个「/」清除块。
     *
     * ⚠️ 与 [blockTextColor] / [blockBackgroundColor]（**块级**色，色名、作用于整段）不同：
     * 本两项是**行内**色，只作用于选区文字。
     */
    val inlineTextColor: String = "",
    val inlineBackgroundColor: String = "",
    /**
     * 当前选区的四个**行内布尔样式**激活态（v2026-09-22 新增；底部工具栏 B / I / U / S 高亮回显）
     *
     * 由 JS 侧 `getActiveStyles().bold/italic/underline/strike` 归一后上行（已是纯布尔）。
     *
     * ⚠️ 为什么必须走 JS 上行：BlockNote 接管正文后，内容只存在于 ProseMirror 文档树，
     * 工具栏手上那份 `RichTextState` 只是「聚焦块 / 首块」的本地镜像，其
     * `currentSpanStyle` 恒为空 —— 沿用 Compose 时代的判据会让这四个按钮**永远不高亮**。
     */
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikethrough: Boolean = false,
    /**
     * 光标块的对齐方式（v2026-09-22 新增；底部工具栏三个对齐按钮高亮回显）
     *
     * 对齐在 BlockNote 里是**块级 prop**（`props.textAlignment`），不是行内样式，
     * 合法值 "left" / "center" / "right" / "justify"。
     *
     * ⚠️ 默认值为 **"left"**：各 block spec 的 `textAlignment` 默认值即 "left"，
     * 未显式设置时渲染结果就是左对齐——按用户要求「文字默认为左对齐高亮」，
     * 故缺省（旧产物未下发该字段、或 prop 未设置）时左对齐按钮点亮。
     */
    val textAlignment: String = "left",
    /**
     * 当前选区字号档位（v2026-09-21 新增；H 面板「正文字号」高亮回显）：
     * JS 侧 `getActiveStyles().fontSize`（"18px" 形式）解析的整数——
     * WebView 内 1px=1dp，数值与宿主的 sp 档位直接对应；0 = 无字号样式（默认档 16）。
     */
    val fontSizeSp: Int = 0,
    /**
     * 是否可「向右缩进」（Nest，v2026-09-22 新增；工具栏 Nest 按钮置灰判据）
     *
     * 语义由 JS 侧 `ed.canNestBlock()` 给出：当前块前**还有**块时可挂到其下 →
     * 首块为 false。
     *
     * ⚠️ 初值 false（未收到 JS 上报 = 状态未知 → 保守置灰），随首次 `blockState`
     * 上行即被真值覆盖。
     */
    val canNestBlock: Boolean = false,
    /**
     * 是否可「向左回退缩进」（Unnest，v2026-09-22 新增；工具栏 Unnest 按钮置灰判据）
     *
     * 语义由 JS 侧 `ed.canUnnestBlock()` 给出：当前块嵌套深度 > 1 时才可退回 →
     * 顶层块为 false。
     *
     * ⚠️ 修复背景（v2026-09-22）：此前该按钮的置灰判据是宿主侧
     * `BodyBlocksController.canDecreaseIndent`（读本地块对象 `indentLevel > 1`
     * 或列表层级 > 1）。BlockNote 模式下正文的真实缩进**只存在于 JS 侧 ProseMirror
     * 文档树**、从不上行给宿主 → 宿主那份恒为 1 → Unnest **永远置灰不可点**。
     * 故缩进可用态必须由 JS 判定后经 `blockState` 上行，宿主不得自行推算。
     */
    val canUnnestBlock: Boolean = false,
    /**
     * 光标 / 选区上的**已有链接 URL**（v2026-09-22 新增；空串 = 当前不在链接上）
     *
     * 两个消费方：
     * 1. 底部工具栏 🔗 按钮的**激活态**（`linkUrl.isNotEmpty()`）——
     *    ⚠️ 修复背景：此前该按钮读的是 Compose 时代遗留的 `RichTextState.isLink`，
     *    而 BlockNote 模式下正文只存在于 ProseMirror 文档树、link mark 从不上行，
     *    宿主那份镜像恒为 false → 光标落在链接上时按钮**永远不高亮**。
     * 2. 链接对话框据此进入「**编辑链接**」模式：预填 URL、确定按钮改文案，
     *    并额外提供「移除链接」（[BlockNoteBridgeController.deleteLink]）。
     *
     * 取值口径与官方 `CreateLinkButton` 一致：`getLinkMarkAtPos(选区锚点)`。
     */
    val linkUrl: String = "",
    val canSetBlockColor: Boolean = false,
    val blockTextColor: String = "",
    val blockBackgroundColor: String = "",
    val canToggleHeader: Boolean = false,
    val isHeaderRow: Boolean = false,
    val isHeaderCol: Boolean = false,
)

class BlockNoteBridgeController {
    internal var webView: WebView? = null
    internal var ready = false
    internal var initSent = false
    internal var latestMarkdown: String = ""

    /** JS 上行的最新 markdown（changed 防抖后），宿主保存时取用 */
    var onMarkdownChanged: ((String) -> Unit)? = null

    /**
     * 正文基础字号变化回调（v2026-09-21）：JS 侧「无选区点正文字号」改全局基础
     * 字号后上行，宿主据此更新 BodyFontSizeManager 并持久化。参数为 px（1px=1dp）。
     */
    var onBaseFontSizeChanged: ((Int) -> Unit)? = null

    /**
     * 撤销/重做可用态（v1.7）：JS 侧历史栈变化后经 `undoState` 上行。
     * true 表示当前历史栈可撤销——宿主左上角按钮据此置灰（对齐 Compose 版 canUndo/canRedo）。
     */
    var canUndo by mutableStateOf(false)
        private set

    /** 重做可用态（v1.7，语义同 [canUndo]） */
    var canRedo by mutableStateOf(false)
        private set

    /**
     * 当前光标块状态（v1.11）：JS 侧光标块变化后经 `blockState` 上行。
     *
     * 驱动宿主底部工具栏的「删除块 / 块颜色 / 表头行 / 表头列」四个入口的
     * 可用态与选中回显。JS 侧已做去重，仅在状态真的变化时上行。
     */
    var blockState by mutableStateOf(BlockState())
        private set

    /**
     * 编辑器 DOM 是否持有焦点（v2026-09-22 新增；JS 侧 `editorFocus` 上行）
     *
     * 用途：宿主底部「T / H / A」面板收起后，据此判断「正文里是否还有光标」
     * 以决定是否把软键盘弹回来（面板展开期间键盘被抑制，但光标一直存在）。
     *
     * ⚠️ 为什么必须走 JS 上报而不是 `webView.hasFocus()`：点底部栏按钮时 Android
     * 的**视图焦点**已转移到 Compose 根视图，而 WebView 内的 `contenteditable`
     * 仍持有 **DOM 焦点**（用户看到光标还在闪）。前者会失真，后者才是真值。
     */
    var editorFocused by mutableStateOf(false)
        private set

    /**
     * 链接面板是否打开（v2026-09-24 新增）
     *
     * 由 [openLinkPanel] / [closeLinkPanel] 置位，并由 JS 上行 `linkPanelClosed` 复位——
     * 后者是必需的：用户点面板外部 / 按 Esc 时面板由官方 popover 的 dismiss 行为关闭，
     * 宿主完全无从得知，没有这条上行就会一直以为面板还开着。
     *
     * 用途与 [editorFocused] 同类：面板收起后据此决定是否把软键盘弹回来。
     */
    var linkPanelOpen by mutableStateOf(false)
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingInit: JSONObject? = null
    private var pendingCommands = mutableListOf<JSONObject>()

    /**
     * `saveSelection` 快照下来的选区区间（v2026-09-22；-1 = 尚无有效快照）
     *
     * 来源：JS 对 `saveSelection` 的应答 `selectionRange { from, to }` 上行。
     * [createLink] / [deleteLink] 会把它随命令带回 JS，作为**不依赖当前选区**的
     * 精确落点（第二道防线）——即便 `restoreSelection` 失败，链接也不会插错位置。
     *
     * ⚠️ 生命周期：[saveSelection] 调用即作废（归 -1），等下一次 `selectionRange`
     * 上行再填。JS 的应答是异步的，但对话框从打开到确认之间隔着用户输入，
     * 时间上必然足够；没等到（如旧产物不下发该消息）就保持 -1，
     * [createLink] 不带位置、JS 回落当前选区——向后兼容。
     */
    private var savedSelectionFrom = -1
    private var savedSelectionTo = -1

    /**
     * 装载编辑器内容（宿主在内容就绪后调用；ready 前调用会缓存待 ready 补发）。
     *
     * @param markdown 正文初始内容（markdown 快照）
     * @param fontFamilyId 初始内容字体 id（v2026-09-21 修复：原硬编码 "system_default"
     *   导致已保存字体的灵感在正文永远回显系统默认。调用方传
     *   `ContentFontManager.currentEntry.value.id`——编辑模式此时 loadInspiration 已
     *   setFonts 装载本条字体，新建模式 VM 构造已 resetToDefault，两路均为正确初值）
     * @param latinFontId 初始英文/数字字体 id（v2026-09-21：拉丁回退层；
     *   空串 = 未选拉丁、跟随中文字体。调用方传 `ContentFontManager.currentLatinId.value`）
     */
    fun load(markdown: String, fontFamilyId: String, latinFontId: String = "") {
        latestMarkdown = markdown
        val init = JSONObject()
            .put("type", "init")
            .put("markdown", markdown)
            .put("readOnly", false)
            .put("fontFamily", fontFamilyId)
            .put("latinFontId", latinFontId)
            .put("fonts", fontsPayload())
        pendingInit = init
        flushIfReady()
    }

    /** 撤销（长按连发由宿主侧按钮处理） */
    fun undo() = enqueueCommand(JSONObject().put("type", "requestUndo"))

    /** 重做 */
    fun redo() = enqueueCommand(JSONObject().put("type", "requestRedo"))

    /**
     * 只读切换（v1.11.1）：宿主锁定态（isLocked）下传 true，禁止正文编辑。
     *
     * ⚠️ 桥协议自 v1 起就定义了 `setReadOnly` 下行，JS 侧也早已实现
     * （`setReadOnly(msg.readOnly)` → 编辑器 `editable`），
     * 但 **Kotlin 侧一直没有对应的下发方法** —— 这条链路从未被使用，
     * 导致锁定态下正文实际上仍可编辑。此处补齐。
     *
     * ⚠️ 本开关只阻止**用户输入**；`removeBlocks` / `updateBlock` 等程序化 API
     * 不受 `editable` 限制，故宿主工具栏在锁定态必须另行禁用
     * （见 RichTextFormatToolbar 的 `enabled` 参数）。
     */
    fun setReadOnly(readOnly: Boolean) =
        enqueueCommand(JSONObject().put("type", "setReadOnly").put("readOnly", readOnly))

    /** 主动要一次 markdown 快照（返回键/切后台前） */
    fun requestSave() = enqueueCommand(JSONObject().put("type", "requestSave"))

    /**
     * requestSave 的即时快照等待信号（v2026-09-23 保存竞态修复）。
     *
     * 背景：正常编辑经 JS 防抖 800ms 上行 changed；用户停手不足 800ms 点「完成」，
     * 宿主 `_contentFormat` 还是旧快照，最后一批编辑（敲的空行/文字）不落库
     * （真机复现：连敲 3 次回车立刻点完成，重进只剩 1 个空行）。
     * JS 侧 `requestSave` 已改为**立即导出上行**，本信号由 [awaitSaveSnapshot] 挂起等待、
     * 收到 changed 时置位（见 handleUpMessage 的 changed 分支）。
     */
    private var saveSnapshotSignal: CompletableDeferred<Unit>? = null

    /**
     * 要一次即时快照并挂起等待上行（v2026-09-23 保存竞态修复）。
     *
     * 与 [requestSave] 的区别：创建等待信号后再发命令，调用方（「完成」按钮）拿到
     * `true` 即保证 `latestMarkdown` / `_contentFormat` 已是**当前文档**的最新导出。
     *
     * @param timeoutMs 超时兜底。WebView 往返 + JS 导出一般 <100ms，超时说明
     *   WebView 未就绪/异常——返回 false，调用方按现有内存值保存（与旧行为一致，不会更糟）。
     * @return true=已收到新快照；false=超时或信号缺失
     */
    suspend fun requestSaveAndAwait(timeoutMs: Long = 800L): Boolean {
        saveSnapshotSignal = CompletableDeferred()
        requestSave()
        val signal = saveSnapshotSignal ?: return false
        return try {
            withTimeout(timeoutMs) {
                signal.await()
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "requestSaveAndAwait 超时/失败，按现有内存值保存: ${e.message}")
            false
        } finally {
            saveSnapshotSignal = null
        }
    }

    /** 插入图片到光标处（S11：path 为本地绝对路径，JS 侧转 file:// URL） */
    fun insertImage(path: String) =
        enqueueCommand(JSONObject().put("type", "insertImage").put("path", path))

    /** 在光标处插入分割线（S10：solid 默认样式，可在分割线上点击工具条切换） */
    fun insertDivider() = enqueueCommand(JSONObject().put("type", "insertDivider"))

    /** 插入视频/音频/文件块（v1.5：本地路径，JS 转 file:// URL） */
    fun insertVideo(path: String) =
        enqueueCommand(JSONObject().put("type", "insertVideo").put("path", path))

    fun insertAudio(path: String) =
        enqueueCommand(JSONObject().put("type", "insertAudio").put("path", path))

    fun insertFile(path: String) =
        enqueueCommand(JSONObject().put("type", "insertFile").put("path", path))

    /** 打开表情选择面板（v1.5：JS 自绘网格） */
    fun openEmojiPicker() = enqueueCommand(JSONObject().put("type", "openEmojiPicker"))

    /**
     * 格式命令（v1.4）：底部格式工具栏按钮统一入口。
     * action：bold / italic / underline / strike / fontSize / textColor /
     *         bulletList / numberedList / checkList / indent / outdent
     * value：fontSize="18px"、textColor="#ff0000" 或 "default"（清除）；其余 action 无值
     */
    fun format(action: String, value: String? = null) {
        val msg = JSONObject().put("type", "format").put("action", action)
        if (value != null) msg.put("value", value)
        enqueueCommand(msg)
    }

    /**
     * 主动要一次块状态（v2026-09-22 新增）：让 JS 侧重新采集并上行 `blockState`。
     *
     * 用途：底部「T / H / A」面板**收起**时调用，保证工具栏的选中态 / 可用态在
     * 收起瞬间就是最新的。面板展开期间正文中可能发生了样式或选区变化，若恰好
     * 被 JS 侧的去重吸收、或时序上没赶在收起前上行，工具栏高亮就会**滞后一拍**
     * （显示面板操作之前的状态）。
     *
     * ⚠️ 本命令**不产生任何文档变更**，只是重新采集一次；JS 侧仍走 `pushBlockState`
     * 的 JSON 去重，状态没变就不会真的上行——可以放心多调。
     *
     * ready 之前调用会进 [pendingCommands] 缓存，ready 后统一补发，无需调用方判时机。
     */
    fun refreshBlockState() = enqueueCommand(JSONObject().put("type", "requestBlockState"))

    /**
     * 插入链接（v2026-09-22）：底部工具栏 🔗 按钮的专用下行，取代原先的
     * `format("createLink", url)`——因为需要额外携带「显示文字」这一维度。
     *
     * ⚠️ **宿主不判断是否有选区**：选区真值只在 WebView 的 ProseMirror state 里，
     * Kotlin 侧无从取得（Android 的 `View.hasFocus()` 会失真，见既有结论）。
     * 因此这里只把「URL + 可选的显示文字」如实下发，由 JS 侧按选区分流：
     * - **有选区 + 未填标题** → 只给选中文字挂 link mark（选中文字即标题）；
     * - **有选区 + 填了标题** → 用标题替换选中文字再挂链接；
     * - **无选区 + 光标落在已有链接上** → 改这条链接（避免插出「链接里套链接」）；
     * - **无选区 + 无链接** → 以标题（留空则用 URL 原文）为文字**插入**一段带链接的新文本。
     *
     * ⚠️ 与「编辑链接」配套：宿主在弹出链接对话框前先发 [saveSelection]，
     * 确认时先发 [restoreSelection]；桥命令按序执行，故本命令拿到的一定是还原后的选区。
     *
     * @param url 目标链接地址（非空；JS 侧会在缺协议时自动补 `https://`）
     * @param text 显示文字，传 null / 空串表示未填写：无选区且无链接时回落 URL 原文，
     *             光标在已有链接上时保留该链接的原显示文字
     */
    fun createLink(url: String, text: String? = null) {
        val msg = JSONObject()
            .put("type", "format")
            .put("action", "createLink")
            .put("value", url)
        val trimmed = text?.trim().orEmpty()
        /** 空标题不下发 `text` 字段：JS 侧靠「字段缺失」而非「空串」判定未填写，避免后续再判空 */
        if (trimmed.isNotEmpty()) msg.put("text", trimmed)
        /**
         * 随命令带回 `saveSelection` 快照下来的选区区间（v2026-09-22）：
         * JS 侧据此**按快照位置**写链接，不再依赖"编辑器当前选区是否还在"——
         * 这是第二道防线，即便 `restoreSelection` 失败也能精确落点。
         * 未拿到上行（旧产物 / saveSelection 尚未应答）时不下发，JS 回落当前选区。
         */
        if (savedSelectionFrom >= 0 && savedSelectionTo >= savedSelectionFrom) {
            msg.put("from", savedSelectionFrom)
            msg.put("to", savedSelectionTo)
        }
        enqueueCommand(msg)
    }

    /**
     * 打开「链接面板」（v2026-09-24 新增）—— 底部工具栏 🔗 按钮的新入口。
     *
     * **取代了什么**：此前该按钮弹的是宿主自绘的 Compose `AlertDialog`（`InspirationEditScreen`
     * 内约 80 行），与 WebView 内官方 FormattingToolbar 的「链接」按钮构成**两套并行实现**：
     * 外观不同、校验不同（自绘版判 `!= "https://"`，官方用 `VALID_LINK_PROTOCOLS` 补全协议）、
     * 提交路径不同。现统一收敛到官方那套表单（`EditLinkMenuItems`）。
     *
     * **为什么必须由宿主下发命令**：面板渲染在 WebView 内部的 React 树上，Compose 侧碰不到它。
     * 官方 `CreateLinkButton` 恰好把 popover 写成**受控**（`open={showPopover}`），
     * 本命令就是接到那个开关上（JS 侧见 `EditorApp.tsx` 的 `openLinkPanel` case）。
     *
     * **锚点与预填由 JS 现取，宿主无需传参**：选区真值只在 WebView 里，
     * 当前选区的 `from`/`to` 与光标所在链接的 URL 都由 JS 直读。
     *
     * 仍建议在本命令**之前**调一次 [saveSelection]：
     * 面板打开后 WebView 会失焦，若 Android 在失焦时折叠了内部选区，
     * 用户提交时官方 `editLink` 依赖的 `range` 仍是 JS 侧快照过的那个区间，位置不受影响。
     */
    fun openLinkPanel() {
        linkPanelOpen = true
        enqueueCommand(JSONObject().put("type", "openLinkPanel"))
    }

    /**
     * 关闭「链接面板」（v2026-09-24 新增）。
     *
     * 幂等：面板未打开时调用无副作用。两条关闭路径里宿主只需要管自己发起的那条——
     * 用户点面板外部 / 按 Esc 关闭时由 JS 主动上行 `linkPanelClosed` 通知宿主
     * （见 [linkPanelOpen] 的说明），此处无需轮询或对账。
     */
    fun closeLinkPanel() {
        linkPanelOpen = false
        enqueueCommand(JSONObject().put("type", "closeLinkPanel"))
    }

    /**
     * 保存当前选区快照（v2026-09-22）：**链接对话框打开前**下发。
     *
     * 为什么需要：链接对话框是 Compose 的 `AlertDialog`，弹出时 WebView 会失焦。
     * 若 Android WebView 在失焦时把内部选区折叠了，随后按「无选区」处理 ——
     * 用户明明选了字，结果却在光标处插了 URL。故先把此刻选区留在 JS 侧。
     *
     * 与 [restoreSelection] 成对使用；**无副作用**（只读快照）。
     *
     * ⚠️ 调用即**作废上一份快照**（[savedSelectionFrom] / [savedSelectionTo] 先归 -1）：
     * JS 的 `selectionRange` 上行是异步的，若不复位，快速连点两次 🔗 的间隙里
     * 第二次写链接可能拿到**第一次**的旧区间（链接插到上一处去）。
     */
    fun saveSelection() {
        savedSelectionFrom = -1
        savedSelectionTo = -1
        enqueueCommand(JSONObject().put("type", "saveSelection"))
    }

    /**
     * 还原上一次 [saveSelection] 的选区（v2026-09-22）：**写链接 / 移除链接之前**下发。
     *
     * 命令经 [enqueueCommand] 顺序下发，WebView 侧也按序执行，故只要在本方法之后
     * 紧接着调 [createLink] 或 [deleteLink]，还原一定先生效。
     *
     * ⚠️ 这只是**第一道防线**（恢复可见选区与书写起点）；落点精度由
     * [createLink] / [deleteLink] 携带的快照位置保证（第二道防线），二者互不依赖——
     * 本方法失败也不会让链接插错位置。
     */
    fun restoreSelection() = enqueueCommand(JSONObject().put("type", "restoreSelection"))

    /**
     * 移除光标 / 选区所在位置的链接，**保留文字**（v2026-09-22）。
     *
     * 链接对话框「编辑链接」模式的「移除链接」按钮。JS 侧调 `editor.deleteLink(position)`：
     * 优先按快照位置定位链接范围后去 mark，找不到时回落为「去掉当前选区上的 link mark」。
     * 与 [restoreSelection] 搭配使用（先还原选区，再移除）。
     */
    fun deleteLink() {
        val msg = JSONObject().put("type", "deleteLink")
        if (savedSelectionFrom >= 0) msg.put("from", savedSelectionFrom)
        enqueueCommand(msg)
    }

    /**
     * 删除当前块（v1.11）：原 ⋮⋮ 手柄点击菜单的「删除」项，移入宿主工具栏。
     *
     * 命中口径由 JS 侧决定（与官方 RemoveBlockItem 一致）：当前选区若包含光标块，
     * 则删除选区内的**全部块**；否则只删光标所在的那一块。
     * 因此宿主无需关心用户选了几个块，也不必传参。
     */
    fun deleteBlock() = enqueueCommand(JSONObject().put("type", "deleteBlock"))

    /**
     * 设置当前块的**块级**颜色（v1.11）：原 ⋮⋮ 手柄点击菜单的「颜色」项。
     *
     * ⚠️ 与 [format] 的 `textColor` 是**不同维度**，别混用：
     * - 本方法 → `updateBlock(block, { props })`，作用于**整个块**；
     * - [format]`("textColor", …)` → `addStyles`，只作用于**选区内的行内文字**。
     * 两者可同时存在、互不覆盖，宿主 UI 上要区分入口。
     *
     * @param textColor 块级文本色（BlockNote 预设色名；"default" 表示清除）；传 null = 不改动该维度
     * @param backgroundColor 块级背景色（预设色名；"default" 表示清除）；传 null = 不改动该维度
     */
    fun setBlockColor(textColor: String? = null, backgroundColor: String? = null) {
        val msg = JSONObject().put("type", "setBlockColor")
        if (textColor != null) msg.put("textColor", textColor)
        if (backgroundColor != null) msg.put("backgroundColor", backgroundColor)
        enqueueCommand(msg)
    }

    /**
     * 切换表头行 / 表头列（v1.11）：原 ⋮⋮ 手柄点击菜单的「表头行 / 表头列」项。
     *
     * 官方目前只支持 1 行 / 1 列，故用布尔开关而非数量。仅当光标在表格块内时生效
     * （宿主可按 [BlockState.canToggleHeader] 置灰；非表格块时 JS 侧静默忽略）。
     *
     * @param target "row" = 表头行，"column" = 表头列
     * @param enabled true = 开启表头，false = 关闭
     */
    fun setTableHeader(target: String, enabled: Boolean) =
        enqueueCommand(
            JSONObject()
                .put("type", "setTableHeader")
                .put("target", target)
                .put("enabled", enabled)
        )

    /**
     * 块上移 / 下移（v1.11.5）：**替代原 ⋮⋮ 手柄的拖拽重排**。
     *
     * 背景：该手柄的拖拽纯用 HTML5 原生 Drag & Drop，而该 API 在 Android WebView /
     * iOS Safari 的触摸下**不触发**（W3C 把 drag 事件定义为鼠标驱动行为），
     * 实测真机"按住无反应"。故手柄已整体删除（`sideMenu={false}`），
     * 块移动改由本方法走程序化 API `editor.moveBlocksUp()` / `moveBlocksDown()`。
     *
     * 无需传参：JS 侧不传 `blockIdentifier`，BlockNote 会取**选区首/末块**或**光标块**，
     * 因而天然支持「多选块一起移动」与嵌套块。**到达首/末块时内部安全 no-op**
     * （不会抛错，但也无法预知能否移动，故工具栏不对这两项做置灰）。
     */
    fun moveBlockUp() = enqueueCommand(JSONObject().put("type", "moveBlockUp"))

    fun moveBlockDown() = enqueueCommand(JSONObject().put("type", "moveBlockDown"))

    /**
     * 下发编辑区最小高度（v1.11.6）
     *
     * 目的：消除"点击正文下方空白无法聚焦"的死区。
     * `.bn-editor` 是 `contenteditable` 但 BlockNote 未给它 `min-height`，
     * 高度完全由内容决定；而宿主给 WebView 设了 `heightIn(min = 屏高 × 62%)`
     * [com.corgimemo.app.ui.screens.inspiration.InspirationEditScreen]。
     * 两者不一致时，WebView 内下方区域虽可见却**不属于 contenteditable**，
     * 点击不会聚焦光标。下发同一个高度值后，JS 侧写入 `.bn-editor { min-height }`
     * 即可让编辑区铺满 WebView。
     *
     * @param heightDp 最小高度的 **dp** 值。WebView 用 `initial-scale=1.0`，
     *                 故 1 CSS px = 1 dp，JS 侧可直接当作 px 使用。
     */
    fun setEditorMinHeight(heightDp: Float) =
        enqueueCommand(
            JSONObject().put("type", "setEditorMinHeight").put("height", heightDp.toDouble())
        )

    /** 主题下行（深浅 + 主色 + 编辑区背景色，v1.9 增 background） */
    fun setTheme(dark: Boolean, primary: String, background: String) {
        val theme = JSONObject()
            .put("dark", dark)
            .put("primary", primary)
            .put("background", background)
        enqueueCommand(JSONObject().put("type", "setTheme").put("theme", theme))
    }

    /** 内容字体切换（FontCatalog.id） */
    fun setFontFamily(fontId: String) {
        enqueueCommand(
            JSONObject().put("type", "setFontFamily").put("fontFamily", fontId)
        )
    }

    /**
     * 英文/数字字体切换（v2026-09-21：拉丁回退层下行通道）。
     *
     * 此前 JS 只有单 fontFamily 通道、@font-face 清单也不含拉丁字体——
     * 「英文/数字字体」在编辑页正文永远无法生效。空串 = 未选拉丁（跟随中文字体）。
     */
    fun setLatinFontFamily(latinId: String) {
        enqueueCommand(
            JSONObject().put("type", "setLatinFontFamily").put("latinFontId", latinId)
        )
    }

    /**
     * 正文基础字号切换（v2026-09-21：无选区点「正文字号」= 改全局正文，单位 px）。
     * JS 侧写入 CSS 变量 `--bn-editor-base-font-size`，作用于未叠加行内样式的全部文字。
     */
    fun setBaseFontSize(px: Int) {
        enqueueCommand(
            JSONObject().put("type", "setBaseFontSize").put("fontSizePx", px)
        )
    }

    /**
     * 让编辑器重新获得 DOM 焦点（v2026-09-22）
     *
     * 宿主收起「T / H / A」面板后要弹回键盘时**必须先发这一条**：Chromium 只有在编辑
     * 元素持有焦点时才肯建立输入连接，[restoreIme] 里的 `showSoftInput` 才有效。
     * 已聚焦时 JS 侧 `focus()` 为空操作，不会打断现有选区（抑制期间选区是保留的）。
     */
    fun focusEditor() = enqueueCommand(JSONObject().put("type", "focusEditor"))

    /**
     * 面板收起后把软键盘弹回来（v2026-09-22）
     *
     * 两步缺一不可：
     * 1. [focusEditor]：DOM 焦点交还 `.bn-editor`（Chromium 才建立输入连接）；
     * 2. [ImeSuppressibleWebView.showImeNow]：视图焦点 + `InputMethodManager.showSoftInput`。
     *
     * ⚠️ 调用前提（顺序不能乱）：
     * - 必须在 **IME 抑制解除之后**（`suppressIme` 已回 false、页面的 `inputmode="none"`
     *   标记已移除）——否则第 1 步聚焦后 Chromium 仍认为自己不该弹键盘；
     * - 必须在 **UI 线程**（WebView 方法 + IMM 调用都有线程要求）；
     * - 只应在 [editorFocused] 为真时调用（无光标就不要抢键盘）。
     */
    fun restoreIme() {
        val wv = webView as? ImeSuppressibleWebView
        if (wv == null) {
            Log.w(TAG, "restoreIme: webView not suppressible")
            return
        }
        focusEditor()
        wv.showImeNow()
    }

    private fun enqueueCommand(msg: JSONObject) {
        val type = msg.optString("type")
        if (ready && !initSent) {
            // ready 前的命令无编辑器可作用，直接丢弃（init 会在 ready 后重放内容）。
            // v2026-09-23 补 WARN：这是唯一的无日志静默丢弃点，出问题（如撤销"点了没反应"）
            // 时 logcat 零痕迹无从排查——异常路径至少留一行证据。
            Log.w(TAG, "enqueueCommand DROPPED (ready && !initSent): $type")
            return
        }
        if (ready) {
            sendDown(webView, msg)
        } else {
            pendingCommands.add(msg)
        }
    }

    private fun flushIfReady() {
        if (!ready) return
        pendingInit?.let { init ->
            sendDown(webView, init)
            initSent = true
        }
        pendingInit = null
        pendingCommands.forEach { sendDown(webView, it) }
        pendingCommands.clear()
    }

    private fun fontsPayload(): JSONArray {
        val arr = JSONArray()
        FontCatalog.bridgeFontResMap().forEach { (id, weights) ->
            if (weights.isNotEmpty()) {
                val weightArr = JSONArray()
                weights.keys.sorted().forEach { weightArr.put(it) }
                arr.put(JSONObject().put("id", id).put("weights", weightArr))
            }
        }
        return arr
    }

    /**
     * 上行消息入口（JS → Kotlin），消息格式见 docs/bridge-protocol.md
     *
     * ⚠️ **线程模型（v2026-09-21 起统一）**：本方法由 `@JavascriptInterface` 调用，
     * 运行在 WebView 的 **Java 桥线程**；而 WebView 的任何方法
     * （`evaluateJavascript` / `loadUrl` / `setBackgroundColor` …）都只能在 **UI 线程**调用，
     * 否则抛 IllegalStateException。若该异常发生在某个分支内，会被下方的 `catch`
     * 静默吞掉，**连带该分支后续语句也不再执行**。真案例：`ready` 分支里同步调用
     * `evaluateJavascript` → `flushIfReady()` 不再执行 → `init` 永不下发 →
     * JS 侧 `booted` 恒为 false → 正文区一直停在「正在装载…」。
     *
     * 因此这里把**整条消息一次性 post 到主线程再解析**，将「线程正确性」收敛为入口处
     * 唯一一处约定：以后往 [handleUpMessageOnMainThread] 里加逻辑（尤其是调用 WebView
     * 方法）都不必再逐个分支 post，也不会踩同一个坑。
     */
    internal fun handleUpMessage(json: String) {
        /** 日志留在桥线程打：时间戳更贴近 JS 真实上行时刻，便于与前端 console 对齐 */
        Log.d(TAG, "up(${json.take(120)})")
        mainHandler.post { handleUpMessageOnMainThread(json) }
    }

    /**
     * 上行消息的实际处理（**保证运行在 UI 线程**，由 [handleUpMessage] post 进入）。
     *
     * 拆成独立方法而非直接写在 lambda 里：既让上面那条线程约定显式可见，
     * 也让异常栈能直接指出是"上行消息处理"出错。
     */
    private fun handleUpMessageOnMainThread(json: String) {
        try {
            val msg = JSONObject(json)
            when (msg.optString("type")) {
                "ready" -> {
                    // v1.8：JS 侧带上构建指纹（<构建时间> <commit 短 hash><-dirty?>）。
                    // assets 里的 editor.html 是静态资源，Gradle 不会重新生成——
                    // 排查「JS 改了但真机没生效」时，看这一行即可确认加载的产物版本。
                    val build = msg.optString("build", "unknown")
                    /**
                     * v2026-09-22：连同**源码内容哈希**一起打——指纹只说"哪一版"，
                     * 哈希才能确认"这版是不是当前 `src/editor/` 编出来的"。
                     * 与 `scripts/check-blocknote-artifact.ps1` 算出的值比对，
                     * 即可判断产物是否需要重建（旧产物不下发该字段时为空串）。
                     */
                    val srcHash = msg.optString("srcHash", "")
                    Log.d(TAG, "ready received | build=$build | src=$srcHash")
                    ready = true
                    /**
                     * v2026-09-21：编辑器挂载完成后补一次「软键盘抑制」注入。
                     *
                     * 抑制可能在编辑器 DOM 就绪**之前**就已开启（宿主进页即展开面板），
                     * 那时注入的脚本查不到 `.bn-editor`；此处 ready 保证 DOM 已存在，
                     * 是补打标记最可靠的时机（另有 onPageFinished 与状态变化两个时机）。
                     */
                    (webView as? ImeSuppressibleWebView)?.applyImeSuppression()
                    flushIfReady()
                }
                "changed" -> {
                    val md = msg.optString("markdown")
                    latestMarkdown = md
                    onMarkdownChanged?.invoke(md)
                    // v2026-09-23 保存竞态修复：requestSaveAndAwait 挂起等待期间，
                    // 第一条 changed（JS 收到 requestSave 后立即导出的最新快照）即放行
                    saveSnapshotSignal?.let { sig ->
                        if (sig.isActive) sig.complete(Unit)
                    }
                }
                "undoState" -> {
                    // v1.7：撤销/重做可用态上行 → 驱动宿主按钮 enabled（Compose 快照态，主线程安全）
                    canUndo = msg.optBoolean("canUndo", false)
                    canRedo = msg.optBoolean("canRedo", false)
                }
                "blockState" -> {
                    // v1.11：当前光标块状态上行 → 驱动宿主工具栏的
                    // 删除块 / 块颜色 / 表头行 / 表头列 四个入口的可用态与回显。
                    // v2026-09-22 追加：Nest / Unnest 两个缩进按钮的可用态
                    // （canNestBlock / canUnnestBlock，见 BlockState 字段注释）。
                    // 一次性构造后整体赋值，避免多次赋值造成中间态（如颜色已改而可用态未改）。
                    // 注意 optString 对缺失字段返回 ""，正好与 BlockState 的默认值语义一致。
                    blockState = BlockState(
                        blockType = msg.optString("blockType"),
                        /**
                         * v2026-09-22：是否复选框块（缺失时 false —— 与"旧产物不下发
                         * 该字段"向后兼容，行为等于复选框按钮不高亮）。
                         */
                        isCheckboxBlock = msg.optBoolean("isCheckboxBlock", false),
                        /** v2026-09-21：标题级别（缺失/非法时 0 → 面板不高亮任何格子） */
                        headingLevel = msg.optInt("headingLevel", 0),
                        /**
                         * v2026-09-22：是否可折叠标题（缺失时 false → 按普通标题处理，
                         * 与"老产物不下发该字段"的情况向后兼容）
                         */
                        headingToggleable = msg.optBoolean("headingToggleable", false),
                        /**
                         * v2026-09-22：选区的行内色（缺失时空串 = 未设置 → 面板高亮「默认」块）
                         */
                        inlineTextColor = msg.optString("inlineTextColor"),
                        inlineBackgroundColor = msg.optString("inlineBackgroundColor"),
                        /**
                         * v2026-09-22：四个行内布尔样式（缺失时 false = 未激活——
                         * 与「旧产物不下发这些字段」向后兼容，最坏情况只是不高亮）
                         */
                        isBold = msg.optBoolean("bold", false),
                        isItalic = msg.optBoolean("italic", false),
                        isUnderline = msg.optBoolean("underline", false),
                        isStrikethrough = msg.optBoolean("strike", false),
                        /**
                         * v2026-09-22：对齐方式。
                         *
                         * ⚠️ 空串必须回落 "left"：老产物不下发该字段，`optString` 返回 ""，
                         * 而 "" 不等于任何合法值 → 三个按钮会**全部不高亮**，
                         * 与「默认左对齐」的要求相悖。归一收在边界处，下游直接比较即可。
                         */
                        textAlignment = msg.optString("textAlignment").takeIf { it.isNotBlank() } ?: "left",
                        /** v2026-09-21：选区字号（0 = 无样式 → 宿主回落默认档） */
                        fontSizeSp = msg.optInt("fontSizePx", 0),
                        /**
                         * v2026-09-22：Nest / Unnest 可用态（由 JS 侧
                         * `ed.canNestBlock()` / `ed.canUnnestBlock()` 判定后上行）。
                         * 缺失字段（旧产物）时按 false 处理 → 两个按钮置灰，
                         * 属"状态未知"的保守行为；重建产物后即为真值。
                         */
                        canNestBlock = msg.optBoolean("canNestBlock", false),
                        canUnnestBlock = msg.optBoolean("canUnnestBlock", false),
                        /**
                         * v2026-09-22：光标/选区上的已有链接（缺失时空串 = 不在链接上）。
                         * 空串口径与 [BlockState.blockTextColor] 一致，下游用 isNotEmpty() 判定即可。
                         */
                        linkUrl = msg.optString("linkUrl"),
                        canSetBlockColor = msg.optBoolean("canSetBlockColor", false),
                        blockTextColor = msg.optString("blockTextColor"),
                        blockBackgroundColor = msg.optString("blockBackgroundColor"),
                        canToggleHeader = msg.optBoolean("canToggleHeader", false),
                        isHeaderRow = msg.optBoolean("isHeaderRow", false),
                        isHeaderCol = msg.optBoolean("isHeaderCol", false),
                    )
                }
                "diagnostic" ->
                    /**
                     * v1.11.7：JS 主动回传的运行时观测值（CSS 变量、computed style、
                     * 元素实际高度等）。**只打 log，不做任何 UI 反应**——
                     * WebView 内部的这些真值在 Kotlin 侧无法直接观测，以往只能靠猜。
                     * 排查命令用：`adb logcat -s BlockNoteEditor:V | grep -E "diag|down\("`
                     */
                    Log.d(TAG, "diag | ${msg.optString("message")}")
                "baseFontSize" ->
                    /**
                     * v2026-09-21：JS「无选区点正文字号」改全局基础字号后上行，
                     * 宿主据此更新 BodyFontSizeManager（内存 → Screen 响应式下发
                     * 幂等确认）并写 SharedPreferences 持久化。
                     */
                    onBaseFontSizeChanged?.invoke(msg.optInt("fontSizePx", 0))
                /**
                 * v2026-09-22：编辑器 DOM 焦点态上行（面板收起后是否弹回键盘的判据）。
                 * 缺失字段时按 false 处理（旧产物不下发 → 不弹键盘，行为与改动前一致）。
                 */
                "editorFocus" -> {
                    editorFocused = msg.optBoolean("focused", false)
                    Log.d(TAG, "diag | editorFocus = $editorFocused")
                }
                /**
                 * v2026-09-22：`saveSelection` 的应答——选区区间上行。
                 * 暂存后由 [createLink] / [deleteLink] 随命令带回 JS 作精确落点。
                 */
                "selectionRange" -> {
                    savedSelectionFrom = msg.optInt("from", -1)
                    savedSelectionTo = msg.optInt("to", -1)
                    Log.d(TAG, "diag | selectionRange = $savedSelectionFrom-$savedSelectionTo")
                }
                "error" -> Log.e(TAG, "js error: ${msg.optString("message")}")
                /**
                 * 外部浏览器打开链接（v2026-09-24 新增）
                 *
                 * JS 侧两个入口汇聚到这条上行（详见 [openInBrowser] 与 JS 侧
                 * `handleLinkClick` / `ProjectLinkToolbar`）：
                 * ① 自定义 LinkToolbar 的「打开」按钮 —— 官方原版调
                 *    `window.open(url, "_blank")`，在 Android WebView 下被静默丢弃；
                 * ② 只读态点击链接 —— 无 LinkToolbar 可用，点击即"要打开"。
                 *
                 * **编辑态单击链接不会走到这里**：那是产品决策下的"只落光标"，
                 * 由 JS 侧吃掉事件（见 `handleLinkClick` 的注释）。
                 *
                 * ⚠️ 本分支是**用户显式表达打开意图**的唯一路径，因此这里不再做
                 * "是否编辑态"之类的判断——JS 侧已在源头分流完毕。
                 */
                "openLink" -> {
                    val url = msg.optString("url")
                    /** WebView 引用可能已释放（onRelease 置空），此时无从取 Context，静默跳过 */
                    val ctx = webView?.context
                    if (ctx == null) {
                        Log.w(TAG, "openLink ignored (no webView): $url")
                    } else {
                        openInBrowser(ctx, url)
                    }
                }
                /**
                 * 链接面板已关闭（v2026-09-24 新增）：对 [openLinkPanel] 的反向通报。
                 *
                 * 面板渲染在 WebView 内部的 React 树上，用户点面板外部 / 按 Esc 关闭时
                 * 宿主完全无从得知——没有这条上行，[linkPanelOpen] 会一直停在 true。
                 * 故 JS 在 `onOpenChange(false)` 时无条件上行一次，此处直接对齐。
                 */
                "linkPanelClosed" -> {
                    linkPanelOpen = false
                    Log.d(TAG, "diag | linkPanelClosed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "bad up message", e)
        }
    }
}

/**
 * 可嵌入的 BlockNote WebView 编辑器（迁移 P1.5）
 *
 * 宿主页面保持自身 UI 不变，仅以本组件替换正文区：
 * - 内容装载：宿主在数据就绪后调 [controller].load(markdown, fontFamilyId)（见 [BlockNoteBridgeController]）
 * - 内容变更：JS 防抖 800ms 后经 [BlockNoteBridgeController.onMarkdownChanged] 上行
 * - 撤销/重做/保存：controller 命令
 * - 主题：跟随 App 主题（ThemeManager 深浅 + 六色主色，变化自动下行）
 * - 字体：fonts 清单下行 + shouldInterceptRequest 字体流（res/font 单份存储）；
 *   init 携带初始字体回显，后续切换经 [BlockNoteBridgeController.setFontFamily]
 *
 * @param backgroundColor 宿主编辑区实际背景色（v1.9）：由宿主下行到 JS，
 *   让 WebView 内部 `.bn-editor` 与 body 与宿主主题背景一致，消除"画中画"白底框；
 *   同时作为 WebView 自身底色兜底，避免首帧未绘制时透出色差。
 * @param suppressIme 是否抑制软键盘（v2026-09-21 新增）。
 *   宿主底部工具栏的「T 字体面板」「Aa 字号颜色面板」展开期间传 true——
 *   两个面板高度 = 键盘高度、占据键盘位，若用户在正文里聚焦光标 / 多选时
 *   键盘再弹出来，会把面板顶走并让 WebView 视口被压缩（v1.11.9 曾因此触发
 *   失控循环）。抑制期间**焦点与选区功能完全保留**，只是不唤起 IME；
 *   传回 false 时解除抑制。**v2026-09-22 起**：解除后是否把键盘弹回来由宿主按
 *   [BlockNoteBridgeController.editorFocused] 决定——正文仍有光标则调
 *   [BlockNoteBridgeController.restoreIme] 主动弹回（面板收起即恢复输入），
 *   无光标则保持收起（不再要求用户"再点一次正文"）。
 */
@Composable
fun BlockNoteEditorWebView(
    controller: BlockNoteBridgeController,
    onMarkdownChanged: (String) -> Unit,
    /** 正文基础字号变化上行（v2026-09-21）：JS 无选区点「正文字号」后宿主更新+持久化 */
    onBaseFontSizeChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    suppressIme: Boolean = false,
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    // 主题真值（S6）：深浅模式 + 六色主色，变化即下行
    val themeMode by ThemeManager.themeMode.collectAsState()
    val themeColor by ThemeManager.themeColor.collectAsState()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val primaryHex = primaryColorHex(themeColor)
    /** 编辑区背景色（v1.9）：未指定时按深浅回落 BlockNote 默认（白 / #1f1f1f） */
    val effectiveBackground = if (backgroundColor.isSpecified) {
        backgroundColor
    } else {
        if (isDark) Color(0xFF1F1F1F) else Color.White
    }
    val backgroundHex = composeColorToHex(effectiveBackground)

    LaunchedEffect(isDark, primaryHex, backgroundHex) {
        controller.setTheme(isDark, primaryHex, backgroundHex)
    }

    // onMarkdownChanged 回调更新（保持最新 lambda 引用）
    LaunchedEffect(onMarkdownChanged) {
        controller.onMarkdownChanged = onMarkdownChanged
    }

    /** onBaseFontSizeChanged 回调更新（v2026-09-21，同上模式） */
    LaunchedEffect(onBaseFontSizeChanged) {
        controller.onBaseFontSizeChanged = onBaseFontSizeChanged
    }

    /**
     * 外层 Box 不追加 `fillMaxSize()`（v1.9）：
     * 宿主在 `verticalScroll` 容器内会通过 `modifier` 传 `heightIn(min=...)`，
     * 若此处再 `fillMaxSize()`，在无限高约束下会覆盖宿主的高度意图，导致
     * WebView 塌成内容高。改为仅 `fillMaxWidth()` 让宿主的高度约束生效。
     *
     * ⚠️ **不要在此处追加 `imePadding()`**（v1.11.9 修复，曾于 P1.5 误加）：
     * 键盘 insets 的消费**只能发生一次**——Scaffold bottomBar 已通过
     * `Modifier.safeAreaForEditBar()`（= `imePadding()`）抬起工具栏、
     * 让 content 区域收缩，WebView 经 `weight` 自然获得键盘上方的剩余空间。
     * 此处若再垫一次 ime，WebView 会被两层 insets 压到只剩十几 dp
     * （旧布局下被 `heightIn(min)` + `verticalScroll` 的"溢出可滚"掩盖，
     * 方案 A 移除掩护后暴露为「键盘弹出视口坍缩到一行高」）。
     */
    Box(modifier = modifier.fillMaxWidth()) {
        AndroidView(
            factory = { appContext ->
                createEditorWebView(appContext, controller, effectiveBackground)
            },
            onRelease = { controller.webView = null },
            /** 主题背景变化时同步刷新 WebView 底色（v1.9） */
            update = { wv ->
                wv.setBackgroundColor(effectiveBackground.toArgb())
                /**
                 * 软键盘抑制态同步（v2026-09-21）：`update` 每次重组都会调用，
                 * 而 [ImeSuppressibleWebView.isImeSuppressed] 的 setter 自带
                 * 「值未变则直接返回」守卫，因此不会产生重复的 JS 注入与 IME 隐藏。
                 */
                (wv as? ImeSuppressibleWebView)?.isImeSuppressed = suppressIme
            },
            /**
             * ⚠️ 必须是 `fillMaxWidth()` 而非 `fillMaxSize()`（v1.9.1 修复）：
             * 外层 Box 已改为「不 fill、让宿主高度约束生效」，但如果内层 AndroidView 仍
             * `fillMaxSize()`，在 `verticalScroll` 提供的**无限高约束**下它无法解出有限高度，
             * 于是退化为"按子内容包装"，再把 Box 顶到内容高——宿主的 `heightIn(min=...)`
             * 会被这一层静默吞掉，表现为"编辑区仍然没占满"（首版修复后真机复现）。
             * 高度只需横向占满，纵向交由父级（=宿主约束）决定。
             */
            modifier = Modifier.fillMaxWidth()
        )
        // appContext 仅用于 WebView 工厂上下文语义校验（编译引用保留）
        @Suppress("UNUSED_EXPRESSION")
        appContext
    }
}

/** Compose Color → "#RRGGBB"（忽略 alpha；与 InspirationEditScreen 的同名工具语义一致） */
private fun composeColorToHex(c: Color): String = String.format(
    java.util.Locale.US,
    "#%02X%02X%02X",
    (c.red * 255).roundToInt(),
    (c.green * 255).roundToInt(),
    (c.blue * 255).roundToInt()
)

private fun primaryColorHex(key: String): String = when (key) {
    "pink" -> "#FFB5C2"
    "green" -> "#7EC8A0"
    "blue" -> "#7EB8DA"
    "purple" -> "#B8A0D4"
    "brown" -> "#C4A882"
    else -> "#FF9A5C" // orange + 兜底
}

/** 下行：Kotlin → JS（evaluateJavascript 调 Bridge 宿主） */
private fun sendDown(webView: WebView?, msg: JSONObject) {
    /**
     * v2026-09-23 诊断（撤销失效排查）：原 `?: return` 是**无日志静默丢弃**——
     * webView 引用丢失（onRelease 置 null / 时序竞态）时所有下行命令凭空消失，
     * 表现为"点了没反应"且日志零痕迹。至少打一行 WARN 留证据。
     */
    val wv = webView ?: run {
        Log.w(TAG, "sendDown SKIPPED (webView==null): ${msg.optString("type")}")
        return
    }
    /**
     * v2026-09-21 诊断增强：down 日志补参数值。原先只打 type，排查
     * 「setFontFamily 下发了什么值 / init 携带几个字体」时是盲区。
     * init 含 markdown 全文，特判只打关键字段（字体 id + 字体清单条数）。
     */
    val summary = if (msg.optString("type") == "init") {
        "init(markdown=${msg.optString("markdown").length}ch" +
            ", fontFamily=${msg.optString("fontFamily")}" +
            ", fonts=${msg.optJSONArray("fonts")?.length() ?: -1})"
    } else {
        msg.toString().take(200)
    }
    Log.d(TAG, "down($summary)")
    wv.evaluateJavascript("window.BlockNoteEditorHost.onMessage(${msg})", null)
}

/**
 * 是否属于编辑器**自身**的运行期 URL（v2026-09-24 新增）
 *
 * 导航拦截的白名单判据。只有下列三类放行，其余一律拦下交外部浏览器：
 *
 * 1. **编辑器页面本体**：`file:///android_asset/blocknote-web/` 下的资源
 *    （editor.html 与其同目录的静态资源）——这是页面运行的基础。
 * 2. **字体流虚拟域**：`https://corgimemo.local/fonts/...`——
 *    不存在真实服务器，全部由 [WebViewClient.shouldInterceptRequest] 在应用内
 *    用 `openRawResource` 回填（见 [FONT_HOST]）。
 * 3. **本地媒体**：`file://` 与 `content://`——图片/音视频附件经此加载，
 *    是编辑器正文的一部分，不是"外链"。
 *
 * ⚠️ 判据刻意用 `startsWith` 而非 `host` 比较：`file://` URL 的 host 为空串，
 * 用 host 判断会让编辑器自身也被拦下（那样页面永远加载不出来）。
 *
 * ⚠️ 不要改成"放行一切 file://"——assets 之外的用户私有目录（如
 * `/sdcard/Download`）若被导航进来，同样是"编辑器被顶掉"。
 *
 * ⚠️ **可见性是 `internal` 而非 `private`**：Kotlin 的 `private` 是**文件级**的，
 * [BlockNoteEditorScreen]（探针页）也要复用同一判据，两处必须行为一致——
 * 探针页存在的意义就是复现正式页行为，判据分叉即失去参照价值。
 *
 * @param url 待判定的 URL 字符串
 * @return true = 编辑器内部 URL，导航放行
 */
internal fun isEditorInternalUrl(url: String): Boolean {
    /** ① 编辑器页面本体（editor.html 及其同目录资源） */
    if (url.startsWith("file:///android_asset/blocknote-web/")) return true
    /** ② 字体流虚拟域（shouldInterceptRequest 在应用内回填） */
    if (url.startsWith("https://$FONT_HOST/")) return true
    /** ③ 其它本地资源（附件图/音视频）：编辑器正文的一部分 */
    if (url.startsWith("file://") || url.startsWith("content://")) return true
    return false
}

/**
 * 在**系统浏览器**中打开一个链接（v2026-09-24 新增）
 *
 * 这是本页所有"链接要打开"路径的唯一出口——JS 侧 `openLink` 上行与只读态点击
 * 都收敛到这里。编辑器 WebView 自身**永不导航**（详见 `createEditorWebView`
 * 里的三道导航拦截）。
 *
 * ## 为什么必须走 ACTION_VIEW 而不是 WebView 内导航
 *
 * 编辑器页面是 `file:///android_asset/blocknote-web/editor/editor.html`，
 * 一旦主框架导航到目标站，**编辑器整体被目标页顶掉**——页面栈里没有后退项、
 * 用户编辑到一半的内容（JS 侧尚未防抖上行的部分）直接丢。三条可用的导航通道
 * 在 Android WebView 上各有坑（见 `createEditorWebView` 的注释），因此干脆
 * 全部拦下、一律外送。
 *
 * ## 关键实现点
 *
 * - **`Uri.parse` 而非 `Uri.fromParts`**：URL 自带 scheme（`https` / `mailto`
 *   / `tel` 等），原样交给系统分发即可；解析出的 scheme 为空串说明 URL 残缺，
 *   此时**直接丢弃**——否则 `ACTION_VIEW` 会因 "No Activity found" 抛异常。
 * - **`FLAG_ACTIVITY_NEW_TASK` 必须有**：WebView 的回调（`shouldOverrideUrlLoading`
 *   / `onCreateWindow`）不保证运行在 Activity 上下文里，且本方法不持有 Activity
 *   引用（只传 `Context`）；不加此 flag 在非 Activity Context 下会抛
 *   `AndroidRuntimeException`。加了对 Activity Context 也是无害的。
 * - **`try/catch` 兜底**：设备可能没有浏览器/邮件客户端能处理该 URL，此时
 *   `startActivity` 抛 `ActivityNotFoundException`。这是**正常情况而非缺陷**，
 *   只打日志、静默返回（宿主不应因为用户点了 `tel:` 而崩）。
 * - **`Intent` 不入 Manifest 的 `<queries>`**：`ACTION_VIEW` 属隐式 Intent，
 *   Android 11+ 的包可见性限制不影响 `startActivity` 的解析（限制的是
 *   `queryIntentActivities` 之类的主动查询）。故无需新增 `<queries>` 声明。
 *
 * ⚠️ **可见性是 `internal` 而非 `private`**：Kotlin 的 `private` 是文件级的，
 * [BlockNoteEditorScreen]（探针页）需复用同一实现，两页对外链的处置必须一致。
 *
 * @param context 任意 Context（内部会加 NEW_TASK，Activity / Application 均可）
 * @param url     目标链接（应为绝对 URL；JS 侧已过滤 `javascript:` 等伪协议）
 */
internal fun openInBrowser(context: Context, url: String) {
    if (url.isBlank()) return
    val uri = try {
        Uri.parse(url)
    } catch (e: Exception) {
        Log.e(TAG, "openInBrowser: bad url $url", e)
        return
    }
    /** scheme 为空说明不是绝对 URL（如 `example.com` 裸域名）——系统无从分发 */
    if (uri.scheme.isNullOrEmpty()) {
        Log.w(TAG, "openInBrowser: no scheme, dropped: $url")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
        Log.d(TAG, "openInBrowser: $url")
    } catch (e: Exception) {
        /**
         * 无匹配应用（无浏览器 / 无邮件客户端等）。属**正常分支**，
         * 不能让它把宿主带崩 —— 只留证据。
         */
        Log.w(TAG, "openInBrowser: no activity for $url", e)
    }
}

/**
 * 创建编辑器 WebView：Bridge 注入 + 字体流拦截 + 错误日志
 *
 * @param backgroundColor 编辑区背景色（v1.9）：作为 WebView 自身底色，
 *   在 JS 首帧绘制前即生效，避免透出宿主内容区颜色造成闪白。
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createEditorWebView(
    context: Context,
    controller: BlockNoteBridgeController,
    backgroundColor: Color
): WebView {
    val appContext = context.applicationContext

    val webView = ImeSuppressibleWebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        /** 底色兜底（v1.9）：JS 侧还会覆盖 body / .bn-editor，此处保证首帧不闪 */
        setBackgroundColor(backgroundColor.toArgb())
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = true
            useWideViewPort = true
            loadWithOverviewMode = true
            /**
             * ⚠️ 这里**没有** `keyboardDisplayRequiresUserGesture` 可用（v2026-09-22 实测）：
             * Android 的 `android.webkit.WebSettings` 从 API 35 的 android.jar 里查，
             * 与键盘相关的只有 `MediaPlaybackRequiresUserGesture`——**不存在**任何
             * "键盘是否需要用户手势"的开关（该语义存在于别的平台/别的 WebView 封装，
             * 别再往这里搬）。因此"程序化聚焦后弹键盘"只能靠宿主侧显式
             * `InputMethodManager.showSoftInput`（见 [ImeSuppressibleWebView.showImeNow]），
             * 由 IMM 直接请求输入法，与 WebView 内部对"用户手势"的策略无关。
             */
            cacheMode = WebSettings.LOAD_DEFAULT
            /**
             * 多窗口支持（v2026-09-24 新增）
             *
             * **默认值是 false，这是本项目"点链接没反应"的根源之一**：
             * 该值 false 时，页面里 `window.open(url, "_blank")` 会被 Chromium
             * **静默丢弃**——不弹窗、不导航、不回调、无日志。官方 LinkToolbar 的
             * 「打开」按钮正是 `window.open(url, "_blank")`，因此点了完全没反应。
             *
             * 置 true 后请求会走到 [WebChromeClient.onCreateWindow]，
             * 我们在那里取 URL 交系统浏览器（**不创建子 WebView**）。
             *
             * ⚠️ 这两个开关必须**成对开启**：只开 `setSupportMultipleWindows`
             * 时，Chromium 仍可能以"非用户手势"为由丢弃请求。WebView 对手势的
             * 判定粒度较粗，两者同开才是稳定形态。
             */
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }

        addJavascriptInterface(
            object {
                /** 上行消息入口（JS→Kotlin），消息格式见 docs/bridge-protocol.md */
                @JavascriptInterface
                fun postMessage(json: String) {
                    controller.handleUpMessage(json)
                }
            },
            "AndroidBridge"
        )

        webViewClient = object : WebViewClient() {
            /**
             * 导航通道 ①：超链接点击（v2026-09-24 新增）
             *
             * 编辑器页面是 assets 里的本地文件，除自身外**任何**目标 URL 都不该在
             * WebView 内加载——否则编辑器被目标页顶掉（内容丢失、无后退项）。
             * 白名单判据统一收敛在 [isEditorInternalUrl]（编辑器页面本体 /
             * 字体流虚拟域 / 本地媒体附件），不在本处重复列举。
             *
             * ⚠️ 本回调在 API 24+ 走 `WebResourceRequest` 重载（旧 `String` 重载
             * 已废弃且不会在此版本被调用），故只实现新版即可。
             *
             * ⚠️ 返回 `true` = "已处理，别导航"；返回 `false` = 放行给 WebView。
             * 非白名单一律 `true` + 外送浏览器。
             */
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                /** 编辑器自身 / 字体流虚拟域 / 本地媒体：放行（都是页面运行必需） */
                if (isEditorInternalUrl(url)) return false
                Log.d(TAG, "nav intercepted (link): $url")
                openInBrowser(appContext, url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "page finished: $url")
                /**
                 * v2026-09-21：页面加载完成后补一次「软键盘抑制」注入。
                 * 页面重载（或首帧早于抑制开启）会丢掉此前的标记，此处兜住。
                 */
                (view as? ImeSuppressibleWebView)?.applyImeSuppression()
            }

            /**
             * 导航通道 ③：兜底还原（v2026-09-24 新增）
             *
             * 前两道（`shouldOverrideUrlLoading` / `onCreateWindow`）覆盖了常规路径，
             * 但仍有漏网的可能：JS 里直接 `location.href = ...`（**不经过**链接点击
             * 回调）、`<meta http-equiv="refresh">`、或未来某次升级改了行为。
             * 一旦真发生，编辑器就被顶掉了，用户会看到"开发者的 WebView 在读新闻"。
             *
             * 这里做最后一道兜底：**主框架**一旦开始加载非白名单 URL，立刻终止导航
             * 并把编辑器 URL 重新载回来——这是用户可见的故障，必须自愈，
             * 不能只打日志。
             *
             * ⚠️ `onPageStarted` **本身就只对主框架触发**（子资源不触发本回调），
             * 故此处无需再判 `isForMainFrame`——不存在"图片加载被误拦"的风险。
             * ⚠️ 重载前 `stopLoading()`：否则中止的导航与新的 loadUrl 会互相打断。
             */
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (view == null || url == null) return
                if (isEditorInternalUrl(url)) return
                Log.w(TAG, "main-frame nav to foreign url, restoring editor: $url")
                view.stopLoading()
                view.loadUrl(EDITOR_URL)
            }

            /** S5：字体流拦截——res/font 字体以流回给 WebView（字体单份存储，零体积增量） */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host != FONT_HOST) return null
                val path = url.path ?: return null
                if (!path.startsWith(FONT_PATH_PREFIX) || !path.endsWith(".ttf")) return null
                val segments = path.removePrefix(FONT_PATH_PREFIX)
                    .removeSuffix(".ttf").split("/")
                if (segments.size != 2) return null
                val (fontId, weightStr) = segments
                val weight = weightStr.toIntOrNull() ?: return null
                val resId = FontCatalog.bridgeFontResMap()[fontId]?.get(weight) ?: return null
                return try {
                    // WebResourceResponse 接管流生命周期，由框架负责关闭
                    WebResourceResponse("font/ttf", null, appContext.resources.openRawResource(resId))
                } catch (e: Exception) {
                    Log.e(TAG, "font stream fail: $fontId/$weight", e)
                    null
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e(TAG, "load error: ${request?.url} ${error?.description}")
            }
        }

        /**
         * 导航通道 ②：JS `window.open`（v2026-09-24 新增）
         *
         * **为什么必须专门接管这一条**：Android WebView 的
         * `WebSettings.setSupportMultipleWindows()` **默认 false**，此时页面里
         * 任何 `window.open(url, "_blank")` 会被 Chromium **静默丢弃**——
         * 不弹新窗口、不导航主框架、不触发 `shouldOverrideUrlLoading`、
         * 不报错、连 console 都没有。官方 LinkToolbar 的「打开」按钮正是这么写的
         * （`@blocknote/react` 的 `OpenLinkButton.tsx`：`window.open(url, "_blank")`），
         * 这正是"点打开毫无反应"的根源。
         *
         * **修法**：打开多窗口支持，让 Chromium 把请求交到 `onCreateWindow`；
         * 我们在回调里**不创建新 WebView**，而是取出目标 URL 交系统浏览器，
         * 并 `return false` 表示"宿主已处理，无需 WebView 创建子窗口"。
         *
         * ⚠️ 配套必须同时开 `setJavaScriptCanOpenWindowsAutomatically(true)`，
         * 否则 Chromium 仍会以"未经用户手势"为由丢弃请求（WebView 的判定粒度较粗，
         * 两个开关一起开才是稳定形态）。
         *
         * ⚠️ 本回调若返回 true 却**不**调用 `resultMsg.sendToTarget()`，
         * WebView 会一直等这个 `WebViewTransport`，可能拖住渲染进程；
         * 故无论如何都在末尾 `sendToTarget()`（不塞 WebView 即为"取消"）。
         */
        setWebChromeClient(object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                /**
                 * ⚠️ 这里**取不到**目标 URL：`WebViewTransport` 要等宿主在
                 * 结果 Message 里塞进一个 WebView 后，才由那个新 WebView 去加载。
                 * 因此采取「造一个一次性 WebView 接住 URL，再读取它」的做法：
                 *
                 * 1. 造一个不可见的临时 WebView，塞进 resultMsg 发回；
                 * 2. Chromium 随即让这个临时 WebView 加载目标 URL，触发其
                 *    `shouldOverrideUrlLoading`（我们已在上面把"非白名单一律外送
                 *    浏览器 + return true"写成统一策略，此处自然复用）；
                 * 3. 临时 WebView 立刻销毁，绝不进入视图树。
                 *
                 * 这样"取 URL"与"外送"两条逻辑都收敛在已有实现里，无需重复解析。
                 */
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                if (transport == null || view == null) {
                    resultMsg?.sendToTarget()
                    return false
                }
                /**
                 * ⚠️ 临时 WebView 用**同一个 applicationContext**：
                 * 它只是用来触发一次 `shouldOverrideUrlLoading`，
                 * 归属哪个 Activity 无所谓；用 appContext 还能避免持有 Activity。
                 */
                val holder = WebView(appContext)
                holder.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (!url.isNullOrEmpty() && !isEditorInternalUrl(url)) {
                            Log.d(TAG, "nav intercepted (window.open): $url")
                            openInBrowser(appContext, url)
                        } else if (url != null) {
                            Log.d(TAG, "window.open to internal url ignored: $url")
                        }
                        /** 用完即毁：不留任何 WebView 实例 */
                        v?.destroy()
                        return true
                    }
                }
                transport.webView = holder
                resultMsg.sendToTarget()
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                /** console 级别 → logcat 优先级（ERROR→E / WARNING→W / 其余→D） */
                val priority = when (message.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                    ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                    else -> Log.DEBUG
                }
                Log.println(priority, TAG, "console[${message.lineNumber()}] ${message.message()}")
                return true
            }
        })

        loadUrl(EDITOR_URL)
    }
    controller.webView = webView
    return webView
}

/**
 * 注入脚本中「抑制开关」的占位符：执行前由 [ImeSuppressibleWebView.applyImeSuppression]
 * 替换为 true / false。用占位符而非字符串拼接，是为了让整段脚本保持可读的 raw string。
 */
private const val IME_SUPPRESS_FLAG = "__CORGI_IME_SUPPRESS_FLAG__"

/**
 * 软键盘抑制脚本（v2026-09-21）
 *
 * **为什么除了原生拦截还要在页面里打标记**：
 * Android WebView 的软键盘由 Chromium（AwContents）**主动**请求，Kotlin 侧的
 * `onCreateInputConnection` 拦截只能让输入法拿不到输入连接，个别 ROM / 输入法
 * 仍会把键盘面板拉起来。`inputmode="none"` 则是 Chromium 官方支持的键盘抑制语义
 * （Web 侧"自绘键盘"应用的标准做法）：在**聚焦之前**给可编辑元素打上该属性，
 * 浏览器自己就不会请求软键盘——两道保险互兜。
 *
 * **为什么用 MutationObserver 而不是一次性打标**：
 * BlockNote 的编辑器 DOM 由 JS 动态挂载，切换块类型时局部节点还会重建，
 * 一次性打标会被"新节点"绕过。抑制期间挂 observer，任何新出现的可编辑节点、
 * 以及 `contenteditable` 属性被改写的节点，都会立刻补上 `inputmode="none"`；
 * 解除抑制时断开 observer，并把 `inputmode` 还原为打标前的原值。
 *
 * **幂等性**：脚本自带 `window.__corgiImeSuppress` 状态位，重复注入只更新开关、
 * 不叠加 observer；`data-ime-prev` 记录原始属性值，故还原是精确的。
 */
private const val IME_SUPPRESS_SCRIPT = """
(function () {
  var S = window.__corgiImeSuppress || (window.__corgiImeSuppress = { on: false, obs: null });
  var ON = __CORGI_IME_SUPPRESS_FLAG__;
  S.on = ON;

  function mark(root) {
    if (!root || root.nodeType !== 1) return;
    var list = [];
    if (root.matches && root.matches('[contenteditable="true"], .bn-editor')) list.push(root);
    if (root.querySelectorAll) {
      list = list.concat(Array.prototype.slice.call(
        root.querySelectorAll('[contenteditable="true"], .bn-editor')));
    }
    list.forEach(function (el) {
      if (el.getAttribute('inputmode') === 'none') return;
      if (el.getAttribute('data-ime-prev') === null) {
        el.setAttribute('data-ime-prev', el.getAttribute('inputmode') || '');
      }
      el.setAttribute('inputmode', 'none');
    });
  }

  function unmark() {
    Array.prototype.forEach.call(document.querySelectorAll('[data-ime-prev]'), function (el) {
      var prev = el.getAttribute('data-ime-prev');
      if (prev) el.setAttribute('inputmode', prev); else el.removeAttribute('inputmode');
      el.removeAttribute('data-ime-prev');
    });
  }

  if (ON) {
    mark(document.body);
    if (!S.obs && window.MutationObserver && document.documentElement) {
      S.obs = new MutationObserver(function (records) {
        if (!S.on) return;
        records.forEach(function (r) {
          if (r.type === 'attributes') mark(r.target);
          Array.prototype.forEach.call(r.addedNodes, function (n) { mark(n); });
        });
      });
      S.obs.observe(document.documentElement, {
        childList: true, subtree: true, attributes: true, attributeFilter: ['contenteditable']
      });
    }
  } else {
    if (S.obs) { S.obs.disconnect(); S.obs = null; }
    unmark();
  }
})();
"""

/**
 * 可抑制软键盘的编辑器 WebView（v2026-09-21 新增）
 *
 * 背景：灵感编辑页底部工具栏的「T 字体面板 / Aa 字号颜色面板」展开时，面板以
 * 「键盘高度」占据键盘位（见 InspirationEditBottomBar 的 keyboardHeight 记录逻辑）。
 * 此时用户在正文里聚焦光标 / 多选，Chromium 会请求软键盘 → 键盘把面板顶走，
 * 并压缩 WebView 视口（v1.11.9 的"视口坍缩到一行高"正由该正反馈引发）。
 *
 * 因此这里提供「保留编辑能力、屏蔽 IME」的状态，[isImeSuppressed] = true 时：
 * 1. 立刻隐藏当前键盘；
 * 2. [onCreateInputConnection] 返回 null（输入法拿不到输入连接，不建立编辑会话）；
 * 3. [onCheckIsTextEditor] 返回 false（系统输入法框架不再视其为可输入控件，
 *    连"尝试显示键盘"都不会做）；
 * 4. 向页面注入 `inputmode="none"`（Chromium 侧就不再请求键盘）。
 *
 * 传回 false 时全部解除。**v2026-09-22 修订**：解除后是否弹回键盘不再"一律不弹"——
 * 宿主（InspirationEditScreen）在面板收起且正文仍持有 DOM 焦点时，会先令编辑器
 * 重新聚焦（[BlockNoteBridgeController.focusEditor]）再调 [showImeNow] 把键盘交还用户。
 * 焦点已不在正文时则维持原行为（保持收起），不会抢键盘。
 *
 * ⚠️ 本状态**不影响光标与选区**：WebView 的点击定位光标、长按选词、拖动选择手柄
 * 都由其自身的触摸手势与渲染层承担，与 IME 无关；`onCheckIsTextEditor` 只被
 * 系统输入法框架用于判断"要不要弹键盘"。
 *
 * 回退点（若个别 ROM 上出现"选择手柄 / 浮动工具条异常"）：把
 * [onCreateInputConnection] 与 [onCheckIsTextEditor] 两个 override 注释掉即可退到
 * 「JS `inputmode` + 主动隐藏键盘」两道，焦点与选区完全走原生默认路径。
 */
private class ImeSuppressibleWebView(context: Context) : WebView(context) {

    /** 是否抑制软键盘（宿主面板展开期间为 true） */
    var isImeSuppressed: Boolean = false
        set(value) {
            /** 值未变直接返回：`AndroidView.update` 每次重组都会赋值，此处必须幂等 */
            if (field == value) return
            field = value
            /** 打开抑制时先收掉"可能已经弹出来"的键盘，再做后面两道拦截 */
            if (value) hideImeNow()
            applyImeSuppression()
            Log.d(TAG, "ime suppress = " + value)
        }

    /**
     * 抑制期间不返回输入连接：输入法拿不到 InputConnection 便不会建立编辑会话，
     * 因而不会弹出键盘面板。
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (isImeSuppressed) {
            Log.d(TAG, "onCreateInputConnection: suppressed")
            return null
        }
        return super.onCreateInputConnection(outAttrs)
    }

    /** 抑制期间对外声明"不是文本编辑器"，避免系统输入法框架主动尝试拉起键盘 */
    override fun onCheckIsTextEditor(): Boolean {
        if (isImeSuppressed) return false
        return super.onCheckIsTextEditor()
    }

    /**
     * 立即隐藏软键盘（幂等：键盘未显示时为空操作）。
     *
     * ⚠️ `windowToken` 为空表示视图尚未 attach 到窗口，此时把 null 传给
     * `hideSoftInputFromWindow` 会抛 IllegalArgumentException，故先做空值守卫。
     */
    fun hideImeNow() {
        val token = windowToken ?: return
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return
        imm.hideSoftInputFromWindow(token, 0)
    }

    /**
     * 主动弹出软键盘（v2026-09-22；与 [hideImeNow] 成对）
     *
     * 面板收起后把键盘交还给用户（正文仍有光标时）。三步各自解决一类失败：
     * 1. **恢复可聚焦性**：触摸模式下 `View.requestFocus()` 只对
     *    `focusableInTouchMode` 的视图生效，而焦点此前可能已被 Compose 根视图拿走，
     *    故先显式打开这两个标志再 `requestFocus`；
     * 2. **`restartInput`**：抑制期间 `onCreateInputConnection` 返回过 null，
     *    IMM 侧可能仍缓存着"这不是文本编辑器"的判断，这里强制重建输入连接；
     * 3. **`showSoftInput`**：真正请求键盘面板（放到 `post` 里，等焦点与布局稳定）。
     *
     * ⚠️ 前提是 DOM 焦点已在编辑器上（见 [BlockNoteBridgeController.restoreIme] 的
     * 第 1 步）——否则 Chromium 建立连接后自己又会把它收起来。
     */
    fun showImeNow() {
        isFocusable = true
        isFocusableInTouchMode = true
        val focused = requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm == null) {
            Log.w(TAG, "showImeNow: no InputMethodManager")
            return
        }
        post {
            imm.restartInput(this)
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
        Log.d(TAG, "ime show requested | viewFocus=$focused")
    }

    /**
     * 把当前抑制状态注入页面（脚本见 [IME_SUPPRESS_SCRIPT]）。
     *
     * 调用时机有三个，缺一不可：
     * 1. [isImeSuppressed] 变化时（来自 `AndroidView.update`，UI 线程）——即时生效；
     * 2. `onPageFinished`（UI 线程）——页面重载后标记会丢失；
     * 3. 桥 `ready` 上行后——编辑器 DOM 此时才真正挂载（进页即展开面板的场景）。
     *
     * ⚠️ 必须在 **UI 线程**调用：`evaluateJavascript` 等 WebView 方法对线程有硬性要求，
     * 在别的线程调用会抛 IllegalStateException（若发生在桥回调里，会被 `handleUpMessage`
     * 的 catch 静默吞掉，连带后续语句也不执行——正是「正文卡在正在装载…」的成因）。
     * 三个时机都在 UI 线程：前两个本身就在（`AndroidView.update` / `onPageFinished`），
     * 第三个由 [handleUpMessage] 在入口处统一 post 到主线程（见其线程模型说明）。
     */
    fun applyImeSuppression() {
        val flag = if (isImeSuppressed) "true" else "false"
        evaluateJavascript(IME_SUPPRESS_SCRIPT.replace(IME_SUPPRESS_FLAG, flag), null)
    }
}
