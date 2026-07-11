package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Scale

/**
 * 内联图片预览组件
 *
 * 在富文本编辑器中显示插入的图片缩略图。
 *
 * **V2.8.2 方案 C 重构**：
 * - 移除 `aspectRatio` 修饰符
 * - 使用 `widthIn(max = maxWidth)` 限制最大宽度
 * - 使用 `wrapContentHeight()` 让图片高度由 drawable 真实尺寸决定
 * - 配合 `ContentScale.Fit` 自动保持原图比例
 * - 不再依赖 `imageAspectRatio` 状态，彻底避免"过方容器导致挤压"的视觉感
 *
 * **历史变更**:
 * - V2.8 移除 `isVisible` 懒加载策略
 * - V2.8.1 改用 `SubcomposeAsyncImage`（仍依赖 `aspectRatio(ratio)` 预设）
 * - V2.8.2 方案 C：移除 `aspectRatio`，用 `wrapContentHeight()` 自适应，
 *   解决图片被"压扁"在固定比例容器中的视觉问题
 *
 * @param imageUri 图片的 Uri 地址
 * @param modifier Modifier（可选）
 * @param maxWidth 图片最大宽度限制（默认 300.dp）
 * @param onClick 图片点击回调（可选）
 */
@Composable
fun InlineImagePreview(
    imageUri: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 300.dp,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    /** 外层容器：限制最大宽度，让子元素（Image）按 drawable 真实比例渲染 */
    Box(
        modifier = modifier
            .widthIn(max = maxWidth)
            .wrapContentHeight()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .then(
                if (isHighlighted) {
                    /** 高亮时显示内阴影 + 浅黄底色 */
                    Modifier
                        .innerShadow(shape = RoundedCornerShape(16.dp)) {
                            color = Color(0xFFFFB74D).copy(alpha = 0.6f)
                            radius = 6f
                        }
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                } else {
                    Modifier
                }
            )
    ) {
        /** 内层容器：圆角 + 背景 + 点击 */
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .wrapContentHeight()
                .clip(RoundedCornerShape(16.dp))
                .background(if (isHighlighted) Color(0xFFFFF8E1) else Color.Transparent)
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
             * 方案 C 核心：移除 aspectRatio，使用 wrapContentHeight + ContentScale.Fit
             * - 加载中/失败：显示固定高度占位符（避免 wrapContentHeight 在无 drawable 时高度=0）
             * - 加载成功：直接用 state.painter 渲染，wrapContentHeight 让 Image 高度
             *             = drawable.intrinsicHeight × (实际宽度 / drawable.intrinsicWidth)
             * - ContentScale.Fit 保证图片不变形
             */
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUri)
                    .crossfade(true)
                    .scale(Scale.FIT)
                    .build(),
                contentDescription = "插入的图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .wrapContentHeight(),
                loading = {
                    /** 加载中：固定 180dp 高度 + 相机占位符（避免布局抖动） */
                    Box(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .height(180.dp)
                            .background(Color(0xFFEEEEEE), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📷",
                            style = TextStyle(fontSize = 24.sp),
                            color = Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                },
                success = { state ->
                    /**
                     * 关键：直接用 state.painter + Image
                     * - wrapContentHeight() 会让 Image 高度 = drawable 真实高度
                     * - widthIn(max = maxWidth) 限制最大宽度
                     * - ContentScale.Fit 让图片按比例缩放
                     * - 三者结合：图片完美按原比例显示，无任何预设 aspectRatio
                     */
                    Image(
                        painter = state.painter,
                        contentDescription = "插入的图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .wrapContentHeight()
                    )
                },
                error = {
                    /** 加载失败：固定高度占位符 */
                    Box(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .height(180.dp)
                            .background(Color(0xFFEEEEEE), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📷",
                            style = TextStyle(fontSize = 24.sp),
                            color = Color.Gray.copy(alpha = 0.4f)
                        )
                    }
                }
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
