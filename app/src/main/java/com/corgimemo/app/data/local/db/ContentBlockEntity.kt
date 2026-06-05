package com.corgimemo.app.data.local.db

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
 * - 独立表结构，支持无限扩展（未来可加视频、文件等类型）
 * - orderIndex 字段记录在内容流中的排序位置
 * - 通过 todoId 外键关联到所属待办事项
 */
@Entity(
    tableName = "content_blocks",
    indices = [Index(value = ["todoId"])]
)
data class ContentBlockEntity(
    /** 主键，自增 */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 关联的待办事项 ID */
    val todoId: Long,
    /** 内容块类型: "image" | "voice" */
    val type: String,
    /** 文件存储路径（绝对路径） */
    val filePath: String,
    /** 语音时长（秒），仅 type="voice" 时有效 */
    val duration: Int? = null,
    /** 排序索引，决定在内容流中的显示顺序 */
    val orderIndex: Int = 0
)
