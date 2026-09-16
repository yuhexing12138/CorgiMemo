package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
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
                                Log.d(TAG, "ready received → sending init")
                                mainHandler.post { onReady() }
                            }
                            "changed" -> {
                                val md = msg.optString("markdown")
                                mainHandler.post { onChanged(md) }
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

        loadUrl("file:///android_asset/blocknote-web/editor/editor.html")
    }
    return webView
}
