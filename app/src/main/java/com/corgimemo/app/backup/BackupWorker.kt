package com.corgimemo.app.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.notification.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自动备份 Worker
 * 使用 WorkManager 定期执行备份任务
 */
class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = CorgiPreferences.getInstance(context)

        val enabled = preferences.getAutoBackupEnabled()
        if (!enabled) {
            return Result.success()
        }

        return try {
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "CorgiMemo_backup_${dateFormat.format(Date(timestamp))}.json"

                val fileUri = createFileInDownloads(context, fileName)

                if (fileUri == null) {
                    NotificationHelper.showBackupNotification(
                        context = context,
                        success = false,
                        todoCount = 0,
                        errorMessage = "无法创建备份文件"
                    )
                    return@withContext Result.failure()
                }

                val outputStream: OutputStream? = context.contentResolver.openOutputStream(fileUri)
                if (outputStream == null) {
                    NotificationHelper.showBackupNotification(
                        context = context,
                        success = false,
                        todoCount = 0,
                        errorMessage = "无法写入备份文件"
                    )
                    return@withContext Result.failure()
                }

                val result = BackupManager.exportData(
                    context = context,
                    uri = fileUri,
                    format = BackupManager.ExportFormat.JSON
                )

                when (result) {
                    is BackupManager.ExportResult.Success -> {
                        val fileSize = getFileSize(context, fileUri)
                        val todoCount = getTodoCount(context)
                        val categoryCount = getCategoryCount(context)

                        BackupHistoryManager.addRecord(
                            context = context,
                            fileUri = fileUri.toString(),
                            fileSizeBytes = fileSize,
                            todoCount = todoCount,
                            categoryCount = categoryCount,
                            isAutoBackup = true
                        )

                        preferences.updateAutoBackupLastTime()

                        val retainCount = preferences.getAutoBackupKeepCount()
                        BackupHistoryManager.cleanupOldBackups(context, retainCount)

                        NotificationHelper.showBackupNotification(
                            context = context,
                            success = true,
                            todoCount = todoCount,
                            errorMessage = null
                        )

                        Result.success()
                    }
                    is BackupManager.ExportResult.Error -> {
                        try {
                            context.contentResolver.delete(fileUri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        NotificationHelper.showBackupNotification(
                            context = context,
                            success = false,
                            todoCount = 0,
                            errorMessage = result.message
                        )
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            NotificationHelper.showBackupNotification(
                context = context,
                success = false,
                todoCount = 0,
                errorMessage = e.message ?: "未知错误"
            )
            Result.retry()
        }
    }

    /**
     * 在 Downloads 目录创建文件
     */
    private fun createFileInDownloads(context: Context, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            context.contentResolver.insert(
                MediaStore.Files.getContentUri("external"),
                contentValues
            )
        } else {
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val file = java.io.File(directory, fileName)
            Uri.fromFile(file)
        }
    }

    private fun getFileSize(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
                } else 0L
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private suspend fun getTodoCount(context: Context): Int {
        return try {
            val database = com.corgimemo.app.data.local.db.CorgiMemoDatabase.getDatabase(context)
            database.todoDao().getAllTodos().first().size
        } catch (e: Exception) {
            0
        }
    }

    private suspend fun getCategoryCount(context: Context): Int {
        return try {
            val database = com.corgimemo.app.data.local.db.CorgiMemoDatabase.getDatabase(context)
            database.categoryDao().getAllCategories().first().size
        } catch (e: Exception) {
            0
        }
    }
}
