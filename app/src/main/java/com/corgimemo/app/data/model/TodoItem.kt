package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["categoryId", "status"]),
        Index(value = ["priority", "dueDate"]),
        Index(value = ["hasSubTasks"])
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
    val completedAt: Long? = null,
    // 地理围栏相关字段
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadius: Float? = null,
    val geofenceType: Int = 0,
    val geofenceEnabled: Boolean = false,
    val geofenceAddress: String? = null,
    // 是否有子任务（用于列表快速判断是否显示进度条）
    val hasSubTasks: Boolean = false
)
