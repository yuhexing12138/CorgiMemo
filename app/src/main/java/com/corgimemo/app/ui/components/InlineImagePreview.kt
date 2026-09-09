package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale

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
 * **V2.8.3 根因补充**：
 * UI 层逻辑（widthIn + wrapContentHeight + ContentScale.Fit）已完全正确，
 * **但**前提是数据源（drawable.intrinsicWidth/intrinsicHeight）保持正确纵横比。
 * 如果上游 ImageUtils 把图片强制拉伸成正方形保存到 internal storage，
 * 这里再怎么 fit 都救不回来——画布本身已经 1:1。
 *
 * V2.8.3 配套修复：ImageUtils.compressAndSaveImage 改为按比例缩放，
 * 保留原始纵横比，UI 层才能正确显示。
 *
 * **历史变更**:
 * - V2.8 移除 `isVisible` 懒加载策略
 * - V2.8.1 改用 `SubcomposeAsyncImage`（仍依赖 `aspectRatio(ratio)` 预设）
 * - V2.8.2 方案 C：移除 `aspectRatio`，用 `wrapContentHeight()` 自适应
 * - V2.8.3 根因定位：上游 ImageUtils 拉伸为正方形是问题源
 *
 * **V2.9.0 块级撑满（v2026-09-09）**：
 * 灵感编辑页的图片已改为块级（Text/Image 交错块），用户要求"插入后图片宽度占满块宽"。
 * 新增 [fillMaxWidth] 开关：
 * - `true` → 宽度 = 父容器可用宽度（即块内容区，与文本块文字左右边界对齐），
 *   高度由 painter 真实比例换算（`Modifier.aspectRatio`），**严格等比、不裁切**；
 * - `false` → 旧行为不变（`widthIn(max = maxWidth)`，最大 300.dp）。
 *
 * @param imageUri 图片的 Uri 地址
 * @param modifier Modifier（可选）
 * @param maxWidth 图片最大宽度限制（默认 300.dp，仅在 [fillMaxWidth] = false 时生效）
 * @param fillMaxWidth true = 宽度占满父容器可用宽度（块级图片），false = 沿用最大宽度限制
 * @param isHighlighted 是否处于选中高亮态（内阴影 + 浅黄底）
 * @param onClick 图片点击回调（可选）
 */
@Composable
fun InlineImagePreview(
    imageUri: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 300.dp,
    fillMaxWidth: Boolean = false,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current

    /**
     * 宽度策略：撑满时用 [Modifier.fillMaxWidth]，否则沿用 [maxWidth] 上限。
     * 内/外层容器与图片共用同一策略，三者宽度始终一致。
     */
    val widthModifier: Modifier =
        if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = maxWidth)

    /**
     * 内边距：撑满态**只保留垂直留白**——水平留白改由调用方给出（如块级图片传
     * 16dp 的编辑器 contentPadding，才能与文本块文字左右缘对齐），内部若再叠加
     * 会让图片比文字窄一圈；非撑满态维持原 4dp 水平留白。
     */
    val paddingModifier: Modifier = if (fillMaxWidth) {
        Modifier.padding(vertical = 8.dp)
    } else {
        Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    }

    /** 外层容器：按宽度策略限宽，高度由子元素（Image）真实比例决定 */
    Box(
        modifier = modifier
            .then(widthModifier)
            .wrapContentHeight()
            .then(paddingModifier)
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
        /** 内层容器：圆角 + 背景 + 点击（宽度策略与外层一致） */
        Box(
            modifier = Modifier
                .then(widthModifier)
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
             * 宽度策略（v2026-09-09 分叉）：
             * - **撑满态（fillMaxWidth）**：宽度 = 块内容区，高度用 painter 真实比例
             *   `Modifier.aspectRatio` 换算 → 严格等比、不裁切（用户要求长图也不限制高度）；
             * - **旧行为**：`widthIn(max) + wrapContentHeight`，宽度由 drawable 决定、上限 300.dp。
             * 两种态都保留固定高度占位符，避免无 drawable 时高度为 0 造成布局抖动。
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
                    .then(widthModifier)
                    .wrapContentHeight(),
                loading = {
                    /** 加载中：占满宽度 + 固定 180dp 高度 + 相机占位符（避免布局抖动） */
                    Box(
                        modifier = Modifier
                            .then(widthModifier)
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
                     * 关键：直接用 state.painter + Image。
                     * - 撑满态：`fillMaxWidth + aspectRatio(真实比例)` → 宽 = 块宽、高 = 宽 / 比例，
                     *   ContentScale.Fit 与容器比例一致，等比铺满、无形变、无留白；
                     * - 旧行为：`widthIn(max) + wrapContentHeight` 按 drawable 真实尺寸渲染。
                     */
                    val ratio = painterAspectRatio(state.painter)
                    Image(
                        painter = state.painter,
                        contentDescription = "插入的图片",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .then(widthModifier)
                            .then(
                                when {
                                    !fillMaxWidth -> Modifier.wrapContentHeight()
                                    /** 比例可用 → 等比定高；不可用 → 退回固定占位高度 */
                                    ratio != null -> Modifier.aspectRatio(ratio)
                                    else -> Modifier.height(180.dp)
                                }
                            )
                    )
                },
                error = {
                    /** 加载失败：占满宽度 + 固定高度占位符 */
                    Box(
                        modifier = Modifier
                            .then(widthModifier)
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
 * 取 [Painter] 的真实宽高比（宽 / 高），用于撑满宽度时按 `Modifier.aspectRatio` 等比定高。
 *
 * **判空技巧**：未加载完成 / 尺寸未知时 [Painter.intrinsicSize] 返回 `Size.Unspecified`
 * （宽高为 NaN），而 NaN 与任何数比较恒为 false，故 `> 0f` 已能同时排除 NaN 与 0/负值，
 * 无需额外 `isNaN()` 判断（历史踩坑：NaN 参与比较极易写错判据）。
 *
 * @return 宽高比；比例不可用时返回 null，由调用方退回固定占位高度
 */
private fun painterAspectRatio(painter: Painter): Float? {
    val size = painter.intrinsicSize
    return if (size.width > 0f && size.height > 0f) size.width / size.height else null
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
