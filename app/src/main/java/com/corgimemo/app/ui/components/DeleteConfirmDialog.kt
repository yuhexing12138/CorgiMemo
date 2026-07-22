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
 * 删除确认对话框的两种使用模式
 *
 * - [Delete]：破坏性删除（删除已存在的待办/卡片等）
 *   弹窗显示"确认删除"标题 + 待办标题高亮 + 红色"确认删除"按钮 + "此操作无法撤销"警告
 *   适用场景：编辑模式（已存在 todoId 的待办）下点击垃圾桶
 *
 * - [Discard]：放弃编辑（未保存的草稿/新建内容）
 *   弹窗显示"放弃编辑"标题 + 通用占位提示 + 红色"放弃编辑"按钮 + "未保存的内容将永久丢失"警告
 *   适用场景：新建模式（todoId == null）下点击垃圾桶
 *   注意：此模式下不显示待办标题高亮区域（因为没有持久化的标题）
 *
 * v2026-07-22 改造：增加 Discard 模式，复用同一组件支持"放弃编辑"语义，
 * 与删除操作共享同一套警告图标 + 二次确认交互体验。
 */
enum class DeleteDialogMode {
    /** 模式 1：删除已存在的对象（弹窗强调"此操作无法撤销"） */
    Delete,

    /** 模式 2：放弃编辑未保存的内容（弹窗强调"未保存的内容将永久丢失"） */
    Discard
}

/**
 * 删除/放弃确认对话框组件
 *
 * 在执行破坏性操作前弹出，要求用户二次确认以防止误操作。
 * 包含警告图标、提示文本和确认/取消按钮。
 *
 * 支持两种使用模式（[mode]）：
 * - [DeleteDialogMode.Delete]：删除已存在对象，弹窗强调"此操作无法撤销"
 * - [DeleteDialogMode.Discard]：放弃未保存内容，弹窗强调"未保存内容将丢失"
 *   - 此模式下不显示 [itemTitle] 高亮（无持久化标题可显示）
 *   - 仍接收 [itemTitle] 参数是为了 API 兼容性（调用方不用分两种模式传参）
 *
 * @param showDialog 是否显示对话框
 * @param itemTitle 要删除的项目标题（用于显示在提示文本中）；
 *                  Discard 模式下会被忽略（不显示标题区域）
 * @param onConfirm 确认回调
 * @param onDismiss 取消/关闭回调
 * @param mode 弹窗模式（默认 [DeleteDialogMode.Delete]）
 */
@Composable
fun DeleteConfirmDialog(
    showDialog: Boolean,
    itemTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    mode: DeleteDialogMode = DeleteDialogMode.Delete
) {
    /** 当前是否为"放弃编辑"模式 */
    val isDiscard = mode == DeleteDialogMode.Discard

    /**
     * 模式相关文案派生
     *
     * 根据 [mode] 切换弹窗内的所有文本：
     * - 标题：删除 → "确认删除"；放弃 → "放弃编辑"
     * - 询问：删除 → "确定要删除以下待办吗？"；放弃 → "确定要放弃编辑吗？"
     * - 警告：删除 → "此操作无法撤销，删除后数据将永久丢失。"；放弃 → "未保存的内容将永久丢失，无法恢复。"
     * - 按钮：删除 → "确认删除"；放弃 → "放弃编辑"
     */
    val titleText = if (isDiscard) "放弃编辑" else "确认删除"
    val questionText = if (isDiscard) "确定要放弃编辑吗？" else "确定要删除以下待办吗？"
    val warningText = if (isDiscard) "未保存的内容将永久丢失，无法恢复。" else "此操作无法撤销，删除后数据将永久丢失。"
    val confirmText = if (isDiscard) "放弃编辑" else "确认删除"

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
                    text = titleText,
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
                        text = questionText,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    /**
                     * 仅 Delete 模式显示待办标题高亮区域
                     *
                     * Discard 模式下跳过：
                     * - 新建待办无持久化标题可显示
                     * - 即使用户填写了 title（StateFlow 中的临时值），它只是"草稿内容"，
                     *   用高亮方式显示"要删的就是它"反而会让用户误以为已保存
                     */
                    if (!isDiscard) {
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
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = warningText,
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
                        text = confirmText,
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
