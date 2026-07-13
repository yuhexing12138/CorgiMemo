package com.corgimemo.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 日期卡片截图工具
 *
 * 提供日期卡片的截图、保存到相册、创建分享 Intent 等功能。
 */
object DateCardScreenshot {

    /**
     * 将 GraphicsLayer 捕获为 Android Bitmap（位图放大版）
     *
     * @param layer 录制了卡片内容的 GraphicsLayer
     * @param scaleFactor 放大倍数（默认 2.0f）
     * @return Android Bitmap
     */
    suspend fun captureAsBitmap(
        layer: GraphicsLayer,
        scaleFactor: Float = 2.0f
    ): Bitmap {
        val originalBitmap = layer.toImageBitmap().asAndroidBitmap()

        if (scaleFactor <= 1f) return originalBitmap

        val newWidth = (originalBitmap.width * scaleFactor).toInt()
        val newHeight = (originalBitmap.height * scaleFactor).toInt()

        if (newWidth <= 0 || newHeight <= 0) return originalBitmap

        val scaledBitmap = Bitmap.createScaledBitmap(
            originalBitmap,
            newWidth,
            newHeight,
            true
        )

        if (scaledBitmap !== originalBitmap) {
            originalBitmap.recycle()
        }

        return scaledBitmap
    }

    /**
     * 保存 Bitmap 到系统相册
     *
     * 复用 InspirationScreenshot 的保存逻辑，文件前缀改为 SpecialDate。
     *
     * @param context 上下文
     * @param bitmap 要保存的位图
     * @return 保存的 Uri，失败返回 null
     */
    suspend fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        return InspirationScreenshot.saveToGallery(context, bitmap)
    }

    /**
     * 创建分享图片 Uri 的 Intent
     *
     * @param context 上下文
     * @param imageUri 分享的图片 Uri
     * @return 启动系统分享面板的 Intent
     */
    fun createShareIntent(context: Context, imageUri: Uri): Intent {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, "分享日期卡片").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
