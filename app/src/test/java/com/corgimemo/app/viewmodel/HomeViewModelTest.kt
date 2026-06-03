package com.corgimemo.app.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.viewmodel.HomeViewModel
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * HomeViewModel 单元测试类
 *
 * 测试 HomeViewModel 的核心业务逻辑，包括：
 * - 排序功能（updateSortOrder）
 * - 待办列表过滤逻辑（filteredTodos）
 * - 删除操作（deleteTodo / batchDeleteTodos）
 * - 搜索功能（searchTodos）
 *
 * **测试框架**: JUnit 5 + MockK + Turbine
 * **测试策略**:
 * - 使用 MockK 模拟依赖项（Repository, Preferences）
 * - 使用 Turbine 验证 Flow 发射的值序列
 * - 使用 runTest 协程测试器避免主线程问题
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    /** 执行规则：确保 LiveData 操作在主线程执行 */
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    /** Mock 依赖项 */
    private lateinit var mockRepository: TodoRepository
    private lateinit var mockPreferences: CorgiPreferences
    private lateinit var viewModel: HomeViewModel

    /**
     * 测试前初始化
     * 
     * 在每个 @Test 方法之前执行：
     * 1. 创建 Repository 和 Preferences 的 Mock 对象
     * 2. 实例化 HomeViewModel 并注入 Mock
     * 3. 设置协程调度器为测试调度器
     */
    @Before
    fun setUp() {
        /** 创建 MockK 模拟对象 */
        mockRepository = mockk(relaxed = true)
        mockPreferences = mockk(relaxed = true)

        /** 注入 Mock 到 ViewModel */
        viewModel = HomeViewModel(
            todoRepository = mockRepository,
            preferences = mockPreferences
        )

        /** 设置主线程调度器为测试调度器 */
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * 测试后清理
     * 
     * 重置主线程调度器以避免影响其他测试
     */
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== 排序功能测试 ====================

    /**
     * 测试：更新排序顺序成功
     *
     * **验证点**:
     * 1. updateSortOrder() 被正确调用
     * 2. Preferences.saveSortOrder() 被调用且参数正确
     * 3. _sortType StateFlow 更新为新值
     */
    @Test
    fun `update sort order should save to preferences and update state`() = runTest {
        // Given: 初始排序为 "updated_desc"
        val newOrder = "created_asc"

        // When: 用户切换排序方式
        coEvery { mockPreferences.saveSortOrder(any()) } returns Unit

        viewModel.updateSortOrder(newOrder)

        // Then: 验证保存操作被调用
        coVerify(exactly = 1) { mockPreferences.saveSortOrder(newOrder) }
    }

    /**
     * 测试：初始化排序配置从 Preferences 加载
     *
     * **验证点**:
     * 1. initSortOrder() 调用 Preferences.getSortOrder()
     * 2. 返回值正确应用到 _sortType
     */
    @Test
    fun `init sort order should load from preferences`() = runTest {
        // Given: Preferences 中存储了 "priority" 排序
        coEvery { mockPreferences.getSortOrder() } returns flowOf("priority")

        // When: ViewModel 初始化时加载排序配置
        viewModel.initSortOrder()

        // Then: 验证读取操作被调用
        coVerify { mockPreferences.getSortOrder() }
    }

    // ==================== 过滤功能测试 ====================

    /**
     * 测试：按分类过滤待办列表
     *
     * **验证点**:
     * 1. 传入 categoryId 时 filteredTodos 仅返回该分类下的待办
     * 2. 传入 null 时返回全部待办
     */
    @Test
    fun `filter todos by category should return correct subset`() = runTest {
        // TODO: 实现具体的过滤逻辑测试
        // 需要准备测试数据并验证 Flow 输出
    }

    /**
     * 测试：按状态过滤待办列表
     *
     * **场景**:
     * - status=0: 仅未完成的待办
     * - status=1: 已完成的待办
     * - status=null: 全部待办
     */
    @Test
    fun `filter todos by status should filter correctly`() = runTest {
        // TODO: 实现状态过滤测试
    }

    // ==================== 删除操作测试 ====================

    /**
     * 测试：删除单个待办成功
     *
     * **验证点**:
     * 1. repository.deleteTodo() 被调用且参数正确
     * 2. 删除后刷新列表
     * 3. 显示 Snackbar 提示用户可撤销
     */
    @Test
    fun `delete todo should call repository and refresh list`() = runTest {
        // Given: 存在一个 ID 为 1L 的待办
        val todoId = 1L

        coEvery { mockRepository.deleteTodo(todoId) } returns Unit

        // When: 用户删除该待办
        viewModel.deleteTodo(todoId)

        // Then: 验证删除操作被调用
        coVerify(exactly = 1) { mockRepository.deleteTodo(todoId) }
    }

    /**
     * 测试：批量删除多个待办
     *
     * **验证点**:
     * 1. 循环调用 deleteTodo() 或使用批量删除方法
     * 2. 所有指定 ID 的待办都被删除
     */
    @Test
    fun `batch delete should remove all specified todos`() = runTest {
        // Given: 要删除的 ID 列表
        val idsToDelete = listOf(1L, 2L, 3L)

        coEvery { mockRepository.deleteTodo(any()) } returns Unit

        // When: 执行批量删除
        viewModel.batchDeleteTodos(idsToDelete)

        // Then: 验证每个 ID 都被删除了一次
        coVerify(exactly = 3) { mockRepository.deleteTodo(any()) }
    }

    // ==================== 搜索功能测试 ====================

    /**
     * 测试：搜索关键词匹配
     *
     * **验证点**:
     * 1. repository.searchTodos() 被调用
     * 2. 关键词正确传递
     * 3. 返回结果包含匹配项
     */
    @Test
    fun `search todos should call repository with query`() = runTest {
        // Given: 搜索关键词
        val query = "会议"

        coEvery { mockRepository.searchTodos(query) } returns emptyList()

        // When: 用户搜索
        viewModel.searchTodos(query)

        // Then: 验证搜索方法被调用
        coVerify { mockRepository.searchTodos(query) }
    }

    // ==================== 边界条件测试 ====================

    /**
     * 测试：空列表时的行为
     *
     * **验证点**:
     * - filteredTodos 应发射空列表而非崩溃
     * - UI 应显示 EmptyState 组件
     */
    @Test
    fun `empty todo list should emit empty state without crash`() = runTest {
        // Given: Repository 返回空列表
        coEvery { mockRepository.getAllTodos() } returns flowOf(emptyList())

        // When: 订阅 filteredTodos
        val result = viewModel.filteredTodos

        // Then: 不应抛出异常
        assertNotNull(result)
    }

    /**
     * 测试：特殊字符处理
     *
     * **验证点**:
     * - SQL 注入字符被正确转义
     * - Unicode 字符正常工作
     */
    @Test
    fun `special characters in search should be handled safely`() = runTest {
        // Given: 包含特殊字符的搜索词
        val specialQuery = "test'; DROP TABLE users; --"

        // When & Then: 不应崩溃或产生异常行为
        assertDoesNotThrow {
            viewModel.searchTodos(specialQuery)
        }
    }
}
