package com.corgimemo.app.ui.components

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import kotlin.math.roundToInt

/**
 * 可拖拽的图片附件组件（v5 - 多图定位修复版）
 *
 * 设计理念：
 * - **Box 动态匹配图片尺寸**（宽度固定 100dp，高度按图片宽高比自适应）
 * - **拖拽时使用 Popup 浮层**（独立窗口，不受父容器裁剪，始终浮于最上方）
 * - **阴影跟随移动**（阴影在 Popup 上，自然跟随手指）
 * - **预读宽高比**（BitmapFactory 预读，避免首次渲染跳变）
 * - **长按缩放反馈**（长按检测阶段轻微缩放，提示用户即将进入拖拽）
 * - **Popup 淡入淡出**（拖拽开始/结束的过渡动画）
 *
 * 架构说明：
 * ```
 * 正常状态:
 * ┌──────────────────────────┐
 * │  Box (width=100dp,       │
 * │       height=自适应)     │
 * │  ┌────────────────────┐  │
 * │  │  AsyncImage (图片) │  │
 * │  │  [x 删除按钮]      │  │
 * │  └────────────────────┘  │
 * └──────────────────────────┘
 *
 * 长按检测中:
 * ┌──────────────────────────┐
 * │  Box (scale=0.95,       │  ← 轻微缩小，提示"即将拖拽"
 * │       alpha=0.8)        │
 * └──────────────────────────┘
 *
 * 拖拽状态:
 * ┌──────────────────────────┐
 * │  Box (半透明占位, a=0.3) │  ← 保持布局稳定
 * └──────────────────────────┘
 *
 *       ┌──────────────────────┐
 *       │  Popup (独立浮层窗口) │  ← 跟随手指移动
 *       │  ┌──────────────────┐│
 *       │  │  AsyncImage+阴影 ││  ← 淡入/淡出动画
 *       │  └──────────────────┘│
 *       └──────────────────────┘
 * ```
 *
 * v5 关键改进（v4 → v5）：
 * - 修复多图场景下 Popup 位置累积偏移问题（通过 parentLayoutCoordinates 差值计算）
 * - 图片中心对齐手指位置（减去 componentSize / 2 居中修正）
 *
 * v4 关键改进（v3 → v4）：
 * - BitmapFactory 预读宽高比，首次渲染即正确，无跳变
 * - 长按检测阶段轻微缩放反馈（scale 0.95）
 * - Popup 浮层淡入淡出动画（alpha 0→1 / 1→0）
 * - ImagePlaceholder 支持动态宽高比
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableImageAttachment(
    imagePath: String,
    lineIndex: Int,
    imageIndex: Int,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    /** 🆕 是否在该图片之前显示移动光标（同行移动模式） */
    showCursorBefore: Boolean = false,
    /** 🆕 是否在该图片之后显示移动光标（同行移动模式，仅最后一张图使用） */
    showCursorAfter: Boolean = false,
    /** 拖拽开始回调（行索引, 图片索引, 图片高度px）*/
    onDragStart: (lineIndex: Int, imageIndex: Int, imageHeightPx: Float) -> Unit = { _, _, _ -> },
    onDragUpdate: (dragOffset: Offset, fingerX: Float, fingerY: Float) -> Unit = { _, _, _ -> },
    onDragEnd: (targetLineIndex: Int, targetImageIndex: Int?) -> Unit = { _, _ -> },
    onClick: (String) -> Unit = {},
    onDelete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    /** 拖拽偏移量（相对于拖拽起始位置） */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /** 组件尺寸（用于 Popup 浮层大小匹配和居中计算） */
    var componentSize by remember { mutableStateOf(IntSize.Zero) }

    /**
     * 组件在父容器中的偏移量（像素）
     *
     * 多张图片在 Row 中排列时，每张图片距离 Row 左上角有不同的偏移。
     * Popup 的锚点是父容器（Row），所以 offset 必须包含此值，
     * 否则所有图片的 Popup 都会从同一位置开始，导致越往后偏离越大。
     *
     * 计算方式：componentScreenPosition - parentScreenPosition
     */
    var componentOffsetInParent by remember { mutableStateOf(Offset.Zero) }

    /**
     * 长按触发时，手指相对于组件左上角的偏移量
     *
     * 用于让 Popup 图片的对应位置对齐到手指。
     */
    var initialTouchOffset by remember { mutableStateOf(Offset.Zero) }

    /**
     * V2.8.4 改造：移除 imageAspectRatio 状态与 BitmapFactory 预读逻辑
     *
     * **原实现问题**：
     * - 默认纵横比为 4/3，首次渲染时容器被强制为 100dp × 75dp（4:3）
     * - BitmapFactory 预读是异步 IO 操作，期间图片已用 4:3 渲染
     * - 真实比例加载完成后容器从 75dp 跳到 56.25dp（16:9 示例），造成视觉跳变
     * - 对横向图片（16:9 / 4:3）影响最大，纵向图片（9:16）影响小
     *
     * **新实现**：
     * - 改用 `widthIn(max = attachmentWidth).wrapContentHeight()` + ContentScale.Fit
     * - 高度由 Coil 加载后的 drawable intrinsic 尺寸决定
     * - 无需预读纵横比，无默认 4/3 跳变
     * - 与 InspirationEditScreen 的 InlineImagePreview（方案 C）保持一致
     *
     * 加载过程：
     * 1. 首次渲染：容器宽度 = attachmentWidth，高度 = 0（wrapContentHeight 等待内容）
     * 2. Coil 加载完成：drawable intrinsic 决定高度（保持纵横比）
     * 3. 容器高度 = (attachmentWidth / drawable.intrinsicWidth) × drawable.intrinsicHeight
     */

    /**
     * 图片附件显示尺寸（方形缩略图）
     *
     * v2026-07-25 改造：从原 100dp 宽 + 自适应高度（4:3）改为 80dp × 80dp 方形缩略图
     * - 尺寸缩小：单行可容纳更多图片（与 MAX_IMAGES_PER_LINE = 3 配合）
     * - 方形：视觉统一，避免不同比例图片导致高度参差不齐
     * - ContentScale.Crop：填充整个容器，保留视觉焦点
     *
     * 此尺寸同时作用于：
     * 1. 外层 Box 容器（size = attachmentSize × attachmentSize）
     * 2. 行内排序的边缘检测计算（CrossLineDragManager.ATTACHMENT_WIDTH_DP）
     */
    val attachmentSize = 80.dp

    /**
     * 优化3：长按检测阶段的缩放反馈
     *
     * 长按检测中：scale = 0.95（轻微缩小，提示"即将拖拽"）
     * 拖拽中：scale = 1.08（放大，表示已进入拖拽）
     * 正常：scale = 1.0
     */
    val targetScale = when {
        isDragging -> 1.08f
        else -> 1.0f
    }
    val currentScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "dragScale"
    )

    /**
     * 优化2：Popup 浮层淡入淡出动画
     *
     * 拖拽开始时 alpha 从 0 渐变到 1（淡入），
     * 拖拽结束时 alpha 从 1 渐变到 0（淡出）。
     */
    val popupAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "popupAlpha"
    )

    /**
     * Popup 的实际显示/隐藏状态（独立于 isDragging）
     *
     * 问题：如果用 `if (isDragging)` 控制 Popup 生命周期，
     * 当 isDragging 变为 false 时 Popup 立即被销毁，淡出动画无法播放。
     *
     * 解决方案：
     * - isDragging=true 时 → showPopup=true（立即显示，配合淡入动画）
     * - isDragging=false 时 → 先让 popupAlpha 播放淡出动画，
     *   等动画结束后再设置 showPopup=false（真正销毁 Popup）
     */
    var showPopup by remember { mutableStateOf(false) }

    /** 拖拽开始时立即显示 Popup（配合淡入动画） */
    LaunchedEffect(isDragging) {
        if (isDragging) {
            showPopup = true
        }
    }

    /**
     * 监听淡出动画完成，延迟销毁 Popup
     *
     * 当 popupAlpha 降到 0（淡出完成）且不在拖拽中时，才真正销毁 Popup。
     */
    LaunchedEffect(popupAlpha) {
        if (!isDragging && popupAlpha < 0.01f) {
            showPopup = false
        }
    }

    /**
     * 拖拽结束后延迟重置偏移量
     *
     * 避免 Popup 在消失前瞬间回弹到原位：
     * - onDragEnd 时只通知外部，不重置 dragOffset
     * - 等 isDragging 变为 false（Popup 已消失）后再重置
     */
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            dragOffset = Offset.Zero
        }
    }

    /**
     * ===== 主容器 =====
     *
     * 拖拽时：半透明占位（保持布局稳定，尺寸不变）
     * 正常时：图片 + 删除按钮 + 可选目标提示
     */
    Box(
        modifier = Modifier
            /**
             * v2026-07-25 改造：从 widthIn + wrapContentHeight 改为固定方形尺寸
             *
             * 原方案（V2.8.4）：宽度 100dp + 高度按图片宽高比自适应
             * - 问题：不同比例图片高度参差不齐，视觉不齐
             * - 加载时高度从 0 跳到真实值，有视觉跳变
             *
             * 新方案：80dp × 80dp 方形缩略图
             * - 尺寸固定，无视觉跳变
             * - ContentScale.Crop 填充整个容器，保留视觉焦点
             * - 圆角 8dp（与卡片圆角风格一致）
             */
            .size(attachmentSize)
            .clip(RoundedCornerShape(8.dp))
            .onGloballyPositioned { coordinates ->
                /** 记录组件尺寸 */
                componentSize = coordinates.size
                /**
                 * 计算组件在父容器中的偏移量（关键修复）
                 *
                 * 原理：
                 * - localToScreen(Offset.Zero) → 组件左上角在屏幕中的绝对坐标
                 * - parentCoordinates?.localToScreen(Offset.Zero) → 父容器左上角的屏幕坐标
                 * - 两者之差 → 组件相对于父容器的像素偏移
                 *
                 * 此值用于 Popup 定位，确保多图场景下每张图片的 Popup
                 * 都从正确的位置开始，不会累积偏移。
                 */
                val componentScreenPos = coordinates.localToScreen(Offset.Zero)
                val parentScreenPos = coordinates.parentLayoutCoordinates
                    ?.localToScreen(Offset.Zero) ?: Offset.Zero
                componentOffsetInParent = Offset(
                    x = componentScreenPos.x - parentScreenPos.x,
                    y = componentScreenPos.y - parentScreenPos.y
                )
            }
            .then(
                when {
                    /** 拖拽中：半透明占位，保持布局稳定 */
                    isDragging -> Modifier.graphicsLayer { alpha = 0.3f }
                    /** 被交换目标：虚线边框 + 微缩，与源图片的半透明形成差异 */
                    isDropTarget -> Modifier
                        .graphicsLayer { scaleX = 0.92f; scaleY = 0.92f }
                        .drawWithContent {
                            drawContent()
                            /** 绘制虚线边框 */
                            drawRoundRect(
                                color = Color(0xFFFF9A5C),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        intervals = floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                                    )
                                ),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    /** 正常状态 */
                    else -> Modifier
                }
            )
            /** 注册长按拖拽手势检测 */
            .pointerInput(lineIndex, imageIndex) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        /**
                         * 记录长按触发时手指相对于组件左上角的偏移
                         *
                         * offset 是 detectDragGesturesAfterLongPress 提供的，
                         * 表示触摸点相对于组件左上角的像素坐标。
                         * Popup 用此值让图片对应位置对齐到手指。
                         */
                        initialTouchOffset = offset
                        /** 重置拖拽累积偏移（新一轮拖拽开始） */
                        dragOffset = Offset.Zero
                        /** 传递组件实际高度（像素） */
                        onDragStart(lineIndex, imageIndex, componentSize.height.toFloat())
                        HapticFeedbackManager.performHapticFeedback(
                            context = context,
                            type = InteractionType.TEXT_MOVE,
                            enabled = true
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset = Offset(
                            x = dragOffset.x + dragAmount.x,
                            y = dragOffset.y + dragAmount.y
                        )
                        /** 传递拖拽偏移量和手指绝对坐标 */
                        onDragUpdate(dragOffset, change.position.x, change.position.y)
                    },
                    onDragEnd = {
                        HapticFeedbackManager.performHapticFeedback(
                            context = context,
                            type = InteractionType.CONFIRM,
                            enabled = true
                        )
                        onDragEnd(-1, null)
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                    }
                )
            }
    ) {
        /** 图片：始终渲染（拖拽时半透明，正常时完整显示）
         * v2026-07-25 改造：Scale.FIT → Scale.FILL + ContentScale.Crop
         * 配合方形容器实现方形缩略图效果（填充整个容器，裁剪超出部分）
         */
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imagePath)
                .crossfade(true)
                .scale(Scale.FILL)
                .build(),
            contentDescription = "图片附件",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isDragging) Modifier.clickable { onClick(imagePath) } else Modifier)
        )

        /** 删除按钮（仅正常状态显示） */
        if (!isDragging) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onDelete(imagePath) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u00D7",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        /** 🆕 同行移动光标指示器 */
        if (showCursorBefore || showCursorAfter) {
            CursorIndicator(
                isBefore = showCursorBefore,
                modifier = Modifier.matchParentSize()
            )
        }
    }

    /**
     * ===== 拖拽浮层（Popup） =====
     *
     * 使用 Popup 实现真正的"浮于最上方"效果：
     * - Popup 是独立窗口，不受父容器裁剪
     * - 阴影、缩放、位移全部在 Popup 内同步
     * - 位置 = 父容器内偏移 + 手指触摸偏移 + 拖拽偏移 - 居中修正
     * - focusable=false 确保 Popup 不拦截触摸事件
     * - 淡入淡出动画（popupAlpha）
     */
    if (showPopup && componentSize != IntSize.Zero) {
        /**
         * 居中修正偏移：让图片中心对齐手指位置
         *
         * 默认 Popup 左上角对齐手指，减去尺寸的一半后，
         * 图片中心点就会精确对齐到手指位置。
         */
        val centerOffsetX = componentSize.width / 2f
        val centerOffsetY = componentSize.height / 2f

        Popup(
            alignment = Alignment.TopStart,
            /**
             * Popup 定位公式（v5 修复版）：
             *
             * offset = componentOffsetInParent + initialTouchOffset + dragOffset - centerOffset
             *
             * │─ componentOffsetInParent ─│ 组件在父容器（Row）中的位置偏移
             *                                → 解决多图场景下越往后偏离越大的问题
             *
             * │─── initialTouchOffset ────│ 长按触发时手指在组件内的触摸位置
             *                                → 让图片对应区域对齐手指按下点
             *
             * │─────── dragOffset ────────│ 拖拽过程中手指的移动量
             *                                → 实时跟随手指
             *
             * │───── centerOffset ────────│ 尺寸的一半（居中修正）
             *                                → 让图片中心而非左上角对齐手指
             */
            offset = IntOffset(
                x = (componentOffsetInParent.x + initialTouchOffset.x + dragOffset.x - centerOffsetX).roundToInt(),
                y = (componentOffsetInParent.y + initialTouchOffset.y + dragOffset.y - centerOffsetY).roundToInt()
            ),
            properties = PopupProperties(focusable = false)
        ) {
            Box(
                modifier = Modifier
                    .size(
                        width = with(density) { componentSize.width.toDp() },
                        height = with(density) { componentSize.height.toDp() }
                    )
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(8.dp),
                        ambientColor = Color.Black.copy(alpha = 0.15f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer {
                        scaleX = currentScale
                        scaleY = currentScale
                        /** 淡入淡出动画 */
                        alpha = popupAlpha
                    }
            ) {
                // v2026-07-25 与主图保持一致：Scale.FILL + ContentScale.Crop
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imagePath)
                        .scale(Scale.FILL)
                        .build(),
                    contentDescription = "拖拽中的图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 同行移动光标指示器组件
 *
 * 在同行移动模式下，当手指位于两张图片之间时显示闪烁光标，
 * 告知用户图片将被插入到该位置。
 *
 * 样式：
 * - 宽度：2dp
 * - 颜色：橙色
 * - 高度：与图片同高
 * - 动画：alpha 在 0.3~1.0 之间循环闪烁，周期约 1 秒（530ms 亮 + 530ms 暗）
 *
 * @param isBefore true=显示在左侧（当前图之前），false=显示在右侧（当前图之后）
 */
@Composable
private fun CursorIndicator(
    isBefore: Boolean,
    modifier: Modifier = Modifier
) {
    /**
     * 文字光标风格闪烁动画：alpha 在 0.3 ~ 1.0 之间循环
     *
     * 使用 Animatable + LaunchedEffect 实现循环效果
     * （兼容 Compose 1.9.2，无需 animateFloat / infiniteTransition）
     */
    val blinkAlpha = remember { androidx.compose.animation.core.Animatable(1.0f) }

    LaunchedEffect(Unit) {
        while (true) {
            /** 渐暗：1.0 → 0.3 */
            blinkAlpha.animateTo(
                targetValue = 0.3f,
                animationSpec = tween(durationMillis = 530, easing = EaseInOut)
            )
            /** 渐亮：0.3 → 1.0 */
            blinkAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 530, easing = EaseInOut)
            )
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = if (isBefore) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        /** 橙色光标竖线 */
        Canvas(modifier = Modifier.width(2.dp).fillMaxHeight()) {
            drawRoundRect(
                color = Color(0xFFFF9A5C).copy(alpha = blinkAlpha.value),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}

/**
 * 图片附件占位符组件
 *
 * 当图片被拖拽离开原位置时，
 * 在原位置显示此占位符以保持布局稳定。
 *
 * v2026-07-25 改造：与 DraggableImageAttachment 保持一致的方形尺寸
 * - 原方案：width × (width*3/4) 矩形（4:3 比例）
 * - 新方案：size × size 方形（与主图 attachmentSize 一致）
 *
 * @param imagePath 图片路径（已不再使用，保留参数兼容旧调用）
 * @param size 占位符边长（dp，默认 80，与 DraggableImageAttachment.attachmentSize 一致）
 */
@Composable
fun ImagePlaceholder(
    imagePath: String = "",
    size: Int = 80
) {
    /**
     * v2026-07-25 改造：方形占位符
     *
     * 原方案：width × (width*3/4) 矩形
     * 新方案：size × size 方形（与主图保持一致，避免拖拽时布局跳变）
     */
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6).copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        /** 空白占位——视觉上几乎不可见 */
    }
}
