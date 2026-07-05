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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 下拉刷新状态持有者
 *
 * 持有：
 * - state: PullRefreshState（当前状态）
 * - pullOffset: Float（当前阻尼后下拉偏移，单位 px，用于 UI 渲染）
 * - rawPullOffset: Float（累计原始手指下拉距离，单位 px，用于阻尼计算）
 * - nestedScrollConnection: NestedScrollConnection（注入外层 Box）
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
     * 嵌套滚动连接
     *
     * 实现策略：
     * - onPreScroll：手指上推时优先收回空白（pullOffset 线性减少）
     * - onPostScroll：手指下拉时增加空白（pullOffset 阻尼递增）
     * - onPreFling：用户释放时，根据 pullOffset 判断触发刷新还是回弹
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
         * 根据 pullOffset 判断：
         * - pullOffset >= refreshThreshold → REFRESHING，触发 onRefresh，动画到阈值位置
         * - pullOffset < refreshThreshold → RELEASING，回弹到 0
         */
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (state != PullRefreshState.PULLING) return Velocity.Zero

            if (pullOffset >= refreshThresholdPx) {
                state = PullRefreshState.REFRESHING
                onRefresh()
                animate(pullOffset, refreshThresholdPx, animationSpec = tween(durationMillis = 300)) { value, _ ->
                    pullOffset = value
                }
                rawPullOffset = computeRawOffsetForDamped(refreshThresholdPx, maxPullHeightPx)
            } else {
                state = PullRefreshState.RELEASING
                animate(pullOffset, 0f, animationSpec = tween(durationMillis = 300)) { value, _ ->
                    pullOffset = value
                }
                pullOffset = 0f
                rawPullOffset = 0f
                state = PullRefreshState.IDLE
            }
            return Velocity.Zero
        }
    }

    /**
     * 刷新完成回调
     *
     * 由外层 LaunchedEffect(isRefreshing) 在 isRefreshing=false 时调用。
     * 启动协程将 pullOffset 动画到 0，并将状态置为 IDLE。
     */
    fun onRefreshComplete() {
        if (state != PullRefreshState.REFRESHING) return
        scope.launch {
            animate(pullOffset, 0f, animationSpec = tween(durationMillis = 300)) { value, _ ->
                pullOffset = value
            }
            pullOffset = 0f
            rawPullOffset = 0f
            state = PullRefreshState.IDLE
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
