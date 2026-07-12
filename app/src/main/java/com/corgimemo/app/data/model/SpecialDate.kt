package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 特殊日期记录实体类
 * 用于存储生日、纪念日、节日等重要日期
 */
@Entity(
    tableName = "special_dates",
    indices = [
        Index(value = ["targetDate"]),
        Index(value = ["isPinned"]),
        Index(value = ["category"])
    ]
)
data class SpecialDate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 标题（必填） */
    val title: String,

    /** 目标日期时间戳(毫秒)，存原始设定日期 */
    val targetDate: Long,

    /** 分类: BIRTHDAY / ANNIVERSARY / HOLIDAY / OTHER */
    @ColumnInfo(defaultValue = "OTHER")
    val category: String = "OTHER",

    /** 计时模式: 0=倒计时, 1=正计时 */
    @ColumnInfo(defaultValue = "0")
    val countMode: Int = 0,

    /** 重复类型: 0=不重复, 1=按年, 2=按月 */
    @ColumnInfo(defaultValue = "0")
    val repeatType: Int = 0,

    /** 提前N天提醒，0=不提醒 */
    @ColumnInfo(defaultValue = "0")
    val reminderDays: Int = 0,

    /** 备注内容（多行文本） */
    @ColumnInfo(defaultValue = "")
    val content: String = "",

    /** 标签 JSON数组: ["重要","家庭"] */
    @ColumnInfo(defaultValue = "")
    val tags: String = "",

    /** 本地图片路径 JSON数组 */
    @ColumnInfo(defaultValue = "")
    val imagePaths: String = "",

    /** 云端图片URL JSON数组（预留） */
    @ColumnInfo(defaultValue = "")
    val imageUrls: String = "",

    /** 是否置顶 */
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,

    /**
     * 是否已归档（软删除）
     * - true: 不在主页列表显示，可通过"已归档"入口（未来提供）查看与恢复
     * - false: 正常显示（默认）
     */
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,

    /** 创建时间戳(毫秒) */
    val createdAt: Long,

    /** 更新时间戳(毫秒) */
    val updatedAt: Long
)
