package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PullRefreshUtils 纯函数单元测试
 *
 * 覆盖：
 * - computeDampedOffset 阻尼曲线（5 用例）
 * - computeNextPullRefreshState 状态机转换（10 用例，Task 2 追加）
 */
class PullRefreshUtilsTest {

    // ===================== computeDampedOffset 阻尼曲线测试 =====================

    @Test
    fun `阻尼曲线_rawOffset为0时返回0`() {
        assertEquals(0f, computeDampedOffset(0f, 100f))
    }

    @Test
    fun `阻尼曲线_中段下拉返回阻尼后的值`() {
        // rawOffset=50, max=100
        // ratio = 0.5, dampingFactor = 1 - 0.25 = 0.75
        // result = 50 * 0.75 = 37.5
        assertEquals(37.5f, computeDampedOffset(50f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_达最大值时返回最大值`() {
        assertEquals(100f, computeDampedOffset(100f, 100f))
    }

    @Test
    fun `阻尼曲线_超过最大值时仍封顶`() {
        assertEquals(100f, computeDampedOffset(200f, 100f))
    }

    @Test
    fun `阻尼曲线_负值归零`() {
        assertEquals(0f, computeDampedOffset(-10f, 100f))
    }

    // ===================== computeNextPullRefreshState 状态机测试 =====================

    @Test
    fun `状态机_IDLE且无下拉时保持IDLE`() {
        assertEquals(
            PullRefreshState.IDLE,
            computeNextPullRefreshState(
                current = PullRefreshState.IDLE,
                pullOffset = 0f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_IDLE有下拉时进入PULLING`() {
        assertEquals(
            PullRefreshState.PULLING,
            computeNextPullRefreshState(
                current = PullRefreshState.IDLE,
                pullOffset = 10f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_PULLING达阈值释放进入REFRESHING`() {
        assertEquals(
            PullRefreshState.REFRESHING,
            computeNextPullRefreshState(
                current = PullRefreshState.PULLING,
                pullOffset = 70f,
                refreshThreshold = 60f,
                isReleased = true,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_PULLING未达阈值释放进入RELEASING`() {
        assertEquals(
            PullRefreshState.RELEASING,
            computeNextPullRefreshState(
                current = PullRefreshState.PULLING,
                pullOffset = 30f,
                refreshThreshold = 60f,
                isReleased = true,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_RELEASING回弹完成进入IDLE`() {
        assertEquals(
            PullRefreshState.IDLE,
            computeNextPullRefreshState(
                current = PullRefreshState.RELEASING,
                pullOffset = 0f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_RELEASING动画中保持RELEASING`() {
        assertEquals(
            PullRefreshState.RELEASING,
            computeNextPullRefreshState(
                current = PullRefreshState.RELEASING,
                pullOffset = 15f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_REFRESHING刷新完成进入IDLE`() {
        assertEquals(
            PullRefreshState.IDLE,
            computeNextPullRefreshState(
                current = PullRefreshState.REFRESHING,
                pullOffset = 60f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_REFRESHING中保持REFRESHING_忽略下拉`() {
        assertEquals(
            PullRefreshState.REFRESHING,
            computeNextPullRefreshState(
                current = PullRefreshState.REFRESHING,
                pullOffset = 80f,
                refreshThreshold = 60f,
                isReleased = true,
                isRefreshing = true
            )
        )
    }

    @Test
    fun `状态机_PULLING未释放保持PULLING`() {
        assertEquals(
            PullRefreshState.PULLING,
            computeNextPullRefreshState(
                current = PullRefreshState.PULLING,
                pullOffset = 50f,
                refreshThreshold = 60f,
                isReleased = false,
                isRefreshing = false
            )
        )
    }

    @Test
    fun `状态机_PULLING恰等于阈值释放进入REFRESHING`() {
        assertEquals(
            PullRefreshState.REFRESHING,
            computeNextPullRefreshState(
                current = PullRefreshState.PULLING,
                pullOffset = 60f,
                refreshThreshold = 60f,
                isReleased = true,
                isRefreshing = false
            )
        )
    }
}
