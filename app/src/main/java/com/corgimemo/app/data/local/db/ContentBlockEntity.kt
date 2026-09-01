package com.corgimemo.app.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 内容块实体类
 *
 * 用于持久化存储待办事项中的混合内容（图片、语音等），
 * 支持独立于 todo_items 表的灵活内容管理。
 *
 * **设计要点**:
 * - 独立表结构，支持无限扩展（未来可加视频、文件等）
 * - orderIndex 字段记录在内容流中的排序位置
 * - 通过 todoId 外键关联到所属待办事项
 *
 * **v2026-07-25 重构（三写存储 → 单一数据源）**:
 * - 新增 [subTaskId] 字段：区分父待办附件（null）和子任务附件（子任务 ID）
 * - 新增 [lineIndex] 字段：记录附件所属行号（多行场景下行级精确恢复）
 * - content_blocks 表现作为附件的**唯一权威源**，
 *   TodoItem.imagePaths/voiceNotePath 和 SubTask.imagePaths/voicePaths 字段保留但不再写入（置空）
 * - contentFormat 中的行级附件快照（|||LINE_ATTACHMENTS|||）不再写入数据库
 *
 * **v2026-07-25 ownerType 字段（解决灵感 ID 污染待办查询）**:
 * - 新增 [ownerType] 字段：区分 "todo"（待办附件）和 "inspiration"（灵感附件）
 * - 原因：content_blocks 表用 todoId 字段统一存储待办和灵感附件，
 *   但灵感 ID 和待办 ID 都从 1 开始自增，查询 todoId=1 时会把灵感图片也查出来
 * - 解决：所有查询加 ownerType 过滤，待办查询用 "todo"，灵感查询用 "inspiration"
 */
@Entity(
    tableName = "content_blocks",
    indices = [Index(value = ["todoId"]), Index(value = ["subTaskId"]), Index(value = ["lineIndex"]), Index(value = ["ownerType"])]
)
data class ContentBlockEntity(
    /** 主键，自增 */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联的待办事项 ID（父待办 ID，即使附件属于子任务也存父待办 ID，便于批量查询） */
    val todoId: Long,
    /**
     * 所有者类型（v2026-07-25 新增）
     *
     * - "todo"：待办附件（默认值，兼容旧数据）
     * - "inspiration"：灵感附件
     *
     * 用于区分待办和灵感的附件，避免 ID 冲突导致查询污染
     */
    @ColumnInfo(defaultValue = "todo") val ownerType: String = "todo",
    /** 内容块类型: "image" | "voice" */
    val type: String,
    /** 文件存储路径（绝对路径） */
    val filePath: String,
    /** 语音时长（秒），仅 type="voice" 时有效 */
    val duration: Int? = null,
    /** 排序索引，决定在内容流中的显示顺序（@ColumnInfo defaultValue 与 Migration DEFAULT 0 保持一致） */
    @ColumnInfo(defaultValue = "0") val orderIndex: Int = 0,
    /**
     * 子任务 ID（v2026-07-25 新增）
     *
     * - null：附件属于父待办本身
     * - 非 null：附件属于指定子任务（值为 SubTask.id）
     *
     * 用于彻底重构后区分父待办和子任务的附件归属，
     * 替代之前用父待办 ID 混存的方式。
     */
    val subTaskId: Long? = null,
    /**
     * 行号索引（v2026-07-25 新增）
     *
     * 记录附件所属的行号（从 0 开始），用于多行场景下精确恢复附件到对应行。
     * 替代之前 contentFormat 中的行级附件快照（|||LINE_ATTACHMENTS|||）。
     *
     * - 0：第一行（通常是父待办标题行）
     * - 1, 2, ...：后续子任务行
     *
     * @ColumnInfo defaultValue 与 Migration DEFAULT 0 保持一致（项目规则：entity与migration同步检查）
     */
    @ColumnInfo(defaultValue = "0") val lineIndex: Int = 0,
    /**
     * 块稳定标识（v2026-09-01 路线 4 预留，MIGRATION_56_57）
     *
     * 编辑器块级模型中每块的唯一 id（UUID），用于跨编辑会话恢复
     * RichTextState 映射与重组 key。旧行为 null（未分配）。
     */
    val blockId: String? = null,
    /**
     * 文本块内容（v2026-09-01 路线 4 预留，MIGRATION_56_57）
     *
     * 当 type 扩展为 "text" 时存放该段落的 markdown；当前正文仍以
     * Inspiration.contentFormat 为单一真相源，此字段暂不写入。
     */
    val textContent: String? = null,
    /**
     * 图片备注（v2026-09-01 后续需求预留，MIGRATION_56_57）
     *
     * 图片的图注/说明文字。本期不写入，块级图片卡片实现时启用。
     */
    val note: String? = null,
    /**
     * 裁剪矩形（v2026-09-01 后续需求预留，MIGRATION_56_57）
     *
     * 格式 "left,top,right,bottom"（相对 originalPath 原图像素坐标）。
     * 裁剪不覆盖原图：originalPath 存原图，filePath 存裁剪结果（或运行时裁剪）。
     * 本期不写入。
     */
    val cropRect: String? = null,
    /**
     * 原图路径（v2026-09-01 后续需求预留，MIGRATION_56_57）
     *
     * 裁剪前的原始文件路径；未裁剪时为 null 或与 filePath 相同。
     * 保证"重新裁剪"可还原。本期不写入。
     */
    val originalPath: String? = null,
    /**
     * 显示宽度比例（v2026-09-01 后续需求预留，MIGRATION_56_57）
     *
     * 相对可用宽度的缩放比例（0.1 ~ 1.0），1.0 = 全宽。本期不写入。
     */
    val displayWidthRatio: Float? = null
)
