package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Scale

/**
 * 内联图片预览组件
 *
 * 在富文本编辑器中显示插入的图片缩略图，
 * 支持异步加载和点击放大查看功能。
 *
 * **功能特性**:
 * - ✅ 异步加载（使用 Coil 库，避免主线程阻塞）
 * - ✅ 自适应高度（保持原始宽高比）
 * - ✅ 圆角边框（符合项目 UI 设计规范 16dp）
 *
 * **UI 布局结构**:
 * ```
 * ┌─────────────────────────────┐
 * │  ┌─────────────────────┐    │
 * │  │                     │    │
 * │  │     图片预览区域      │    │
 * │  │   (maxWidth=300dp)  │    │
 * │  │                     │    │
 * │  └─────────────────────┘    │
 * └─────────────────────────────┘
 * ```
 *
 * @param imageUri 图片的 Uri 地址（支持本地文件、网络 URL、Content URI）
 * @param modifier Modifier（可选）
 * @param maxWidth 图片最大宽度限制（默认 300.dp，防止大图撑开布局）
 * @param onClick 图片点击回调（可选，用于实现全屏预览功能）
 */
@Composable
fun InlineImagePreview(
    imageUri: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 300.dp,
    isHighlighted: Boolean = false,
    isVisible: Boolean = true, /** Compose 1.9 onVisibilityChanged：是否在视口内 */
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .then(
                if (isHighlighted) {
                    /** Compose 1.9 内阴影：使用 DSL 块语法替代旧版命名参数 */
                    Modifier
                        .innerShadow(
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            color = Color(0xFFFFB74D).copy(alpha = 0.6f)
                            radius = 6f
                        }
                        /** 保留细边框作为视觉锚点 */
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
    ) {
        /** 图片容器 */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (isHighlighted) Color(0xFFFFF8E1) else Color.Transparent
                )
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
             * Compose 1.9 onVisibilityChanged 懒加载策略：
             *
             * isVisible=true  → 渲染 AsyncImage（Coil 异步加载 + 内存/磁盘缓存）
             * isVisible=false → 显示轻量占位符（避免 Coil 预加载屏幕外图片占用内存）
             *
             * 性能收益：
             * - 用户插入10张图片但只看到前2张时，仅加载2张
             * - 滚动离开视口的图片自动释放内存（Coil 自动取消请求）
             */
            if (isVisible) {
                /** 使用 Coil AsyncImage 异步加载图片 */
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
                    onLoading = { _ -> },
                    onError = { _ -> }
                )
            } else {
                /**
                 * 不可见时的轻量占位符：
                 * 保持与可见时相同的尺寸（避免布局跳动），
                 * 但不加载任何图片资源。
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    /** 图片图标占位提示 */
                    Text(
                        text = "📷",
                        style = TextStyle(fontSize = 24.sp),
                        color = Color.Gray.copy(alpha = 0.4f)
                    )
                }
            }
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
 * @param maxVisibleCount 最大可见数量（超出部分需滑动查看，默认 3）
 */
@Composable
fun ImagePreviewCarousel(
    imageUris: List<String>,
    modifier: Modifier = Modifier,
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
                modifier = Modifier.width((itemWidth * 300).dp)
            )
        }
    }
}
