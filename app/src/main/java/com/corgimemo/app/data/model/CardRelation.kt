package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 统一卡片关联关系实体类
 * 用于存储所有类型卡片（待办/灵感/日期）之间的单向关联关系
 * 替代原有的 InspirationRelation 和 SpecialDateRelation 分散设计
 */
@Entity(
    tableName = "card_relations",
    indices = [
        // 修改：source 索引加上 groupId，支持按分组查询
        Index(value = ["sourceType", "sourceId", "groupId"]),
        Index(value = ["targetType", "targetId"]),
        // 修改：唯一约束加上 groupId，同一分组内不能重复关联
        Index(value = ["sourceType", "sourceId", "groupId", "targetType", "targetId"], unique = true)
    ]
)
data class CardRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联发起方类型: "todo" | "inspiration" | "date" */
    val sourceType: String,

    /** 关联发起方ID */
    val sourceId: Long,

    /**
     * 关联发起方分组ID（仅 todo 类型有意义；inspiration/date 默认 0）
     * - 多分组架构下，每个分组独立维护自己的关联列表
     * - 旧数据迁移后默认为 0（主分组）
     */
    @ColumnInfo(defaultValue = "0")
    val groupId: Int = 0,

    /** 关联目标方类型: "todo" | "inspiration" | "date" */
    val targetType: String,

    /** 关联目标方ID */
    val targetId: Long,

    /** 创建时间戳 */
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
