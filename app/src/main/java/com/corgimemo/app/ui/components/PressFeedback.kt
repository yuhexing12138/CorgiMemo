package com.corgimemo.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.MutableFloatState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 按压反馈状态机的状态枚举
 *
 * 用于将手势状态转换逻辑抽离为可独立测试的纯函数，
 * 避免依赖 Compose Runtime。
 */
enum class PressState {
    /** 静止状态：无任何反馈 */
    IDLE,
    /** 滑动接触状态：scale 缩放，无水波纹 */
    SCROLL_CONTACT,
    /** 按压状态：显示水波纹 */
    PRESS
}

/**
 * 按压反馈状态机的纯函数算法
 *
 * 将手势状态转换逻辑抽离为可独立测试的纯函数，
 * 避免依赖 Compose Runtime。
 */
object PressFeedbackLogic {

    /**
     * 根据当前 down 状态、移动距离、经过时间决定下一步状态
     *
     * @param currentState 当前状态
     * @param dragDistance 当前移动距离（px）
     * @param touchSlop 系统触摸阈值（px）
     * @param elapsedSinceDownMs 自 down 事件以来经过的毫秒数
     * @return 下一个状态
     */
    fun resolveNextState(
        currentState: PressState,
        dragDistance: Float,
        touchSlop: Float,
        elapsedSinceDownMs: Long
    ): PressState {
        // IDLE 状态由调用方根据是否处于 down 流程判断，此处不处理
        if (currentState == PressState.IDLE) return currentState

        // 在 down 后的前 16ms 内，若移动 > touchSlop → 切换为 SCROLL_CONTACT
        if (elapsedSinceDownMs < 16L && dragDistance > touchSlop) {
            return PressState.SCROLL_CONTACT
        }

        // 16ms 后未触发滑动 → 进入 PRESS
        if (elapsedSinceDownMs >= 16L && dragDistance <= touchSlop) {
            return PressState.PRESS
        }

        return currentState
    }

    /**
     * 根据目标状态计算 Animatable 目标 scale 值
     *
     * @param state 目标状态
     * @param scaleDown 缩小后的目标值（如 0.92f）
     * @return Animatable 的目标值
     */
    fun targetScaleForState(state: PressState, scaleDown: Float): Float {
        return when (state) {
            PressState.IDLE -> 1f
            PressState.SCROLL_CONTACT -> scaleDown
            PressState.PRESS -> 1f
        }
    }
}

/**
 * 为 Composable 提供统一的按压反馈修饰符
 *
 * 整合三套反馈逻辑：
 * 1. **滑动接触反馈**：滑动中手指接触时，卡片缩放至 0.92f 后恢复
 * 2. **点击/长按水波纹**：静止点击时显示水波纹
 * 3. **长按检测**：500ms 长按触发 onLongClick
 * 4. **拖拽让位协调**：500ms 后若 isDragActive=true（拖拽接管），自动 break 让位
 *
 * 状态机：
 * - down → 立即 scale 缩小（SCROLL_CONTACT 临时态）
 * - 16ms 内未移动 → 发射 Press（显示水波纹）
 * - 16ms 内移动 > touchSlop → 保持缩小（无水波纹）
 * - 手指抬起 → scale 恢复 + 发射 Release/Cancel
 * - 长按 500ms 后 isDragActive=true → break 让位给拖拽容器
 *
 * 重要：scale 必须由调用方创建并传入（使用 remember { mutableFloatStateOf(1f) }），
 * 以保证 graphicsLayer 能在重组时读取最新值。
 *
 * @param interactionSource 外部传入的交互源，用于发射 Press/Release/Cancel 事件
 * @param scale 缩放状态（由调用方 remember 创建）
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
 * @return 包装了 graphicsLayer + pointerInput 的 Modifier
 */
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scale: MutableFloatState,
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
        .graphicsLayer {
            scaleX = scale.floatValue
            scaleY = scale.floatValue
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
            // pointerInput 内部使用 this.launch 启动子协程
            // 这里的 this 是 AwaitPointerEventScope，launch 是其合法扩展
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downPosition = down.position

                // 立即缩小 scale（视觉上立即反馈触摸）
                scale.floatValue = scaleDown

                // 启动 16ms 延迟 Press 事件发射
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

                var terminalEmitted = false
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.first()

                        // 手指抬起：触发 tap/longClick
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
                            // 恢复 scale
                            scale.floatValue = 1f
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
                            // 恢复 scale
                            scale.floatValue = 1f
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
                            scale.floatValue = 1f
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
                    scale.floatValue = 1f
                }
            }
        }
)
