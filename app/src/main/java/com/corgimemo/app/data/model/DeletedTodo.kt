package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_todos")
data class DeletedTodo(
    @PrimaryKey
    val id: Long,
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
    val hasSubTasks: Boolean = false,
    val voiceNotePath: String? = null,
    val voiceDuration: Int? = null,
    val deletedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromTodoItem(todo: TodoItem): DeletedTodo {
            return DeletedTodo(
                id = todo.id,
                title = todo.title,
                content = todo.content,
                categoryId = todo.categoryId,
                priority = todo.priority,
                status = todo.status,
                startDate = todo.startDate,
                estimatedDurationMinutes = todo.estimatedDurationMinutes,
                reminderTime = todo.reminderTime,
                repeatType = todo.repeatType,
                createdAt = todo.createdAt,
                updatedAt = todo.updatedAt,
                completedAt = todo.completedAt,
                geofenceLat = todo.geofenceLat,
                geofenceLng = todo.geofenceLng,
                geofenceRadius = todo.geofenceRadius,
                geofenceType = todo.geofenceType,
                geofenceEnabled = todo.geofenceEnabled,
                geofenceAddress = todo.geofenceAddress,
                hasSubTasks = todo.hasSubTasks,
                voiceNotePath = todo.voiceNotePath,
                voiceDuration = todo.voiceDuration,
                deletedAt = System.currentTimeMillis()
            )
        }

        fun toTodoItem(deleted: DeletedTodo): TodoItem {
            return TodoItem(
                id = deleted.id,
                title = deleted.title,
                content = deleted.content,
                categoryId = deleted.categoryId,
                priority = deleted.priority,
                status = deleted.status,
                startDate = deleted.startDate,
                estimatedDurationMinutes = deleted.estimatedDurationMinutes,
                reminderTime = deleted.reminderTime,
                repeatType = deleted.repeatType,
                createdAt = deleted.createdAt,
                updatedAt = deleted.updatedAt,
                completedAt = deleted.completedAt,
                geofenceLat = deleted.geofenceLat,
                geofenceLng = deleted.geofenceLng,
                geofenceRadius = deleted.geofenceRadius,
                geofenceType = deleted.geofenceType,
                geofenceEnabled = deleted.geofenceEnabled,
                geofenceAddress = deleted.geofenceAddress,
                hasSubTasks = deleted.hasSubTasks,
                voiceNotePath = deleted.voiceNotePath,
                voiceDuration = deleted.voiceDuration
            )
        }
    }
}