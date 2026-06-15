package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.ui.model.VoiceAttachment

/**
 * 可拖拽的语音附件组件（纯净悬浮版 v2）
 *
 * 与 DraggableImageAttachment v2 保持完全一致的架构：
 * - 外层容器与内容尺寸一致
 * - graphicsLayer 应用于最外层 Box（整体跟随移动）
 * - 零容器感设计
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DraggableVoiceAttachment(
    voiceAttachment: VoiceAttachment,
    lineIndex: Int,
    voiceIndex: Int,
    isDragging: Boolean = false,
    isDropTarget: Boolean = false,
    isPlaying: Boolean = false,
    onDragStart: (lineIndex: Int, voiceIndex: Int) -> Unit = { _, _ -> },
    onDragUpdate: (dragOffset: Offset, fingerX: Float, fingerY: Float) -> Unit = { _, _, _ -> },
    onDragEnd: (targetLineIndex: Int, targetVoiceIndex: Int?) -> Unit = { _, _ -> },
    onPauseRequest: () -> Unit = {},
    onResumeRequest: () -> Unit = {},
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current

    /** 拖拽偏移量 */
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    /**
     * 🎯 纯净悬浮缩放动画（与图片组件一致：1.08x）
     */
    val targetScale = if (isDragging) 1.08f else 1.0f
    val currentScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "voicePureFloatScale"
    )

    /**
     * ===== 核心渲染区域 =====
     *
     * 🆕 v2：graphicsLayer 应用于最外层 Box
     * 拖拽时整个语音条（含阴影）跟随手指移动
     */
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(
                when {
                    /** ★★★ 拖拽中：整个 Box 纯净悬浮 ★★★ */
                    isDragging -> Modifier
                        .shadow(
                            elevation = 6.dp,
                            shape = RectangleShape,
                            ambientColor = Color.Black.copy(alpha = 0.15f),
                            spotColor = Color.Black.copy(alpha = 0.08f)
                        )
                        .graphicsLayer {
                            scaleX = currentScale
                            scaleY = currentScale
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            /** 禁用裁剪，防止拖出边界后消失 */
                            this.clip = false
                        }
                        .padding(vertical = 4.dp)

                    /** 跨行目标位置：极简提示 */
                    isDropTarget -> Modifier
                        .background(Color(0xFFFF9A5C).copy(alpha = 0.06f))
                        .padding(vertical = 4.dp)

                    /** 正常状态 */
                    else -> Modifier
                }
            )
            /** 注册长按拖拽手势 */
            .pointerInput(lineIndex, voiceIndex) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        if (isPlaying) onPauseRequest()
                        onDragStart(lineIndex, voiceIndex)
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
                        /** 🆕 v7：传递手指绝对坐标（用于跨行 X 轴位置检测） */
                        onDragUpdate(dragOffset, change.position.x, change.position.y)
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
                        if (isPlaying) onResumeRequest()
                    }
                )
            }
    ) {
        if (isDragging) {
            /**
             * 拖拽中：纯净简化UI（仅图标+时长）
             */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "语音",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = formatDuration(voiceAttachment.duration ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            /**
             * 正常状态 / 目标占位符
             */
            Box(modifier = Modifier.fillMaxWidth()) {
                VoicePlayerComponent(
                    voicePlayer = null,
                    filePath = voiceAttachment.path,
                    totalDuration = voiceAttachment.duration,
                    onDelete = { onDelete() },
                    isHighlighted = isDropTarget,
                    modifier = Modifier.fillMaxWidth(),
                    isVisible = true
                )

                /** 跨行目标提示（极简） */
                if (isDropTarget) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "\uD83C\uDFA7",
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
 * 格式化语音时长（秒 → MM:SS 格式）
 */
private fun formatDuration(durationSeconds: Int?): String {
    if (durationSeconds == null || durationSeconds <= 0) return "0:00"

    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
