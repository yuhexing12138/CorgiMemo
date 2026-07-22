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
        Index(value = ["category"]),
        // isArchived 索引：主页查询常态按 isArchived=0 过滤未归档日期
        // 注意：必须与 MIGRATION_33_34 中创建的 index_special_dates_isArchived 保持一致
        // （参见 .trae/rules/entity与migration同步检查.md）
        Index(value = ["isArchived"]),
        // v2026-07-22 新增：title 索引
        // 注：SQLite LIKE '%x%' 实际无法走 B-Tree 索引，但为后续 FTS5 切换铺垫
        // 必须与 MIGRATION_44_TO_45 中创建的 index_special_dates_title 保持一致
        // （参见 .trae/rules/entity与migration同步检查.md）
        Index(value = ["title"])
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

    /**
     * 卡片样式(参考"日期卡片样式选择页")
     * - "ORANGE_TEAR_OFF": 橙色撕页样式(参考图 2)
     * - "CALENDAR_TEAR_OFF": 米色日历撕页样式(参考图 3)
     * - 默认 "ORANGE_TEAR_OFF",与 @ColumnInfo defaultValue 必须完全一致
     *
     * 注意:本次主页不读该字段,仅 Entity 持久化。后续任务(主页按样式渲染)会读取。
     */
    @ColumnInfo(defaultValue = "ORANGE_TEAR_OFF")
    val cardStyle: String = "ORANGE_TEAR_OFF",

    /**
     * 卡片颜色(参考"日期卡片颜色选择设计")
     * - "DEFAULT": 无颜色,使用样式原色
     * - "BLUE" / "SKY_BLUE" / "TEAL" / "GREEN" / "LIME" / "ORANGE":
     *   "RED" / "PINK" / "PURPLE" / "NAVY" / "BROWN" / "BLACK": 12 个单色
     * - "RAINBOW": 彩虹色占位(后续实现)
     * - 默认 "DEFAULT",与 @ColumnInfo defaultValue 必须完全一致
     *
     * 注意:本次主页不读该字段,仅 Entity 持久化。
     */
    @ColumnInfo(defaultValue = "DEFAULT")
    val cardColor: String = "DEFAULT",

    /** 创建时间戳(毫秒) */
    val createdAt: Long,

    /** 更新时间戳(毫秒) */
    val updatedAt: Long
)
