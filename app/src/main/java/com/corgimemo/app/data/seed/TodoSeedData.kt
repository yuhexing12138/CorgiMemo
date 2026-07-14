package com.corgimemo.app.data.seed

import android.util.Log
import com.corgimemo.app.data.local.db.SubTaskDao
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import org.json.JSONArray

/**
 * 待办种子数据注入器
 *
 * 负责 6 条待办（T1-T6）及其子任务的数据库注入。
 * 数据内容取材于参赛贴的实际开发过程，覆盖：
 * - 高/中/低优先级
 * - 4 个 TodoZone（PINNED_PENDING / PENDING / PINNED_COMPLETED / COMPLETED）
 * - 过去/当前/未来提醒时间
 * - 0-5 个子任务
 * - 1-3 张图片附件
 * - 不同时长语音附件（10s/30s/65s）
 *
 * @param todoDao 待办 DAO
 * @param subTaskDao 子任务 DAO
 */
class TodoSeedData(
    private val todoDao: TodoDao,
    private val subTaskDao: SubTaskDao
) {
    private val tag = "DemoSeeder"

    /**
     * 注入待办种子数据
     *
     * @param categoryIds 分类 ID 映射（type → categoryId）
     * @param imagePaths 图片路径映射（数据编号 → 路径列表）
     * @param voicePaths 语音路径映射（数据编号 → 路径）
     * @return 待办 ID 映射（编号 T1-T6 → todoId）
     */
    suspend fun seed(
        categoryIds: Map<Int, Long>,
        imagePaths: Map<String, List<String>>,
        voicePaths: Map<String, String>
    ): Map<String, Long> {
        val todoIds = mutableMapOf<String, Long>()

        // ========== T1：TRAE 初赛参赛贴撰写 ==========
        val t1 = TodoItem(
            title = "TRAE 初赛参赛贴撰写",
            content = "基于项目实际开发过程，系统性整理参赛贴内容，涵盖产品概述、主要功能、创作思路、TRAE 实践过程和经验总结。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 0, // 待办
            startDate = timeStamp(2026, 7, 10, 9, 0),
            dueDate = timeStamp(2026, 7, 15, 23, 59, 59),
            reminderTime = timeStamp(2026, 7, 15, 20, 0), // 未来
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 10, 9, 0),
            updatedAt = timeStamp(2026, 7, 14, 10, 0),
            hasSubTasks = true,
            voiceNotePath = voicePaths["T1"],
            voiceDuration = 8,
            imagePaths = toJsonArray(imagePaths["T1"] ?: emptyList()),
            isPinned = true,
            sortOrder = 100
        )
        todoIds["T1"] = todoDao.insert(t1)

        // ========== T2：刻记+ 数据库架构设计 ==========
        val t2 = TodoItem(
            title = "刻记+ 数据库架构设计",
            content = "设计 Room 数据库的 20 个 Entity 和 20 个 DAO，规划从 v2 到 v38 的 Migration 路径，确保数据层稳定演进。",
            categoryId = categoryIds[0]!!, // 学习
            priority = 2, // 中
            status = 0, // 待办
            startDate = timeStamp(2026, 6, 20, 10, 0),
            dueDate = timeStamp(2026, 6, 25, 18, 0),
            reminderTime = timeStamp(2026, 6, 20, 9, 0), // 过去
            repeatType = 0,
            createdAt = timeStamp(2026, 6, 20, 10, 0),
            updatedAt = timeStamp(2026, 6, 20, 10, 0),
            hasSubTasks = true,
            voiceNotePath = voicePaths["T2"],
            voiceDuration = 28,
            imagePaths = toJsonArray(imagePaths["T2"] ?: emptyList()),
            isPinned = false,
            sortOrder = 10000
        )
        todoIds["T2"] = todoDao.insert(t2)

        // ========== T3：悬浮柯基按钮交互优化 ==========
        val t3 = TodoItem(
            title = "悬浮柯基按钮交互优化",
            content = "解决悬浮按钮滑动阻尼、位置偏差和右屏限制问题，采用绝对位置追踪方案重写手势处理逻辑。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 1, // 已完成
            startDate = timeStamp(2026, 7, 13, 14, 0),
            dueDate = timeStamp(2026, 7, 13, 18, 0),
            reminderTime = timeStamp(2026, 7, 14, 12, 0), // 当前
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 13, 14, 0),
            updatedAt = timeStamp(2026, 7, 13, 18, 0),
            completedAt = timeStamp(2026, 7, 13, 18, 0),
            hasSubTasks = true,
            voiceNotePath = voicePaths["T3"],
            voiceDuration = 65,
            imagePaths = toJsonArray(imagePaths["T3"] ?: emptyList()),
            isPinned = true,
            sortOrder = 20000
        )
        todoIds["T3"] = todoDao.insert(t3)

        // ========== T4：日期页侧滑栏重构 ==========
        val t4 = TodoItem(
            title = "日期页侧滑栏重构",
            content = "移除旧的倒计时/正计时/已归档选项，改为类型分类（纪念日/生日），添加\"添加类型\"按钮，顶部柯基静态图替换为帧动画。",
            categoryId = categoryIds[2]!!, // 生活
            priority = 0, // 无优先级
            status = 1, // 已完成
            startDate = timeStamp(2026, 7, 14, 9, 0),
            dueDate = timeStamp(2026, 7, 14, 17, 0),
            reminderTime = timeStamp(2026, 7, 14, 8, 30), // 过去
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 14, 9, 0),
            updatedAt = timeStamp(2026, 7, 14, 17, 0),
            completedAt = timeStamp(2026, 7, 14, 17, 0),
            hasSubTasks = false,
            voiceNotePath = voicePaths["T4"],
            voiceDuration = 5,
            imagePaths = toJsonArray(imagePaths["T4"] ?: emptyList()),
            isPinned = false,
            sortOrder = 30000
        )
        todoIds["T4"] = todoDao.insert(t4)

        // ========== T5：灵感页富文本编辑器集成 ==========
        val t5 = TodoItem(
            title = "灵感页富文本编辑器集成",
            content = "在灵感编辑页集成 Markdown 富文本编辑器，支持粗体/斜体/删除线/列表等格式，与 contentFormat 字段对接。",
            categoryId = categoryIds[0]!!, // 学习
            priority = 1, // 低
            status = 0, // 待办
            startDate = timeStamp(2026, 7, 16, 10, 0),
            dueDate = timeStamp(2026, 7, 20, 18, 0),
            reminderTime = timeStamp(2026, 7, 16, 9, 0), // 未来
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 14, 11, 0),
            updatedAt = timeStamp(2026, 7, 14, 11, 0),
            hasSubTasks = true,
            voiceNotePath = voicePaths["T5"],
            voiceDuration = 32,
            imagePaths = toJsonArray(imagePaths["T5"] ?: emptyList()),
            isPinned = false,
            sortOrder = 10100
        )
        todoIds["T5"] = todoDao.insert(t5)

        // ========== T6：演示种子数据注入机制 ==========
        val t6 = TodoItem(
            title = "演示种子数据注入机制",
            content = "实现 Application.onCreate 首次启动自动注入演示数据，覆盖待办/灵感/日期三大模块及跨模块关联。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 0, // 待办
            startDate = timeStamp(2026, 7, 14, 15, 0),
            dueDate = timeStamp(2026, 7, 14, 23, 59, 59),
            reminderTime = timeStamp(2026, 7, 14, 15, 0), // 当前
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 14, 15, 0),
            updatedAt = timeStamp(2026, 7, 14, 15, 0),
            hasSubTasks = true,
            voiceNotePath = voicePaths["T6"],
            voiceDuration = 70,
            imagePaths = toJsonArray(imagePaths["T6"] ?: emptyList()),
            isPinned = true,
            sortOrder = 200
        )
        todoIds["T6"] = todoDao.insert(t6)

        // ========== T7：参赛贴最终校验与排版（高优先级，普通待办区） ==========
        val t7 = TodoItem(
            title = "参赛贴最终校验与排版",
            content = "完成参赛贴的最终内容校验、截图替换、排版优化和提交确认，确保文档质量符合评审标准。重点检查格式一致性、图片清晰度和链接有效性。",
            categoryId = categoryIds[1]!!, // 工作
            priority = 3, // 高
            status = 0, // 待办
            startDate = timeStamp(2026, 7, 15, 9, 0),
            dueDate = timeStamp(2026, 7, 15, 18, 0),
            reminderTime = timeStamp(2026, 7, 15, 8, 0), // 未来
            repeatType = 0,
            createdAt = timeStamp(2026, 7, 14, 20, 0),
            updatedAt = timeStamp(2026, 7, 14, 20, 0),
            hasSubTasks = true,
            imagePaths = toJsonArray(imagePaths["T7"] ?: emptyList()),
            isPinned = false,
            sortOrder = 10200
        )
        todoIds["T7"] = todoDao.insert(t7)

        // ========== 注入子任务 ==========
        seedSubTasks(todoIds)

        Log.d(tag, "✅ 步骤 3/7 待办注入完成（7 条 + 子任务）")
        return todoIds
    }

    /**
     * 注入子任务
     */
    private suspend fun seedSubTasks(todoIds: Map<String, Long>) {
        val now = System.currentTimeMillis()

        // T1 的 5 个子任务
        val t1Id = todoIds["T1"]!!
        listOf(
            "整理 Session ID 列表" to 0,
            "撰写产品概述" to 1,
            "补充主要功能" to 2,
            "完善创作思路" to 3,
            "校验经验总结" to 4
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(todoId = t1Id, title = title, order = order, createdAt = now))
        }

        // T2 的 3 个子任务
        val t2Id = todoIds["T2"]!!
        listOf(
            "定义 Entity 字段" to 0,
            "编写 DAO 接口" to 1,
            "设计 Migration 路径" to 2
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(todoId = t2Id, title = title, order = order, createdAt = now))
        }

        // T3 的 4 个子任务
        val t3Id = todoIds["T3"]!!
        listOf(
            "分析触摸事件逻辑" to 0,
            "重写手势处理" to 1,
            "调整动画参数" to 2,
            "验证边界约束" to 3
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(
                todoId = t3Id,
                title = title,
                isCompleted = true,
                createdAt = now,
                completedAt = now,
                order = order
            ))
        }

        // T5 的 2 个子任务
        val t5Id = todoIds["T5"]!!
        listOf(
            "调研 Compose 富文本方案" to 0,
            "实现 Markdown 解析器" to 1
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(todoId = t5Id, title = title, order = order, createdAt = now))
        }

        // T6 的 5 个子任务
        val t6Id = todoIds["T6"]!!
        listOf(
            "设计分层 Seeder 架构" to 0,
            "实现资源管理器" to 1,
            "编写待办种子数据" to 2,
            "编写灵感种子数据" to 3,
            "编写日期种子数据" to 4
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(todoId = t6Id, title = title, order = order, createdAt = now))
        }

        // T7 的 2 个子任务
        val t7Id = todoIds["T7"]!!
        listOf(
            "检查格式与排版一致性" to 0,
            "替换演示截图与校验链接" to 1
        ).forEach { (title, order) ->
            subTaskDao.insert(SubTask(todoId = t7Id, title = title, order = order, createdAt = now))
        }
    }

    /**
     * 将路径列表转为 JSON 数组字符串
     */
    private fun toJsonArray(paths: List<String>): String {
        val jsonArray = JSONArray()
        paths.forEach { jsonArray.put(it) }
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
