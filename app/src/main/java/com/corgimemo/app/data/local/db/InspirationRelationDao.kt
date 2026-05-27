package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.InspirationRelation
import kotlinx.coroutines.flow.Flow

/**
 * 灵感关联关系数据访问对象接口
 * 提供对 inspiration_relations 表的增删改查操作
 */
@Dao
interface InspirationRelationDao {
    
    /**
     * 插入新关联关系
     * @param relation 关联实体
     * @return 新插入记录的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: InspirationRelation): Long
    
    /**
     * 批量插入关联关系
     * @param relations 关联列表
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(relations: List<InspirationRelation>)
    
    /**
     * 删除关联关系
     * @param relation 关联实体
     */
    @Delete
    suspend fun delete(relation: InspirationRelation)
    
    /**
     * 按ID删除关联关系
     * @param id 关联ID
     */
    @Query("DELETE FROM inspiration_relations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /**
     * 按灵感ID删除所有关联（级联删除时调用）
     * @param inspirationId 灵感ID
     */
    @Query("DELETE FROM inspiration_relations WHERE inspirationId = :inspirationId")
    suspend fun deleteByInspirationId(inspirationId: Long)
    
    /**
     * 获取指定灵感的所有关联关系
     * @param inspirationId 灵感ID
     * @return 关联列表流
     */
    @Query("SELECT * FROM inspiration_relations WHERE inspirationId = :inspirationId ORDER BY createdAt ASC")
    fun getRelationsByInspirationId(inspirationId: Long): Flow<List<InspirationRelation>>
    
    /**
     * 获取指定灵感的所有关联关系（阻塞方式）
     * @param inspirationId 灵感ID
     * @return 关联列表
     */
    @Query("SELECT * FROM inspiration_relations WHERE inspirationId = :inspirationId ORDER BY createdAt ASC")
    suspend fun getRelationsByInspirationIdBlocking(inspirationId: Long): List<InspirationRelation>
    
    /**
     * 检查关联是否已存在
     * @param inspirationId 灵感ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 是否存在
     */
    @Query("""SELECT COUNT(*) > 0 FROM inspiration_relations 
              WHERE inspirationId = :inspirationId 
                AND targetType = :targetType 
                AND targetId = :targetId""")
    suspend fun isRelationExist(inspirationId: Long, targetType: String, targetId: Long): Boolean
    
    /**
     * 删除指定关联
     * @param inspirationId 灵感ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     */
    @Query("""DELETE FROM inspiration_relations 
              WHERE inspirationId = :inspirationId 
                AND targetType = :targetType 
                AND targetId = :targetId""")
    suspend fun deleteRelation(inspirationId: Long, targetType: String, targetId: Long)
}
