package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * 悬浮柯基按钮组件
 *
 * 可拖动的悬浮按钮，显示柯基头像帧动画。
 * 支持呼吸动画、点击缩放、长按拖动、贴边吸附、位置记忆、空闲动画、情绪变化和完成庆祝。
 *
 * 手势说明:
 * - 点击(位移 < 8px 且时长 < 200ms):触发 onClick
 * - 长按(> 200ms)或拖动(位移 > 8px):进入拖动模式,绝对位置追踪,松手贴边吸附
 *
 * @param onClick 点击回调（进入柯基详情页）
 * @param onPositionChanged 位置变化回调（保存到 DataStore,百分比坐标）
 * @param initialPosition 初始位置（从 DataStore 恢复，百分比坐标）
 * @param triggerCelebration 触发庆祝动画的信号（值变化时触发）
 * @param currentMood 当前柯基情绪（用于情绪变化时切换表情）
 * @param modifier 修饰符
 */
@Composable
fun FloatingCorgiButton(
    onClick: () -> Unit,
    onPositionChanged: (Float, Float) -> Unit,
    initialPosition: Pair<Float, Float>? = null,
    triggerCelebration: Long = 0,
    currentMood: CorgiMood = CorgiMood.NORMAL,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // 按钮尺寸（dp 常量，用于视觉;px 值在 BoxWithConstraints 内计算）
    val buttonSize = 48.dp

    // 安全区域边距（dp 常量）
    // topSafeMargin: 容器已通过 paddingValues 避开 topBar,留小边距避免按钮紧贴顶部
    // bottomSafeMargin: 与 FAB 的 bottom padding(16dp)一致,使默认位置与 FAB 等高
    // horizontalMargin: 左右边距,也是贴边吸附的目标位置
    val topSafeMargin = 8.dp
    val bottomSafeMargin = 16.dp
    val horizontalMargin = 16.dp

    // 当前位置（像素 Int，用于 offset）
    // 初始值 0,在 BoxWithConstraints 内根据实际尺寸初始化为默认左下角
    var offsetX by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0) }

    // 是否已初始化位置
    var isPositionInitialized by remember { mutableStateOf(false) }

    // 拖动状态
    var isDragging by remember { mutableStateOf(false) }

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

    // 初始化位置占位:实际初始化在 BoxWithConstraints 内进行(依赖容器真实尺寸)
    // 此处仅保留 isPositionInitialized 状态消费方,具体赋值见下方 BoxWithConstraints 块

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

    // 使用 BoxWithConstraints 获取真实可用尺寸(含 insets 处理后的实际宽度)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 容器真实尺寸(像素)
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }

        // 像素常量(由 dp 转换,用于边界计算)
        val buttonSizePx = with(density) { buttonSize.toPx() }
        val horizontalMarginPx = with(density) { horizontalMargin.toPx() }
        val topSafeMarginPx = with(density) { topSafeMargin.toPx() }
        val bottomSafeMarginPx = with(density) { bottomSafeMargin.toPx() }

        // 安全边界(像素)
        val minX = horizontalMarginPx
        val maxX = containerWidthPx - buttonSizePx - horizontalMarginPx
        val minY = topSafeMarginPx
        val maxY = containerHeightPx - buttonSizePx - bottomSafeMarginPx

        // 初始化位置(在 BoxWithConstraints 内,确保 containerWidthPx/Height 已就绪)
        LaunchedEffect(initialPosition, containerWidthPx, containerHeightPx) {
            if (!isPositionInitialized && containerWidthPx > 0f && containerHeightPx > 0f) {
                if (initialPosition != null) {
                    val (xPercent, yPercent) = initialPosition
                    offsetX = (xPercent * containerWidthPx).toInt()
                        .coerceIn(minX.toInt(), maxX.toInt())
                    offsetY = ((1f - yPercent) * containerHeightPx).toInt()
                        .coerceIn(minY.toInt(), maxY.toInt())
                } else {
                    // 默认左下角
                    offsetX = minX.toInt()
                    offsetY = maxY.toInt()
                }
                isPositionInitialized = true
            }
        }

        /**
         * 贴边吸附:根据当前 offsetX 判断吸附到左/右边缘,用 tween 平滑动画过渡
         * Y 坐标保持不变。完成后持久化百分比坐标到 DataStore。
         * 动画规格:FastOutSlowInEasing(300ms),Material 标准平滑过渡,无弹跳
         */
        fun snapToEdge() {
            val halfWidth = containerWidthPx / 2
            val targetX = if (offsetX < halfWidth) minX else maxX

            coroutineScope.launch {
                playBounceAnimation()
                // tween 平滑过渡(无弹跳,避免突兀感)
                val animX = Animatable(offsetX.toFloat())
                animX.animateTo(
                    targetValue = targetX,
                    animationSpec = tween(
                        durationMillis = 300,
                        easing = FastOutSlowInEasing
                    )
                )
                offsetX = animX.value.toInt()

                // 持久化(百分比坐标,与 DataStore 旧格式兼容)
                val percentX = offsetX / containerWidthPx
                val percentY = 1f - (offsetY / containerHeightPx)
                onPositionChanged(percentX, percentY)
            }
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) }
                .size(buttonSize)
                .zIndex(10f)
                .shadow(
                    elevation = if (isDragging) 16.dp else 8.dp,
                    shape = CircleShape
                )
                .scale(finalScale)
                // 手势处理:挂在 Surface 上,只覆盖 48dp 按钮区域,不拦截页面其他组件事件
                // change.position 为相对按钮左上角的局部坐标
                .pointerInput(Unit) {
                    // 长按阈值:200ms(不再为快速滑动让步,降低误触)
                    val LONG_PRESS_THRESHOLD = 200L
                    // 拖动启动位移阈值:8px(超过即视为拖动,响应更快)
                    val DRAG_START_THRESHOLD = 8f
                    // 点击位移阈值:8px(小于此值视为点击)
                    val CLICK_THRESHOLD = 8f

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent()
                            val downChange = down.changes.firstOrNull() ?: continue

                            // 按下时记录手指相对按钮左上角的偏移(用于绝对位置追踪)
                            // pointerInput 挂在 Surface 上,position 是局部坐标,无需 insideButton 判断
                            val touchOffsetX = downChange.position.x
                            val touchOffsetY = downChange.position.y

                            val dragStartTime = System.currentTimeMillis()
                            var totalDragX = 0f
                            var totalDragY = 0f
                            // 局部拖动标志:控制手势循环内逻辑流
                            // 注意:与外层可观察状态 isDragging 同名会遮蔽,故用 isDragActive
                            var isDragActive = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.isConsumed) break

                                // 绝对位置追踪:手指相对按钮局部坐标 + 按钮当前位置 = 手指容器坐标
                                // 当组件移动时 change.position 反向变化,但 offsetX+change.position 保持不变
                                // 消除组件移动导致的抖动
                                val absCurrentX = offsetX + change.position.x
                                val absCurrentY = offsetY + change.position.y

                                if (!isDragActive) {
                                    // 累计相对按下点的总位移(用于点击 vs 拖动判定)
                                    // 在按钮未移动时,change.position - downChange.position 等价于绝对位移
                                    totalDragX = change.position.x - downChange.position.x
                                    totalDragY = change.position.y - downChange.position.y

                                    val elapsed = System.currentTimeMillis() - dragStartTime
                                    val moved = abs(totalDragX) > DRAG_START_THRESHOLD ||
                                                abs(totalDragY) > DRAG_START_THRESHOLD
                                    // 长按超时 或 位移超阈值,任一满足即进入拖动
                                    if (elapsed > LONG_PRESS_THRESHOLD || moved) {
                                        isDragActive = true
                                        isDragging = true  // 同步外层可观察状态,触发 scale/shadow 变化
                                    }
                                }

                                if (isDragActive) {
                                    // 绝对位置追踪:目标按钮位置 = 手指容器坐标 - 按下偏移(局部)
                                    // 无截断、无累积误差,丢帧后下一帧立即追上手指
                                    val targetX = (absCurrentX - touchOffsetX)
                                        .coerceIn(minX, maxX)
                                    val targetY = (absCurrentY - touchOffsetY)
                                        .coerceIn(minY, maxY)
                                    offsetX = targetX.toInt()
                                    offsetY = targetY.toInt()
                                    change.consume()
                                }

                                if (!change.pressed) {
                                    // 手指抬起
                                    if (isDragActive) {
                                        // 拖动结束:贴边吸附
                                        isDragActive = false
                                        isDragging = false
                                        snapToEdge()
                                    } else if (abs(totalDragX) < CLICK_THRESHOLD &&
                                               abs(totalDragY) < CLICK_THRESHOLD) {
                                        // 点击:先播放缩放动画再回调
                                        coroutineScope.launch {
                                            playClickAnimation()
                                            onClick()
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
