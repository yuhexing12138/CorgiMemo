package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 删除确认对话框组件
 *
 * 在执行破坏性操作（如删除待办）前弹出，
 * 要求用户二次确认以防止误操作。
 * 包含警告图标、提示文本和确认/取消按钮。
 *
 * @param showDialog 是否显示对话框
 * @param itemTitle 要删除的项目标题（用于显示在提示文本中）
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun DeleteConfirmDialog(
    showDialog: Boolean,
    itemTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = Color(0xFFFF8A80), // 警告红色（柔和）
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            },
            title = {
                Text(
                    text = "确认删除",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "确定要删除以下待办吗？",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    /** 待办标题（高亮显示） */
                    Text(
                        text = "「$itemTitle」",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error, // 红色高亮
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "此操作无法撤销，删除后数据将永久丢失。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm
                ) {
                    Text(
                        text = "确认删除",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}

/**
 * 批量删除确认对话框组件
 *
 * 用于批量选择模式下的删除确认，
 * 显示将要删除的项目数量而非具体标题。
 *
 * @param showDialog 是否显示对话框
 * @param itemCount 要删除的项目数量
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消/关闭回调
 */
@Composable
fun BatchDeleteConfirmDialog(
    showDialog: Boolean,
    itemCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "警告",
                    tint = Color(0xFFFF8A80), // 警告红色（柔和）
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            },
            title = {
                Text(
                    text = "批量删除确认",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "确定要删除选中的 $itemCount 个待办吗？",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "此操作无法撤销，删除后数据将永久丢失。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm
                ) {
                    Text(
                        text = "删除 $itemCount 项",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss
                ) {
                    Text(
                        text = "取消",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        )
    }
}
