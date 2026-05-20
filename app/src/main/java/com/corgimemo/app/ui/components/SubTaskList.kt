package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.SubTask

/**
 * 子任务列表组件
 *
 * @param subTasks 子任务列表
 * @param showAddButton 是否显示添加子任务输入框
 * @param onAddSubTask 添加子任务回调
 * @param onToggleSubTask 切换子任务完成状态回调
 * @param onDeleteSubTask 删除子任务回调
 * @param modifier Modifier
 */
@Composable
fun SubTaskList(
    subTasks: List<SubTask>,
    showAddButton: Boolean = true,
    onAddSubTask: (String) -> Unit = {},
    onToggleSubTask: (SubTask) -> Unit = {},
    onDeleteSubTask: (SubTask) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var newSubTaskText by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (subTasks.isNotEmpty()) {
            // 进度显示
            val completedCount = subTasks.count { it.isCompleted }
            val totalCount = subTasks.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "子任务",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$completedCount/$totalCount",
                    fontSize = 13.sp,
                    color = if (completedCount == totalCount && totalCount > 0) {
                        Color(0xFF10B981)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // 子任务列表
            subTasks.forEach { subTask ->
                SubTaskListItem(
                    subTask = subTask,
                    onToggleComplete = onToggleSubTask,
                    onDelete = onDeleteSubTask,
                    showDelete = showAddButton
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        if (showAddButton) {
            Spacer(modifier = Modifier.height(8.dp))
            AddSubTaskInput(
                value = newSubTaskText,
                onValueChange = { newSubTaskText = it },
                onAdd = {
                    if (newSubTaskText.text.isNotBlank()) {
                        onAddSubTask(newSubTaskText.text.trim())
                        newSubTaskText = TextFieldValue("")
                    }
                }
            )
        }
    }
}

/**
 * 添加子任务输入框
 *
 * @param value 输入框值
 * @param onValueChange 值变化回调
 * @param onAdd 添加回调
 */
@Composable
private fun AddSubTaskInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    text = "添加子任务...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { onAdd() }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onAdd,
            enabled = value.text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加",
                tint = if (value.text.isNotBlank()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                }
            )
        }
    }
}
