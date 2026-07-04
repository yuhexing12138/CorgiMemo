package com.corgimemo.app.ui.components

import com.corgimemo.app.data.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TodoZone 枚举与 zone() 派生函数单元测试
 *
 * 覆盖 (isPinned, status) 四种笛卡尔组合：
 * - 置顶待完成 → PINNED_PENDING
 * - 普通待完成 → PENDING
 * - 置顶已完成 → PINNED_COMPLETED
 * - 普通已完成 → COMPLETED
 */
class TodoZoneTest {

    /**
     * 构造测试用 TodoItem
     *
     * TodoItem 有多个必填字段（title、categoryId、priority、repeatType、createdAt、updatedAt），
     * 此处填入合理默认值，仅让 isPinned / status 在用例中变化。
     */
    private fun buildItem(isPinned: Boolean, status: Int): TodoItem = TodoItem(
        id = 1,
        title = "测试项",
        categoryId = 1L,
        priority = 1,
        status = status,
        repeatType = 0,
        createdAt = 0L,
        updatedAt = 0L,
        isPinned = isPinned
    )

    @Test
    fun `置顶待完成项映射到 PINNED_PENDING`() {
        val item = buildItem(isPinned = true, status = 0)
        assertEquals(TodoZone.PINNED_PENDING, item.zone())
    }

    @Test
    fun `普通待完成项映射到 PENDING`() {
        val item = buildItem(isPinned = false, status = 0)
        assertEquals(TodoZone.PENDING, item.zone())
    }

    @Test
    fun `置顶已完成项映射到 PINNED_COMPLETED`() {
        val item = buildItem(isPinned = true, status = 1)
        assertEquals(TodoZone.PINNED_COMPLETED, item.zone())
    }

    @Test
    fun `普通已完成项映射到 COMPLETED`() {
        val item = buildItem(isPinned = false, status = 1)
        assertEquals(TodoZone.COMPLETED, item.zone())
    }
}
