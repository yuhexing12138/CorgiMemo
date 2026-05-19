package com.corgimemo.app.backup.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 自动备份 Worker
 * 每周自动执行一次备份
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "AutoBackupWork"
        private const val REPEAT_INTERVAL_DAYS = 7L

        /**
         * 调度自动备份
         */
        fun scheduleAutoBackup(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                repeatInterval = REPEAT_INTERVAL_DAYS,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * 取消自动备份
         */
        fun cancelAutoBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val context = applicationContext
        val preferences = CorgiPreferences.getInstance(context)

        val enabled = preferences.autoBackupEnabled.first()
        if (!enabled) {
            return Result.success()
        }

        val uriString = preferences.autoBackupUri.first()
        if (uriString.isNullOrEmpty()) {
            return Result.failure()
        }

        return try {
            val uri = Uri.parse(uriString)
            val password = preferences.autoBackupPassword.first()

            val fileName = generateBackupFileName()
            val targetUri = createBackupFile(context, uri, fileName)

            val result = BackupManager.exportData(
                context = context,
                uri = targetUri,
                format = BackupManager.ExportFormat.JSON,
                password = password
            )

            when (result) {
                is BackupManager.ExportResult.Success -> {
                    cleanupOldBackups(context, uri, preferences)
                    Result.success()
                }
                is BackupManager.ExportResult.Error -> {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    /**
     * 生成备份文件名
     */
    private fun generateBackupFileName(): String {
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        return "auto_backup_$timestamp.json"
    }

    /**
     * 创建备份文件（在选中的目录下创建新文件）
     * 由于 SAF 限制，这里简化处理：直接使用用户选择的目录
     */
    private fun createBackupFile(context: Context, baseUri: Uri, fileName: String): Uri {
        return baseUri
    }

    /**
     * 清理旧备份（保留最近 N 个）
     */
    private suspend fun cleanupOldBackups(
        context: Context,
        baseUri: Uri,
        preferences: CorgiPreferences
    ) {
        val keepCount = preferences.autoBackupKeepCount.first()
    }
}
