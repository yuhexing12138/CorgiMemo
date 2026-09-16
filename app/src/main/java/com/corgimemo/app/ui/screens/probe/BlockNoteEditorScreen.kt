package com.corgimemo.app.ui.screens.probe

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * BlockNote 正式编辑器容器（迁移 P0-S3）
 *
 * 职责（对照 docs/bridge-protocol.md v1）：
 * - 注入 AndroidBridge（JS→Kotlin 上行通道）
 * - 编辑器 ready 后下行 init{markdown, readOnly, theme, fontFamily}
 * - 收 changed 更新本地最新 markdown（防抖已在 JS 侧完成）
 * - 离开页面（onDispose）时把最新 markdown 交给 onSave（P0 内存 mock，P1-S13 接 Repository）
 * - BackHandler：返回键走 requestSave 协议后关闭
 * - 容器策略：imePadding（POC 定案，见适配度报告 §7.5）
 *
 * 主题/字体的真值在 P0 阶段来自 [resolveTheme]/[resolveFontFamily] 的简化实现，
 * P1 接 ThemeManager / ContentFontManager。
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
    val context = LocalContext.current

    /** 返回键：保存最新快照后关闭（changed 防抖值已持有，requestSave 作为协议兜底） */
    BackHandler {
        webViewRef?.let { sendDown(it, downMessage("requestSave")) }
        onSaveMarkdown(latestMarkdown)
        onBack()
    }

    // 离开页面时保存（覆盖系统返回手势之外的销毁路径）
    DisposableEffect(Unit) {
        onDispose { if (ready) onSaveMarkdown(latestMarkdown) }
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
                            // ready 后下行 init（主题/字体 P0 用简化真值）
                            webViewRef?.let { wv ->
                                val init = downMessage("init")
                                init.put("markdown", initialMarkdown)
                                init.put("readOnly", false)
                                init.put(
                                    "theme",
                                    JSONObject()
                                        .put("dark", resolveIsDarkTheme(appContext))
                                        .put("primary", "#1976d2")
                                )
                                init.put("fontFamily", resolveFontFamily())
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

/** P0 简化：跟随系统深色模式（P1 接 ThemeManager 六色主题） */
private fun resolveIsDarkTheme(context: Context): Boolean {
    val mode = context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK
    return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/** P0 简化：固定系统默认字体（P1 接 ContentFontManager + 字体流拦截） */
private fun resolveFontFamily(): String = "system_default"

/** 组装下行消息（键值对 → JSONObject） */
private fun downMessage(type: String, vararg fields: Pair<String, Any>): JSONObject {
    val obj = JSONObject()
    obj.put("type", type)
    fields.forEach { (k, v) -> obj.put(k, v) }
    return obj
}

/** 下行：Kotlin → JS（evaluateJavascript 调 Bridge 宿主） */
private fun sendDown(webView: WebView, msg: JSONObject) {
    webView.evaluateJavascript("window.BlockNoteEditorHost.onMessage(${msg})", null)
}

/** 创建编辑器 WebView：Bridge 注入 + 错误日志（S5 将在此扩展 shouldInterceptRequest 字体流拦截） */
@SuppressLint("SetJavaScriptEnabled")
private fun createEditorWebView(
    context: Context,
    onReady: () -> Unit,
    onChanged: (markdown: String) -> Unit
): WebView {
    val mainHandler = android.os.Handler(context.mainLooper)

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
                    try {
                        val msg = JSONObject(json)
                        when (msg.optString("type")) {
                            "ready" -> mainHandler.post { onReady() }
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

        loadUrl("file:///android_asset/blocknote-web/editor/index.html")
    }
    return webView
}
