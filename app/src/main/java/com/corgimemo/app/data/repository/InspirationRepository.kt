package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.local.db.InspirationRelationDao
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.InspirationRelation
import com.corgimemo.app.util.MarkdownParser
import com.corgimemo.app.data.model.InspirationTagOrder
import com.corgimemo.app.data.local.db.InspirationTagOrderDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 灵感数据仓库
 * 封装对灵感数据和关联关系的所有操作
 * 提供统一的数据访问接口给 ViewModel 使用
 */
@Singleton
class InspirationRepository @Inject constructor(
    private val inspirationDao: InspirationDao,
    private val relationDao: InspirationRelationDao,
    private val cardRelationRepository: CardRelationRepository,
    // v2026-07-27 新增：灵感标签拖拽排序 DAO
    private val inspirationTagOrderDao: InspirationTagOrderDao
) {
    
    // ========== 灵感 CRUD 操作 ==========
    
    /**
     * 获取所有灵感（响应式流）
     * @return 按置顶和创建时间排序的灵感列表
     */
    fun getAllInspirations(): Flow<List<Inspiration>> = 
        inspirationDao.getAllInspirations()
    
    /**
     * 获取所有灵感（阻塞方式）
     * @return 灵感列表
     */
    suspend fun getAllInspirationsBlocking(): List<Inspiration> = 
        inspirationDao.getAllInspirationsBlocking()
    
    /**
     * 根据ID获取灵感
     * @param id 灵感ID
     * @return 灵感实体，不存在返回null
     */
    suspend fun getInspirationById(id: Long): Inspiration? = 
        inspirationDao.getInspirationById(id)
    
    /**
     * 插入新灵感
     * @param inspiration 灵感实体
     * @return 新插入记录的ID
     */
    suspend fun insert(inspiration: Inspiration): Long = 
        inspirationDao.insert(inspiration)
    
    /**
     * 更新灵感
     * @param inspiration 灵感实体
     */
    suspend fun update(inspiration: Inspiration) = 
        inspirationDao.update(inspiration)
    
    /**
     * 删除灵感（同时删除其关联关系）
     * @param id 灵感ID
     */
    suspend fun deleteById(id: Long) {
        relationDao.deleteByInspirationId(id)
        cardRelationRepository.removeAllForCard("inspiration", id)
        inspirationDao.deleteById(id)
    }
    
    /**
     * 删除灵感实体
     * @param inspiration 灵感实体
     */
    suspend fun delete(inspiration: Inspiration) = 
        inspirationDao.delete(inspiration)
    
    // ========== 搜索操作 ==========
    
    /**
     * 搜索灵感
     * @param query 搜索关键词（匹配标题/内容/标签）
     * @return 匹配的灵感列表流
     */
    fun searchInspirations(query: String): Flow<List<Inspiration>> = 
        inspirationDao.searchInspirations(query)
    
    /**
     * 获取灵感总数
     * @return 记录数
     */
    suspend fun getCount(): Int = 
        inspirationDao.getCount()
    
    // ========== 状态切换操作 ==========
    
    /**
     * 切换置顶状态
     * @param id 灵感ID
     * @param isPinned 是否置顶
     */
    suspend fun togglePin(id: Long, isPinned: Boolean) = 
        inspirationDao.togglePin(id, isPinned)
    
    /**
     * 切换归档状态
     * @param id 灵感ID
     * @param isArchived 是否归档
     */
    suspend fun toggleArchive(id: Long, isArchived: Boolean) = 
        inspirationDao.toggleArchive(id, isArchived)

    /**
     * 获取非置顶灵感的最大 position 值
     *
     * 用于恢复灵感时计算新的排序位置。
     *
     * @param isPinned 是否置顶
     * @return 最大 position 值，无记录时返回 null
     */
    suspend fun getMaxPosition(isPinned: Boolean): Int? =
        inspirationDao.getMaxPosition(isPinned)
    
    // ========== 数据迁移（v2026-09-18 BlockNote 迁移兼容） ==========

    /**
     * 一次性回填灵感正文纯文本。
     *
     * 迁移间隙/之前保存的灵感，其 content（纯文本摘要）字段可能为空，
     * 导致首页列表不显示正文。本方法遍历所有 content 为空、但 contentFormat 非空的灵感，
     * 用 [MarkdownParser.stripMarkdown] 将富文本 markdown 转为纯文本回填到 content。
     *
     * **幂等**：content 已非空（含本次已回填）的灵感会被跳过，可安全在每次启动时调用，
     * 无需额外标记位。回填后 Room Flow 会自动推送新列表，首页即时刷新。
     *
     * @return 实际回填的灵感条数
     */
    suspend fun backfillEmptyInspirationContent(): Int {
        val all = inspirationDao.getAllInspirationsBlocking()
        var backfilled = 0
        for (insp in all) {
            if (insp.content.isBlank() && insp.contentFormat.isNotBlank()) {
                val plain = MarkdownParser.stripMarkdown(insp.contentFormat)
                if (plain.isNotBlank()) {
                    inspirationDao.update(insp.copy(content = plain))
                    backfilled++
                }
            }
        }
        return backfilled
    }

    // ========== 关联关系操作 ==========
    
    /**
     * 获取灵感的关联关系列表
     * @param inspirationId 灵感ID
     * @return 关联列表流
     */
    fun getRelationsByInspirationId(inspirationId: Long): Flow<List<InspirationRelation>> = 
        relationDao.getRelationsByInspirationId(inspirationId)
    
    /**
     * 获取灵感的关联关系列表（阻塞方式）
     * @param inspirationId 灵感ID
     * @return 关联列表
     */
    suspend fun getRelationsByInspirationIdBlocking(inspirationId: Long): List<InspirationRelation> = 
        relationDao.getRelationsByInspirationIdBlocking(inspirationId)
    
    /**
     * 添加关联关系
     * @param relation 关联实体
     * @return 新插入记录的ID
     */
    suspend fun addRelation(relation: InspirationRelation): Long {
        if (!relationDao.isRelationExist(relation.inspirationId, relation.targetType, relation.targetId)) {
            return relationDao.insert(relation)
        }
        return -1L
    }
    
    /**
     * 批量添加关联关系
     * @param relations 关联列表
     */
    suspend fun addRelations(relations: List<InspirationRelation>) {
        val newRelations = relations.filter { 
            !relationDao.isRelationExist(it.inspirationId, it.targetType, it.targetId) 
        }
        if (newRelations.isNotEmpty()) {
            relationDao.insertAll(newRelations)
        }
    }
    
    /**
     * 删除关联关系
     * @param id 关联ID
     */
    suspend fun deleteRelationById(id: Long) = 
        relationDao.deleteById(id)
    
    /**
     * 删除指定关联
     * @param inspirationId 灵感ID
     * @param targetType 目标类型
     * @param targetId 目标ID
     */
    suspend fun deleteRelation(inspirationId: Long, targetType: String, targetId: Long) = 
        relationDao.deleteRelation(inspirationId, targetType, targetId)
    
    /**
     * 删除灵感所有关联
     * @param inspirationId 灵感ID
     */
    suspend fun deleteAllRelationsByInspirationId(inspirationId: Long) =
        relationDao.deleteByInspirationId(inspirationId)

    // ========== 标签拖拽排序（v2026-07-27 新增，P8 Phase 4 实施） ==========

    /**
     * 获取已排序的标签名列表
     *
     * 如 DB 中无记录则返回空 Flow（首次启动会 seed 全部已知标签）。
     * 调用方应做 fallback：空列表时从 inspirations.tags 字段聚合后按字母序排序。
     */
    fun getOrderedTagNames(): Flow<List<String>> = inspirationTagOrderDao.getOrderedTagNames()

    /**
     * 更新标签拖拽顺序（v2026-07-27 新增）
     *
     * INSPIRATION Tab 拖拽完成后由 ViewModel 调用。
     * 重新分配 sortOrder 后批量 REPLACE 写入。
     *
     * @param orderedTagNames 拖拽后的新标签顺序
     */
    suspend fun updateTagOrder(orderedTagNames: List<String>) {
        val orders = orderedTagNames.mapIndexed { idx, name ->
            InspirationTagOrder(tagName = name, sortOrder = idx)
        }
        inspirationTagOrderDao.insertAll(orders)
    }
}
