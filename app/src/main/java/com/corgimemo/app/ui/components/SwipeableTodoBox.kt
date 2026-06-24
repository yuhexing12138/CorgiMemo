package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch

/**
 * 可左滑展开操作区的容器组件（飞书风格）
 *
 * 用法：将待办卡片作为 content 传入，自动获得左滑操作能力。
 * 采用自定义 Layout 测量顺序：先测 Card 拿高度，再测 Actions 锁定尺寸。
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（由父组件控制，用于"互斥展开"语义）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onPinClick 点击置顶按钮回调
 * @param onShareClick 点击分享按钮回调
 * @param onDeleteClick 点击删除按钮回调
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onPinClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    /** 动作区总宽 = 3 × 72dp = 216dp */
    val actionsWidthDp = 216.dp

    /** 单按钮宽 72dp（用于阈值判断：拖动 ≥ 72dp 触发完全展开） */
    val buttonWidthDp = 72.dp

    /** 卡片水平位移 Animatable：范围 -actionsWidthPx..0f（px） */
    val cardOffsetX = remember { Animatable(0f) }

    /** 动作区宽度的 px 值，用于 coerce 卡片位移 */
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }

    /**
     * 监听外部 isExpanded 变化：
     * - 外部要求展开但当前未完全展开 → 动画到 -actionsWidthPx
     * - 外部要求收起但当前处于展开 → 动画到 0
     * 实现"互斥展开"控制
     */
    LaunchedEffect(isExpanded) {
        if (isExpanded && cardOffsetX.value > -actionsWidthPx) {
            cardOffsetX.animateTo(
                -actionsWidthPx,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else if (!isExpanded && cardOffsetX.value < 0f) {
            cardOffsetX.animateTo(0f, spring())
        }
    }

    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // ⚠️ 严格顺序：先 Card 后 Actions，确保 measurables[0] = Card, measurables[1] = Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = Dp(cardOffsetX.value))
                    .pointerInput(isEnabled, isExpanded) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                // 拖动结束：根据最终位移判定"完全展开 / 收回"
                                val final = cardOffsetX.value
                                val triggerThresholdPx = with(density) { buttonWidthDp.toPx() }
                                val target = when {
                                    // 左滑 ≥ 单按钮宽（72dp） → 完全展开
                                    final <= -triggerThresholdPx -> -actionsWidthPx
                                    // 右滑 > 30px → 收回
                                    final >= 30f -> 0f
                                    // 处于展开状态且轻微抖动 → 保持展开
                                    isExpanded -> -actionsWidthPx
                                    // 其他 → 收回
                                    else -> 0f
                                }
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(
                                        target,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                // 拖动被取消（如被父布局拦截）：与 onDragEnd 行为一致
                                val final = cardOffsetX.value
                                val triggerThresholdPx = with(density) { buttonWidthDp.toPx() }
                                val target = if (final <= -triggerThresholdPx) -actionsWidthPx else 0f
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(target, spring())
                                }
                            }
                        ) { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (cardOffsetX.value + dragAmount)
                                    .coerceIn(-actionsWidthPx, 0f)
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
                    }
            ) {
                content()
            }

            // ActionsLayer：3 按钮堆叠操作区（飞书风格）
            if (isEnabled) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topEnd = 20.dp,
                                bottomEnd = 20.dp
                            )
                        )
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 按钮 1：置顶（暖阳橙 #FF9A5C）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.VerticalAlignTop,
                            label = "置顶",
                            backgroundColor = Color(0xFFFF9A5C),
                            alpha = 0f,  // Task 5: 由 revealProgress 驱动
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onPinClick()
                            }
                        )

                        // 按钮 2：分享（柔和蓝 #90CAF9）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.IosShare,
                            label = "分享",
                            backgroundColor = Color(0xFF90CAF9),
                            alpha = 0f,
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onShareClick()
                            }
                        )

                        // 按钮 3：删除（柔和红 #FF8A80）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            backgroundColor = Color(0xFFFF8A80),
                            alpha = 0f,
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            // 1) 测量卡片（占据全部父约束）
            val cardPlaceable = measurables[0].measure(constraints)
            val cardWidth = cardPlaceable.width
            val cardHeight = cardPlaceable.height

            // 2) 测量动作区（宽度 = actionsWidthDp, 高度 = cardHeight, 锁定尺寸）
            val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
                measurables[1].measure(
                    Constraints.fixed(
                        width = with(density) { actionsWidthDp.roundToPx() },
                        height = cardHeight
                    )
                )
            } else null

            // 3) 整体布局尺寸 = 卡片尺寸（动作区紧贴右侧）
            layout(cardWidth, cardHeight) {
                cardPlaceable.placeRelative(0, 0)
                actionsPlaceable?.placeRelative(
                    x = cardWidth - actionsPlaceable.width,
                    y = 0
                )
            }
        }
    )
}

/**
 * 3 按钮左滑操作按钮（飞书风格）
 *
 * alpha/scaleX/translateX 均应用于外层 Box（不是内部 Column），
 * 实现"按钮整体渐入"而非仅图标文字渐入。
 *
 * @param icon 图标
 * @param label 文字标签
 * @param backgroundColor 按钮背景色
 * @param alpha 整体透明度（0f..1f），由外层 Animatable 驱动
 * @param scaleX 水平缩放（0.7f..1.0f），形成"展开"层次感
 * @param translateX 水平位移（px，正值=向右偏移），形成"滑入"效果
 * @param onClick 点击回调
 * @param modifier 外部修饰符（用于 weight/align）
 */
@Composable
private fun SwipeActionButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    alpha: Float,
    scaleX: Float,
    translateX: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scaleX
                this.translationX = translateX
            }
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            // 固定 4dp 间距（在 alpha=0 时不影响视觉）
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
