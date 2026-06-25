package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corgimemo.app.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 弹性缓动函数（对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * 使用 easeOutBack 数学模型实现回弹效果
 */
val ElasticOutEasing: Easing = Easing { fraction ->
    val c1 = 1.56f
    val c3 = c1 + 1f
    1f + c3 * Math.pow(fraction - 1.0, 3.0).toFloat() +
        c1 * Math.pow(fraction - 1.0, 2.0).toFloat()
}

/**
 * 左滑操作按钮配置
 *
 * @param label 按钮文字
 * @param backgroundColorRes 背景色资源 ID
 * @param icon Material 图标
 * @param zIndex z-index 值（从左到右递减：分享=3, 置顶=2, 删除=1）
 */
private data class SwipeButtonConfig(
    val label: String,
    val backgroundColorRes: Int,
    val icon: ImageVector,
    val zIndex: Float
)

/**
 * 可左滑展开操作区的容器组件（飞书风格级联重叠堆叠动效）
 *
 * 将待办卡片作为 content 传入，自动获得左滑操作能力。
 * 按钮顺序：分享 → 置顶 → 删除（从左到右）
 * 动画参数：duration=300ms, staggerRatio=0, thresholdRatio=0.20, ElasticOutEasing
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（父组件控制互斥）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onShareClick 分享按钮回调
 * @param onPinClick 置顶按钮回调
 * @param onDeleteClick 删除按钮回调
 * @param durationMs 动画时长（默认 300ms）
 * @param staggerRatio 级联延迟比例（默认 0.00，同步移动）
 * @param thresholdRatio 吸附比例（默认 0.20）
 * @param easing 缓动函数（默认弹性效果，对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    durationMs: Int = 300,
    staggerRatio: Float = 0.00f,
    thresholdRatio: Float = 0.20f,
    easing: Easing = ElasticOutEasing,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 几何参数
    val buttonWidthDp = 72.dp
    val actionsWidthDp = buttonWidthDp * 3 // 3 个按钮 = 216dp
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
    val thresholdPx = actionsWidthPx * thresholdRatio

    // 卡片位移状态（px，范围 -actionsWidthPx..0）
    val cardOffsetX = remember { Animatable(0f) }

    // 按钮配置（顺序固定：分享→置顶→删除）
    val buttons = remember {
        listOf(
            SwipeButtonConfig("分享", R.color.ui_swipe_share, Icons.Outlined.Share, 3f),
            SwipeButtonConfig("置顶", R.color.ui_primary, Icons.Outlined.PushPin, 2f),
            SwipeButtonConfig("删除", R.color.ui_swipe_delete, Icons.Outlined.Delete, 1f)
        )
    }

    // 父组件强制收起时同步动画
    LaunchedEffect(isExpanded, isEnabled) {
        if (!isExpanded && cardOffsetX.value < 0f && isEnabled) {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = easing)
            )
        }
    }

    // revealProgress 连续函数（与 Web 原型 1:1 对齐）
    val revealPx = (-cardOffsetX.value).coerceIn(0f, actionsWidthPx)
    val revealProgress = if (actionsWidthPx > 0f) revealPx / actionsWidthPx else 0f

    // 按钮点击后收起的公共逻辑
    val onButtonClicked: () -> Unit = {
        onExpandChange(false)
        coroutineScope.launch {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = easing)
            )
        }
    }

    // 双层叠加 Layout：内容层(z=10) + 操作层(z=1)
    Layout(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        content = {
            // === 内容层（measurables[0]）===
            Box(
                modifier = Modifier
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onDragEnd = {
                                // 阈值吸附
                                val currentReveal = -cardOffsetX.value
                                val target = if (currentReveal >= thresholdPx) {
                                    -actionsWidthPx
                                } else {
                                    0f
                                }
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(
                                        targetValue = target,
                                        animationSpec = tween(
                                            durationMillis = durationMs,
                                            easing = easing
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                val currentReveal = -cardOffsetX.value
                                val target = if (currentReveal >= thresholdPx) {
                                    -actionsWidthPx
                                } else {
                                    0f
                                }
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(
                                        targetValue = target,
                                        animationSpec = tween(
                                            durationMillis = durationMs,
                                            easing = easing
                                        )
                                    )
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
                    .offset { IntOffset(cardOffsetX.value.roundToInt(), 0) }
                    .zIndex(10f)
            ) {
                content()
            }

            // === 操作层（measurables[1]）===
            if (isEnabled) {
                Row(modifier = Modifier.fillMaxHeight()) {
                    buttons.forEachIndexed { index, btnConfig ->
                        // 级联算法：计算本地进度
                        val localStart = index * staggerRatio
                        val denom = 1f - localStart
                        val localProgress = if (denom > 0f) {
                            ((revealProgress - localStart) / denom).coerceIn(0f, 1f)
                        } else {
                            if (revealProgress >= localStart) 1f else 0f
                        }
                        // 偏移量：初始堆叠在 Delete 槽位 → 终态回到原始位置
                        val offset = (buttons.size - 1 - index) * buttonWidthPx
                        val translateX = offset * (1f - localProgress)
                        // opacity 二元化：无淡入淡出
                        val alpha = if (revealPx > 0f) 1f else 0f

                        // 点击回调
                        val clickAction: () -> Unit = when (btnConfig.label) {
                            "分享" -> {
                                { onShareClick(); onButtonClicked() }
                            }
                            "置顶" -> {
                                { onPinClick(); onButtonClicked() }
                            }
                            "删除" -> {
                                { onDeleteClick(); onButtonClicked() }
                            }
                            else -> ({})
                        }

                        SwipeActionButton(
                            config = btnConfig,
                            translateX = translateX,
                            alpha = alpha,
                            onClick = clickAction,
                            modifier = Modifier.zIndex(btnConfig.zIndex)
                        )
                    }
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            val contentPlaceable = measurables[0].measure(constraints)
            val cardWidth = contentPlaceable.width
            val cardHeight = contentPlaceable.height
            val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
                measurables[1].measure(
                    Constraints.fixed(
                        width = with(density) { (72.dp * 3).roundToPx() },
                        height = cardHeight
                    )
                )
            } else null

            layout(cardWidth, cardHeight) {
                contentPlaceable.placeRelative(0, 0)
                actionsPlaceable?.placeRelative(
                    x = cardWidth - actionsPlaceable.width,
                    y = 0
                )
            }
        }
    )
}

/**
 * 单个左滑操作按钮（图标在上，文字在下，纵向居中）
 *
 * @param config 按钮配置
 * @param translateX 横向偏移量（级联堆叠动画）
 * @param alpha 透明度（二元化）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SwipeActionButton(
    config: SwipeButtonConfig,
    translateX: Float,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = colorResource(id = config.backgroundColorRes)

    Box(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .graphicsLayer {
                this.translationX = translateX
                this.alpha = alpha
            }
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = config.label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.label,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
