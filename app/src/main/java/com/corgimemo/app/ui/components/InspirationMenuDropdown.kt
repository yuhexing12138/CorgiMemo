package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

/**
 * 灵感页功能菜单弹窗
 *
 * 点击右上角三点图标后弹出，包含 7 个功能项：
 * 1. 隐藏详情/显示详情（实际开发）
 * 2. 管理标签（占位）
 * 3. 批量选择（实际开发）
 * 4. 分享（占位）
 * 5. 创建灵感副本（占位）
 * 6. 写信未来（占位）
 * 7. 回收站（占位）
 *
 * @param expanded 是否展开
 * @param onDismiss 关闭回调
 * @param hideDetails 当前是否隐藏详情
 * @param onToggleHideDetails 切换隐藏详情回调
 * @param onBatchSelectClick 批量选择点击回调
 * @param onPlaceholderClick 占位项点击回调（Toast"开发中"）
 */
@Composable
fun InspirationMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    hideDetails: Boolean,
    onToggleHideDetails: () -> Unit,
    onBatchSelectClick: () -> Unit,
    onPlaceholderClick: () -> Unit,
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
        // 2. 管理标签（占位）
        DropdownMenuItem(
            text = { Text("管理标签") },
            onClick = {
                onPlaceholderClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Label,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.alpha(0.4f)
        )
        // 3. 批量选择
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
        // 4. 分享（占位）
        DropdownMenuItem(
            text = { Text("分享") },
            onClick = {
                onPlaceholderClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.alpha(0.4f)
        )
        // 5. 创建灵感副本（占位）
        DropdownMenuItem(
            text = { Text("创建灵感副本") },
            onClick = {
                onPlaceholderClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.alpha(0.4f)
        )
        // 6. 写信未来（占位）
        DropdownMenuItem(
            text = { Text("写信未来") },
            onClick = {
                onPlaceholderClick()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Mail,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            },
            modifier = Modifier.alpha(0.4f)
        )
        // 7. 回收站
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
