package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType

/**
 * 可拖拽的图片附件组件（纯净悬浮版 v2）
 *
 * 设计理念：
 * - **外层 Box 与图片尺寸完全一致**（无多余空间）
 * - **拖拽时整个 Box 同步移动**（不是只浮动内部内容）
 * - **零容器感**：无边界框、无背景色、仅柔和阴影
 *
 * 架构说明：
 * ```
 * ┌─────────────────────────────────┐
 * │  Box (外层容器)                  │ ← graphicsLayer 整体偏移 ✅
 * │  ┌─────────────────────────────┐│
 * │  │  InlineImagePreview (图片)  ││ ← 紧贴 Box 边缘 ✅
 * │  │  [删除按钮]                 ││
 * │  └─────────────────────────────┘│
 * └─────────────────────────────────┘
 * ```
 *
 * 关键改进（v1 → v2）：
 * - 去掉硬编码 size(100,80)，改由内容决定尺寸
 * - graphicsLayer 应用于最外层 Box，拖拽时整体移动
 * - InlineImagePreview 使用 fillMaxSize 适配 Box
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableImageAttachment(
    imagePath: String,
    lineIndex: Int,
    imageIndex: Int,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    onDragStart: (lineIndex: Int, imageIndex: Int) -> Unit = { _, _ -> },
    onDragUpdate: (dragOffset: Offset, fingerY: Float) -> Unit = { _, _ -> },
    onDragEnd: (targetLineIndex: Int, targetImageIndex: Int?) -> Unit = { _, _ -> },
    onClick: (String) -> Unit = {},
    onDelete: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    /** 拖拽偏移量 */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /** 长按检测阶段标记 */
    var isDetecting by remember { mutableStateOf(false) }

    /**
     * 图片附件统一显示尺寸
     *
     * 此尺寸同时作用于：
     * 1. 外层 Box 容器（确保布局稳定）
     * 2. 行内排序的单位宽度计算（CrossLineDragManager.INLINE_ITEM_WIDTH_DP）
     *
     * 宽度 100dp + 间距 8dp = CrossLineDragManager 中使用的 ~108dp 单位宽度
     */
    val attachmentWidth = 100.dp
    val attachmentHeight = 80.dp

    /**
     * 🎯 纯净悬浮缩放动画
     */
    val targetScale = if (isDragging) 1.08f else 1.0f
    val currentScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "pureFloatScale"
    )

    /**
     * ===== 核心渲染区域 =====
     *
     * 🆕 v2 关键改进：graphicsLayer 应用于最外层 Box
     *
     * 之前的问题：
     *   graphicsLayer 在内部 Box 上 → 只有内容浮起，外层容器不动
     *   → 视觉上像"内容从容器里飘出来"（容器感）
     *
     * 现在的设计：
     *   graphicsLayer 在最外层 Box 上 → 整个 Box（含阴影、圆角）一起移动
     *   → 视觉上像"拿起整张卡片"（自然拖拽感）
     */
    Box(
        modifier = Modifier
            /** 🆕 固定尺寸：与图片内容完全匹配 */
            .size(width = attachmentWidth, height = attachmentHeight)

            /** 🆕 统一基础样式（三种状态共享）*/
            .clip(RoundedCornerShape(4.dp))

            /** 状态相关的样式差异 */
            .then(
                when {
                    /**
                     * ★★★ 拖拽中：整个 Box 纯净悬浮 ★★★
                     *
                     * shadow + graphicsLayer 都作用在外层 Box 上，
                     * 拖拽时 Box 整体跟随手指移动。
                     */
                    isDragging -> Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RectangleShape,
                            ambientColor = Color.Black.copy(alpha = 0.15f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .graphicsLayer {
                            /** 缩放：以 Box 中心为原点放大 */
                            scaleX = currentScale
                            scaleY = currentScale
                            /** 偏移：整个 Box 跟随手指 */
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            /** 禁用裁剪：允许拖出父容器边界 */
                            this.clip = false
                        }

                    /**
                     * 跨行目标位置：极简提示
                     */
                    isDropTarget -> Modifier
                        .background(Color(0xFFFF9A5C).copy(alpha = 0.06f))

                    /**
                     * 正常状态：无特殊修饰（仅有上面的 clip）
                     */
                    else -> Modifier
                }
            )

            /** 注册长按拖拽手势检测 */
            .pointerInput(lineIndex, imageIndex) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        isDetecting = false
                        onDragStart(lineIndex, imageIndex)
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
                        onDragUpdate(dragOffset, dragOffset.y)
                    },
                    onDragEnd = {
                        dragOffset = Offset.Zero
                        HapticFeedbackManager.performHapticFeedback(
                            context = context,
                            type = InteractionType.CONFIRM,
                            enabled = true
                        )
                        onDragEnd(-1, null)
                    },
                    onDragCancel = {
                        dragOffset = Offset.Zero
                        isDetecting = false
                    }
                )
            }
    ) {
        if (isDragging) {
            /**
             * 拖拽中：纯净图片（无附加UI）
             *
             * fillMaxSize 确保 InlineImagePreview 填满外层 Box，
             * 实现"Box 与图片尺寸完全一致"的要求。
             */
            InlineImagePreview(
                imageUri = imagePath,
                modifier = Modifier.fillMaxSize(),
                isHighlighted = true,
                isVisible = true
            )
        } else {
            /**
             * 正常状态 / 目标占位符
             *
             * 包含：图片（紧贴 Box）+ 删除按钮 + 可选目标提示
             */
            Box(modifier = Modifier.fillMaxSize()) {
                /** 图片预览：fillMaxSize 紧贴外层 Box */
                InlineImagePreview(
                    imageUri = imagePath,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onClick(imagePath) },
                    isHighlighted = isDropTarget,
                    isVisible = true
                )

                /** 右上角删除按钮 */
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

                /** 跨行目标提示（极简） */
                if (isDropTarget) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\uD83D\uDCC5",
                            color = Color(0xFFFF9A5C).copy(alpha = 0.6f),
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图片附件占位符组件
 *
 * 当图片被拖拽离开原位置时，
 * 在原位置显示此占位符以保持布局稳定。
 *
 * 尺寸与 DraggableImageAttachment 保持一致（100×80dp）。
 */
@Composable
fun ImagePlaceholder(
    width: Int = 100,
    height: Int = 80
) {
    Box(
        modifier = Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFF3F4F6).copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        /** 空白占位——视觉上几乎不可见 */
    }
}
