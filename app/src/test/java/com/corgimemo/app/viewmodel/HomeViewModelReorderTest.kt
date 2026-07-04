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
 * - reorderOnDisplayList：单拖偏移校正、跨区域拖拽、dividerIndex=-1 处理
 *
 * 说明：HomeViewModel 构造函数有 11 个依赖 + Context，全部使用 relaxed mock。
 * 由于 reorderOnDisplayList 仅依赖 todoRepository，
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
     * 测试辅助：注入数据并激活 pendingTodos StateFlow
     *
     * pendingTodos 使用 stateIn(WhileSubscribed)，需要活跃订阅者才会处理 _todos 的变更。
     * setUp 与测试方法是独立的 runTest 作用域，所以必须在每个测试内订阅。
     *
     * Task 4：同时激活 4 个独立 zone StateFlow（pinnedPendingTodos /
     * pendingTodosNew / pinnedCompletedTodos / completedTodos），
     * 让 reorderOnDragResult 能取到正确的 value。
     */
    private fun kotlinx.coroutines.test.TestScope.setupTodos(todos: List<TodoItem>) {
        viewModel.refreshTodosForTest(todos)
        backgroundScope.launch { viewModel.pendingTodos.collect {} }
        backgroundScope.launch { viewModel.pinnedPendingTodos.collect {} }
        backgroundScope.launch { viewModel.pendingTodosNew.collect {} }
        backgroundScope.launch { viewModel.pinnedCompletedTodos.collect {} }
        backgroundScope.launch { viewModel.completedTodos.collect {} }
        runCurrent()
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
    fun `Case A 置顶大于等于4 跨边界拖拽应标记为完成`() = runTest(testDispatcher) {
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
        setupTodos(todos)

        // When: Case A 真实 dividerIndex = 11
        // N5(原始 index=10)拖到 C1 位置(index=12,即第 13 项,已完成的顶部)
        viewModel.reorderOnDisplayList(
            fromIndex = 10,
            toIndex = 12,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
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
    fun `Case B 置顶小于4 跨边界拖拽应标记为完成`() = runTest(testDispatcher) {
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
        setupTodos(todos)

        // When: Case B 真实 dividerIndex = 8
        // N5(原始 index=7)拖到 C1 位置(index=9)
        viewModel.reorderOnDisplayList(
            fromIndex = 7,
            toIndex = 9,
            dividerIndex = 8,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // Then: updateTodo 被调用(N5 跨区完成)
        coVerify(atLeast = 1) { mockTodoRepository.updateTodo(match { it.id == 7L && it.status == 1 }) }
    }

    /**
     * 场景：边界检查 - dividerIndex = -1 (无已完成区)
     * 预期：reorderOnDisplayList 不会因 totalSize 校验而误判
     */
    @Test
    fun `dividerIndex 负一表示无已完成区不应误判跨区`() = runTest(testDispatcher) {
        // Given: 全部都是待办,无已完成
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1)
        )
        setupTodos(todos)

        // When: dividerIndex = -1,fromIndex 越界 → 应当直接 return
        viewModel.reorderOnDisplayList(
            fromIndex = 0,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // Then: 正常执行(同区排序),不调用 updateTodo (无状态变更)
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(any()) }
    }

    /**
     * 场景：Case B（置顶 < 4）同区拖拽，9 待完成 + 1 已完成
     *
     * displayItems 结构：
     * [PendingDivider(0), 1(1), 2(2), 3(3), ..., 9(9), CompletedDivider(10), 10(11)]
     * pendingStartIndex = 1, midPendingDividerIndex = -1, dividerIndex = 10
     *
     * 拖"1"到 2、3 之间 → from=1, to=2
     * 预期 pending 顺序：[2,1,3,4,5,6,7,8,9]（不是 [1,3,2,...]）
     */
    @Test
    fun `Case B 同区拖拽应正确偏移`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2),
            testTodo(4, isPinned = false, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        setupTodos(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = 10,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // 验证：1 的 sortOrder 应为 1（位置 2），2 的 sortOrder 应为 0（位置 1）
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[1L]?.sortOrder == 1 && byId[2L]?.sortOrder == 0
        }) }
    }

    /**
     * 场景：Case A（置顶 ≥ 4）置顶区内拖拽
     *
     * displayItems 结构：
     * [PinnedDivider(0), P1(1), P2(2), P3(3), P4(4), PendingDivider(5), N1(6), ..., N5(10), CompletedDivider(11), C1(12)]
     * pendingStartIndex = 1, midPendingDividerIndex = 5, dividerIndex = 11
     *
     * 拖 P1 到 P2、P3 之间 → from=1, to=2（均在置顶区，< mid=5）
     * 偏移 = pendingStartIndex(1) + 0 = 1
     * 预期 pendingList 顺序：[P2, P1, P3, P4, N1, N2, N3, N4, N5]
     */
    @Test
    fun `Case A 置顶区内拖拽应正确偏移`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        setupTodos(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[1L]?.sortOrder == 1 && byId[2L]?.sortOrder == 0
        }) }
    }

    /**
     * 场景：Case A（置顶 ≥ 4）非置顶区内拖拽
     *
     * displayItems 同上：pendingStartIndex = 1, mid = 5, dividerIndex = 11
     * 拖 N1(6) 到 N2(7)、N3(8) 之间 → from=6, to=7（均 > mid=5）
     * 偏移 = 1 + 1 = 2
     * 预期 pendingList 非置顶部分：[N2, N1, N3, N4, N5]
     */
    @Test
    fun `Case A 非置顶区内拖拽应正确偏移`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6),
            testTodo(8, isPinned = false, sortOrder = 7),
            testTodo(9, isPinned = false, sortOrder = 8),
            testTodo(10, isPinned = false, sortOrder = 9).copy(status = 1)
        )
        setupTodos(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 6,
            toIndex = 7,
            dividerIndex = 11,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        // N1(id=5, 原 sortOrder=4) → 新位置 5；N2(id=6, 原 5) → 新 4
        // 拖 N1 到 N2、N3 之间，应交换 N1 和 N2 的 sortOrder
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[5L]?.sortOrder == 5 && byId[6L]?.sortOrder == 4
        }) }
    }

    /**
     * 场景：dividerIndex = -1（无已完成区）同区拖拽应正常持久化
     *
     * 旧 bug：dividerIndex=-1 时所有项被误判为已完成区 → removeAt 越界 → 静默 return
     * 修复后：dividerIndex<0 全部按 pending 处理
     */
    @Test
    fun `dividerIndex 负一同区拖拽应持久化`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2)
        )
        setupTodos(todos)

        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // 应当调用 updateTodos（而非静默跳过）
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(any()) }
    }

    // ==================== 置顶区跨区误判回归测试 ====================

    /**
     * 场景：Case A（置顶≥4）置顶项拖到置顶区内，不应翻转 isPinned
     *
     * displayItems 结构：
     * [PinnedDivider(0), P1(1), P2(2), P3(3), P4(4), PendingDivider(5), N1(6), ..., N6(11)]
     * pendingStartIndex = 1, midPendingDividerIndex = 5, dividerIndex = -1（无已完成区）
     *
     * 拖 P4(4) 到 P1(1) 上方 → fromIndex=4, toIndex=1
     * 预期：crossedPinnedZone=false（置顶区内移动，邻居是 P1 而非 PinnedDivider）
     */
    @Test
    fun `Case A 置顶区内拖拽不应翻转 isPinned`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6)
        )
        setupTodos(todos)

        // crossedPinnedZone=false（置顶区内移动，邻居是 P1 而非 PinnedDivider）
        viewModel.reorderOnDisplayList(
            fromIndex = 4,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        // 验证：不调用 updateTodo 翻转 isPinned（P4 仍为 isPinned=true）
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(match { it.id == 4L && !it.isPinned }) }
        // 验证：调用 updateTodos 重排 sortOrder（P4 新 sortOrder=0，P1 新 sortOrder=1）
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val byId = updates.associateBy { it.id }
            byId[4L]?.sortOrder == 0 && byId[1L]?.sortOrder == 1 && byId[4L]?.isPinned == true
        }) }
    }

    /**
     * 场景：Case A（置顶≥4）非置顶项拖入置顶区，应翻转 isPinned=true
     *
     * displayItems 结构同上
     * 拖 N1(6) 到 P1(1) 上方 → fromIndex=6, toIndex=1
     * 预期：crossedPinnedZone=true（跨入置顶区，邻居是 P1=true）
     */
    @Test
    fun `Case A 非置顶项拖入置顶区应翻转 isPinned`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4),
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6)
        )
        setupTodos(todos)

        // crossedPinnedZone=true（跨入置顶区，邻居是 P1=true）
        viewModel.reorderOnDisplayList(
            fromIndex = 6,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = true,
            pendingStartIndex = 1,
            midPendingDividerIndex = 5
        )

        // 验证：调用 updateTodo 翻转 N1(id=5) 的 isPinned=true
        coVerify(atLeast = 1) { mockTodoRepository.updateTodo(match { it.id == 5L && it.isPinned }) }
    }

    /**
     * 场景：N5 首次拖入置顶区后再次在置顶区内拖动
     *
     * 这是用户报告的 "二次拖拽跳跃" bug 场景的 ViewModel 层验证：
     * - 初始：N1-N4 已 pinned，N5 已 pinned（首次拖入后的状态）
     * - 二次拖动：N5 在置顶区内移动，crossedPinnedZone=false（修复后正确值）
     * - 预期：不调用 updateTodo 翻转 isPinned（N5 保持 isPinned=true）
     *         仅调用 updateTodos 重排 sortOrder
     *
     * 回归保护：确保 ViewModel 在 crossedPinnedZone=false 时不会误翻转 isPinned。
     */
    @Test
    fun `二次拖拽已置顶项不应翻转 isPinned`() = runTest(testDispatcher) {
        // Given: N1-N4 已 pinned + N5 已 pinned（首次拖入后的状态）+ N6-N7 pending
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = true, sortOrder = 3),
            testTodo(5, isPinned = true, sortOrder = 4),   // N5 已 pinned
            testTodo(6, isPinned = false, sortOrder = 5),
            testTodo(7, isPinned = false, sortOrder = 6)
        )
        setupTodos(todos)

        // When: 二次拖动 N5(置顶区内移动)，crossedPinnedZone=false（修复后正确值）
        // displayItems: [PinnedDivider(0), P1(1), P2(2), P3(3), P4(4), N5(5), PendingDivider(6), N6(7), N7(8)]
        // 拖 N5(5) 到 P4(4) 上方 → fromIndex=5, toIndex=4
        viewModel.reorderOnDisplayList(
            fromIndex = 5,
            toIndex = 4,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = 6
        )

        // Then: 不调用 updateTodo 翻转 N5 的 isPinned
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(match { it.id == 5L && !it.isPinned }) }
        // Then: 调用 updateTodos 重排 sortOrder，且 N5 仍为 isPinned=true
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(match { updates ->
            val n5 = updates.find { it.id == 5L }
            n5?.isPinned == true
        }) }
    }

    /**
     * 场景：pinnedCount=1 时跨区拖拽（N 拖到 P 上方）
     *
     * 验证新阈值 pinnedCount >= 1 下，PinnedDivider 显示，
     * 非置顶项拖到置顶区时 isPinned 正确翻转。
     *
     * displayItems: [PinnedDivider(0), P1(1), PendingDivider(2), N1(3), N2(4)]
     * 拖 N1(3) 到 P1(1) 上方 → toIndex=1, crossedPinnedZone=true
     * 预期：N1.isPinned 翻转为 true
     */
    @Test
    fun `pinnedCount 为 1 时跨区拖拽应翻转 isPinned`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2)
        )
        setupTodos(todos)

        // displayItems: [PinnedDivider(0), P1(1), PendingDivider(2), N1(3), N2(4)]
        // 拖 N1(3) 到 P1(1) 上方 → fromIndex=3, toIndex=1
        viewModel.reorderOnDisplayList(
            fromIndex = 3,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = true,
            pendingStartIndex = 1,
            midPendingDividerIndex = 2
        )

        // 验证：N1(id=2) isPinned 翻转为 true
        coVerify(exactly = 1) { mockTodoRepository.updateTodo(match { it.id == 2L && it.isPinned }) }
    }

    /**
     * 场景：pinnedCount=3 时跨区拖拽（N 拖到 P 上方）
     *
     * 验证阈值不再是 4，pinnedCount=3 也能正常跨区拖拽。
     *
     * displayItems: [PinnedDivider(0), P1(1), P2(2), P3(3), PendingDivider(4), N1(5), N2(6)]
     * 拖 N1(5) 到 P1(1) 上方 → toIndex=1, crossedPinnedZone=true
     * 预期：N1.isPinned 翻转为 true
     */
    @Test
    fun `pinnedCount 为 3 时跨区拖拽应翻转 isPinned`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = true, sortOrder = 0),
            testTodo(2, isPinned = true, sortOrder = 1),
            testTodo(3, isPinned = true, sortOrder = 2),
            testTodo(4, isPinned = false, sortOrder = 3),
            testTodo(5, isPinned = false, sortOrder = 4)
        )
        setupTodos(todos)

        // displayItems: [PinnedDivider(0), P1(1), P2(2), P3(3), PendingDivider(4), N1(5), N2(6)]
        // 拖 N1(5) 到 P1(1) 上方 → fromIndex=5, toIndex=1
        viewModel.reorderOnDisplayList(
            fromIndex = 5,
            toIndex = 1,
            dividerIndex = -1,
            crossedPinnedZone = true,
            pendingStartIndex = 1,
            midPendingDividerIndex = 4
        )

        // 验证：N1(id=4) isPinned 翻转为 true
        coVerify(exactly = 1) { mockTodoRepository.updateTodo(match { it.id == 4L && it.isPinned }) }
    }

    /**
     * 场景：pinnedCount=0 时同区拖拽不翻转 isPinned
     *
     * 验证无置顶时，待完成区内拖拽不会误翻转 isPinned。
     *
     * displayItems: [PendingDivider(0), N1(1), N2(2), N3(3)]
     * 拖 N1(1) 到 N2(2) 上方 → toIndex=2, crossedPinnedZone=false
     * 预期：不调用 updateTodo 翻转 isPinned，但仍调用 updateTodos 重排 sortOrder
     */
    @Test
    fun `pinnedCount 为 0 时同区拖拽不应翻转 isPinned`() = runTest(testDispatcher) {
        val todos = listOf(
            testTodo(1, isPinned = false, sortOrder = 0),
            testTodo(2, isPinned = false, sortOrder = 1),
            testTodo(3, isPinned = false, sortOrder = 2)
        )
        setupTodos(todos)

        // displayItems: [PendingDivider(0), N1(1), N2(2), N3(3)]
        // 拖 N1(1) 到 N2(2) 上方 → fromIndex=1, toIndex=2
        viewModel.reorderOnDisplayList(
            fromIndex = 1,
            toIndex = 2,
            dividerIndex = -1,
            crossedPinnedZone = false,
            pendingStartIndex = 1,
            midPendingDividerIndex = -1
        )

        // 验证：不调用 updateTodo（isPinned 未变，status 未变）
        coVerify(exactly = 0) { mockTodoRepository.updateTodo(any()) }
        // 验证：调用 updateTodos 重排 sortOrder
        coVerify(atLeast = 1) { mockTodoRepository.updateTodos(any()) }
    }

    // ==================== Task 4: reorderOnDragResult 测试 ====================

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
