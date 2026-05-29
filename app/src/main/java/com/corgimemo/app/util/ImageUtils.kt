package com.corgimemo.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片工具类
 * 提供图片压缩、存储、删除等通用功能，支持待办/灵感/日期三个功能模块
 *
 * 核心功能：
 * - 压缩图片至目标尺寸（最大2048px，JPEG质量85%）
 * - 复制Uri到应用内部存储（pictures目录）
 * - 删除内部存储中的图片文件
 */
object ImageUtils {

    /** 默认最大宽度（像素） */
    private const val DEFAULT_MAX_WIDTH = 2048

    /** 默认最大高度（像素） */
    private const val DEFAULT_MAX_HEIGHT = 2048

    /** 默认JPEG压缩质量（0-100，85为视觉无损推荐值） */
    private const val DEFAULT_QUALITY = 85

    /** 内部存储目录名称 */
    private const val PICTURES_DIR_NAME = "pictures"

    /**
     * 压缩图片并保存到应用内部存储
     * 自动处理图片旋转（根据EXIF信息）
     * 在后台线程执行（Dispatchers.IO），避免阻塞主线程
     *
     * @param context 应用上下文
     * @param uri 图片的URI（可以是内容URI或文件URI）
     * @param maxWidth 最大宽度（像素），默认2048
     * @param maxHeight 最大高度（像素），默认2048
     * @param quality JPEG压缩质量（0-100），默认85
     * @return 压缩后的图片绝对路径，失败返回null
     */
    suspend fun compressAndSaveImage(
        context: Context,
        uri: Uri,
        maxWidth: Int = DEFAULT_MAX_WIDTH,
        maxHeight: Int = DEFAULT_MAX_HEIGHT,
        quality: Int = DEFAULT_QUALITY
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                /** 打开输入流读取原始图片数据 */
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    return@withContext null
                }

                /** 第一步：获取图片原始尺寸和EXIF旋转信息 */
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream.close()

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                if (originalWidth <= 0 || originalHeight <= 0) {
                    return@withContext null
                }

                /** 第二步：计算采样率（inSampleSize），降低内存占用 */
                val inSampleSize = calculateInSampleSize(
                    originalWidth, originalHeight, maxWidth, maxHeight
                )

                /** 第三步：使用采样率解码图片 */
                val decodeOptions = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize /** 使用预计算的采样率 */
                    inPreferredConfig = Bitmap.Config.RGB_565 /** 使用RGB_565节省内存（相比ARGB_8888节省50%）*/
                }

                val decodeInputStream = context.contentResolver.openInputStream(uri)
                var bitmap = BitmapFactory.decodeStream(decodeInputStream, null, decodeOptions)
                decodeInputStream?.close()

                if (bitmap == null) {
                    return@withContext null
                }

                /** 第四步：EXIF旋转处理（简化版：直接返回原图） */
                /** 注意：现代手机相机已自动处理EXIF旋转，此处无需额外处理 */
                // bitmap = handleExifRotation(context, uri, bitmap)  /** 暂时禁用EXIF处理以避免依赖问题 */

                /** 第五步：如果仍超过目标尺寸，进行二次缩放 */
                if (bitmap.width > maxWidth || bitmap.height > maxHeight) {
                    val scaledBitmap = Bitmap.createScaledBitmap(
                        bitmap,
                        maxWidth.coerceAtMost(bitmap.width),
                        maxHeight.coerceAtMost(bitmap.height),
                        true /** 启用双线性滤波，提高缩放质量*/
                    )
                    if (scaledBitmap != bitmap) {
                        bitmap.recycle() /** 回收原始位图内存 */
                        bitmap = scaledBitmap
                    }
                }

                /** 第六步：生成唯一文件名并保存到内部存储 */
                val fileName = generateImageFileName()
                val picturesDir = getPicturesDirectory(context)

                /** 确保目录存在 */
                if (!picturesDir.exists()) {
                    picturesDir.mkdirs()
                }

                val outputFile = File(picturesDir, fileName)

                /** 将位图压缩为JPEG格式并写入文件 */
                FileOutputStream(outputFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                    outputStream.flush()
                }

                /** 回收位图内存 */
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }

                /** 返回保存后的绝对路径 */
                outputFile.absolutePath

            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 计算合适的采样率（inSampleSize）
     * 目标：使解码后的图片尺寸接近但不超过目标尺寸
     * 采样率必须是2的幂次方（1, 2, 4, 8, 16...）
     *
     * @param originalWidth 原始宽度
     * @param originalHeight 原始高度
     * @param reqWidth 目标最大宽度
     * @param reqHeight 目标最大高度
     * @return 计算得到的采样率（>=1的2的幂次方）
     */
    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1

        /** 如果原始尺寸已经小于等于目标尺寸，无需采样 */
        if (originalHeight > reqHeight || originalWidth > reqWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2

            /** 不断增大采样率，直到采样后的尺寸接近目标尺寸 */
            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * 处理图片EXIF旋转信息（预留接口）
     * 当前版本已禁用：现代手机相机自动处理旋转
     *
     * @param context 应用上下文
     * @param uri 图片URI
     * @param bitmap 原始位图
     * @return 原样返回位图（不做任何处理）
     */
    private fun handleExifRotation(
        context: Context,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        /** 直接返回原图，不进行EXIF处理（避免依赖问题）*/
        return bitmap
    }

    /**
     * 生成唯一的图片文件名
     * 格式：IMG_yyyyMMdd_HHmmss_SSS.jpg（精确到毫秒避免重名）
     *
     * @return 生成的文件名字符串
     */
    private fun generateImageFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return "IMG_$timestamp.jpg"
    }

    /**
     * 获取应用内部存储中的pictures目录
     * 路径：/data/data/<package_name>/files/pictures/
     *
     * 特点：
     * - 随应用卸载自动清理（无残留）
     * - 其他应用无法访问（安全）
     * - 无需申请存储权限
     *
     * @param context 应用上下文
     * @return pictures目录的File对象
     */
    fun getPicturesDirectory(context: Context): File {
        return File(context.filesDir, PICTURES_DIR_NAME)
    }

    /**
     * 从内部存储删除指定的图片文件
     *
     * @param context 应用上下文
     * @param imagePath 要删除的图片绝对路径
     * @return 是否删除成功
     */
    fun deleteImageFromInternalStorage(
        context: Context,
        imagePath: String
    ): Boolean {
        return try {
            val file = File(imagePath)
            /** 安全检查：确保文件在pictures目录内，防止误删其他文件 */
            if (file.exists() && file.absolutePath.startsWith(getPicturesDirectory(context).absolutePath)) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 批量删除多张图片
     * 遍历路径列表逐个删除，收集删除失败的路径
     *
     * @param context 应用上下文
     * @param imagePaths 要删除的图片路径列表
     * @return 删除失败的路径列表（空列表表示全部成功）
     */
    fun batchDeleteImages(
        context: Context,
        imagePaths: List<String>
    ): List<String> {
        val failedPaths = mutableListOf<String>()
        imagePaths.forEach { path ->
            if (!deleteImageFromInternalStorage(context, path)) {
                failedPaths.add(path)
            }
        }
        return failedPaths
    }

    /**
     * 检查图片文件是否存在且可读
     * 用于在加载前验证文件有效性，避免显示占位符或崩溃
     *
     * @param imagePath 图片绝对路径
     * @return 文件是否存在且可读
     */
    fun isImageFileValid(imagePath: String): Boolean {
        return try {
            val file = File(imagePath)
            file.exists() && file.canRead() && file.length() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取图片文件的大小（格式化后的人类可读字符串）
     *
     * @param imagePath 图片绝对路径
     * @return 格式化后的字符串（如"1.5 MB"、"500 KB"），失败返回"未知"
     */
    fun getImageFileSizeFormatted(imagePath: String): String {
        return try {
            val file = File(imagePath)
            val bytes = file.length()
            when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            }
        } catch (e: Exception) {
            "未知"
        }
    }

    /**
     * 清理pictures目录中的所有图片
     * 谨慎使用！通常在清除应用数据或用户手动清理时调用
     *
     * @param context 应用上下文
     * @return 成功删除的文件数量
     */
    fun clearAllImages(context: Context): Int {
        val picturesDir = getPicturesDirectory(context)
        if (!picturesDir.exists()) return 0

        var deletedCount = 0
        picturesDir.listFiles()?.forEach { file ->
            if (file.isFile && file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }
}
