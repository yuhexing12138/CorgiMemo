package com.corgimemo.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 用户头像私有目录管理
 *
 * 存储路径：/data/data/com.corgimemo.app/files/avatars/
 * - 应用卸载时自动清理（隐私好）
 * - 不占用公共相册空间
 * - 无需 WRITE_EXTERNAL_STORAGE 权限
 *
 * 命名规则：{uuid}.png
 * - UUID 避免冲突
 * - 固定 PNG 格式（按本期需求"无压缩"）
 */
object AvatarStorage {

    /** 私有目录名：context.filesDir/avatars */
    private const val DIR_NAME = "avatars"

    /** 文件后缀：固定 PNG 格式 */
    private const val EXT = ".png"

    /**
     * 获取头像目录（不存在则创建）
     *
     * @param context Context（用于访问 filesDir）
     * @return 头像目录 File 对象（保证存在）
     */
    fun getAvatarDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 保存 Bitmap 为新头像文件，返回绝对路径
     *
     * 文件名按 UUID 随机生成，避免与历史头像冲突
     * 使用 PNG 无压缩（quality=100），保持原画质
     *
     * @param context Context
     * @param bitmap  裁剪后的 Bitmap
     * @return 保存的文件绝对路径
     */
    suspend fun saveAvatar(context: Context, bitmap: Bitmap): String =
        withContext(Dispatchers.IO) {
            val file = File(getAvatarDir(context), "${UUID.randomUUID()}$EXT")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            file.absolutePath
        }

    /**
     * 删除指定路径的头像文件（仅用户上传类型，预设不需要删）
     *
     * 通过 [AvatarPath.isPreset] 判定：预设路径以 "preset:" 开头，跳过删除
     * 留空 / null 也直接返回 false
     *
     * @return true 表示成功删除；false 表示文件不存在或非用户上传
     */
    suspend fun deleteAvatar(avatarPath: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (avatarPath.isNullOrBlank() || AvatarPath.isPreset(avatarPath)) return@withContext false
            val file = File(avatarPath)
            file.exists() && file.delete()
        }

    /**
     * 从 URI 解码 Bitmap（用于拍照 / 相册选择）
     *
     * 采样率 = 1（先按原图加载，裁剪环节再缩放）
     * 失败时（如 URI 无效）返回 null
     *
     * @param context Context
     * @param uri     源图片 URI（content:// 或 file://）
     * @param maxSize 预留参数（当前未用，保留扩展位）
     * @return 解码后的 Bitmap，失败返回 null
     */
    suspend fun decodeBitmap(context: Context, uri: Uri, maxSize: Int = 2048): Bitmap? =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)
            }
        }
}
