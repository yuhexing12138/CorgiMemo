package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
 * @param canSetBlockColor 是否支持块级颜色（决定「块颜色」入口是否可点）
 * @param blockTextColor 当前块文本色（预设色名；空串 = 默认色，用于色板回显）
 * @param blockBackgroundColor 当前块背景色（预设色名；空串 = 无背景色）
 * @param canToggleHeader 是否可切换表头（table 块且 header 特性开启）
 * @param isHeaderRow 表格当前是否有标题行（非表格恒 false）
 * @param isHeaderCol 表格当前是否有标题列（非表格恒 false）
 */
data class BlockState(
    val blockType: String = "",
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

    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingInit: JSONObject? = null
    private var pendingCommands = mutableListOf<JSONObject>()

    /** 装载编辑器内容（宿主在内容就绪后调用；ready 前调用会缓存待 ready 补发） */
    fun load(markdown: String) {
        latestMarkdown = markdown
        val init = JSONObject()
            .put("type", "init")
            .put("markdown", markdown)
            .put("readOnly", false)
            .put("fontFamily", "system_default")
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

    internal fun handleUpMessage(json: String) {
        Log.d(TAG, "up(${json.take(120)})")
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
                    mainHandler.post { flushIfReady() }
                }
                "changed" -> {
                    val md = msg.optString("markdown")
                    latestMarkdown = md
                    mainHandler.post { onMarkdownChanged?.invoke(md) }
                }
                "undoState" -> {
                    // v1.7：撤销/重做可用态上行 → 驱动宿主按钮 enabled（Compose 快照态，主线程安全）
                    val u = msg.optBoolean("canUndo", false)
                    val r = msg.optBoolean("canRedo", false)
                    mainHandler.post {
                        canUndo = u
                        canRedo = r
                    }
                }
                "blockState" -> {
                    // v1.11：当前光标块状态上行 → 驱动宿主工具栏的
                    // 删除块 / 块颜色 / 表头行 / 表头列 四个入口的可用态与回显。
                    // 一次性构造后整体赋值，避免多次 post 造成中间态（如颜色已改而可用态未改）。
                    // 注意 optString 对缺失字段返回 ""，正好与 BlockState 的默认值语义一致。
                    val st = BlockState(
                        blockType = msg.optString("blockType"),
                        canSetBlockColor = msg.optBoolean("canSetBlockColor", false),
                        blockTextColor = msg.optString("blockTextColor"),
                        blockBackgroundColor = msg.optString("blockBackgroundColor"),
                        canToggleHeader = msg.optBoolean("canToggleHeader", false),
                        isHeaderRow = msg.optBoolean("isHeaderRow", false),
                        isHeaderCol = msg.optBoolean("isHeaderCol", false),
                    )
                    mainHandler.post { blockState = st }
                }
                "diagnostic" ->
                    /**
                     * v1.11.7：JS 主动回传的运行时观测值（CSS 变量、computed style、
                     * 元素实际高度等）。**只打 log，不做任何 UI 反应**——
                     * WebView 内部的这些真值在 Kotlin 侧无法直接观测，以往只能靠猜。
                     * 排查命令用：`adb logcat -s BlockNoteEditor:V | grep -E "diag|down\("`
                     */
                    Log.d(TAG, "diag | ${msg.optString("message")}")
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
 * - 内容装载：宿主在数据就绪后调 [controller].load(markdown)（见 [BlockNoteBridgeController]）
 * - 内容变更：JS 防抖 800ms 后经 [BlockNoteBridgeController.onMarkdownChanged] 上行
 * - 撤销/重做/保存：controller 命令
 * - 主题：跟随 App 主题（ThemeManager 深浅 + 六色主色，变化自动下行）
 * - 字体：fonts 清单下行 + shouldInterceptRequest 字体流（res/font 单份存储）
 *
 * @param backgroundColor 宿主编辑区实际背景色（v1.9）：由宿主下行到 JS，
 *   让 WebView 内部 `.bn-editor` 与 body 与宿主主题背景一致，消除"画中画"白底框；
 *   同时作为 WebView 自身底色兜底，避免首帧未绘制时透出色差。
 */
@Composable
fun BlockNoteEditorWebView(
    controller: BlockNoteBridgeController,
    onMarkdownChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
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
            update = { wv -> wv.setBackgroundColor(effectiveBackground.toArgb()) },
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
    Log.d(TAG, "down(${msg.optString("type")})")
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

    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        /** 底色兜底（v1.9）：JS 侧还会覆盖 body / .bn-editor，此处保证首帧不闪 */
        setBackgroundColor(backgroundColor.toArgb())
        /**
         * 禁用 overscroll 拉伸（v1.11.9）：Android 12+ 的 WebView overscroll
         * 是 stretch 效果——滑到内容顶/底时整页被拉伸回弹，观感为「页面上下
         * 跳跃」的候选成因之一。禁用只移除这层视觉装饰，滚动与边界行为不变。
         */
        overScrollMode = View.OVER_SCROLL_NEVER
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

        loadUrl(EDITOR_URL)
    }
    controller.webView = webView
    return webView
}
