package com.corgimemo.app.backup.exporter

import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * iCal 导出器
 * 将待办列表转换为 iCalendar (.ics) 格式
 * 手动构建 iCal 字符串，避免外部库 API 兼容性问题
 */
object IcsExporter {

    private val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    init {
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * 获取预计完成时间
     * 计算方式：startDate + estimatedDurationMinutes
     *
     * @param todo 待办项
     * @return 预计完成时间戳，如果没有 startDate 或 estimatedDurationMinutes 则返回 null
     */
    private fun getEstimatedEndTime(todo: TodoItem): Long? {
        return if (todo.startDate != null && todo.estimatedDurationMinutes != null) {
            todo.startDate + todo.estimatedDurationMinutes * 60000L
        } else {
            null
        }
    }

    /**
     * 导出待办列表为 iCal 格式字符串
     *
     * @param todos 待办列表
     * @param categories 分类列表
     * @return iCal 格式的字符串
     */
    fun exportTodos(
        todos: List<TodoItem>,
        categories: List<Category>
    ): String {
        val sb = StringBuilder()

        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//CorgiMemo//待办导出//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")

        todos.forEach { todo ->
            val categoryName = categories.find { it.id == todo.categoryId }?.name ?: "默认"
            sb.append(todoToIcs(todo, categoryName))
        }

        sb.appendLine("END:VCALENDAR")

        return sb.toString()
    }

    /**
     * 将待办项转换为 iCal VEVENT 格式
     *
     * @param todo 待办项
     * @param categoryName 分类名称
     * @return VEVENT 字符串
     */
    private fun todoToIcs(todo: TodoItem, categoryName: String): String {
        val sb = StringBuilder()

        val estimatedEndTime = getEstimatedEndTime(todo)
        val eventTime = todo.reminderTime ?: estimatedEndTime ?: todo.startDate ?: System.currentTimeMillis()
        val startTime = formatDateTime(eventTime)
        val endTime = formatDateTime(eventTime + 30 * 60 * 1000)

        val status = if (todo.status == 1) "COMPLETED" else "CONFIRMED"
        val uid = UUID.randomUUID().toString() + "@corgimemo"

        sb.appendLine("BEGIN:VEVENT")
        sb.appendLine("UID:$uid")
        sb.appendLine("DTSTAMP:$startTime")
        sb.appendLine("DTSTART:$startTime")
        sb.appendLine("DTEND:$endTime")
        sb.appendLine("SUMMARY:${escapeIcsText(buildSummary(todo, categoryName))}")

        val description = buildDescription(todo)
        if (description.isNotEmpty()) {
            sb.appendLine("DESCRIPTION:${escapeIcsText(description)}")
        }

        sb.appendLine("CATEGORIES:${escapeIcsText(categoryName)}")

        val priority = getIcalPriority(todo.priority)
        sb.appendLine("PRIORITY:$priority")

        sb.appendLine("STATUS:$status")
        sb.appendLine("SEQUENCE:0")

        val rrule = getRepeatRule(todo.repeatType)
        if (rrule != null) {
            sb.appendLine("RRULE:$rrule")
        }

        sb.appendLine("END:VEVENT")

        return sb.toString()
    }

    /**
     * 构建事件标题
     */
    private fun buildSummary(todo: TodoItem, categoryName: String): String {
        val priorityLabel = when (todo.priority) {
            2 -> "🔴"
            1 -> "🟡"
            0 -> "🟢"
            else -> ""
        }
        return "$priorityLabel [$categoryName] ${todo.title}"
    }

    /**
     * 构建事件描述
     */
    private fun buildDescription(todo: TodoItem): String {
        val sb = StringBuilder()

        todo.startDate?.let {
            sb.append("开始时间: ${displayDateFormat.format(Date(it))}\\n")
        }

        todo.estimatedDurationMinutes?.let {
            sb.append("预计时长: ${formatDuration(it)}\\n")
        }

        todo.reminderTime?.let {
            sb.append("提醒时间: ${displayDateFormat.format(Date(it))}\\n")
        }

        sb.append("优先级: ${getPriorityText(todo.priority)}\\n")

        getRepeatText(todo.repeatType)?.let {
            sb.append("重复: $it\\n")
        }

        todo.content?.let {
            if (sb.isNotEmpty()) {
                sb.append("\\n")
            }
            sb.append(it)
        }

        return sb.toString()
    }

    /**
     * 格式化预计完成时长为显示文本
     *
     * @param minutes 预计完成时长（分钟）
     * @return 格式化后的时长文本，如 "1小时30分钟"、"2小时"、"45分钟"
     */
    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}小时${mins}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${mins}分钟"
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
            else -> "普通"
        }
    }

    /**
     * 获取 iCal 优先级值
     * iCal 标准: 1=高, 5=中, 9=低
     */
    private fun getIcalPriority(priority: Int): Int {
        return when (priority) {
            2 -> 1
            1 -> 5
            0 -> 9
            else -> 5
        }
    }

    /**
     * 获取重复文本
     */
    private fun getRepeatText(repeatType: Int): String? {
        return when (repeatType) {
            1 -> "每天"
            2 -> "每周"
            3 -> "每月"
            4 -> "每年"
            else -> null
        }
    }

    /**
     * 获取 iCal 重复规则
     */
    private fun getRepeatRule(repeatType: Int): String? {
        return when (repeatType) {
            1 -> "FREQ=DAILY"
            2 -> "FREQ=WEEKLY"
            3 -> "FREQ=MONTHLY"
            4 -> "FREQ=YEARLY"
            else -> null
        }
    }

    /**
     * 格式化日期时间为 iCal UTC 格式
     */
    private fun formatDateTime(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }

    /**
     * 转义 iCal 文本中的特殊字符
     * 根据 RFC 5545 规范，需要转义: \, ;, ,, 换行
     */
    private fun escapeIcsText(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
            .replace("\r", "")
            .let { foldLongLine(it) }
    }

    /**
     * 折叠长行（iCal 规范要求每行不超过 75 个字符）
     */
    private fun foldLongLine(text: String): String {
        if (text.length <= 75) {
            return text
        }

        val result = StringBuilder()
        var currentLine = StringBuilder()
        var firstLine = true

        for (char in text) {
            if (currentLine.length >= 70) {
                result.appendLine(currentLine.toString())
                currentLine = StringBuilder()
                if (firstLine) {
                    firstLine = false
                }
                currentLine.append(" ")
            }
            currentLine.append(char)
        }

        if (currentLine.isNotEmpty()) {
            result.append(currentLine.toString())
        }

        return result.toString()
    }
}
