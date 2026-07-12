package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.corgimemo.app.viewmodel.DateGroup
import com.corgimemo.app.viewmodel.DisplayDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 把毫秒差值格式化为 "X年 X天 X时 X分 X秒"，从最大有效单位逐级显示到秒。
 *
 * 例：
 * - 366_1000ms → "1年 1天 0时 0分 1秒"
 * - 5天3时     → "5天 3时 0分 0秒"
 * - 不足1时   → "4分 5秒"
 * - =0        → "0秒"（兜底）
 *
 * 注：使用"365天=1年"简化计算（不区分闰年），与现有 calculateDaysRemaining 一致。
 *
 * @param millis 时间差毫秒（负数会被 coerce 为 0）
 * @return 格式化字符串
 */
internal fun formatDuration(millis: Long): String {
    val totalSec = (millis / 1000).coerceAtLeast(0L)
    val sec = (totalSec % 60).toInt()
    val min = (totalSec / 60 % 60).toInt()
    val hr = (totalSec / 3600 % 24).toInt()
    val day = (totalSec / 86400 % 365).toInt()
    val yr = (totalSec / (86400 * 365)).toInt()
    return when {
        yr > 0 -> "$yr 年 $day 天 $hr 时 $min 分 $sec 秒"
        day > 0 -> "$day 天 $hr 时 $min 分 $sec 秒"
        hr > 0 -> "$hr 时 $min 分 $sec 秒"
        min > 0 -> "$min 分 $sec 秒"
        else -> "$sec 秒"
    }
}

/**
 * 特殊日期卡片（重构版）
 *
 * 视觉结构（左→右）：
 * - 圆形图片区（56dp，emoji 占位）
 * - Spacer 12dp
 * - 内容区（weight=1f）：标题行 + 时间信息行 + 日期标签行
 * - Spacer 8dp
 * - 右侧大数字（24sp Bold，剩余/已过天数）
 *
 * 左滑操作由父级 SpecialDateScreen 用 SwipeableTodoBox 包裹注入，
 * 本组件只关心视觉与渲染（职责单一）。
 *
 * @param date 展示数据
 * @param nowMs 当前时间戳（毫秒），由 SpecialDateScreen 顶层 ticker 驱动
 * @param isClickBlocked 左滑操作面板是否展开（true 时屏蔽卡片内的点击/长按）
 * @param onClick 卡片整体点击回调（进入编辑）
 * @param onLongClick 卡片整体长按回调（预留）
 * @param modifier 外部 Modifier
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecialDateCard(
    date: DisplayDate,
    nowMs: Long,
    isClickBlocked: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 1. 是否处于"已过期"分组（决定整体 alpha 0.6 降权）
    val isExpiredGroup = date.groupType == DateGroup.EXPIRED

    // 2. 时间差（毫秒），diff >= 0 表示目标日期未到（未来），否则已过
    val diff = date.targetDate - nowMs
    val isFuture = diff >= 0

    // 3. 时间信息颜色：
    //    - EXPIRED 分组 → 灰色（#999999）
    //    - 未来（倒计时）→ 主色（暖橙）
    //    - 已开始（正计时）→ 柔和绿（#7EC8A0）
    val timeColor = when {
        isExpiredGroup -> Color(0xFF999999)
        isFuture -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF7EC8A0)
    }

    // 4. 日期字符串（中文格式：yyyy年M月d日）
    val formattedDate = SimpleDateFormat("yyyy年M月d日", Locale.getDefault())
        .format(Date(date.targetDate))

    // 5. 剩余/已过天数（取整除一天）
    val daysAbs = abs(diff) / 86_400_000L

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            // 已过期分组整体降权
            .then(if (isExpiredGroup) Modifier.alpha(0.6f) else Modifier)
            // 左滑操作面板展开时屏蔽卡片内的点击/长按
            .then(
                if (isClickBlocked) Modifier
                else Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 圆形图片区（56dp，emoji 占位）
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = date.category.emoji,
                    fontSize = 28.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            // 2. 内容区（weight=1f）
            Column(modifier = Modifier.weight(1f)) {
                // 2.1 标题行：置顶图标 + title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (date.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "置顶",
                            tint = if (isExpiredGroup) Color(0xFF999999) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = date.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.size(4.dp))

                // 2.2 时间信息行（动态格式：年/天/时/分/秒）
                Text(
                    text = formatDuration(abs(diff)),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = timeColor
                )

                Spacer(Modifier.size(4.dp))

                // 2.3 日期标签行：还有/已经/已过 + yyyy年M月d日
                Text(
                    text = "${if (isFuture) "还有" else if (isExpiredGroup) "已过" else "已经"} $formattedDate",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(8.dp))

            // 3. 右侧大数字（剩余/已过天数）
            Text(
                text = daysAbs.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = timeColor
            )
        }
    }
}
