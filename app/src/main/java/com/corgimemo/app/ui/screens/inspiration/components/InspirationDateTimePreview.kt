package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 灵感日期时间预览组件
 *
 * 在灵感「改日期」弹窗的日历区下方展示当前修改的灵感内容，
 * 让用户直观看到修改后的日期时间对应哪条灵感。
 *
 * 展示内容：
 * - 日期（动态，跟随用户选择的日期变化）
 * - 标题（静态，来自灵感原始数据）
 * - 时间（动态，跟随用户选择的时间变化）
 * - 正文（静态，去 HTML 标签）
 * - 标签（静态，最多 3 个）
 * - 图片（静态，emoji 占位符，最多 2 张 + 剩余数量）
 *
 * 样式参考 CalendarInspirationItem（InspirationCalendarDialog.kt），
 * 字号/间距体系与 TimelineInspirationItem 同步。
 * 无背景，纯展示，无点击事件。
 *
 * @param inspiration 灵感数据（提供标题/内容/标签/图片等静态字段）
 * @param date 用户选中的日期（动态）
 * @param hour 用户选中的小时（动态）
 * @param minute 用户选中的分钟（动态）
 */
@Composable
fun InspirationDateTimePreview(
    inspiration: Inspiration,
    date: LocalDate,
    hour: Int,
    minute: Int
) {
    // 解码标签和图片路径
    val tags = remember(inspiration.tags) { decodeTagsJson(inspiration.tags) }
    val imagePaths = remember(inspiration.imagePaths) { decodePathsJson(inspiration.imagePaths) }

    // 格式化日期："YYYY年M月d日 周X"（包含年份，与用户需求一致）
    val formattedDate = remember(date) {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
        "${date.year}年${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }

    // 格式化时间："HH:mm"
    val formattedTime = remember(hour, minute) {
        String.format("%02d:%02d", hour, minute)
    }

    // 中文字间距（与灵感页统一）
    val chineseLetterSpacing = 0.5.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)  // 仅水平 padding，垂直间距由外部 Spacer 控制
    ) {
        // ===== 日期行（动态，14sp SemiBold）=====
        Text(
            text = formattedDate,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = chineseLetterSpacing
        )

        // 日期 → 标题 间距 8dp
        Spacer(modifier = Modifier.height(8.dp))

        // ===== 标题行（置顶图标 + 标题 16sp Medium）=====
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inspiration.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = Color(0xFFFF9A5C),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = inspiration.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = chineseLetterSpacing
            )
        }

        // 标题 → 时间 间距 4dp
        Spacer(modifier = Modifier.height(4.dp))

        // ===== 时间（动态，11sp 灰色）=====
        Text(
            text = formattedTime,
            fontSize = 11.sp,
            color = Color(0xFF999999),
            letterSpacing = chineseLetterSpacing
        )

        // 时间 → 正文 间距 9dp
        Spacer(modifier = Modifier.height(9.dp))

        // ===== 正文（14sp，行高 21sp）=====
        if (inspiration.content.isNotBlank()) {
            val plainContent = remember(inspiration.content) {
                removeHtmlTags(inspiration.content)
            }
            Text(
                text = plainContent,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color(0xFF666666),
                letterSpacing = chineseLetterSpacing
            )
        }

        // ===== 标签（最多 3 个，11sp 橙色）=====
        if (tags.isNotEmpty()) {
            // 正文 → 标签 间距 7dp
            Spacer(modifier = Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.take(3).forEach { tag ->
                    Text(
                        text = "#$tag",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = UiColors.Primary,
                        letterSpacing = chineseLetterSpacing,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 1.dp, vertical = 0.dp)
                    )
                }
                if (tags.size > 3) {
                    Text(
                        text = "+${tags.size - 3}",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = Color(0xFF999999),
                        letterSpacing = chineseLetterSpacing,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 1.dp, vertical = 0.dp)
                    )
                }
            }
        }

        // ===== 图片缩略图（emoji 占位符，最多 2 张 + 剩余数量）=====
        if (imagePaths.isNotEmpty()) {
            // 标签 → 图片 间距 4dp
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                imagePaths.take(2).forEach { _ ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🖼️", fontSize = 12.sp)
                    }
                }
                if (imagePaths.size > 2) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEEEEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${imagePaths.size - 2}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 去除HTML标签（独立定义，避免与 InspirationCalendarDialog.kt 的 private 版本冲突）
 *
 * @param html 包含HTML标签的字符串
 * @return 纯文本字符串
 */
private fun removeHtmlTags(html: String): String {
    return html
        .replace("<[^>]*>".toRegex(), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
