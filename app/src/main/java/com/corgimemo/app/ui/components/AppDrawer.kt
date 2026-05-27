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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.theme.UiColors

private const val DRAWER_ICON_ALL = "📋"
private const val DRAWER_ICON_DELETED = "🗑️"
private const val DRAWER_ICON_UNCATEGORIZED = "📦"

private val categoryIcons = mapOf(
    0 to "📚",
    1 to "💼",
    2 to "🏠",
    3 to "🏃"
)

@Composable
fun AppDrawerContent(
    corgiData: CorgiData?,
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    recentlyDeletedCount: Int,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onAddCategoryClick: () -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 48.dp)
    ) {
        UserProfileSection(corgiData = corgiData)

        Spacer(modifier = Modifier.height(16.dp))

        CategoryGroupSection(
            categories = categories,
            todoCountByCategory = todoCountByCategory,
            recentlyDeletedCount = recentlyDeletedCount,
            selectedCategoryId = selectedCategoryId,
            onCategoryClick = onCategoryClick,
            onRecentlyDeletedClick = onRecentlyDeletedClick,
            onCategoryAction = onCategoryAction,
            modifier = Modifier.weight(1f)
        )

        AddCategoryButton(onClick = onAddCategoryClick)

        Spacer(modifier = Modifier.height(8.dp))

        BottomActionSection(
            onSettingsClick = onSettingsClick,
            onHelpClick = onHelpClick
        )
    }
}

@Composable
private fun UserProfileSection(corgiData: CorgiData?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(UiColors.PrimaryLight),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "🐕",
                fontSize = 24.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = corgiData?.name ?: "柯基",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (corgiData != null) {
                Text(
                    text = "Lv.${corgiData.level} 柯基少年",
                    fontSize = 13.sp,
                    color = Color(0xFF79747E)
                )
            }
        }
    }
}

@Composable
private fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    recentlyDeletedCount: Int,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onRecentlyDeletedClick: () -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "分组管理",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryId == null,
                    showMenu = false,
                    onClick = { onCategoryClick(null) }
                )
            }

            item {
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分类",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = selectedCategoryId == 0L,
                    showMenu = false,
                    onClick = { onCategoryClick(0L) }
                )
            }

            items(categories) { category ->
                val icon = categoryIcons[category.type] ?: "📂"
                CategoryItem(
                    icon = icon,
                    name = category.name,
                    count = todoCountByCategory[category.id] ?: 0,
                    isSelected = selectedCategoryId == category.id,
                    showMenu = !category.isDefault,
                    onClick = { onCategoryClick(category.id) },
                    onMenuClick = {
                        onCategoryAction(
                            CategoryAction.ShowMenu(category)
                        )
                    }
                )
            }

            item {
                CategoryItem(
                    icon = DRAWER_ICON_DELETED,
                    name = "最近删除",
                    count = recentlyDeletedCount,
                    isSelected = false,
                    showMenu = false,
                    textColor = Color(0xFF79747E),
                    onClick = onRecentlyDeletedClick
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    icon: String,
    name: String,
    count: Int,
    isSelected: Boolean,
    showMenu: Boolean,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) UiColors.Primary else textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (count > 0) {
            Text(
                text = "($count)",
                fontSize = 13.sp,
                color = Color(0xFF79747E)
            )
        }

        if (showMenu) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddCategoryButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiColors.Primary,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "添加分组",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BottomActionSection(
    onSettingsClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        color = Color(0xFFE0E0E0)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onSettingsClick) {
            Text(
                text = "⚙️ 设置",
                fontSize = 14.sp,
                color = Color(0xFF79747E)
            )
        }
        TextButton(onClick = onHelpClick) {
            Text(
                text = "❓ 帮助与反馈",
                fontSize = 14.sp,
                color = Color(0xFF79747E)
            )
        }
    }
}

// ==================== 弹窗组件 ====================

@Composable
fun AddCategoryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建分组",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiColors.Primary,
                    focusedLabelColor = UiColors.Primary,
                    cursorColor = UiColors.Primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

@Composable
fun RenameCategoryDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名分组",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiColors.Primary,
                    focusedLabelColor = UiColors.Primary,
                    cursorColor = UiColors.Primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "删除分组",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = "确定要删除分组「$categoryName」吗？\n该分组下的待办将变为未分类状态。",
                fontSize = 14.sp,
                color = Color(0xFF1C1B1F)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    category: Category,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = category.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OperationOption(
                emoji = "📌",
                text = "置顶分组",
                onClick = {
                    onPin()
                    onDismiss()
                }
            )

            OperationOption(
                emoji = "✏️",
                text = "编辑分组",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )

            OperationOption(
                emoji = "🗑️",
                text = "删除分组",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OperationOption(
    emoji: String,
    text: String,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = textColor
        )
    }
}

/**
 * 分类操作动作密封类
 */
sealed class CategoryAction {
    data class ShowMenu(val category: Category) : CategoryAction()
    data class Pin(val category: Category) : CategoryAction()
    data class Rename(val category: Category) : CategoryAction()
    data class Delete(val category: Category) : CategoryAction()
}