package com.corgimemo.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * computeScrollDrivenProgress 纯函数单元测试
 *
 * 覆盖:
 * - 列表在顶部(firstVisibleItemIndex == 0)的各种 scrollOffset
 * - 第一项已完全滚出(firstVisibleItemIndex > 0)
 * - 边界情况(searchBarHeightPx <= 0)
 */
class SearchRevealProgressTest {

    private val searchBarHeight = 192f  // 64dp 在 xxhdpi(3x)下的 px 值

    @Test
    fun `列表在顶部且未滚动时进度为1`() {
        assertEquals(
            1f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                searchBarHeightPx = searchBarHeight
            ),
            0.001f
        )
    }

    @Test
    fun `列表在顶部且滚动半高度时进度为0_5`() {
        assertEquals(
            0.5f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 96,  // searchBarHeight / 2
                searchBarHeightPx = searchBarHeight
            ),
            0.001f
        )
    }

    @Test
    fun `列表在顶部且滚动超过高度时进度为0`() {
        assertEquals(
            0f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 300,  // > searchBarHeight
                searchBarHeightPx = searchBarHeight
            ),
            0.001f
        )
    }

    @Test
    fun `第一项已完全滚出时进度强制为0`() {
        assertEquals(
            0f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
                searchBarHeightPx = searchBarHeight
            ),
            0.001f
        )
    }

    @Test
    fun `第一项已完全滚出时无论scrollOffset都为0`() {
        assertEquals(
            0f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 500,
                searchBarHeightPx = searchBarHeight
            ),
            0.001f
        )
    }

    @Test
    fun `searchBarHeight为0时返回1f避免除零`() {
        assertEquals(
            1f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 100,
                searchBarHeightPx = 0f
            ),
            0.001f
        )
    }

    @Test
    fun `searchBarHeight为负时返回1f防御`() {
        assertEquals(
            1f,
            computeScrollDrivenProgress(
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 100,
                searchBarHeightPx = -10f
            ),
            0.001f
        )
    }
}
