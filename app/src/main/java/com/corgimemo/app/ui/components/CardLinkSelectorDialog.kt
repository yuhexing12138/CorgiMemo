package com.corgimemo.app.ui.components

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.ui.theme.UiColors

/**
 * 卡片关联搜索选择弹窗
 * 按需求文档 11.6.2 设计，支持搜索和按类型分组选择卡片
 *
 * @param onDismiss 关闭弹窗回调
 * @param onCardSelected 选择卡片回调（参数：卡片类型、卡片ID、卡片标题）
 * @param searchCards 搜索方法（参数：搜索关键词，结果回调）
 * @param excludeIds 需要排除的卡片ID列表（已关联的卡片不再显示）
 * @param sourceType 当前来源类型（排除自身卡片）
 * @param sourceId 当前来源ID（排除自身卡片）
 */
@Composable
fun CardLinkSelectorDialog(
    onDismiss: () -> Unit,
    onCardSelected: (cardType: String, cardId: Long, cardTitle: String) -> Unit,
    searchCards: (query: String, callback: (List<CardSearchResult>) -> Unit) -> Unit,
    excludeIds: Set<Pair<String, Long>> = emptySet(),
    sourceType: String = "",
    sourceId: Long = 0,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<CardSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        searchCards("") { results ->
            searchResults = results.filterNot { result ->
                (result.cardType == sourceType && result.cardId == sourceId) ||
                excludeIds.contains(result.cardType to result.cardId)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(480.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "关联卡片",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF999999),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { query ->
                        searchQuery = query
                        isSearching = true
                        searchCards(query) { results ->
                            searchResults = results.filterNot { result ->
                                (result.cardType == sourceType && result.cardId == sourceId) ||
                                excludeIds.contains(result.cardType to result.cardId)
                            }
                            isSearching = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text(
                            text = "搜索卡片标题...",
                            fontSize = 14.sp,
                            color = Color(0xFF999999)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = UiColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = UiColors.SearchBackground,
                        unfocusedContainerColor = UiColors.SearchBackground,
                        focusedBorderColor = UiColors.Primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = UiColors.Primary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            searchCards(searchQuery) { results ->
                                searchResults = results.filterNot { result ->
                                    (result.cardType == sourceType && result.cardId == sourceId) ||
                                    excludeIds.contains(result.cardType to result.cardId)
                                }
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (searchResults.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "没有找到匹配的卡片",
                            fontSize = 14.sp,
                            color = Color(0xFF999999)
                        )
                    }
                } else {
                    val groupedResults = searchResults.groupBy { it.cardType }
                    val typeOrder = listOf("todo", "inspiration", "date")
                    val typeNames = mapOf(
                        "todo" to "待办",
                        "inspiration" to "灵感",
                        "date" to "日期"
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        typeOrder.forEach { type ->
                            val items = groupedResults[type] ?: return@forEach
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${items.first().typeIcon} ${typeNames[type]} (${items.size})",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = UiColors.TextSecondary
                                    )
                                }
                            }

                            items(
                                items = items,
                                key = { "${it.cardType}_${it.cardId}" }
                            ) { card ->
                                CardSearchItem(
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
}

/**
 * 搜索结果项组件
 * 显示单个可选择的卡片搜索结果
 */
@Composable
private fun CardSearchItem(
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
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = card.typeIcon,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 10.dp)
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
            color = UiColors.TextSecondary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
