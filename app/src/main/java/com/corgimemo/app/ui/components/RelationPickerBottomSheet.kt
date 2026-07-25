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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CardSearchResult
import kotlinx.coroutines.delay

/**
 * 多选关联选择底部弹窗
 *
 * 带搜索选择器型变体（多选），用于待办编辑页 @ 或 ＋ 触发。
 * 支持跨三种类型分组折叠、多选复选框、已选计数、完成确认。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 *
 * @param visible 是否显示
 * @param excludeIds 需要排除的卡片（已关联的）
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调
 * @param searchCards 搜索方法
 * @param maxSelection 最大选择数量，默认 10
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelationPickerBottomSheet(
    visible: Boolean,
    excludeIds: Set<Pair<String, Long>> = emptySet(),
    onDismiss: () -> Unit,
    onConfirm: (selectedCards: List<CardSearchResult>) -> Unit,
    searchCards: (query: String, callback: (List<CardSearchResult>) -> Unit) -> Unit,
    maxSelection: Int = 10
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<CardSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val selectedIds = remember { mutableStateMapOf<Pair<String, Long>, CardSearchResult>() }
    val collapsedTypes = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(visible) {
        if (visible) {
            isSearching = true
            searchCards("") { results ->
                searchResults = results.filterNot { result ->
                    excludeIds.contains(result.cardType to result.cardId)
                }
                isSearching = false
            }
        }
    }

    LaunchedEffect(searchQuery) {
        delay(300)
        isSearching = true
        searchCards(searchQuery) { results ->
            searchResults = results.filterNot { result ->
                excludeIds.contains(result.cardType to result.cardId)
            }
            isSearching = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
            DragHandle()

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
            TitleBar(title = "关联卡片", onDismiss = onDismiss)

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 搜索框 */
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "搜索卡片标题…",
                                fontSize = 16.sp,
                                color = Color(0xFF999999)
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = Color(0xFF2D2D2D)
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            /** 卡片列表区域 */
            if (isSearching && searchResults.isEmpty()) {
                Text(
                    text = "搜索中…",
                    fontSize = 14.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else if (searchResults.isEmpty()) {
                Text(
                    text = "无匹配结果",
                    fontSize = 14.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
            } else {
                val grouped = searchResults.take(50).groupBy { it.cardType }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    grouped.forEach { (cardType, cards) ->
                        item(key = "header_$cardType") {
                            TypeGroupHeader(
                                cardType = cardType,
                                totalCount = cards.size,
                                selectedCount = cards.count { (it.cardType to it.cardId) in selectedIds },
                                isCollapsed = collapsedTypes[cardType] == true,
                                onToggle = {
                                    collapsedTypes[cardType] = !(collapsedTypes[cardType] == true)
                                }
                            )
                        }
                        if (collapsedTypes[cardType] != true) {
                            items(
                                items = cards,
                                key = { "${it.cardType}_${it.cardId}" }
                            ) { card ->
                                val isSelected = (card.cardType to card.cardId) in selectedIds
                                PickerCardItem(
                                    card = card,
                                    isSelected = isSelected,
                                    enabled = isSelected || selectedIds.size < maxSelection,
                                    onClick = {
                                        val key = card.cardType to card.cardId
                                        if (isSelected) {
                                            selectedIds.remove(key)
                                        } else if (selectedIds.size < maxSelection) {
                                            selectedIds[key] = card
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            /** 底部栏：已选 N/M + 完成按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "已选 ${selectedIds.size} / $maxSelection",
                    fontSize = 14.sp,
                    color = Color(0xFF666666)
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF9A5C))
                        .clickable { onConfirm(selectedIds.values.toList()) }
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "完成",
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
        )
    }
}

/** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
@Composable
private fun TitleBar(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFFFF0E5))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color(0xFFFF9A5C),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 类型分组标题（可点击折叠/展开）
 */
@Composable
private fun TypeGroupHeader(
    cardType: String,
    totalCount: Int,
    selectedCount: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit
) {
    val typeName = when (cardType) {
        "todo" -> "待办"
        "inspiration" -> "灵感"
        "date" -> "日期"
        else -> "其他"
    }
    val typeIcon = when (cardType) {
        "todo" -> "📝"
        "inspiration" -> "💡"
        "date" -> "📅"
        else -> "📎"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isCollapsed) "▶" else "▼",
            fontSize = 12.sp,
            color = Color(0xFF999999)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$typeIcon $typeName",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "[$totalCount 项]",
            fontSize = 12.sp,
            color = Color(0xFF999999)
        )
        if (selectedCount > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "[$selectedCount 已选]",
                fontSize = 12.sp,
                color = Color(0xFFFF9A5C)
            )
        }
    }
}

/**
 * 选择器中的卡片项
 */
@Composable
private fun PickerCardItem(
    card: CardSearchResult,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when (card.cardType) {
        "todo" -> Color(0xFFE3F2FD)
        "inspiration" -> Color(0xFFFFF3E0)
        "date" -> Color(0xFFFCE4EC)
        else -> Color(0xFFF5F5F5)
    }
    val textColor = when (card.cardType) {
        "todo" -> Color(0xFF1976D2)
        "inspiration" -> Color(0xFFE65100)
        "date" -> Color(0xFFC2185B)
        else -> Color(0xFF666666)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) bgColor else Color.Transparent)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isSelected) textColor else Color.Transparent)
                .border(1.dp, if (isSelected) textColor else Color(0xFFCCCCCC), RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text("✓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = card.typeIcon, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = card.title,
            fontSize = 14.sp,
            color = Color(0xFF2D2D2D),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!enabled && !isSelected) {
            Text(
                text = "已达上限",
                fontSize = 11.sp,
                color = Color(0xFFCCCCCC)
            )
        }
    }
}
