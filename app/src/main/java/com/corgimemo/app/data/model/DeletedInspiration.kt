package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已删除灵感记录实体类
 * 用于存储被软删除的灵感数据，支持回收站恢复功能
 */
@Entity(tableName = "deleted_inspirations")
data class DeletedInspiration(
    @PrimaryKey
    val id: Long,
    val title: String,
    val content: String = "",
    val tags: String = "",
    val imagePaths: String = "",
    val imageUrls: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val categoryId: Long = 0,
    val priority: Int = 0,
    val status: Int = 0,
    val startDate: Long? = null,
    val dueDate: Long? = null,
    val estimatedDurationMinutes: Int? = null,
    val reminderTime: Long? = null,
    val repeatType: Int = 0,
    val completedAt: Long? = null,
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadius: Float? = null,
    val geofenceType: Int? = null,
    val geofenceEnabled: Boolean = false,
    val geofenceAddress: String? = null,
    val hasSubTasks: Boolean = false,
    val voiceNotePath: String? = null,
    val voiceDuration: Int? = null,
    val backgroundColor: Int = -1,
    val position: Int = 0,
    val contentFormat: String = "",
    val deletedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 从 Inspiration 转换为 DeletedInspiration，deletedAt 使用当前时间 */
        fun fromInspiration(inspiration: Inspiration): DeletedInspiration {
            return DeletedInspiration(
                id = inspiration.id,
                title = inspiration.title,
                content = inspiration.content,
                tags = inspiration.tags,
                imagePaths = inspiration.imagePaths,
                imageUrls = inspiration.imageUrls,
                createdAt = inspiration.createdAt,
                updatedAt = inspiration.updatedAt,
                isPinned = inspiration.isPinned,
                isArchived = inspiration.isArchived,
                categoryId = inspiration.categoryId,
                priority = inspiration.priority,
                status = inspiration.status,
                startDate = inspiration.startDate,
                dueDate = inspiration.dueDate,
                estimatedDurationMinutes = inspiration.estimatedDurationMinutes,
                reminderTime = inspiration.reminderTime,
                repeatType = inspiration.repeatType,
                completedAt = inspiration.completedAt,
                geofenceLat = inspiration.geofenceLat,
                geofenceLng = inspiration.geofenceLng,
                geofenceRadius = inspiration.geofenceRadius,
                geofenceType = inspiration.geofenceType,
                geofenceEnabled = inspiration.geofenceEnabled,
                geofenceAddress = inspiration.geofenceAddress,
                hasSubTasks = inspiration.hasSubTasks,
                voiceNotePath = inspiration.voiceNotePath,
                voiceDuration = inspiration.voiceDuration,
                backgroundColor = inspiration.backgroundColor,
                position = inspiration.position,
                contentFormat = inspiration.contentFormat,
                deletedAt = System.currentTimeMillis()
            )
        }

        /** 从 DeletedInspiration 转换回 Inspiration（用于恢复操作） */
        fun toInspiration(deleted: DeletedInspiration): Inspiration {
            return Inspiration(
                id = deleted.id,
                title = deleted.title,
                content = deleted.content,
                tags = deleted.tags,
                imagePaths = deleted.imagePaths,
                imageUrls = deleted.imageUrls,
                createdAt = deleted.createdAt,
                updatedAt = deleted.updatedAt,
                isPinned = deleted.isPinned,
                isArchived = deleted.isArchived,
                categoryId = deleted.categoryId,
                priority = deleted.priority,
                status = deleted.status,
                startDate = deleted.startDate,
                dueDate = deleted.dueDate,
                estimatedDurationMinutes = deleted.estimatedDurationMinutes,
                reminderTime = deleted.reminderTime,
                repeatType = deleted.repeatType,
                completedAt = deleted.completedAt,
                geofenceLat = deleted.geofenceLat,
                geofenceLng = deleted.geofenceLng,
                geofenceRadius = deleted.geofenceRadius,
                geofenceType = deleted.geofenceType,
                geofenceEnabled = deleted.geofenceEnabled,
                geofenceAddress = deleted.geofenceAddress,
                hasSubTasks = deleted.hasSubTasks,
                voiceNotePath = deleted.voiceNotePath,
                voiceDuration = deleted.voiceDuration,
                backgroundColor = deleted.backgroundColor,
                position = deleted.position,
                contentFormat = deleted.contentFormat
            )
        }
    }
}
