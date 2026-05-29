package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.ui.theme.UiColors

/**
 * @触发关联选择器弹窗
 * 按需求文档 11.6.1 设计，在 TextField 中输入 @ 时弹出内联搜索选择器
 *
 * @param visible 是否显示弹窗
 * @param searchQuery @ 后的搜索关键词
 * @param onDismiss 关闭弹窗回调
 * @param onCardSelected 选择卡片回调（参数：卡片类型、卡片ID、卡片标题）
 * @param searchCards 搜索方法
 * @param excludeIds 需要排除的卡片ID列表
 * @param sourceType 当前来源类型
 * @param sourceId 当前来源ID
 * @param offsetY 弹窗垂直偏移量
 */
@Composable
fun MentionTriggerPopup(
    visible: Boolean,
    searchQuery: String,
    onDismiss: () -> Unit,
    onCardSelected: (cardType: String, cardId: Long, cardTitle: String) -> Unit,
    searchCards: (query: String, callback: (List<CardSearchResult>) -> Unit) -> Unit,
    excludeIds: Set<Pair<String, Long>> = emptySet(),
    sourceType: String = "",
    sourceId: Long = 0,
    offsetY: Int = 0
) {
    var searchResults by remember { mutableStateOf<List<CardSearchResult>>(emptyList()) }

    LaunchedEffect(searchQuery) {
        if (visible) {
            searchCards(searchQuery) { results ->
                searchResults = results.filterNot { result ->
                    (result.cardType == sourceType && result.cardId == sourceId) ||
                    excludeIds.contains(result.cardType to result.cardId)
                }.take(5)
            }
        }
    }

    if (visible && searchResults.isNotEmpty()) {
        Popup(
            alignment = Alignment.BottomStart,
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            ),
            onDismissRequest = onDismiss
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔍",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = if (searchQuery.isBlank()) "搜索关联卡片..." else searchQuery,
                            fontSize = 13.sp,
                            color = if (searchQuery.isBlank()) Color(0xFF999999) else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(
                            items = searchResults,
                            key = { "${it.cardType}_${it.cardId}" }
                        ) { card ->
                            MentionSearchItem(
                                card = card,
                                onClick = {
                                    onCardSelected(card.cardType, card.cardId, card.title)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * @触发搜索结果项
 */
@Composable
private fun MentionSearchItem(
    card: CardSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (card.cardType) {
        "todo" -> Color(0xFFE3F2FD)
        "date" -> Color(0xFFFCE4EC)
        "inspiration" -> Color(0xFFFFF3E0)
        else -> Color(0xFFF5F5F5)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = card.typeIcon,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )

        Text(
            text = card.title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )

        Text(
            text = card.typeName,
            fontSize = 12.sp,
            color = UiColors.TextSecondary
        )
    }
}
