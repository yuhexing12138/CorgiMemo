// RecentlyDeletedUiState 数据类单元测试
package com.corgimemo.app.ui.screens.recentlydeleted

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecentlyDeletedUiState / DeletedTodoGroup / DeletedTodoListItem 数据类测试
 *
 * 验证默认值、copy 不变性、字段访问正确性。
 */
class RecentlyDeletedUiStateTest {

    @Test
    fun `默认 UiState 是 loading 状态`() {
        val state = RecentlyDeletedUiState()
        assertTrue(state.isLoading)
        assertEquals(0, state.totalCount)
        assertEquals(0, state.groups.size)
        assertEquals(false, state.showClearAllDialog)
    }

    @Test
    fun `copy 切换 isLoading 不影响其他字段`() {
        val original = RecentlyDeletedUiState(totalCount = 5)
        val updated = original.copy(isLoading = false)
        assertEquals(false, updated.isLoading)
        assertEquals(5, updated.totalCount)
        // 保持其他字段不变
        assertEquals(0, updated.groups.size)
        assertEquals(false, updated.showClearAllDialog)
    }

    @Test
    fun `DeletedTodoGroup 构造正确`() {
        val items = listOf(
            DeletedTodoListItem(
                id = 1L, title = "A", originalCategoryId = null,
                categoryName = null, deletedAt = 100L, relativeTime = "刚刚"
            )
        )
        val group = DeletedTodoGroup(
            kind = DeletedTodoGroupKind.TODAY,
            title = "今天",
            items = items
        )
        assertEquals(DeletedTodoGroupKind.TODAY, group.kind)
        assertEquals("今天", group.title)
        assertEquals(1, group.items.size)
    }

    @Test
    fun `DeletedTodoListItem 字段访问`() {
        val item = DeletedTodoListItem(
            id = 42L, title = "买菜", originalCategoryId = 1L,
            categoryName = "生活", deletedAt = 1000L, relativeTime = "5 分钟前"
        )
        assertEquals(42L, item.id)
        assertEquals("买菜", item.title)
        assertEquals(1L, item.originalCategoryId)
        assertEquals("生活", item.categoryName)
        assertEquals(1000L, item.deletedAt)
        assertEquals("5 分钟前", item.relativeTime)
    }
}
