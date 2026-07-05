package com.corgimemo.app.ui.components

import com.corgimemo.app.data.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DragZoneStateMachineTest {

    private val sm = DragZoneStateMachine()

    /** 构造 TodoItem 辅助函数，仅 zone 关心的字段可变 */
    private fun buildItem(id: Long = 1, isPinned: Boolean = false, status: Int = 0): TodoItem {
        return TodoItem(
            id = id,
            title = "测试项",
            categoryId = 1L,
            priority = 1,
            status = status,
            repeatType = 0,
            createdAt = 0L,
            updatedAt = 0L,
            isPinned = isPinned
        )
    }

    @Before
    fun setUp() {
        sm.reset()
    }

    @Test
    fun `startDrag 初始化 originalZone 与 visualIsPinned`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        assertEquals(TodoZone.PINNED_PENDING, sm.originalZone)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `同区域拖拽不翻转状态`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=0),
            buildItem(id=1, isPinned=true, status=0)
        )
        val crossed = sm.onPositionChanged(items, 1)
        assertFalse(crossed)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
    }

    @Test
    fun `场景1 - PINNED_PENDING 到 PENDING 翻转 isPinned`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=0),
            buildItem(id=1, isPinned=true, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PENDING, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `场景2 - PENDING 到 PINNED_PENDING 翻转 isPinned`() {
        val item = buildItem(isPinned=false, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=0),
            buildItem(id=1, isPinned=false, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
    }

    @Test
    fun `场景3 - PINNED_PENDING 到 PINNED_COMPLETED 翻转 status`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=1),
            buildItem(id=1, isPinned=true, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_COMPLETED, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    @Test
    fun `场景4 - PINNED_COMPLETED 到 PINNED_PENDING 翻转 status`() {
        val item = buildItem(isPinned=true, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=0),
            buildItem(id=1, isPinned=true, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `场景5 - PINNED_PENDING 到 COMPLETED 翻转 isPinned 和 status`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=1),
            buildItem(id=1, isPinned=true, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    @Test
    fun `场景6 - COMPLETED 到 PINNED_PENDING 翻转 isPinned 和 status`() {
        val item = buildItem(isPinned=false, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=0),
            buildItem(id=1, isPinned=false, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `场景7 - PENDING 到 PINNED_COMPLETED 翻转 isPinned 和 status`() {
        val item = buildItem(isPinned=false, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=1),
            buildItem(id=1, isPinned=false, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_COMPLETED, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    @Test
    fun `场景8 - PINNED_COMPLETED 到 PENDING 翻转 isPinned 和 status`() {
        val item = buildItem(isPinned=true, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=0),
            buildItem(id=1, isPinned=true, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PENDING, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `场景9 - PENDING 到 COMPLETED 翻转 status`() {
        val item = buildItem(isPinned=false, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=1),
            buildItem(id=1, isPinned=false, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    @Test
    fun `场景10 - COMPLETED 到 PENDING 翻转 status`() {
        val item = buildItem(isPinned=false, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=0),
            buildItem(id=1, isPinned=false, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PENDING, sm.currentZone)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `场景11 - PINNED_COMPLETED 到 COMPLETED 翻转 isPinned`() {
        val item = buildItem(isPinned=true, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=1),
            buildItem(id=1, isPinned=true, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    @Test
    fun `场景12 - COMPLETED 到 PINNED_COMPLETED 翻转 isPinned`() {
        val item = buildItem(isPinned=false, status=1)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=true, status=1),
            buildItem(id=1, isPinned=false, status=1)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.PINNED_COMPLETED, sm.currentZone)
        assertEquals(true, sm.visualIsPinned)
    }

    @Test
    fun `inferZone 仅自己时保持原 zone`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(item)
        sm.onPositionChanged(items, 0)
        assertEquals(TodoZone.PINNED_PENDING, sm.currentZone)
    }

    @Test
    fun `inferZone 通过后面邻居推断 zone（前面无项）`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=1, isPinned=true, status=0),
            buildItem(id=2, isPinned=false, status=0)
        )
        sm.onPositionChanged(items, 0)
        assertEquals(TodoZone.PENDING, sm.currentZone)
    }

    @Test
    fun `endDrag 返回 ZoneDragResult 携带最终状态`() {
        val item = buildItem(isPinned=false, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=1),
            buildItem(id=1, isPinned=false, status=0)
        )
        sm.onPositionChanged(items, 1)
        val result = sm.endDrag()
        assertEquals(TodoZone.PENDING, result.originalZone)
        assertEquals(TodoZone.COMPLETED, result.currentZone)
        assertEquals(false, result.finalIsPinned)
        assertEquals(1, result.finalStatus)
        assertTrue(result.crossedZone)
    }

    @Test
    fun `reset 清空所有状态`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        sm.reset()
        assertEquals(TodoZone.PENDING, sm.originalZone)
        assertEquals(TodoZone.PENDING, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
    }

    @Test
    fun `快速拖拽跨多 zone 仍正确翻转（PINNED_PENDING 直接到 COMPLETED）`() {
        val item = buildItem(isPinned=true, status=0)
        sm.startDrag(item)
        val items = listOf(
            buildItem(id=2, isPinned=false, status=1),
            buildItem(id=1, isPinned=true, status=0)
        )
        sm.onPositionChanged(items, 1)
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(1, sm.visualStatus)
    }

    // ==================== setZone 测试 ====================

    /**
     * 场景：setZone 同 zone 不触发翻转
     *
     * startDrag(A/PENDING) → setZone(PENDING)
     *
     * 预期：返回 false；visualIsPinned/visualStatus 不变
     */
    @Test
    fun `setZone 同 zone 不触发翻转`() {
        sm.startDrag(buildItem(id = 1, isPinned = false, status = 0))
        val result = sm.setZone(TodoZone.PENDING)
        assertFalse(result)
        assertEquals(false, sm.visualIsPinned)
        assertEquals(0, sm.visualStatus)
        assertEquals(TodoZone.PENDING, sm.currentZone)
    }

    /**
     * 场景：setZone 跨 zone 触发视觉翻转
     *
     * startDrag(A/PENDING) → setZone(COMPLETED)
     *
     * 预期：返回 true；visualStatus=1（PENDING → COMPLETED 翻转 status）
     */
    @Test
    fun `setZone 跨 zone 触发视觉翻转`() {
        sm.startDrag(buildItem(id = 1, isPinned = false, status = 0))
        val result = sm.setZone(TodoZone.COMPLETED)
        assertTrue(result)
        assertEquals(1, sm.visualStatus)
        assertEquals(false, sm.visualIsPinned)  // PENDING 和 COMPLETED 都是 isPinned=false，不翻转
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
    }

    /**
     * 场景：setZone 跨 zone 后再 setZone 同 zone 不触发
     *
     * startDrag(A/PENDING) → setZone(COMPLETED) → setZone(COMPLETED)
     *
     * 预期：第一次返回 true，第二次返回 false
     */
    @Test
    fun `setZone 跨 zone 后再 setZone 同 zone 不触发`() {
        sm.startDrag(buildItem(id = 1, isPinned = false, status = 0))
        val first = sm.setZone(TodoZone.COMPLETED)
        val second = sm.setZone(TodoZone.COMPLETED)
        assertTrue(first)
        assertFalse(second)
        assertEquals(TodoZone.COMPLETED, sm.currentZone)
    }
}
