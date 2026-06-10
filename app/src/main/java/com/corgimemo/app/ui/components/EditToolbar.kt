package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 编辑页底部工具栏组件
 *
 * 横排图标布局：
 *   左侧：字体格式 | 列表 | 相机 | 麦克风 | 背景 | 分享
 *   右侧：删除 | 字数统计
 *
 * 子任务添加功能已移至复选框编辑器的 "/" 命令和回车新建行，
 * 本工具栏不再包含独立的"添加子任务"按钮。
 *
 * @param backgroundColor 工具栏背景色，跟随页面整体背景色变化
 */
@Composable
fun EditToolbar(
    onFontClick: () -> Unit,
    onListClick: () -> Unit,
    onPhotoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onShareClick: () -> Unit,
    onDeleteClick: () -> Unit,
    wordCount: Int = 0,
    modifier: Modifier = Modifier,
    /** 默认使用主题背景色（暖米色），与新建日期/新建灵感页一致 */
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 4.dp,
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Column {
            /** 工具栏图标行 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /** ===== 左侧工具按钮组 ===== */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarIconBtn(icon = "A/A", contentDescription = "字体格式", onClick = onFontClick)
                    ToolbarIconBtn(imageVector = Icons.Default.FormatListBulleted, contentDescription = "列表", onClick = onListClick)
                    ToolbarIconBtn(imageVector = Icons.Default.PhotoCamera, contentDescription = "照片", onClick = onPhotoClick)
                    ToolbarIconBtn(imageVector = Icons.Default.Mic, contentDescription = "语音", onClick = onVoiceClick)
                    ToolbarIconBtn(imageVector = Icons.Default.Palette, contentDescription = "背景色", onClick = onBackgroundClick)
                    ToolbarIconBtn(imageVector = Icons.Default.Share, contentDescription = "分享", onClick = onShareClick)
                }

                /** ===== 右侧信息区（删除 + 字数）===== */
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "共${wordCount}字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 工具栏图标按钮
 *
 * 支持 ImageVector 图标或纯文字标签（如 "A/A"）
 */
@Composable
private fun ToolbarIconBtn(
    imageVector: ImageVector? = null,
    icon: String? = null,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(40.dp)
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        } else if (icon != null) {
            Text(
                text = icon,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = androidx.compose.ui.text.TextStyle(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            )
        }
    }
}
