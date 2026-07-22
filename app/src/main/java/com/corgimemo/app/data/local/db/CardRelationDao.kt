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
     * 按ID查询关联关系详情（v2026-07-21 新增，供双向删除使用）
     *
     * 使用场景：[com.corgimemo.app.data.repository.CardRelationRepository.removeRelationById]
     * 需要先查询出关联详情（source/target），才能同步删除反向记录。
     *
     * @param id 关联ID
     * @return 关联实体，不存在返回 null
     */
    @Query("SELECT * FROM card_relations WHERE id = :id")
    suspend fun getById(id: Long): CardRelation?

    /**
     * 删除某卡片某分组发起的所有关联（删除卡片或清空分组时调用）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     */
    @Query("DELETE FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId AND groupId = :groupId")
    suspend fun deleteBySource(sourceType: String, sourceId: Long, groupId: Int)

    /**
     * 删除某卡片所有分组发起的所有关联（删除整张卡片时调用，绕过 groupId）
     *
     * 注：DAO 的 deleteBySource 已加 groupId 参数，此方法用于"删除整张卡片"场景，
     * 避免在 ViewModel 中遍历所有 groupIds。
     *
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     */
    @Query("DELETE FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun deleteBySourceAllGroups(sourceType: String, sourceId: Long)

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
     * @param groupId 分组ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    @Query(
        """DELETE FROM card_relations
           WHERE sourceType = :sourceType
             AND sourceId = :sourceId
             AND groupId = :groupId
             AND targetType = :targetType
             AND targetId = :targetId"""
    )
    suspend fun deleteRelation(
        sourceType: String,
        sourceId: Long,
        groupId: Int,
        targetType: String,
        targetId: Long
    )

    /**
     * 获取某卡片某分组发起的所有关联（响应式流）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID（todo 类型有意义，其他类型传 0）
     * @return 关联列表流，按创建时间升序排列
     */
    @Query(
        "SELECT * FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId AND groupId = :groupId ORDER BY createdAt ASC"
    )
    fun getBySource(sourceType: String, sourceId: Long, groupId: Int): Flow<List<CardRelation>>

    /**
     * 获取某卡片某分组发起的所有关联（阻塞方式）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @return 关联列表
     */
    @Query(
        "SELECT * FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId AND groupId = :groupId ORDER BY createdAt ASC"
    )
    suspend fun getBySourceBlocking(sourceType: String, sourceId: Long, groupId: Int): List<CardRelation>

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
     * @param groupId 分组ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     * @return 是否存在
     */
    @Query(
        """SELECT COUNT(*) > 0 FROM card_relations
           WHERE sourceType = :sourceType
             AND sourceId = :sourceId
             AND groupId = :groupId
             AND targetType = :targetType
             AND targetId = :targetId"""
    )
    suspend fun isRelationExist(
        sourceType: String,
        sourceId: Long,
        groupId: Int,
        targetType: String,
        targetId: Long
    ): Boolean

    /**
     * 统计某卡片某分组发起的关联数量（用于10张限制检查）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @return 关联数量
     */
    @Query("SELECT COUNT(*) FROM card_relations WHERE sourceType = :sourceType AND sourceId = :sourceId AND groupId = :groupId")
    suspend fun countBySource(sourceType: String, sourceId: Long, groupId: Int): Int

    /**
     * 把所有 sourceId=0 的"占位关联"批量改写为新建卡片的实际 ID（v2026-07-22 新增）
     *
     * **背景**：
     * 新建模式下用户在卡片尚未入库时打开"关联选择器"添加关联，
     * [com.corgimemo.app.data.repository.CardRelationRepository.addRelation] 会立即把
     * sourceId=0 写入 card_relations 表。本方法在新建保存成功后被调用，
     * 把所有"卡片 0 发起"的脏数据迁移到真实 ID。
     *
     * **触发方**：
     * - [com.corgimemo.app.viewmodel.InspirationEditViewModel.performSave]
     * - [com.corgimemo.app.viewmodel.SpecialDateViewModel.saveDate]
     * - [com.corgimemo.app.viewmodel.TodoEditViewModel.performSave]
     *
     * **幂等性**：
     * - 重复运行安全（只命中 sourceId=0 的行，运行后无 sourceId=0 残留）
     * - 与 [fixupZeroTargetId] 配合使用，两个 UPDATE 各自独立
     *
     * @param newSourceType 新卡片类型 ("todo" | "inspiration" | "date")
     * @param newSourceId   新卡片实际 ID
     * @return 影响的行数
     */
    @Query("UPDATE card_relations SET sourceId = :newSourceId WHERE sourceType = :newSourceType AND sourceId = 0")
    suspend fun fixupZeroSourceId(newSourceType: String, newSourceId: Long): Int

    /**
     * 把所有 targetId=0 的"占位关联"批量改写为新建卡片的实际 ID（v2026-07-22 新增）
     *
     * **背景**：
     * [com.corgimemo.app.data.repository.CardRelationRepository.addRelation] 在插入正向 A→B
     * 后会自动插入反向 B→A。当 A 还在新建模式时，targetId=A.id=0，所以反向记录的
     * targetId 也是 0。本方法与 [fixupZeroSourceId] 配对，把反向记录的 targetId 修复为真实 ID。
     *
     * **典型使用**（在 [com.corgimemo.app.data.repository.CardRelationRepository.fixupZeroSourceRelations]）：
     * ```kotlin
     * suspend fun fixupZeroSourceRelations(type: String, id: Long) {
     *     cardRelationDao.fixupZeroSourceId(type, id)
     *     cardRelationDao.fixupZeroTargetId(type, id)
     * }
     * ```
     *
     * @param newSourceType 新卡片类型
     * @param newSourceId   新卡片实际 ID（同时作为反向记录的 targetId）
     * @return 影响的行数
     */
    @Query("UPDATE card_relations SET targetId = :newSourceId WHERE targetType = :newSourceType AND targetId = 0")
    suspend fun fixupZeroTargetId(newSourceType: String, newSourceId: Long): Int
}
