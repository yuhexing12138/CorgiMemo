package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * 待办事项数据仓库
 * 
 * 负责协调数据来源（本地数据库、网络等），提供统一的数据访问接口
 * 
 * @param todoDao 待办事项数据访问对象
 */
class TodoRepository(private val todoDao: TodoDao) {

    /**
     * 获取所有待办事项的流
     * 
     * @return 待办事项列表流
     */
    fun getAllTodos(): Flow<List<TodoItem>> {
        return todoDao.getAllTodos()
    }

    /**
     * 根据ID获取待办事项
     * 
     * @param id 待办事项ID
     * @return 待办事项对象（可能为null）
     */
    suspend fun getTodoById(id: String): TodoItem? {
        return todoDao.getTodoById(id)
    }

    /**
     * 保存待办事项（新建或更新）
     * 
     * @param title 待办标题
     * @param description 待办描述（可选）
     * @param id 待办ID（新建时为null）
     */
    suspend fun saveTodo(title: String, description: String?, id: String?) {
        if (id.isNullOrEmpty()) {
            // 新建待办
            val newTodo = TodoItem(
                title = title,
                description = description,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now()
            )
            todoDao.insertTodo(newTodo)
        } else {
            // 更新现有待办
            val existingTodo = todoDao.getTodoById(id)
            existingTodo?.let {
                val updatedTodo = it.copy(
                    title = title,
                    description = description,
                    updatedAt = LocalDateTime.now()
                )
                todoDao.updateTodo(updatedTodo)
            }
        }
    }

    /**
     * 删除待办事项
     * 
     * @param todo 待删除的待办事项
     */
    suspend fun deleteTodo(todo: TodoItem) {
        todoDao.deleteTodo(todo)
    }

    /**
     * 根据ID删除待办事项
     * 
     * @param id 待办事项ID
     */
    suspend fun deleteTodoById(id: String) {
        todoDao.deleteTodoById(id)
    }

    /**
     * 删除所有已完成的待办事项
     */
    suspend fun deleteCompletedTodos() {
        todoDao.deleteCompletedTodos()
    }

    /**
     * 切换待办事项完成状态
     * 
     * @param id 待办事项ID
     */
    suspend fun toggleTodoStatus(id: String) {
        val todo = todoDao.getTodoById(id)
        todo?.let {
            val updatedTodo = it.copy(
                isCompleted = !it.isCompleted,
                updatedAt = LocalDateTime.now()
            )
            todoDao.updateTodo(updatedTodo)
        }
    }

    /**
     * 获取待办事项统计
     * 
     * @return Pair(总数, 已完成数)
     */
    suspend fun getStats(): Pair<Int, Int> {
        val total = todoDao.getTodoCount()
        val completed = todoDao.getCompletedCount()
        return Pair(total, completed)
    }
}