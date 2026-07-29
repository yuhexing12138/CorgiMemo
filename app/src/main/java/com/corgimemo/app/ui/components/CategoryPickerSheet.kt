package com.corgimemo.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType

/**
 * 分类选择底部弹窗（带搜索选择器型）
 *
 * 替换原 CategorySelectorDialog（AlertDialog），提供：
 * - FlowRow Tag 标签布局：单一分组列表（v2026-07-29 改造，原"默认分类"+"我的分组"两区已合并）
 * - 搜索过滤
 * - 分类颜色高亮 + 选中边框
 * - 「+ 自定义」按钮 → 展开输入框创建分组
 * - 长按任意分组触发删除（v2026-07-29 改造，原仅自定义分组可长按）
 *
 * v2026-07-29 改造：
 * - 取消"默认/自定义分组"区分后，所有分组均按 `isPinned DESC, sortOrder ASC, id ASC`
 *   顺序混合显示（由 CategoryDao.getAllCategories 排序保证）
 * - 移除"默认分类"和"我的分组"两个 SectionHeader
 * - 所有分组都支持长按触发 onCategoryLongPress 回调
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循带搜索选择器型底部弹窗原型规范。
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param categories 可选分类列表（已按 isPinned DESC, sortOrder ASC, id ASC 排序）
 * @param currentCategoryId 当前分类 ID（用于高亮显示）
 * @param onDismiss 关闭弹窗回调
 * @param onCategorySelected 分类选中回调，参数为 (id, name)。
 *        id == 0L 表示自定义创建（name 为用户输入）
 * @param onCategoryLongPress 任意分组长按回调（传 null 时不支持长按）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun CategoryPickerSheet(
    sheetState: SheetState,
    categories: List<Category>,
    currentCategoryId: Long? = null,
    onDismiss: () -> Unit,
    onCategorySelected: (id: Long, name: String) -> Unit,
    onCategoryLongPress: ((Category) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    // v2026-07-29 改造：合并为单一分组列表，按数据库返回顺序（isPinned DESC, sortOrder ASC, id ASC）显示
    val filteredCategories = if (searchQuery.isBlank()) {
        categories
    } else {
        categories.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val hasResults = filteredCategories.isNotEmpty()

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
            /** 拖动指示器 */
            DragHandle()

            /** 标题栏 */
            TitleBar(title = "选择分类", onDismiss = onDismiss)

            /** 分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 搜索框 */
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("搜索分类...", color = Color(0xFF999999), fontSize = 16.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(20.dp)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF9A5C),
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 内容区：FlowRow Tag 标签 + 垂直滚动 */
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (!hasResults && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "未找到匹配的分类",
                            color = Color(0xFF999999),
                            fontSize = 14.sp
                        )
                    }
                } else {
                    /** 单一分组列表（v2026-07-29 改造：原"默认分类"+"我的分组"两区合并） */
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredCategories.forEach { category ->
                            CategoryTag(
                                category = category,
                                isSelected = category.id == currentCategoryId,
                                onClick = {
                                    onCategorySelected(category.id, category.name)
                                    onDismiss()
                                },
                                onLongClick = if (onCategoryLongPress != null) {
                                    { onCategoryLongPress(category) }
                                } else null,
                                forceIcon = null
                            )
                        }

                        /** 「+ 自定义」按钮 */
                        CustomCategoryButton(
                            onClick = { showCustomInput = !showCustomInput }
                        )
                    }

                    /** 自定义输入框 */
                    if (showCustomInput) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { customInput = it },
                                placeholder = { Text("输入分组名称", fontSize = 14.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    val name = customInput.trim()
                                    if (name.isNotBlank()) {
                                        onCategorySelected(0L, name)
                                        onDismiss()
                                    }
                                },
                                enabled = customInput.trim().isNotBlank()
                            ) {
                                Text("确定", fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/** ==================== 子组件 ==================== */

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryTag(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    forceIcon: String? = null
) {
    val categoryColor = getCategoryColor(category.type)
    val bgColor = if (isSelected) categoryColor.copy(alpha = 0.25f)
                  else categoryColor.copy(alpha = 0.12f)
    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, categoryColor, RoundedCornerShape(20.dp))
    } else {
        Modifier
    }

    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = interactionModifier
            .then(borderModifier)
            .background(color = bgColor, shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = forceIcon ?: getCategoryEmoji(category.type), fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = category.name,
            fontSize = 14.sp,
            color = Color(0xFF2D2D2D),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun CustomCategoryButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = Color(0xFFF0F0F2),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "+",
            fontSize = 16.sp,
            color = Color(0xFF888888),
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "自定义",
            fontSize = 14.sp,
            color = Color(0xFF888888)
        )
    }
}

private fun getCategoryEmoji(type: Int): String = when (type) {
    CategoryType.STUDY -> "📚"
    CategoryType.WORK -> "💼"
    CategoryType.LIFE -> "🏠"
    CategoryType.SPORT -> "🏃"
    CategoryType.ENTERTAINMENT -> "🎮"
    else -> "📋"
}

private fun getCategoryColor(type: Int): Color = when (type) {
    CategoryType.STUDY -> Color(0xFF7EC8A0)
    CategoryType.WORK -> Color(0xFF90CAF9)
    CategoryType.LIFE -> Color(0xFFFFB74D)
    CategoryType.SPORT -> Color(0xFF7EB8DA)
    CategoryType.ENTERTAINMENT -> Color(0xFFE1BEE7)
    else -> Color(0xFFB8A0D4)
}
