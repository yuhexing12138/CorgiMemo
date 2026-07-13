package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 日期详情页底部工具栏
 *
 * 从左到右排列四个图标按钮：
 * 1. 备注 - 编辑备注文字
 * 2. 主题 - 选择样式和颜色
 * 3. 编辑 - 跳转至编辑页面
 * 4. 分享 - 分享卡片图片
 *
 * @param onNoteClick 备注按钮点击回调
 * @param onThemeClick 主题按钮点击回调
 * @param onEditClick 编辑按钮点击回调
 * @param onShareClick 分享按钮点击回调
 */
@Composable
fun DateDetailBottomToolbar(
    onNoteClick: () -> Unit,
    onThemeClick: () -> Unit,
    onEditClick: () -> Unit,
    onShareClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 备注
        ToolbarItem(
            icon = Icons.Outlined.StickyNote2,
            label = "备注",
            onClick = onNoteClick
        )

        // 2. 主题
        ToolbarItem(
            icon = Icons.Outlined.Palette,
            label = "主题",
            onClick = onThemeClick
        )

        // 3. 编辑
        ToolbarItem(
            icon = Icons.Outlined.Edit,
            label = "编辑",
            onClick = onEditClick
        )

        // 4. 分享
        ToolbarItem(
            icon = Icons.Outlined.Share,
            label = "分享",
            onClick = onShareClick
        )
    }
}

/**
 * 底部工具栏单项
 *
 * @param icon 图标
 * @param label 文字标签
 * @param onClick 点击回调
 */
@Composable
private fun ToolbarItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
