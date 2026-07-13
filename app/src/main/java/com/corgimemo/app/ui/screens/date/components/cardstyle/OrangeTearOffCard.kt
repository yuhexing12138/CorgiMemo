package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.backgroundColor
import com.corgimemo.app.data.model.bigNumberColor
import com.corgimemo.app.data.model.topBarColor

/**
 * 橙色撕页样式卡片（完整版 + 缩略图版）
 *
 * 完整版：
 * - 固定宽高比 4:5
 * - 使用 BoxWithConstraints 按比例分配内部元素
 * - 底部左侧 6 个点点装饰
 * - 右下角分享/日历操作图标
 *
 * 缩略图版：
 * - 保持原固定尺寸逻辑
 * - 不显示操作图标
 *
 * @param title 标题（空时显示 "未命名"）
 * @param targetDateMillis 目标日期时间戳（毫秒）
 * @param isThumbnail 是否为缩略图（默认 false）
 * @param cardColor 卡片颜色（默认 DEFAULT，使用样式原色）
 * @param onShareClick 分享图标点击（缩略图版不显示）
 * @param onCalendarClick 日历图标点击（缩略图版不显示）
 * @param onCardClick 整个卡片点击（可选）
 */
@Composable
fun OrangeTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onShareClick: (() -> Unit)? = null,
    onCalendarClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    if (isThumbnail) {
        ThumbnailOrangeTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            cardColor = cardColor,
            onCardClick = onCardClick
        )
    } else {
        FullOrangeTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            cardColor = cardColor,
            onShareClick = onShareClick,
            onCalendarClick = onCalendarClick,
            onCardClick = onCardClick
        )
    }
}

/**
 * 完整版橙色撕页卡片
 *
 * 使用 BoxWithConstraints 按卡片总高度比例分配内部元素：
 * - 顶部撕页条高度: 20%
 * - 圆孔直径: 顶部条高度的 27%
 * - 圆孔左右边距: 卡片宽度的 10%
 * - 距离文字字号: 4%
 * - 大数字字号: 30%
 * - 标题字号: 5%
 * - 底部波浪高度: 3%（转换为 px）
 * - 右下角图标大小: 5%
 * - 点点装饰圆点大小: 1.5%
 *
 * 底部点点装饰：6 个小圆点，前 3 个深灰，后 3 个浅灰
 */
@Composable
private fun FullOrangeTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onShareClick: (() -> Unit)? = null,
    onCalendarClick: (() -> Unit)? = null,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val density = LocalDensity.current

    val topColor = topBarColor(cardColor)
    val bgColor = backgroundColor(DateCardColor.DEFAULT, DateCardStyle.OrangeTearOff)
    val numberColor = bigNumberColor(cardColor, DateCardStyle.OrangeTearOff)
    val titleColor = topBarColor(cardColor)

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(4f / 5f)
    ) {
        val cardHeight = maxHeight
        val cardWidth = maxWidth
        val cornerRadius = cardWidth * 0.07f
        val shadowElevation = cardWidth * 0.015f

        val topBarHeight = cardHeight * 0.20f
        val holeSize = topBarHeight * 0.27f
        val holeHorizontalPadding = cardWidth * 0.10f
        val distanceFontSize = with(density) { (cardHeight * 0.04f).toSp() }
        val numberFontSize = with(density) { (cardHeight * 0.30f).toSp() }
        val titleFontSize = with(density) { (cardHeight * 0.05f).toSp() }
        val waveHeightPx = with(density) { (cardHeight * 0.03f).toPx() }
        val iconSize = cardHeight * 0.05f
        val dotSize = cardHeight * 0.015f
        val dotSpacing = dotSize * 0.8f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(WavyBottomShape(waveHeightPx = waveHeightPx))
                    .background(bgColor)
                    .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topBarHeight)
                        .background(topColor)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = holeHorizontalPadding)
                            .size(holeSize)
                            .background(Color.White, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = holeHorizontalPadding)
                            .size(holeSize)
                            .background(Color.White, CircleShape)
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = cardWidth * 0.08f,
                            vertical = cardHeight * 0.04f
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDistanceTextWithWeekday(targetDateMillis),
                        fontSize = distanceFontSize,
                        color = Color(0xFF999999)
                    )
                    Spacer(modifier = Modifier.height(cardHeight * 0.02f))
                    Text(
                        text = daysUntil(targetDateMillis).toString(),
                        fontSize = numberFontSize,
                        fontWeight = FontWeight.Bold,
                        color = numberColor
                    )
                    Spacer(modifier = Modifier.height(cardHeight * 0.02f))
                    Text(
                        text = displayTitle,
                        fontSize = titleFontSize,
                        fontWeight = FontWeight.Medium,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = cardWidth * 0.08f, bottom = cardHeight * 0.04f),
                horizontalArrangement = Arrangement.spacedBy(dotSpacing)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(Color(0xFFCCCCCC), CircleShape)
                    )
                }
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(Color(0xFFEEEEEE), CircleShape)
                    )
                }
            }
            if (onShareClick != null || onCalendarClick != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = cardWidth * 0.04f, bottom = cardHeight * 0.02f),
                    horizontalArrangement = Arrangement.spacedBy(iconSize * 0.2f)
                ) {
                    if (onShareClick != null) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "分享",
                            modifier = Modifier
                                .size(iconSize)
                                .clickable { onShareClick.invoke() },
                            tint = Color(0xFF999999)
                        )
                    }
                    if (onCalendarClick != null) {
                        Icon(
                            imageVector = Icons.Filled.CalendarMonth,
                            contentDescription = "添加到日历",
                            modifier = Modifier
                                .size(iconSize)
                                .clickable { onCalendarClick.invoke() },
                            tint = Color(0xFF999999)
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * 缩略图版橙色撕页卡片
 *
 * 保持原固定尺寸逻辑，所有元素使用固定 dp/sp 值，不显示操作图标
 */
@Composable
private fun ThumbnailOrangeTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val topSectionHeight = 18.dp
    val holeSize = 4.dp
    val numberFontSize = 28.sp
    val titleFontSize = 10.sp
    val distanceFontSize = 8.sp
    val cornerRadius = 8.dp
    val shadowElevation = 0.dp

    val topColor = topBarColor(cardColor)
    val bgColor = backgroundColor(DateCardColor.DEFAULT, DateCardStyle.OrangeTearOff)
    val numberColor = bigNumberColor(cardColor, DateCardStyle.OrangeTearOff)
    val titleColor = topBarColor(cardColor)

    Box(
        modifier = modifier
            .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
            .clip(WavyBottomShape(waveHeightPx = 3f))
            .background(bgColor)
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topSectionHeight)
                    .background(topColor)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 8.dp)
                        .size(holeSize)
                        .background(Color.White, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(holeSize)
                        .background(Color.White, CircleShape)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 4.dp,
                        vertical = 4.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = formatDistanceTextWithWeekday(targetDateMillis),
                    fontSize = distanceFontSize,
                    color = Color(0xFF999999)
                )
                Text(
                    text = daysUntil(targetDateMillis).toString(),
                    fontSize = numberFontSize,
                    fontWeight = FontWeight.Bold,
                    color = numberColor
                )
                Text(
                    text = displayTitle,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
