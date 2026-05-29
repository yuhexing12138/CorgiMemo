package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CardRelationDao
import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.local.db.SpecialDateDao
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一卡片关联关系仓库
 * 封装对 card_relations 表的所有操作
 * 提供跨三种卡片类型（待办/灵感/日期）的关联管理和搜索功能
 */
@Singleton
class CardRelationRepository @Inject constructor(
    private val cardRelationDao: CardRelationDao,
    private val todoDao: TodoDao,
    private val inspirationDao: InspirationDao,
    private val specialDateDao: SpecialDateDao
) {

    companion object {
        /** 每张卡片最多关联的其他卡片数量 */
        const val MAX_RELATIONS_PER_CARD = 10
    }

    /**
     * 获取某卡片发起的所有关联（响应式流）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联列表流
     */
    fun getRelations(sourceType: String, sourceId: Long): Flow<List<CardRelation>> =
        cardRelationDao.getBySource(sourceType, sourceId)

    /**
     * 获取某卡片发起的所有关联（阻塞方式）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联列表
     */
    suspend fun getRelationsBlocking(sourceType: String, sourceId: Long): List<CardRelation> =
        cardRelationDao.getBySourceBlocking(sourceType, sourceId)

    /**
     * 添加关联关系（含去重和数量限制检查）
     * @param relation 关联实体
     * @return 新插入记录的ID，-1表示已存在，-2表示超过数量限制
     */
    suspend fun addRelation(relation: CardRelation): Long {
        if (cardRelationDao.isRelationExist(
                relation.sourceType, relation.sourceId,
                relation.targetType, relation.targetId
            )
        ) {
            return -1L
        }
        val count = cardRelationDao.countBySource(relation.sourceType, relation.sourceId)
        if (count >= MAX_RELATIONS_PER_CARD) {
            return -2L
        }
        return cardRelationDao.insert(relation)
    }

    /**
     * 删除指定关联
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    suspend fun removeRelation(
        sourceType: String,
        sourceId: Long,
        targetType: String,
        targetId: Long
    ) {
        cardRelationDao.deleteRelation(sourceType, sourceId, targetType, targetId)
    }

    /**
     * 按关联ID删除
     * @param id 关联ID
     */
    suspend fun removeRelationById(id: Long) {
        cardRelationDao.deleteById(id)
    }

    /**
     * 删除某卡片发起的所有关联（删除卡片时调用）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     */
    suspend fun removeAllBySource(sourceType: String, sourceId: Long) {
        cardRelationDao.deleteBySource(sourceType, sourceId)
    }

    /**
     * 删除指向某卡片的所有关联（被删卡片时调用，解除被关联关系）
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    suspend fun removeAllByTarget(targetType: String, targetId: Long) {
        cardRelationDao.deleteByTarget(targetType, targetId)
    }

    /**
     * 删除卡片时同时解除发起和被关联的所有关系
     * @param cardType 卡片类型
     * @param cardId 卡片ID
     */
    suspend fun removeAllForCard(cardType: String, cardId: Long) {
        cardRelationDao.deleteBySource(cardType, cardId)
        cardRelationDao.deleteByTarget(cardType, cardId)
    }

    /**
     * 获取某卡片发起的关联数量
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联数量
     */
    suspend fun getRelationCount(sourceType: String, sourceId: Long): Int =
        cardRelationDao.countBySource(sourceType, sourceId)

    /**
     * 跨三表搜索卡片（用于关联选择器）
     * @param query 搜索关键词
     * @return 搜索结果列表，按类型分组
     */
    suspend fun searchCards(query: String): List<CardSearchResult> {
        if (query.isBlank()) return getAllCards()

        val results = mutableListOf<CardSearchResult>()

        val todos = todoDao.searchTodos(query)
        todos.forEach { todo ->
            results.add(
                CardSearchResult(
                    cardType = "todo",
                    cardId = todo.id,
                    title = todo.title
                )
            )
        }

        val inspirations = inspirationDao.getAllInspirationsBlocking()
        inspirations.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
        }.forEach { inspiration ->
            results.add(
                CardSearchResult(
                    cardType = "inspiration",
                    cardId = inspiration.id,
                    title = inspiration.title
                )
            )
        }

        val dates = specialDateDao.getAllSpecialDatesBlocking()
        dates.filter {
            it.title.contains(query, ignoreCase = true)
        }.forEach { date ->
            results.add(
                CardSearchResult(
                    cardType = "date",
                    cardId = date.id,
                    title = date.title
                )
            )
        }

        return results
    }

    /**
     * 获取所有卡片（搜索关键词为空时使用）
     * @return 所有卡片列表
     */
    private suspend fun getAllCards(): List<CardSearchResult> {
        val results = mutableListOf<CardSearchResult>()

        val todos = todoDao.getAllTodosBlocking()
        todos.forEach { todo ->
            results.add(
                CardSearchResult(
                    cardType = "todo",
                    cardId = todo.id,
                    title = todo.title
                )
            )
        }

        val inspirations = inspirationDao.getAllInspirationsBlocking()
        inspirations.forEach { inspiration ->
            results.add(
                CardSearchResult(
                    cardType = "inspiration",
                    cardId = inspiration.id,
                    title = inspiration.title
                )
            )
        }

        val dates = specialDateDao.getAllSpecialDatesBlocking()
        dates.forEach { date ->
            results.add(
                CardSearchResult(
                    cardType = "date",
                    cardId = date.id,
                    title = date.title
                )
            )
        }

        return results
    }

    /**
     * 根据卡片类型和ID获取卡片标题
     * @param cardType 卡片类型
     * @param cardId 卡片ID
     * @return 卡片标题，不存在返回null
     */
    suspend fun getCardTitle(cardType: String, cardId: Long): String? {
        return when (cardType) {
            "todo" -> todoDao.getTodoById(cardId)?.title
            "inspiration" -> inspirationDao.getInspirationById(cardId)?.title
            "date" -> specialDateDao.getSpecialDateById(cardId)?.title
            else -> null
        }
    }

    /**
     * 获取某卡片的第一条关联摘要（用于列表页提示）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @return 关联摘要（第一条关联的类型+标题+总数），无关联返回null
     */
    suspend fun getRelationSummary(sourceType: String, sourceId: Long): RelationSummary? {
        val relations = cardRelationDao.getBySourceBlocking(sourceType, sourceId)
        if (relations.isEmpty()) return null

        val firstRelation = relations.first()
        val title = getCardTitle(firstRelation.targetType, firstRelation.targetId) ?: "已删除"

        return RelationSummary(
            targetType = firstRelation.targetType,
            targetTitle = title,
            totalCount = relations.size
        )
    }
}

/**
 * 关联摘要数据类
 * 用于列表页卡片底部显示关联提示
 */
data class RelationSummary(
    /** 被关联卡片的类型 */
    val targetType: String,
    /** 被关联卡片的标题 */
    val targetTitle: String,
    /** 关联总数 */
    val totalCount: Int
) {
    /** 获取类型中文名 */
    val targetTypeName: String
        get() = when (targetType) {
            "todo" -> "待办"
            "inspiration" -> "灵感"
            "date" -> "日期"
            else -> "未知"
        }

    /** 格式化显示文本，如 "🔗 关联: @待办:完成周报 +2" */
    val displayText: String
        get() = if (totalCount > 1) {
            "🔗 关联: @${targetTypeName}:${targetTitle} +${totalCount - 1}"
        } else {
            "🔗 关联: @${targetTypeName}:${targetTitle}"
        }
}
