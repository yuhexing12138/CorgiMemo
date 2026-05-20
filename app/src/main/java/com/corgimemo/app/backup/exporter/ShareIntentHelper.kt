package com.corgimemo.app.backup.exporter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * 分享 Intent 构建器
 * 用于创建分享图片和文件的 Intent
 */
object ShareIntentHelper {

    /**
     * 创建分享图片的 Intent
     *
     * @param context 上下文
     * @param imageFile 图片文件
     * @param text 附带的文本（可选）
     * @return 分享 Intent
     */
    fun createShareImageIntent(
        context: Context,
        imageFile: File,
        text: String? = null
    ): Intent {
        val imageUri = getContentUri(context, imageFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            text?.let {
                putExtra(Intent.EXTRA_TEXT, it)
            }
        }

        return Intent.createChooser(shareIntent, "分享待办卡片")
    }

    /**
     * 创建分享 iCal 文件的 Intent
     *
     * @param context 上下文
     * @param icsFile .ics 文件
     * @return 分享 Intent
     */
    fun createShareIcalIntent(
        context: Context,
        icsFile: File
    ): Intent {
        val fileUri = getContentUri(context, icsFile)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(shareIntent, "导出到日历")
    }

    /**
     * 创建保存 iCal 文件到 Downloads 的 Intent
     *
     * @param fileName 文件名
     * @return Intent
     */
    fun createSaveIcalIntent(fileName: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/calendar"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
    }

    /**
     * 获取文件的 Content URI
     *
     * @param context 上下文
     * @param file 文件对象
     * @return Content URI
     */
    fun getContentUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * 清理缓存目录中的旧文件
     *
     * @param context 上下文
     * @param dirName 目录名
     * @param keepHours 保留时间（小时）
     */
    fun cleanCacheFiles(context: Context, dirName: String, keepHours: Int = 24) {
        val cacheDir = File(context.cacheDir, dirName)
        if (!cacheDir.exists()) return

        val cutoffTime = System.currentTimeMillis() - (keepHours * 60 * 60 * 1000)

        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }
}
