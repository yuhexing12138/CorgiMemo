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
        Index(value = ["isPinned"]),
        Index(value = ["categoryId"]),
        Index(value = ["priority"]),
        Index(value = ["status", "createdAt"]),
        Index(value = ["dueDate", "status"]),
        // v2026-07-22 新增：title 索引
        // 注：SQLite LIKE '%x%' 实际无法走 B-Tree 索引，但为后续 FTS5 切换铺垫
        // 必须与 MIGRATION_44_TO_45 中创建的 index_inspirations_title 保持一致
        // （参见 .trae/rules/entity与migration同步检查.md）
        Index(value = ["title"])
    ]
)
data class Inspiration(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 标题（必填） */
    val title: String,

    /** 纯文本内容 */
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
    val isArchived: Boolean = false,

    // ========== 以下为从 TodoEditScreen 迁移的字段 (v27 新增) ==========

    /** 分类ID */
    @ColumnInfo(defaultValue = "0")
    val categoryId: Long = 0,

    /** 优先级: 0=低, 1=中, 2=高 */
    @ColumnInfo(defaultValue = "0")
    val priority: Int = 0,

    /** 状态: 0=进行中, 1=已完成, 2=归档 */
    @ColumnInfo(defaultValue = "0")
    val status: Int = 0,

    /** 开始时间（时间戳毫秒） */
    val startDate: Long? = null,

    /** 截止时间（时间戳毫秒） */
    val dueDate: Long? = null,

    /** 预估时长（分钟） */
    val estimatedDurationMinutes: Int? = null,

    /** 提醒时间（时间戳毫秒） */
    val reminderTime: Long? = null,

    /** 重复类型: 0=不重复, 1=每天, 2=每周, 3=每月, 4=每年 */
    @ColumnInfo(defaultValue = "0")
    val repeatType: Int = 0,

    /** 完成时间（时间戳毫秒） */
    val completedAt: Long? = null,

    // --- 地理围栏 (6 字段) ---
    /** 地理围栏纬度 */
    val geofenceLat: Double? = null,
    /** 地理围栏经度 */
    val geofenceLng: Double? = null,
    /** 地理围栏半径(米) */
    val geofenceRadius: Float? = null,
    /** 地理围栏类型: 0=到达提醒, 1=离开提醒 */
    val geofenceType: Int? = null,
    /** 地理围栏是否启用 */
    @ColumnInfo(defaultValue = "0")
    val geofenceEnabled: Boolean = false,
    /** 地理围栏地址描述 */
    val geofenceAddress: String? = null,

    // --- 子任务 / 语音 / 样式 ---
    /** 是否有子任务 */
    @ColumnInfo(defaultValue = "0")
    val hasSubTasks: Boolean = false,
    /** 语音备注文件路径 */
    val voiceNotePath: String? = null,
    /** 语音备注时长(秒) */
    val voiceDuration: Int? = null,

    /**
     * 背景颜色值（ARGB 整数）
     * 默认 -1 = 0xFFFFFFFF（白色/透明背景）
     */
    @ColumnInfo(defaultValue = "-1")
    val backgroundColor: Int = -1,

    /** 手动排序位置索引（从 0 开始，用于拖拽排序功能） */
    @ColumnInfo(defaultValue = "0")
    val position: Int = 0,

    /**
     * 富文本格式化内容（Markdown 格式）
     * 存储完整的 Markdown 格式文本，保留粗体/斜体/删除线/列表等格式信息
     */
    @ColumnInfo(defaultValue = "")
    val contentFormat: String = ""
)
