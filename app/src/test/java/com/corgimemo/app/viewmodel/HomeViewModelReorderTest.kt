package com.corgimemo.app.viewmodel

import android.content.Context
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.AchievementChecker
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.DeletedTodoRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.OperationLogRepository
import com.corgimemo.app.data.repository.TaskDailyStatsRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.util.FileCopyManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * HomeViewModel 拖拽排序相关方法单元测试
 *
 * 覆盖：
 * - reorderTodos：单项向下/向上移动、置顶区跨越
 * - restoreDefaultOrder：按 sortType 重算 sortOrder
 *
 * 说明：HomeViewModel 构造函数有 11 个依赖 + Context，全部使用 relaxed mock。
 * 由于 reorderTodos/restoreDefaultOrder 仅依赖 todoRepository，
 * 其他 mock 不需要特定 stub。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelReorderTest {

    private lateinit var mockTodoRepository: TodoRepository
    private lateinit var mockCorgiRepository: CorgiRepository
    private lateinit var mockCategoryRepository: CategoryRepository
    private lateinit var mockDeletedTodoRepository: DeletedTodoRepository
    private lateinit var mockAchievementChecker: AchievementChecker
    private lateinit var mockAchievementRepository: AchievementRepository
    private lateinit var mockCorgiPreferences: CorgiPreferences
    private lateinit var mockMoodHistoryRepository: MoodHistoryRepository
    private lateinit var mockOperationLogRepository: OperationLogRepository
    private lateinit var mockTaskDailyStatsRepository: TaskDailyStatsRepository
    private lateinit var mockFileCopyManager: FileCopyManager
    private lateinit var mockContext: Context
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() = runTest {
        mockTodoRepository = mockk(relaxed = true)
        mockCorgiRepository = mockk(relaxed = true)
        mockCategoryRepository = mockk(relaxed = true)
        mockDeletedTodoRepository = mockk(relaxed = true)
        mockAchievementChecker = mockk(relaxed = true)
        mockAchievementRepository = mockk(relaxed = true)
        mockCorgiPreferences = mockk(relaxed = true)
        mockMoodHistoryRepository = mockk(relaxed = true)
        mockOperationLogRepository = mockk(relaxed = true)
        mockTaskDailyStatsRepository = mockk(relaxed = true)
        mockFileCopyManager = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        // 默认返回空列表，避免 filteredTodos 初始化时 NPE
        coEvery { mockTodoRepository.getAllTodos() } returns flowOf(emptyList())
        coEvery { mockTodoRepository.observeAllSorted() } returns flowOf(emptyList())

        Dispatchers.setMain(UnconfinedTestDispatcher())

        viewModel = HomeViewModel(
            todoRepository = mockTodoRepository,
            corgiRepository = mockCorgiRepository,
            categoryRepository = mockCategoryRepository,
            deletedTodoRepository = mockDeletedTodoRepository,
            achievementChecker = mockAchievementChecker,
            achievementRepository = mockAchievementRepository,
            corgiPreferences = mockCorgiPreferences,
            moodHistoryRepository = mockMoodHistoryRepository,
            operationLogRepository = mockOperationLogRepository,
            taskDailyStatsRepository = mockTaskDailyStatsRepository,
            fileCopyManager = mockFileCopyManager,
            context = mockContext
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 场景：3 项待办（无置顶），将第 0 项拖到位置 2
     * 预期：
     * - updateSortOrder 被调用 3 次（重新分配所有 sortOrder）
     * - 不调用 updatePinnedStatus（未跨越置顶区）
     */
    @Test
    fun `reorderTodos 单项向下移动更新所有 sortOrder`() = runTest {
        // Given: ViewModel 当前 filteredTodos 为 3 项
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2)
        )
        // 重新触发 collect
        viewModel.refreshTodosForTest(todos)

        // When: 拖拽 fromIndex=0 → toIndex=2
        viewModel.reorderTodos(fromIndex = 0, toIndex = 2, crossedPinnedZone = false)

        // Then: updateSortOrder 被调用 3 次
        coVerify(atLeast = 3) { mockTodoRepository.updateSortOrder(any(), any()) }
        // 不调用 updatePinnedStatus
        coVerify(exactly = 0) { mockTodoRepository.updatePinnedStatus(any(), any()) }
    }

    /**
     * 场景：拖拽跨越置顶区
     * 预期：updatePinnedStatus 被调用 1 次，状态翻转
     */
    @Test
    fun `reorderTodos 跨越置顶区更新 isPinned`() = runTest {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 0)
        )
        viewModel.refreshTodosForTest(todos)

        viewModel.reorderTodos(fromIndex = 0, toIndex = 1, crossedPinnedZone = true)

        // 验证：updatePinnedStatus(1, false) 被调用（true → false）
        coVerify(atLeast = 1) { mockTodoRepository.updatePinnedStatus(1, false) }
    }

    /**
     * 场景：调用 restoreDefaultOrder，sortType = "updated_desc"
     * 预期：按 updatedAt DESC 重新分配 sortOrder = [0, 1, 2]
     */
    @Test
    fun `restoreDefaultOrder 按 updatedAt DESC 重算 sortOrder`() = runTest {
        val todos = listOf(
            testTodo(1, updatedAt = 100, sortOrder = 5),
            testTodo(2, updatedAt = 300, sortOrder = 5),
            testTodo(3, updatedAt = 200, sortOrder = 5)
        )
        viewModel.refreshTodosForTest(todos)
        viewModel.setSortTypeForTest("updated_desc")

        viewModel.restoreDefaultOrder()

        // 排序后顺序应为 [2(300), 3(200), 1(100)]，sortOrder 分别为 [0, 1, 2]
        coVerify { mockTodoRepository.updateSortOrder(2, 0) }
        coVerify { mockTodoRepository.updateSortOrder(3, 1) }
        coVerify { mockTodoRepository.updateSortOrder(1, 2) }
    }

    /**
     * 场景：fromIndex 越界
     * 预期：直接 return，不调用任何 DAO 方法
     */
    @Test
    fun `reorderTodos fromIndex 越界安全返回`() = runTest {
        viewModel.reorderTodos(fromIndex = 99, toIndex = 0, crossedPinnedZone = false)
        coVerify(exactly = 0) { mockTodoRepository.updateSortOrder(any(), any()) }
    }

    // ==================== 测试辅助方法 ====================

    /** 构造测试 TodoItem */
    private fun testTodo(
        id: Long,
        isPinned: Boolean = false,
        sortOrder: Int = 0,
        updatedAt: Long = 0L,
        createdAt: Long = 0L
    ): TodoItem {
        return TodoItem(
            id = id,
            title = "test$id",
            content = null,
            categoryId = 1L,
            priority = 0,
            status = 0,
            repeatType = 0,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isPinned = isPinned,
            sortOrder = sortOrder
        )
    }
}
