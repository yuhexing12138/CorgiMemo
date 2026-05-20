package com.corgimemo.app.backup

import android.content.Context
import android.net.Uri
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * 备份历史管理器
 * 统一管理备份记录的增删改查
 */
object BackupHistoryManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 获取所有备份记录（按时间倒序）
     *
     * @param context 上下文
     * @return 备份记录列表
     */
    suspend fun getRecords(context: Context): List<BackupRecord> {
        val preferences = CorgiPreferences.getInstance(context)
        val historyJson = preferences.getBackupHistory()

        return if (historyJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val records = json.decodeFromString<List<BackupRecord>>(historyJson)
                records.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 添加新备份记录
     *
     * @param context 上下文
     * @param fileUri 文件 URI
     * @param fileSizeBytes 文件大小（字节）
     * @param todoCount 待办数量
     * @param categoryCount 分类数量
     * @param isAutoBackup 是否自动备份
     * @return 新增的记录
     */
    suspend fun addRecord(
        context: Context,
        fileUri: String,
        fileSizeBytes: Long,
        todoCount: Int,
        categoryCount: Int,
        isAutoBackup: Boolean = false
    ): BackupRecord {
        val record = BackupRecord(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            fileSizeBytes = fileSizeBytes,
            locationType = "local",
            fileUri = fileUri,
            todoCount = todoCount,
            categoryCount = categoryCount,
            isAutoBackup = isAutoBackup
        )

        val currentRecords = getRecords(context)
        val updatedRecords = listOf(record) + currentRecords

        saveRecords(context, updatedRecords)

        return record
    }

    /**
     * 删除指定备份记录
     *
     * @param context 上下文
     * @param recordId 记录 ID
     * @return 是否删除成功
     */
    suspend fun deleteRecord(context: Context, recordId: String): Boolean {
        val records = getRecords(context)
        val recordToDelete = records.find { it.id == recordId } ?: return false

        try {
            deleteFile(context, recordToDelete.fileUri)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val updatedRecords = records.filter { it.id != recordId }
        saveRecords(context, updatedRecords)

        return true
    }

    /**
     * 删除指定 URI 的文件
     *
     * @param context 上下文
     * @param fileUri 文件 URI
     */
    private fun deleteFile(context: Context, fileUri: String) {
        try {
            val uri = Uri.parse(fileUri)
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理超过保留数量的旧备份
     *
     * @param context 上下文
     * @param retainCount 保留数量
     */
    suspend fun cleanupOldBackups(context: Context, retainCount: Int) {
        val records = getRecords(context)

        if (records.size <= retainCount) return

        val recordsToDelete = records.drop(retainCount)

        recordsToDelete.forEach { record ->
            try {
                deleteFile(context, record.fileUri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val updatedRecords = records.take(retainCount)
        saveRecords(context, updatedRecords)
    }

    /**
     * 保存记录列表
     *
     * @param context 上下文
     * @param records 记录列表
     */
    private suspend fun saveRecords(context: Context, records: List<BackupRecord>) {
        val preferences = CorgiPreferences.getInstance(context)
        val jsonString = json.encodeToString(records)
        preferences.saveBackupHistory(jsonString)
    }

    /**
     * 获取指定数量的最近备份记录
     *
     * @param context 上下文
     * @param limit 限制数量
     * @return 备份记录列表
     */
    suspend fun getRecentRecords(context: Context, limit: Int): List<BackupRecord> {
        return getRecords(context).take(limit)
    }

    /**
     * 清除所有备份记录（不删除文件）
     *
     * @param context 上下文
     */
    suspend fun clearAllRecords(context: Context) {
        val preferences = CorgiPreferences.getInstance(context)
        preferences.clearBackupHistory()
    }

    /**
     * 获取备份记录数量
     *
     * @param context 上下文
     * @return 记录数量
     */
    suspend fun getRecordCount(context: Context): Int {
        return getRecords(context).size
    }
}
