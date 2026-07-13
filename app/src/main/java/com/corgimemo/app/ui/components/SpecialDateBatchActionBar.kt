package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 日期页批量操作栏
 *
 * 批量选择模式下替代底部导航栏，提供全选/取消全选 + 5 个操作图标。
 * 实际开发：删除、归档、创建副本；占位：分享、更多。
 *
 * 布局：[全选/取消全选]   [分享] [归档] [副本] [删除] [更多]
 *
 * @param isBatchMode 是否处于批量模式（控制显隐动画）
 * @param selectedDateIds 当前选中的日期 ID 集合
 * @param totalDateCount 日期总数（用于判断全选状态）
 * @param onSelectAll 全选回调
 * @param onClearSelection 取消全选回调
 * @param onShare 分享回调（占位）
 * @param onArchive 归档回调
 * @param onDuplicate 创建副本回调
 * @param onDelete 删除回调
 * @param onMoreOptions 更多选项回调（占位）
 */
@Composable
fun SpecialDateBatchActionBar(
    isBatchMode: Boolean,
    selectedDateIds: Set<Long>,
    totalDateCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onMoreOptions: () -> Unit
) {
    AnimatedVisibility(
        visible = isBatchMode,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSelection = selectedDateIds.isNotEmpty()
                val isAllSelected = totalDateCount > 0 &&
                    selectedDateIds.size == totalDateCount

                /** 左侧：全选 / 取消全选 */
                TextButton(
                    onClick = {
                        if (isAllSelected) onClearSelection() else onSelectAll()
                    },
                    enabled = totalDateCount > 0
                ) {
                    Text(
                        text = if (isAllSelected) "取消全选" else "全选",
                        color = UiColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                /** 右侧：5 个图标按钮 */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 1. 分享（占位） */
                    IconButton(
                        onClick = onShare,
                        enabled = false,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.4f)
                        )
                    }

                    /** 2. 归档（实际开发） */
                    IconButton(
                        onClick = onArchive,
                        enabled = hasSelection,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Archive,
                            contentDescription = "归档",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 3. 创建副本（实际开发） */
                    IconButton(
                        onClick = onDuplicate,
                        enabled = hasSelection,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "创建副本",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 4. 删除（实际开发） */
                    IconButton(
                        onClick = onDelete,
                        enabled = hasSelection,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除",
                            tint = if (hasSelection) {
                                UiColors.Error
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 5. 更多（占位） */
                    IconButton(
                        onClick = onMoreOptions,
                        enabled = false,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.4f)
                        )
                    }
                }
            }
        }
    }
}
