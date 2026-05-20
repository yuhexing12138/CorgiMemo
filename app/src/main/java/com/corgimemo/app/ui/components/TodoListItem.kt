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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import com.corgimemo.app.data.model.TodoItem
import java.util.concurrent.TimeUnit

/**
 * 待办列表项组件
 *
 * @param todo 待办数据
 * @param isBatchMode 是否处于批量选择模式
 * @param isSelected 是否已选中（批量模式下）
 * @param onToggleComplete 切换完成状态回调
 * @param onDelete 删除回调
 * @param onClick 点击回调（普通模式）
 * @param onLongClick 长按回调（进入批量模式）
 * @param onSelectClick 选择回调（批量模式下点击）
 * @param onShareAsImage 分享为图片回调
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TodoListItem(
    todo: TodoItem,
    isBatchMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleComplete: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onSelectClick: () -> Unit = {},
    onShareAsImage: () -> Unit = {}
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
                    Text(
                        text = todo.title,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        textDecoration = if (todo.status == 1) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (todo.status == 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
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
                }

                PriorityBadge(priority = todo.priority)
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
