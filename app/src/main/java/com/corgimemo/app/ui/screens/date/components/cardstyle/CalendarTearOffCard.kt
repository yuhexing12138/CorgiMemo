package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.backgroundColor
import com.corgimemo.app.data.model.targetRingColor
import com.corgimemo.app.data.model.topBarColor
import java.time.Instant
import java.time.ZoneId

/**
 * 米色日历撕页样式(完整版 + 缩略图版)
 *
 * 完整版:
 * - 固定宽高比 1:2 的米色卡片
 * - 顶部和底部均有撕页波浪效果
 * - 顶部 "yyyy年M月" 标题 + 三点指示器
 * - 单月日历(目标日红圈高亮,带分割线)
 * - 月历下方主分割虚线
 * - 标题 + 大数字 + 距离文字
 * - 所有内部元素按卡片总高度百分比分配
 *
 * 缩略图版:保持原固定尺寸逻辑,80dp x 120dp
 *
 * @param title 标题(空时显示 "未命名")
 * @param targetDateMillis 目标日期时间戳(毫秒)
 * @param isThumbnail 是否为缩略图(默认 false)
 * @param cardColor 卡片颜色(默认 DEFAULT,使用样式原色;由 DateCardColor 透传)
 * @param onCardClick 整个卡片点击(可选)
 */
@Composable
fun CalendarTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    isThumbnail: Boolean = false,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onCardClick: (() -> Unit)? = null
) {
    if (isThumbnail) {
        ThumbnailCalendarTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            cardColor = cardColor,
            onCardClick = onCardClick
        )
    } else {
        FullCalendarTearOffCard(
            title = title,
            targetDateMillis = targetDateMillis,
            modifier = modifier,
            cardColor = cardColor,
            onCardClick = onCardClick
        )
    }
}

/**
 * 完整版日历撕页卡片
 *
 * 使用 BoxWithConstraints 获取卡片高度,所有内部元素按卡片总高百分比分配。
 * 固定宽高比 1:2,顶部和底部均有波浪效果。
 *
 * @param title 标题
 * @param targetDateMillis 目标日期时间戳
 * @param modifier 修饰符
 * @param cardColor 卡片颜色
 * @param onCardClick 点击回调
 */
@Composable
private fun FullCalendarTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val targetDate = Instant.ofEpochMilli(targetDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val targetYear = targetDate.year
    val targetMonth = targetDate.monthValue
    val targetDay = targetDate.dayOfMonth
    val density = LocalDensity.current

    val numberColor = topBarColor(cardColor)
    val ringColor = targetRingColor(cardColor)
    val titleColor = MaterialTheme.colorScheme.onSurface
    val targetTextColor = topBarColor(cardColor)
    val bgColor = backgroundColor(DateCardColor.DEFAULT, DateCardStyle.CalendarTearOff)

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val cardMaxHeight = maxHeight
            val cardMaxWidth = maxWidth
            val cornerRadius = cardMaxWidth * 0.07f
            val shadowElevation = cardMaxWidth * 0.015f
            val topWaveHeightPx = with(density) { (cardMaxHeight * 0.03f).toPx() }
            val bottomWaveHeightPx = with(density) { (cardMaxHeight * 0.03f).toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(shadowElevation, RoundedCornerShape(cornerRadius))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(
                            WavyTopBottomShape(
                                topWaveHeightPx = topWaveHeightPx,
                                bottomWaveHeightPx = bottomWaveHeightPx
                            )
                        )
                        .background(bgColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = cardMaxHeight * 0.03f)
                            .padding(horizontal = cardMaxWidth * 0.06f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val headerHeight = cardMaxHeight * 0.06f
                        val calendarHeight = cardMaxHeight * 0.45f
                        val titleAreaHeight = cardMaxHeight * 0.06f
                        val bigNumberHeight = cardMaxHeight * 0.22f
                        val distanceTextHeight = cardMaxHeight * 0.06f
                        val dividerTopPadding = cardMaxHeight * 0.01f
                        val dividerBottomPadding = cardMaxHeight * 0.01f
                        val bottomPadding = cardMaxHeight * 0.01f

                        val monthTitleFontSize = with(density) { (cardMaxHeight * 0.035f).toSp() }
                        val dotSize = cardMaxHeight * 0.010f
                        val weekHeaderFontSize = with(density) { (cardMaxHeight * 0.028f).toSp() }
                        val dateFontSize = with(density) { (cardMaxHeight * 0.030f).toSp() }
                        val titleFontSize = with(density) { (cardMaxHeight * 0.038f).toSp() }
                        val bigNumberFontSize = with(density) { (cardMaxHeight * 0.18f).toSp() }
                        val distanceFontSize = with(density) { (cardMaxHeight * 0.028f).toSp() }

                        TopHeaderArea(
                            year = targetYear,
                            month = targetMonth,
                            height = headerHeight,
                            monthTitleFontSize = monthTitleFontSize,
                            dotSize = dotSize
                        )

                        MiniCalendar(
                            year = targetYear,
                            month = targetMonth,
                            targetDay = targetDay,
                            textSize = dateFontSize,
                            weekHeaderTextSize = weekHeaderFontSize,
                            targetRingColor = ringColor,
                            targetTextColor = targetTextColor,
                            showDividers = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(calendarHeight)
                        )

                        Spacer(Modifier.height(dividerTopPadding))

                        MainDivider()

                        Spacer(Modifier.height(dividerBottomPadding))

                        TitleArea(
                            title = displayTitle,
                            height = titleAreaHeight,
                            titleFontSize = titleFontSize,
                            titleColor = titleColor
                        )

                        BigNumberArea(
                            targetDateMillis = targetDateMillis,
                            height = bigNumberHeight,
                            numberFontSize = bigNumberFontSize,
                            numberColor = numberColor
                        )

                        DistanceTextArea(
                            targetDateMillis = targetDateMillis,
                            height = distanceTextHeight,
                            distanceFontSize = distanceFontSize
                        )

                        Spacer(Modifier.height(bottomPadding))
                    }
                }
            }
        }
    }
}


/**
 * 缩略图版日历撕页卡片
 *
 * 保持原固定尺寸逻辑,80dp x 120dp。
 *
 * @param title 标题
 * @param targetDateMillis 目标日期时间戳
 * @param modifier 修饰符
 * @param cardColor 卡片颜色
 * @param onCardClick 点击回调
 */
@Composable
private fun ThumbnailCalendarTearOffCard(
    title: String,
    targetDateMillis: Long,
    modifier: Modifier = Modifier,
    cardColor: DateCardColor = DateCardColor.DEFAULT,
    onCardClick: (() -> Unit)? = null
) {
    val displayTitle = title.ifBlank { "未命名" }
    val targetDate = Instant.ofEpochMilli(targetDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val targetMonth = targetDate.monthValue
    val targetDay = targetDate.dayOfMonth

    val numberColor = topBarColor(cardColor)
    val titleColor = MaterialTheme.colorScheme.onSurface
    val bgColor = backgroundColor(DateCardColor.DEFAULT, DateCardStyle.CalendarTearOff)

    Box(
        modifier = modifier
            .size(80.dp, 120.dp)
            .clip(WavyBottomShape(waveHeightPx = 3f))
            .background(bgColor)
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = "${targetMonth}月",
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
                    color = numberColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = displayTitle,
                fontSize = 9.sp,
                color = titleColor,
                maxLines = 1
            )
            Text(
                text = formatDistanceTextShort(targetDateMillis),
                fontSize = 8.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 顶部标题区域(月份标题 + 三点指示器)
 *
 * @param year 年份
 * @param month 月份
 * @param height 区域高度
 * @param monthTitleFontSize 月份标题字号
 * @param dotSize 三点指示器圆点大小
 */
@Composable
private fun TopHeaderArea(
    year: Int,
    month: Int,
    height: Dp,
    monthTitleFontSize: androidx.compose.ui.unit.TextUnit,
    dotSize: Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${year}年${month}月",
            fontSize = monthTitleFontSize,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dotSize * 0.8f)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .background(Color(0xFFCCCCCC), CircleShape)
                )
            }
        }
    }
}

/**
 * 月历下方主分割虚线
 *
 * @param modifier 修饰符
 */
@Composable
private fun MainDivider(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
    ) {
        drawLine(
            color = Color(0xFFCCCCCC),
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                0f
            )
        )
    }
}

/**
 * 标题区域(用户输入标题)
 *
 * @param title 标题文本
 * @param height 区域高度
 * @param titleFontSize 标题字号
 * @param titleColor 标题颜色
 */
@Composable
private fun TitleArea(
    title: String,
    height: Dp,
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    titleColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.Medium,
            color = titleColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

/**
 * 大数字区域(距离天数)
 *
 * @param targetDateMillis 目标日期时间戳
 * @param height 区域高度
 * @param numberFontSize 大数字字号
 * @param numberColor 大数字颜色
 */
@Composable
private fun BigNumberArea(
    targetDateMillis: Long,
    height: Dp,
    numberFontSize: androidx.compose.ui.unit.TextUnit,
    numberColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = daysUntil(targetDateMillis).toString(),
            fontSize = numberFontSize,
            fontWeight = FontWeight.Bold,
            color = numberColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 距离文字区域
 *
 * @param targetDateMillis 目标日期时间戳
 * @param height 区域高度
 * @param distanceFontSize 距离文字字号
 */
@Composable
private fun DistanceTextArea(
    targetDateMillis: Long,
    height: Dp,
    distanceFontSize: androidx.compose.ui.unit.TextUnit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = formatDistanceTextShort(targetDateMillis),
            fontSize = distanceFontSize,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center
        )
    }
}
