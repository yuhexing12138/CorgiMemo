package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已删除特殊日期实体（回收站）
 *
 * 镜像 DeletedTodo / DeletedInspiration 的设计：
 * - 字段与 SpecialDate 完全一致 + deletedAt 记录删除时间
 * - fromSpecialDate() 拷贝所有字段并设置 deletedAt
 * - toSpecialDate() 恢复时重置 isPinned=false, isArchived=false
 */
@Entity(tableName = "deleted_special_dates")
data class DeletedSpecialDate(
    @PrimaryKey
    val id: Long,
    val title: String,
    @ColumnInfo(defaultValue = "OTHER")
    val category: String = "OTHER",
    @ColumnInfo(defaultValue = "0")
    val countMode: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val repeatType: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val reminderDays: Int = 0,
    @ColumnInfo(defaultValue = "")
    val content: String = "",
    @ColumnInfo(defaultValue = "")
    val tags: String = "",
    @ColumnInfo(defaultValue = "")
    val imagePaths: String = "",
    @ColumnInfo(defaultValue = "")
    val imageUrls: String = "",
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "ORANGE_TEAR_OFF")
    val cardStyle: String = "ORANGE_TEAR_OFF",
    @ColumnInfo(defaultValue = "DEFAULT")
    val cardColor: String = "DEFAULT",
    val targetDate: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * 从 SpecialDate 转换为 DeletedSpecialDate
         * 保留所有字段，新增 deletedAt 时间戳
         */
        fun fromSpecialDate(date: SpecialDate): DeletedSpecialDate {
            return DeletedSpecialDate(
                id = date.id,
                title = date.title,
                category = date.category,
                countMode = date.countMode,
                repeatType = date.repeatType,
                reminderDays = date.reminderDays,
                content = date.content,
                tags = date.tags,
                imagePaths = date.imagePaths,
                imageUrls = date.imageUrls,
                isPinned = date.isPinned,
                isArchived = date.isArchived,
                cardStyle = date.cardStyle,
                cardColor = date.cardColor,
                targetDate = date.targetDate,
                createdAt = date.createdAt,
                updatedAt = date.updatedAt,
                deletedAt = System.currentTimeMillis()
            )
        }

        /**
         * 从 DeletedSpecialDate 转换回 SpecialDate
         * 恢复时重置 isPinned=false, isArchived=false（避免恢复后立即置顶/归档）
         */
        fun toSpecialDate(deleted: DeletedSpecialDate): SpecialDate {
            return SpecialDate(
                id = deleted.id,
                title = deleted.title,
                category = deleted.category,
                countMode = deleted.countMode,
                repeatType = deleted.repeatType,
                reminderDays = deleted.reminderDays,
                content = deleted.content,
                tags = deleted.tags,
                imagePaths = deleted.imagePaths,
                imageUrls = deleted.imageUrls,
                isPinned = false,
                isArchived = false,
                cardStyle = deleted.cardStyle,
                cardColor = deleted.cardColor,
                targetDate = deleted.targetDate,
                createdAt = deleted.createdAt,
                updatedAt = deleted.updatedAt
            )
        }
    }
}
