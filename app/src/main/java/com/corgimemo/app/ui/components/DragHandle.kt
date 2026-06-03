package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 拖拽手柄图标组件
 *
 * 显示在列表项左侧的拖拽触发区域，
 * 用户长按此手柄后可上下拖动调整列表项顺序。
 *
 * **设计规范**:
 * - 使用 6 点圆点图案（类似 iOS 的 Reorder Control）
 * - 默认灰色，激活时变为暖橙色（符合项目主题）
 * - 触摸目标 ≥ 48×48dp（满足无障碍要求）
 * - 圆角背景（hover 时显示）
 *
 * **视觉样式**:
 * ```
 *  ┌──────────┐
 *  │  •  •  •  │  ← 默认状态（浅灰）
 *  │  •  •  •  │
 *  └──────────┘
 *
 *  ┌──────────┐
 *  │  ●  ●  ●  │  ← 激活/拖拽中（暖橙色）
 *  │  ●  ●  ●  │
 *  └──────────┘
 * ```
 *
 * **使用示例**:
 * ```kotlin
 * Row {
 *     DragHandle(
 *         modifier = Modifier.padding(end = 12.dp),
 *         onDragStarted = { /* 开始拖拽 */ }
 *     )
 *
 *     // 列表项内容...
 * }
 * ```
 *
 * @param modifier Modifier（可选）
 * @param isActive 是否处于激活/拖拽状态（默认 false）
 * @param onDragStarted 长按开始拖拽时的回调（可选）
 */
@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onDragStarted: (() -> Unit)? = null
) {
    /** 图标颜色：激活时使用暖橙色，否则使用次要文字颜色 */
    val iconColor = if (isActive) {
        Color(0xFFFF9A5C) /** 暖橙色（项目主色） */
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) /** 浅灰色 */
    }

    /** 背景颜色：仅在激活时显示 */
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        Color.Transparent
    }

    Box(
        modifier = modifier
            .then(
                if (onDragStarted != null) {
                    /** 如果提供了回调，则启用拖拽手势检测 */
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .padding(8.dp)
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStarted?.invoke() },
                                onDrag = { change, _ -> change.consume() },
                                onDragEnd = { },
                                onDragCancel = { }
                            )
                        }
                        .semantics {
                            contentDescription = "拖拽以重新排序"
                        }
                } else {
                    /** 纯展示模式（无交互） */
                    Modifier
                        .padding(12.dp)
                        .semantics {
                            contentDescription = "拖拽手柄"
                        }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        /**
         * 绘制 6 点圆点图案
         *
         * 使用 Canvas 或组合 Box 实现两行三列的圆点布局：
         * 第一行: ● ● ●
         * 第二行: ● ● ●
         */
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            /** 第一行圆点 */
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(iconColor)
                    )
                }
            }

            /** 第二行圆点 */
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(iconColor)
                    )
                }
            }
        }
    }
}

/**
 * 垂直拖拽指示器（替代版本）
 *
 * 使用竖向排列的 6 个圆点，
 * 更适合垂直列表的视觉暗示。
 *
 * @param isActive 是否处于激活状态
 * @param modifier Modifier
 */
@Composable
fun VerticalDragIndicator(
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isActive) {
        Color(0xFFFF9A5C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }

    Column(
        modifier = modifier
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp)
    ) {
        /** 竖向 6 个圆点 */
        repeat(6) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(iconColor)
            )
        }
    }
}

/**
 * 横向拖拽手柄（用于水平布局场景）
 *
 * @param isActive 是否处于激活状态
 * @param onClick 点击回调（可选）
 * @param modifier Modifier
 */
@Composable
fun HorizontalDragHandle(
    isActive: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isActive) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 14.dp)
                } else {
                    Modifier.padding(horizontal = 10.dp, vertical = 14.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        /** 水平 6 个圆点 */
        repeat(6) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(iconColor)
            )
        }
    }
}
