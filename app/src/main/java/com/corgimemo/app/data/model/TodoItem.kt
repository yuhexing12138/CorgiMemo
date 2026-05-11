package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

/**
 * 待办事项数据模型
 * 
 * 使用 Room 数据库注解定义实体
 * 
 * @param id 唯一标识（UUID）
 * @param title 待办标题
 * @param description 待办描述（可选）
 * @param isCompleted 是否已完成
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now()
)