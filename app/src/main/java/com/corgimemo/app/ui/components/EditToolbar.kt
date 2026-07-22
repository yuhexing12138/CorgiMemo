package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
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
 * 基础布局（2 个始终显示的核心按钮）：
 *   相机 | 麦克风
 *
 * 可选按钮（仅在调用方传入对应回调时渲染）：
 * - 背景色按钮（Icons.Default.Palette）：传入 onBackgroundClick 时显示
 * - 关联按钮（@, Icons.Default.AlternateEmail）：传入 onMentionClick 时显示，触发卡片关联弹窗
 * - 位置按钮（#, Icons.Default.LocationOn）：传入 onLocationClick 时显示，触发位置提醒弹窗
 *   v2026-07-22 改动：图标从 Icons.Default.Tag 改为 Icons.Default.LocationOn，语义更清晰
 * - 分享按钮（Icons.Default.Share）：传入 onShareClick 时显示
 * - 删除按钮（Icons.Default.Delete）：传入 onDeleteClick 时显示
 *
 * 设计原则：
 * - 未传回调时不渲染对应按钮，让 SpaceEvenly 仅基于可见按钮做均匀分布，
 *   避免占位按钮挤压其他图标的间距
 * - 待办编辑页：传所有回调 → 渲染 7 个按钮（相机/麦克风/背景/@/#/分享/删除）
 * - 灵感编辑页：仅传相机/麦克风 → 2 按钮布局（画板/分享/删除已移至顶部工具栏）
 *
 * 已移除的功能：
 * - 字体格式（A/A）和列表格式按钮（富文本编辑功能）
 * - 字数统计显示
 *
 * 子任务添加功能已移至复选框编辑器的 "/" 命令和回车新建行，
 * 本工具栏不再包含独立的"添加子任务"按钮。
 *
 * @param backgroundColor 工具栏背景色，跟随页面整体背景色变化
 */
@Composable
fun EditToolbar(
    onPhotoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onBackgroundClick: (() -> Unit)? = null,
    onMentionClick: (() -> Unit)? = null,
    onLocationClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
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
            /** 工具栏图标行 - 使用 SpaceEvenly 实现均匀分布 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                /** 核心功能按钮组（均匀分布） */
                ToolbarIconBtn(imageVector = Icons.Default.PhotoCamera, contentDescription = "照片", onClick = onPhotoClick)
                ToolbarIconBtn(imageVector = Icons.Default.Mic, contentDescription = "语音", onClick = onVoiceClick)
                /**
                 * 背景色按钮：调用方传入 onBackgroundClick 时才渲染。
                 * 灵感编辑页已将此按钮移至顶部工具栏，不再传入此回调。
                 */
                if (onBackgroundClick != null) {
                    ToolbarIconBtn(imageVector = Icons.Default.Palette, contentDescription = "背景色", onClick = onBackgroundClick)
                }
                /**
                 * 关联按钮（@）：
                 * - 调用方传入 onMentionClick 时才渲染（待办编辑页）
                 * - 未传时不渲染（灵感编辑页）
                 * 这样可保证 SpaceEvenly 仅基于实际显示的按钮做均匀分布，
                 * 不会因为占位空按钮而挤压其他图标的间距。
                 */
                if (onMentionClick != null) {
                    ToolbarIconBtn(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = "关联",
                        onClick = onMentionClick
                    )
                }
                /**
                 * 位置按钮（#, Icons.Default.LocationOn）：传入 onLocationClick 时显示，触发位置提醒弹窗
                 *  v2026-07-22 改动：图标从 Icons.Default.Tag 改为 Icons.Default.LocationOn，
                 *  语义更清晰（地图标记点 = 位置），与功能"设置位置提醒"对齐。
                 */
                if (onLocationClick != null) {
                    ToolbarIconBtn(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "位置",
                        onClick = onLocationClick
                    )
                }
                /**
                 * 分享按钮：调用方传入 onShareClick 时才渲染。
                 * 灵感编辑页已将此按钮移至顶部工具栏，不再传入此回调。
                 */
                if (onShareClick != null) {
                    ToolbarIconBtn(imageVector = Icons.Default.Share, contentDescription = "分享", onClick = onShareClick)
                }
                /**
                 * 删除按钮：调用方传入 onDeleteClick 时才渲染。
                 * 灵感编辑页已将此按钮移至顶部工具栏，不再传入此回调。
                 */
                if (onDeleteClick != null) {
                    ToolbarIconBtn(imageVector = Icons.Default.Delete, contentDescription = "删除", onClick = onDeleteClick)
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
