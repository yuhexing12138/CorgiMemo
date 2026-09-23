package com.corgimemo.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
/**
 * ⚠️ `Modifier.indication(interactionSource, indication)` 是 **androidx.compose.foundation
 * 包**的顶层扩展（foundation 1.11.2 的 Indication.kt，已核对源码 jar）——不存在
 * `androidx.compose.foundation.indication` 子包，import 写成那样必报 Unresolved。
 */
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 长按连发：按下到开始连发的等待时长（对齐原 BlockNote JS 按钮） */
const val DEFAULT_REPEAT_INITIAL_DELAY_MS = 450L

/** 长按连发：连发间隔（对齐原 BlockNote JS 按钮） */
const val DEFAULT_REPEAT_INTERVAL_MS = 150L

/**
 * 滚动容器内波纹延迟发射时长（v2026-09-23 对齐官方 clickable 行为）。
 *
 * 对齐 Foundation 内部常量 `TapIndicationDelay = ViewConfiguration.getTapTimeout()`
 * （Android 框架值 = 100ms；该常量是 internal，无法直接引用）。
 */
const val DEFAULT_PRESS_INDICATION_DELAY_MS = 100L

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
 * 3. **连发定时器用 `rememberCoroutineScope()` 的普通作用域承载**：`onPress` 自身不提供
 *    协程作用域（连发 Job 不能挂在手势回调里）；受限域只负责「何时开始/停止」，
 *    普通域负责「按节拍重复」。波纹延迟 Job 则相反——必须随手势协程一起消亡
 *    （要点 5 的 `coroutineScope{}` 结构化子协程），两处作用域选择各有理由，别混用。
 *
 * 4. **原生 ripple 由调用方持有交互源、手势全权 emit**：传入 [interactionSource] 时由本手势
 *    驱动 Press 与收尾（Release/Cancel）——不手绘、视觉与官方 `IconButton` 完全一致，任何
 *    退出路径都有 finally 兜底，圆圈永不残留。
 *
 * 5. **滚动容器内波纹延迟发射（v2026-09-23，参考工具栏其他按钮的官方实现）**：其他按钮走
 *    `IconButton`（内部 `clickable`），Foundation 在 `horizontalScroll` 容器内会把 Press
 *    交互延迟 `TapIndicationDelay`（100ms）发射、滑动被消费时**什么都不发**——这正是
 *    其他按钮滑动起手不闪小波纹的原因。`delayPressIndication = true` 时本手势复刻同一
 *    语义（按 foundation 1.11.2 `ClickableNode.handlePressInteraction` 源码逐条对齐）：
 *    延迟期间正常抬起 → 瞬时补发 Press+Release（快速点按波纹不受影响）；
 *    延迟已过 → 抬起发 Release、取消发 Cancel。
 *
 * @param onAction 单次动作；短按抬起时调用一次，长按时被连续调用
 * @param enabled 是否可交互；false 时完全不响应手势
 * @param canRepeat 是否允许继续连发；连发期间实时读取，变 false 即停
 * @param initialDelayMs 按住到开始连发的等待时长
 * @param repeatIntervalMs 连发间隔
 * @param interactionSource 可选：传入后由本手势驱动其 Press/Release/Cancel，供
 *   `.indication(ripple)` 渲染原生水波纹（不传则不改变任何交互源，适用于自带 clickable 的
 *   `IconButton`）
 * @param delayPressIndication 所在按钮位于可滚动容器（如工具栏 `horizontalScroll`）内时传
 *   `true`：Press 延迟 [DEFAULT_PRESS_INDICATION_DELAY_MS] 发射，滑动起手不闪小波纹，
 *   语义与官方 `clickable` 在滚动容器内的行为一致；默认 `false`（down 即发）
 */
@Composable
fun Modifier.longPressRepeat(
    onAction: () -> Unit,
    enabled: Boolean,
    canRepeat: Boolean,
    initialDelayMs: Long = DEFAULT_REPEAT_INITIAL_DELAY_MS,
    repeatIntervalMs: Long = DEFAULT_REPEAT_INTERVAL_MS,
    interactionSource: MutableInteractionSource? = null,
    delayPressIndication: Boolean = false,
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

    return this.pointerInput(enabled, delayPressIndication, initialDelayMs, repeatIntervalMs) {
        if (!enabled) return@pointerInput
        detectTapGestures(
            onPress = { offset ->
                /**
                 * 用 detectTapGestures 的 onPress + tryAwaitRelease 区分「点击/长按」与
                 * 「拖动」：工具栏 Row 处于 horizontalScroll，左右滑动会被父级消费 →
                 * onPress 被取消、tryAwaitRelease 返回 false，此时不触发任何动作，
                 * 也不会误触 Nest / Unnest（v2026-09-22 滑动误触根因修复）。
                 */
                var repeated = false
                var released = false

                /**
                 * 已向交互源发出、但尚未收尾（Release/Cancel）的 Press。
                 * 只在手势回调里读写、从不参与组合读取，用普通变量即可；
                 * finally 兜底据此补发 Cancel（见下）。
                 */
                var pendingPress: PressInteraction.Press? = null

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
                    val source = interactionSource
                    when {
                        // 未接交互源：只管手势与动作，波纹完全由调用方自管
                        // （⚠️ 本组件已不再走此分支——v2026-09-23 根因修复后所有连发按钮
                        //  都传交互源。此分支仅为 longPressRepeat 的公共 API 语义保留）
                        source == null -> {
                            released = tryAwaitRelease()
                        }

                        // 非滚动容器：对齐官方「不延迟」路径——down 即发 Press，
                        // 抬起发 Release、被父级消费发 Cancel
                        !delayPressIndication -> {
                            val press = PressInteraction.Press(offset)
                            pendingPress = press
                            source.tryEmit(press)
                            released = tryAwaitRelease()
                            source.tryEmit(
                                if (released) {
                                    PressInteraction.Release(press)
                                } else {
                                    PressInteraction.Cancel(press)
                                }
                            )
                            pendingPress = null
                        }

                        // 滚动容器内：完整复刻官方 ClickableNode.handlePressInteraction
                        // （foundation 1.11.2 源码）的延迟发射语义（v2026-09-23）——
                        // ① delayJob 延迟 DEFAULT_PRESS_INDICATION_DELAY_MS 再发 Press；
                        // ② 延迟期间手势就结束：
                        //    · 被父级消费（滑动起手）→ 什么都不发 → **滑动不闪小波纹**；
                        //    · 正常抬起（快速点按）→ 瞬时补发 Press+Release，点按仍有波纹；
                        // ③ 延迟已过（Press 已发）→ 抬起发 Release、取消发 Cancel。
                        else -> coroutineScope {
                            val delayJob = launch {
                                delay(DEFAULT_PRESS_INDICATION_DELAY_MS)
                                val press = PressInteraction.Press(offset)
                                pendingPress = press
                                source.tryEmit(press)
                            }
                            released = tryAwaitRelease()
                            if (delayJob.isActive) {
                                delayJob.cancelAndJoin()
                                if (released) {
                                    val press = PressInteraction.Press(offset)
                                    source.tryEmit(press)
                                    source.tryEmit(PressInteraction.Release(press))
                                }
                                // 取消（滑动）分支：官方注释「No else branch」——什么都不发
                            } else {
                                pendingPress?.let { press ->
                                    source.tryEmit(
                                        if (released) {
                                            PressInteraction.Release(press)
                                        } else {
                                            PressInteraction.Cancel(press)
                                        }
                                    )
                                }
                                pendingPress = null
                            }
                        }
                    }

                    /**
                     * 短按：用户在 initialDelayMs 内抬起（连发未触发）→ 补一次单击动作，
                     * 行为与其他普通按钮一致（动作在抬起时执行，而非按下时）。
                     * 长按：连发已覆盖动作，此处不再重复。
                     * 滑动/被消费：tryAwaitRelease 返回 false → 不触发动作（修复误触）。
                     */
                    if (released && !repeated) {
                        currentAction()
                    }
                } finally {
                    repeatJobRef[0]?.cancel()
                    repeatJobRef[0] = null

                    /**
                     * 收尾兜底（v2026-09-22 引入、v2026-09-23 改为 Cancel）：协程被杀
                     * （enabled 翻转重启 pointerInput / 组合离开，发生在挂起点）时，
                     * 若 Press 已发但尚未收尾 → 补发 Cancel。Cancel 与 Release 在
                     * ripple 视觉上等价，圆圈照常消散、永不残留。
                     */
                    pendingPress?.let { press ->
                        interactionSource?.tryEmit(PressInteraction.Cancel(press))
                    }
                }
            }
        )
    }
}

/**
 * 带「长按连发」能力的图标按钮（v2026-09-17；v2026-09-23 根因修复不再包 IconButton）
 *
 * 动机：软键盘没有 Ctrl+Z，撤销/重做要连续回退多步时逐次点击很累。
 * 手势与节拍逻辑见 [Modifier.longPressRepeat]。
 *
 * ## ⚠️ 为什么不能再用 IconButton 包裹（v2026-09-23 根因修复）
 *
 * 撤销/重做按钮自 v2026-09-22（b8140c5e）起「点了没反应」，根因是一处双层手势互斥：
 * - 本组件原结构 = `IconButton(onClick = {})` 内层 + 外层 `longPressRepeat`；
 * - longPressRepeat 的底层在 b8140c5e 由 `awaitFirstDown(requireUnconsumed = false)`
 *   重写为 `detectTapGestures`——后者内部的 `awaitFirstDown()` 是**默认
 *   `requireUnconsumed = true`**；
 * - 而 IconButton 内层的 clickable 会在 down 时**消费事件**（本项目 `PressFeedback.kt`
 *   同款踩坑记录，旧实现正是为此才写 `requireUnconsumed = false`）；
 * - 于是外层手势**永远等不到未消费的 down** → `onPress` 不进入 → 动作永不触发。
 *   内层 clickable 的波纹照常播放——按钮看着亮、点着有波纹、却毫无动作
 *   （真机 2026-09-23 实证：logcat 连桥命令日志都没有）。
 * - Nest/Unnest（`RiFormatButton`）在同一提交里已改为 Box 自持波纹（无内层 clickable），
 *   所以它们不受影响——这正是"只有撤销坏了"的原因。
 *
 * 修法：与 `RiFormatButton` 对齐——去掉内层 clickable，改
 * `Box + indication(interactionSource, ripple())`，手势成为 down 的唯一消费者、
 * 并全权驱动波纹。顶栏 Row 不在 horizontalScroll 内，无需 `delayPressIndication`
 * （down 即发 Press，波纹时机与原 IconButton 一致）。
 *
 * @param icon 图标（如 `Icons.AutoMirrored.Filled.Undo`）
 * @param contentDescription 无障碍描述
 * @param onAction 单次动作（撤销/重做）；短按抬起时被调用一次，长按时被连续调用
 * @param enabled 是否可交互；false 时置灰且完全不响应手势（无波纹、无动作）
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
    /** 波纹交互源：由 [Modifier.longPressRepeat] 全权 emit（Press/Release/Cancel 兜底齐全） */
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            /** 与 IconButtonImpl 同款顺序：先保 48dp 交互目标下限，再定视觉尺寸 */
            .minimumInteractiveComponentSize()
            .size(buttonSize)
            .clip(CircleShape)
            .indication(interactionSource, ripple())
            .longPressRepeat(
                onAction = onAction,
                enabled = enabled,
                canRepeat = canRepeat,
                interactionSource = interactionSource,
            ),
        contentAlignment = Alignment.Center
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
