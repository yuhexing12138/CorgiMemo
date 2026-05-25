package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 可选的图标列表
 */
val TEMPLATE_ICONS = listOf(
    "☀️", "💼", "📚", "🏠", "🎯", "❤️",
    "🏋️", "💰", "🎨", "🍳", "✈️", "💻"
)

/**
 * 模板编辑对话框组件
 * 用于创建新模板或编辑现有模板
 *
 * @param initialName 初始模板名称（编辑模式）
 * @param initialIcon 初始图标（编辑模式）
 * @param initialDescription 初始描述（编辑模式）
 * @param initialTodos 初始待办列表（编辑模式）
 * @param isEditMode 是否为编辑模式
 * @param onSave 保存回调（传入 name, icon, description, todosJson）
 * @param onDismiss 关闭对话框回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TemplateEditDialog(
    initialName: String = "",
    initialIcon: String = "☀️",
    initialDescription: String = "",
    initialTodos: List<String> = emptyList(),
    isEditMode: Boolean = false,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    /** 当前输入的模板名称 */
    var templateName by remember { mutableStateOf(initialName) }

    /** 当前选择的图标 */
    var selectedIcon by remember { mutableStateOf(initialIcon) }

    /** 当前输入的描述 */
    var templateDescription by remember { mutableStateOf(initialDescription) }

    /** 待办事项列表 */
    val todoItems = remember { mutableStateListOf<String>().apply { addAll(initialTodos) } }

    /** 新增的待办项输入框内容 */
    var newTodoText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                /** 标题 */
                Text(
                    text = if (isEditMode) "编辑模板" else "创建新模板",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(weight = 1f, fill = false)
                ) {
                    /** 模板名称输入框 */
                    item {
                        OutlinedTextField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            label = { Text("模板名称") },
                            placeholder = { Text("例如：我的日常习惯") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    /** 图标选择器 */
                    item {
                        Column {
                            Text(
                                text = "图标选择",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TEMPLATE_ICONS.forEach { icon ->
                                    val isSelected = icon == selectedIcon
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable { selectedIcon = icon },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = icon, fontSize = 22.sp)
                                    }
                                }
                            }
                        }
                    }

                    /** 描述输入框 */
                    item {
                        OutlinedTextField(
                            value = templateDescription,
                            onValueChange = { templateDescription = it },
                            label = { Text("描述（可选）") },
                            placeholder = { Text("简短描述这个模板的用途") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    /** 待办事项列表 */
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "待办事项 (${todoItems.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            /** 已添加的待办列表 */
                            if (todoItems.isEmpty()) {
                                Text(
                                    text = "暂无待办，请在下方添加",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                todoItems.forEachIndexed { index, todo ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}. $todo",
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f)
                                        )

                                        IconButton(
                                            onClick = { todoItems.removeAt(index) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "删除待办",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            /** 添加待办输入框 */
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newTodoText,
                                    onValueChange = { newTodoText = it },
                                    placeholder = { Text("输入待办事项...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (newTodoText.isNotBlank()) {
                                            todoItems.add(newTodoText.trim())
                                            newTodoText = ""
                                        }
                                    },
                                    enabled = newTodoText.isNotBlank()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "添加待办"
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                /** 底部按钮 */
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (templateName.isNotBlank() && todoItems.isNotEmpty()) {
                                /** 将待办列表手动构建为 JSON 字符串 */
                                val todosJson = buildString {
                                    append("[")
                                    todoItems.forEachIndexed { index, todo ->
                                        if (index > 0) append(",")
                                    append("\"${todo.replace("\"", "\\\"")}\"")
                                    }
                                    append("]")
                                }
                                onSave(templateName, selectedIcon, templateDescription, todosJson)
                            }
                        },
                        enabled = templateName.isNotBlank() && todoItems.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isEditMode) "保存修改" else "创建模板")
                    }
                }
            }
        }
    }
}
