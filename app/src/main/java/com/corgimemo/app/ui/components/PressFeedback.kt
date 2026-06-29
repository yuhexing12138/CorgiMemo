package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 为 Composable 提供统一的按压反馈修饰符
 *
 * 整合三套反馈逻辑：
 * 1. **滑动接触反馈**：滑动中手指接触时，卡片缩放至 scaleDown 后恢复（无水波纹）
 * 2. **点击/长按水波纹**：静止点击时显示水波纹
 * 3. **长按检测**：500ms 长按触发 onLongClick
 * 4. **拖拽让位协调**：500ms 后若 isDragActive=true（拖拽接管），自动 break 让位
 *
 * 状态机（内联在 awaitEachGesture 中，避免引入额外抽象）：
 * - down → 启动 scaleDown 动画（60ms）
 * - 16ms 内未移动 → 发射 Press（indication 监听到后显示水波纹）
 * - 16ms 内移动 > touchSlop → 取消 Press 发射、scale 保持缩小（无水波纹）
 * - 手指抬起 → 启动 scaleUp 动画（80ms）+ 发射 Release
 * - 长按 500ms 后 isDragActive=true → break 让位给拖拽容器
 *
 * 重要：scale 必须由调用方创建并传入（使用 `remember { Animatable(1f) }`），
 * 以保证 graphicsLayer 能在重组时读取最新值。
 *
 * @param interactionSource 外部传入的交互源，用于发射 Press/Release/Cancel 事件
 * @param scale 缩放 Animatable（由调用方 remember 创建）
 * @param enabled 是否启用交互（false 时所有逻辑跳过）
 * @param isBatchMode 是否处于批量选择模式（保留参数以备扩展）
 * @param onTap 短按回调（< 500ms 抬起时触发）
 * @param onLongClick 长按回调（≥ 500ms 后抬起时触发）
 * @param scaleDown 缩小后的目标值，默认 0.92f
 * @param scaleDownDurationMs 缩小动画时长（毫秒），默认 60ms
 * @param scaleUpDurationMs 恢复动画时长（毫秒），默认 80ms
 * @param pressDelayMs Press 事件延迟发射时间（毫秒），默认 16ms
 * @param isDragActive 用于让位协调的 lambda（默认 false）。当长按已触发后，
 *                     若该 lambda 返回 true，则主动 break 让位给拖拽容器
 * @return 包装了 indication + graphicsLayer + pointerInput 的 Modifier
 */
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scale: Animatable<Float, *>,
    enabled: Boolean = true,
    isBatchMode: Boolean = false,
    onTap: () -> Unit = {},
    onLongClick: () -> Unit = {},
    scaleDown: Float = 0.92f,
    scaleDownDurationMs: Int = 60,
    scaleUpDurationMs: Int = 80,
    pressDelayMs: Long = 16L,
    isDragActive: () -> Boolean = { false }
): Modifier = this.then(
    if (!enabled) Modifier
    else Modifier
        // 关键：indication 必须在 graphicsLayer 和 pointerInput 之前，
        // 这样 graphicsLayer 缩放后 indication 仍能正确显示水波纹
        .indication(
            interactionSource = interactionSource,
            indication = ripple()
        )
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(
            interactionSource,
            isBatchMode,
            onTap,
            onLongClick,
            scaleDown,
            scaleDownDurationMs,
            scaleUpDurationMs,
            pressDelayMs,
            isDragActive
        ) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = down.position

                // 启动 16ms 延迟 Press 事件发射
                // 16ms 是经验值：比一次屏幕刷新（16.67ms @ 60Hz）略短，
                // 让 Press 在第二次刷新前就派发，indication 立即绘制水波纹
                var pressSent = false
                val pressInteraction = PressInteraction.Press(downPosition)
                val pressJob = launch {
                    delay(pressDelayMs)
                    interactionSource.emit(pressInteraction)
                    pressSent = true
                }

                // 启动 500ms 长按定时器
                var longPressTriggered = false
                val longPressJob = launch {
                    delay(500L)
                    longPressTriggered = true
                }

                // 立即启动 scaleDown 动画（launch 不阻塞 gesture 循环）
                var scaleAnimJob: Job? = null
                scaleAnimJob = launch {
                    scale.animateTo(
                        targetValue = scaleDown,
                        animationSpec = tween(durationMillis = scaleDownDurationMs)
                    )
                }

                var terminalEmitted = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        // 手指抬起：触发 tap/longClick + scaleUp 动画
                        if (change.changedToUp()) {
                            longPressJob.cancel()
                            pressJob.cancel()
                            if (pressSent) {
                                interactionSource.emit(PressInteraction.Release(pressInteraction))
                            }
                            terminalEmitted = true

                            if (longPressTriggered) {
                                onLongClick()
                            } else {
                                onTap()
                            }
                            // 恢复 scale（动画）
                            scaleAnimJob?.cancel()
                            scaleAnimJob = launch {
                                scale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = scaleUpDurationMs)
                                )
                            }
                            break
                        }

                        // 移动距离检测：超过 touchSlop 视为滑动接触
                        val dragDistance = (change.position - downPosition).getDistance()
                        val touchSlop = viewConfiguration.touchSlop
                        if (dragDistance > touchSlop) {
                            longPressJob.cancel()
                            pressJob.cancel()
                            if (pressSent) {
                                interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                            }
                            terminalEmitted = true
                            // 滑动接触：scale 立即恢复（设计要求「缩小后立即恢复」）
                            // indication 已收到 Cancel 不显示水波纹
                            scaleAnimJob?.cancel()
                            scaleAnimJob = launch {
                                scale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = scaleUpDurationMs)
                                )
                            }
                            break
                        }

                        // 长按已触发后：检测拖拽容器是否接管，若是则让位
                        // 关键：必须放在 awaitPointerEvent 内才能持续读取最新 isDragActive 值
                        if (longPressTriggered && isDragActive()) {
                            longPressJob.cancel()
                            pressJob.cancel()
                            if (pressSent) {
                                interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                            }
                            terminalEmitted = true
                            // 恢复 scale（拖拽开始时让拖拽容器接管视觉）
                            scaleAnimJob?.cancel()
                            scaleAnimJob = launch {
                                scale.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = scaleUpDurationMs)
                                )
                            }
                            break
                        }
                    }
                } finally {
                    pressJob.cancel()
                    longPressJob.cancel()
                    if (!terminalEmitted && pressSent) {
                        interactionSource.emit(PressInteraction.Cancel(pressInteraction))
                    }
                    // 异常路径：恢复 scale
                    scaleAnimJob?.cancel()
                    scaleAnimJob = launch {
                        scale.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = scaleUpDurationMs)
                        )
                    }
                }
            }
        }
)
