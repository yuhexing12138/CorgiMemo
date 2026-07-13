package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 日期详情页三点操作弹窗
 *
 * 包含三个操作项（从上到下）：
 * 1. 置顶 - 切换置顶状态
 * 2. 归档 - 归档日期
 * 3. 删除 - 删除日期
 *
 * @param expanded 弹窗是否展开
 * @param onDismiss 关闭弹窗回调
 * @param isPinned 当前是否已置顶
 * @param onTogglePin 切换置顶回调
 * @param onArchive 归档回调
 * @param onDelete 删除回调
 */
@Composable
fun DateDetailMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // 1. 置顶
        DropdownMenuItem(
            text = { Text(if (isPinned) "取消置顶" else "置顶") },
            onClick = {
                onTogglePin()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        // 2. 归档
        DropdownMenuItem(
            text = { Text("归档") },
            onClick = {
                onArchive()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Archive,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )

        // 3. 删除
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = {
                onDelete()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}
