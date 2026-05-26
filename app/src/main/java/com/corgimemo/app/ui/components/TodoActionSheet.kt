package com.corgimemo.app.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 待办操作底部弹窗 (Todo Action Sheet)
 *
 * 长按待办项时弹出的 ModalBottomSheet，提供以下操作：
 * - 📌 置顶待办
 * - ✏️ 编辑待办
 * - 🖼️ 分享为图片
 * - 📋 批量选择
 * - ────────────────
 * - 🗑️ 删除待办（红色警告）
 *
 * @param sheetState 底部弹窗状态
 * @param onDismiss 关闭回调
 * @param onPin 置顶回调
 * @param onEdit 编辑回调
 * @param onShare 分享回调
 * @param onBatchSelect 批量选择回调
 * @param onDelete 删除回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoActionSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPin: () -> Unit = {},
    onEdit: () -> Unit = {},
    onShare: () -> Unit = {},
    onBatchSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 拖动指示器
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // 使用 HorizontalDivider 模拟拖动条
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }

            // 操作选项列表
            ActionItem(
                icon = Icons.Default.PushPin,
                text = "📌 置顶待办",
                onClick = {
                    onPin()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Edit,
                text = "✏️ 编辑待办",
                onClick = {
                    onEdit()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.Share,
                text = "🖼️ 分享为图片",
                onClick = {
                    onShare()
                    onDismiss()
                }
            )

            ActionItem(
                icon = Icons.Default.SelectAll,
                text = "📋 批量选择",
                onClick = {
                    onBatchSelect()
                    onDismiss()
                }
            )

            // 分割线
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 删除操作（红色警告）
            ActionItem(
                icon = Icons.Default.Delete,
                text = "🗑️ 删除待办",
                isDestructive = true,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * 操作选项项组件
 *
 * @param icon 图标
 * @param text 文字描述
 * @param isDestructive 是否为破坏性操作（红色显示）
 * @param onClick 点击回调
 */
@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
