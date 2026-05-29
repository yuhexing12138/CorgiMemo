package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.CardRelation
import kotlinx.coroutines.flow.Flow

/**
 * 统一卡片关联关系数据访问对象接口
 * 提供对 card_relations 表的增删改查操作
 * 支持待办/灵感/日期三种卡片类型的关联管理
 */
@Dao
interface CardRelationDao {

    /**
     * 插入新关联关系（冲突时替换）
     * @param relation 关联实体
     * @return 新插入记录的ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: CardRelation): Long

    /**
     * 按ID删除关联关系
     * @param id 关联ID
     */
    @Query("DELETE FROM card_relations WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * 删除某卡片发起的所有关联（删除卡片时调用）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     */
    @Query("DELETE FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteBySource(sourceType: String, sourceId: Long)

    /**
     * 删除指向某卡片的所有关联（被删卡片时调用，解除被关联关系）
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    @Query("DELETE FROM card_relations WHERE targetType = :targetType AND targetId = :targetId")
    suspend fun deleteByTarget(targetType: String, targetId: Long)

    /**
     * 精确删除一条关联
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    @Query(
        """DELETE FROM card_relations 
           WHERE sourceType = :sourceType 
             AND sourceId = :sourceId 
             AND targetType = :targetType 
             AND targetId = :targetId"""
    )
    suspend fun deleteRelation(
        sourceType: String,
        sourceId: Long,
        targetType: String,
        targetId: Long
    )

    /**
     * 获取某卡片发起的所有关联（响应式流）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联列表流，按创建时间升序排列
     */
    @Query(
        "SELECT * FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY createdAt ASC"
    )
    fun getBySource(sourceType: String, sourceId: Long): Flow<List<CardRelation>>

    /**
     * 获取某卡片发起的所有关联（阻塞方式）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联列表
     */
    @Query(
        "SELECT * FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId ORDER BY createdAt ASC"
    )
    suspend fun getBySourceBlocking(sourceType: String, sourceId: Long): List<CardRelation>

    /**
     * 获取指向某卡片的所有关联（响应式流，用于级联删除检查）
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     * @return 关联列表流
     */
    @Query("SELECT * FROM card_relations WHERE targetType = :targetType AND targetId = :targetId")
    fun getByTarget(targetType: String, targetId: Long): Flow<List<CardRelation>>

    /**
     * 检查关联是否已存在（去重判断）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     * @return 是否存在
     */
    @Query(
        """SELECT COUNT(*) > 0 FROM card_relations 
           WHERE sourceType = :sourceType 
             AND sourceId = :sourceId 
             AND targetType = :targetType 
             AND targetId = :targetId"""
    )
    suspend fun isRelationExist(
        sourceType: String,
        sourceId: Long,
        targetType: String,
        targetId: Long
    ): Boolean

    /**
     * 统计某卡片发起的关联数量（用于10张限制检查）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联数量
     */
    @Query("SELECT COUNT(*) FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun countBySource(sourceType: String, sourceId: Long): Int
}
