// app/src/main/java/com/corgimemo/app/util/InspirationScreenshot.kt
package com.corgimemo.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import com.corgimemo.app.backup.exporter.ShareIntentHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 灵感卡片截图工具
 * 使用 Compose 1.9 GraphicsLayer 录制并保存为 PNG
 */
object InspirationScreenshot {

    /**
     * 将 GraphicsLayer 捕获为 Android Bitmap
     *
     * @param layer 录制了卡片内容的 GraphicsLayer
     * @return Android Bitmap
     */
    fun captureAsBitmap(layer: GraphicsLayer): Bitmap {
        return layer.toImageBitmap().asAndroidBitmap()
    }

    /**
     * 保存 Bitmap 到应用外部 Pictures 目录
     *
     * @param context 上下文
     * @param bitmap 要保存的位图
     * @return 保存后的文件，失败返回 null
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): File? =
        withContext(Dispatchers.IO) {
            try {
                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: return@withContext null
                if (!picturesDir.exists()) picturesDir.mkdirs()
                val file = File(picturesDir, "Inspiration_${System.currentTimeMillis()}.png")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                file
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

    /**
     * 创建分享图片的 Intent（复用项目内现有 ShareIntentHelper）
     *
     * @param context 上下文
     * @param imageFile 要分享的图片文件
     * @return 启动系统分享面板的 Intent
     */
    fun createShareIntent(context: Context, imageFile: File): Intent {
        return ShareIntentHelper.createShareImageIntent(
            context = context,
            imageFile = imageFile,
            text = null
        )
    }
}
