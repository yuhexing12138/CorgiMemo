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
import io.mockk.match
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

    /**
     * 场景：Case A(置顶 ≥ 4)拖拽跨边界到已完成区
     *
     * displayItems 结构(假设):
     * [PinnedDivider, P1, P2, P3, P4, PendingDivider, N1, N2, N3, N4, N5, CompletedDivider, C1, C2, C3]
     *   0          1   2   3   4    5             6   7   8   9   10  11              12  13  14
     *
     * dividerIndex = 11(CompletedDivider 真实位置)
     *
     * 旧实现错误:val dividerIndex = pendingList.size = 9 → 偏差 +2
     * 拖拽:N5(原始 index=10)拖到 C1 位置(index=12)
     * 预期:
     * - fromPending = (10 < 11) = true
     * - toCompleted = (12 > 11) = true
     * - 触发跨区域状态变更:N5.status 0 → 1
     * - updateTodo 被调用(N5 状态变更)
     */
    @Test
    fun `Case A 置顶大于等于4 跨边界拖拽应标记为完成`() = runTest {
        // Given: 4 个置顶待办 + 5 个非置顶待办 + 3 个已完成待办
        val todos = listOf(
            // 4 个置顶 (sortOrder 0-3)
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            // 5 个非置顶 (sortOrder 4-8)
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            // 3 个已完成 (sortOrder 9-11)
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1),
            testTodo(11, isPinned = false, sortOrder = 10).copy(status = 1),
            testTodo(12, isPinned = false, sortOrder = 11).copy(status = 1)
        )
        viewModel.refreshTodosForTest(todos)

        // When: Case A 真实 dividerIndex = 11
        // N5(原始 index=10)拖到 C1 位置(index=12,即第 13 项,已完成的顶部)
        viewModel.reorderOnDisplayList(
            fromIndex = 10,
            toIndex = 12,
            dividerIndex = 11,
            crossedPinnedZone = false
        )

        // Then: updateTodo 被调用(N5 跨区完成)
        coVerify(atLeast = 1) { mockTodoRepository.updateTodo(match { it.id == 9L && it.status == 1 }) }
    }

    /**
     * 场景：Case B(置顶 < 4)拖拽跨边界到已完成区
     *
     * displayItems 结构(假设):
     * [PendingDivider, P1, P2, N1, N2, N3, N4, N5, CompletedDivider, C1, C2, C3]
     *   0             1   2   3   4   5   6   7   8               9   10  11
     *
     * dividerIndex = 8(CompletedDivider 真实位置)
     *
     * 旧实现错误:val dividerIndex = pendingList.size = 7 → 偏差 +1
     * 拖拽:N5(原始 index=7)拖到 C1 位置(index=9)
     * 预期:
     * - fromPending = (7 < 8) = true
     * - toCompleted = (9 > 8) = true
     * - 触发跨区域状态变更:N5.status 0 → 1
     */
    @Test
    fun `Case B 置顶小于4 跨边界拖拽应标记为完成`() = runTest {
        // Given: 2 个置顶 + 5 个非置顶 + 3 个已完成
        val todos = listOf(
            // 2 个置顶 (sortOrder 0-1)
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            // 5 个非置顶 (sortOrder 2-6)
            testTodo(3, isPinned = false, sortOrder = 2),
            testTodo(4, isPinned = false, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            // 3 个已完成 (sortOrder 7-9)
            testTodo(8, isPinned = false, sortOrder = 7).copy(status = 1),
            testTodo(9, isPinned = false, sortOrder = 8).copy(status = 1),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        viewModel.refreshTodosForTest(todos)

        // When: Case B 真实 dividerIndex = 8
        // N5(原始 index=7)拖到 C1 位置(index=9)
        viewModel.reorderOnDisplayList(
            fromIndex = 7,
            toIndex = 9,
            dividerIndex = 8,
            crossedPinnedZone = false
        )

        // Then: updateTodo 被调用(N5 跨区完成)
        coVerify(atLeast = 1) { mockTodoRepository.updateTodo(match { it.id == 7L && it.status == 1 }) }
    }

    /**
     * 场景：边界检查 - dividerIndex = -1 (无已完成区)
     * 预期：reorderOnDisplayList 不会因 totalSize 校验而误判
     */
    @Test
    fun `dividerIndex 负一表示无已完成区不应误判跨区`() = runTest {
        // Given: 全部都是待办,无已完成
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1)
        )
        viewModel.refreshTodosForTest(todos)

        // When: dividerIndex = -1,fromIndex 越界 → 应当直接 return
        viewModel.reorderOnDisplayList(
            fromIndex = 0,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = false
        )

        // Then: 正常执行(同区排序),不调用 updateTodo (无状态变更)
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(any()) }
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
