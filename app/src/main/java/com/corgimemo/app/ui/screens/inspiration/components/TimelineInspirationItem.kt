package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors
import java.util.Calendar

/**
 * 时间线灵感条目组件（参考图规范版）
 *
 * 布局结构：
 * ```
 * [左侧时间栏 44dp] [间距 14dp] [节点 8dp] [右侧内容区]
 * ```
 *
 * 字号体系（与 PRD 参考图一致）：
 * - 左侧时间栏：年月 12sp / 大号日期数字 25sp Medium
 * - 右侧内容区：标题 16sp Medium / 时分时间 11sp / 正文 14sp / 标签 11sp
 * - 中文字间距统一 +0.5sp
 *
 * 间距体系：
 * - 标题 → 时分时间：4dp
 * - 时分时间 → 正文：9dp
 * - 正文 → 标签：7dp
 * - 正文行高：21sp
 *
 * 横向边距：
 * - 时间栏宽度：44dp
 * - 时间栏 → 节点中心：14dp
 * - 节点直径：8dp
 * - 节点中心 X 坐标：58dp（=44+14）
 * - 内容区起始 X 坐标：62dp（=58+4 节点半径）
 *
 * @param inspiration 灵感实体数据
 * @param tags 标签列表
 * @param imagePaths 图片路径列表
 * @param formattedTime 格式化后的时间字符串（如 "09:00"）
 * @param showDate 是否显示左侧日期列（同一天多条时仅第一条显示）
 * @param isPinnedItem 是否为置顶项
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param modifier 修饰符
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TimelineInspirationItem(
    inspiration: Inspiration,
    tags: List<String>,
    imagePaths: List<String>,
    formattedTime: String,
    showDate: Boolean = true,
    isPinnedItem: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // ===== 横向布局常量 =====
    val dateColumnWidth = 44.dp                 // 左侧时间栏宽度
    val dateToNodeGap = 14.dp                   // 时间栏右侧到节点中心
    val nodeDiameter = 8.dp                     // 节点直径
    val nodeCenterX = dateColumnWidth + dateToNodeGap     // 58dp
    val nodeRadius = nodeDiameter / 2                      // 4dp
    val contentStartX = nodeCenterX + nodeRadius          // 62dp
    val timelineLineX = nodeCenterX                        // 竖线 X = 58dp

    // ===== 垂直间距常量 =====
    val titleToTimeGap = 4.dp                   // 标题 → 时分时间
    val timeToContentGap = 9.dp                 // 时分时间 → 正文
    val contentToTagGap = 7.dp                  // 正文 → 标签
    val tagToImageGap = 4.dp                    // 标签 → 图片

    // ===== 中文字间距 =====
    val chineseLetterSpacing = 0.5.sp

    // ===== 状态：测量时间栏实际高度（用于节点 Y 定位）=====
    var dateColumnHeightPx by remember { mutableIntStateOf(0) }
    val dateColumnHeightDp = with(density) { dateColumnHeightPx.toDp() }

    // 节点中心 Y = "2026.07" 高度 + "08" 高度/2
    // 经验估算："2026.07" lineHeight 14dp，"08" lineHeight 30dp
    // 比例 14:30 ≈ 0.32:0.68，"08" 中心 = 0.32h + 0.34h = 0.66h
    // 使用 Dp * Float 运算符重载，比 (value * 0.66f).dp 更可靠
    val nodeCenterY = dateColumnHeightDp * 0.66f
    val nodeTopY = (nodeCenterY - nodeRadius).coerceAtLeast(0.dp)

    // ===== 状态：测量正文实际高度（用于图片位置备用）=====
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val contentHeightDp = with(density) { contentHeightPx.toDp() }

    // ===== 颜色 =====
    val nodeColor = if (isPinnedItem) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.primary
    val timelineLineColor = Color(0xFFEEEEEE)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            // 竖线贯通整个 Item 高度
            .drawBehind {
                val x = timelineLineX.toPx()
                drawLine(
                    color = timelineLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
    ) {
        // ========== 左侧时间栏（年月 + 大号日期）==========
        if (showDate) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .width(dateColumnWidth)
                    .align(Alignment.TopStart)
                    .onSizeChanged { dateColumnHeightPx = it.height }
            ) {
                // 年月文本（12sp 灰色）
                Text(
                    text = String.format("%04d.%02d", getYear(inspiration.createdAt), getMonth(inspiration.createdAt)),
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    letterSpacing = chineseLetterSpacing
                )
                // 大号日期数字（25sp 黑色 Medium）
                Text(
                    text = String.format("%02d", getDay(inspiration.createdAt)),
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = chineseLetterSpacing
                )
            }
        }

        // ========== 节点（8dp 圆点，垂直对齐"08"数字中心）==========
        if (showDate) {
            Box(
                modifier = Modifier
                    .offset(x = nodeCenterX - nodeRadius, y = nodeTopY)
                    .size(nodeDiameter)
                    .background(color = nodeColor, shape = CircleShape)
                    .align(Alignment.TopStart)
            )
        }

        // ========== 右侧内容区（标题、时分时间、正文、标签、图片）==========
        Column(
            modifier = Modifier
                .padding(start = contentStartX)
                .align(Alignment.TopStart)
        ) {
            // 标题（16sp Medium）
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinnedItem) {
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

            // 标题 → 时分时间 间距
            Spacer(modifier = Modifier.height(titleToTimeGap))

            // 时分时间（11sp 灰色）
            Text(
                text = formattedTime,
                fontSize = 11.sp,
                color = Color(0xFF999999),
                letterSpacing = chineseLetterSpacing
            )

            // 时分时间 → 正文 间距
            Spacer(modifier = Modifier.height(timeToContentGap))

            // 正文（14sp，行高 21sp）
            if (inspiration.content.isNotBlank()) {
                val plainContent = removeHtmlTags(inspiration.content)
                Text(
                    text = plainContent,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = Color(0xFF666666),
                    letterSpacing = chineseLetterSpacing,
                    modifier = Modifier.onSizeChanged { contentHeightPx = it.height }
                )
            }

            // 正文 → 标签 间距
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(contentToTagGap))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 最多显示 3 个标签
                    tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            fontSize = 11.sp,
                            color = UiColors.Primary,
                            letterSpacing = chineseLetterSpacing,
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // 超出 3 个显示 "+N"
                    if (tags.size > 3) {
                        Text(
                            text = "+${tags.size - 3}",
                            fontSize = 11.sp,
                            color = Color(0xFF999999),
                            letterSpacing = chineseLetterSpacing,
                            modifier = Modifier
                                .background(
                                    color = Color(0xFFF5F5F5),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 标签 → 图片 间距
            if (imagePaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(tagToImageGap))
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
}

/**
 * 从时间戳提取年份
 */
private fun getYear(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.YEAR)
}

/**
 * 从时间戳提取月份（1-12）
 */
private fun getMonth(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.MONTH) + 1
}

/**
 * 从时间戳提取日（1-31）
 */
private fun getDay(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.DAY_OF_MONTH)
}

/**
 * 去除HTML标签工具函数
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
