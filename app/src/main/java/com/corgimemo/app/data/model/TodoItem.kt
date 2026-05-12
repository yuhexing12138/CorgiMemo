package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["categoryId", "status"]),
        Index(value = ["priority", "dueDate"])
    ]
)
data class TodoItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String? = null,
    val categoryId: Long,
    val priority: Int,
    val status: Int,
    val dueDate: Long? = null,
    val reminderTime: Long? = null,
    val repeatType: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)
