package com.corgimemo.app.data.seed

import android.util.Log
import com.corgimemo.app.data.local.db.CardRelationDao
import com.corgimemo.app.data.model.CardRelation

/**
 * 跨模块关联种子数据注入器
 *
 * 负责 5 条 CardRelation 的数据库注入，覆盖三种关联类型：
 * - 待办 → 日期（R1）
 * - 灵感 → 待办（R2、R4）
 * - 灵感 → 灵感（R3）
 * - 日期 → 待办（R5）
 *
 * @param cardRelationDao 卡片关联 DAO
 */
class RelationSeedData(
    private val cardRelationDao: CardRelationDao
) {
    private val tag = "DemoSeeder"

    /**
     * 注入跨模块关联种子数据
     *
     * @param todoIds 待办 ID 映射（编号 → ID）
     * @param inspirationIds 灵感 ID 映射（编号 → ID）
     * @param dateIds 日期 ID 映射（编号 → ID）
     */
    suspend fun seed(
        todoIds: Map<String, Long>,
        inspirationIds: Map<String, Long>,
        dateIds: Map<String, Long>
    ) {
        val now = System.currentTimeMillis()

        // R1: T1 → D1（待办"参赛贴撰写"关联日期"初赛报名截止"）
        cardRelationDao.insert(CardRelation(
            sourceType = "todo",
            sourceId = todoIds["T1"]!!,
            targetType = "date",
            targetId = dateIds["D1"]!!,
            createdAt = now
        ))

        // R2: I2 → T2（灵感"状态机架构"关联待办"数据库架构设计"）
        cardRelationDao.insert(CardRelation(
            sourceType = "inspiration",
            sourceId = inspirationIds["I2"]!!,
            targetType = "todo",
            targetId = todoIds["T2"]!!,
            createdAt = now
        ))

        // R3: I3 → I1（灵感"卡片关联设计"关联灵感"陪伴系统设计"）
        cardRelationDao.insert(CardRelation(
            sourceType = "inspiration",
            sourceId = inspirationIds["I3"]!!,
            targetType = "inspiration",
            targetId = inspirationIds["I1"]!!,
            createdAt = now
        ))

        // R4: T3 → I4（待办"悬浮按钮优化"关联灵感"Snackbar 规范"）
        cardRelationDao.insert(CardRelation(
            sourceType = "todo",
            sourceId = todoIds["T3"]!!,
            targetType = "inspiration",
            targetId = inspirationIds["I4"]!!,
            createdAt = now
        ))

        // R5: D2 → T6（日期"项目启动日"关联待办"种子数据注入"）
        cardRelationDao.insert(CardRelation(
            sourceType = "date",
            sourceId = dateIds["D2"]!!,
            targetType = "todo",
            targetId = todoIds["T6"]!!,
            createdAt = now
        ))

        Log.d(tag, "✅ 步骤 7/7 关联注入完成（5 条）")
    }
}
