package com.corgimemo.app.ui.model

/**
 * 复选框编辑器的单行数据模型
 *
 * 每一行待办内容对应一个 TodoLine 实例，
 * 支持主任务行和子任务行（缩进）两种模式。
 * 多个同 groupId 的行组成一个独立容器（待办组）。
 *
 * 混合存储策略：
 * - 文本内容拼接后存入 TodoItem.content 字段（用于显示和搜索）
 * - 勾选状态和结构信息同步到 sub_tasks 表（用于结构化查询）
 * - 图片和语音附件与行关联，支持每行独立管理附件
 */
data class TodoLine(
    /** 行文本内容 */
    val text: String = "",
    /** 是否已勾选完成 */
    val isChecked: Boolean = false,
    /** 是否为子任务行（子任务行带缩进） */
    val isSubTask: Boolean = false,
    /** 关联的子任务数据库 ID（0 表示尚未持久化） */
    val subTaskId: Long = 0L,
    /** 所属容器分组 ID，同一组的行渲染在同一个圆角容器内 */
    val groupId: Int = 0,
    /** 行在列表中的排序索引 */
    val order: Int = 0,
    /** 该行的图片附件路径列表（支持多张图片） */
    val imagePaths: List<String> = emptyList(),
    /** 该行的语音附件列表（支持多条语音） */
    val voiceAttachments: List<VoiceAttachment> = emptyList()
) {
    /** 判断该行是否为空行（无实质内容且无附件） */
    fun isEmpty(): Boolean = text.isBlank() && imagePaths.isEmpty() && voiceAttachments.isEmpty()

    /** 将本行序列化为纯文本格式（用于写入 content 字段） */
    fun toPlainText(): String {
        val prefix = if (isChecked) "☑" else "☐"
        val indent = if (isSubTask) "  " else ""
        return "$indent${prefix} ${text}"
    }

    companion object {
        /** 从纯文本解析为 TodoLine 列表 */
        fun parseFromText(text: String): List<TodoLine> {
            if (text.isBlank()) return listOf(TodoLine())
            return text.lines().mapIndexed { index, line ->
                // 解析缩进（前导空格判断是否为子任务）
                val trimmed = line.trimStart()
                val isSubTask = line.startsWith("  ") || line.startsWith("\t")
                val content = trimmed.removePrefix("☑").removePrefix("☐").trim()
                val isChecked = trimmed.startsWith("☑")
                TodoLine(
                    text = content,
                    isChecked = isChecked,
                    isSubTask = isSubTask,
                    order = index
                )
            }
        }

        /** 从 SubTask 列表转换为 TodoLine 列表 */
        fun fromSubTasks(subTasks: List<com.corgimemo.app.data.model.SubTask>): List<TodoLine> {
            return subTasks.map { subTask ->
                TodoLine(
                    text = subTask.title,
                    isChecked = subTask.isCompleted,
                    isSubTask = true,
                    subTaskId = subTask.id,
                    order = subTask.order
                )
            }
        }
    }
}

/**
 * 语音附件数据类
 *
 * 存储语音文件的路径和时长信息，
 * 与 TodoLine 关联，支持每行独立管理语音附件。
 *
 * @param path 语音文件本地存储路径
 * @param duration 语音时长（单位：秒），null 表示未知时长
 */
data class VoiceAttachment(
    val path: String,
    val duration: Int? = null
)
