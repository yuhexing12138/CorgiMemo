// RecentlyDeletedViewModel 单元测试
package com.corgimemo.app.ui.screens.recentlydeleted

import app.cash.turbine.test
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.TodoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RecentlyDeletedViewModel 单元测试
 *
 * 覆盖：
 * - init 加载流程与 isLoading 切换
 * - 分组与计数映射
 * - 30 天自动清理
 * - 恢复/永久删除/清空全部/撤销
 * - 分组存在性判断
 * - sortOrder 末尾追加
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentlyDeletedViewModelTest {

    private lateinit var deletedRepo: DeletedTodoRepository
    private lateinit var todoRepo: TodoRepository
    private lateinit var categoryRepo: CategoryRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        deletedRepo = mockk(relaxed = true)
        todoRepo = mockk(relaxed = true)
        categoryRepo = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun todo(id: Long, title: String, deletedAt: Long, categoryId: Long? = null) =
        DeletedTodo(
            id = id, title = title, content = null, categoryId = categoryId ?: 0L,
            priority = 0, status = 0, startDate = null, estimatedDurationMinutes = null,
            reminderTime = null, repeatType = 0, createdAt = 0L, updatedAt = 0L,
            completedAt = null, deletedAt = deletedAt
        )

    private fun category(id: Long, name: String) =
        Category(id = id, name = name, type = 0, isDefault = false)

    @Test
    fun `init 后 isLoading 从 true 变 false`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertFalse("isLoading 应变为 false", state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleted 列表为空时 groups 为空且 totalCount=0`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(0, state.groups.size)
            assertEquals(0, state.totalCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `3 条 deleted 按时分组正确`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val todos = listOf(
            todo(1L, "今天项", now - 1000L),
            todo(2L, "昨天项", now - day),
            todo(3L, "本周项", now - 3 * day)
        )
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(todos)
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            val state = awaitItem()
            assertEquals(3, state.groups.size)
            assertEquals("今天", state.groups[0].title)
            assertEquals("昨天", state.groups[1].title)
            assertEquals("本周", state.groups[2].title)
            assertEquals(3, state.totalCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init 时调用 cleanUpOldDeletedTodos 30 天阈值`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            deletedRepo.cleanUpOldDeletedTodos(match { threshold ->
                val days = (System.currentTimeMillis() - threshold) / (24L * 60 * 60 * 1000)
                days in 29L..30L
            })
        }
    }

    @Test
    fun `showClearAllDialog 切换 showClearAllDialog 状态`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.showClearAllDialog()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.uiState.test {
            assertTrue(awaitItem().showClearAllDialog)
            vm.dismissClearAllDialog()
            testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(awaitItem().showClearAllDialog)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmClearAll 调用 permanentlyDeleteAll`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.permanentlyDeleteAll() } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.confirmClearAll()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { deletedRepo.permanentlyDeleteAll() }
    }

    @Test
    fun `permanentlyDelete 正确调用 repository`() = runTest(testDispatcher) {
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.permanentlyDelete(any()) } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.permanentlyDelete(42L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { deletedRepo.permanentlyDelete(42L) }
    }

    @Test
    fun `restoreTodo 调用 insertTodo+permanentlyDelete`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val deleted = todo(1L, "买菜", now, categoryId = null)
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.getByIdBlocking(1L) } returns deleted
        coEvery { todoRepo.getMaxSortOrderBlocking(false, 0) } returns 100
        coEvery { todoRepo.insertTodo(any()) } returns 1L
        coEvery { deletedRepo.permanentlyDelete(1L) } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.restoreTodo(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { todoRepo.insertTodo(match { it.title == "买菜" }) }
        coVerify { deletedRepo.permanentlyDelete(1L) }
    }

    @Test
    fun `restoreTodo 时原 categoryId 不存在则置 0`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val deleted = todo(1L, "买菜", now, categoryId = 99L) // 99L 分组不存在
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.getByIdBlocking(1L) } returns deleted
        coEvery { categoryRepo.getCategoryById(99L) } returns null
        coEvery { todoRepo.getMaxSortOrderBlocking(false, 0) } returns 100
        coEvery { todoRepo.insertTodo(any()) } returns 1L
        coEvery { deletedRepo.permanentlyDelete(1L) } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.restoreTodo(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            todoRepo.insertTodo(match {
                it.categoryId == 0L && it.sortOrder == 101
            })
        }
    }

    @Test
    fun `restoreTodo 时 sortOrder 设为 max+1`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val deleted = todo(1L, "A", now, categoryId = null)
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.getByIdBlocking(1L) } returns deleted
        coEvery { todoRepo.getMaxSortOrderBlocking(false, 0) } returns 500
        coEvery { todoRepo.insertTodo(any()) } returns 1L
        coEvery { deletedRepo.permanentlyDelete(1L) } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.restoreTodo(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { todoRepo.insertTodo(match { it.sortOrder == 501 }) }
    }

    @Test
    fun `分类存在时 restoreTodo 保留原 categoryId`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val deleted = todo(1L, "买菜", now, categoryId = 5L)
        every { deletedRepo.getAllDeletedTodos() } returns flowOf(emptyList())
        every { categoryRepo.getAllCategories() } returns flowOf(emptyList())
        coEvery { deletedRepo.cleanUpOldDeletedTodos(any()) } returns 0
        coEvery { deletedRepo.getByIdBlocking(1L) } returns deleted
        coEvery { categoryRepo.getCategoryById(5L) } returns category(5L, "生活")
        coEvery { todoRepo.getMaxSortOrderBlocking(false, 0) } returns 100
        coEvery { todoRepo.insertTodo(any()) } returns 1L
        coEvery { deletedRepo.permanentlyDelete(1L) } returns Unit

        val vm = RecentlyDeletedViewModel(deletedRepo, todoRepo, categoryRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.restoreTodo(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { todoRepo.insertTodo(match { it.categoryId == 5L }) }
    }
}
