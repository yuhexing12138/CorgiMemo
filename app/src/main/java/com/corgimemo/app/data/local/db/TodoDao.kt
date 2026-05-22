package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(todos: List<TodoItem>)

    @Update
    suspend fun update(todo: TodoItem)

    @Delete
    suspend fun delete(todo: TodoItem)

    @Query("DELETE FROM todo_items WHERE id = :todoId")
    suspend fun deleteById(todoId: Long)

    @Query("DELETE FROM todo_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM todo_items WHERE id = :todoId")
    suspend fun getTodoById(todoId: Long): TodoItem?

    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    fun getAllTodos(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items ORDER BY createdAt DESC")
    suspend fun getAllTodosBlocking(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE status = 0 AND reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    suspend fun getPendingRemindersBlocking(currentTime: Long): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE status = :status ORDER BY createdAt DESC")
    fun getTodosByStatus(status: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun getTodosByCategory(categoryId: Long): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE priority = :priority ORDER BY createdAt DESC")
    fun getTodosByPriority(priority: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE reminderTime IS NOT NULL AND reminderTime > :currentTime ORDER BY reminderTime ASC")
    fun getTodosWithReminders(currentTime: Long): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE categoryId = :categoryId AND status = :status ORDER BY createdAt DESC")
    fun getTodosByCategoryAndStatus(categoryId: Long, status: Int): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE status = :status ORDER BY priority DESC, startDate ASC")
    fun getTodosByStatusPriorityDueDate(status: Int): Flow<List<TodoItem>>

    @Query("SELECT COUNT(*) FROM todo_items WHERE status = 1 AND categoryId = :categoryId")
    suspend fun getCompletedCountByCategory(categoryId: Long): Int

    @Query("SELECT COUNT(*) FROM todo_items WHERE status = 1")
    suspend fun getTotalCompletedCount(): Int

    @Query("DELETE FROM todo_items WHERE status = 1 AND completedAt < :threshold AND completedAt IS NOT NULL")
    suspend fun deleteOldCompletedTodos(threshold: Long): Int

    /**
     * 获取指定分类类型（工作=0/学习=1/生活=2）的已完成任务数
     */
    @Query("""
        SELECT COUNT(*) FROM todo_items t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE t.status = 1 AND c.type = :categoryType
    """)
    suspend fun getCompletedCountByCategoryType(categoryType: Int): Int

    /**
     * 获取今天完成的任务数
     */
    @Query("""
        SELECT COUNT(*) FROM todo_items 
        WHERE status = 1 AND completedAt >= :startOfDay AND completedAt < :endOfDay
    """)
    suspend fun getCompletedCountToday(startOfDay: Long, endOfDay: Long): Int
}
