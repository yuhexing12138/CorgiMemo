package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 * @param headingLevel 光标块的标题级别（v2026-09-21 新增；供「标题面板」回显选中态）：
 *   `blockType == "heading"` 时为 1–6（普通标题**与**可折叠标题同为 heading 块），
 *   非标题块为 0（不高亮任何格子）。
 *   ⚠️ v2026-09-22 修正：原注释称"可折叠标题是独立块类型 toggleHeading*"，与 BlockNote
 *   真实模型不符——折叠态是 `props.isToggleable`，不是块类型，故级别一律取自 props.level。
 * @param headingToggleable 是否为「可折叠标题」（v2026-09-22 新增）：BlockNote 里折叠标题
 *   = `heading` 块 + `props.isToggleable = true`，级别仍看 [headingLevel]。标题面板据此在
 *   「普通标题 / 可折叠标题」两个分区之间分流——两类都有 1/2/3 级，只靠级别无法区分。
 *   普通标题与非标题块恒为 false。
 * @param canSetBlockColor 是否支持块级颜色（决定「块颜色」入口是否可点）
 * @param blockTextColor 当前块文本色（预设色名；空串 = 默认色，用于色板回显）
 * @param blockBackgroundColor 当前块背景色（预设色名；空串 = 无背景色）
 * @param canToggleHeader 是否可切换表头（table 块且 header 特性开启）
 * @param isHeaderRow 表格当前是否有标题行（非表格恒 false）
 * @param isHeaderCol 表格当前是否有标题列（非表格恒 false）
 */
data class BlockState(
    val blockType: String = "",
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
     * 当前选区字号档位（v2026-09-21 新增；H 面板「正文字号」高亮回显）：
     * JS 侧 `getActiveStyles().fontSize`（"18px" 形式）解析的整数——
     * WebView 内 1px=1dp，数值与宿主的 sp 档位直接对应；0 = 无字号样式（默认档 16）。
     */
    val fontSizeSp: Int = 0,
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingInit: JSONObject? = null
    private var pendingCommands = mutableListOf<JSONObject>()

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
        if (ready && !initSent) {
            // ready 前的命令无编辑器可作用，直接丢弃（init 会在 ready 后重放内容）
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
                    Log.d(TAG, "ready received | build=$build")
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
                }
                "undoState" -> {
                    // v1.7：撤销/重做可用态上行 → 驱动宿主按钮 enabled（Compose 快照态，主线程安全）
                    canUndo = msg.optBoolean("canUndo", false)
                    canRedo = msg.optBoolean("canRedo", false)
                }
                "blockState" -> {
                    // v1.11：当前光标块状态上行 → 驱动宿主工具栏的
                    // 删除块 / 块颜色 / 表头行 / 表头列 四个入口的可用态与回显。
                    // 一次性构造后整体赋值，避免多次赋值造成中间态（如颜色已改而可用态未改）。
                    // 注意 optString 对缺失字段返回 ""，正好与 BlockState 的默认值语义一致。
                    blockState = BlockState(
                        blockType = msg.optString("blockType"),
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
                        /** v2026-09-21：选区字号（0 = 无样式 → 宿主回落默认档） */
                        fontSizeSp = msg.optInt("fontSizePx", 0),
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
                "error" -> Log.e(TAG, "js error: ${msg.optString("message")}")
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
    val wv = webView ?: return
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
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "page finished: $url")
                /**
                 * v2026-09-21：页面加载完成后补一次「软键盘抑制」注入。
                 * 页面重载（或首帧早于抑制开启）会丢掉此前的标记，此处兜住。
                 */
                (view as? ImeSuppressibleWebView)?.applyImeSuppression()
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
         * console 转发（v2026-09-21）：JS 侧 console.log/warn/error 全量转 logcat。
         *
         * 背景：@font-face 字体加载失败（404 / CORS 拒绝 / 解码失败）Chromium **只打
         * console，不触发 onReceivedError**——没有这条通道，正文字体「换了个寂寞」时
         * 完全黑盒。现在 `adb logcat -s chromium`（或本 TAG）可直接看到字体流的真实结果，
         * 配合 [shouldInterceptRequest] 的拦截日志即可闭环诊断字体链路。
         */
        setWebChromeClient(object : WebChromeClient() {
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
