package com.corgimemo.app.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 备份文件选择器
 * 管理 SAF 文件选择和备份操作
 */
object BackupFileHandler {

    /**
     * 导出数据的回调类型
     */
    data class ExportResult(
        val format: BackupManager.ExportFormat,
        val uri: Uri
    )

    private var pendingExportFormat: BackupManager.ExportFormat? = null

    /**
     * 生成导出文件名
     */
    fun generateExportFileName(format: BackupManager.ExportFormat): String {
        val timestamp = java.text.SimpleDateFormat(
            "yyyyMMdd_HHmmss",
            java.util.Locale.getDefault()
        ).format(java.util.Date())
        val extension = when (format) {
            BackupManager.ExportFormat.JSON -> "json"
            BackupManager.ExportFormat.CSV -> "csv"
        }
        return "corgimemo_backup_$timestamp.$extension"
    }

    /**
     * 获取文件选择 Intent
     */
    fun getExportIntent(format: BackupManager.ExportFormat): Intent {
        val fileName = generateExportFileName(format)
        val mimeType = when (format) {
            BackupManager.ExportFormat.JSON -> "application/json"
            BackupManager.ExportFormat.CSV -> "text/csv"
        }
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    /**
     * 获取导入文件 Intent
     */
    fun getImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf("application/json", "text/json", "application/octet-stream")
            )
        }
    }

    /**
     * 存储待导出的格式
     */
    fun setPendingExportFormat(format: BackupManager.ExportFormat) {
        pendingExportFormat = format
    }

    /**
     * 获取并清除待导出的格式
     */
    fun getAndClearPendingExportFormat(): BackupManager.ExportFormat? {
        val format = pendingExportFormat
        pendingExportFormat = null
        return format
    }
}
