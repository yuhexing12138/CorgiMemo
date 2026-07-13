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
import com.corgimemo.app.viewmodel.DisplayDate
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
 * - 圆形图片区（48dp，emoji 占位）
 * - Spacer 12dp
 * - 内容区（weight=1f）：标题行 + 时间信息行（移除原"还有/已经/已归档 + 日期"行，简化卡片高度）
 * - Spacer 8dp
 * - 右侧大数字（22sp Bold，剩余/已过天数）
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
    // 1. 是否处于"已归档"分组（决定整体 alpha 0.6 降权）
    // 2026-07-13 重构：改用 isArchived 字段判断（原为 groupType == EXPIRED），
    // 原因：业务侧"已归档"分组（EXPIRED）的语义已变为"归档卡"而非"已过期卡"，
    // 应当跟随 isArchived 字段独立判断，与分组解耦（未来分组规则再变也不会误判降权）
    val isArchivedGroup = date.isArchived

    // 2. 时间差（毫秒），diff >= 0 表示目标日期未到（未来），否则已过
    val diff = date.targetDate - nowMs

    // 3. 时间信息颜色：
    //    - 已归档 → 灰色（#999999）
    //    - 未来（倒计时）→ 主色（暖橙）
    //    - 已开始（正计时）→ 柔和绿（#7EC8A0）
    val isFuture = diff >= 0
    val timeColor = when {
        isArchivedGroup -> Color(0xFF999999)
        isFuture -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF7EC8A0)
    }

    // 4. 天数计算：
    //    - daysDiff：带符号的天数（>0 未来，=0 今天，<0 过去）
    //    - daysAbs：绝对天数（用于大于 1 天时显示数字）
    val daysDiff = diff / 86_400_000L
    val daysAbs = abs(daysDiff)

    // 5. 右侧大数字文案：
    //    - daysDiff == 0L → "今天"（不显示数字与单位）
    //    - daysDiff == 1L → "明天"
    //    - daysDiff == -1L → "昨天"
    //    - 其他 → daysAbs 的字符串形式
    val daysLabel = when (daysDiff) {
        0L -> "今天"
        1L -> "明天"
        -1L -> "昨天"
        else -> daysAbs.toString()
    }

    // 6. 右侧单位文案（仅 daysAbs > 1 时显示）：
    //    - 未来（倒计时）→ "剩余天数"
    //    - 过去/今天（正计时）→ "已过天数"
    //    - 已归档卡片按 daysDiff 决定（视觉降权后仍能传达剩余/已过信息）
    //    - 特殊日期（昨天/今天/明天）→ null（不显示单位）
    val daysUnit = when {
        daysDiff in listOf(-1L, 0L, 1L) -> null
        isFuture -> "剩余天数"
        else -> "已过天数"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            // 关键：alpha 不再应用在 Card 上，而是应用在 Card 内部的 Row（内容层）。
            // 原因：SwipeableTodoBox 将左滑按钮区域绘制在 Card 之后（同一 layout 区域），
            //       若 alpha 应用在 Card 上，Card 半透会导致左滑按钮被 Card 颜色污染（按钮"透过"卡片显示）。
            //       把 alpha 下沉到内部 Row 后，Card 背景仍保持完全不透明，左滑按钮正常显示。
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
                // 2026-07-13：减小外层 padding（16→12），同步降低卡片高度
                .padding(12.dp)
                // 已归档分组整体降权（仅内容层），左滑按钮区域仍保持完全不透明
                .then(if (isArchivedGroup) Modifier.alpha(0.6f) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 圆形图片区（48dp，emoji 占位）
            // 2026-07-13：图片尺寸从 56dp 减小到 48dp，同步 emoji 字号 28sp→24sp
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

            Spacer(Modifier.width(12.dp))

            // 2. 内容区（weight=1f）
            // 2026-07-13：移除原"还有/已经/已归档 + 日期"行，简化为两行结构（标题 + 时间）
            Column(modifier = Modifier.weight(1f)) {
                // 2.1 标题行：置顶图标 + title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (date.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "置顶",
                            // 已归档卡片的置顶图标使用灰色（与卡片整体降权风格一致）
                            tint = if (isArchivedGroup) Color(0xFF999999) else MaterialTheme.colorScheme.primary,
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
            }

            Spacer(Modifier.width(8.dp))

            // 3. 右侧大数字+单位两行布局
            // 2026-07-13 优化：原仅显示大数字，现按用户需求拆分为两行：
            //   - 上行：昨天/今天/明天 或 天数数字（22sp Bold）
            //   - 下行：剩余天数 / 已过天数（11sp，与上行同色，特殊日期时不显示）
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = daysLabel,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = timeColor
                )
                // 特殊日期（昨天/今天/明天）不显示单位标签
                daysUnit?.let { unit ->
                    Text(
                        text = unit,
                        fontSize = 11.sp,
                        color = timeColor
                    )
                }
            }
        }
    }
}
