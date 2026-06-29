package com.corgimemo.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 为 Composable 提供统一的按压反馈修饰符（scale 缩放 + 点击/长按 + 拖拽让位）
 *
 * 提供三套反馈逻辑：
 * 1. **滑动接触反馈**：手指接触时 scale 缩小到 scaleDown，抬起/移动超过 touchSlop 时恢复 1f
 * 2. **点击/长按检测**：< 500ms 抬起 = onTap，>= 500ms 抬起 = onLongClick
 * 3. **拖拽让位协调**：长按已触发后若 isDragActive=true，主动 break 让位给拖拽容器
 *
 * 动画实现：
 * - PressFeedback 内部同步修改 [scale]（MutableFloatState）目标值
 * - 外部 Composable 层用 [animateFloatAsState] 监听 [scale] 并平滑过渡到目标值
 * - 缩小（target < 1f）和放大（target = 1f）使用不同的 duration
 *   实现"按下立即缩、抬起缓慢回弹"的物理感
 *
 * 状态机（内联在 awaitEachGesture 中）：
 * - down -> 立即 scale.floatValue = scaleDown（同步写入，Composable 自动动画过渡）
 * - 在 awaitPointerEvent 主循环中通过时间戳检测长按（now - downTime >= 500L）
 * - 手指抬起 -> scale.floatValue = 1f + 触发 onTap/onLongClick
 * - 移动 > touchSlop -> scale.floatValue = 1f，不触发 onTap/onLongClick
 * - 长按 + isDragActive -> scale.floatValue = 1f，让位给拖拽容器
 *
 * 关于缩放动画：使用 [animateFloatAsState]（Composable API），而非 Animatable.animateTo。
 * 原因：在 Compose 1.9 中，AwaitPointerEventScope 是 @RestrictsSuspension 标注的接口，
 * 其 block 是 restricted suspend code，只能调用 awaitPointerEvent，不能调用
 * Animatable.animateTo / InteractionSource.emit / delay 等其他 suspend fun。
 * 因此 PressFeedback 内部只同步修改 scale 目标值，动画过渡由 Composable 层完成。
 *
 * 关于水波纹（indication ripple）：本版本**不**发射 PressInteraction 给 interactionSource，
 * 因此 indication 不会触发水波纹。如需水波纹，请使用 Modifier.clickable 等内置 modifier。
 *
 * 关于 interactionSource 参数：保留仅为 API 向后兼容，传入后**不**会发射任何事件。
 *
 * @param interactionSource 保留参数以兼容 API（已不再使用）
 * @param scale 缩放目标状态（由调用方 remember 创建，初始值 1f）
 * @param enabled 是否启用交互（false 时所有逻辑跳过）
 * @param isBatchMode 保留参数以备扩展
 * @param onTap 短按回调（< 500ms 抬起时触发）
 * @param onLongClick 长按回调（>= 500ms 抬起时触发）
 * @param scaleDown 缩小后的目标值，默认 0.88f
 * @param scaleDownDurationMs 缩小动画时长（毫秒，默认 60ms）
 * @param scaleUpDurationMs 恢复动画时长（毫秒，默认 200ms）—— 用户反馈 80ms 太快，调慢至 200ms
 * @param pressDelayMs 保留参数以备扩展（已不再发射 Press）
 * @param isDragActive 让位协调 lambda（默认 false）
 * @return 包装了 graphicsLayer + pointerInput 的 Modifier
 */
@Suppress("LongParameterList", "LongMethod", "UNUSED_PARAMETER")
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scale: MutableFloatState,
    enabled: Boolean = true,
    isBatchMode: Boolean = false,
    onTap: () -> Unit = {},
    onLongClick: () -> Unit = {},
    scaleDown: Float = 0.94f,
    scaleDownDurationMs: Int = 60,
    scaleUpDurationMs: Int = 200,
    pressDelayMs: Long = 16L,
    isDragActive: () -> Boolean = { false }
): Modifier {
    // 禁用态：直接返回原 Modifier（无任何反馈叠加）
    if (!enabled) return this

    // 用 animateFloatAsState 包装 scale.floatValue
    // - 目标值 < 1f（缩小方向）→ 用 scaleDownDurationMs（快速按下反馈）
    // - 目标值 = 1f（放大方向）→ 用 scaleUpDurationMs（缓慢回弹）
    // 用 remember(targetValue) 缓存 animationSpec，避免无关重组导致 spec 引用变化、
    // 动画重启造成的视觉抖动
    val targetValue = scale.floatValue
    val animationSpec: AnimationSpec<Float> = remember(targetValue) {
        tween(
            durationMillis = if (targetValue < 1f) scaleDownDurationMs else scaleUpDurationMs,
            easing = FastOutSlowInEasing
        )
    }
    val animatedScale by animateFloatAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = "pressFeedbackScale"
    )

    return this
        // scale 缩放层：graphicsLayer 在每帧读取 animatedScale
        .graphicsLayer {
            scaleX = animatedScale
            scaleY = animatedScale
        }
        .pointerInput(
            scale,
            onTap,
            onLongClick,
            scaleDown,
            isDragActive
        ) {
            // 注意：awaitEachGesture 的 block 是 restricted suspend code
            // （AwaitPointerEventScope 有 @RestrictsSuspension 标注），
            // 内部只能调用 awaitPointerEvent 和扩展属性，**不能**调用任何其他 suspend fun
            // （包括 emit、delay、Animatable.animateTo 等）。
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = down.position
                // 记录 down 时刻，用于在 awaitPointerEvent 主循环中检测长按
                val downTime = System.currentTimeMillis()

                // 立即同步修改 scale 目标值
                // Composable 层 animateFloatAsState 监听到 scale 变化会自动动画过渡
                scale.floatValue = scaleDown

                var longPressTriggered = false
                var terminalEmitted = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()
                        val now = System.currentTimeMillis()

                        // 长按检测：>= 500ms（替代原 scope.launch + delay 协程）
                        if (!longPressTriggered && now - downTime >= 500L) {
                            longPressTriggered = true
                        }

                        // 拖拽让位：长按已触发 + 拖拽容器接管
                        // 必须放在 awaitPointerEvent 内才能持续读取最新 isDragActive 值
                        if (longPressTriggered && isDragActive()) {
                            terminalEmitted = true
                            // 恢复 scale 让拖拽容器接管视觉
                            scale.floatValue = 1f
                            break
                        }

                        // 手指抬起：触发 tap/longClick + 恢复 scale
                        if (change.changedToUp()) {
                            terminalEmitted = true
                            scale.floatValue = 1f
                            if (longPressTriggered) {
                                onLongClick()
                            } else {
                                onTap()
                            }
                            break
                        }

                        // 移动距离检测：超过 touchSlop 视为滑动接触
                        val dragDistance = (change.position - downPosition).getDistance()
                        val touchSlop = viewConfiguration.touchSlop
                        if (dragDistance > touchSlop) {
                            terminalEmitted = true
                            // 滑动接触：scale 立即恢复（设计要求"缩小后立即恢复"）
                            scale.floatValue = 1f
                            break
                        }
                    }
                } finally {
                    // 异常路径：恢复 scale 避免卡在缩小状态
                    if (!terminalEmitted) {
                        scale.floatValue = 1f
                    }
                }
            }
        }
}
