package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PullRefreshIndicatorLayout 纯函数单元测试
 *
 * 覆盖 9 个核心场景，覆盖所有 PullRefreshState 与 pullOffset 边界。
 */
class PullRefreshIndicatorLayoutTest {

    // ===================== IDLE 早返测试 =====================

    @Test
    fun `IDLE且无下拉时shouldRender为false`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 0f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.IDLE
        )
        assertFalse("IDLE+无下拉 shouldRender 应为 false", layout.shouldRender)
        assertEquals("IDLE+无下拉 displayHeightDp 应为 0", 0f, layout.displayHeightDp, 0.001f)
        assertNull("IDLE+无下拉 tipText 应为 null", layout.tipText)
    }

    // ===================== REFRESHING 锁测试 =====================

    @Test
    fun `REFRESHING即使pullOffset为0也强制渲染且显示柯基努力加载中`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 0f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.REFRESHING
        )
        assertTrue("REFRESHING shouldRender 应为 true", layout.shouldRender)
        assertEquals("REFRESHING contentAlpha 应为 1", 1f, layout.contentAlpha, 0.001f)
        assertEquals("REFRESHING tipText 应为「柯基努力加载中~」", "柯基努力加载中~", layout.tipText)
        assertEquals("REFRESHING displayHeightDp 应为 72（最小高度）", 72f, layout.displayHeightDp, 0.001f)
    }

    // ===================== PULLING 微小下拉测试 =====================

    @Test
    fun `PULLING微小下拉4dp时contentAlpha为0但布局稳定`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 4f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertTrue("微小下拉 shouldRender 应为 true", layout.shouldRender)
        assertEquals("微小下拉 contentAlpha 应为 0（淡入起点前）", 0f, layout.contentAlpha, 0.001f)
        assertEquals("微小下拉 tipText 应为「下拉刷新」", "下拉刷新", layout.tipText)
        assertEquals("微小下拉 displayHeightDp 应为 72（最小高度）", 72f, layout.displayHeightDp, 0.001f)
    }

    // ===================== PULLING 淡入起点测试 =====================

    @Test
    fun `PULLING下拉8dp时contentAlpha仍为0是淡入起点`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 8f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertEquals("下拉 8dp contentAlpha 应为 0（淡入起点）", 0f, layout.contentAlpha, 0.001f)
        assertEquals("下拉 8dp tipText 应为「下拉刷新」", "下拉刷新", layout.tipText)
    }

    // ===================== PULLING 淡入完成测试 =====================

    @Test
    fun `PULLING下拉16dp时contentAlpha为1是淡入完成`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 16f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertEquals("下拉 16dp contentAlpha 应为 1（淡入完成）", 1f, layout.contentAlpha, 0.001f)
        assertEquals("下拉 16dp tipText 应为「下拉刷新」", "下拉刷新", layout.tipText)
    }

    // ===================== PULLING 中段测试 =====================

    @Test
    fun `PULLING中段40dp时contentAlpha为1且显示下拉刷新`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 40f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertEquals("中段 40dp contentAlpha 应为 1", 1f, layout.contentAlpha, 0.001f)
        assertEquals("中段 40dp tipText 应为「下拉刷新」", "下拉刷新", layout.tipText)
    }

    // ===================== PULLING 阈值切换测试 =====================

    @Test
    fun `PULLING阈值60dp时tipText切换为释放刷新`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 60f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertEquals("阈值 60dp tipText 应为「释放刷新」", "释放刷新", layout.tipText)
        assertEquals("阈值 60dp contentAlpha 应为 1", 1f, layout.contentAlpha, 0.001f)
    }

    // ===================== PULLING 最大高度测试 =====================

    @Test
    fun `PULLING最大100dp时displayHeightDp等于pullOffset`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 100f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.PULLING
        )
        assertEquals("最大 100dp displayHeightDp 应等于 pullOffset", 100f, layout.displayHeightDp, 0.001f)
        assertEquals("最大 100dp 柯基应为 64dp", 64f, layout.corgiSizeDp, 0.001f)
        assertEquals("最大 100dp tipText 应为「释放刷新」", "释放刷新", layout.tipText)
    }

    // ===================== RELEASING 回弹测试 =====================

    @Test
    fun `RELEASING回弹中20dp时contentAlpha为1且显示下拉刷新`() {
        val layout = computePullRefreshIndicatorLayout(
            pullOffsetDp = 20f,
            maxPullHeightDp = 100f,
            refreshThresholdDp = 60f,
            state = PullRefreshState.RELEASING
        )
        assertTrue("RELEASING shouldRender 应为 true", layout.shouldRender)
        assertEquals("RELEASING 20dp contentAlpha 应为 1（>8dp 完全可见）", 1f, layout.contentAlpha, 0.001f)
        assertEquals("RELEASING 20dp tipText 应为「下拉刷新」", "下拉刷新", layout.tipText)
    }
}
