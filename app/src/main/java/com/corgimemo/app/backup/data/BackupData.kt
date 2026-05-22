package com.corgimemo.app.backup.data

import kotlinx.serialization.Serializable

/**
 * 备份数据容器
 * 包含所有需要备份的数据
 *
 * @property meta 备份元数据
 * @property data 具体备份数据
 */
@Serializable
data class BackupContainer(
    val meta: BackupMeta,
    val data: BackupData
)

/**
 * 备份数据
 * 包含所有需要备份的实体数据
 *
 * @property todos 待办列表
 * @property categories 分类列表
 * @property corgiData 柯基数据
 * @property moodHistory 心情历史
 * @property preferences 用户设置
 */
@Serializable
data class BackupData(
    val todos: List<BackupTodoItem> = emptyList(),
    val categories: List<BackupCategory> = emptyList(),
    val corgiData: BackupCorgiData? = null,
    val moodHistory: List<BackupMoodHistory> = emptyList(),
    val preferences: Map<String, String> = emptyMap()
)

/**
 * 可序列化的待办项
 */
@Serializable
data class BackupTodoItem(
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
    val geofenceAddress: String? = null
)

/**
 * 可序列化的分类
 */
@Serializable
data class BackupCategory(
    val id: Long = 0,
    val name: String,
    val type: Int,
    val isDefault: Boolean = false
)

/**
 * 可序列化的柯基数据
 */
@Serializable
data class BackupCorgiData(
    val id: Long = 0,
    val name: String,
    val level: Int = 1,
    val experience: Int = 0,
    val currentOutfit: String? = null,
    val unlockedOutfits: String = "[]",
    val unlockedAchievements: String = "[]",
    val moodValue: Int = 50,
    val lastActiveDate: String,
    val totalCompleted: Int = 0,
    val consecutiveDays: Int = 0,
    val maxConsecutiveDays: Int = 0
)

/**
 * 可序列化的心情历史
 */
@Serializable
data class BackupMoodHistory(
    val id: Long = 0,
    val date: String,
    val moodValue: Int,
    val changeReason: String? = null
)
