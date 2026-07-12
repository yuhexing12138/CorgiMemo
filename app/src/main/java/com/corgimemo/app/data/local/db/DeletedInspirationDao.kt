package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.DeletedInspiration
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedInspirationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deletedInspiration: DeletedInspiration)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deletedInspirations: List<DeletedInspiration>)

    @Query("SELECT * FROM deleted_inspirations ORDER BY deletedAt DESC")
    fun getAllDeletedInspirations(): Flow<List<DeletedInspiration>>

    @Query("SELECT * FROM deleted_inspirations ORDER BY deletedAt DESC")
    suspend fun getAllDeletedInspirationsBlocking(): List<DeletedInspiration>

    @Query("SELECT COUNT(*) FROM deleted_inspirations")
    fun getDeletedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM deleted_inspirations")
    suspend fun getDeletedCountBlocking(): Int

    @Query("DELETE FROM deleted_inspirations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM deleted_inspirations")
    suspend fun deleteAll()

    @Query("DELETE FROM deleted_inspirations WHERE deletedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long): Int

    @Query("SELECT * FROM deleted_inspirations WHERE id = :id")
    suspend fun getDeletedInspirationById(id: Long): DeletedInspiration?
}
