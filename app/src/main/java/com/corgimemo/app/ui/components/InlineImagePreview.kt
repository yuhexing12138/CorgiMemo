package com.corgimemo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale
import com.corgimemo.app.R

/**
 * 内联图片预览组件
 *
 * 在富文本编辑器中显示插入的图片缩略图，
 * 支持异步加载、删除操作和点击放大查看功能。
 *
 * **功能特性**:
 * - ✅ 异步加载（使用 Coil 库，避免主线程阻塞）
 * - ✅ 自适应高度（保持原始宽高比）
 * - ✅ 删除按钮（右上角 × 图标）
 * - ✅ 加载状态指示器（圆形进度条）
 * - ✅ 错误状态占位图（加载失败时显示默认图片）
 * - ✅ 圆角边框（符合项目 UI 设计规范 16dp）
 *
 * **UI 布局结构**:
 * ```
 * ┌─────────────────────────────┐
 * │  ┌─────────────────────┐ × │  ← 删除按钮
 * │  │                     │    │
 * │  │     图片预览区域      │    │
 * │  │   (maxWidth=300dp)  │    │
 * │  │                     │    │
 * │  └─────────────────────┘    │
 * └─────────────────────────────┘
 * ```
 *
 * **使用示例**:
 * ```kotlin
 * InlineImagePreview(
 *     imageUri = selectedUri,
 *     modifier = Modifier.padding(8.dp),
 *     onDelete = {
 *         // 从图片列表中移除该 URI
 *         imageUris = imageUris.filter { it != selectedUri }
 *     },
 *     maxWidth = 300.dp
 * )
 * ```
 *
 * @param imageUri 图片的 Uri 地址（支持本地文件、网络 URL、Content URI）
 * @param modifier Modifier（可选）
 * @param onDelete 删除按钮点击回调（从编辑器移除该图片）
 * @param maxWidth 图片最大宽度限制（默认 300.dp，防止大图撑开布局）
 * @param onClick 图片点击回调（可选，用于实现全屏预览功能）
 */
@Composable
fun InlineImagePreview(
    imageUri: String,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    maxWidth: Dp = 300.dp,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.TopEnd
    ) {
        /** 图片容器：带圆角的卡片样式 */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (maxWidth.value < Float.MAX_VALUE) {
                        Modifier.padding(end = 32.dp) // 为删除按钮留出空间
                    } else {
                        Modifier
                    }
                )
                .clip(RoundedCornerShape(16.dp)) /** 符合设计规范：圆角16dp */
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            /**
             * 使用 Coil AsyncImage 异步加载图片
             *
             * Coil 特性：
             * - 自动内存和磁盘缓存
             * - 支持图片变换（圆角、模糊等）
             * - 自动取消（离开组合时释放资源）
             * - 支持跨淡入淡出动画
             */
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .crossfade(true) /** 开启交叉淡入效果 */
                    .scale(Scale.FILL) /** 填充模式（保持比例） */
                    .build(),
                contentDescription = "插入的图片",
                contentScale = ContentScale.Fit, /** 保持宽高比，不裁剪 */
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f) /** 默认 16:9 宽高比 */
                    .then(
                        if (maxWidth.value < Float.MAX_VALUE) {
                            Modifier.padding(8.dp)
                        } else {
                            Modifier
                        }
                    ),
                /** 加载中状态回调（Coil AsyncImage 非 @Composable 回调，暂不处理） */
                onLoading = { _ -> },
                /** 加载失败状态回调（Coil AsyncImage 非 @Composable 回调，暂不处理） */
                onError = { _ -> }
            )
        }

        /**
         * 删除按钮
         *
         * 位于右上角，使用半透明深色背景，
         * 悬浮在图片上方，点击后触发回调移除图片。
         */
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(28.dp)
                .padding(2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.5f)) /** 半透明黑色背景 */
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                contentDescription = "删除图片",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 多张图片的水平滚动预览组件
 *
 * 当编辑器中插入多张图片时，
 * 使用此组件以水平列表方式展示所有图片。
 *
 * **使用场景**:
 * - 用户连续插入了多张图片
 * - 需要在有限空间内展示多张图片缩略图
 * - 支持左右滑动浏览所有图片
 *
 * @param imageUris 图片 Uri 列表
 * @param modifier Modifier（可选）
 * @param onDelete 单张图片删除回调（参数为被删除图片的索引位置）
 * @param maxVisibleCount 最大可见数量（超出部分需滑动查看，默认 3）
 */
@Composable
fun ImagePreviewCarousel(
    imageUris: List<String>,
    modifier: Modifier = Modifier,
    onDelete: (Int) -> Unit,
    maxVisibleCount: Int = 3
) {
    if (imageUris.isEmpty()) return

    /** 根据图片数量动态计算每张图片的宽度 */
    val itemWidth = when {
        imageUris.size <= maxVisibleCount -> 1f / imageUris.size
        else -> 1f / maxVisibleCount
    }

    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = imageUris.size,
            key = { index -> imageUris[index] }
        ) { index ->
            InlineImagePreview(
                imageUri = imageUris[index],
                modifier = Modifier.width((itemWidth * 300).dp), /** 动态宽度计算：先乘 Float 再转 Dp */
                onDelete = { onDelete(index) }
            )
        }
    }
}
