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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * - down -> 立即 down.consume() 主动消费 + scale.floatValue = scaleDown（同步写入，Composable 自动动画过渡）
 *   主动消费 down 的原因：pressFeedback 声明"我处理这个手势"，
 *   父级 pressFeedback 会通过 down.isConsumed == true 短路 return，
 *   避免父级误触发 onTap（典型 bug：点击子任务勾选时 Card 同时响应导致页面跳转）。
 * - **down 事件已被子组件消费（down.isConsumed == true）时直接 return**：
 *   表明更内层的子组件（如 clickable、Surface.onClick、CircularCheckbox）已经接管该手势，
 *   父级 pressFeedback 不应再响应，避免两个并发问题：
 *   1. scale 缩小后 up 事件被子组件消费，awaitPointerEvent 收不到 up → scale 永远卡在缩小状态
 *   2. 父级 pressFeedback 误触发 onTap/onLongClick，与子组件 click 冲突
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
 * @param scaleDown 缩小后的目标值，默认 [DEFAULT_SCALE_DOWN_VALUE]
 * @param scaleDownDurationMs 缩小动画时长（毫秒，默认 [DEFAULT_SCALE_DOWN_DURATION_MS]）
 * @param scaleUpDurationMs 恢复动画时长（毫秒，默认 [DEFAULT_SCALE_UP_DURATION_MS]）—— 用户反馈 80ms 太快，调慢至 200ms
 * @param pressDelayMs 保留参数以备扩展（已不再发射 Press）
 * @param isDragActive 让位协调 lambda（默认 false）
 * @param isLongPressed 长按过程状态（>= 500ms 持续按下时为 true，抬起/移动/cancel/拖拽让位时为 false）。
 *                      外部 Composable 可用 `animateDpAsState(isLongPressed.value)` 平滑过渡阴影 elevation 等视觉属性。
 *                      默认 `remember { mutableStateOf(false) }`。
 * @return 包装了 graphicsLayer + pointerInput 的 Modifier
 */

/**
 * 默认按压"缩小"动画时长（毫秒）
 *
 * 设计目的：v2026-07-21 把此值从 PressFeedback 内部默认值提升为顶层 public const，
 * 让 SwipeableTodoBox 等共享卡片缩放状态的组件能复用同一套时长常量，
 * 避免硬编码 60 / 200 在多处不同步（修改时漏改）。
 *
 * 与 [Modifier.pressFeedback] 的 `scaleDownDurationMs` 参数默认值保持一致：60ms 快速按下反馈。
 */
const val DEFAULT_SCALE_DOWN_DURATION_MS: Int = 60

/**
 * 默认按压"放大回弹"动画时长（毫秒）
 *
 * 与 [Modifier.pressFeedback] 的 `scaleUpDurationMs` 参数默认值保持一致：200ms 缓慢回弹。
 */
const val DEFAULT_SCALE_UP_DURATION_MS: Int = 200

/**
 * 默认按压"缩小"后的目标 scale 值
 *
 * 与 [Modifier.pressFeedback] 的 `scaleDown` 参数默认值保持一致：0.94f。
 */
const val DEFAULT_SCALE_DOWN_VALUE: Float = 0.94f

@Suppress("LongParameterList", "LongMethod", "UNUSED_PARAMETER")
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    scale: MutableFloatState,
    enabled: Boolean = true,
    isBatchMode: Boolean = false,
    onTap: () -> Unit = {},
    onLongClick: () -> Unit = {},
    scaleDown: Float = DEFAULT_SCALE_DOWN_VALUE,
    scaleDownDurationMs: Int = DEFAULT_SCALE_DOWN_DURATION_MS,
    scaleUpDurationMs: Int = DEFAULT_SCALE_UP_DURATION_MS,
    pressDelayMs: Long = 16L,
    isDragActive: () -> Boolean = { false },
    /**
     * 长按过程状态（>= 500ms 持续按下时为 true，抬起/移动/cancel/拖拽让位时为 false）。
     *
     * v2026-07-20 新增：用于让外部 Composable（如 TodoListItem）根据长按状态
     * 动态调整阴影 elevation / 边框等视觉属性，实现"长按抬升"效果。
     *
     * 默认 remember 一个新的 MutableState<Boolean>(false)，与 Modifier.pressFeedback 同生命周期。
     */
    isLongPressed: MutableState<Boolean> = remember { mutableStateOf(false) }
): Modifier {
    // 禁用态：直接返回原 Modifier（无任何反馈叠加）
    // 同时把 isLongPressed 重置为 false，避免外部 Composable 阴影卡在"长按态"
    if (!enabled) {
        isLongPressed.value = false
        return this
    }

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
            isDragActive,
            isLongPressed
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

                // 关键：down 事件已被子组件消费时，父级 pressFeedback 不应再响应
                //
                // 背景：在 TodoListItem 中，Card 内部包含可点击子组件
                // （Surface(onClick = onToggleExpand) 展开按钮、CircularCheckbox 复选框）。
                // 这些子组件的 Modifier.clickable 内部使用 detectTapGestures，
                // 会在 down 事件时立即调用 down.consume() 标记事件已被处理，
                // 并在 up 时再次消费事件触发 onClick。
                //
                // 父级 pressFeedback 使用 awaitFirstDown(requireUnconsumed = false)，
                // 即使 down 事件被消费也会立即返回 → 若继续往下走：
                // 1. scale.floatValue = scaleDown 让卡片缩小
                // 2. awaitPointerEvent() 在 Main pass 下收不到被消费的 up 事件
                // 3. while 循环阻塞，scale 永远卡在缩小状态无法恢复
                // 4. onTap 也不会被触发（因为 up 事件被消费）但视觉上误触发了"按下"动画
                //
                // 修复：检测 down.isConsumed，若已被消费则直接 return，
                // 让子组件的 clickable 独立处理该手势。
                // 这样父级 pressFeedback 完全不参与，避免视觉卡顿和事件冲突。
                if (down.isConsumed) {
                    return@awaitEachGesture
                }

                // 主动消费 down 事件，阻止父级 pressFeedback 误响应
                //
                // 场景：SubTaskCheckbox、TitleColumn 等"无 clickable 但有 pressFeedback"的子组件
                // 如果 pressFeedback 不消费 down 事件，父级 pressFeedback（Card）也会处理，
                // 导致点击子任务勾选时同时触发 Card 的 onTap（页面跳转 bug）。
                //
                // 语义：pressFeedback 表示"该组件声明处理点击/长按"，
                // 消费 down 事件是合理且必要的边界声明。
                // 对父级 pressFeedback 的影响：awaitFirstDown(requireUnconsumed = false)
                // 仍会收到 down（因参数不要求未消费），但会检测到 down.isConsumed == true
                // 在上一段 if 判断处 return，完美实现"谁声明、谁处理"。
                down.consume()

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
                        // v2026-07-20 改动：触发时同步将 isLongPressed 暴露给外部，
                        // 用于实现"长按抬升"视觉（阴影/边框加深等）
                        if (!longPressTriggered && now - downTime >= 500L) {
                            longPressTriggered = true
                            isLongPressed.value = true
                        }

                        // 拖拽让位：长按已触发 + 拖拽容器接管
                        // 必须放在 awaitPointerEvent 内才能持续读取最新 isDragActive 值
                        if (longPressTriggered && isDragActive()) {
                            terminalEmitted = true
                            // 恢复 scale 让拖拽容器接管视觉
                            scale.floatValue = 1f
                            isLongPressed.value = false
                            break
                        }

                        // 手指抬起：触发 tap/longClick + 恢复 scale
                        if (change.changedToUp()) {
                            terminalEmitted = true
                            scale.floatValue = 1f
                            isLongPressed.value = false
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
                            isLongPressed.value = false
                            break
                        }
                    }
                } finally {
                    // 异常路径：恢复 scale 避免卡在缩小状态
                    if (!terminalEmitted) {
                        scale.floatValue = 1f
                    }
                    // 异常路径：无论何种异常退出，都确保 isLongPressed 重置，
                    // 避免阴影 elevation 卡在长按态（v2026-07-20 防护）
                    isLongPressed.value = false
                }
            }
        }
}
