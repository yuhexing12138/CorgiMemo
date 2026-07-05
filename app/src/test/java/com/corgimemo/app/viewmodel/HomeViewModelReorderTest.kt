package com.corgimemo.app.viewmodel

import android.content.Context
import com.corgimemo.app.animation.HapticFeedbackController
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
import com.corgimemo.app.data.repository.SubTaskManager
import com.corgimemo.app.data.repository.SubTaskProgress
import com.corgimemo.app.ui.components.TodoZone
import com.corgimemo.app.ui.components.ZoneDragResult
import com.corgimemo.app.util.FileCopyManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
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
 * - reorderOnDragResult：同区拖拽、跨区拖拽、sortOrder 分段分配
 *
 * 说明：HomeViewModel 构造函数有 12 个依赖 + Context，全部使用 relaxed mock。
 * 由于 reorderOnDragResult 仅依赖 todoRepository，
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
    private lateinit var mockHapticFeedbackController: HapticFeedbackController
    private lateinit var mockContext: Context
    private lateinit var viewModel: HomeViewModel

    /**
     * 共享的 TestDispatcher
     *
     * 关键设计：setUp 中 [Dispatchers.setMain] 和测试方法中 [runTest] 必须使用
     * 同一个 dispatcher 实例，否则 viewModelScope（通过 Dispatchers.Main）
     * 会绑定到 setUp 的 dispatcher，而测试方法的 runCurrent()/advanceUntilIdle()
     * 操作的是另一个 dispatcher，导致 viewModelScope.launch 中的协程无法被推进。
     */
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
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
        mockHapticFeedbackController = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        // 默认返回空列表，避免 filteredTodos 初始化时 NPE
        coEvery { mockTodoRepository.getAllTodos() } returns emptyFlow()
        coEvery { mockTodoRepository.observeAllSorted() } returns emptyFlow()

        // 桩：corgiPreferences 的所有 Flow 用 emptyFlow 立即完成，避免 init 中 collect 永久挂起
        coEvery { mockCorgiPreferences.showCompleted } returns emptyFlow()
        coEvery { mockCorgiPreferences.showPinned } returns emptyFlow()
        coEvery { mockCorgiPreferences.hideDetails } returns emptyFlow()
        coEvery { mockCorgiPreferences.hideCompletedItems } returns emptyFlow()
        coEvery { mockCorgiPreferences.userType } returns emptyFlow()

        // 桩：SubTaskManager 是 object，跨区域拖拽会调用 getProgress 检查子任务约束。
        // mockContext 无法创建真实数据库，会导致 NPE 使协程静默失败。
        // 返回 total=0 表示无子任务，跳过 "未完成子任务" 拦截。
        mockkObject(SubTaskManager)
        coEvery { SubTaskManager.getProgress(any(), any()) } returns SubTaskProgress(0, 0, 0f)

        // 使用共享 dispatcher，确保 viewModelScope 与测试方法的 runTest 共用同一 scheduler
        Dispatchers.setMain(testDispatcher)

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
            hapticFeedbackController = mockHapticFeedbackController,
            context = mockContext
        )

        // 取消 init 中 startIdleDetection 的 while(isActive) 无限循环，避免阻塞 runTest
        viewModel.stopIdleDetection()
    }

    @After
    fun tearDown() {
        viewModel.stopIdleDetection()
        unmockkObject(SubTaskManager)
        Dispatchers.resetMain()
    }

    /**
     * 测试辅助：注入数据并激活 4 个 zone StateFlow
     *
     * 各 zone StateFlow 使用 stateIn(WhileSubscribed)，需要活跃订阅者才会处理 _todos 的变更。
     * setUp 与测试方法是独立的 runTest 作用域，所以必须在每个测试内订阅。
     *
     * 同时激活 4 个独立 zone StateFlow（pinnedPendingTodos /
     * pendingTodos / pinnedCompletedTodos / completedTodos），
     * 让 reorderOnDragResult 能取到正确的 value。
     */
    private fun kotlinx.coroutines.test.TestScope.setupTodos(todos: List<TodoItem>) {
        viewModel.refreshTodosForTest(todos)
        backgroundScope.launch { viewModel.pinnedPendingTodos.collect {} }
        backgroundScope.launch { viewModel.pendingTodos.collect {} }
        backgroundScope.launch { viewModel.pinnedCompletedTodos.collect {} }
        backgroundScope.launch { viewModel.completedTodos.collect {} }
        runCurrent()
    }

    // ==================== reorderOnDragResult 测试 ====================

    /**
     * 场景：同区域拖拽（PENDING 区内）保持 zone 不变，仅重排 sortOrder
     *
     * 数据：3 个非置顶待完成（id=1,2,3, sortOrder=10000,10001,10002）
     * 拖 id=1 到 targetZoneRelativeIndex=1（即 id=2 和 id=3 之间）
     *
     * 预期：
     * - originalZone = currentZone = PENDING（crossedZone=false）
     * - 不调用 updateTodo（无字段翻转）
     * - 调用 updateTodos 重排 sortOrder：
     *   - id=1 的 sortOrder=10001
     *   - id=2 的 sortOrder=10000
     *   - id=3 的 sortOrder=10002（不变）
     */
    @Test
    fun `reorderOnDragResult 同区拖拽保持 zone 不变仅重排 sortOrder`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 10000),
            testTodo(2, isPinned = false, sortOrder = 10001),
            testTodo(3, isPinned = false, sortOrder = 10002)
        )
        setupTodos(todos)

        val draggedTodo = todos[0]
        val dragResult = ZoneDragResult(
            originalZone = TodoZone.PENDING,
            currentZone = TodoZone.PENDING,
            finalIsPinned = false,
            finalStatus = 0,
            crossedZone = false
        )

        viewModel.reorderOnDragResult(
            draggedItemId = 1L,
            draggedTodo = draggedTodo,
            dragResult = dragResult,
            targetZoneRelativeIndex = 1
        )

        // 不调用 updateTodo（无字段翻转）
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(any()) }
        // 调用 updateTodos 重排 sortOrder：id=1 → 10001, id=2 → 10000
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[1L]?.sortOrder == 10001 && byId[2L]?.sortOrder == 10000
        }) }
    }

    /**
     * 场景：跨区拖拽 PENDING → COMPLETED，翻转 status 并设置 completedAt
     *
     * 数据：2 个非置顶待完成（id=1,2）+ 1 个非置顶已完成（id=3, sortOrder=30000）
     * 拖 id=1 到 COMPLETED 区 targetZoneRelativeIndex=0
     *
     * 预期：
     * - finalItem.status = 1, finalItem.completedAt != null
     * - updateTodos 被调用：
     *   - id=1 的 status=1, completedAt != null, sortOrder=30000, isPinned=false
     *   - id=3 的 sortOrder=30001
     *   - id=2 的 sortOrder 不变（10001，PENDING 区未受影响区域不重排）
     *
     * 注：handleTaskCompleted 会因 _corgiData.value == null 而 return，
     *     不影响 updateTodos 的调用验证。
     */
    @Test
    fun `reorderOnDragResult 跨区拖拽 PENDING 到 COMPLETED 翻转 status`() = runTest(testDispatcher) {
        val now = System.currentTimeMillis()
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 10000),
            testTodo(2, isPinned = false, sortOrder = 10001),
            testTodo(3, isPinned = false, sortOrder = 30000).copy(status = 1, completedAt = now)
        )
        setupTodos(todos)

        val draggedTodo = todos[0]
        val dragResult = ZoneDragResult(
            originalZone = TodoZone.PENDING,
            currentZone = TodoZone.COMPLETED,
            finalIsPinned = false,
            finalStatus = 1,
            crossedZone = true
        )

        viewModel.reorderOnDragResult(
            draggedItemId = 1L,
            draggedTodo = draggedTodo,
            dragResult = dragResult,
            targetZoneRelativeIndex = 0
        )

        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            // id=1 翻转为完成
            byId[1L]?.status == 1 &&
                byId[1L]?.completedAt != null &&
                byId[1L]?.sortOrder == 30000 &&
                byId[1L]?.isPinned == false &&
                // id=3 顺延为 30001
                byId[3L]?.sortOrder == 30001
        }) }
    }

    /**
     * 场景：sortOrder 按 zone 分段分配（PENDING → PINNED_PENDING 跨入置顶区）
     *
     * 数据：1 个置顶待完成（id=1, sortOrder=0）+ 1 个非置顶待完成（id=2, sortOrder=10000）
     * 拖 id=2 到 PINNED_PENDING 区 targetZoneRelativeIndex=0
     *
     * 预期：
     * - finalItem.isPinned=true, finalStatus=0
     * - updateTodos 被调用：
     *   - id=2 在 PINNED_PENDING 区，sortOrder=0，isPinned=true
     *   - id=1 在 PINNED_PENDING 区，sortOrder=1（被顺延）
     *   - PENDING 区已空（id=2 已移走），不参与更新
     */
    @Test
    fun `reorderOnDragResult sortOrder 按 zone 分段分配`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 10000)
        )
        setupTodos(todos)

        val draggedTodo = todos[1]
        val dragResult = ZoneDragResult(
            originalZone = TodoZone.PENDING,
            currentZone = TodoZone.PINNED_PENDING,
            finalIsPinned = true,
            finalStatus = 0,
            crossedZone = true
        )

        viewModel.reorderOnDragResult(
            draggedItemId = 2L,
            draggedTodo = draggedTodo,
            dragResult = dragResult,
            targetZoneRelativeIndex = 0
        )

        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            // id=2 跨入 PINNED_PENDING 区，sortOrder=0，isPinned=true
            byId[2L]?.sortOrder == 0 &&
                byId[2L]?.isPinned == true &&
                byId[2L]?.status == 0 &&
                // id=1 顺延为 sortOrder=1
                byId[1L]?.sortOrder == 1
        }) }
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
