package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.DisplayDate
import kotlin.math.abs

/**
 * 置顶特殊日期卡片（2026-07-14 新增）
 *
 * 与普通 SpecialDateCard 平行的特殊视觉版：
 * - 左区（widthIn min=70dp）：标题 + 大数字(40sp Bold) + 单位(16sp, 紧跟数字右下)
 * - 中间（weight=1f）：emoji 大背景(80sp, alpha 0.4) + 单行详细倒数(14sp)
 * - 右侧：圆形头像 (48dp, surfaceVariant + emoji 居中)
 *
 * 单位显示规则：
 * - 昨天/今天/明天 → 直接显示文字（不显示数字与单位）
 * - ≤ 1 年 → "X 天"
 * - > 1 年 → "X 年 Y 天"
 *
 * @param date 展示数据（已确保 isPinned=true 且 !isArchived）
 * @param nowMs 当前时间戳（毫秒），由 SpecialDateScreen 顶层 ticker 驱动
 * @param isClickBlocked 左滑操作面板是否展开（true 时屏蔽卡片内点击）
 * @param onClick 卡片整体点击回调（进入编辑）
 * @param modifier 外部 Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedDateCard(
    date: DisplayDate,
    nowMs: Long,
    isClickBlocked: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. 时间差与颜色（与 SpecialDateCard 同样的规则）
    val diff = date.targetDate - nowMs
    val isFuture = diff >= 0
    val daysDiff = diff / 86_400_000L
    val daysAbs = abs(daysDiff)
    val timeColor = when {
        isFuture -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF7EC8A0)  // 正计时柔和绿
    }

    // 2. 数字 + 单位（"昨天/今天/明天" 特殊处理 + 1年分界）
    val (labelText: String, unitText: String?) = when (daysDiff) {
        0L -> "今天" to null
        1L -> "明天" to null
        -1L -> "昨天" to null
        else -> {
            when {
                daysAbs >= 365L -> {
                    val years = daysAbs / 365L
                    val rem = daysAbs % 365L
                    years.toString() to if (rem > 0L) "年 $rem 天" else "年"
                }
                else -> daysAbs.toString() to "天"
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isClickBlocked) Modifier
                else Modifier.combinedClickable(onClick = onClick)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左区：标题 + 大数字 + 单位
            Column(modifier = Modifier.widthIn(min = 70.dp)) {
                Text(
                    text = date.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = labelText,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = timeColor
                    )
                    if (unitText != null) {
                        Text(
                            text = unitText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = timeColor,
                            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            // 中间：emoji 大背景 + 单行倒数
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                // 装饰：emoji 大背景（半透）
                Text(
                    text = date.category.emoji,
                    fontSize = 80.sp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.alpha(0.4f)
                )
                // 倒数文本（后声明，绘制顺序自然在上层）
                Text(
                    text = formatDuration(abs(diff)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = timeColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右区：圆形头像
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.category.emoji,
                    fontSize = 24.sp
                )
            }
        }
    }
}
