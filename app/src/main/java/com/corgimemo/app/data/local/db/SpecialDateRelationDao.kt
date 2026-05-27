package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.corgimemo.app.data.model.SpecialDateRelation
import kotlinx.coroutines.flow.Flow

/**
 * 特殊日期关联关系数据访问对象
 * 管理特殊日期与其他卡片（待办/日期/灵感）的关联
 */
@Dao
interface SpecialDateRelationDao {

    /** 新增关联关系（忽略冲突） */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insert(relation: SpecialDateRelation): Long

    /** 批量新增关联关系 */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(relations: List<SpecialDateRelation>)

    /** 删除关联关系 */
    @Delete
    suspend fun delete(relation: SpecialDateRelation)

    /** 根据ID删除关联关系 */
    @Query("DELETE FROM special_date_relations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 根据特殊日期ID删除所有关联 */
    @Query("DELETE FROM special_date_relations WHERE specialDateId = :specialDateId")
    suspend fun deleteBySpecialDateId(specialDateId: Long)

    /** 获取某特殊日期的所有关联（Flow响应式） */
    @Query("SELECT * FROM special_date_relations WHERE specialDateId = :specialDateId ORDER BY createdAt ASC")
    fun getRelationsBySpecialDateId(specialDateId: Long): Flow<List<SpecialDateRelation>>

    /** 获取某特殊日期的所有关联（阻塞） */
    @Query("SELECT * FROM special_date_relations WHERE specialDateId = :specialDateId ORDER BY createdAt ASC")
    suspend fun getRelationsBySpecialDateIdBlocking(specialDateId: Long): List<SpecialDateRelation>

    /** 判断关联是否已存在 */
    @Query(
        "SELECT COUNT(*) > 0 FROM special_date_relations " +
        "WHERE specialDateId = :specialDateId AND targetType = :targetType AND targetId = :targetId"
    )
    suspend fun isRelationExist(specialDateId: Long, targetType: String, targetId: Long): Boolean

    /** 删除指定关联 */
    @Query(
        "DELETE FROM special_date_relations " +
        "WHERE specialDateId = :specialDateId AND targetType = :targetType AND targetId = :targetId"
    )
    suspend fun deleteRelation(specialDateId: Long, targetType: String, targetId: Long)
}
