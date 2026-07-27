package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 我的待办页面功能菜单弹窗
 *
 * 点击右上角三点图标后弹出，包含 6 个功能项：
 * 1. 隐藏/显示详情
 * 2. 隐藏/显示已完成
 * 3. 待办排序
 * 4. 批量选择
 * 5. 创建待办副本（弹 [DuplicateTodoDialog] 中心弹窗，让用户选择复制范围）
 * 6. 回收站
 */
@Composable
fun TodoMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hideDetails: Boolean,
    onToggleHideDetails: () -> Unit,
    hideCompletedItems: Boolean,
    onToggleHideCompletedItems: () -> Unit,
    onSortClick: () -> Unit,
    onBatchSelectClick: () -> Unit,
    onDuplicateTodoClick: () -> Unit,
    onRecycleBinClick: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        // 1. 隐藏详情 / 显示详情
        DropdownMenuItem(
            text = { Text(if (hideDetails) "显示详情" else "隐藏详情") },
            onClick = {
                onToggleHideDetails()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 2. 隐藏已完成 / 显示已完成
        DropdownMenuItem(
            text = { Text(if (hideCompletedItems) "显示已完成" else "隐藏已完成") },
            onClick = {
                onToggleHideCompletedItems()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.CheckBox,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 3. 待办排序
        DropdownMenuItem(
            text = { Text("待办排序") },
            onClick = {
                onSortClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 4. 批量选择
        DropdownMenuItem(
            text = { Text("批量选择") },
            onClick = {
                onBatchSelectClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 5. 创建待办副本（弹中心弹窗，让用户选择复制范围）
        DropdownMenuItem(
            text = { Text("创建待办副本") },
            onClick = {
                onDuplicateTodoClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
        // 6. 回收站
        DropdownMenuItem(
            text = { Text("回收站") },
            onClick = {
                onRecycleBinClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        )
    }
}
