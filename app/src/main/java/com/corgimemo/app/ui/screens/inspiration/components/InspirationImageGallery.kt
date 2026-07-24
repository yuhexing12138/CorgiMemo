// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt
package com.corgimemo.app.ui.screens.inspiration.components

import android.graphics.BitmapFactory
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.util.InspirationScreenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 灵感图片全屏预览
 *
 * 使用独立 Dialog Window 渲染，覆盖全屏（含 MainScreen 的 AppBar/BottomBar/柯基悬浮球）。
 * 通过 DialogWindowProvider 将 Dialog Window 强制设为 MATCH_PARENT，并对 Dialog Window
 * 应用粘性沉浸式（隐藏状态栏与导航栏），确保与系统 UI 不重叠。
 *
 * 深色背景（黑色），使用 HorizontalPager 支持多图滑动翻页
 * 双指捏合缩放（1x~4x），双击放大/还原，点击右上角 X 按钮关闭
 *
 * v2026-07-24 新增：
 * - 左下角删除按钮（Material Icons Outlined.Delete）
 * - 点击删除弹出二次确认 AlertDialog（标题/说明/取消/红色删除按钮）
 * - onDeleteClick 为 null 时仅 Snackbar 提示「演示功能」，便于后续接入实际删除逻辑
 *
 * @param imagePaths 图片绝对路径列表
 * @param initialIndex 初始显示的图片索引
 * @param onDeleteClick 删除按钮点击回调（传入当前图片索引，可选）。
 *        为 null 时删除按钮仍显示，确认后只显示 Snackbar 提示，便于后续接入。
 * @param onDismiss 关闭回调
 */
@Composable
fun InspirationImageGallery(
    imagePaths: List<String>,
    initialIndex: Int,
    onDeleteClick: ((Int) -> Unit)? = null,
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

    /**
     * 二次确认对话框显示状态
     * 点击左下角删除按钮时置 true，用户点击「取消」或「删除」后置 false
     * 用于实现"删除图片？"的二次确认交互，避免误触
     */
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 下载当前显示的图片到相册：使用 BitmapFactory.decodeFile 直接解码（比 Coil 更直接，无压缩），再复用 InspirationScreenshot 保存到系统相册
    // IO 线程执行避免主线程阻塞，Snackbar 反馈结果
    fun downloadCurrentImage() {
        val path = imagePaths.getOrNull(pagerState.currentPage) ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap == null) {
                        Log.w("InspirationImageGallery", "图片不存在或无法解码: $path")
                        return@withContext false
                    }
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

    // 独立 Dialog Window 渲染：覆盖全屏（Activity 内的所有内容均不可见）
    // 关键：通过 DialogWindowProvider 强制设置 Window 尺寸为 MATCH_PARENT（Dialog 默认 wrap_content），
    // 并对 Dialog Window 应用粘性沉浸式（不是 Activity Window），避免与系统状态栏/导航栏重叠
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // 关键：LocalView.current 在 Dialog content 中就是 DialogLayout 自身，
        // DialogLayout extends AbstractComposeView implements DialogWindowProvider，
        // 所以 view 自己就是 DialogWindowProvider；view.parent 是 Dialog 的 content FrameLayout，
        // 不是 DialogWindowProvider。
        val view = LocalView.current
        val dialogWindow = remember(view) {
            (view as? DialogWindowProvider)?.window
        }
        DisposableEffect(dialogWindow) {
            val window = dialogWindow
            if (window == null) {
                onDispose { }
            } else {
                // 强制 Dialog Window 尺寸为 MATCH_PARENT x MATCH_PARENT
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // 让 Dialog 内容延伸到系统栏后面
                WindowCompat.setDecorFitsSystemWindows(window, false)
                // 对 Dialog Window 应用粘性沉浸式
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                onDispose {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
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

            // 标题（左上角）："图片附件"
            // v2026-07-24 新增：参考 VoicePreviewDialog 左上角"录音预览"标题，
            // 为图片预览左上角添加"图片附件"标题，相对位置保持一致。
            // top=24dp 使标题垂直中心与右上角关闭按钮（padding 16dp + size 40dp，中心 36dp）对齐：
            // 标题行高约 24dp（18sp * 1.33），中心 = 24 + 12 = 36dp
            Text(
                text = "图片附件",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 24.dp)
            )

            // 关闭按钮（右上角）：圆形 40x40 + 半透明黑色背景 + 白色图标
            // v2026-07-24 调整：与下载/删除按钮视觉统一，三个按钮共享圆形半透明黑背景样式
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
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

            // 下载按钮（右下角）：圆形 40x40 + 半透明黑色背景 + 白色图标
            // v2026-07-24 调整：与左下角删除按钮视觉对称，统一圆形半透明黑背景 + 24dp 图标
            // 点击触发 downloadCurrentImage()：Coil 同步加载 + InspirationScreenshot 保存到相册 + Snackbar 反馈
            IconButton(
                onClick = ::downloadCurrentImage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = "保存到相册",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // v2026-07-24 新增：删除按钮（左下角）
            // 与原型 .img-btn.delete 一致：bottom:40px; left:16px;
            // 圆形 40x40 + 半透明黑色背景（alpha 0.5）+ 红色 Delete 图标
            // 图标色 0xFFFF6B6B 与 AlertDialog 删除按钮文字色呼应，强化视觉警示
            // 与右下角下载按钮对称布局，点击触发二次确认对话框（不直接删除，防误触）
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除图片",
                    tint = Color(0xFFFF6B6B),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Snackbar 提示：底部居中，离底 80dp 避开下载按钮
            // 在 Dialog Window 内显示，z 轴层级高于 Activity 内的柯基悬浮球
            // 使用 AppSnackbarHost 统一全项目 Snackbar 样式（柯基图标 + 文字 + 按钮）
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )

            // v2026-07-24 新增：删除图片二次确认对话框
            // 视觉样式与 VoicePreviewDialog 中的删除确认弹窗保持一致：
            // - title 使用 FontWeight.SemiBold
            // - 删除按钮文字颜色 0xFFFF6B6B（与录音弹窗一致，非原型 #DC2626）
            // - 取消按钮使用默认主题色，不自定义颜色
            // - 不自定义 containerColor/titleContentColor/textContentColor，跟随 Material3 主题
            // 文案风格对齐：「删除图片」+「确定要删除这张图片吗？删除后不可恢复。」
            if (showDeleteConfirm) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = {
                        Text(
                            text = "删除图片",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Text("确定要删除这张图片吗？删除后不可恢复。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                val currentIndex = pagerState.currentPage
                                if (onDeleteClick != null) {
                                    onDeleteClick(currentIndex)
                                } else {
                                    // 演示模式：未接入实际删除逻辑时仅 Snackbar 提示
                                    scope.launch {
                                        snackbarHostState.showSnackbar("演示功能：实际删除逻辑待接入")
                                    }
                                }
                            }
                        ) {
                            Text("删除", color = Color(0xFFFF6B6B))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showDeleteConfirm = false }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
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
                // 关键：pointerInput 依赖 scale，scale 变化时重启
                // scale = 1f（未放大）时不消费指针，让 HorizontalPager 接收单指 pan 用于翻页
                // scale > 1f（已放大）时消费指针处理平移，让用户能拖动查看图片细节
                .pointerInput(scale) {
                    if (scale > 1f) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                    }
                }
                // 双击：放大/还原（独立 pointerInput，不影响翻页手势）
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
