package com.corgimemo.app.viewmodel

import android.content.Context
import com.corgimemo.app.animation.HapticFeedbackController
import com.corgimemo.app.data.local.datastore.CorgiPreferences
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * HomeViewModel "置顶"区域展开/折叠相关 ViewModel 逻辑单元测试
 *
 * 覆盖：
 * - showPinned 默认值为 true
 * - toggleShowPinned 翻转状态并触发持久化
 * - toggleShowPinned 两次回到原状态
 *
 * 说明：HomeViewModel 构造函数有 12 个依赖 + Context，全部使用 relaxed mock。
 * 关键点：必须在 setUp 中 stub corgiPreferences 的所有 booleanFlow
 * （showCompleted/showPinned/hideDetails/hideCompletedItems），
 * 否则 init 块中的 collect 会因 relaxed mock 返回 null Flow 而崩溃。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelPinnedButtonTest {

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

    @Before
    fun setUp() = runTest {
        // 创建 13 个 relaxed mock（12 依赖 + Context）
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

        // 桩：todoRepository 的 Flow，避免 filteredTodos 初始化 NPE
        coEvery { mockTodoRepository.getAllTodos() } returns flowOf(emptyList())
        coEvery { mockTodoRepository.observeAllSorted() } returns flowOf(emptyList())

        // 桩：corgiPreferences 的所有 booleanFlow，匹配持久化默认值
        // （必须 stub，否则 init 块中 collect 会因 relaxed mock 返回 null 而崩溃）
        coEvery { mockCorgiPreferences.showCompleted } returns flowOf(false)
        coEvery { mockCorgiPreferences.showPinned } returns flowOf(true)
        coEvery { mockCorgiPreferences.hideDetails } returns flowOf(false)
        coEvery { mockCorgiPreferences.hideCompletedItems } returns flowOf(false)

        // 必须在 viewModel 构造前 setMain，确保 init 块中 viewModelScope.launch 立即执行
        Dispatchers.setMain(UnconfinedTestDispatcher())

        // 构造 ViewModel，命名参数顺序与 HomeViewModel 构造函数完全一致
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
        Dispatchers.resetMain()
    }

    /**
     * 场景：ViewModel 初始化，corgiPreferences.showPinned 默认发射 true
     * 预期：viewModel.showPinned.value == true
     */
    @Test
    fun `showPinned 默认值为 true`() = runTest {
        assertTrue(viewModel.showPinned.value)
    }

    /**
     * 场景：用户点击置顶按钮
     * 预期：
     * - showPinned 状态翻转（true → false 或 false → true）
     * - corgiPreferences.setShowPinned 被调用一次，参数为翻转后的值
     */
    @Test
    fun `toggleShowPinned 翻转状态并触发持久化`() = runTest {
        // Given: 初始状态
        val initial = viewModel.showPinned.value

        // When: 调用 toggleShowPinned
        viewModel.toggleShowPinned()

        // Then: 状态翻转
        assertEquals(!initial, viewModel.showPinned.value)
        // Then: 持久化方法被调用 1 次，参数为翻转后的值
        coVerify(exactly = 1) { mockCorgiPreferences.setShowPinned(!initial) }
    }

    /**
     * 场景：用户连续点击两次置顶按钮
     * 预期：
     * - showPinned 状态回到初始值（双翻转）
     * - corgiPreferences.setShowPinned 被调用 2 次
     */
    @Test
    fun `toggleShowPinned 两次回到原状态`() = runTest {
        // Given: 初始状态
        val initial = viewModel.showPinned.value

        // When: 连续点击两次
        viewModel.toggleShowPinned()
        viewModel.toggleShowPinned()

        // Then: 状态回到 initial
        assertEquals(initial, viewModel.showPinned.value)
        // Then: 持久化方法被调用 2 次
        coVerify(exactly = 2) { mockCorgiPreferences.setShowPinned(any()) }
    }
}
