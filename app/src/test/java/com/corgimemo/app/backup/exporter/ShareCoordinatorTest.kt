package com.corgimemo.app.backup.exporter

import android.content.Context
import com.corgimemo.app.data.model.TodoItem
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ShareCoordinator 失败测试（TDD - 仅写测试，验证实现是否正确）
 *
 * ShareCoordinator 是分享流程的统一入口，负责协调"待办编辑页"和"多选模式"两处入口：
 * - 单个待办：直接调 shareTodoAsImage（单张分享）
 * - 多个待办：通过 onShowDialog 回调显示选择弹窗（合并/一条条）
 * - 未保存分组：通过 onShowSnackBar 提示用户先保存
 *
 * 覆盖以下场景：
 * 1. hasUnsavedGroups=true → 不进入分享流程，仅触发 SnackBar
 * 2. 仅 mainTodo（size=1）→ 不弹 Dialog
 * 3. mainTodo + 多个 subTodos（size>1）→ 弹 Dialog 传总数量
 * 4. 多选模式 size=1 → 不弹 Dialog
 * 5. 多选模式 size>1 → 弹 Dialog 传数量
 * 6. 多选模式 size=0 → 静默 return
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareCoordinatorTest {

    /**
     * 创建一个最小可用的 TodoItem 测试样例
     *
     * 必填字段（title/categoryId/priority/status/repeatType/createdAt/updatedAt）都给默认值。
     * 业务无关字段（geofence/voiceNote/hasSubTasks 等）保持默认。
     *
     * @param id 主键
     * @param title 待办标题
     * @return 测试用 TodoItem
     */
    private fun makeTodo(id: Long, title: String = "测试 todo"): TodoItem = TodoItem(
        id = id,
        title = title,
        content = null,
        categoryId = 0L,
        priority = 0,
        status = 0,
        startDate = null,
        dueDate = null,
        estimatedDurationMinutes = null,
        reminderTime = null,
        repeatType = 0,
        createdAt = 0L,
        updatedAt = 0L
    )

    /**
     * 场景 1：hasUnsavedGroups=true 时不进入分享流程
     *
     * 期望：
     * - 触发 onShowSnackBar("请先保存所有分组")
     * - 不弹 Dialog
     * - 不调 context.startActivity
     */
    @Test
    fun `hasUnsavedGroups true 时不进入分享流程 仅触发 SnackBar 回调`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var snackBarMessage: String? = null
        var dialogCount: Int? = null
        val todos = listOf(makeTodo(1), makeTodo(2))

        ShareCoordinator.shareTodosFromEdit(
            context = context,
            mainTodo = makeTodo(0),
            savedSubTodos = todos,
            categories = emptyList(),
            hasUnsavedGroups = true,
            onShowSnackBar = { msg -> snackBarMessage = msg },
            onShowDialog = { count -> dialogCount = count }
        )

        assertEquals("请先保存所有分组", snackBarMessage)
        assertNull("未保存分组时不应弹 Dialog", dialogCount)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    /**
     * 场景 2：仅 mainTodo（size=1）时不弹 Dialog
     *
     * 期望：
     * - 不弹 Dialog（直接走 shareTodoAsImage 路径）
     *
     * 注：shareTodoAsImage 内部使用 fire-and-forget CoroutineScope.launch，
     * runBlocking 不会等待其内部协程完成，测试只验证 Dialog 未弹即可。
     */
    @Test
    fun `mainTodo only size 等于 1 时不弹 Dialog`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var dialogCount: Int? = null

        ShareCoordinator.shareTodosFromEdit(
            context = context,
            mainTodo = makeTodo(1),
            savedSubTodos = emptyList(),
            categories = emptyList(),
            hasUnsavedGroups = false,
            onShowSnackBar = { /* noop */ },
            onShowDialog = { count -> dialogCount = count }
        )

        assertNull("size=1 时不应弹 Dialog", dialogCount)
    }

    /**
     * 场景 3：mainTodo + 多个 subTodos（size>1）时弹 Dialog
     *
     * 期望：
     * - 弹 Dialog 且参数为 (1 mainTodo + 3 subTodos) = 4
     */
    @Test
    fun `size 大于 1 时弹 Dialog 传入正确数量 mainTodo 加 subTodos`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var dialogCount: Int? = null
        val todos = listOf(makeTodo(1), makeTodo(2), makeTodo(3))

        ShareCoordinator.shareTodosFromEdit(
            context = context,
            mainTodo = makeTodo(0),
            savedSubTodos = todos,
            categories = emptyList(),
            hasUnsavedGroups = false,
            onShowSnackBar = { /* noop */ },
            onShowDialog = { count -> dialogCount = count }
        )

        assertEquals(4, dialogCount)
    }

    /**
     * 场景 4：多选模式 size=1 时不弹 Dialog
     */
    @Test
    fun `多选模式 shareTodos size 等于 1 不弹 Dialog`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var dialogCount: Int? = null

        ShareCoordinator.shareTodos(
            context = context,
            todos = listOf(makeTodo(1)),
            categories = emptyList(),
            onShowDialog = { count -> dialogCount = count }
        )

        assertNull("size=1 时不应弹 Dialog", dialogCount)
    }

    /**
     * 场景 5：多选模式 size>1 时弹 Dialog 传入正确数量
     */
    @Test
    fun `多选模式 shareTodos size 大于 1 弹 Dialog 传入正确数量`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var dialogCount: Int? = null
        val todos = listOf(makeTodo(1), makeTodo(2), makeTodo(3))

        ShareCoordinator.shareTodos(
            context = context,
            todos = todos,
            categories = emptyList(),
            onShowDialog = { count -> dialogCount = count }
        )

        assertEquals(3, dialogCount)
    }

    /**
     * 场景 6：多选模式 size=0 时静默 return
     *
     * 期望：
     * - 不弹 Dialog
     * - 不调 context.startActivity
     * - 不抛异常
     */
    @Test
    fun `多选模式 shareTodos size 等于 0 静默 return 不抛异常不弹 Dialog 不调 startActivity`() = runBlocking {
        val context = mockk<Context>(relaxed = true)
        var dialogCount: Int? = null

        ShareCoordinator.shareTodos(
            context = context,
            todos = emptyList(),
            categories = emptyList(),
            onShowDialog = { count -> dialogCount = count }
        )

        assertNull("空列表不应弹 Dialog", dialogCount)
        verify(exactly = 0) { context.startActivity(any()) }
    }
}
