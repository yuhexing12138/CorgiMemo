package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 灵感关联关系实体类
 * 用于存储灵感与其他卡片（待办/日期/其他灵感）的关联关系
 */
@Entity(
    tableName = "inspiration_relations",
    indices = [Index(value = ["inspirationId"])],
    foreignKeys = [
        ForeignKey(
            entity = Inspiration::class,
            parentColumns = ["id"],
            childColumns = ["inspirationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InspirationRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /** 所属灵感ID */
    val inspirationId: Long,
    
    /** 目标类型: "todo" | "date" | "inspiration" */
    val targetType: String,
    
    /** 目标实体ID */
    val targetId: Long,
    
    /** 创建时间戳 */
    val createdAt: Long = System.currentTimeMillis()
)
