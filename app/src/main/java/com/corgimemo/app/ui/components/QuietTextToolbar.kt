package com.corgimemo.app.ui.components

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * 带「静默窗口」的 TextToolbar 装饰器（v2026-09-09）。
 *
 * 修复问题：长按文本弹出系统选择工具栏后，再次点击文本行时工具栏收不起来。
 *
 * 根因（两段机制叠加）：
 * 1. Compose 1.11 默认启用新 context menu 体系（`ComposeFoundationFlags
 *    .isNewContextMenuEnabled = true`），选择工具栏由 foundation 内部的
 *    toolbarRequester 弹出，**LocalTextToolbar（AndroidTextToolbar）被完全绕开**——
 *    我们在"按下即收起工具栏"的被动观察里调 `textToolbar.hide()` 等于对空气挥拳
 *    （其 status 恒为初始 Hidden，真机日志实证）。配合主入口把该 flag 切回 false，
 *    工具栏重新由 LocalTextToolbar 接管，hide() 才真正生效。
 * 2. 即便 hide() 生效，CoreTextField 的 `SelectionToolbarAndHandles` 在每次重组时
 *    都会在 `showFloatingToolbar == true` 的前提下重新调用 `showSelectionToolbar()`
 *    （foundation 源码 CoreTextField.kt L1046-1054）——"收起 → 下一帧重组重弹"
 *    的竞态让用户看到工具栏"从未消失"。
 *
 * 本装饰器负责第 2 段：**每次 [hide]（无论 status）都开启 [QUIET_WINDOW_MS]
 * 静默窗口**，窗口内 [showMenu] 直接忽略，拦住重组重弹。
 *
 * 时序安全性：窗口（200ms）小于系统长按阈值（默认 400ms），因此
 * "按下 → hide() 开窗 → 长按生效弹出"路径中弹出发生在窗口之后，正常弹出不受影响；
 * 而 "tap 收起 → 重组重弹"（实测 ~85ms 内）落在窗口内被拦截。
 */
class QuietTextToolbar(
    private val inner: TextToolbar,
) : TextToolbar {

    /** 静默窗口截止时刻（elapsedRealtime 单调时钟，不受系统时间调整影响）。 */
    private var quietUntilElapsedMs = 0L

    override val status: TextToolbarStatus
        get() = inner.status

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        /** 静默窗口内忽略 showMenu：拦住重组对刚收起的工具栏的重弹。 */
        if (SystemClock.elapsedRealtime() < quietUntilElapsedMs) return
        inner.showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested,
            onPasteRequested = onPasteRequested,
            onCutRequested = onCutRequested,
            onSelectAllRequested = onSelectAllRequested,
        )
    }

    override fun hide() {
        /**
         * v2026-09-09 二次修正：**无条件**开启静默窗口。
         *
         * 此前仅在 status == Shown（工具栏确实在显示）时开窗——但"重组重弹"的触发
         * 与 status 无关（showFloatingToolbar=true 时每次重组都会重新 show）：
         * 工具栏已收起状态下的再次点击（status=Hidden）不开窗 → 重组重弹不被拦截
         * → 表现为"奇数次点击收起、偶数次点击又弹出"的循环（真机日志实证）。
         * 无条件开窗后，每次 hide 都压住其后 200ms 内的重组重弹。
         */
        quietUntilElapsedMs = SystemClock.elapsedRealtime() + QUIET_WINDOW_MS
        inner.hide()
    }

    companion object {
        /**
         * 静默窗口时长。约束：
         * - 大于"tap 收起 → 重组重弹"的实测延迟（~85ms，tap 抬手→光标定位→重组）；
         * - 小于系统长按阈值（默认 400ms），否则长按选词的弹出会被误拦。
         * 取 200ms：对重弹留足余量，距长按阈值也有翻倍安全边距。
         */
        const val QUIET_WINDOW_MS = 200L
    }
}
