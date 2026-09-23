package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.corgimemo.app.ui.theme.FontCatalog
import com.corgimemo.app.ui.theme.ThemeManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * BlockNote 正式编辑器容器（迁移 P0）
 *
 * 职责（对照 docs/bridge-protocol.md）：
 * - 注入 AndroidBridge（JS→Kotlin 上行通道）；ready 后下行 init（含字体清单 fonts）
 * - 收 changed 更新本地最新 markdown（防抖在 JS 侧）；离开页面时交给 onSave
 * - **主题**（S6）：collect ThemeManager（深浅模式 + 六色主色），变化即下行 setTheme
 * - **字体**（S5）：init 下行字体清单；`shouldInterceptRequest` 拦截
 *   `https://corgimemo.local/fonts/{id}/{weight}.ttf` 并以 `openRawResource` 流式回流
 *   （字体留在 res/font/ 单份存储，零 APK 体积增量）；setFontFamily 下行切换
 * - 容器策略：imePadding（POC 定案，见适配度报告 §7.5）
 *
 * 入口：adb `--es navigate_to blocknote_editor`（P3 起接入正式入口与灰度开关）
 */
@Composable
fun BlockNoteEditorScreen(
    initialMarkdown: String,
    onSaveMarkdown: (String) -> Unit,
    onBack: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var latestMarkdown by remember { mutableStateOf(initialMarkdown) }

    // 主题真值（S6）：深浅模式 + 六色主色
    val themeMode by ThemeManager.themeMode.collectAsState()
    val themeColor by ThemeManager.themeColor.collectAsState()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val primaryHex = primaryColorHex(themeColor)

    /** 主题/字体变化即下行（init 由 ready 触发发全量，此处补增量同步） */
    androidx.compose.runtime.LaunchedEffect(isDark, primaryHex) {
        if (ready) {
            webViewRef?.let { sendDown(it, setThemeMessage(isDark, primaryHex)) }
        }
    }

    /** 返回键：保存最新快照后关闭（changed 防抖值已持有，requestSave 作为协议兜底） */
    BackHandler {
        webViewRef?.let { sendDown(it, downMessage("requestSave")) }
        onSaveMarkdown(latestMarkdown)
        onBack()
    }

    // 离开页面时保存（覆盖系统返回手势之外的销毁路径）
    DisposableEffect(Unit) {
        onDispose {
            if (ready) {
                onSaveMarkdown(latestMarkdown)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(onClick = {
                webViewRef?.let { sendDown(it, downMessage("requestSave")) }
                onSaveMarkdown(latestMarkdown)
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "BlockNote 编辑器（P0）",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Box(modifier = Modifier.fillMaxSize().imePadding()) {
            AndroidView(
                factory = { appContext ->
                    createEditorWebView(
                        context = appContext,
                        onReady = {
                            ready = true
                            webViewRef?.let { wv ->
                                val init = downMessage("init")
                                init.put("markdown", initialMarkdown)
                                init.put("readOnly", false)
                                init.put(
                                    "theme",
                                    JSONObject().put("dark", isDark).put("primary", primaryHex)
                                )
                                init.put("fontFamily", "system_default")
                                init.put("fonts", fontsPayload())
                                sendDown(wv, init)
                            }
                        },
                        onChanged = { md -> latestMarkdown = md }
                    ).also { webViewRef = it }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (!ready) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/** 六色主题 key → 主色 hex（与 Color.kt 的 ThemePresets 表一致；未知 key 兜底暖阳橙） */
private fun primaryColorHex(key: String): String = when (key) {
    "pink" -> "#FFB5C2"
    "green" -> "#7EC8A0"
    "blue" -> "#7EB8DA"
    "purple" -> "#B8A0D4"
    "brown" -> "#C4A882"
    else -> "#FF9A5C" // orange + 兜底
}

/** 字体清单载荷：{ 字体id: [字重...] }（JS 侧据此生成 @font-face） */
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

/** setTheme 下行消息 */
private fun setThemeMessage(dark: Boolean, primary: String): JSONObject =
    downMessage("setTheme")
        .put("theme", JSONObject().put("dark", dark).put("primary", primary))

/** 组装下行消息（键值对 → JSONObject） */
private fun downMessage(type: String, vararg fields: Pair<String, Any>): JSONObject {
    val obj = JSONObject()
    obj.put("type", type)
    fields.forEach { (k, v) -> obj.put(k, v) }
    return obj
}

/** 下行：Kotlin → JS（evaluateJavascript 调 Bridge 宿主） */
private fun sendDown(webView: WebView, msg: JSONObject) {
    Log.d(TAG, "down(${msg.optString("type")})")
    webView.evaluateJavascript("window.BlockNoteEditorHost.onMessage(${msg})", null)
}

private const val TAG = "BlockNoteEditor"

/** 字体流拦截的伪域名与其路径前缀 */
private const val FONT_HOST = "corgimemo.local"
private const val FONT_PATH_PREFIX = "/fonts/"

/**
 * 编辑器产物的加载地址（v2026-09-24 抽出为常量）
 *
 * 原先硬编码在 `loadUrl(...)` 里、只出现一次；现在 `onPageStarted` 兜底还原
 * 也要用它（主框架被劫持时把编辑器载回来），抽出常量避免两处写法漂移。
 */
private const val EDITOR_URL = "file:///android_asset/blocknote-web/editor/editor.html"

/**
 * 创建编辑器 WebView：
 * - Bridge 注入 + 错误日志
 * - 字体流拦截（S5）：命中伪域名请求时按 id+字重查 resId，openRawResource 流式回流
 */
@SuppressLint("SetJavaScriptEnabled")
private fun createEditorWebView(
    context: Context,
    onReady: () -> Unit,
    onChanged: (markdown: String) -> Unit
): WebView {
    val mainHandler = Handler(Looper.getMainLooper())
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
                    Log.d(TAG, "up(${json.take(120)})")
                    try {
                        val msg = JSONObject(json)
                        when (msg.optString("type")) {
                            "ready" -> {
                                // v1.8：构建指纹随 ready 上行，便于确认 assets 产物版本
                                Log.d(
                                    TAG,
                                    "ready received | build=${msg.optString("build", "unknown")} " +
                                        "→ sending init"
                                )
                                mainHandler.post { onReady() }
                            }
                            "changed" -> {
                                val md = msg.optString("markdown")
                                mainHandler.post { onChanged(md) }
                            }
                            "undoState" -> {
                                // v1.7：探针页只记录可用态（该页无顶栏按钮，无需驱动 UI）
                                Log.d(
                                    TAG,
                                    "undoState: canUndo=${msg.optBoolean("canUndo")}, " +
                                        "canRedo=${msg.optBoolean("canRedo")}"
                                )
                            }
                            "blockState" -> {
                                // v1.11：探针页只记录（该页无工具栏，无需驱动 UI）
                                Log.d(
                                    TAG,
                                    "blockState: type=${msg.optString("blockType")}, " +
                                        "canSetBlockColor=${msg.optBoolean("canSetBlockColor")}, " +
                                        "canToggleHeader=${msg.optBoolean("canToggleHeader")}"
                                )
                            }
                            "diagnostic" -> {
                                // v1.11.7：JS 回传的运行时观测值，仅供 logcat 排错
                                Log.d(TAG, "diag | ${msg.optString("message")}")
                            }
                            /**
                             * 外部浏览器打开链接（v2026-09-24 新增）
                             *
                             * 与正式页同一语义：JS 侧「打开」按钮 / 只读态点击
                             * 汇聚到这条上行，由宿主送系统浏览器（编辑器自身永不导航）。
                             * 完整原理见 [BlockNoteEditorWebView] 的 [openInBrowser]。
                             *
                             * ⚠️ 本分支**必须 post 到主线程**：`startActivity` 是
                             * 主线程 API，而本处运行在 WebView 的 JS 桥线程。
                             * 正式页因「整条消息已 post 到主线程再解析」而无需再 post，
                             * 探针页是内联解析，故必须显式 post。
                             */
                            "openLink" -> {
                                val url = msg.optString("url")
                                mainHandler.post { openInBrowser(appContext, url) }
                            }
                            "error" -> Log.e(
                                "BlockNoteEditor",
                                "js error: ${msg.optString("message")}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("BlockNoteEditor", "bad up message", e)
                    }
                }
            },
            "AndroidBridge"
        )

        webViewClient = object : WebViewClient() {
            /**
             * 导航通道 ①：超链接点击（v2026-09-24 新增）
             *
             * 与正式页 [createEditorWebView] 保持**行为一致**（探针页的价值就在于
             * 复现正式页的行为，两处若不齐就失去参照意义）。判据与处置全部复用
             * 同一套 `isEditorInternalUrl` / `openInBrowser`。
             */
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                if (isEditorInternalUrl(url)) return false
                Log.d(TAG, "nav intercepted (link): $url")
                openInBrowser(appContext, url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "page finished: $url")
            }

            /**
             * 导航通道 ③：兜底还原（v2026-09-24 新增）
             *
             * 主框架一旦要加载非白名单 URL，终止导航并把编辑器载回来。
             * 与正式页同一策略，详见 [createEditorWebView] 的注释。
             */
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: Bitmap?
            ) {
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
                    Log.e("BlockNoteEditor", "font stream fail: $fontId/$weight", e)
                    null
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.e(
                    "BlockNoteEditor",
                    "load error: ${request?.url} ${error?.description}"
                )
            }
        }

        /**
         * 导航通道 ②：JS `window.open`（v2026-09-24 新增）
         *
         * 与正式页同一套做法：打开多窗口支持让 Chromium 回调到 `onCreateWindow`，
         * 在那里取 URL 交系统浏览器、绝不创建子 WebView。完整原理见
         * [createEditorWebView] 中对应段落。
         */
        setWebChromeClient(object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                if (transport == null || view == null) {
                    resultMsg?.sendToTarget()
                    return false
                }
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
                        }
                        v?.destroy()
                        return true
                    }
                }
                transport.webView = holder
                resultMsg.sendToTarget()
                return true
            }
        })

        /**
         * 多窗口支持（v2026-09-24）：`setSupportMultipleWindows` 默认 false 时，
         * `window.open(url, "_blank")` 会被 Chromium 静默丢弃（无回调、无日志）。
         * 两个开关成对开启，原理详见 [createEditorWebView]。
         */
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true

        loadUrl(EDITOR_URL)
    }
    return webView
}
