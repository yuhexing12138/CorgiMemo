package com.corgimemo.app.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.core.content.FileProvider
import com.corgimemo.app.ui.components.GlobalSnackbarController
import java.io.File
import java.util.UUID

/**
 * 图片剪贴板工具
 *
 * 职责：
 * 1. 把图片写入系统剪贴板（生成 FileProvider content URI），供 QQ/微信等外部应用粘贴；
 * 2. 把系统剪贴板里的图片读回应用内部 pictures/ 目录，返回可插入编辑页的绝对路径；
 * 3. 提供「粘贴」按钮与 Ctrl+V / Cmd+V 快捷键的统一接入（Modifier 扩展）。
 *
 * 设计要点：复制到系统剪贴板采用 content URI 而非直接拷贝字节，避免无谓的存储占用；
 * 应用内粘贴时再按需把字节落盘到 pictures/（与 ImagePicker / ImageUtils 保存图片同目录，
 * 便于统一管理、清理）。
 */
object ClipboardImageHelper {

    /** 应用内部图片目录名（与 ImageUtils.compressAndSaveImage 保存图片所用的 pictures/ 一致） */
    private const val PICTURES_DIR = "pictures"

    /**
     * 复制图片到系统剪贴板，并弹出 Snackbar 提示。
     *
     * @param context 上下文（用于获取 ClipboardManager 与 FileProvider）
     * @param imagePath 应用内部存储中的图片绝对路径（必须位于 FileProvider 映射目录）
     */
    fun copyImageToClipboard(context: Context, imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
            // 文件不存在（如已被清理）时给出失败提示，避免静默无反应
            GlobalSnackbarController.showMessage("图片文件不存在，复制失败")
            return
        }
        // 用 FileProvider 生成 content URI：满足 Android 7.0+ 安全策略，且外部应用可凭剪贴板授予的
        // 临时读权限读取（QQ/微信等收到粘贴事件后即可 openInputStream 取图）。
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val clip = ClipData.newUri(context.contentResolver, "image", uri)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(clip)
        GlobalSnackbarController.showMessage("柯基图像+已添加到剪贴板")
    }

    /**
     * 判断系统剪贴板当前是否含有图片。
     *
     * @param context 上下文
     * @return 存在图片 URI 返回 true，否则 false
     */
    fun hasImageInClipboard(context: Context): Boolean {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return false
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i).uri ?: continue
            val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (type?.startsWith("image/") == true) return true
        }
        return false
    }

    /**
     * 读取系统剪贴板中的图片，并拷贝字节到应用内部 pictures/ 目录。
     *
     * 兼容两类来源：
     * - 本应用复制的图片（content URI 指向自身 FileProvider，同 UID 可直接读取）；
     * - 其它应用复制的图片（剪贴板框架已授予临时读权限）。
     *
     * @param context 上下文
     * @return 成功返回应用内部绝对路径；剪贴板无图片或读取失败返回 null
     */
    fun readCopiedImageToAppStorage(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i).uri ?: continue
            val type = runCatching { context.contentResolver.getType(uri) }.getOrNull()
            if (type?.startsWith("image/") != true) continue
            val dir = File(context.filesDir, PICTURES_DIR)
            if (!dir.exists()) dir.mkdirs()
            val ext = resolveExtension(uri, type)
            val dst = File(dir, "paste_${UUID.randomUUID()}.$ext")
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                // 拷贝成功且非空才返回路径，否则视为失败
                if (dst.exists() && dst.length() > 0) dst.absolutePath else null
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    /**
     * 从 content URI 解析文件扩展名（优先用 URI 路径后缀，回退用 MIME 子类型）。
     *
     * @param uri content URI
     * @param mimeType contentResolver.getType 返回的 MIME（如 image/jpeg）
     * @return 不含点的扩展名（如 jpg / png）
     */
    private fun resolveExtension(uri: Uri, mimeType: String): String {
        // 优先采用 URI 路径里的真实后缀（最贴近原格式，避免 png→jpg 透明丢失）
        val fromPath = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,5}")) }
        if (!fromPath.isNullOrBlank()) return fromPath
        // 回退：用 MIME 子类型（image/jpeg → jpeg）
        val subtype = mimeType.substringAfter('/', "").substringBefore(';')
        return if (subtype.isNotBlank()) subtype else "jpg"
    }

    /**
     * 统一的「粘贴图片」入口：读取剪贴板图片并插入到编辑页。
     * 剪贴板无图片时给出 Snackbar 提示，避免按钮点击无反馈。
     *
     * @param context 上下文
     * @param onInsert 插入回调（灵感编辑页传 bodyBlocks::insertImageAtFocused，
     *                  待办编辑页传 ::addImageToFocusedLine）
     */
    fun pasteClipboardImage(context: Context, onInsert: (String) -> Unit) {
        val path = readCopiedImageToAppStorage(context)
        if (path != null) {
            onInsert(path)
        } else {
            GlobalSnackbarController.showMessage("剪贴板中没有图片")
        }
    }
}

/**
 * Modifier 扩展：拦截 Ctrl+V / Cmd+V 快捷键，当剪贴板含图片时粘贴到编辑页。
 * 剪贴板只有文本时返回 false，交由原本的输入框处理文本粘贴（互不干扰）。
 *
 * 说明：onPreviewKeyEvent 在按键下发阶段先于聚焦的输入框被调用，返回 true 即消费事件、
 * 阻止输入框自身的文本粘贴，从而把图片粘贴重定向到编辑页插入逻辑。
 *
 * @param context 上下文
 * @param onInsert 粘贴图片时的插入回调
 */
fun Modifier.pasteImageOnCtrlV(context: Context, onInsert: (String) -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val isPasteShortcut = (event.isCtrlPressed || event.isMetaPressed) &&
            event.key == Key.V &&
            event.type == KeyEventType.KeyDown
        if (isPasteShortcut && ClipboardImageHelper.hasImageInClipboard(context)) {
            ClipboardImageHelper.pasteClipboardImage(context, onInsert)
            true
        } else {
            false
        }
    }
