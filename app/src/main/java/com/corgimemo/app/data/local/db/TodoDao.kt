package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.flow.Flow

/**
 * 待办事项数据访问对象（DAO）
 * 
 * 定义对 todo_items 表的所有操作
 */
@Dao
interface TodoDao {

    /**
     * 查询所有待办事项，按创建时间降序排列
     * 
     * @return 待办事项流（Flow），支持实时数据观察
     */
    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    /**
     * 根据ID查询单个待办事项
     * 
     * @param id 待办事项ID
     * @return 待办事项对象（可能为null）
     */
    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoById(id: String): TodoItem?

    /**
     * 插入待办事项
     * 
     * @param todo 待办事项对象
     * @param onConflict 冲突策略（默认替换）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: TodoItem)

    /**
     * 更新待办事项
     * 
     * @param todo 待更新的待办事项对象
     */
    @Update
    suspend fun updateTodo(todo: TodoItem)

    /**
     * 删除待办事项
     * 
     * @param todo 待删除的待办事项对象
     */
    @Delete
    suspend fun deleteTodo(todo: TodoItem)

    /**
     * 根据ID删除待办事项
     * 
     * @param id 待办事项ID
     */
    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteTodoById(id: String)

    /**
     * 删除所有已完成的待办事项
     */
    @Query("DELETE FROM todo_items WHERE isCompleted = 1")
    suspend fun deleteCompletedTodos()

    /**
     * 统计待办事项总数
     * 
     * @return 待办事项数量
     */
    @Query("SELECT COUNT(*) FROM todo_items")
    suspend fun getTodoCount(): Int

    /**
     * 统计已完成的待办事项数量
     * 
     * @return 已完成数量
     */
    @Query("SELECT COUNT(*) FROM todo_items WHERE isCompleted = 1")
    suspend fun getCompletedCount(): Int
}