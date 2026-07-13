package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors
import java.time.Instant
import java.time.ZoneId

/**
 * 米色日历撕页样式(完整版 + 缩略图版)
 *
 * 完整版(参考图 3):
 * - 圆角 20dp 米色 #FFF8F0 卡片,elevation 4dp
 * - 顶部 "yyyy年M月" 标题 + 三点指示器
 * - 单月日历(目标日红圈高亮)
 * - 标题 + 大数字
 * - 底部距离文字
 * - 撕页波浪
 *
 * 缩略图版:所有元素同比缩放,80dp x 120dp
 *
 * @param title 标题(空时显示 "未命名")
 * @param targetDateMillis 目标日期时间戳(毫秒)
 * @param isThumbnail 是否为缩略图(默认 false)
 * @param onCardClick 整个卡片点击(可选)
 */
@Composable
fun CalendarTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val targetDate = Instant.ofEpochMilli(targetDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val targetYear = targetDate.year
    val targetMonth = targetDate.monthValue
    val targetDay = targetDate.dayOfMonth

    val numberFontSize = if (isThumbnail) 28.sp else 64.sp
    val titleFontSize = if (isThumbnail) 10.sp else 16.sp
    val distanceFontSize = if (isThumbnail) 8.sp else 12.sp
    val headerFontSize = if (isThumbnail) 9.sp else 16.sp
    val calendarTextSize = if (isThumbnail) 8.sp else 14.sp
    val cornerRadius = if (isThumbnail) 8.dp else 20.dp
    val shadowElevation = if (isThumbnail) 0.dp else 4.dp
    val cardBackground = Color(0xFFFFF8F0)  // 米色

    Box(
        modifier = modifier
            .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
            .clip(WavyBottomShape(waveHeightPx = if (isThumbnail) 3f else 8f))
            .background(cardBackground)
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isThumbnail) 6.dp else 12.dp, vertical = if (isThumbnail) 4.dp else 8.dp)
        ) {
            // 顶部标题 + 三点指示器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${targetYear}年${targetMonth}月",
                    fontSize = headerFontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // 三点指示器(缩略图版隐藏)
                if (!isThumbnail) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color(0xFFCCCCCC), CircleShape)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (isThumbnail) 2.dp else 8.dp))
            // 日历(缩略图版简化显示)
            if (isThumbnail) {
                // 缩略图版:只显示目标月+目标日
                Text(
                    text = "$targetMonth月",
                    fontSize = 8.sp,
                    color = Color(0xFF666666)
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Transparent, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = targetDay.toString(),
                        fontSize = 10.sp,
                        color = UiColors.Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // 完整版:完整月历
                MiniCalendar(
                    year = targetYear,
                    month = targetMonth,
                    targetDay = targetDay,
                    textSize = calendarTextSize,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.weight(1f))
            // 标题(缩略图版放上面已省略大数字区域,完整版按 reference)
            if (isThumbnail) {
                Text(
                    text = displayTitle,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            } else {
                Text(
                    text = displayTitle,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1
                )
            }
            // 大数字(仅完整版显示)
            if (!isThumbnail) {
                Text(
                    text = daysUntil(targetDateMillis).toString(),
                    fontSize = numberFontSize,
                    fontWeight = FontWeight.Bold,
                    color = UiColors.Primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // 距离文字
            Text(
                text = formatDistanceTextShort(targetDateMillis),
                fontSize = distanceFontSize,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
