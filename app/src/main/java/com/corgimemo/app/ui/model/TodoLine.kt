package com.corgimemo.app.ui.model

import java.util.concurrent.atomic.AtomicLong

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
 *
 * 🆕 v2026-07-25 架构根治：stableId 字段
 *
 * [stableId] 是一个跨重组稳定的唯一标识符，主要用于：
 * - Compose 重组时保持行身份不变（避免 copy() 后引用失效）
 * - onFocusChange 反查行索引时替代 line 引用比较（===）
 * - FocusRequester Map 的 key（替代易过期的 Int 行索引）
 *
 * 生命周期：
 * - 新建行时由 [generateStableId] 分配（AtomicLong 自增）
 * - copy() 时默认保留 stableId（关键：这是 stableId 的核心价值）
 * - 不持久化到 DB（重启后会重新分配，但不影响会话内稳定性）
 * - 从 DB 加载时基于 subTaskId 或 hash 重新分配 stableId
 *
 * 注意：stableId 不保证全局唯一，仅保证同一会话内唯一。
 */
data class TodoLine(
    /**
     * 行身份稳定 ID（v2026-07-25 新增）
     *
     * - 跨 Compose 重组保持不变（copy 时保留）
     * - 用于 FocusRequester Map 的 key 和 onFocusChange 反查
     * - 默认 0L 表示"尚未分配"，使用前应通过 [generateStableId] 分配
     */
    val stableId: Long = 0L,
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
        /**
         * 全局自增 stableId 生成器（AtomicLong 保证线程安全）
         *
         * - 初值从 1 开始（0 保留为"未分配"哨兵值）
         * - 跨整个 App 生命周期单调递增
         * - 即使行被删除，已分配的 ID 不会被复用
         */
        private val stableIdCounter = AtomicLong(1)

        /**
         * 生成一个新的全局唯一 stableId
         *
         * 使用场景：
         * - 新建 TodoLine 时（用户回车新建行、"/"新建容器、新建待办）
         * - 从 DB 加载 TodoLine 时（基于 subTaskId 或 hash 派生）
         *
         * @return 新的 stableId（>= 1）
         */
        fun generateStableId(): Long = stableIdCounter.getAndIncrement()

        /**
         * 基于 subTaskId 派生 stableId（用于从 DB 加载时稳定身份）
         *
         * - subTaskId > 0：使用 subTaskId 直接作为 stableId（同一 subTask 跨会话稳定）
         * - subTaskId == 0：调用 [generateStableId] 分配新值
         *
         * 注意：subTaskId 全局唯一且持久化，作为 stableId 时跨会话也稳定，
         * 这是它比 [generateStableId] 更优的地方。
         */
        fun stableIdFromSubTaskId(subTaskId: Long): Long {
            return if (subTaskId > 0) subTaskId else generateStableId()
        }

        /** 从纯文本解析为 TodoLine 列表 */
        fun parseFromText(text: String): List<TodoLine> {
            if (text.isBlank()) return listOf(TodoLine(stableId = generateStableId()))
            return text.lines().mapIndexed { index, line ->
                // 解析缩进（前导空格判断是否为子任务）
                val trimmed = line.trimStart()
                val isSubTask = line.startsWith("  ") || line.startsWith("\t")
                val content = trimmed.removePrefix("☑").removePrefix("☐").trim()
                val isChecked = trimmed.startsWith("☑")
                TodoLine(
                    stableId = generateStableId(),
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
                    // 从 DB 加载的 SubTask：用 subTaskId 作为 stableId，跨会话稳定
                    stableId = stableIdFromSubTaskId(subTask.id),
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
