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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors
import java.util.Calendar

/**
 * 时间线灵感条目组件
 *
 * 布局结构：[日期列 52dp] [时间线区域 20dp（竖线+节点）] [间距 4dp] [内容区]
 *
 * 竖线使用 drawBehind 在 padding 之前绘制，贯穿整个条目高度（包括 padding 区域），
 * 确保相邻条目间的竖线连续不中断。
 *
 * 节点与标题第一行垂直居中对齐，通过精确的 padding 计算：
 * - 标题 16sp 行高约 22dp，中心约在 11dp 处
 * - 条目 padding(top=8dp)，标题中心绝对位置 = 8 + 11 = 19dp
 * - 节点 8dp，半径 4dp，padding(top) = 19 - 4 = 15dp
 *
 * @param inspiration 灵感实体数据
 * @param tags 标签列表
 * @param imagePaths 图片路径列表
 * @param formattedTime 格式化后的时间字符串
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
    // 时间线竖线的X坐标：日期列52dp + 时间线区域一半10dp = 62dp
    val timelineLineX = 62.dp
    val timelineLineColor = Color(0xFFEEEEEE)
    val nodeColor = if (isPinnedItem) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            // drawBehind 在 padding 之前，绘制范围包括 padding 区域，
            // 确保竖线贯穿条目间的 padding 间隙，视觉上连续不中断
            .drawBehind {
                val x = timelineLineX.toPx()
                drawLine(
                    color = timelineLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧日期列（固定宽度52dp）
        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            if (isPinnedItem) {
                // 置顶项显示PushPin图标，与日期数字同一位置
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = Color(0xFFFF9A5C),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(20.dp)
                )
            } else if (showDate) {
                TimelineDateColumn(timestamp = inspiration.createdAt)
            }
        }

        // 时间线节点区域（固定宽度20dp，竖线已通过drawBehind绘制）
        Box(
            modifier = Modifier.width(20.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // 节点（圆点）- 与标题第一行垂直居中对齐
            // 标题16sp行高约22dp，中心约11dp；条目padding(top=8dp)
            // 节点中心需要在 8+11=19dp 处，节点半径4dp，padding = 19-4 = 15dp
            Box(
                modifier = Modifier
                    .padding(top = 15.dp)
                    .size(8.dp)
                    .background(color = nodeColor, shape = CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 右侧内容区
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // 第一行：标题（置顶图标 + 标题）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 置顶标识（标题前，Material图标）
                if (isPinnedItem) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // 标题
                Text(
                    text = inspiration.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            // 时间（在标题下方，左对齐）
            Text(
                text = formattedTime,
                fontSize = 12.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(top = 2.dp)
            )

            // 内容预览
            if (inspiration.content.isNotBlank()) {
                val plainContent = removeHtmlTags(inspiration.content)
                Text(
                    text = plainContent,
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // 标签 + 图片缩略图
            if (tags.isNotEmpty() || imagePaths.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // 标签
                    if (tags.isNotEmpty()) {
                        tags.take(2).forEach { tag ->
                            Text(
                                text = "#$tag",
                                fontSize = 12.sp,
                                color = UiColors.Primary,
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFFFF3E0),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        if (tags.size > 2) {
                            Text(
                                text = "+${tags.size - 2}",
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                modifier = Modifier
                                    .background(
                                        color = Color(0xFFF5F5F5),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // 图片缩略图
                    if (imagePaths.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            imagePaths.take(2).forEach { _ ->
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFFF5F5F5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🖼️",
                                        fontSize = 12.sp
                                    )
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
    }
}

/**
 * 时间线日期列组件
 * 显示年月和日期数字，整体居中对齐
 *
 * @param timestamp 时间戳
 * @param modifier 修饰符
 */
@Composable
private fun TimelineDateColumn(
    timestamp: Long,
    modifier: Modifier = Modifier
) {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // 年月
        Text(
            text = String.format("%04d.%02d", year, month),
            fontSize = 11.sp,
            color = Color(0xFF999999)
        )
        Spacer(modifier = Modifier.height(2.dp))
        // 日期数字
        Text(
            text = String.format("%02d", day),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
