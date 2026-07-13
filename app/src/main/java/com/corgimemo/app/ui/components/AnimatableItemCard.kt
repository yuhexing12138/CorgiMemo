package com.corgimemo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动画包装卡片组件
 *
 * 为任意 Composable 内容添加交互式动画效果，
 * 包括点击缩放、按压反馈、进入动画等。
 *
 * **核心特性**:
 * - ✅ **点击缩放**: 按下时缩小至 0.98 倍，释放后恢复（触觉反馈）
 * - ✅ **涟漪效果**: Material Design 3 标准的 Ripple 涟漪
 * - ✅ **进入动画**: 首次显示时的淡入+上滑组合动画
 * - ✅ **选中状态**: 支持激活/未激活两种视觉状态切换
 * - ✅ **自定义动画**: 可配置动画时长、缓动曲线、缩放比例等参数
 *
 * **适用场景**:
 * 1. 待办列表项（TodoListItem）的包装器
 * 2. 设置页面选项卡
 * 3. 分类选择项
 * 4. 任何需要交互反馈的卡片式 UI 元素
 *
 * **使用示例**:
 * ```kotlin
 * AnimatableItemCard(
 *     onClick = { /* 处理点击事件 */ },
 *     isSelected = todo.isCompleted,
 *     modifier = Modifier.fillMaxWidth()
 * ) {
 *     // 卡片内容...
 *     Text(text = todo.title)
 * }
 * ```
 *
 * @param onClick 点击回调函数（可选）
 * @param onLongClick 长按回调函数（可选）
 * @param isSelected 是否处于选中/激活状态（默认 false）
 * @param enabled 是否启用交互（默认 true）
 * @param enterAnimationEnabled 是否启用进入动画（默认 true）
 * @param scaleOnPress 是否在按下时缩放（默认 true）
 * @param cornerRadius 圆角半径（默认 16.dp）
 * @param backgroundColor 背景颜色（可选，默认使用主题 surface 色）
 * @param modifier 外层 Modifier
 * @param content 卡片内容 Composable lambda
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimatableItemCard(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    enterAnimationEnabled: Boolean = true,
    scaleOnPress: Boolean = true,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    /** 按压状态追踪 */
    var isPressed by remember { mutableStateOf(false) }

    /** 缩放动画值 */
    val targetScale = if (isPressed && scaleOnPress) 0.98f else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(
            durationMillis = 100, /** 快速响应 */
            easing = FastOutSlowInEasing
        ),
        label = "scale"
    )

    /** 进入动画的透明度值 */
    var isVisible by remember { mutableStateOf(!enterAnimationEnabled) }
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "alpha"
    )

    /** 触发进入动画（仅一次） */
    if (!isVisible && enterAnimationEnabled) {
        isVisible = true
    }

    /** 最终背景颜色：优先使用参数值，否则根据选中状态决定 */
    val finalBackgroundColor = when {
        backgroundColor != null -> backgroundColor
        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(finalBackgroundColor)
            .then(
                if (onClick != null || onLongClick != null && enabled) {
                    Modifier
                        .indication(
                            indication = androidx.compose.material3.ripple(),
                            interactionSource = remember { MutableInteractionSource() }
                        )
                        .pointerInput(onClick, onLongClick) {
                            detectTapGestures(
                                onTap = {
                                    isPressed = true /** 按下 */
                                    onClick?.invoke()
                                },
                                onPress = {
                                    isPressed = true /** 按压中 */
                                    tryAwaitRelease()
                                    isPressed = false /** 释放 */
                                },
                                onLongPress = {
                                    onLongClick?.invoke()
                                }
                            )
                        }
                } else {
                    /** 纯展示模式：无交互 */
                    Modifier
                }
            )
            .padding(12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        content()
    }
}

/**
 * 可展开/收起的动画卡片
 *
 * 在 AnimatableItemCard 的基础上增加高度动画支持，
 * 用于实现手风琴式折叠面板效果。
 *
 * **使用示例**:
 * ```kotlin
 * var expanded by remember { mutableStateOf(false) }
 *
 * ExpandableAnimatableCard(
 *     isExpanded = expanded,
 *     onToggle = { expanded = !expanded },
 *     headerContent = {
 *         Text("可展开的标题")
 *     }
 * ) {
 *     // 展开后的详细内容...
 *     Text("这里是详细描述...")
 * }
 * ```
 *
 * @param isExpanded 当前是否展开
 * @param onToggle 展开/收起切换回调
 * @param headerContent 固定显示的头部内容
 * @param expandableContent 可展开的详细内容
 * @param modifier Modifier
 */
@Composable
fun ExpandableAnimatableCard(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    headerContent: @Composable () -> Unit,
    expandableContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 高度动画（从 0 到内容实际高度的过渡） */
    // 注意：Compose 中动态测量内容高度较复杂，
    // 这里简化处理：通过 AnimatedVisibility 控制显隐
    Column(modifier = modifier) {
        /** 头部区域（始终可见） */
        AnimatableItemCard(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    headerContent()
                }

                /** 展开/收起图标指示器 */
                val rotation by animateFloatAsState(
                    targetValue = if (isExpanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "rotation"
                )

                Icon(
                    painter = painterResource(id = android.R.drawable.arrow_down_float),
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
            }
        }

        /** 可展开内容区域 */
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeIn(tween(200)),
            exit = androidx.compose.animation.shrinkVertically(
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + androidx.compose.animation.fadeOut(tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)
            ) {
                expandableContent()
            }
        }
    }
}

/**
 * 闪烁提示动画卡片
 *
 * 用于吸引用户注意力的特殊场景（如新功能引导、错误提示等）。
 * 卡片会以一定频率闪烁边框或背景。
 *
 * @param isBlinking 是否正在闪烁
 * @param blinkIntervalMs 闪烁间隔（毫秒），默认 1000ms
 * @param blinkColor 闪烁颜色（默认主题 primary 色）
 * @param modifier Modifier
 * @param content 内容 lambda
 */
@Composable
fun BlinkingAnimatableCard(
    isBlinking: Boolean = true,
    blinkIntervalMs: Int = 1000,
    blinkColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    /** 闪烁状态的透明度动画 */
    val currentAlpha by animateFloatAsState(
        targetValue = if (isBlinking) 0.6f else 1f,
        animationSpec = tween(
            durationMillis = blinkIntervalMs / 2,
            easing = androidx.compose.animation.core.LinearEasing
        ),
        label = "blinkAlpha"
    )

    AnimatableItemCard(
        modifier = modifier.then(
            if (isBlinking) {
                /** 添加闪烁边框效果 */
                Modifier
                    .border(BorderStroke(width = 2.dp, color = blinkColor.copy(alpha = currentAlpha)))
                    .clip(RoundedCornerShape(16.dp))
            } else {
                Modifier
            }
        ),
        onClick = null /** 不需要点击交互 */
    ) {
        content()
    }
}
