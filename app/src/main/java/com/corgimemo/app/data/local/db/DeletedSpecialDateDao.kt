package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.DeletedSpecialDate
import kotlinx.coroutines.flow.Flow

/**
 * 已删除特殊日期 DAO
 *
 * 镜像 DeletedTodoDao 的接口设计：
 * - insert / insertAll：移入回收站
 * - getAllDeletedDates：回收站列表（按删除时间倒序）
 * - getByIdBlocking：根据 ID 获取单条记录（供恢复使用）
 * - deleteById / deleteAll / deleteOlderThan：永久删除与清理
 */
@Dao
interface DeletedSpecialDateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deleted: DeletedSpecialDate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deleted: List<DeletedSpecialDate>)

    @Query("SELECT * FROM deleted_special_dates ORDER BY deletedAt DESC")
    fun getAllDeletedDates(): Flow<List<DeletedSpecialDate>>

    @Query("SELECT * FROM deleted_special_dates ORDER BY deletedAt DESC")
    suspend fun getAllDeletedDatesBlocking(): List<DeletedSpecialDate>

    @Query("SELECT COUNT(*) FROM deleted_special_dates")
    fun getDeletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM deleted_special_dates")
    suspend fun getDeletedCountBlocking(): Int

    @Query("SELECT * FROM deleted_special_dates WHERE id = :id")
    suspend fun getDeletedDateById(id: Long): DeletedSpecialDate?

    @Query("DELETE FROM deleted_special_dates WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM deleted_special_dates")
    suspend fun deleteAll()

    @Query("DELETE FROM deleted_special_dates WHERE deletedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int
}
