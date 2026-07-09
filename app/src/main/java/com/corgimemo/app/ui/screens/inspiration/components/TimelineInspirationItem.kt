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
 * 时间线灵感条目组件（参考图规范版）
 *
 * 布局结构：
 * ```
 * [左侧时间栏 50dp] [间距 7dp] [节点 6dp] [间距 7dp] [右侧内容区]
 *   "2026.07"+"08"                                标题/时分时间/正文/标签
 * ```
 *
 * 字号体系（与 PRD 参考图一致）：
 * - 左侧时间栏：年月 12sp / 大号日期数字 24sp Medium
 * - 右侧内容区：标题 16sp Medium / 时分时间 11sp / 正文 14sp / 标签 11sp
 * - 中文字间距统一 +0.5sp
 *
 * 间距体系：
 * - 标题 → 时分时间：4dp
 * - 时分时间 → 正文：9dp
 * - 正文 → 标签：7dp
 * - 正文行高：21sp
 * - 标签内边距：水平 0.5dp / 垂直 0dp（紧凑型）
 * - 标签 lineHeight：11sp（等于 fontSize，最小行高）
 *
 * 横向边距：
 * - 时间栏宽度：50dp（精确匹配"2026.07"实际宽度，"2026.07"居中后右边距 = 0）
 * - 时间栏右边缘 → 节点左边缘：7dp
 * - 节点直径：6dp
 * - 节点右边缘 → 内容区左边缘：7dp
 * - 节点中心 X 坐标：60dp（=50+7+3）
 * - 内容区起始 X 坐标：70dp（=60+3+7）
 * - 时间栏内部 Column 水平居中：让"2026.07"和"08"视觉中心在同一垂直线
 * - **关键**：两个 7dp 间距视觉上相等（"2026.07"到节点 = 节点到内容区 = 7dp）
 *
 * 节点 Y 位置：固定 11dp，对齐"灵感标题"16sp Medium 中心，
 * 让"2026.07"、节点、"灵感标题"在第一行同一水平线上
 *
 * 节点显示规则：
 * - 节点（橙黄色圆点）每条灵感都显示（包括同一天内的非首条）
 * - 左侧"2026.07"+"08"日期栏仅在每天第一条灵感显示（showDate=true）
 *
 * 竖线连续性：
 * - 竖线起点 Y = -18dp（向上延伸 18dp），终点 Y = Item 高度
 * - 延伸 18dp 用于覆盖 LazyColumn.verticalArrangement = spacedBy(18.dp) 的间距
 * - 这样竖线在 Item 顶部之上 18dp 到 Item 底部范围内连续绘制，不被 Item 间距中断
 * - 由于 LazyColumn 顶部边界裁剪，第一个 Item 顶部之上 18dp 不会显示，但其他 Item 之间的间距被完整覆盖
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
    // ===== 横向布局常量 =====
    // 时间栏宽度 50dp：精确匹配"2026.07" 12sp 实际渲染宽度（约 50dp），
    // 让"2026.07"在 Column 内居中后右边距 = 0，时间栏右边缘紧贴"2026.07"右边缘。
    // 这样"2026.07" → 节点视觉距离 = 节点 → 内容区 = 7dp，两个间距视觉相等。
    val dateColumnWidth = 50.dp                 // 左侧时间栏宽度（v1.12 从 56dp 改为 50dp）
    val dateToNodeGap = 7.dp                    // 时间栏右边缘到节点左边缘（用户要求 7dp）
    val nodeDiameter = 6.dp                     // 节点直径（用户要求 6dp）
    val nodeToContentGap = 7.dp                 // 节点右边缘到内容区左边缘（用户要求 7dp）
    val nodeCenterX = dateColumnWidth + dateToNodeGap + nodeDiameter / 2  // 50 + 7 + 3 = 60dp
    val nodeRadius = nodeDiameter / 2                      // 3dp
    val contentStartX = nodeCenterX + nodeRadius + nodeToContentGap  // 60 + 3 + 7 = 70dp
    val timelineLineX = nodeCenterX                        // 竖线 X = 60dp

    // ===== 垂直间距常量 =====
    val titleToTimeGap = 4.dp                   // 标题 → 时分时间
    val timeToContentGap = 9.dp                 // 时分时间 → 正文
    val contentToTagGap = 7.dp                  // 正文 → 标签
    val tagToImageGap = 4.dp                    // 标签 → 图片
    val lazyColumnItemGap = 18.dp               // LazyColumn 相邻 Item 间距（与 InspirationScreen.kt 保持一致）
    val timelineLineOverlap = lazyColumnItemGap // 竖线向上延伸量，覆盖 Item 间 18dp 间距实现连续

    // ===== 中文字间距 =====
    val chineseLetterSpacing = 0.5.sp

    // ===== 节点 Y 位置：固定对齐"灵感标题"16sp Medium 中心 =====
    // 16sp Medium 默认 lineHeight ≈ 22dp，文字中心 y = 11dp
    // 这样节点与"2026.07"和"灵感标题"在第一行同一水平线上
    val nodeCenterY = 11.dp
    val nodeTopY = (nodeCenterY - nodeRadius).coerceAtLeast(0.dp)

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
            // 竖线贯通整个 Item 高度 + 向上延伸 18dp 覆盖 LazyColumn 间距，实现连续不中断
            .drawBehind {
                val x = timelineLineX.toPx()
                val startY = -timelineLineOverlap.toPx()  // 向上延伸 18dp
                drawLine(
                    color = timelineLineColor,
                    start = Offset(x, startY),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
    ) {
        // ========== 左侧时间栏（年月 + 大号日期）==========
        // Column 水平居中：让"2026.07"和"08"在同一垂直中线
        // - "2026.07" 12sp 宽度约 50dp，56dp 内居中
        // - "08" 24sp 宽度约 26dp，56dp 内居中
        // - 两者视觉中心都在 Column 宽度（56dp）的中点 X=28dp
        if (showDate) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(dateColumnWidth)
                    .align(Alignment.TopStart)
            ) {
                // 年月文本（12sp 灰色）
                Text(
                    text = String.format("%04d.%02d", getYear(inspiration.createdAt), getMonth(inspiration.createdAt)),
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    letterSpacing = chineseLetterSpacing
                )
                // 大号日期数字（20sp 黑色 Medium）
                Text(
                    text = String.format("%02d", getDay(inspiration.createdAt)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = chineseLetterSpacing
                )
            }
        }

        // ========== 节点（8dp 圆点，垂直对齐"灵感标题"中心）==========
        // 节点 Y 中心固定 11dp，与"2026.07"、"灵感标题"在第一行同一水平线
        // 节点始终显示：每条灵感都有时间节点（包括同一天内的非首条）
        Box(
            modifier = Modifier
                .offset(x = nodeCenterX - nodeRadius, y = nodeTopY)
                .size(nodeDiameter)
                .background(color = nodeColor, shape = CircleShape)
                .align(Alignment.TopStart)
        )

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
                    letterSpacing = chineseLetterSpacing
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
                            lineHeight = 11.sp,  // 压缩行高到 fontSize，减小标签上下间距
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
                    // 超出 3 个显示 "+N"
                    if (tags.size > 3) {
                        Text(
                            text = "+${tags.size - 3}",
                            fontSize = 11.sp,
                            lineHeight = 11.sp,  // 压缩行高到 fontSize，减小标签上下间距
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
