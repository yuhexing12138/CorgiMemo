package com.corgimemo.app.ui.components

import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 下拉刷新状态持有者
 *
 * 持有：
 * - state: PullRefreshState（当前状态）
 * - pullOffset: Float（当前阻尼后下拉偏移，单位 px，用于 UI 渲染）
 * - rawPullOffset: Float（累计原始手指下拉距离，单位 px，用于阻尼计算）
 * - nestedScrollConnection: NestedScrollConnection（注入外层 Box）
 * - onRelease: () -> Unit（外部"松手"入口，处理 PULLING/RELEASING 卡死场景）
 * - onRefreshComplete: () -> Unit（刷新完成回调，由 LaunchedEffect 调用）
 *
 * 坐标系约定（Compose NestedScrollConnection）：
 * - y 轴向下为正方向
 * - available.y > 0：用户手指下拉（内容向下移动方向），列表在顶部无法消费时由父组件处理
 * - available.y < 0：用户手指上推（内容向上移动方向）
 *
 * 滚动消费策略：
 * - onPreScroll：手指上推时优先收回空白（pullOffset 线性减少）
 * - onPostScroll：手指下拉时增加空白（pullOffset 阻尼递增），末尾启动 idleTimer
 * - onPreFling：用户释放且 fling 速度足够时，根据 pullOffset 触发刷新或回弹
 * - onRelease：外部"松手"入口，处理 PULLING/RELEASING 卡死（idleTimer / pointerInput up 兜底调用）
 * - idleTimer（内部）：onPostScroll 后 200ms 无新 scroll 事件则强制 onRelease
 *   是最根本的自动回弹机制，不依赖任何外部 LaunchedEffect/pointerInput
 *
 * 关键修复点（v3）：
 * 1. cancelRelease() 在 RELEASING 状态下必须把 state 重置为 PULLING，否则状态机卡死
 *    （computeNextPullRefreshState 在 RELEASING 状态下只能靠 animate 完成后转 IDLE）
 * 2. onRelease() 内部同时处理 PULLING 和 RELEASING 状态（卡死时强制恢复）
 * 3. 内部 idleTimer 完全自洽：不依赖外部 LaunchedEffect/pointerInput，仅在 PullRefreshStateHolder 内部
 *
 * @param maxPullHeightPx 最大下拉高度（px）
 * @param refreshThresholdPx 刷新阈值（px）
 * @param onRefresh 触发刷新回调
 * @param scope 协程作用域
 */
class PullRefreshStateHolder(
    val maxPullHeightPx: Float,
    val refreshThresholdPx: Float,
    private val onRefresh: () -> Unit,
    private val scope: CoroutineScope
) {
    /** 当前状态 */
    var state: PullRefreshState by mutableStateOf(PullRefreshState.IDLE)
        private set

    /** 当前阻尼后下拉偏移（px），用于 UI 渲染（空白高度、柯基位置） */
    var pullOffset: Float by mutableFloatStateOf(0f)
        private set

    /** 累计原始手指下拉距离（px），未阻尼，用于阻尼曲线计算 */
    private var rawPullOffset: Float = 0f

    /**
     * 释放后回弹/刷新到目标值的协程句柄
     *
     * 统一管理所有"松手 → 动画到目标值"的协程。新 scroll 事件会取消进行中的 releaseJob，
     * 避免旧动画与新下拉手势冲突。onRefreshComplete 也复用同一字段。
     */
    private var releaseJob: Job? = null

    /**
     * Idle timer 协程：在 onPostScroll 末尾启动，200ms 内无新 scroll 事件则强制 onRelease。
     *
     * 这是**根本性自动回弹机制**：
     * - 不依赖外部 LaunchedEffect
     * - 不依赖 onPreFling（onPreFling 只在子组件 fling 时触发）
     * - 不依赖 pointerInput up 事件（部分设备/版本传递不可靠）
     * - 完全在 PullRefreshStateHolder 内部
     *
     * 协作规则：
     * - 每次 onPostScroll 末尾 scheduleIdleTimer（取消旧的 + 启动新的）
     * - 任何 pullOffset / state 变化（动画进行中）也会重置 timer
     * - 用户继续操作时 timer 不断重启，永不触发
     * - 用户停止操作 200ms 后 timer 触发 → onRelease → 自动回弹
     */
    private var idleTimerJob: Job? = null

    /**
     * 嵌套滚动连接
     *
     * 实现策略：
     * - onPreScroll：手指上推时优先收回空白（pullOffset 线性减少），取消 idle timer
     * - onPostScroll：手指下拉时增加空白（pullOffset 阻尼递增），末尾 schedule idle timer
     * - onPreFling：用户释放且 fling 速度足够时，根据 pullOffset 触发刷新或回弹
     *
     * 在 REFRESHING 状态下锁定，不响应任何手势。
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        /**
         * 子组件滚动前处理
         *
         * 用于收回空白：当 pullOffset > 0（空白已露出）且用户手指上推（available.y < 0）时，
         * 父组件优先消费上推 delta 线性收回空白，剩余再给列表。
         */
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (state == PullRefreshState.REFRESHING) return Offset.Zero

            // 手指上推（available.y < 0），且已露出空白，优先收回
            if (available.y < 0f && pullOffset > 0f) {
                // 取消任何进行中的 release 动画（用户主动接管）
                cancelRelease(reason = "onPreScroll: 用户上推")
                // 取消 idle timer（用户在继续操作）
                cancelIdleTimer(reason = "onPreScroll: 用户上推")

                val delta = available.y // 负值
                val newOffset = (pullOffset + delta).coerceAtLeast(0f)
                val consumed = newOffset - pullOffset // 负值（减少了多少）
                pullOffset = newOffset
                // 同步 rawPullOffset，保证后续下拉阻尼曲线连续
                rawPullOffset = computeRawOffsetForDamped(newOffset, maxPullHeightPx)
                val prevState = state
                updateState(isReleased = false)
                logStateChange("onPreScroll", prevState, state, pullOffset)
                return Offset(0f, consumed)
            }

            return Offset.Zero
        }

        /**
         * 子组件滚动后处理
         *
         * 用于下拉拉出空白：列表在顶部无法继续向下滚动时，
         * 剩余的 available.y > 0（手指下拉 delta）转为 pullOffset（带阻尼）。
         *
         * 末尾启动 idle timer：200ms 内无新 scroll 事件则强制自动回弹。
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (state == PullRefreshState.REFRESHING) return Offset.Zero

            // 手指下拉（available.y > 0），列表无法继续向下滚动，消费剩余 delta 拉出空白
            if (available.y > 0f) {
                // 取消任何进行中的 release 动画（用户继续下拉）
                cancelRelease(reason = "onPostScroll: 用户继续下拉")
                // 取消 idle timer
                cancelIdleTimer(reason = "onPostScroll: 用户继续下拉")

                // 关键：先根据当前 pullOffset 重新校准 rawPullOffset，
                // 避免 releaseJob 被取消时（pullOffset 停在中间值）导致 rawPullOffset 与
                // pullOffset 的对应关系断裂
                rawPullOffset = computeRawOffsetForDamped(pullOffset, maxPullHeightPx)
                rawPullOffset += available.y
                val newOffset = computeDampedOffset(rawPullOffset, maxPullHeightPx)
                val consumedY = newOffset - pullOffset
                pullOffset = newOffset
                val prevState = state
                updateState(isReleased = false)
                logStateChange("onPostScroll", prevState, state, pullOffset)

                // 启动 idle timer：200ms 内无新 scroll 事件则强制 onRelease
                scheduleIdleTimer()

                return Offset(0f, consumedY)
            }

            return Offset.Zero
        }

        /**
         * 用户释放（fling 开始前）
         *
         * 注意：Compose NestedScroll 的 onPreFling 只在子组件 fling 时被调用。
         * 如果用户在列表底部快速下滑，LazyColumn 自身没有 fling（无法向上滚动），
         * 父组件的 onPreFling 不会被触发 → 此时需由 idleTimer / 外部 pointerInput
         * 兜底调用 [onRelease]。本方法只处理"fling 路径"。
         *
         * 释放行为：
         * - pullOffset >= refreshThreshold → REFRESHING，触发 onRefresh，动画到阈值位置
         * - pullOffset < refreshThreshold → RELEASING，回弹到 0
         */
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (state != PullRefreshState.PULLING) {
                Log.d(TAG, "onPreFling: state=$state, 忽略（非 PULLING）")
                return Velocity.Zero
            }
            Log.d(TAG, "onPreFling: 触发 release, pullOffset=$pullOffset")
            // 取消 idle timer（即将手动处理 release）
            cancelIdleTimer(reason = "onPreFling: 主动处理 release")
            // 委托给统一的释放处理（异步执行，不再阻塞 fling 协程）
            onRelease()
            return Velocity.Zero
        }
    }

    /**
     * 外部"松手"入口
     *
     * 由以下调用：
     * 1. Box 的 Modifier.pointerInput 监听 up 事件
     * 2. 内部 idleTimer 定时到期
     * 3. onPreFling 内部
     *
     * 关键改进（v3）：同时处理 PULLING 和 RELEASING 状态。
     * - PULLING：正常处理（达阈值则 REFRESHING，未达则 RELEASING）
     * - RELEASING：**卡死恢复路径** - cancelRelease 取消了 animate 但 state 仍为 RELEASING，
     *   必须先重置 state 为 PULLING，再走正常 release 流程
     *
     * @param forceResetFromReleasing true 表示从 RELEASING 强制恢复（idleTimer / 卡死恢复）
     */
    fun onRelease(forceResetFromReleasing: Boolean = false) {
        // 关键修复：处理 RELEASING 卡死场景
        if (state == PullRefreshState.RELEASING) {
            if (!forceResetFromReleasing) {
                // 正常 RELEASING 状态下不重复调用 onRelease（动画已经在执行）
                Log.d(TAG, "onRelease: state=RELEASING，跳过（动画中）")
                return
            }
            // forceResetFromReleasing=true：从 RELEASING 强制恢复
            Log.w(TAG, "onRelease: 从 RELEASING 状态强制恢复，重置为 PULLING")
            cancelRelease(reason = "onRelease: 强制恢复")
            state = PullRefreshState.PULLING
            doRelease()
            return
        }

        if (state != PullRefreshState.PULLING) {
            Log.d(TAG, "onRelease: state=$state, 忽略（非 PULLING/RELEASING）")
            return
        }
        Log.d(TAG, "onRelease: state=PULLING, pullOffset=$pullOffset")
        doRelease()
    }

    /**
     * 内部统一"松手"处理
     *
     * 行为分支（与 onPreFling 旧实现完全一致）：
     * - pullOffset >= refreshThresholdPx → REFRESHING + onRefresh + 动画到阈值
     * - pullOffset < refreshThresholdPx → RELEASING + 动画到 0
     *
     * 异步执行（scope.launch）：不阻塞调用方（onPreFling / 外部 up 回调）。
     * try-catch CancellationException 透传（不抢 state 重置权）。
     */
    private fun doRelease() {
        // 取消任何旧的 release 任务
        releaseJob?.cancel()
        // 取消 idle timer（即将手动处理 release）
        cancelIdleTimer(reason = "doRelease: 主动处理 release")

        val startOffset = pullOffset
        val goToRefresh = startOffset >= refreshThresholdPx

        Log.d(TAG, "doRelease: startOffset=$startOffset, goToRefresh=$goToRefresh")

        releaseJob = scope.launch {
            try {
                if (goToRefresh) {
                    // 1. 达阈值：先置为 REFRESHING，触发 ViewModel.onRefresh()
                    val prevState = state
                    state = PullRefreshState.REFRESHING
                    logStateChange("doRelease", prevState, state, pullOffset)
                    onRefresh()

                    // 2. 动画到阈值位置（300ms）
                    animate(
                        startOffset,
                        refreshThresholdPx,
                        animationSpec = tween(durationMillis = RELEASE_ANIMATION_DURATION_MS)
                    ) { value, _ ->
                        pullOffset = value
                    }

                    // 3. 同步 rawPullOffset，保证回弹后再次下拉的阻尼曲线连续
                    rawPullOffset = computeRawOffsetForDamped(refreshThresholdPx, maxPullHeightPx)
                } else {
                    // 1. 未达阈值：置为 RELEASING
                    val prevState = state
                    state = PullRefreshState.RELEASING
                    logStateChange("doRelease", prevState, state, pullOffset)

                    // 2. 动画到 0
                    animate(
                        startOffset,
                        0f,
                        animationSpec = tween(durationMillis = RELEASE_ANIMATION_DURATION_MS)
                    ) { value, _ ->
                        pullOffset = value
                    }

                    // 3. 收尾：归零
                    pullOffset = 0f
                    rawPullOffset = 0f
                    val prevState2 = state
                    state = PullRefreshState.IDLE
                    logStateChange("doRelease 收尾", prevState2, state, pullOffset)
                }
            } catch (e: CancellationException) {
                // 关键：协程被取消时，如果当前是 RELEASING 状态，把 state 拉回 PULLING，
                // 避免状态机卡死（因为 RELEASING 只能靠 animate 完成转 IDLE）
                if (state == PullRefreshState.RELEASING) {
                    Log.w(TAG, "doRelease: RELEASING 动画被取消，强制重置 state 为 PULLING")
                    state = PullRefreshState.PULLING
                }
                throw e
            }
        }
    }

    /**
     * 取消任何进行中的释放动画
     *
     * 关键修复（v3）：在 RELEASING 状态取消时，必须把 state 重置为 PULLING，
     * 否则状态机卡死（computeNextPullRefreshState 在 RELEASING 状态下只能
     * 靠 animate 完成后转 IDLE，外部没有触发会让 state 转 IDLE 的入口）。
     */
    private fun cancelRelease(reason: String = "未指定") {
        val wasReleasing = state == PullRefreshState.RELEASING
        val wasRefreshing = state == PullRefreshState.REFRESHING
        releaseJob?.cancel()
        releaseJob = null
        if (wasReleasing) {
            Log.w(TAG, "cancelRelease(reason=$reason): RELEASING 状态被取消，强制重置 state 为 PULLING")
            state = PullRefreshState.PULLING
        } else if (wasRefreshing) {
            // REFRESHING 状态不应该被 cancelRelease 取消（应该由 onRefreshComplete 处理）
            Log.w(TAG, "cancelRelease(reason=$reason): 注意，REFRESHING 状态被取消，这可能不应该发生")
        }
    }

    /**
     * 调度 idle timer：200ms 内无新 scroll 事件则强制 onRelease
     *
     * 这是**根本性自动回弹机制**。调用方：
     * - onPostScroll 末尾（用户每次下拉后启动）
     * - doRelease 内部取消（即将手动处理 release）
     * - onPreFling 内部取消（即将手动处理 release）
     */
    private fun scheduleIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = scope.launch {
            try {
                delay(IDLE_TIMEOUT_MS)
                // 200ms 内无新 scroll 事件
                Log.d(TAG, "idleTimer: 200ms 无新事件, state=$state, pullOffset=$pullOffset")
                if (state == PullRefreshState.PULLING || state == PullRefreshState.RELEASING) {
                    onRelease(forceResetFromReleasing = true)
                }
            } catch (e: CancellationException) {
                // timer 被取消是正常的（用户继续操作或主动处理）
                throw e
            }
        }
    }

    /**
     * 取消 idle timer
     */
    private fun cancelIdleTimer(reason: String = "未指定") {
        if (idleTimerJob?.isActive == true) {
            Log.d(TAG, "cancelIdleTimer(reason=$reason)")
        }
        idleTimerJob?.cancel()
        idleTimerJob = null
    }

    /**
     * 刷新完成回调
     *
     * 由外层 LaunchedEffect(isRefreshing) 在 isRefreshing=false 时调用。
     * 取消任何进行中的 releaseJob，启动 0 动画到 IDLE。
     */
    fun onRefreshComplete() {
        if (state != PullRefreshState.REFRESHING) return
        Log.d(TAG, "onRefreshComplete: 启动回弹到 0 动画")
        // 取消任何旧的 release 任务
        releaseJob?.cancel()
        // 取消 idle timer
        cancelIdleTimer(reason = "onRefreshComplete")

        releaseJob = scope.launch {
            try {
                animate(
                    pullOffset,
                    0f,
                    animationSpec = tween(durationMillis = RELEASE_ANIMATION_DURATION_MS)
                ) { value, _ ->
                    pullOffset = value
                }
                pullOffset = 0f
                rawPullOffset = 0f
                val prevState = state
                state = PullRefreshState.IDLE
                logStateChange("onRefreshComplete", prevState, state, pullOffset)
            } catch (e: CancellationException) {
                if (state == PullRefreshState.REFRESHING) {
                    Log.w(TAG, "onRefreshComplete: REFRESHING 动画被取消，强制重置 state 为 IDLE")
                    state = PullRefreshState.IDLE
                }
                throw e
            }
        }
    }

    /**
     * 更新状态（下拉过程中实时调用）
     */
    private fun updateState(isReleased: Boolean) {
        val prevState = state
        state = computeNextPullRefreshState(
            current = state,
            pullOffset = pullOffset,
            refreshThreshold = refreshThresholdPx,
            isReleased = isReleased,
            isRefreshing = state == PullRefreshState.REFRESHING
        )
        // 注意：这里不 log，由调用方在 context 中 log
        if (prevState != state) {
            Log.d(TAG, "updateState: $prevState -> $state, pullOffset=$pullOffset, isReleased=$isReleased")
        }
    }

    private fun logStateChange(context: String, prev: PullRefreshState, current: PullRefreshState, offset: Float) {
        if (prev != current) {
            Log.d(TAG, "[$context] state: $prev -> $current, pullOffset=$offset")
        }
    }

    companion object {
        /** 释放后回弹 / 收缩到阈值位置的动画时长 */
        private const val RELEASE_ANIMATION_DURATION_MS = 300

        /** Idle timer 超时：200ms 内无新 scroll 事件则强制自动回弹 */
        private const val IDLE_TIMEOUT_MS = 200L

        /** 调试日志 tag */
        private const val TAG = "PullRefresh"
    }
}

/**
 * 记住 PullRefreshStateHolder
 *
 * @param maxPullHeight 最大下拉高度（默认 100dp）
 * @param refreshThreshold 刷新阈值（默认 60dp）
 * @param onRefresh 触发刷新回调
 */
@Composable
fun rememberPullRefreshStateHolder(
    maxPullHeight: Dp = 100.dp,
    refreshThreshold: Dp = 60.dp,
    onRefresh: () -> Unit
): PullRefreshStateHolder {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val maxPullHeightPx = with(density) { maxPullHeight.toPx() }
    val refreshThresholdPx = with(density) { refreshThreshold.toPx() }

    return remember(maxPullHeightPx, refreshThresholdPx) {
        PullRefreshStateHolder(
            maxPullHeightPx = maxPullHeightPx,
            refreshThresholdPx = refreshThresholdPx,
            onRefresh = onRefresh,
            scope = scope
        )
    }
}
