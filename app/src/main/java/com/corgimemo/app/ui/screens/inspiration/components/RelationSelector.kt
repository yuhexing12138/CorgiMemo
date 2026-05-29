package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.ui.components.CardLinkSelectorDialog
import com.corgimemo.app.ui.theme.UiColors

/**
 * 关联选择器组件（统一版本）
 * 用于管理卡片（待办/灵感/日期）与其他卡片的关联关系
 *
 * @param relations 当前已有关联关系列表
 * @param onRelationAdd 添加关联回调函数（参数：目标类型、目标ID）
 * @param onRelationDelete 删除关联回调函数（参数：关联ID）
 * @param searchCards 搜索卡片方法
 * @param sourceType 当前来源类型（用于排除自身）
 * @param sourceId 当前来源ID（用于排除自身）
 * @param cardRelationRepository 关联仓库（用于获取卡片标题）
 * @param modifier 修饰符
 */
@Composable
fun RelationSelector(
    relations: List<CardRelation>,
    onRelationAdd: (targetType: String, targetId: Long) -> Unit,
    onRelationDelete: (relationId: Long) -> Unit,
    searchCards: (query: String, callback: (List<CardSearchResult>) -> Unit) -> Unit,
    sourceType: String = "",
    sourceId: Long = 0,
    cardRelationRepository: CardRelationRepository? = null,
    modifier: Modifier = Modifier
) {
    var showSelectorDialog by remember { mutableStateOf(false) }
    val relationTitles = remember(relations) { mutableMapOf<Pair<String, Long>, String>() }

    LaunchedEffect(relations) {
        cardRelationRepository?.let { repo ->
            relations.forEach { relation ->
                val key = relation.targetType to relation.targetId
                if (!relationTitles.containsKey(key)) {
                    val title = repo.getCardTitle(relation.targetType, relation.targetId)
                    relationTitles[key] = title ?: "已删除"
                }
            }
        }
    }

    val isMaxRelations = relations.size >= CardRelationRepository.MAX_RELATIONS_PER_CARD

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🔗 已关联",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (relations.isNotEmpty()) {
                Text(
                    text = "${relations.size}/${CardRelationRepository.MAX_RELATIONS_PER_CARD}",
                    fontSize = 12.sp,
                    color = if (isMaxRelations) Color(0xFFFF8A80) else Color(0xFF999999),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (relations.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minOf((relations.size * 60).dp, 200.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = relations, key = { it.id }) { relation ->
                    val title = relationTitles[relation.targetType to relation.targetId]
                        ?: "${getTypeName(relation.targetType)} #${relation.targetId}"
                    RelationItem(
                        relation = relation,
                        title = title,
                        onDelete = {
                            onRelationDelete(relation.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Text(
                text = "暂无关联，可以添加待办、日期或其他灵感作为关联",
                fontSize = 13.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isMaxRelations) Color(0xFFF5F5F5)
                    else UiColors.Primary.copy(alpha = 0.1f)
                )
                .clickable(enabled = !isMaxRelations) { showSelectorDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加关联",
                tint = if (isMaxRelations) Color(0xFFCCCCCC) else UiColors.Primary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = if (isMaxRelations) "已达关联上限" else "添加关联",
                fontSize = 14.sp,
                color = if (isMaxRelations) Color(0xFFCCCCCC) else UiColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }

    if (showSelectorDialog) {
        val excludeIds = relations.map { it.targetType to it.targetId }.toSet()
        CardLinkSelectorDialog(
            onDismiss = { showSelectorDialog = false },
            onCardSelected = { cardType, cardId, cardTitle ->
                onRelationAdd(cardType, cardId)
                showSelectorDialog = false
            },
            searchCards = searchCards,
            excludeIds = excludeIds,
            sourceType = sourceType,
            sourceId = sourceId
        )
    }
}

/**
 * 关联项组件
 * 显示单个已关联的卡片信息
 */
@Composable
private fun RelationItem(
    relation: CardRelation,
    title: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, bgColor) = when (relation.targetType) {
        "todo" -> "📝" to Color(0xFFE3F2FD)
        "date" -> "📅" to Color(0xFFFCE4EC)
        "inspiration" -> "💡" to Color(0xFFFFF3E0)
        else -> "📎" to Color(0xFFF5F5F5)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 10.dp)
        )

        Text(
            text = title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.05f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除关联",
                tint = Color(0xFF666666),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 获取类型中文名
 */
private fun getTypeName(type: String): String = when (type) {
    "todo" -> "待办事项"
    "date" -> "特殊日期"
    "inspiration" -> "灵感记录"
    else -> "未知类型"
}
