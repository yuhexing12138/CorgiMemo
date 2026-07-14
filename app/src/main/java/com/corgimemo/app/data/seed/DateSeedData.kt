package com.corgimemo.app.data.seed

import android.util.Log
import com.corgimemo.app.data.local.db.SpecialDateDao
import com.corgimemo.app.data.model.SpecialDate
import org.json.JSONArray

/**
 * 日期种子数据注入器
 *
 * 负责 6 条日期（D1-D6）的数据库注入。
 * 数据内容取材于参赛贴的实际开发过程，覆盖：
 * - 倒计时（countMode=0）/ 正计时（countMode=1）
 * - 归档（isArchived=true）
 * - 过去/当前/未来时间
 * - 置顶（isPinned=true）
 * - 6 条日期使用不同的 cardStyle 和 cardColor 组合，确保视觉多样性
 *
 * @param specialDateDao 日期 DAO
 */
class DateSeedData(
    private val specialDateDao: SpecialDateDao
) {
    private val tag = "DemoSeeder"

    /**
     * 注入日期种子数据
     *
     * @param imagePaths 图片路径映射（数据编号 → 路径列表）
     * @return 日期 ID 映射（编号 D1-D6 → specialDateId）
     */
    suspend fun seed(imagePaths: Map<String, List<String>>): Map<String, Long> {
        val dateIds = mutableMapOf<String, Long>()

        // ========== D1：TRAE 初赛报名截止 ==========
        val d1 = SpecialDate(
            title = "TRAE 初赛报名截止",
            targetDate = timeStamp(2026, 7, 15, 23, 59, 59),
            category = "OTHER",
            countMode = 0, // 倒计时
            repeatType = 0,
            reminderDays = 1,
            content = "TRAE 初赛参赛贴提交截止时间，需在此之前完成所有内容整理和提交。",
            tags = toJsonArray(listOf("参赛", "截止")),
            imagePaths = toJsonArray(imagePaths["D1"] ?: emptyList()),
            cardStyle = "ORANGE_TEAR_OFF",
            cardColor = "RED",
            isPinned = false,
            isArchived = false,
            createdAt = timeStamp(2026, 7, 10, 9, 0),
            updatedAt = timeStamp(2026, 7, 10, 9, 0)
        )
        dateIds["D1"] = specialDateDao.insert(d1)

        // ========== D2：刻记+ 项目启动日 ==========
        val d2 = SpecialDate(
            title = "刻记+ 项目启动日",
            targetDate = timeStamp(2026, 5, 20, 10, 0),
            category = "ANNIVERSARY",
            countMode = 1, // 正计时
            repeatType = 1, // 按年
            reminderDays = 0,
            content = "刻记+ 项目正式启动，开始为期近两个月的开发旅程。",
            tags = toJsonArray(listOf("里程碑", "启动")),
            imagePaths = toJsonArray(imagePaths["D2"] ?: emptyList()),
            cardStyle = "CALENDAR_TEAR_OFF",
            cardColor = "BLUE",
            isPinned = false,
            isArchived = false,
            createdAt = timeStamp(2026, 5, 20, 10, 0),
            updatedAt = timeStamp(2026, 5, 20, 10, 0)
        )
        dateIds["D2"] = specialDateDao.insert(d2)

        // ========== D3：数据库 v38 版本发布 ==========
        val d3 = SpecialDate(
            title = "数据库 v38 版本发布",
            targetDate = timeStamp(2026, 7, 10, 18, 0),
            category = "OTHER",
            countMode = 1, // 正计时
            repeatType = 0,
            reminderDays = 0,
            content = "Room 数据库从 v2 演进至 v38，涵盖 36 个 Migration，支持 20 个 Entity 和 20 个 DAO。",
            tags = toJsonArray(listOf("架构", "数据库")),
            imagePaths = toJsonArray(imagePaths["D3"] ?: emptyList()),
            cardStyle = "ORANGE_TEAR_OFF",
            cardColor = "GREEN",
            isPinned = false,
            isArchived = false,
            createdAt = timeStamp(2026, 7, 10, 18, 0),
            updatedAt = timeStamp(2026, 7, 10, 18, 0)
        )
        dateIds["D3"] = specialDateDao.insert(d3)

        // ========== D4：参赛贴提交截止 ==========
        val d4 = SpecialDate(
            title = "参赛贴提交截止",
            targetDate = timeStamp(2026, 7, 16, 12, 0),
            category = "HOLIDAY",
            countMode = 0, // 倒计时
            repeatType = 0,
            reminderDays = 0,
            content = "参赛贴最终提交时间，需完成所有校验和排版。",
            tags = toJsonArray(listOf("参赛", "提交")),
            imagePaths = toJsonArray(imagePaths["D4"] ?: emptyList()),
            cardStyle = "CALENDAR_TEAR_OFF",
            cardColor = "PURPLE",
            isPinned = false,
            isArchived = false,
            createdAt = timeStamp(2026, 7, 14, 10, 0),
            updatedAt = timeStamp(2026, 7, 14, 10, 0)
        )
        dateIds["D4"] = specialDateDao.insert(d4)

        // ========== D5：复赛启动日 ==========
        val d5 = SpecialDate(
            title = "复赛启动日",
            targetDate = timeStamp(2026, 8, 1, 9, 0),
            category = "OTHER",
            countMode = 0, // 倒计时
            repeatType = 0,
            reminderDays = 7,
            content = "TRAE 复赛正式启动，需提前准备复赛功能和演示材料。",
            tags = toJsonArray(listOf("参赛", "复赛")),
            imagePaths = toJsonArray(imagePaths["D5"] ?: emptyList()),
            cardStyle = "ORANGE_TEAR_OFF",
            cardColor = "ORANGE",
            isPinned = true,
            isArchived = false,
            createdAt = timeStamp(2026, 7, 14, 15, 0),
            updatedAt = timeStamp(2026, 7, 14, 15, 0)
        )
        dateIds["D5"] = specialDateDao.insert(d5)

        // ========== D6：旧版引导流程废弃 ==========
        val d6 = SpecialDate(
            title = "旧版引导流程废弃",
            targetDate = timeStamp(2026, 7, 14, 16, 32),
            category = "OTHER",
            countMode = 1, // 正计时
            repeatType = 0,
            reminderDays = 0,
            content = "删除旧的 OperationGuide/FirstTimeGuideOverlay/CorgiGuideAnimation 组件，替换为 10 页 HorizontalPager 引导流。",
            tags = toJsonArray(listOf("重构", "引导")),
            imagePaths = toJsonArray(imagePaths["D6"] ?: emptyList()),
            cardStyle = "CALENDAR_TEAR_OFF",
            cardColor = "NAVY",
            isPinned = false,
            isArchived = true, // 归档
            createdAt = timeStamp(2026, 7, 14, 16, 32),
            updatedAt = timeStamp(2026, 7, 14, 16, 32)
        )
        dateIds["D6"] = specialDateDao.insert(d6)

        Log.d(tag, "✅ 步骤 6/7 日期注入完成（6 条，含随机样式与颜色）")
        return dateIds
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
    private fun timeStamp(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(year, month - 1, day, hour, minute, second)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
