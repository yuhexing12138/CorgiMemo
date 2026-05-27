package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.DeletedTodo
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedTodoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deletedTodo: DeletedTodo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deletedTodos: List<DeletedTodo>)

    @Query("SELECT * FROM deleted_todos ORDER BY deletedAt DESC")
    fun getAllDeletedTodos(): Flow<List<DeletedTodo>>

    @Query("SELECT * FROM deleted_todos ORDER BY deletedAt DESC")
    suspend fun getAllDeletedTodosBlocking(): List<DeletedTodo>

    @Query("SELECT COUNT(*) FROM deleted_todos")
    fun getDeletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM deleted_todos")
    suspend fun getDeletedCountBlocking(): Int

    @Query("DELETE FROM deleted_todos WHERE id = :todoId")
    suspend fun deleteById(todoId: Long)

    @Query("DELETE FROM deleted_todos")
    suspend fun deleteAll()

    @Query("DELETE FROM deleted_todos WHERE deletedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int

    @Query("SELECT * FROM deleted_todos WHERE id = :todoId")
    suspend fun getDeletedTodoById(todoId: Long): DeletedTodo?
}