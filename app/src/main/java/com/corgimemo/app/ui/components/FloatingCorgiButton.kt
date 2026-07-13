package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 悬浮柯基按钮组件
 *
 * 可拖动的悬浮按钮，显示柯基头像帧动画。
 * 支持呼吸动画、点击缩放、长按拖动、快速滑动识别、贴边吸附、位置记忆、空闲动画、情绪变化和完成庆祝。
 *
 * @param onClick 点击回调（进入柯基详情页）
 * @param onPositionChanged 位置变化回调（保存到 DataStore）
 * @param onSwipeLeft 快速左滑回调（快速添加待办）
 * @param onSwipeRight 快速右滑回调（进入柯基详情页）
 * @param initialPosition 初始位置（从 DataStore 恢复，百分比坐标）
 * @param triggerCelebration 触发庆祝动画的信号（值变化时触发）
 * @param currentMood 当前柯基情绪（用于情绪变化时切换表情）
 * @param modifier 修饰符
 */
@Composable
fun FloatingCorgiButton(
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    initialPosition: Pair<Float, Float>? = null,
    triggerCelebration: Long = 0,
    currentMood: CorgiMood = CorgiMood.NORMAL,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // 屏幕尺寸（dp）
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // 按钮尺寸
    val buttonSize = 48.dp

    // 安全区域边距（dp）
    val topSafeMargin = 56.dp
    val bottomSafeMargin = 88.dp
    val horizontalMargin = 16.dp

    // 默认位置（dp）：左下角，避免与FAB重叠
    val defaultPaddingStart = horizontalMargin
    val defaultPaddingBottom = bottomSafeMargin

    // 当前位置（dp单位，用于 padding）
    var paddingStartDp by remember { mutableStateOf(defaultPaddingStart) }
    var paddingBottomDp by remember { mutableStateOf(defaultPaddingBottom) }

    // 是否已初始化位置
    var isPositionInitialized by remember { mutableStateOf(false) }

    // 拖动状态
    var isDragging by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }
    var longPressStartTime by remember { mutableLongStateOf(0L) }

    // 当前动画类型（默认站立，空闲时切换）
    var currentAnimationType by remember { mutableStateOf(AnimationType.STAND) }

    // 呼吸动画：使用 Animatable 在 0.95~1.05 之间缓慢振荡
    val breathingScale = remember { Animatable(1.0f) }

    // 点击缩放动画
    val clickScale = remember { Animatable(1.0f) }

    // 拖动结束回弹动画
    val bounceScale = remember { Animatable(1.0f) }

    // 计算最终缩放值：呼吸 * 点击 * 回弹 * 拖动
    val finalScale = breathingScale.value * clickScale.value * bounceScale.value *
            if (isDragging) 1.2f else 1.0f

    // 呼吸动画循环
    LaunchedEffect(Unit) {
        while (true) {
            breathingScale.animateTo(
                targetValue = 1.05f,
                animationSpec = tween(durationMillis = 1000)
            )
            breathingScale.animateTo(
                targetValue = 0.95f,
                animationSpec = tween(durationMillis = 1000)
            )
        }
    }

    // 初始化位置（从 DataStore 恢复或使用默认值）
    LaunchedEffect(initialPosition) {
        if (!isPositionInitialized) {
            if (initialPosition != null) {
                // 将百分比坐标转换为 dp
                val xPercent = initialPosition.first
                val yPercent = initialPosition.second
                val maxX = screenWidthDp - buttonSize - horizontalMargin
                val maxY = screenHeightDp - buttonSize - bottomSafeMargin
                val minY = topSafeMargin

                paddingStartDp = (xPercent * screenWidthDp.value).dp.coerceIn(horizontalMargin, maxX)
                paddingBottomDp = ((1f - yPercent) * screenHeightDp.value).dp.coerceIn(bottomSafeMargin, maxY)
            } else {
                paddingStartDp = defaultPaddingStart
                paddingBottomDp = defaultPaddingBottom
            }
            isPositionInitialized = true
        }
    }

    // 空闲动画：每30秒随机播放（30%概率）
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            if (!isDragging && Random.nextFloat() < 0.3f) {
                val idleAnimations = listOf(AnimationType.WINK, AnimationType.TILT, AnimationType.WAG)
                val selectedAnimation = idleAnimations.random()
                currentAnimationType = selectedAnimation
                delay(500L)
                currentAnimationType = AnimationType.STAND
            }
        }
    }

    // 情绪变化时切换表情
    LaunchedEffect(currentMood) {
        val moodAnimation = when (currentMood) {
            CorgiMood.EXCITED -> AnimationType.WAG
            CorgiMood.HAPPY -> AnimationType.STAND
            CorgiMood.NORMAL -> AnimationType.STAND
            CorgiMood.EXPECTING -> AnimationType.TILT
            CorgiMood.WORRIED -> AnimationType.WORRY
            CorgiMood.SLEEPY -> AnimationType.SLEEP
            CorgiMood.SAD -> AnimationType.SAD
        }
        currentAnimationType = moodAnimation
    }

    /**
     * 执行点击缩放动画（1.0→1.2→1.0，300ms）
     */
    suspend fun playClickAnimation() {
        clickScale.animateTo(
            targetValue = 1.2f,
            animationSpec = tween(durationMillis = 150)
        )
        clickScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 150)
        )
    }

    /**
     * 执行拖动结束回弹动画（1.0→0.9→1.0，200ms）
     */
    suspend fun playBounceAnimation() {
        bounceScale.animateTo(
            targetValue = 0.9f,
            animationSpec = tween(durationMillis = 100)
        )
        bounceScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 100)
        )
    }

    // 贴边吸附逻辑
    fun snapToEdge() {
        val halfScreenWidth = screenWidthDp / 2
        val maxX = screenWidthDp - buttonSize - horizontalMargin

        // 根据当前位置判断吸附到左边还是右边
        val targetStart = if (paddingStartDp < halfScreenWidth) {
            horizontalMargin  // 吸附到左边缘
        } else {
            maxX  // 吸附到右边缘
        }

        coroutineScope.launch {
            playBounceAnimation()
            paddingStartDp = targetStart

            // 保存位置（百分比坐标）
            val percentX = targetStart.value / screenWidthDp.value
            val percentY = 1f - (paddingBottomDp.value / screenHeightDp.value)
            onPositionChanged(percentX, percentY)
        }
    }

    // 边界限制（dp单位）
    fun clampPadding(start: Float, bottom: Float): Pair<Float, Float> {
        val maxStart = screenWidthDp.value - buttonSize.value - horizontalMargin.value
        val maxBottom = screenHeightDp.value - buttonSize.value - bottomSafeMargin.value
        val minBottom = bottomSafeMargin.value
        return Pair(
            start.coerceIn(horizontalMargin.value, maxStart),
            bottom.coerceIn(minBottom, maxBottom)
        )
    }

    // 使用 fillMaxSize 的 Box 作为容器，确保正确的布局和命中测试
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomStart
    ) {
        Surface(
            modifier = Modifier
                .padding(start = paddingStartDp, bottom = paddingBottomDp)
                .size(buttonSize)
                .zIndex(10f)
                .shadow(
                    elevation = if (isDragging) 16.dp else 8.dp,
                    shape = CircleShape
                )
                .scale(finalScale)
                .pointerInput(Unit) {
                    // 快速滑动阈值：触摸时长 < 200ms 且水平位移 > 50px
                    val SWIPE_TIME_THRESHOLD = 200L
                    val SWIPE_DISTANCE_THRESHOLD = 50f

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val change = down.changes.firstOrNull() ?: continue

                            longPressStartTime = System.currentTimeMillis()
                            isLongPressed = false

                            // 记录上一帧位置（用于计算位移增量）
                            var prevX = change.position.x
                            var prevY = change.position.y

                            // 等待长按判定或手指移动
                            var isLongPressTriggered = false
                            var totalDragX = 0f
                            var totalDragY = 0f

                            while (true) {
                                val event = awaitPointerEvent()
                                val currentChange = event.changes.firstOrNull() ?: break

                                if (currentChange.isConsumed) break

                                // 手动计算位置变化量（Compose 1.7+ 移除了 positionChange()）
                                val currentX = currentChange.position.x
                                val currentY = currentChange.position.y
                                val deltaX = currentX - prevX
                                val deltaY = currentY - prevY
                                prevX = currentX
                                prevY = currentY

                                if (!isLongPressed) {
                                    totalDragX += deltaX
                                    totalDragY += deltaY

                                    // 长按超过 500ms 且未大幅移动
                                    val elapsed = System.currentTimeMillis() - longPressStartTime
                                    if (elapsed > 500L && kotlin.math.abs(totalDragX) < 10f && kotlin.math.abs(totalDragY) < 10f) {
                                        isLongPressed = true
                                        isDragging = true
                                        isLongPressTriggered = true
                                    }
                                }

                                if (isDragging) {
                                    // 将像素位移转换为 dp 并更新位置
                                    val deltaXDp = with(density) { deltaX.toDp() }
                                    val deltaYDp = with(density) { deltaY.toDp() }
                                    val (clampedStart, clampedBottom) = clampPadding(
                                        paddingStartDp.value + deltaXDp.value,
                                        paddingBottomDp.value - deltaYDp.value
                                    )
                                    paddingStartDp = clampedStart.dp
                                    paddingBottomDp = clampedBottom.dp
                                    currentChange.consume()
                                }

                                if (!currentChange.pressed) {
                                    // 手指抬起
                                    val touchDuration = System.currentTimeMillis() - longPressStartTime
                                    val totalHorizontalDrag = kotlin.math.abs(totalDragX)

                                    if (isDragging) {
                                        isDragging = false
                                        isLongPressed = false
                                        snapToEdge()
                                    } else if (!isLongPressTriggered) {
                                        // 快速滑动识别：触摸时长 < 200ms 且水平位移 > 阈值
                                        if (touchDuration < SWIPE_TIME_THRESHOLD && totalHorizontalDrag > SWIPE_DISTANCE_THRESHOLD) {
                                            if (totalDragX < 0) {
                                                onSwipeLeft()
                                            } else {
                                                onSwipeRight()
                                            }
                                        } else {
                                            // 短按：先播放点击缩放动画，再跳转
                                            coroutineScope.launch {
                                                playClickAnimation()
                                                onClick()
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
                },
            shape = CircleShape,
            color = UiColors.Primary
        ) {
            FrameAnimation(
                animationType = currentAnimationType,
                fps = 8,
                isLooping = true,
                modifier = Modifier.size(buttonSize)
            )
        }
    }
}
