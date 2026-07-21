package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 关联卡片预览 Dialog（按原型完整重写 v2026-07-21）
 *
 * 点击 [LinkedCardsRow] 中的 Chip 时弹出，按卡片类型差异化展示关键信息：
 * - 📝 待办：截止时间(橙高亮) + 分类 + 优先级 + 子任务进度条
 * - 💡 灵感：创建时间 + 内容预览(3行渐变遮罩) + 图片缩略图(最多3张+N) + 标签
 * - 📅 日期：目标日期(粉红) + 倒计时大数字(橙色渐变背景) + 备注
 *
 * 共同元素：
 * - 头部：类型图标(32x32 圆角 8dp 带背景色) + 标题(14sp SemiBold) + 类型标签(10sp hint) + × 关闭
 * - 底部：取消关联(红边框透明) / 跳转详情(橙底白字) / 关闭(灰底)
 *
 * 颜色规范（来自 docs/superpowers/specs/UI设计规范.md）：
 * - 主色 #FF9A5C，主色浅 #FFE4CC
 * - 页面背景 #F8F6F3（用于内容预览块/缩略图占位）
 * - 卡片背景 #FFFFFF
 * - 主文字 #2D2D2D，次要文字 #666666，提示文字 #999999
 * - 分割线 #EEEEEE
 * - 优先级：高 #FF8A80 / 中 #FFB74D / 低 #90CAF9 / 无 #C8E6C9
 * - 类型色：todo #E3F2FD+#1976D2 / inspiration #FFF3E0+#E65100 / date #FCE4EC+#C2185B
 * - 倒计时渐变：#FFF5EE → #FFE4D6 + 1dp 主色边框
 * - 取消关联：边框 #FCA5A5，文字 #DC2626
 *
 * @param relation 关联实体（提供 targetType / targetId / id）
 * @param cardDetail 卡片详情（null 表示加载中或未加载）
 * @param isLoading 是否正在加载详情
 * @param onDismiss 关闭回调
 * @param onUnlink 取消关联回调，参数为关联ID
 * @param onJumpToDetail 跳转详情回调，参数为 (cardType, cardId)
 */
@Composable
fun LinkedCardPreviewDialog(
    relation: CardRelation,
    cardDetail: CardDetail?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onUnlink: (relationId: Long) -> Unit,
    onJumpToDetail: (cardType: String, cardId: Long) -> Unit
) {
    // ========== 类型相关视觉常量 ==========
    // 背景色 + 文字色 + 图标 + 中文名
    val typeBg = when (relation.targetType) {
        "todo" -> Color(0xFFE3F2FD)
        "inspiration" -> Color(0xFFFFF3E0)
        "date" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF5F5F5)
    }
    val typeIconText = when (relation.targetType) {
        "todo" -> "📝"
        "inspiration" -> "💡"
        "date" -> "📅"
        else -> "📎"
    }
    val typeLabel = when (relation.targetType) {
        "todo" -> "📝 待办"
        "inspiration" -> "💡 灵感"
        "date" -> "📅 日期"
        else -> "未知"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = true)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))   // 弹窗规范：24dp
                .background(Color(0xFFFFFFFF))      // 卡片背景
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ============ 头部 ============
                DialogHeader(
                    typeBg = typeBg,
                    typeIconText = typeIconText,
                    title = cardDetail?.title,
                    typeLabel = typeLabel,
                    onClose = onDismiss
                )

                // ============ 内容区 ============
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when {
                        isLoading -> {
                            // 加载中：居中进度指示器
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFFFF9A5C)
                                )
                            }
                        }
                        cardDetail == null -> {
                            // 加载失败/卡片已删除
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "该卡片已不存在",
                                    fontSize = 13.sp,
                                    color = Color(0xFF999999)
                                )
                            }
                        }
                        else -> {
                            // 按类型差异化渲染
                            when (cardDetail) {
                                is CardDetail.TodoDetail -> TodoDetailBody(cardDetail)
                                is CardDetail.InspirationDetail -> InspirationDetailBody(cardDetail)
                                is CardDetail.DateDetail -> DateDetailBody(cardDetail)
                            }
                        }
                    }
                }

                // ============ 底部按钮 ============
                DialogFooter(
                    onUnlink = { onUnlink(relation.id) },
                    onJumpToDetail = { onJumpToDetail(relation.targetType, relation.targetId) },
                    onClose = onDismiss
                )
            }
        }
    }
}

// ============================================================================
// 头部
// ============================================================================

/**
 * Dialog 头部：类型图标 + 标题 + 类型标签 + × 关闭
 *
 * @param typeBg 类型图标背景色
 * @param typeIconText 类型图标 emoji
 * @param title 卡片标题（加载中时为 null，显示占位）
 * @param typeLabel 类型标签文字（如 "📝 待办"）
 * @param onClose 关闭回调
 */
@Composable
private fun DialogHeader(
    typeBg: Color,
    typeIconText: String,
    title: String?,
    typeLabel: String,
    onClose: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型图标（32x32 圆角 8dp 带背景色）
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = typeIconText,
                    fontSize = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // 标题 + 类型标签
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title ?: "加载中…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = typeLabel,
                    fontSize = 10.sp,
                    color = Color(0xFF999999)
                )
            }
            // × 关闭按钮（24x24）
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "×",
                    fontSize = 20.sp,
                    color = Color(0xFF999999)
                )
            }
        }
        // 头部下方分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE))
        )
    }
}

// ============================================================================
// 待办详情 Body
// ============================================================================

/**
 * 待办详情 Body：截止时间(橙高亮) + 分类 + 优先级 + 子任务进度条
 */
@Composable
private fun TodoDetailBody(detail: CardDetail.TodoDetail) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 截止时间
        if (detail.dueDate != null) {
            InfoRow(
                label = "截止时间",
                value = formatDueDate(detail.dueDate),
                valueColor = Color(0xFFFF9A5C),       // 主色高亮
                valueBold = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        // 分类
        InfoRow(
            label = "分类",
            value = "📁 ${detail.categoryName ?: "未分类"}",
            valueColor = Color(0xFF2D2D2D)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 优先级
        val (priorityText, priorityColor) = priorityDisplay(detail.priority)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "优先级",
                fontSize = 12.sp,
                color = Color(0xFF999999),
                modifier = Modifier.width(60.dp)
            )
            // 圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(priorityColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = priorityText,
                fontSize = 12.sp,
                color = Color(0xFF2D2D2D)
            )
        }
        // 子任务进度（仅当有子任务时显示）
        if (detail.subTaskTotal > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "子任务",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.width(60.dp)
                )
                // 进度条
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFEEEEEE))
                ) {
                    val progress = if (detail.subTaskTotal > 0) {
                        detail.subTaskCompleted.toFloat() / detail.subTaskTotal
                    } else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFFF9A5C))
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${detail.subTaskCompleted}/${detail.subTaskTotal}",
                    fontSize = 10.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}

/**
 * 优先级显示文字 + 颜色映射
 *
 * 项目实际定义（参考 NotificationHelper.kt）：
 * - 0 → 无优先级（#C8E6C9 浅绿）
 * - 1 → 低优先级（#90CAF9 柔和蓝）
 * - 2 → 中优先级（#FFB74D 柔和橙）
 * - 3 → 高优先级（#FF8A80 柔和红）
 *
 * @return (文字, 颜色)
 */
private fun priorityDisplay(priority: Int): Pair<String, Color> {
    return when (priority) {
        0 -> "无" to Color(0xFFC8E6C9)
        1 -> "低" to Color(0xFF90CAF9)
        2 -> "中" to Color(0xFFFFB74D)
        3 -> "高" to Color(0xFFFF8A80)
        else -> "无" to Color(0xFFC8E6C9)
    }
}

/**
 * 格式化截止时间戳为可读字符串
 *
 * - 今天 → "今天 HH:mm"
 * - 明天 → "明天 HH:mm"
 * - 昨天 → "昨天 HH:mm"
 * - 其他 → "M月d日 HH:mm"
 */
private fun formatDueDate(timestamp: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    val isSameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isSameDay) return "今天 ${timeFmt.format(Date(timestamp))}"

    now.add(Calendar.DAY_OF_YEAR, 1)
    val isTomorrow = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isTomorrow) return "明天 ${timeFmt.format(Date(timestamp))}"

    now.add(Calendar.DAY_OF_YEAR, -2)
    val isYesterday = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天 ${timeFmt.format(Date(timestamp))}"

    val fmt = SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())
    return fmt.format(Date(timestamp))
}

// ============================================================================
// 灵感详情 Body
// ============================================================================

/**
 * 灵感详情 Body：创建时间 + 内容预览(3行渐变遮罩) + 图片缩略图(最多3+N) + 标签
 */
@Composable
private fun InspirationDetailBody(detail: CardDetail.InspirationDetail) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 创建时间
        InfoRow(
            label = "创建时间",
            value = formatDate(detail.createdAt, "M月d日 HH:mm"),
            valueColor = Color(0xFF2D2D2D)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 内容预览（3行 + 渐变遮罩）
        if (detail.content.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF8F6F3))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = detail.content,
                    fontSize = 11.sp,
                    color = Color(0xFF666666),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 图片缩略图（最多显示 3 张真实图片 + "+N"）
        if (detail.imagePaths.isNotEmpty()) {
            val context = LocalContext.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 最多显示 3 张真实缩略图（Coil 加载本地路径）
                detail.imagePaths.take(3).forEach { path ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(path)
                            .crossfade(true)
                            .build(),
                        contentDescription = "灵感图片缩略图",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF8F6F3))
                    )
                }
                // 多余图片显示 +N 占位
                if (detail.imagePaths.size > 3) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF8F6F3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${detail.imagePaths.size - 3}",
                            fontSize = 10.sp,
                            color = Color(0xFF999999)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 标签（#标签 形式，最多 5 个）
        if (detail.tags.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                detail.tags.take(5).forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8F6F3))
                            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            fontSize = 10.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 日期详情 Body
// ============================================================================

/**
 * 日期详情 Body：目标日期(粉红) + 倒计时大数字(橙色渐变背景) + 备注
 */
@Composable
private fun DateDetailBody(detail: CardDetail.DateDetail) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 目标日期（粉红色高亮）
        InfoRow(
            label = "目标日期",
            value = formatDate(detail.targetDate, "yyyy年M月d日"),
            valueColor = Color(0xFFC2185B),         // date 类型文字色
            valueBold = true
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 倒计时大数字（橙色渐变背景 + 1dp 主色边框）
        val (days, isPast) = computeDaysFromNow(detail.targetDate)
        CountdownBox(days = days, isPast = isPast)

        // 备注
        if (detail.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(
                label = "备注",
                value = detail.content,
                valueColor = Color(0xFF2D2D2D)
            )
        }
    }
}

/**
 * 倒计时展示盒子
 *
 * 视觉：橙色渐变背景 #FFF5EE → #FFE4D6 + 1dp 主色边框 + 圆角 10dp
 * - 大数字 30sp Bold 主色 #FF9A5C
 * - 单位"天" 12sp #E65100
 * - 标签"剩余倒计时"/"已过天数" 10sp 次要文字
 *
 * @param days 天数（正数）
 * @param isPast 是否为过去日期（true=已过 N 天，false=还有 N 天）
 */
@Composable
private fun CountdownBox(days: Int, isPast: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFFF9A5C), RoundedCornerShape(10.dp))
            .background(
                brush = Brush.linearGradient(
                    listOf(Color(0xFFFFF5EE), Color(0xFFFFE4D6))
                )
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = days.toString(),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9A5C),
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "天",
                    fontSize = 12.sp,
                    color = Color(0xFFE65100),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isPast) "已过天数" else "剩余倒计时",
                fontSize = 10.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

/**
 * 计算目标日期与今天的天数差
 *
 * @return (绝对天数, isPast)
 * - isPast=true 表示目标日期已过，days 为已过天数
 * - isPast=false 表示目标日期未到，days 为剩余天数
 */
private fun computeDaysFromNow(targetDate: Long): Pair<Int, Boolean> {
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val target = Calendar.getInstance().apply {
        timeInMillis = targetDate
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val diffMs = target.timeInMillis - now.timeInMillis
    val days = (diffMs / (24 * 60 * 60 * 1000)).toInt()
    return Pair(kotlin.math.abs(days), days < 0)
}

// ============================================================================
// 通用 InfoRow 组件
// ============================================================================

/**
 * 信息行：左侧 60dp 灰色标签 + 右侧主文字色值
 *
 * @param label 标签文字（如 "截止时间"）
 * @param value 值文字
 * @param valueColor 值文字颜色
 * @param valueBold 值是否粗体
 */
@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color(0xFF2D2D2D),
    valueBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFF999999),
            modifier = Modifier.width(60.dp)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = valueColor,
            fontWeight = if (valueBold) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 格式化时间戳
 * @param timestamp 毫秒时间戳
 * @param pattern SimpleDateFormat 模式
 */
private fun formatDate(timestamp: Long, pattern: String): String {
    val fmt = SimpleDateFormat(pattern, Locale.getDefault())
    return fmt.format(Date(timestamp))
}

// ============================================================================
// 底部按钮
// ============================================================================

/**
 * 底部三按钮：取消关联(红边框) / 跳转详情(橙底白字) / 关闭(灰底)
 */
@Composable
private fun DialogFooter(
    onUnlink: () -> Unit,
    onJumpToDetail: () -> Unit,
    onClose: () -> Unit
) {
    Column {
        // 顶部分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 取消关联（红边框透明底）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(10.dp))
                    .clickable { onUnlink() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "取消关联",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )
            }
            // 跳转详情（橙底白字）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF9A5C))
                    .clickable { onJumpToDetail() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "跳转详情",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            // 关闭（灰底）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF8F6F3))
                    .clickable { onClose() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "关闭",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }
}
