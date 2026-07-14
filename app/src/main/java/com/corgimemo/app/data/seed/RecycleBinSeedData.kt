package com.corgimemo.app.data.seed

import android.util.Log
import com.corgimemo.app.data.local.db.DeletedInspirationDao
import com.corgimemo.app.data.local.db.DeletedSpecialDateDao
import com.corgimemo.app.data.local.db.DeletedTodoDao
import com.corgimemo.app.data.model.DeletedInspiration
import com.corgimemo.app.data.model.DeletedSpecialDate
import com.corgimemo.app.data.model.DeletedTodo
import org.json.JSONArray

/**
 * 回收站种子数据注入器
 *
 * 负责向回收站注入 5 条已删除的示例数据（2 待办 + 2 灵感 + 1 日期），
 * 模拟用户在开发过程中删除的旧版任务、过时灵感和废弃日期。
 *
 * ID 使用 9001+ 区间，避免与正常 autoGenerate 的 ID 冲突。
 *
 * @param deletedTodoDao 已删除待办 DAO
 * @param deletedInspirationDao 已删除灵感 DAO
 * @param deletedSpecialDateDao 已删除日期 DAO
 */
class RecycleBinSeedData(
    private val deletedTodoDao: DeletedTodoDao,
    private val deletedInspirationDao: DeletedInspirationDao,
    private val deletedSpecialDateDao: DeletedSpecialDateDao
) {
    private val tag = "DemoSeeder"

    /**
     * 注入回收站种子数据
     *
     * @param categoryIds 分类 ID 映射（type → categoryId）
     */
    suspend fun seed(categoryIds: Map<Int, Long>) {

        // ========== 已删除待办 1：旧版引导流程设计 ==========
        val dt1 = DeletedTodo(
            id = 9001L,
            title = "旧版引导流程设计",
            content = "设计首次启动引导流程，包含功能介绍和操作指引。后被 10 页 HorizontalPager 引导流替代。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 2, // 中
            status = 1, // 已完成
            repeatType = 0,
            createdAt = timeStamp(2026, 6, 1, 10, 0),
            updatedAt = timeStamp(2026, 7, 13, 10, 0),
            completedAt = timeStamp(2026, 7, 13, 10, 0),
            hasSubTasks = false,
            deletedAt = timeStamp(2026, 7, 14, 16, 32)
        )
        deletedTodoDao.insert(dt1)

        // ========== 已删除待办 2：初始 UI 原型设计 ==========
        val dt2 = DeletedTodo(
            id = 9002L,
            title = "初始 UI 原型设计",
            content = "绘制 app 的初始界面原型，包含主要页面布局和导航结构。后经多轮迭代已大幅调整。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 1, // 低
            status = 0, // 待办
            repeatType = 0,
            createdAt = timeStamp(2026, 5, 25, 14, 0),
            updatedAt = timeStamp(2026, 7, 10, 16, 0),
            hasSubTasks = false,
            deletedAt = timeStamp(2026, 7, 10, 16, 0)
        )
        deletedTodoDao.insert(dt2)

        // ========== 已删除灵感 1：旧版配色方案构思 ==========
        val di1Content = "最初考虑使用蓝白配色方案，后来觉得太冷淡，改为温暖的橙色调，更符合柯基带来的温暖感。"
        val di1 = DeletedInspiration(
            id = 9001L,
            title = "旧版配色方案构思",
            content = di1Content,
            contentFormat = di1Content,
            tags = toJsonArray(listOf("设计", "配色")),
            imagePaths = toJsonArray(emptyList()),
            createdAt = timeStamp(2026, 5, 22, 15, 0),
            updatedAt = timeStamp(2026, 7, 8, 9, 0),
            isPinned = false,
            categoryId = categoryIds[1]!!,
            priority = 0,
            status = 0,
            deletedAt = timeStamp(2026, 7, 8, 9, 0)
        )
        deletedInspirationDao.insert(di1)

        // ========== 已删除灵感 2：初版功能列表设想 ==========
        val di2Content = "最初规划了天气、日历、笔记等多个功能模块，后来精简为待办、灵感、日期三大核心，专注做好一件事。"
        val di2 = DeletedInspiration(
            id = 9002L,
            title = "初版功能列表设想",
            content = di2Content,
            contentFormat = di2Content,
            tags = toJsonArray(listOf("产品", "规划")),
            imagePaths = toJsonArray(emptyList()),
            createdAt = timeStamp(2026, 5, 20, 20, 0),
            updatedAt = timeStamp(2026, 7, 5, 10, 0),
            isPinned = false,
            categoryId = categoryIds[1]!!,
            priority = 1,
            status = 0,
            deletedAt = timeStamp(2026, 7, 5, 10, 0)
        )
        deletedInspirationDao.insert(di2)

        // ========== 已删除日期 1：初版设计评审日 ==========
        val dd1 = DeletedSpecialDate(
            id = 9001L,
            title = "初版设计评审日",
            category = "OTHER",
            countMode = 1, // 正计时
            targetDate = timeStamp(2026, 6, 1, 14, 0),
            createdAt = timeStamp(2026, 5, 28, 10, 0),
            updatedAt = timeStamp(2026, 7, 8, 16, 0),
            cardStyle = "CALENDAR_TEAR_OFF",
            cardColor = "BROWN",
            deletedAt = timeStamp(2026, 7, 8, 16, 0)
        )
        deletedSpecialDateDao.insert(dd1)

        Log.d(tag, "✅ 步骤 8/8 回收站注入完成（2 待办 + 2 灵感 + 1 日期）")
    }

    /**
     * 将字符串列表转为 JSON 数组字符串
     */
    private fun toJsonArray(items: List<String>): String {
        val jsonArray = JSONArray()
        items.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    /**
     * 生成时间戳（毫秒）
     */
    private fun timeStamp(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month - 1, day, hour, minute, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
