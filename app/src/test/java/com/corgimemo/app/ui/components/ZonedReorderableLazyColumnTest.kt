package com.corgimemo.app.ui.components

import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.screens.home.DisplayItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ZonedReorderableLazyColumn 组件内部纯函数单元测试
 *
 * 覆盖 computeRelativeIndexInZone 的 4 种场景：
 * - 同 zone 内拖拽
 * - 跨 zone 拖拽（PENDING → PINNED_PENDING）
 * - 拖到 zone 开头
 * - 跨 zone 拖到已完成区
 */
class ZonedReorderableLazyColumnTest {

    /** 构造 TodoItem 辅助函数 */
    private fun buildItem(
        id: Long,
        isPinned: Boolean = false,
        status: Int = 0
    ): TodoItem {
        return TodoItem(
            id = id,
            title = "测试项$id",
            categoryId = 1L,
            priority = 1,
            status = status,
            repeatType = 0,
            createdAt = 0L,
            updatedAt = 0L,
            isPinned = isPinned
        )
    }

    /**
     * 场景：同 zone 内拖拽（PENDING 区内）
     *
     * 列表：[PendingDivider, A, B, C]（A/B/C 均为 PENDING）
     * 被拖项当前在 idx=2（B 的位置）
     * targetZone = PENDING
     *
     * 预期：relativeIndex = 1（前面只有 A 一个同 zone 项）
     */
    @Test
    fun `同 zone 内拖拽计算 relativeIndex`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PendingDivider(count = 3, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1)),
            DisplayItem.Todo(buildItem(id = 2)),
            DisplayItem.Todo(buildItem(id = 3))
        )
        val result = computeRelativeIndexInZone(
            displayItems = displayItems,
            draggedCurrentIndex = 2,
            targetZone = TodoZone.PENDING
        )
        assertEquals(1, result)
    }

    /**
     * 场景：跨 zone 拖拽（被拖项从 COMPLETED 跨到 PENDING）
     *
     * 列表：[PinnedDivider, A_pinned, PendingDivider, B, C, draggedItem]
     *   - A_pinned 是 PINNED_PENDING（isPinned=true, status=0）
     *   - B/C 是 PENDING
     *   - draggedItem 是被拖项，已在 idx=5（原 COMPLETED 跨到 PENDING 区末尾）
     * targetZone = PENDING
     *
     * 预期：relativeIndex = 2（前面有 B、C 两个 PENDING 项；A_pinned 不算）
     */
    @Test
    fun `跨 zone 拖拽计算 relativeIndex`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PinnedDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1, isPinned = true)),
            DisplayItem.PendingDivider(count = 2, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 2)),
            DisplayItem.Todo(buildItem(id = 3)),
            DisplayItem.Todo(buildItem(id = 4, status = 1))  // 被拖项（status=1 但 currentZone=PENDING）
        )
        val result = computeRelativeIndexInZone(
            displayItems = displayItems,
            draggedCurrentIndex = 5,
            targetZone = TodoZone.PENDING
        )
        assertEquals(2, result)
    }

    /**
     * 场景：拖到 zone 开头
     *
     * 列表：[PendingDivider, draggedItem, A, B]
     * 被拖项当前在 idx=1（divider 后第一项）
     * targetZone = PENDING
     *
     * 预期：relativeIndex = 0（前面没有同 zone 项）
     */
    @Test
    fun `拖到 zone 开头 relativeIndex 为 0`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PendingDivider(count = 3, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1)),
            DisplayItem.Todo(buildItem(id = 2)),
            DisplayItem.Todo(buildItem(id = 3))
        )
        val result = computeRelativeIndexInZone(
            displayItems = displayItems,
            draggedCurrentIndex = 1,
            targetZone = TodoZone.PENDING
        )
        assertEquals(0, result)
    }

    /**
     * 场景：跨 zone 拖到已完成区
     *
     * 列表：[PinnedDivider, A_pinned, PendingDivider, B, CompletedDivider, X, draggedItem]
     *   - A_pinned 是 PINNED_PENDING
     *   - B 是 PENDING
     *   - X 是 COMPLETED（isPinned=false, status=1）
     *   - draggedItem 被拖到 idx=6（已完成区末尾）
     * targetZone = COMPLETED
     *
     * 预期：relativeIndex = 1（前面只有 X 一个 COMPLETED 项）
     */
    @Test
    fun `跨 zone 拖到已完成区 relativeIndex`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PinnedDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1, isPinned = true)),
            DisplayItem.PendingDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 2)),
            DisplayItem.CompletedDivider(count = 2, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 3, status = 1)),
            DisplayItem.Todo(buildItem(id = 4))  // 被拖项（status=0 但 currentZone=COMPLETED）
        )
        val result = computeRelativeIndexInZone(
            displayItems = displayItems,
            draggedCurrentIndex = 6,
            targetZone = TodoZone.COMPLETED
        )
        assertEquals(1, result)
    }

    // ==================== inferZoneFromDisplayItems 测试 ====================

    /**
     * 场景：扫描到 PinnedDivider 返回 PINNED_PENDING
     *
     * 列表：[PinnedDivider, A, B]（A/B 均为 PINNED_PENDING）
     * 被拖项当前在 idx=2（B 的位置）
     *
     * 预期：扫描 newDisplay[1]=A（Todo，继续）→ newDisplay[0]=PinnedDivider → 返回 PINNED_PENDING
     */
    @Test
    fun `inferZoneFromDisplayItems 扫描到 PinnedDivider 返回 PINNED_PENDING`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PinnedDivider(count = 2, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1, isPinned = true)),
            DisplayItem.Todo(buildItem(id = 2, isPinned = true))
        )
        val result = inferZoneFromDisplayItems(
            displayItems = displayItems,
            draggedIndex = 2
        )
        assertEquals(TodoZone.PINNED_PENDING, result)
    }

    /**
     * 场景：扫描到 PendingDivider 返回 PENDING
     *
     * 列表：[PinnedDivider, A, PendingDivider, B]
     * 被拖项当前在 idx=3（B 的位置）
     *
     * 预期：扫描 newDisplay[2]=PendingDivider → 返回 PENDING
     */
    @Test
    fun `inferZoneFromDisplayItems 扫描到 PendingDivider 返回 PENDING`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PinnedDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1, isPinned = true)),
            DisplayItem.PendingDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 2))
        )
        val result = inferZoneFromDisplayItems(
            displayItems = displayItems,
            draggedIndex = 3
        )
        assertEquals(TodoZone.PENDING, result)
    }

    /**
     * 场景：扫描到 CompletedDivider 返回 COMPLETED
     *
     * 列表：[PendingDivider, A, CompletedDivider, X]
     * 被拖项当前在 idx=3（X 的位置）
     *
     * 预期：扫描 newDisplay[2]=CompletedDivider → 返回 COMPLETED
     */
    @Test
    fun `inferZoneFromDisplayItems 扫描到 CompletedDivider 返回 COMPLETED`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PendingDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1)),
            DisplayItem.CompletedDivider(count = 1, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 2, status = 1))
        )
        val result = inferZoneFromDisplayItems(
            displayItems = displayItems,
            draggedIndex = 3
        )
        assertEquals(TodoZone.COMPLETED, result)
    }

    /**
     * 场景：跨过多项找到最近的 divider
     *
     * 列表：[PinnedDivider, A, B, C]（A/B/C 均为 PINNED_PENDING）
     * 被拖项当前在 idx=3（C 的位置）
     *
     * 预期：扫描 newDisplay[2]=B、newDisplay[1]=A（Todo，继续）→ newDisplay[0]=PinnedDivider → 返回 PINNED_PENDING
     */
    @Test
    fun `inferZoneFromDisplayItems 跨过多项找到最近的 divider`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.PinnedDivider(count = 3, isExpanded = true),
            DisplayItem.Todo(buildItem(id = 1, isPinned = true)),
            DisplayItem.Todo(buildItem(id = 2, isPinned = true)),
            DisplayItem.Todo(buildItem(id = 3, isPinned = true))
        )
        val result = inferZoneFromDisplayItems(
            displayItems = displayItems,
            draggedIndex = 3
        )
        assertEquals(TodoZone.PINNED_PENDING, result)
    }

    /**
     * 场景：无 divider 回退到被拖项自身 zone
     *
     * 列表：[A, B, C]（无 divider，A/B/C 均为 PENDING）
     * 被拖项当前在 idx=1（B 的位置）
     *
     * 预期：扫描到列表开头仍无 divider → 回退到被拖项 B.zone() = PENDING
     */
    @Test
    fun `inferZoneFromDisplayItems 无 divider 回退到被拖项自身 zone`() {
        val displayItems = listOf<DisplayItem>(
            DisplayItem.Todo(buildItem(id = 1)),
            DisplayItem.Todo(buildItem(id = 2)),
            DisplayItem.Todo(buildItem(id = 3))
        )
        val result = inferZoneFromDisplayItems(
            displayItems = displayItems,
            draggedIndex = 1
        )
        assertEquals(TodoZone.PENDING, result)
    }
}
