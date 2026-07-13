package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
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
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.backgroundColor
import com.corgimemo.app.data.model.bigNumberColor
import com.corgimemo.app.data.model.topBarColor
import com.corgimemo.app.ui.theme.UiColors

/**
 * 橙色撕页样式(完整版 + 缩略图版)
 *
 * 完整版(参考图 2):
 * - 圆角 20dp 白底卡片,elevation 4dp
 * - 顶部 60dp 橙色撕页条 + 2 个白圆孔(撕页装订孔)
 * - 距离文字 + 大数字 + 标题
 * - 右下角分享/日历 IconButton
 * - 底部撕页波浪
 *
 * 缩略图版:所有元素同比缩放,80dp x 120dp,不显示操作图标
 *
 * @param title 标题(空时显示 "未命名")
 * @param targetDateMillis 目标日期时间戳(毫秒)
 * @param isThumbnail 是否为缩略图(默认 false)
 * @param cardColor 卡片颜色(默认 DEFAULT,使用样式原色;由 DateCardColor 透传)
 * @param onShareClick 分享图标点击(缩略图版不显示,完整版默认 Snackbar 占位)
 * @param onCalendarClick 日历图标点击(缩略图版不显示,完整版默认 Snackbar 占位)
 * @param onCardClick 整个卡片点击(可选,用于将来扩展)
 */
@Composable
fun OrangeTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false,
    cardColor: DateCardColor = DateCardColor.DEFAULT,  // ← 新增
    onShareClick: (() -> Unit)? = null,
    onCalendarClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val topSectionHeight = if (isThumbnail) 18.dp else 60.dp
    val holeSize = if (isThumbnail) 4.dp else 16.dp
    val numberFontSize = if (isThumbnail) 28.sp else 64.sp
    val titleFontSize = if (isThumbnail) 10.sp else 16.sp
    val distanceFontSize = if (isThumbnail) 8.sp else 12.sp
    val cornerRadius = if (isThumbnail) 8.dp else 20.dp
    val shadowElevation = if (isThumbnail) 0.dp else 4.dp

    // 颜色源(全部走 helper 函数;DEFAULT 时输出与现有硬编码完全一致)
    val topColor = topBarColor(cardColor)
    // 卡片自身背景色**永远不跟随 cardColor 变化**,保持 DEFAULT 逻辑(白色)—
    // 设计意图:让 cardColor 只影响主屏背景与撕页条/数字/目标日圆圈等"装饰"元素,
    // 卡片本体始终是纯白底,与主屏颜色叠加形成层次感。
    val bgColor = backgroundColor(DateCardColor.DEFAULT, DateCardStyle.OrangeTearOff)
    val numberColor = bigNumberColor(cardColor, DateCardStyle.OrangeTearOff)

    Box(
        modifier = modifier
            .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
            .clip(WavyBottomShape(waveHeightPx = if (isThumbnail) 3f else 8f))
            .background(bgColor)                          // ← 原 MaterialTheme.colorScheme.surface
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部橙色撕页条 + 2 个白圆孔
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topSectionHeight)
                    .background(topColor)                // ← 原 UiColors.Primary
            ) {
                // 左圆孔
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                        .size(holeSize)
                        .background(Color.White, CircleShape)
                )
                // 右圆孔
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                        .size(holeSize)
                        .background(Color.White, CircleShape)
                )
            }
            // 主体
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isThumbnail) 4.dp else 16.dp,
                        vertical = if (isThumbnail) 4.dp else 12.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // 距离文字
                Text(
                    text = formatDistanceTextWithWeekday(targetDateMillis),
                    fontSize = distanceFontSize,
                    color = Color(0xFF999999)
                )
                // 大数字
                Text(
                    text = daysUntil(targetDateMillis).toString(),
                    fontSize = numberFontSize,
                    fontWeight = FontWeight.Bold,
                    color = numberColor  // ← 原 UiColors.Primary
                )
                // 标题
                Text(
                    text = displayTitle,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        // 右下角操作图标(仅完整版)
        if (!isThumbnail) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = "分享",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onShareClick?.invoke() },
                    tint = Color(0xFF999999)
                )
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = "添加到日历",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onCalendarClick?.invoke() },
                    tint = Color(0xFF999999)
                )
            }
        }
    }
}
