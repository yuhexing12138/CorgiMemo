package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 灵感记录实体类
 * 用于存储用户的灵感、想法、笔记等内容
 */
@Entity(
    tableName = "inspirations",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isPinned"])
    ]
)
data class Inspiration(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 标题（必填） */
    val title: String,

    /** 富文本内容 (HTML格式) */
    @ColumnInfo(defaultValue = "")
    val content: String = "",

    /** 标签 JSON数组: ["产品","设计"] */
    @ColumnInfo(defaultValue = "")
    val tags: String = "",

    /** 本地图片路径 JSON数组 */
    @ColumnInfo(defaultValue = "")
    val imagePaths: String = "",

    /** 云端图片URL JSON数组（预留） */
    @ColumnInfo(defaultValue = "")
    val imageUrls: String = "",

    /** 创建时间戳(毫秒) */
    val createdAt: Long,

    /** 更新时间戳(毫秒) */
    val updatedAt: Long,

    /** 是否置顶 */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,

    /** 是否归档 */
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false
)
