package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PullRefreshUtils 纯函数单元测试
 *
 * 覆盖：
 * - computeDampedOffset 阻尼曲线（6 用例）
 * - computeRawOffsetForDamped 反向换算（4 用例）
 * - computeNextPullRefreshState 状态机转换（10 用例）
 */
class PullRefreshUtilsTest {

    // ===================== computeDampedOffset 阻尼曲线测试 =====================

    @Test
    fun `阻尼曲线_rawOffset为0时返回0`() {
        assertEquals(0f, computeDampedOffset(0f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_小下拉接近线性跟随`() {
        // rawOffset=20, max=100: damped = 20 - 400/400 = 19
        assertEquals(19f, computeDampedOffset(20f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_中段下拉阻尼递增`() {
        // rawOffset=50, max=100: damped = 50 - 2500/400 = 50 - 6.25 = 43.75
        assertEquals(43.75f, computeDampedOffset(50f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_达2倍max时返回最大值`() {
        // rawOffset=200, max=100: cap at 100
        assertEquals(100f, computeDampedOffset(200f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_超过2倍max时仍封顶`() {
        assertEquals(100f, computeDampedOffset(500f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_负值归零`() {
        assertEquals(0f, computeDampedOffset(-10f, 100f), 0.001f)
    }

    @Test
    fun `阻尼曲线_单调性验证_逐步递增`() {
        // 验证阻尼曲线全程单调递增（修复原bug：原公式在 ratio>0.577 后递减）
        var prev = 0f
        for (raw in 1..200) {
            val current = computeDampedOffset(raw.toFloat(), 100f)
            assert(current >= prev) { "阻尼曲线在 rawOffset=$raw 处递减: prev=$prev, current=$current" }
            prev = current
        }
        assertEquals(100f, prev, 0.001f)
    }

    // ===================== computeRawOffsetForDamped 反向换算测试 =====================

    @Test
    fun `反向换算_damped为0时返回0`() {
        assertEquals(0f, computeRawOffsetForDamped(0f, 100f), 0.001f)
    }

    @Test
    fun `反向换算_damped为max时返回2倍max`() {
        assertEquals(200f, computeRawOffsetForDamped(100f, 100f), 0.001f)
    }

    @Test
    fun `反向换算_中间值正确`() {
        // damped=43.75 → raw=50 (verify: 50 - 2500/400 = 43.75)
        val raw = computeRawOffsetForDamped(43.75f, 100f)
        assertEquals(50f, raw, 0.1f)
    }

    @Test
    fun `反向换算_正向反向互逆`() {
        // 对多个点验证 computeRawOffsetForDamped(computeDampedOffset(raw)) = raw
        for (raw in 0..200 step 10) {
            val damped = computeDampedOffset(raw.toFloat(), 100f)
            val recovered = computeRawOffsetForDamped(damped, 100f)
            assertEquals(raw.toFloat(), recovered, 0.5f)
        }
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
