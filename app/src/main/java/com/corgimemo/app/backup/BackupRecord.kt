package com.corgimemo.app.backup

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份记录数据类
 *
 * @property id 唯一 ID（UUID）
 * @property timestamp 备份时间戳
 * @property fileSizeBytes 文件大小（字节）
 * @property locationType 位置类型（local）
 * @property fileUri 文件 URI
 * @property todoCount 备份的待办数量
 * @property categoryCount 备份的分类数量
 * @property isAutoBackup 是否自动备份
 */
@Serializable
data class BackupRecord(
    val id: String,
    val timestamp: Long,
    val fileSizeBytes: Long,
    val locationType: String,
    val fileUri: String,
    val todoCount: Int,
    val categoryCount: Int,
    val isAutoBackup: Boolean
) {
    /**
     * 格式化的备份时间
     */
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

    /**
     * 格式化的文件大小
     */
    val formattedSize: String
        get() = formatFileSize(fileSizeBytes)

    /**
     * 格式化文件大小
     *
     * @param bytes 字节数
     * @return 格式化后的大小字符串
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    /**
     * 是否是今天的备份
     */
    val isToday: Boolean
        get() {
            val cal = java.util.Calendar.getInstance()
            val todayStart = cal.apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis

            val tomorrowStart = cal.apply {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }.timeInMillis

            return timestamp in todayStart until tomorrowStart
        }

    /**
     * 获取备份类型显示名称
     */
    val typeName: String
        get() = if (isAutoBackup) "自动备份" else "手动备份"
}
