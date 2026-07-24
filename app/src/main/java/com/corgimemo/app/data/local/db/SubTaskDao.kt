package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.SubTask
import kotlinx.coroutines.flow.Flow

/**
 * 子任务数据访问接口
 */
@Dao
interface SubTaskDao {

    @Insert
    suspend fun insert(subTask: SubTask): Long

    /**
     * 批量插入子任务并返回每个子任务的 ID（与输入列表一一对应）
     *
     * v2026-07-25 优化：返回 ID 列表用于 saveGroup 中回填到 groupLines，
     * 以便 saveContentBlocksFromTodoLines 能正确设置子任务附件的 subTaskId 字段。
     *
     * 注意：OnConflictStrategy.IGNORE 策略下冲突项返回 -1，
     * 调用方使用返回值时需处理 -1 的情况（saveGroup 场景下不会冲突）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subTasks: List<SubTask>): List<Long>

    @Update
    suspend fun update(subTask: SubTask)

    @Update
    suspend fun updateAll(subTasks: List<SubTask>)

    @Delete
    suspend fun delete(subTask: SubTask)

    @Query("DELETE FROM sub_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sub_tasks WHERE todoId = :todoId")
    suspend fun deleteByTodoId(todoId: Long)

    @Query("SELECT * FROM sub_tasks WHERE todoId = :todoId ORDER BY `order` ASC")
    suspend fun getSubTasksByTodoId(todoId: Long): List<SubTask>

    @Query("SELECT * FROM sub_tasks WHERE todoId = :todoId ORDER BY `order` ASC")
    fun getSubTasksByTodoIdFlow(todoId: Long): Flow<List<SubTask>>

    @Query("SELECT COUNT(*) FROM sub_tasks WHERE todoId = :todoId")
    suspend fun getSubTaskCount(todoId: Long): Int

    @Query("SELECT COUNT(*) FROM sub_tasks WHERE todoId = :todoId AND isCompleted = 1")
    suspend fun getCompletedSubTaskCount(todoId: Long): Int

    @Query("SELECT * FROM sub_tasks WHERE id = :id")
    suspend fun getSubTaskById(id: Long): SubTask?

    @Query("UPDATE sub_tasks SET isCompleted = :isCompleted, completedAt = :completedAt WHERE id = :id")
    suspend fun updateCompletion(id: Long, isCompleted: Boolean, completedAt: Long?)

    @Query("SELECT MAX(`order`) FROM sub_tasks WHERE todoId = :todoId")
    suspend fun getMaxOrder(todoId: Long): Int?
}
