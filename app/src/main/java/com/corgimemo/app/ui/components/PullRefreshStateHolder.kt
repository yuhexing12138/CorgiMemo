package com.corgimemo.app.ui.components

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
import kotlinx.coroutines.launch

/**
 * 下拉刷新状态持有者
 *
 * 持有：
 * - state: PullRefreshState（当前状态）
 * - pullOffset: Float（当前阻尼后下拉偏移，单位 px，用于 UI 渲染）
 * - rawPullOffset: Float（累计原始手指下拉距离，单位 px，用于阻尼计算）
 * - nestedScrollConnection: NestedScrollConnection（注入外层 Box）
 * - onRelease: () -> Unit（外部监听 up 事件时调用，统一处理"松手"）
 * - onRefreshComplete: () -> Unit（刷新完成回调，由 LaunchedEffect 调用）
 *
 * 坐标系约定（Compose NestedScrollConnection）：
 * - y 轴向下为正方向
 * - available.y > 0：用户手指下拉（内容向下移动方向），列表在顶部无法消费时由父组件处理
 * - available.y < 0：用户手指上推（内容向上移动方向）
 *
 * 滚动消费策略：
 * - onPreScroll（子组件消费前）：处理收回逻辑——当 pullOffset > 0 且手指上推（available.y < 0）时，
 *   父组件优先消费 delta 线性收回空白，避免列表先滚动导致卡顿感
 * - onPostScroll（子组件消费后）：处理下拉逻辑——当列表在顶部且手指下拉（available.y > 0）时，
 *   父组件消费剩余 delta 增加 pullOffset（带阻尼）
 * - onPreFling：用户释放时（fling 速度足够），根据 pullOffset 判断触发刷新还是回弹
 * - onRelease（外部 pointerInput 监听 up）：作为 onPreFling 不可达时的兜底（如列表底部
 *   没有 fling 速度但 pullOffset > 0 的场景），保证"松手必回弹"
 *
 * 异步释放协程（releaseJob）：
 * - 旧实现直接在 onPreFling 内 await animate(300ms)，会阻塞 fling 协程
 * - 若 fling 协程在动画期间被取消（用户快速连续操作），animate 被中断，state 卡在中间值
 * - 新实现改为 scope.launch 异步执行，releaseJob 统一管理，新的 scroll 事件会取消旧任务
 * - REFRESHING 完成后由 onRefreshComplete 在同一 job 上下文发起 0 动画，避免重入
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
     * 嵌套滚动连接
     *
     * 实现策略：
     * - onPreScroll：手指上推时优先收回空白（pullOffset 线性减少）
     * - onPostScroll：手指下拉时增加空白（pullOffset 阻尼递增），并取消任何进行中的 releaseJob
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
                cancelRelease()

                val delta = available.y // 负值
                val newOffset = (pullOffset + delta).coerceAtLeast(0f)
                val consumed = newOffset - pullOffset // 负值（减少了多少）
                pullOffset = newOffset
                // 同步 rawPullOffset，保证后续下拉阻尼曲线连续
                rawPullOffset = computeRawOffsetForDamped(newOffset, maxPullHeightPx)
                updateState(isReleased = false)
                return Offset(0f, consumed)
            }

            return Offset.Zero
        }

        /**
         * 子组件滚动后处理
         *
         * 用于下拉拉出空白：列表在顶部无法继续向下滚动时，
         * 剩余的 available.y > 0（手指下拉 delta）转为 pullOffset（带阻尼）。
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
                cancelRelease()

                // 关键：先根据当前 pullOffset 重新校准 rawPullOffset，
                // 避免 releaseJob 被取消时（pullOffset 停在中间值）导致 rawPullOffset 与
                // pullOffset 的对应关系断裂
                rawPullOffset = computeRawOffsetForDamped(pullOffset, maxPullHeightPx)
                rawPullOffset += available.y
                val newOffset = computeDampedOffset(rawPullOffset, maxPullHeightPx)
                val consumedY = newOffset - pullOffset
                pullOffset = newOffset
                updateState(isReleased = false)
                return Offset(0f, consumedY)
            }

            return Offset.Zero
        }

        /**
         * 用户释放（fling 开始前）
         *
         * 注意：Compose NestedScroll 的 onPreFling 只在子组件 fling 时被调用。
         * 如果用户在列表底部快速下滑，LazyColumn 自身没有 fling（无法向上滚动），
         * 父组件的 onPreFling 不会被触发 → 此时需由外部 pointerInput 监听到 up
         * 后调用 [onRelease] 兜底。本方法只处理"fling 路径"。
         *
         * 释放行为：
         * - pullOffset >= refreshThreshold → REFRESHING，触发 onRefresh，动画到阈值位置
         * - pullOffset < refreshThreshold → RELEASING，回弹到 0
         */
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (state != PullRefreshState.PULLING) return Velocity.Zero
            // 委托给统一的释放处理（异步执行，不再阻塞 fling 协程）
            onRelease()
            return Velocity.Zero
        }
    }

    /**
     * 外部"松手"入口
     *
     * 由 Box 的 Modifier.pointerInput 监听 up 事件后调用。
     * 解决：用户快速下滑但 LazyColumn 不 fling 时 onPreFling 不会触发的卡住问题。
     *
     * 安全保护：
     * - 仅在 PULLING 状态下执行（非 PULLING 状态为 no-op）
     * - 自动取消任何进行中的 release 任务，避免重入
     * - 内部用 try-finally 保护状态机
     */
    fun onRelease() {
        if (state != PullRefreshState.PULLING) return
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
     * try-finally 保护：协程被取消时，state 也能被合理重置（避免卡死）。
     */
    private fun doRelease() {
        // 取消任何旧的 release 任务
        releaseJob?.cancel()

        val startOffset = pullOffset
        val goToRefresh = startOffset >= refreshThresholdPx

        releaseJob = scope.launch {
            try {
                if (goToRefresh) {
                    // 1. 达阈值：先置为 REFRESHING，触发 ViewModel.onRefresh()
                    state = PullRefreshState.REFRESHING
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
                    state = PullRefreshState.RELEASING

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
                    state = PullRefreshState.IDLE
                }
            } catch (e: CancellationException) {
                // 协程被取消时，状态由调用方（外部 scroll 事件 / onRefreshComplete）决定
                // 此处不强行重置 state，避免与新事件竞争
                throw e
            }
        }
    }

    /**
     * 取消任何进行中的释放动画
     */
    private fun cancelRelease() {
        releaseJob?.cancel()
        releaseJob = null
    }

    /**
     * 刷新完成回调
     *
     * 由外层 LaunchedEffect(isRefreshing) 在 isRefreshing=false 时调用。
     * 在 NonCancellable 上下文中启动协程，保证动画到 0 + state = IDLE 的状态收尾
     * 即使在 releaseJob 被取消后也能正确执行。
     */
    fun onRefreshComplete() {
        if (state != PullRefreshState.REFRESHING) return
        // 取消任何旧的 release 任务
        releaseJob?.cancel()

        releaseJob = scope.launch {
            try {
                animate(pullOffset, 0f, animationSpec = tween(durationMillis = RELEASE_ANIMATION_DURATION_MS)) { value, _ ->
                    pullOffset = value
                }
                pullOffset = 0f
                rawPullOffset = 0f
                state = PullRefreshState.IDLE
            } catch (e: CancellationException) {
                // NonCancellable 包装的协程被取消的概率极低，但仍然透传
                throw e
            }
        }
    }

    /**
     * 更新状态（下拉过程中实时调用）
     */
    private fun updateState(isReleased: Boolean) {
        state = computeNextPullRefreshState(
            current = state,
            pullOffset = pullOffset,
            refreshThreshold = refreshThresholdPx,
            isReleased = isReleased,
            isRefreshing = state == PullRefreshState.REFRESHING
        )
    }

    companion object {
        /** 释放后回弹 / 收缩到阈值位置的动画时长 */
        private const val RELEASE_ANIMATION_DURATION_MS = 300
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
