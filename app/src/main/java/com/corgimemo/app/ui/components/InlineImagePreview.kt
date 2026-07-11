package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
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
 * - ✅ 自适应高度（成功加载后按 drawable 真实宽高比渲染）
 * - ✅ 圆角边框（符合项目 UI 设计规范 16dp）
 * - ✅ 加载中/失败占位符（避免布局跳动）
 *
 * **历史变更**:
 * - V2.8 移除 `isVisible` 懒加载策略：编辑器场景下图片数量少（通常 1-3 张），
 *   上下滚动时图片被识别为不可见会切换为占位符，造成"图片消失"问题。
 * - V2.8.1 改用 `SubcomposeAsyncImage`：原方案使用 `imageAspectRatio` 状态 + `BitmapFactory`
 *   预读 + Coil `onSuccess` 更新比例的机制，在预读失败或 Coil 加载异常时，
 *   容器宽高比停留在默认值 4/3 = 1.333，导致实际为 1.5:1 的横图被显示在
 *   "过方"容器中产生"被挤压"视觉感。改为 `SubcomposeAsyncImage` 后，
 *   加载成功才渲染真实图片，比例直接来自 drawable.intrinsicWidth/Height。
 *
 * @param imageUri 图片的 Uri 地址（支持本地文件、网络 URL、Content URI）
 * @param modifier Modifier（可选）
 * @param maxWidth 图片最大宽度限制（默认 300.dp，预留参数，当前未使用宽度硬限制）
 * @param onClick 图片点击回调（可选，用于实现全屏预览功能）
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
             * V2.8.1 改用 SubcomposeAsyncImage：
             * - loading/error slot → 显示带相机图标的占位符（轻量、无图）
             * - success slot → 从 drawable.intrinsicWidth/Height 计算真实宽高比，
             *                 按 aspectRatio 渲染，**完全避免默认值误用**
             * - heightIn(max = 400.dp) → 加载中占位符的最大高度（避免过高）
             *
             * **为什么不用 BitmapFactory 预读**：
             *   预读是"推测"，与 Coil 实际加载结果可能不一致（EXIF 旋转、缓存命中、
             *   inSampleSize 计算误差），反而造成布局抖动。直接信任 Coil 的最终结果
             *   更稳健。
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
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                loading = {
                    /**
                     * 使用 matchParentSize() 跟随 SubcomposeAsyncImage 容器尺寸
                     * （而非 fillMaxSize()，后者在 subcompose slot 内行为不一致）
                     */
                    Box(
                        modifier = Modifier
                            .matchParentSize()
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
                success = { state: AsyncImagePainter.State.Success ->
                    /**
                     * 从 drawable 真实尺寸计算宽高比
                     * - drawable.intrinsicWidth/Height 是加载完成后的实际像素尺寸
                     * - 已包含 EXIF 旋转后的方向（Coil 内部处理 EXIF 后输出 drawable）
                     * - 使用 remember(result) 缓存比例，state 变化才重新计算
                     */
                    val ratio = remember(state.result) {
                        val drawable = state.result.drawable
                        if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                            drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()
                        } else {
                            4f / 3f /** drawable 未报告尺寸时兜底 */
                        }
                    }
                    /**
                     * 直接使用 state.painter 渲染（不再二次请求 Coil）
                     * - state.painter 是 SubcomposeAsyncImage 已经加载完成的 Painter
                     * - 配合 aspectRatio(ratio) 锁定容器宽高比
                     * - ContentScale.Fit 保证图片不变形
                     */
                    Image(
                        painter = state.painter,
                        contentDescription = "插入的图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio)
                    )
                },
                error = {
                    /** 加载失败占位符（与 loading 复用 matchParentSize 模式） */
                    Box(
                        modifier = Modifier
                            .matchParentSize()
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
