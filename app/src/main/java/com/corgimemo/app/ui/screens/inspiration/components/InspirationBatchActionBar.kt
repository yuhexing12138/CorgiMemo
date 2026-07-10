package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
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
 * 灵感页批量操作栏
 *
 * 批量选择模式下替代底部导航栏，提供全选/取消全选 + 5 个操作图标。
 * 实际开发：删除、置顶；占位：分享、创建副本、转换为待办。
 *
 * 布局：[全选/取消全选]   [分享] [删除] [置顶] [副本] [转待办]
 *
 * @param isBatchMode 是否处于批量模式（控制显隐动画）
 * @param selectedInspirationIds 当前选中的灵感 ID 集合
 * @param totalInspirationCount 灵感总数（用于判断全选状态）
 * @param onSelectAll 全选回调
 * @param onClearSelection 取消全选回调
 * @param onDelete 删除回调
 * @param onPin 置顶回调
 */
@Composable
fun InspirationBatchActionBar(
    isBatchMode: Boolean,
    selectedInspirationIds: Set<Long>,
    totalInspirationCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit
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
                val hasSelection = selectedInspirationIds.isNotEmpty()
                val isAllSelected = totalInspirationCount > 0 &&
                    selectedInspirationIds.size == totalInspirationCount

                /** 左侧：全选 / 取消全选 */
                TextButton(
                    onClick = {
                        if (isAllSelected) onClearSelection() else onSelectAll()
                    },
                    enabled = totalInspirationCount > 0
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
                        onClick = {},
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

                    /** 2. 删除（实际开发） */
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

                    /** 3. 置顶（实际开发） */
                    IconButton(
                        onClick = onPin,
                        enabled = hasSelection,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "置顶",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 4. 创建副本（占位） */
                    IconButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "创建副本",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.4f)
                        )
                    }

                    /** 5. 转换为待办（占位） */
                    IconButton(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SwapHoriz,
                            contentDescription = "转换为待办",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.4f)
                        )
                    }
                }
            }
        }
    }
}
