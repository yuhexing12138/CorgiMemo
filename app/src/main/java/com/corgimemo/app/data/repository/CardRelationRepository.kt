package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CardRelationDao
import com.corgimemo.app.data.local.db.CategoryDao
import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.local.db.SpecialDateDao
import com.corgimemo.app.data.local.db.SubTaskDao
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.supervisorScope
import org.json.JSONArray
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
    private val specialDateDao: SpecialDateDao,
    private val subTaskDao: SubTaskDao,
    private val categoryDao: CategoryDao
) {

    companion object {
        /** 每张卡片最多关联的其他卡片数量 */
        const val MAX_RELATIONS_PER_CARD = 10
    }

    /**
     * 获取某卡片某分组发起的所有关联（响应式流）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @return 关联列表流
     */
    fun getRelations(sourceType: String, sourceId: Long, groupId: Int): Flow<List<CardRelation>> =
        cardRelationDao.getBySource(sourceType, sourceId, groupId)

    /**
     * 获取某卡片某分组发起的所有关联（阻塞方式）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @return 关联列表
     */
    suspend fun getRelationsBlocking(sourceType: String, sourceId: Long, groupId: Int): List<CardRelation> =
        cardRelationDao.getBySourceBlocking(sourceType, sourceId, groupId)

    /**
     * 添加关联关系（含去重和数量限制检查，v2026-07-21 改造为自动双向）
     *
     * **双向关联逻辑**（用户需求：不区分关联和被关联）：
     * - 插入正向 A→B 后，自动插入反向 B→A（反向 groupId 固定为 0）
     * - 反向记录同样进行去重和数量限制检查
     * - 反向插入失败（超限或已存在）不影响正向插入结果
     *
     * **反向 groupId 固定为 0 的原因**：
     * - 如果 B 是 inspiration/date，其 groupId 本就是 0（这两类无分组概念）
     * - 如果 B 是 todo，反向关联归属于 B 的主分组（groupId=0），符合"主卡片"语义
     * - 避免反向关联落入 B 的非主分组，造成查询遗漏
     *
     * @param relation 关联实体（必须包含 groupId）
     * @return 新插入记录的ID，-1表示已存在，-2表示超过数量限制
     */
    suspend fun addRelation(relation: CardRelation): Long {
        // === 正向 A→B 去重与数量检查 ===
        if (cardRelationDao.isRelationExist(
                relation.sourceType, relation.sourceId, relation.groupId,
                relation.targetType, relation.targetId
            )
        ) {
            return -1L
        }
        val count = cardRelationDao.countBySource(relation.sourceType, relation.sourceId, relation.groupId)
        if (count >= MAX_RELATIONS_PER_CARD) {
            return -2L
        }
        val result = cardRelationDao.insert(relation)

        // === 反向 B→A 自动插入（双向关联核心逻辑） ===
        val reverseRelation = CardRelation(
            sourceType = relation.targetType,
            sourceId = relation.targetId,
            groupId = 0,
            targetType = relation.sourceType,
            targetId = relation.sourceId
        )
        // 反向去重检查（避免重复插入）
        val reverseExists = cardRelationDao.isRelationExist(
            reverseRelation.sourceType, reverseRelation.sourceId, reverseRelation.groupId,
            reverseRelation.targetType, reverseRelation.targetId
        )
        if (!reverseExists) {
            val reverseCount = cardRelationDao.countBySource(
                reverseRelation.sourceType, reverseRelation.sourceId, reverseRelation.groupId
            )
            // 反向未超限时才插入（超限不报错，静默跳过，保证正向插入成功）
            if (reverseCount < MAX_RELATIONS_PER_CARD) {
                cardRelationDao.insert(reverseRelation)
            }
        }
        return result
    }

    /**
     * 删除指定关联（v2026-07-21 改造为自动双向删除）
     *
     * **双向删除逻辑**：
     * - 删除正向 A→B 后，自动删除反向 B→A（反向 groupId 固定为 0）
     * - 反向记录不存在时静默跳过（DELETE 语句天然幂等）
     *
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @param targetType 目标方类型
     * @param targetId 目标方ID
     */
    suspend fun removeRelation(
        sourceType: String,
        sourceId: Long,
        groupId: Int,
        targetType: String,
        targetId: Long
    ) {
        // 删除正向 A→B
        cardRelationDao.deleteRelation(sourceType, sourceId, groupId, targetType, targetId)
        // 删除反向 B→A（groupId 固定为 0，与 addRelation 的反向插入对应）
        cardRelationDao.deleteRelation(targetType, targetId, 0, sourceType, sourceId)
    }

    /**
     * 按关联ID删除（v2026-07-21 改造为自动双向删除）
     *
     * **双向删除逻辑**：
     * - 先通过 id 查询出关联详情（sourceType/sourceId/groupId/targetType/targetId）
     * - 删除正向记录（按 id）
     * - 删除反向记录（按 source/target 精确删除，groupId 固定为 0）
     *
     * @param id 关联ID
     */
    suspend fun removeRelationById(id: Long) {
        // 先查询关联详情，才能定位反向记录
        val relation = cardRelationDao.getById(id) ?: return
        // 删除正向（按 id）
        cardRelationDao.deleteById(id)
        // 删除反向 B→A（groupId 固定为 0，与 addRelation 的反向插入对应）
        cardRelationDao.deleteRelation(
            relation.targetType, relation.targetId, 0,
            relation.sourceType, relation.sourceId
        )
    }

    /**
     * 删除某卡片某分组发起的所有关联
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     */
    suspend fun removeAllBySource(sourceType: String, sourceId: Long, groupId: Int) {
        cardRelationDao.deleteBySource(sourceType, sourceId, groupId)
    }

    /**
     * 删除某卡片所有分组发起的所有关联（删除整张卡片时调用）
     *
     * 由于 DAO 的 deleteBySource 已加 groupId 参数，此方法绕过 groupId
     * 直接删除该 source 的所有分组关联，避免在 ViewModel 中遍历所有 groupIds。
     *
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     */
    suspend fun removeAllBySourceAllGroups(sourceType: String, sourceId: Long) {
        cardRelationDao.deleteBySourceAllGroups(sourceType, sourceId)
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
        cardRelationDao.deleteBySourceAllGroups(cardType, cardId)
        cardRelationDao.deleteByTarget(cardType, cardId)
    }

    /**
     * 获取某卡片某分组的关联数量
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID
     * @return 关联数量
     */
    suspend fun getRelationCount(sourceType: String, sourceId: Long, groupId: Int): Int =
        cardRelationDao.countBySource(sourceType, sourceId, groupId)

    /**
     * 修复"新建模式提前 addRelation"导致的 sourceId=0 / targetId=0 脏数据（v2026-07-22 新增）
     *
     * **调用场景**：
     * 用户在"新建卡片"模式下打开关联选择器添加关联时，由于此时卡片尚未入库，
     * [addRelation] 会用 `existingCardId ?: 0L` 计算 sourceId，导致 card_relations 表里
     * 留下指向"卡片 0"的脏记录。本方法在新建保存成功后被调用一次，把所有此类脏数据
     * 迁移到新建卡片的真实 ID。
     *
     * **修复内容**：
     * - 正向 A→B 记录 (`sourceType=:type AND sourceId=0`)：UPDATE sourceId = :newId
     * - 反向 B→A 记录 (`targetType=:type AND targetId=0`)：UPDATE targetId = :newId
     *
     * **幂等性**：
     * 两个 UPDATE 独立且幂等，重复运行安全（运行后无 0 残留）。
     *
     * **非事务**：
     * 不引入 `@Transaction`，避免与已有事务方法冲突；
     * 两个 UPDATE 都命中"type=X AND id=0"的极少数行，即使并发执行也只会重复更新为相同值。
     *
     * @param newSourceType 新卡片类型 ("todo" | "inspiration" | "date")
     * @param newSourceId   新卡片实际 ID
     */
    suspend fun fixupZeroSourceRelations(newSourceType: String, newSourceId: Long) {
        val sourceFixed = cardRelationDao.fixupZeroSourceId(newSourceType, newSourceId)
        val targetFixed = cardRelationDao.fixupZeroTargetId(newSourceType, newSourceId)
        if (sourceFixed > 0 || targetFixed > 0) {
            android.util.Log.d(
                "CardRelationRepository",
                "fixupZeroSourceRelations: type=$newSourceType, id=$newSourceId, " +
                    "fixed sourceId=$sourceFixed, targetId=$targetFixed"
            )
        }
    }

    /**
     * 跨三表搜索卡片（用于关联选择器，v2026-07-22 性能优化）
     *
     * **历史问题**：
     * 旧实现对 inspirations / special_dates 用 `getAllXxxBlocking()` 全表加载后再
     * 内存过滤，当数据量上升时 P95 延迟 > 200ms。
     *
     * **新实现**：
     * - 三个查询用 [supervisorScope] + [async] **并发**执行
     * - inspirations / special_dates 改用各自 DAO 的 `searchXxxBlocking` SQL 层 LIKE 过滤
     * - 关键词为空时回退到 `getAllXxxBlocking`（保持向后兼容）
     *
     * **关于 LIMIT**：
     * 按用户意图"上限只限制显示不限制搜索"，本方法不做 LIMIT。
     * 渲染层 ([com.corgimemo.app.ui.components.RelationPickerBottomSheet]) 在显示时 take(50) 兜底。
     *
     * @param query 搜索关键词（空字符串返回全部卡片）
     * @return 搜索结果列表（todo / inspiration / date 合并，按各表自身排序）
     */
    suspend fun searchCards(query: String): List<CardSearchResult> = supervisorScope {
        val trimmedQuery = query.trim()
        val isBlank = trimmedQuery.isBlank()

        // 三个查询并发执行；任一异常被 supervisor 隔离，不影响其他两个
        val todosDeferred = async { todoDao.searchTodos(trimmedQuery) }
        val inspirationsDeferred = async {
            if (isBlank) {
                inspirationDao.getAllInspirationsBlocking()
            } else {
                inspirationDao.searchInspirationsBlocking(trimmedQuery)
            }
        }
        val datesDeferred = async {
            if (isBlank) {
                specialDateDao.getAllSpecialDatesBlocking()
            } else {
                specialDateDao.searchSpecialDatesBlocking(trimmedQuery)
            }
        }

        // 分别 await：async 已并发启动，逐个 await 仅同步结果
        val todos = todosDeferred.await()
        val inspirations = inspirationsDeferred.await()
        val dates = datesDeferred.await()

        buildList {
            todos.forEach { todo ->
                add(
                    CardSearchResult(
                        cardType = "todo",
                        cardId = todo.id,
                        title = todo.title
                    )
                )
            }
            inspirations.forEach { inspiration ->
                add(
                    CardSearchResult(
                        cardType = "inspiration",
                        cardId = inspiration.id,
                        title = inspiration.title
                    )
                )
            }
            dates.forEach { date ->
                add(
                    CardSearchResult(
                        cardType = "date",
                        cardId = date.id,
                        title = date.title
                    )
                )
            }
        }
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
     * 加载卡片详情（用于关联预览 Dialog 按类型差异化展示）
     *
     * 按卡片类型从对应表加载完整数据：
     * - todo：TodoItem + 分类名（CategoryDao） + 子任务进度（SubTaskDao）
     * - inspiration：Inspiration（含 tags/imagePaths JSON 解析）
     * - date：SpecialDate（targetDate/content/category）
     *
     * @param cardType 卡片类型
     * @param cardId 卡片ID
     * @return 卡片详情，卡片不存在返回 null
     */
    suspend fun loadCardDetail(cardType: String, cardId: Long): CardDetail? {
        return when (cardType) {
            "todo" -> {
                val todo = todoDao.getTodoById(cardId) ?: return null
                // 分类名：categoryId>0 才查询，避免无谓 DB 调用
                val categoryName = if (todo.categoryId > 0) {
                    categoryDao.getCategoryById(todo.categoryId)?.name
                } else {
                    null
                }
                // 子任务进度
                val subTasks = if (todo.hasSubTasks) {
                    subTaskDao.getSubTasksByTodoId(cardId)
                } else {
                    emptyList()
                }
                CardDetail.TodoDetail(
                    cardId = cardId,
                    title = todo.title,
                    dueDate = todo.dueDate,
                    categoryName = categoryName,
                    priority = todo.priority,
                    subTaskTotal = subTasks.size,
                    subTaskCompleted = subTasks.count { it.isCompleted }
                )
            }
            "inspiration" -> {
                val insp = inspirationDao.getInspirationById(cardId) ?: return null
                CardDetail.InspirationDetail(
                    cardId = cardId,
                    title = insp.title,
                    createdAt = insp.createdAt,
                    content = insp.content,
                    tags = parseJsonArray(insp.tags),
                    imagePaths = parseJsonArray(insp.imagePaths)
                )
            }
            "date" -> {
                val date = specialDateDao.getSpecialDateById(cardId) ?: return null
                CardDetail.DateDetail(
                    cardId = cardId,
                    title = date.title,
                    targetDate = date.targetDate,
                    content = date.content,
                    category = date.category
                )
            }
            else -> null
        }
    }

    /**
     * 解析 JSON 数组字符串为 List<String>
     *
     * 用于 Inspiration.tags / Inspiration.imagePaths 等字段的反序列化。
     * 空字符串或解析失败返回空列表。
     *
     * @param json JSON 数组字符串
     * @return 字符串列表
     */
    private fun parseJsonArray(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 获取某卡片某分组的第一条关联摘要（用于列表页提示）
     * @param sourceType 发起方类型
     * @param sourceId 发起方ID
     * @param groupId 分组ID（默认 0，向后兼容列表页主分组查询）
     * @return 关联摘要（第一条关联的类型+标题+总数），无关联返回null
     */
    suspend fun getRelationSummary(sourceType: String, sourceId: Long, groupId: Int = 0): RelationSummary? {
        val relations = cardRelationDao.getBySourceBlocking(sourceType, sourceId, groupId)
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
