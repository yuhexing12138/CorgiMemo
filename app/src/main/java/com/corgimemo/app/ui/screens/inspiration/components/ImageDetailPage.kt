// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/ImageDetailPage.kt
package com.corgimemo.app.ui.screens.inspiration.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片详情信息（v2026-09-10，对照原型实现）
 *
 * @property dateLine1 拍摄时间第一行：`2026年9月10日`
 * @property dateLine2 拍摄时间第二行（降级灰）：`星期四  9:41`
 * @property fileLine1 文件信息第一行：文件名（原型参考稿的 `79` 为其业务媒体 id，
 *                     本项目图片为本地文件路径，取文件名最贴近）
 * @property fileLine2 文件信息第二行（降级灰）：`1.95MB  2160x3840px`
 */
private class ImageDetailInfo(
    val dateLine1: String,
    val dateLine2: String,
    val fileLine1: String,
    val fileLine2: String,
)

/**
 * 读取图片详情信息（含文件 IO / EXIF 解析，须在 IO 线程调用）：
 * - 拍摄时间：优先 EXIF DateTimeOriginal（格式 `yyyy:MM:dd HH:mm:ss`），
 *   缺失或解析失败回退文件修改时间；文件不存在则留空（展示时兜底为「—」）。
 * - 文件信息：文件名 + 大小（KB/MB）+ 像素尺寸（inJustDecodeBounds 只读头，不解码位图）。
 */
private fun readImageDetailInfo(imagePath: String): ImageDetailInfo {
    val file = File(imagePath)

    // ---- 拍摄时间 ----
    var dateTime: LocalDateTime? = null
    try {
        val exif = ExifInterface(imagePath)
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        if (!raw.isNullOrBlank()) {
            dateTime = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
        }
    } catch (_: Exception) {
        // EXIF 读取/解析失败（非图片文件、无该 tag、格式异常）→ 回退文件修改时间
    }
    if (dateTime == null && file.exists()) {
        dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault())
    }
    val dateLine1 = dateTime?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: ""
    val dateLine2 = dateTime?.let {
        // Locale.CHINA → 「星期四」；时间 H:mm → 「9:41」（不补零小时，与原型一致）
        val weekday = it.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
        "$weekday  ${it.format(DateTimeFormatter.ofPattern("H:mm"))}"
    } ?: ""

    // ---- 文件信息 ----
    val sizeText = if (file.exists()) formatFileSize(file.length()) else ""
    var width = 0
    var height = 0
    if (file.exists()) {
        try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imagePath, opts)
            width = opts.outWidth
            height = opts.outHeight
        } catch (_: Exception) {
            // 无法解码（损坏/非图片）→ 不展示尺寸段
        }
    }
    val fileLine2 = listOf(sizeText, if (width > 0 && height > 0) "${width}x${height}px" else null)
        .filterNotNull()
        .filter { it.isNotEmpty() }
        .joinToString("  ")

    return ImageDetailInfo(
        dateLine1 = dateLine1,
        dateLine2 = dateLine2,
        fileLine1 = file.name,
        fileLine2 = fileLine2,
    )
}

/**
 * 文件大小格式化：< 1MB → KB（整数，如 `812KB`）；≥ 1MB → MB（两位小数，如 `1.95MB`，与原型一致）。
 */
private fun formatFileSize(bytes: Long): String {
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.US, "%.2fMB", bytes / 1048576.0)
    } else {
        String.format(Locale.US, "%.0fKB", bytes / 1024.0)
    }
}

/**
 * 图片详情页（v2026-09-10，对照原型实现）
 *
 * 全屏白底信息页：顶栏返回箭头 + 居中「详情」标题，下方三组信息行——
 * 拍摄时间 / 文件信息 / 文件路径，灰色小标签 + 深色值、次行降级灰。
 * 在 [InspirationImageGallery] 内以全屏覆盖层形式呈现（黑色沉浸预览之上）；
 * 系统栏由外层按 showDetail 状态切换显示（白底 → 深色图标）。
 *
 * @param imagePath 当前预览图片的绝对路径
 * @param onBack 返回附件页回调
 */
@Composable
fun ImageDetailPage(
    imagePath: String,
    onBack: () -> Unit,
) {
    // 信息读取含文件 IO / EXIF 解析：produceState + IO 线程，按 imagePath 记忆化，
    // 仅首次进入（或换图）时读取一次；未就绪前先渲染顶栏，信息行随后补上。
    val info by produceState<ImageDetailInfo?>(initialValue = null, imagePath) {
        value = withContext(Dispatchers.IO) { readImageDetailInfo(imagePath) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 先铺白底再让出系统栏：状态栏/导航条区域同为白色（浅色外观，深色图标）
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：返回箭头（左） + 居中「详情」标题
            Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Color(0xFF1B1B1B)
                    )
                }
                Text(
                    text = "详情",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1B1B1B),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // 信息行（IO 读取完成后渲染）
            val current = info
            if (current != null) {
                Column(
                    modifier = Modifier
                        .padding(top = 26.dp)
                        .padding(horizontal = 20.dp)
                ) {
                    DetailInfoRow(
                        label = "拍摄时间",
                        value = current.dateLine1.ifBlank { "—" },
                        subValue = current.dateLine2.takeIf { it.isNotBlank() },
                    )
                    DetailInfoRow(
                        label = "文件信息",
                        value = current.fileLine1.ifBlank { "—" },
                        subValue = current.fileLine2.takeIf { it.isNotBlank() },
                    )
                    DetailInfoRow(
                        label = "文件路径",
                        value = imagePath,
                    )
                }
            }
        }
    }
}

/**
 * 单行信息：左侧灰色小标签（固定宽 64dp + 间距 14dp），右侧深色值 + 可选降级灰次行；
 * 行间距 36dp（对照原型）。
 */
@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    subValue: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color(0xFF9E9E9E),
            modifier = Modifier.width(64.dp)
        )
        Column {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1B1B1B),
                lineHeight = 22.sp
            )
            if (!subValue.isNullOrEmpty()) {
                Text(
                    text = subValue,
                    fontSize = 13.sp,
                    color = Color(0xFF9E9E9E),
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
