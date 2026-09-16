package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.corgimemo.app.ui.theme.FontCatalog
import com.corgimemo.app.ui.theme.ThemeManager
import org.json.JSONArray
import org.json.JSONObject

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
 * 数据流纪律（docs/bridge-protocol.md）：单向数据流——
 * Kotlin 只下行「配置与命令」，内容以 markdown 快照经 `changed` 上行。
 */
class BlockNoteBridgeController {
    internal var webView: WebView? = null
    internal var ready = false
    internal var initSent = false
    internal var latestMarkdown: String = ""

    /** JS 上行的最新 markdown（changed 防抖后），宿主保存时取用 */
    var onMarkdownChanged: ((String) -> Unit)? = null

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

    /** 主动要一次 markdown 快照（返回键/切后台前） */
    fun requestSave() = enqueueCommand(JSONObject().put("type", "requestSave"))

    /** 主题下行（深浅 + 主色） */
    fun setTheme(dark: Boolean, primary: String) {
        val theme = JSONObject().put("dark", dark).put("primary", primary)
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
                    Log.d(TAG, "ready received")
                    ready = true
                    mainHandler.post { flushIfReady() }
                }
                "changed" -> {
                    val md = msg.optString("markdown")
                    latestMarkdown = md
                    mainHandler.post { onMarkdownChanged?.invoke(md) }
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
 * - 内容装载：宿主在数据就绪后调 [controller].load(markdown)（见 [BlockNoteBridgeController]）
 * - 内容变更：JS 防抖 800ms 后经 [BlockNoteBridgeController.onMarkdownChanged] 上行
 * - 撤销/重做/保存：controller 命令
 * - 主题：跟随 App 主题（ThemeManager 深浅 + 六色主色，变化自动下行）
 * - 字体：fonts 清单下行 + shouldInterceptRequest 字体流（res/font 单份存储）
 */
@Composable
fun BlockNoteEditorWebView(
    controller: BlockNoteBridgeController,
    onMarkdownChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
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

    LaunchedEffect(isDark, primaryHex) {
        controller.setTheme(isDark, primaryHex)
    }

    // onMarkdownChanged 回调更新（保持最新 lambda 引用）
    LaunchedEffect(onMarkdownChanged) {
        controller.onMarkdownChanged = onMarkdownChanged
    }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        AndroidView(
            factory = { appContext ->
                createEditorWebView(appContext, controller)
            },
            onRelease = { controller.webView = null },
            modifier = Modifier.fillMaxSize()
        )
        // appContext 仅用于 WebView 工厂上下文语义校验（编译引用保留）
        @Suppress("UNUSED_EXPRESSION")
        appContext
    }
}

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

/** 创建编辑器 WebView：Bridge 注入 + 字体流拦截 + 错误日志 */
@SuppressLint("SetJavaScriptEnabled")
private fun createEditorWebView(
    context: Context,
    controller: BlockNoteBridgeController
): WebView {
    val appContext = context.applicationContext

    val webView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
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
