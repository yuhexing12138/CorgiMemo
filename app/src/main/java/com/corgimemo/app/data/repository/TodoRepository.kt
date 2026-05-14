package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(
    private val todoDao: TodoDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun insertTodo(todo: TodoItem): Long = withContext(ioDispatcher) {
        todoDao.insert(todo)
    }

    suspend fun insertTodos(todos: List<TodoItem>) = withContext(ioDispatcher) {
        todoDao.insertAll(todos)
    }

    suspend fun updateTodo(todo: TodoItem) = withContext(ioDispatcher) {
        todoDao.update(todo)
    }

    suspend fun deleteTodo(todo: TodoItem) = withContext(ioDispatcher) {
        todoDao.delete(todo)
    }

    suspend fun deleteTodoById(todoId: Long) = withContext(ioDispatcher) {
        todoDao.deleteById(todoId)
    }

    suspend fun deleteAllTodos() = withContext(ioDispatcher) {
        todoDao.deleteAll()
    }

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
}
