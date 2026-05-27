package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 特殊日期关联关系实体类
 * 用于存储特殊日期与其他卡片（待办/日期/其他特殊日期/灵感）的关联关系
 */
@Entity(
    tableName = "special_date_relations",
    indices = [Index(value = ["specialDateId"])],
    foreignKeys = [
        ForeignKey(
            entity = SpecialDate::class,
            parentColumns = ["id"],
            childColumns = ["specialDateId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SpecialDateRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 所属特殊日期ID */
    val specialDateId: Long,

    /** 目标类型: "todo" | "date" | "inspiration" */
    val targetType: String,

    /** 目标实体ID */
    val targetId: Long,

    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
