package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.DayColor
import com.corgimemo.app.viewmodel.DisplayDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 特殊日期卡片组件
 * 展示单条特殊日期的倒计时/正计时信息，包括：
 * - 左侧圆形天数指示器（根据DayColor显示不同颜色）
 * - 中间区域：标题、日期、分类标签、关联提示
 * - 右侧缩略图（仅当hasImage为true时显示）
 * 支持点击和长按回调
 *
 * @param date 展示日期数据实体
 * @param onClick 点击回调（进入编辑页）
 * @param onLongClick 长按回调（显示操作菜单）
 * @param modifier 修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecialDateCard(
    date: DisplayDate,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    /** 根据DayColor获取对应的背景色和文字色 */
    val (dayBgColor, dayTextColor) = when (date.dayColor) {
        DayColor.RED -> Color(0xFFFFF0F0) to Color(0xFFE53935)
        DayColor.ORANGE -> Color(0xFFFFF3E0) to Color(0xFFFF9A5C)
        DayColor.GRAY -> Color(0xFFF5F5F5) to Color(0xFF999999)
        DayColor.GREEN -> Color(0xFFE8F5E9) to Color(0xFF4CAF50)
    }

    /** 格式化目标日期为"yyyy年MM月dd日"格式 */
    val formattedDate = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
        .format(Date(date.targetDate))

    /** 根据分类获取标签的背景色和文字色 */
    val (tagBgColor, tagTextColor) = when (date.category.name) {
        "BIRTHDAY" -> Color(0xFFFFF0F5) to Color(0xFFE91E63)
        "ANNIVERSARY" -> Color(0xFFFFF3E0) to Color(0xFFFF9A5C)
        "HOLIDAY" -> Color(0xFFE0F7FA) to Color(0xFF00BCD4)
        else -> Color(0xFFF3E5F5) to Color(0xFF9C27B0)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /** 左侧圆形天数指示器（64dp） */
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(dayBgColor),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    /** 天数数字（大字号） */
                    Text(
                        text = "${date.daysAbsolute}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = dayTextColor
                    )

                    /** 天数单位文本（"天后"/"天"/"天前"） */
                    Text(
                        text = date.displayText.replace("${date.daysAbsolute}", ""),
                        fontSize = 11.sp,
                        color = dayTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            /** 中间信息区域（标题、日期、分类标签等） */
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                /** 标题行（单行省略） */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = date.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                /** 格式化后的目标日期 */
                Text(
                    text = formattedDate,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                /** 底部行：分类标签 + 关联提示 */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    /** 分类标签药丸形状 */
                    Text(
                        text = "${date.category.emoji} ${date.category.displayName}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = tagTextColor,
                        modifier = Modifier
                            .background(
                                color = tagBgColor,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )

                    /** 关联提示（仅当存在关联时显示） */
                    if (date.hasRelation) {
                        Text(
                            text = "🔗 已关联",
                            fontSize = 10.sp,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            /** 右侧缩略图占位（40dp圆角8dp，仅当有图片时显示） */
            if (date.hasImage) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🖼️",
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}
