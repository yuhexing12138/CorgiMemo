package com.corgimemo.app.data.repository

import android.content.Context
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.SubTask
import kotlinx.coroutines.flow.Flow

/**
 * 子任务自动完成父任务的结果
 *
 * @property updatedSubTask 更新后的子任务
 * @property parentTodoCompleted 父任务是否被自动完成
 */
data class SubTaskToggleResult(
    val updatedSubTask: SubTask?,
    val parentTodoCompleted: Boolean = false
)

/**
 * 子任务管理器
 * 统一管理子任务的增删改查、进度计算等操作
 */
object SubTaskManager {

    /**
     * 获取待办的所有子任务
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 子任务列表
     */
    suspend fun getSubTasks(context: Context, todoId: Long): List<SubTask> {
        val database = CorgiMemoDatabase.getDatabase(context)
        return database.subTaskDao().getSubTasksByTodoId(todoId)
    }

    /**
     * 获取待办的所有子任务（Flow）
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 子任务列表 Flow
     */
    fun getSubTasksFlow(context: Context, todoId: Long): Flow<List<SubTask>> {
        val database = CorgiMemoDatabase.getDatabase(context)
        return database.subTaskDao().getSubTasksByTodoIdFlow(todoId)
    }

    /**
     * 添加子任务
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @param title 子任务标题
     * @return 新子任务的 ID
     */
    suspend fun addSubTask(context: Context, todoId: Long, title: String): Long {
        val database = CorgiMemoDatabase.getDatabase(context)
        val maxOrder = database.subTaskDao().getMaxOrder(todoId) ?: 0
        val newOrder = maxOrder + 1

        val subTask = SubTask(
            todoId = todoId,
            title = title,
            order = newOrder,
            createdAt = System.currentTimeMillis()
        )
        return database.subTaskDao().insert(subTask)
    }

    /**
     * 批量添加子任务（含完整数据，含附件字段 imagePaths / voicePaths）
     *
     * 取代旧版仅接受标题列表的重载，避免按行分摊附件时丢失 imagePaths/voicePaths。
     * 编辑器按行分摊附件到 SubTask 时调用。
     *
     * @param context 上下文
     * @param todoId 父待办 ID（会覆盖 subTask.todoId）
     * @param subTasks 子任务完整数据（含 imagePaths / voicePaths / isCompleted / order 等）
     */
    suspend fun addSubTasks(context: Context, todoId: Long, subTasks: List<SubTask>) {
        val database = CorgiMemoDatabase.getDatabase(context)
        val maxOrder = database.subTaskDao().getMaxOrder(todoId) ?: 0
        val toInsert = subTasks.mapIndexed { index, st ->
            st.copy(
                todoId = todoId,
                order = if (st.order > 0) st.order else maxOrder + index + 1,
                createdAt = if (st.createdAt > 0L) st.createdAt else System.currentTimeMillis()
            )
        }
        database.subTaskDao().insertAll(toInsert)
    }

    /**
     * 更新子任务标题
     *
     * @param context 上下文
     * @param subTaskId 子任务 ID
     * @param title 新标题
     */
    suspend fun updateSubTaskTitle(context: Context, subTaskId: Long, title: String) {
        val database = CorgiMemoDatabase.getDatabase(context)
        val subTask = database.subTaskDao().getSubTaskById(subTaskId) ?: return
        database.subTaskDao().update(subTask.copy(title = title))
    }

    /**
     * 切换子任务完成状态
     * 如果所有子任务都完成，自动完成父任务
     *
     * @param context 上下文
     * @param subTaskId 子任务 ID
     * @return 切换结果（包含更新后的子任务和父任务是否被自动完成）
     */
    suspend fun toggleSubTaskCompletion(context: Context, subTaskId: Long): SubTaskToggleResult {
        val database = CorgiMemoDatabase.getDatabase(context)
        val subTask = database.subTaskDao().getSubTaskById(subTaskId)
            ?: return SubTaskToggleResult(updatedSubTask = null)

        val newCompletedAt = if (!subTask.isCompleted) {
            System.currentTimeMillis()
        } else {
            null
        }

        database.subTaskDao().updateCompletion(
            id = subTaskId,
            isCompleted = !subTask.isCompleted,
            completedAt = newCompletedAt
        )

        val updatedSubTask = database.subTaskDao().getSubTaskById(subTaskId)

        // 检查是否所有子任务都完成（如果是切换为完成状态）
        var parentTodoCompleted = false
        if (updatedSubTask != null && updatedSubTask.isCompleted) {
            val allCompleted = areAllSubTasksCompleted(context, updatedSubTask.todoId)
            if (allCompleted) {
                // 自动完成父任务
                val todoDao = database.todoDao()
                val parentTodo = todoDao.getTodoById(updatedSubTask.todoId)
                if (parentTodo != null && parentTodo.status == 0) {
                    val currentTime = System.currentTimeMillis()
                    todoDao.update(
                        parentTodo.copy(
                            status = 1,
                            completedAt = currentTime,
                            updatedAt = currentTime
                        )
                    )
                    parentTodoCompleted = true
                }
            }
        }

        return SubTaskToggleResult(
            updatedSubTask = updatedSubTask,
            parentTodoCompleted = parentTodoCompleted
        )
    }

    /**
     * 删除子任务
     *
     * @param context 上下文
     * @param subTaskId 子任务 ID
     */
    suspend fun deleteSubTask(context: Context, subTaskId: Long) {
        val database = CorgiMemoDatabase.getDatabase(context)
        database.subTaskDao().deleteById(subTaskId)
    }

    /**
     * 删除待办的所有子任务
     *
     * @param context 上下文
     * @param todoId 待办 ID
     */
    suspend fun deleteAllSubTasks(context: Context, todoId: Long) {
        val database = CorgiMemoDatabase.getDatabase(context)
        database.subTaskDao().deleteByTodoId(todoId)
    }

    /**
     * 获取子任务进度
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 进度信息（已完成/总数）
     */
    suspend fun getProgress(context: Context, todoId: Long): SubTaskProgress {
        val database = CorgiMemoDatabase.getDatabase(context)
        val total = database.subTaskDao().getSubTaskCount(todoId)
        val completed = database.subTaskDao().getCompletedSubTaskCount(todoId)

        return SubTaskProgress(
            completed = completed,
            total = total,
            percentage = if (total > 0) completed.toFloat() / total.toFloat() else 0f
        )
    }

    /**
     * 检查所有子任务是否完成
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 是否全部完成
     */
    suspend fun areAllSubTasksCompleted(context: Context, todoId: Long): Boolean {
        val progress = getProgress(context, todoId)
        return progress.total > 0 && progress.completed == progress.total
    }

    /**
     * 获取进度文本（如 "2/5"）
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 进度文本，如果没有子任务返回 null
     */
    suspend fun getProgressText(context: Context, todoId: Long): String? {
        val progress = getProgress(context, todoId)
        return if (progress.total > 0) {
            "${progress.completed}/${progress.total}"
        } else {
            null
        }
    }
}

/**
 * 子任务进度信息
 *
 * @property completed 已完成数量
 * @property total 总数
 * @property percentage 完成百分比（0.0 - 1.0）
 */
data class SubTaskProgress(
    val completed: Int,
    val total: Int,
    val percentage: Float
)
