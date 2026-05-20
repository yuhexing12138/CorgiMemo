package com.corgimemo.app.data.repository

import android.content.Context
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.di.IoDispatcher
import com.corgimemo.app.widget.WidgetUpdateReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import java.util.Calendar

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationContext private val context: Context
) {

    /**
     * 插入单个待办
     *
     * @param todo 待办项
     * @return 插入的 ID
     */
    suspend fun insertTodo(todo: TodoItem): Long = withContext(ioDispatcher) {
        val result = todoDao.insert(todo)
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
        result
    }

    /**
     * 批量插入待办
     *
     * @param todos 待办列表
     */
    suspend fun insertTodos(todos: List<TodoItem>) = withContext(ioDispatcher) {
        todoDao.insertAll(todos)
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
    }

    /**
     * 更新待办
     *
     * @param todo 待办项
     */
    suspend fun updateTodo(todo: TodoItem) = withContext(ioDispatcher) {
        todoDao.update(todo)
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
    }

    /**
     * 删除待办
     *
     * @param todo 待办项
     */
    suspend fun deleteTodo(todo: TodoItem) = withContext(ioDispatcher) {
        todoDao.delete(todo)
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
    }

    /**
     * 根据 ID 删除待办
     *
     * @param todoId 待办 ID
     */
    suspend fun deleteTodoById(todoId: Long) = withContext(ioDispatcher) {
        todoDao.deleteById(todoId)
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
    }

    /**
     * 删除所有待办
     */
    suspend fun deleteAllTodos() = withContext(ioDispatcher) {
        todoDao.deleteAll()
        WidgetUpdateReceiver.sendRefreshBroadcast(context)
    }

    /**
     * 根据 ID 获取待办
     *
     * @param todoId 待办 ID
     * @return 待办项
     */
    suspend fun getTodoById(todoId: Long): TodoItem? = withContext(ioDispatcher) {
        todoDao.getTodoById(todoId)
    }

    fun getAllTodos(): Flow<List<TodoItem>> = todoDao.getAllTodos()

    fun getTodosByStatus(status: Int): Flow<List<TodoItem>> = 
        todoDao.getTodosByStatus(status)

    fun getTodosByCategory(categoryId: Long): Flow<List<TodoItem>> = 
        todoDao.getTodosByCategory(categoryId)

    fun getTodosByPriority(priority: Int): Flow<List<TodoItem>> = 
        todoDao.getTodosByPriority(priority)

    fun getTodosWithReminders(currentTime: Long): Flow<List<TodoItem>> = 
        todoDao.getTodosWithReminders(currentTime)

    fun getTodosByCategoryAndStatus(
        categoryId: Long,
        status: Int
    ): Flow<List<TodoItem>> = todoDao.getTodosByCategoryAndStatus(categoryId, status)

    fun getTodosByStatusPriorityDueDate(status: Int): Flow<List<TodoItem>> = 
        todoDao.getTodosByStatusPriorityDueDate(status)

    /**
     * 获取指定分类已完成任务数
     *
     * @param categoryId 分类 ID
     * @return 已完成任务数
     */
    suspend fun getCompletedCountByCategory(categoryId: Long): Int = withContext(ioDispatcher) {
        todoDao.getCompletedCountByCategory(categoryId)
    }

    /**
     * 获取累计完成任务总数
     *
     * @return 累计完成任务数
     */
    suspend fun getTotalCompletedCount(): Int = withContext(ioDispatcher) {
        todoDao.getTotalCompletedCount()
    }

    /**
     * 清理超过指定时间的已完成待办
     *
     * @param threshold 时间阈值（毫秒），超过此时间的已完成待办将被删除
     * @return 删除的待办数量
     */
    suspend fun cleanupOldCompletedTodos(threshold: Long): Int = withContext(ioDispatcher) {
        val deleted = todoDao.deleteOldCompletedTodos(threshold)
        if (deleted > 0) {
            WidgetUpdateReceiver.sendRefreshBroadcast(context)
        }
        deleted
    }

    /**
     * 获取指定分类类型的已完成任务数
     *
     * @param categoryType 分类类型（0=工作，1=学习，2=生活）
     * @return 已完成任务数
     */
    suspend fun getCompletedCountByCategoryType(categoryType: Int): Int = withContext(ioDispatcher) {
        todoDao.getCompletedCountByCategoryType(categoryType)
    }

    /**
     * 获取今天完成的任务数
     *
     * @return 今天完成的任务数
     */
    suspend fun getCompletedCountToday(): Int = withContext(ioDispatcher) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis

        todoDao.getCompletedCountToday(startOfDay, endOfDay)
    }
}
