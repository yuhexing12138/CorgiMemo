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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
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
 * 分类选择底部弹窗组件
 *
 * 用于"移动到分组"功能，让用户选择待办要移动到的目标分类。
 * 支持搜索过滤、分类图标显示、选中状态反馈等。
 *
 * @param sheetState 底部弹窗状态控制对象
 * @param categories 可选分类列表
 * @param currentCategoryId 当前待办所属的分类 ID（用于高亮显示）
 * @param onDismiss 弹窗关闭回调
 * @param onCategorySelected 分类选择回调，返回选中的分类 ID
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    sheetState: SheetState,
    categories: List<Category>,
    currentCategoryId: Long? = null,
    onDismiss: () -> Unit,
    onCategorySelected: (Long) -> Unit
) {
    /** 搜索关键词状态 */
    var searchQuery by remember { mutableStateOf("") }

    /** 过滤后的分类列表 */
    val filteredCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            categories.filter { category ->
                category.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null // 使用自定义拖动指示器
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 自定义拖动指示器 */
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.width(36.dp)
                )
            }

            /** 标题栏：标题 + 关闭按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "移动到分组",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFFF9A5C), // 暖橙色
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            /** 搜索框 */
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("搜索分类...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(12.dp))

            /** 分类列表 */
            if (filteredCategories.isEmpty()) {
                /** 空状态提示 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "未找到匹配的分类" else "暂无可用分类",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredCategories) { category ->
                        CategoryPickerItem(
                            category = category,
                            isSelected = category.id == currentCategoryId,
                            onClick = {
                                onCategorySelected(category.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 分类选择项组件
 *
 * 单个分类的 UI 展示，包含图标、名称和选中状态。
 *
 * @param category 分类数据
 * @param isSelected 是否为当前选中状态（当前所属分类）
 * @param onClick 点击回调
 */
@Composable
private fun CategoryPickerItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    /** 根据分类类型获取对应图标 */
    val categoryIcon = when (category.type) {
        CategoryType.STUDY -> "📚"
        CategoryType.WORK -> "💼"
        CategoryType.LIFE -> "🏠"
        CategoryType.SPORT -> "🏃"
        else -> "📋"
    }

    /** 获取分类对应的颜色 */
    val categoryColor = when (category.type) {
        CategoryType.STUDY -> Color(0xFF7EC8A0) // 薄荷绿
        CategoryType.WORK -> Color(0xFF90CAF9) // 天空蓝
        CategoryType.LIFE -> Color(0xFFFFB74D) // 暖橙色
        CategoryType.SPORT -> Color(0xFF7EB8DA) // 运动蓝
        else -> Color(0xFFB8A0D4) // 默认紫色
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** 分类图标 */
        Text(
            text = categoryIcon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        /** 分类名称 */
        Text(
            text = category.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        /** 选中状态指示器 */
        if (isSelected) {
            Text(
                text = "当前",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = categoryColor,
                modifier = Modifier
                    .background(
                        color = categoryColor.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
