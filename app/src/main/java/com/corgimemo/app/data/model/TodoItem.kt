package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["categoryId", "status"]),
        Index(value = ["priority", "startDate"]),
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
    val startDate: Long? = null,
    val estimatedDurationMinutes: Int? = null,
    val reminderTime: Long? = null,
    val repeatType: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadius: Float? = null,
    val geofenceType: Int = 0,
    val geofenceEnabled: Boolean = false,
    val geofenceAddress: String? = null,
    val hasSubTasks: Boolean = false
)
