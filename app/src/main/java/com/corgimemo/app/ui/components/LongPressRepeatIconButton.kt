package com.corgimemo.app.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 长按连发：按下到开始连发的等待时长（对齐原 BlockNote JS 按钮） */
const val DEFAULT_REPEAT_INITIAL_DELAY_MS = 450L

/** 长按连发：连发间隔（对齐原 BlockNote JS 按钮） */
const val DEFAULT_REPEAT_INTERVAL_MS = 150L

/**
 * 「长按连发」手势 Modifier（v2026-09-17，v1.8 抽出复用）
 *
 * 手感对齐原 BlockNote JS 按钮 `AutoRepeatButton`——
 * **按下立即执行一次 → 450ms 后每 150ms 重复一次 → 抬起/移出/取消即停**。
 *
 * 抽成 Modifier 的目的是让视觉各异的按钮（图标 / 文字 / 带降级的 Ri 图标）都能复用同一套
 * 手势与节拍逻辑，而不必各自重写 pointerInput。
 *
 * ## 三个必须遵守的约束（均已核对 Compose 源码）
 *
 * 1. **`awaitPointerEventScope` / `awaitEachGesture` 是 `@RestrictsSuspension` 受限作用域**
 *    （`SuspendingPointerInputFilter.kt` 第 63 行），块内**只能**挂起于 `awaitPointerEvent`，
 *    禁止 `delay` / `launch` / `coroutineScope` 等其它挂起调用。
 *    所以连发定时器**不能**写在 `awaitEachGesture {}` 里。
 *
 * 2. **`PointerInputScope` 与 `AwaitPointerEventScope` 都不实现 `CoroutineScope`**
 *    （同文件第 121-128 行的设计说明：刻意如此，避免破坏结构化并发），直接 `launch` 不可用。
 *    因此用 `rememberCoroutineScope()` 拿到 Composable 作用域承载连发协程——
 *    **手势检测（受限域）与连发定时器（普通域）彻底分离**：
 *    受限域只负责「何时开始/停止」，普通域负责「按节拍重复」。
 *
 * 3. **`IconButton` 的 `clickable` 会在 down 时立即 `consume()` 事件**
 *    （参见本项目 `PressFeedback.kt` 第 180-196 行的同款踩坑记录）。
 *    故使用本 Modifier 时**不要**再让 onClick 承载动作（传空实现），
 *    动作完全由 `awaitFirstDown(requireUnconsumed = false)` + `waitForUpOrCancellation()`
 *    驱动，避免「点击」与「连发」各触发一次造成双执行。
 *
 * @param onAction 单次动作；按下时调用一次，长按时被连续调用
 * @param enabled 是否可交互；false 时完全不响应手势
 * @param canRepeat 是否允许继续连发；连发期间实时读取，变 false 即停
 * @param initialDelayMs 按下到开始连发的等待时长
 * @param repeatIntervalMs 连发间隔
 */
@Composable
fun Modifier.longPressRepeat(
    onAction: () -> Unit,
    enabled: Boolean,
    canRepeat: Boolean,
    initialDelayMs: Long = DEFAULT_REPEAT_INITIAL_DELAY_MS,
    repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
): Modifier {
    /**
     * `pointerInput` 的 lambda 只在 key 变化时重启；直接捕获 onAction / canRepeat
     * 会形成旧闭包（stale closure），连发时调到的仍是首次组合时的逻辑。
     * 用 rememberUpdatedState 包一层，保证始终读到最新值。
     */
    val currentAction by rememberUpdatedState(onAction)
    val currentCanRepeat by rememberUpdatedState(canRepeat)

    /**
     * 连发定时器专用作用域（普通 Composable 协程作用域，非受限域）。
     * 组件离开组合时自动取消，不会泄漏。
     */
    val timerScope = rememberCoroutineScope()

    /**
     * 当前连发任务。必须 remember——否则每次重组都会重置为 null，
     * 抬手时 `cancel()` 就取消不到真正在跑的那个协程（连发停不下来）。
     * 用普通可变持有器而非 mutableStateOf：该值只在手势回调里读写、从不参与组合读取，
     * 用 State 反而会在每次赋值时触发一次无意义的重组。
     */
    val repeatJobRef = remember { arrayOfNulls<Job>(1) }

    return this.pointerInput(enabled, initialDelayMs, repeatIntervalMs) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            /**
             * requireUnconsumed = false：IconButton 的水波纹（clickable 内部
             * detectTapGestures）可能已消费 down，这里仍要拿到以确保响应。
             */
            awaitFirstDown(requireUnconsumed = false)
            // 按下即刻执行一次（对齐原 JS onPointerDown 语义；轻点即一次）
            currentAction()
            // 交给普通作用域按节拍重复；受限域内不挂起于 delay，合规
            repeatJobRef[0]?.cancel()
            repeatJobRef[0] = timerScope.launch {
                delay(initialDelayMs)
                while (isActive) {
                    // 见底立即停发，不产生无效点击
                    if (!currentCanRepeat) break
                    currentAction()
                    delay(repeatIntervalMs)
                }
            }
            // 挂起点：抬起 / 滑出边界 / 被上层拦截都会返回，随后停发
            waitForUpOrCancellation()
            repeatJobRef[0]?.cancel()
            repeatJobRef[0] = null
        }
    }
}

/**
 * 带「长按连发」能力的图标按钮（v2026-09-17）
 *
 * 动机：软键盘没有 Ctrl+Z，撤销/重做要连续回退多步时逐次点击很累。
 * 手势与节拍逻辑见 [Modifier.longPressRepeat]。
 *
 * @param icon 图标（如 `Icons.AutoMirrored.Filled.Undo`）
 * @param contentDescription 无障碍描述
 * @param onAction 单次动作（撤销/重做）；长按时被连续调用
 * @param enabled 是否可交互；false 时置灰且完全不响应手势
 * @param canRepeat 是否允许继续连发；连发期间实时读取，变 false（历史栈见底）即停
 * @param modifier 外部修饰符
 * @param iconSize 图标尺寸，默认 18dp（与顶栏其它图标一致）
 * @param buttonSize 触摸区尺寸，默认 36dp（与顶栏其它图标一致）
 * @param initialDelayMs 按下到开始连发的等待时长
 * @param repeatIntervalMs 连发间隔
 */
@Composable
fun LongPressRepeatIconButton(
    icon: ImageVector,
    contentDescription: String,
    onAction: () -> Unit,
    enabled: Boolean,
    canRepeat: Boolean,
    modifier: Modifier = Modifier,
    iconSize: Dp = 18.dp,
    buttonSize: Dp = 36.dp,
    initialDelayMs: Long = DEFAULT_REPEAT_INITIAL_DELAY_MS,
    repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
) {
    IconButton(
        /** 动作不走 onClick（见 [Modifier.longPressRepeat] 约束 3），完全交给手势 Modifier */
        onClick = {},
        enabled = enabled,
        modifier = modifier
            .size(buttonSize)
            .longPressRepeat(
                onAction = onAction,
                enabled = enabled,
                canRepeat = canRepeat,
                initialDelayMs = initialDelayMs,
                repeatIntervalMs = repeatIntervalMs,
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(iconSize)
        )
    }
}
