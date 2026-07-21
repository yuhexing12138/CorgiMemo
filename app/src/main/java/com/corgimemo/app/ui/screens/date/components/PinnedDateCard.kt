package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.ui.components.pressFeedback
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
 * @param cardScale 卡片缩放状态（v2026-07-21 新增，用于左滑按钮同步卡片"先缩后放"）
 *        - null（默认）：内部 remember 独立 state
 *        - 非 null：与外层 SwipeableTodoBox 共享，按钮完全跟随 pressFeedback 实时缩放
 */
@Composable
fun PinnedDateCard(
    date: DisplayDate,
    nowMs: Long,
    isClickBlocked: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    cardScale: MutableFloatState? = null
) {
    // 1. 是否已归档（2026-07-14 新增：已归档+置顶时卡片视觉降级）
    val isArchivedCard = date.isArchived

    // 2. 时间差与颜色
    //    - 已归档：灰色（与 SpecialDateCard 一致）
    //    - 未来：主题色 primary（倒计时）
    //    - 过去：柔和绿（正计时）
    val diff = date.targetDate - nowMs
    val isFuture = diff >= 0
    val daysDiff = diff / 86_400_000L
    val daysAbs = abs(daysDiff)
    val timeColor = when {
        isArchivedCard -> Color(0xFF999999)
        isFuture -> MaterialTheme.colorScheme.primary
        else -> Color(0xFF7EC8A0)  // 正计时柔和绿
    }

    // 3. 按压交互状态 + 缩放状态（v2026-07-21 新增）
    //    - interactionSource：保留参数以兼容 PressFeedback API
    //    - effectiveCardScale：与外层 SwipeableTodoBox 共享（如果传入），或内部 remember
    //    - pressFeedback 内部同步修改 scale 目标值，Composable 层 animateFloatAsState 自动动画过渡
    val interactionSource = remember { MutableInteractionSource() }
    val internalCardScale = remember { mutableFloatStateOf(1f) }
    val effectiveCardScale: MutableFloatState = cardScale ?: internalCardScale

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
        // v2026-07-21 改造：把 combinedClickable 替换为 pressFeedback
        // 原因：参考待办页 TodoListItem 的实现，按下时卡片缩小到 0.94f（与左滑按钮同步缩放），
        //       抬起/移动超过 touchSlop 时恢复 1f（200ms 缓慢回弹）。
        // Modifier 顺序：fillMaxWidth() -> pressFeedback
        // - pressFeedback 内部已包含 graphicsLayer，无需外层再包
        // - isClickBlocked 为 true 时禁用 pressFeedback（左滑面板展开时不响应按压）
        // - onTap 替代 combinedClickable.onClick：< 500ms 抬起触发 onClick
        modifier = modifier
            .fillMaxWidth()
            .pressFeedback(
                interactionSource = interactionSource,
                scale = effectiveCardScale,
                enabled = !isClickBlocked,
                onTap = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                // 2026-07-14 新增：已归档+置顶的卡片整体降权（仅内容层）
                // 与 SpecialDateCard 保持一致的设计：alpha 应用在内部 Row，
                // 避免 SwipeableTodoBox 的左滑按钮区域被 Card 颜色污染
                .then(if (isArchivedCard) Modifier.alpha(0.6f) else Modifier),
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
                // 装饰：柯基躺姿图背景（2026-07-14 从 emoji 改为 Image 资源，alpha 调整可靠）
                // 使用 corgi_lie_3frames_01.png（躺着的柯基）作为装饰背景
                // ContentScale.Fit 保持原始纵横比，alpha(0.15f) 让背景非常淡
                // 2026-07-14 二次调整：固定 60dp 尺寸而非 fillMaxSize()，避免撑大 Card 高度
                Image(
                    painter = painterResource(id = R.drawable.corgi_lie_3frames_01),
                    contentDescription = null,  // 装饰性图片，无需无障碍描述
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(60.dp)
                        .alpha(0.15f)
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

            // 右区：圆形头像（2026-07-14 适度增大 48dp → 56dp，emoji 24sp → 28sp）
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
        }
    }
}
