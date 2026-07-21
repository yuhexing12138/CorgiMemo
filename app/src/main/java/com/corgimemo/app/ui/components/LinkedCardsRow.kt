package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CardRelation

/**
 * 已关联卡片横向 Chip 流显示区
 *
 * 显示在 TodoGroupContainer 的 Chips 行和按钮行之间，
 * 以横向自动换行的 Chip 流形式展示该分组已关联的所有卡片。
 *
 * 每个 Chip 内容：类型图标 + 标题 + × 删除按钮
 * - 点击 Chip（非 × 区域）→ 弹出预览 Dialog
 * - 点击 × → 删除该关联
 *
 * Chip 颜色与 MentionTriggerPopup.MentionSearchItem 保持一致：
 * - todo: 背景 #E3F2FD / 文字 #1976D2
 * - inspiration: 背景 #FFF3E0 / 文字 #E65100
 * - date: 背景 #FCE4EC / 文字 #C2185B
 *
 * @param relations 当前分组的关联列表
 * @param groupId 当前分组ID（用于删除回调定位）
 * @param relationTitles 关联ID → 标题的映射（由 ViewModel 异步加载并缓存）
 * @param onAddClick 点击 ＋ 添加按钮的回调
 * @param onChipClick 点击 Chip（非 × 区域）的回调，参数为关联实体
 * @param onChipDelete 点击 × 删除按钮的回调，参数为 (relationId, groupId)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkedCardsRow(
    relations: List<CardRelation>,
    groupId: Int,
    relationTitles: Map<Long, String>,
    onAddClick: () -> Unit,
    onChipClick: (CardRelation) -> Unit,
    onChipDelete: (relationId: Long, groupId: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 标题行：🔗 已关联 [N] + ＋ 添加按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "🔗 已关联 ${relations.size}",
                fontSize = 12.sp,
                color = Color(0xFF999999),
                fontWeight = FontWeight.Normal
            )
            Spacer(modifier = Modifier.weight(1f))
            // ＋ 添加按钮
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFFFF5EE))
                    .border(1.dp, Color(0xFFFF9A5C), RoundedCornerShape(12.dp))
                    .clickable { onAddClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "＋",
                    fontSize = 14.sp,
                    color = Color(0xFFFF9A5C),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Chip 流（使用 FlowRow 自动换行）
        if (relations.isNotEmpty()) {
            // 使用 Compose 1.9+ 的 FlowRow（ExperimentalLayoutApi）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                relations.forEach { relation ->
                    LinkedCardChip(
                        relation = relation,
                        title = relationTitles[relation.id] ?: "加载中…",
                        onChipClick = { onChipClick(relation) },
                        onChipDelete = { onChipDelete(relation.id, groupId) }
                    )
                }
            }
        }
    }
}

/**
 * 单个关联 Chip
 *
 * 颜色按 targetType 区分：
 * - todo: 浅蓝底/深蓝字
 * - inspiration: 浅橙底/深橙字
 * - date: 浅粉底/深粉字
 */
@Composable
private fun LinkedCardChip(
    relation: CardRelation,
    title: String,
    onChipClick: () -> Unit,
    onChipDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 按类型确定 Chip 颜色（与 MentionTriggerPopup.MentionSearchItem 一致）
    val bgColor = when (relation.targetType) {
        "todo" -> Color(0xFFE3F2FD)
        "inspiration" -> Color(0xFFFFF3E0)
        "date" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF5F5F5)
    }
    val textColor = when (relation.targetType) {
        "todo" -> Color(0xFF1976D2)
        "inspiration" -> Color(0xFFE65100)
        "date" -> Color(0xFFC2185B)
        else -> Color(0xFF666666)
    }
    val typeIcon = when (relation.targetType) {
        "todo" -> "📝"
        "inspiration" -> "💡"
        "date" -> "📅"
        else -> "📎"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable { onChipClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = typeIcon,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            color = textColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // × 删除按钮
        Text(
            text = "×",
            fontSize = 14.sp,
            color = textColor.copy(alpha = 0.6f),
            modifier = Modifier.clickable { onChipDelete() }
        )
    }
}
