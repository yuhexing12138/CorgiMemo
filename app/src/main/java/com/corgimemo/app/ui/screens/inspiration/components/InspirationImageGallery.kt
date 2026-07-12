// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import android.app.Activity
import android.util.Log
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.image.BitmapImage
import coil3.imageLoader
import coil3.request.SuccessResult
import com.corgimemo.app.util.InspirationScreenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 灵感图片全屏预览
 *
 * 深色背景（黑色），使用 HorizontalPager 支持多图滑动翻页
 * 双指捏合缩放（1x~4x），双击放大/还原，点击右上角 X 按钮关闭
 *
 * @param imagePaths 图片绝对路径列表
 * @param initialIndex 初始显示的图片索引
 * @param onDismiss 关闭回调
 */
@Composable
fun InspirationImageGallery(
    imagePaths: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    // 空列表直接关闭
    if (imagePaths.isEmpty()) {
        onDismiss()
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex) { imagePaths.size }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 下载当前显示的图片到相册：使用 Coil 同步加载图片为 Bitmap，再复用 InspirationScreenshot 保存到系统相册
    // IO 线程执行避免主线程阻塞，Snackbar 反馈结果
    fun downloadCurrentImage() {
        val path = imagePaths.getOrNull(pagerState.currentPage) ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context).data(path).build()
                    val result = context.imageLoader.execute(request)
                    if (result !is SuccessResult) {
                        Log.w("InspirationImageGallery", "图片加载失败: $path")
                        return@withContext false
                    }
                    val bitmap = (result.image as BitmapImage).bitmap
                    InspirationScreenshot.saveToGallery(context, bitmap) != null
                } catch (e: Exception) {
                    Log.e("InspirationImageGallery", "下载失败: $path", e)
                    false
                }
            }
            snackbarHostState.showSnackbar(
                if (saved) "已保存到相册" else "保存失败"
            )
        }
    }

    // 粘性沉浸式：隐藏状态栏与导航栏
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 多图 Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            ZoomableImage(path = imagePaths[page])
        }

        // 关闭按钮（右上角）
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // 页码（顶部居中）
        Text(
            text = "${pagerState.currentPage + 1}/${imagePaths.size}",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
        )

        // 下载按钮（右下角）：透明背景 + 白色图标，与关闭按钮风格一致
        // 点击触发 downloadCurrentImage()：Coil 同步加载 + InspirationScreenshot 保存到相册 + Snackbar 反馈
        IconButton(
            onClick = ::downloadCurrentImage,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = "保存到相册",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Snackbar 提示：底部居中，离底 80dp 避开下载按钮
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        )
    }
}

/**
 * 可缩放/平移的单张图片
 * - 双指捏合：缩放（1x~4x）
 * - 单指拖动：仅在缩放 > 1x 时平移
 * - 双击：放大/还原
 *
 * @param path 图片绝对路径
 */
@Composable
private fun ZoomableImage(path: String) {
    // 缩放比例（1f ~ 4f）
    var scale by remember { mutableFloatStateOf(1f) }
    // 平移偏移
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(path)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                // 双指捏合/拖动：缩放 + 缩放>1 时平移
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                }
                // 双击：放大/还原
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1f) {
                                // 当前已放大：还原
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                // 当前未放大：放大到 2x
                                scale = 2f
                            }
                        }
                    )
                }
                // 应用缩放与平移
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
    }
}
