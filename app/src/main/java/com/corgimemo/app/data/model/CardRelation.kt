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
        Index(value = ["sourceType", "sourceId"]),
        Index(value = ["targetType", "targetId"]),
        Index(value = ["sourceType", "sourceId", "targetType", "targetId"], unique = true)
    ]
)
data class CardRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 关联发起方类型: "todo" | "inspiration" | "date" */
    val sourceType: String,

    /** 关联发起方ID */
    val sourceId: Long,

    /** 关联目标方类型: "todo" | "inspiration" | "date" */
    val targetType: String,

    /** 关联目标方ID */
    val targetId: Long,

    /** 创建时间戳 */
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
)
