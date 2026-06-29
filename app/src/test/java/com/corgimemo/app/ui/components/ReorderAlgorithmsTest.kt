package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拖拽排序算法纯函数单元测试
 *
 * 重构后仅保留置顶区跨越检测的测试，其余算法由 Calvin-LL/Reorderable 库内部处理。
 */
class ReorderAlgorithmsTest {

    /**
     * 场景：原置顶，拖到非置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶到非置顶返回 true`() {
        // 算法设计：传入的 displayItems 是被拖项已移除后的列表
        val displayItems = listOf(false, false)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 0 // 插入到位置 0，邻居为位置 1（false）
        )
        assertEquals(true, result)
    }

    /**
     * 场景：原非置顶，拖到置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶到置顶返回 true`() {
        // 列表（被拖移除后）：[true, true]
        // 被拖原始 isPinned=false，插入到位置 0
        val displayItems = listOf(true, true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = false,
            draggedCurrentIndex = 0
        )
        assertEquals(true, result)
    }

    /**
     * 场景：置顶区内移动
     * 预期：返回 false（未跨越分界线）
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶区内移动返回 false`() {
        val displayItems = listOf(true, true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 1
        )
        assertEquals(false, result)
    }

    /**
     * 场景：非置顶区内移动
     * 预期：返回 false
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶区内移动返回 false`() {
        val displayItems = listOf(false, false)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = false,
            draggedCurrentIndex = 1
        )
        assertEquals(false, result)
    }

    /**
     * 场景：index 越界
     * 预期：返回 false（兜底）
     */
    @Test
    fun `checkPinnedZoneCrossed index 越界返回 false`() {
        val displayItems = listOf(true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 5
        )
        assertEquals(false, result)
    }
}
