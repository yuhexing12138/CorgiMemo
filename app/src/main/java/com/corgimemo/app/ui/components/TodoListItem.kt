package com.corgimemo.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import java.util.concurrent.TimeUnit

/**
 * 待办列表项组件
 *
 * @param todo 待办数据
 * @param subTaskProgress 子任务进度（如 "2/5"，无子任务时为 null）
 * @param subTasks 子任务列表
 * @param isExpanded 是否展开显示子任务
 * @param isBatchMode 是否处于批量选择模式
 * @param isSelected 是否已选中（批量模式下）
 * @param onToggleComplete 切换完成状态回调
 * @param onDelete 删除回调
 * @param onClick 点击回调（普通模式）
 * @param onLongClick 长按回调（进入批量模式）
 * @param onSelectClick 选择回调（批量模式下点击）
 * @param onShareAsImage 分享为图片回调
 * @param onToggleExpand 切换展开状态回调
 * @param onToggleSubTask 切换子任务完成状态回调
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodoListItem(
    todo: TodoItem,
    subTaskProgress: String? = null,
    subTasks: List<SubTask> = emptyList(),
    isExpanded: Boolean = false,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    categoryName: String? = null,
    categoryIcon: String? = null,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onShareAsImage: () -> Unit = {},
    onToggleExpand: () -> Unit = {},
    onToggleSubTask: (Long) -> Unit = {}
) {
    val deleteWidth = 80.dp
    var offsetX by remember { mutableStateOf(0f) }

    val cardBackground by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 200),
        label = "cardBackground"
    )

    val checkboxStartPadding by animateDpAsState(
        targetValue = if (isBatchMode) 8.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "checkboxStartPadding"
    )

    var showLongPressMenu by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (!isBatchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box(
                    modifier = Modifier
                        .width(deleteWidth)
                        .height(68.dp)
                        .background(Color(0xFFEF4444))
                        .clip(RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .offset(x = Dp(offsetX))
                .pointerInput(isBatchMode) {
                    if (!isBatchMode) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX < -deleteWidth.value / 2) {
                                    onDelete(todo.id)
                                }
                                offsetX = 0f
                            }
                        ) { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-deleteWidth.value, 0f)
                        }
                    }
                }
                .combinedClickable(
                    onClick = {
                        if (isBatchMode) {
                            onSelectClick()
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (isBatchMode) {
                            onLongClick()
                        } else {
                            showLongPressMenu = true
                        }
                    },
                    role = Role.Tab
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBackground)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isBatchMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onSelectClick() },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    } else {
                        if (checkboxStartPadding > 0.dp) {
                            Spacer(modifier = Modifier.width(checkboxStartPadding))
                        }
                        Checkbox(
                            checked = todo.status == 1,
                            onCheckedChange = { isChecked ->
                                onToggleComplete(todo.id, isChecked)
                            },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = todo.title,
                                fontSize = 16.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                                color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            subTaskProgress?.let { progress ->
                                Text(
                                    text = " ($progress)",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                )
                            }
                        }
                        if (todo.status == 1 && todo.completedAt != null) {
                            Text(
                                text = formatCompletedTime(todo.completedAt),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        } else if (!todo.content.isNullOrBlank()) {
                            Text(
                                text = todo.content,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        if (categoryName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = categoryIcon ?: "📋",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = categoryName,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (todo.status != 1 && todo.content.isNullOrBlank() && subTaskProgress != null) {
                            SubTaskProgressBar(
                                progress = parseProgress(subTaskProgress),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }

                    // 展开/收起按钮（仅在有子任务且非批量模式时显示）
                    if (subTaskProgress != null && !isBatchMode) {
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) {
                                    Icons.Default.ExpandLess
                                } else {
                                    Icons.Default.ExpandMore
                                },
                                contentDescription = if (isExpanded) "收起" else "展开",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        PriorityBadge(priority = todo.priority)
                    }
                }

                // 展开时显示子任务列表
                if (isExpanded && subTasks.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 64.dp, end = 16.dp, bottom = 16.dp)
                    ) {
                        subTasks.forEach { subTask ->
                            SubTaskInTodoListItem(
                                subTask = subTask,
                                onToggleComplete = { onToggleSubTask(subTask.id) }
                            )
                            if (subTask != subTasks.last()) {
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }

        if (showLongPressMenu) {
            AlertDialog(
                onDismissRequest = { showLongPressMenu = false },
                title = { Text("操作") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                showLongPressMenu = false
                                onShareAsImage()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "🖼️ 分享为图片")
                        }
                        TextButton(
                            onClick = {
                                showLongPressMenu = false
                                onLongClick()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "📋 批量选择")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLongPressMenu = false }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

/**
 * 格式化完成时间为友好的显示文本
 *
 * @param completedAt 完成时间戳（毫秒）
 * @return 格式化后的时间文本，如 "3 分钟前完成"、"2 小时前完成"、"5 天前完成"
 */
private fun formatCompletedTime(completedAt: Long): String {
    val diffMillis = System.currentTimeMillis() - completedAt
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
    val diffHours = TimeUnit.MILLISECONDS.toHours(diffMillis)
    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMillis)

    return when {
        diffMinutes < 1 -> "刚刚完成"
        diffMinutes < 60 -> "$diffMinutes 分钟前完成"
        diffHours < 24 -> "$diffHours 小时前完成"
        else -> "$diffDays 天前完成"
    }
}

/**
 * 子任务进度条组件
 *
 * @param progress 进度值 (0.0-1.0)
 * @param modifier Modifier
 */
@Composable
private fun SubTaskProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val warmOrange = Color(0xFFF97316)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        ) {
            val trackWidth = size.width
            val progressWidth = trackWidth * progress.coerceIn(0f, 1f)
            val height = size.height

            // 绘制进度条（暖橙色）
            if (progressWidth > 0f) {
                drawRoundRect(
                    color = warmOrange,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(progressWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                )
            }
        }
    }
}

/**
 * 解析进度文本为浮点值
 *
 * @param progressText 进度文本，如 "2/5"
 * @return 进度值 (0.0-1.0)，解析失败返回 0f
 */
private fun parseProgress(progressText: String): Float {
    return try {
        val parts = progressText.split("/")
        if (parts.size == 2) {
            val completed = parts[0].toInt()
            val total = parts[1].toInt()
            if (total > 0) completed.toFloat() / total.toFloat() else 0f
        } else {
            0f
        }
    } catch (e: Exception) {
        0f
    }
}

/**
 * 待办列表中的子任务项组件
 * 用于展开待办后显示的子任务列表
 *
 * @param subTask 子任务
 * @param onToggleComplete 切换完成状态回调
 * @param modifier Modifier
 */
@Composable
private fun SubTaskInTodoListItem(
    subTask: SubTask,
    onToggleComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 圆形复选框
        SubTaskCheckbox(
            isCompleted = subTask.isCompleted,
            onClick = onToggleComplete
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 子任务标题
        Text(
            text = subTask.title,
            fontSize = 14.sp,
            color = if (subTask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (subTask.isCompleted) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 子任务复选框组件（与SubTaskListItem一致）
 *
 * @param isCompleted 是否已完成
 * @param onClick 点击回调
 */
@Composable
private fun SubTaskCheckbox(
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp
            )
        }
    }
}
