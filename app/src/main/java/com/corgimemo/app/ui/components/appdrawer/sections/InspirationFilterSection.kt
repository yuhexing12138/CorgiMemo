package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.TagFilterMode

/**
 * 灵感标签筛选分区（侧边栏）
 *
 * 布局：
 * 1. 标题"🏷️ 标签筛选" + 橙色横线
 * 2. 搜索框（本地状态，模糊匹配标签名）
 * 3. 筛选模式芯片：OR（任一匹配） / AND（全部匹配） / NOT（排除）
 * 4. "全部灵感" 项 + 过滤后的标签列表
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param tags 所有标签名列表
 * @param selectedTags 当前选中的标签集合（多选）
 * @param filterMode 标签筛选模式（OR / AND / NOT）
 * @param tagCounts 每个标签对应的灵感数量
 * @param totalInspirationCount 灵感总数（用于"全部灵感"项计数）
 * @param onTagClick 标签点击回调（传入标签名，由调用方切换选中状态）
 * @param onFilterModeChange 筛选模式切换回调
 * @param onClearTagSelection 清空所有选中标签回调（"全部灵感"点击时调用）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun InspirationFilterSection(
    tags: List<String>,
    selectedTags: Set<String>,
    filterMode: TagFilterMode,
    tagCounts: Map<String, Int>,
    totalInspirationCount: Int,
    onTagClick: (String) -> Unit,
    onFilterModeChange: (TagFilterMode) -> Unit,
    onClearTagSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 搜索框本地状态（不持久化，关闭侧边栏后清空）
    var searchQuery by remember { mutableStateOf("") }

    // 根据搜索词过滤标签列表（忽略大小写的模糊匹配）
    val filteredTags = if (searchQuery.isBlank()) {
        tags
    } else {
        tags.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        // 1. 标题
        Text(
            text = "🏷️ 标签筛选",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 2. 橙色分割线
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "搜索标签",
                    fontSize = 14.sp,
                    color = Color(0xFF79747E)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                            tint = Color(0xFF79747E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiColors.Primary,
                unfocusedBorderColor = Color(0xFFE0E0E0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. 筛选模式芯片（OR / AND / NOT）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == TagFilterMode.OR,
                onClick = { onFilterModeChange(TagFilterMode.OR) },
                label = { Text("OR", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.AND,
                onClick = { onFilterModeChange(TagFilterMode.AND) },
                label = { Text("AND", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.NOT,
                onClick = { onFilterModeChange(TagFilterMode.NOT) },
                label = { Text("NOT", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 5. 标签列表（可滚动）
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // "全部灵感" 选项（无选中标签时高亮）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部灵感",
                    count = totalInspirationCount,
                    isSelected = selectedTags.isEmpty(),
                    showMenu = false,
                    onClick = { onClearTagSelection() }
                )
            }

            // 过滤后的标签列表
            items(filteredTags) { tag ->
                CategoryItem(
                    icon = "#",
                    name = tag,
                    count = tagCounts[tag] ?: 0,
                    isSelected = tag in selectedTags,
                    showMenu = false,
                    onClick = { onTagClick(tag) }
                )
            }
        }
    }
}
