package com.corgimemo.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
 * 「长按连发」手势 Modifier（v2026-09-17 抽出复用，v2026-09-22 重写手势底层）
 *
 * 手感对齐原 BlockNote JS 按钮 `AutoRepeatButton`——
 * **短按抬起即一次 → 按住 450ms 后每 150ms 重复一次 → 抬起/移出/取消即停**。
 *
 * 抽成 Modifier 的目的是让视觉各异的按钮（图标 / 文字 / 带降级的 Ri 图标）都能复用同一套
 * 手势与节拍逻辑，而不必各自重写 pointerInput。
 *
 * ## 实现要点（均已核对 Compose 源码，v2026-09-22）
 *
 * 1. **底层用 `detectTapGestures(onPress)` 而非裸 `awaitEachGesture`**：
 *    `PressGestureScope.tryAwaitRelease()` 能区分「点击/长按」与「拖动」——
 *    父级 `horizontalScroll` 消费了滑动时，`onPress` 被取消、`tryAwaitRelease` 返回
 *    `false`，此时**不触发任何动作**，从根上消除「左右滑动工具栏误触 Nest/Unnest」。
 *
 * 2. **动作不在 `down` 即执行**：短按在抬起时补一次（与其他普通按钮一致）；
 *    长按由连发定时器覆盖。这样「down 即动作 → 同帧 enabled 翻转 orphan 原生 ripple」
 *    的时序缝隙也不存在（详见 [RiFormatButton] 内注释）。
 *
 * 3. **连发定时器用 `rememberCoroutineScope()` 的普通作用域承载**：`onPress` 是受限挂起域，
 *    内部不能直接 `launch`/`delay`；受限域只负责「何时开始/停止」，普通域负责「按节拍重复」，
 *    两者彻底分离（与旧实现一致）。
 *
 * 4. **原生 ripple 由调用方持有交互源、手势全权 emit**：传入 [interactionSource] 时，
 *    `down` 即 `tryEmit(Press)`、`finally` 必 `tryEmit(Release)`——不手绘、视觉与官方
 *    `IconButton` 完全一致，且任何退出路径都有 finally 兜底，圆圈永不残留。
 *
 * @param onAction 单次动作；短按抬起时调用一次，长按时被连续调用
 * @param enabled 是否可交互；false 时完全不响应手势
 * @param canRepeat 是否允许继续连发；连发期间实时读取，变 false 即停
 * @param initialDelayMs 按住到开始连发的等待时长
 * @param repeatIntervalMs 连发间隔
 * @param interactionSource 可选：传入后由本手势驱动其 Press/Release，供 `.indication(ripple)`
 *   渲染原生水波纹（不传则不改变任何交互源，适用于自带 clickable 的 `IconButton`）
 */
@Composable
fun Modifier.longPressRepeat(
    onAction: () -> Unit,
    enabled: Boolean,
    canRepeat: Boolean,
    initialDelayMs: Long = DEFAULT_REPEAT_INITIAL_DELAY_MS,
    repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
    interactionSource: MutableInteractionSource? = null,
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
        detectTapGestures(
            onPress = { offset ->
                /**
                 * 用 detectTapGestures 的 onPress + tryAwaitRelease 区分「点击/长按」与
                 * 「拖动」：工具栏 Row 处于 horizontalScroll，左右滑动会被父级消费 →
                 * onPress 被取消、tryAwaitRelease 返回 false，此时不触发任何动作，
                 * 也不会误触 Nest / Unnest（v2026-09-22 滑动误触根因修复）。
                 */
                // down 即向交互源下发 Press —— 由 .indication(ripple) 渲染原生水波纹，
                // 视觉/色值与其他 IconButton 完全一致（v2026-09-22 不手绘修复）。
                val press = PressInteraction.Press(offset)
                interactionSource?.tryEmit(press)
                var repeated = false
                // 连发定时器：普通 Composable 作用域承载，不写在受限的 onPress 挂起域内
                repeatJobRef[0]?.cancel()
                repeatJobRef[0] = timerScope.launch {
                    delay(initialDelayMs)
                    while (isActive) {
                        // 见底立即停发，不产生无效点击
                        if (!currentCanRepeat) break
                        repeated = true
                        currentAction()
                        delay(repeatIntervalMs)
                    }
                }
                try {
                    /**
                     * 短按：用户在 initialDelayMs 内抬起（连发未触发）→ 补一次单击动作，
                     * 行为与其他普通按钮一致（动作在抬起时执行，而非按下时）。
                     * 长按：连发已覆盖动作，此处不再重复。
                     * 滑动/被消费：tryAwaitRelease 返回 false → 不触发动作（修复误触）。
                     */
                    if (tryAwaitRelease() && !repeated) {
                        currentAction()
                    }
                } finally {
                    /**
                     * 任何退出（抬起 / 滑动取消 / 参数变化重启 pointerInput / 组合离开）
                     * 都向交互源下发 Release —— 原生 ripple 由手势全权持有、finally 必复位，
                     * 彻底消除「水波纹圆圈永久残留」（v2026-09-22 根因修复）。
                     */
                    repeatJobRef[0]?.cancel()
                    repeatJobRef[0] = null
                    interactionSource?.tryEmit(PressInteraction.Release(press))
                }
            }
        )
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
