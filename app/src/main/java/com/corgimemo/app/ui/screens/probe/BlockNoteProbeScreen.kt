package com.corgimemo.app.ui.screens.probe

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * BlockNote 探针 WebView 容器（POC 专用，验证 5 点清单的 #1/#2/#3）
 *
 * 加载 assets/blocknote-probe/index.html（viteSingleFile 内联单文件），
 * 对应真机验证项：
 * - #1 移动端跨块选择（长按拖拽跨段落/图片块，页面顶部「选区检测」实时显示）
 * - #2 中文 IME（组合输入状态 + 块边界退格 + 撤销链路）
 * - #3 软键盘 inset（顶栏开关切换两种容器行为对照：
 *     开 = Compose imePadding 让 WebView 随键盘缩放；
 *     关 = WebView 全屏，由内核 visualViewport 自行适配）
 *
 * 入口（adb 直达，不占任何现有 UI 入口）：
 * adb shell am start -n com.corgimemo.app/.ui.MainActivity --es navigate_to blocknote_probe
 */
@Composable
fun BlockNoteProbeScreen(onBack: () -> Unit) {
    /** 是否让 WebView 随软键盘缩放（imePadding）；关 = 交给内核 visualViewport */
    var imePaddingEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏：返回 + 标题 + 键盘适配开关
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "BlockNote 探针",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "键盘缩放",
                style = MaterialTheme.typography.bodySmall
            )
            Switch(
                checked = imePaddingEnabled,
                onCheckedChange = { imePaddingEnabled = it }
            )
        }

        // WebView 容器：factory 仅首次组合执行（WebView 不随开关重建）；
        // imePadding 开 = Compose 侧随键盘缩放；关 = WebView 全屏由内核 visualViewport 适配
        AndroidView(
            factory = { context -> createProbeWebView(context) },
            modifier = Modifier
                .fillMaxSize()
                .then(if (imePaddingEnabled) Modifier.imePadding() else Modifier)
        )
    }
}

/**
 * 创建探针 WebView：
 * - 允许 file:// 跨文件访问（探针页为单文件内联，通常无跨文件请求，此开关作兜底；
 *   Chromium 148+ 收紧 file:// CORS 的坑，POC 资产全部来自 APK 内可信内容）
 * - JavaScript / DOM 存储开启
 * - 加载错误打到 logcat（tag=BlockNoteProbe）
 */
private fun createProbeWebView(context: Context): WebView = WebView(context).apply {
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
    webViewClient = object : WebViewClient() {
        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            Log.e(
                "BlockNoteProbe",
                "load error: ${request?.url} ${error?.description}"
            )
        }
    }
    loadUrl("file:///android_asset/blocknote-web/probe/index.html")
}
