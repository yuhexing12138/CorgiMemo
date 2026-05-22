package com.corgimemo.app.backup.serializer

import com.corgimemo.app.backup.data.BackupTodoItem
import com.corgimemo.app.backup.data.BackupCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV 序列化器
 * 用于将待办数据导出为 CSV 格式（Excel 兼容）
 */
object CsvSerializer {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * CSV 表头
     */
    private val headers = listOf(
        "ID",
        "标题",
        "内容",
        "分类",
        "分类ID",
        "优先级",
        "状态",
        "开始时间",
        "预计时长(分钟)",
        "提醒时间",
        "重复类型",
        "创建时间",
        "更新时间",
        "完成时间",
        "地理围栏地址"
    )

    /**
     * 获取预计完成时间
     * 计算方式：startDate + estimatedDurationMinutes
     *
     * @param todo 待办项
     * @return 预计完成时间戳，如果没有 startDate 或 estimatedDurationMinutes 则返回 null
     */
    private fun getEstimatedEndTime(todo: BackupTodoItem): Long? {
        return if (todo.startDate != null && todo.estimatedDurationMinutes != null) {
            todo.startDate + todo.estimatedDurationMinutes * 60000L
        } else {
            null
        }
    }

    /**
     * 将待办数据序列化为 CSV 字符串
     *
     * @param todos 待办列表
     * @param categories 分类列表（用于解析分类名称）
     * @return CSV 字符串
     */
    fun serialize(
        todos: List<BackupTodoItem>,
        categories: List<BackupCategory>
    ): String {
        val categoryMap = categories.associate { it.id to it.name }

        val csvBuilder = StringBuilder()

        csvBuilder.appendLine(headers.joinToString(","))

        todos.forEach { todo ->
            val row = listOf(
                todo.id.toString(),
                escapeCsvField(todo.title),
                escapeCsvField(todo.content ?: ""),
                escapeCsvField(categoryMap[todo.categoryId] ?: ""),
                todo.categoryId.toString(),
                getPriorityText(todo.priority),
                getStatusText(todo.status),
                formatDate(todo.startDate),
                todo.estimatedDurationMinutes?.toString() ?: "",
                formatDate(todo.reminderTime),
                getRepeatTypeText(todo.repeatType),
                formatDate(todo.createdAt),
                formatDate(todo.updatedAt),
                formatDate(todo.completedAt),
                escapeCsvField(todo.geofenceAddress ?: "")
            )
            csvBuilder.appendLine(row.joinToString(","))
        }

        return csvBuilder.toString()
    }

    /**
     * 转义 CSV 字段
     * 处理包含逗号、引号、换行符的情况
     */
    private fun escapeCsvField(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    /**
     * 格式化日期时间
     */
    private fun formatDate(timestamp: Long?): String {
        return if (timestamp != null && timestamp > 0) {
            dateFormat.format(Date(timestamp))
        } else {
            ""
        }
    }

    /**
     * 获取优先级文本
     */
    private fun getPriorityText(priority: Int): String {
        return when (priority) {
            2 -> "高"
            1 -> "中"
            0 -> "低"
            else -> priority.toString()
        }
    }

    /**
     * 获取状态文本
     */
    private fun getStatusText(status: Int): String {
        return when (status) {
            0 -> "待办"
            1 -> "已完成"
            2 -> "已归档"
            else -> status.toString()
        }
    }

    /**
     * 获取重复类型文本
     */
    private fun getRepeatTypeText(type: Int): String {
        return when (type) {
            0 -> "不重复"
            1 -> "每天"
            2 -> "每周"
            3 -> "每月"
            4 -> "每年"
            else -> type.toString()
        }
    }
}
