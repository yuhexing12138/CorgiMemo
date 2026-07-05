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
import kotlin.math.min

/**
 * 下拉刷新状态持有者
 *
 * 持有：
 * - state: PullRefreshState（当前状态）
 * - pullOffset: Float（当前下拉偏移，单位 px，已阻尼）
 * - nestedScrollConnection: NestedScrollConnection（注入外层 Box）
 * - onRefreshComplete: () -> Unit（刷新完成回调，由 LaunchedEffect 调用）
 *
 * 不持有 isRefreshing，由 ViewModel 提供，外层通过 LaunchedEffect 监听并调用 onRefreshComplete。
 *
 * 设计说明：
 * - pullOffset 用 mutableFloatStateOf 存储，可在同步的 onPostScroll 中实时更新
 * - 动画用 animate suspend 函数（在 onPreFling / onRefreshComplete 中调用）
 *
 * 坐标系约定（Compose NestedScrollConnection）：
 * - available.y < 0：用户下拉（列表在顶部，无法继续向下滚动）
 * - available.y > 0：用户上拉（列表在底部，无法继续向上滚动）
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

    /** 当前下拉偏移（px，已阻尼） */
    var pullOffset: Float by mutableFloatStateOf(0f)
        private set

    /**
     * 嵌套滚动连接
     *
     * 实现策略：
     * - onPostScroll：列表在顶部无法继续向下滚动时，剩余的 available.y < 0 转为 pullOffset
     * - onPreFling：用户释放时，根据 pullOffset 判断触发刷新还是回弹
     *
     * 在 REFRESHING 状态下锁定，不响应任何手势。
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        /**
         * 列表滚动后处理
         *
         * available.y < 0：用户下拉，列表无法继续滚动 → 增加 pullOffset
         * available.y > 0：用户上拉，且 pullOffset > 0 → 减少 pullOffset
         */
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (state == PullRefreshState.REFRESHING) return Offset.Zero

            // 用户下拉（available.y < 0）
            if (available.y < 0f) {
                val rawDelta = -available.y  // 转为正数
                val newRawOffset = pullOffset + rawDelta
                val newDampedOffset = computeDampedOffset(newRawOffset, maxPullHeightPx)
                val actualConsumed = newDampedOffset - pullOffset
                pullOffset = newDampedOffset  // 同步更新
                updateState(isReleased = false)
                return Offset(0f, -actualConsumed)
            }

            // 用户上拉（available.y > 0），且 pullOffset > 0，回收 pullOffset
            if (available.y > 0f && pullOffset > 0f) {
                val consumed = min(available.y, pullOffset)
                pullOffset = pullOffset - consumed
                updateState(isReleased = false)
                return Offset(0f, consumed)
            }

            return Offset.Zero
        }

        /**
         * 用户释放（fling 开始前）
         *
         * 根据 pullOffset 判断：
         * - pullOffset >= refreshThreshold → REFRESHING，触发 onRefresh，动画到阈值
         * - pullOffset < refreshThreshold → RELEASING，回弹到 0
         */
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (state != PullRefreshState.PULLING) return Velocity.Zero

            if (pullOffset >= refreshThresholdPx) {
                // 达阈值，触发刷新
                state = PullRefreshState.REFRESHING
                onRefresh()
                // 动画到阈值位置
                animate(pullOffset, refreshThresholdPx, animationSpec = tween(durationMillis = 300)) { value, _ ->
                    pullOffset = value
                }
            } else {
                // 未达阈值，回弹
                state = PullRefreshState.RELEASING
                animate(pullOffset, 0f, animationSpec = tween(durationMillis = 300)) { value, _ ->
                    pullOffset = value
                }
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
            state = PullRefreshState.IDLE
        }
    }

    /**
     * 更新状态（下拉过程中实时调用）
     *
     * @param isReleased 用户是否释放
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
