package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 编辑页底部工具栏组件
 *
 * 提供待办编辑页的富文本编辑功能入口，
 * 包含 6 个工具按钮：照片、文本格式、加粗、列表、待办子项、背景色。
 * 可选支持撤销/重做（Undo/Redo）功能按钮。
 *
 * 工具栏采用水平均匀分布布局，每个按钮包含图标和标签。
 * 按钮具有悬停效果和点击反馈，符合 Material Design 交互规范。
 *
 * 使用示例：
 * ```kotlin
 * Scaffold(
 *     topBar = { /* 增强的标题栏 */ },
 *     bottomBar = {
 *         EditToolbar(
 *             onPhotoClick = { /* 打开图片选择器 */ },
 *             onTextFormatClick = { /* 显示文本格式选项 */ },
 *             onBoldClick = { /* 切换加粗状态 */ },
 *             onListClick = { /* 插入列表标记 */ },
 *             onTodoClick = { /* 插入子待办复选框 */ },
 *             onBackgroundClick = { /* 显示背景色选择器 */ },
 *             // 可选：Undo/Redo 支持
 *             onUndoClick = viewModel::undo,
 *             onRedoClick = viewModel::redo,
 *             canUndo = true,
 *             canRedo = false
 *         )
 *     }
 * ) { padding ->
 *     // 内容区域
 * }
 * ```
 *
 * @param onPhotoClick 照片按钮点击回调（添加图片附件）
 * @param onTextFormatClick 文本格式按钮点击回调（字体样式、大小等）
 * @param onBoldClick 加粗按钮点击回调（切换文字加粗状态）
 * @param onListClick 列表按钮点击回调（插入有序/无序列表）
 * @param onTodoClick 待办按钮点击回调（在正文中插入子待办复选框）
 * @param onBackgroundClick 背景按钮点击回调（选择内容区域背景色）
 * @param onUndoClick 撤销按钮点击回调（可选，不提供则不显示 Undo 按钮）
 * @param onRedoClick 重做按钮点击回调（可选，不提供则不显示 Redo 按钮）
 * @param canUndo 是否可以撤销（控制 Undo 按钮启用状态）
 * @param canRedo 是否可以重做（控制 Redo 按钮启用状态）
 */
@Composable
fun EditToolbar(
    onPhotoClick: () -> Unit,
    onTextFormatClick: () -> Unit,
    onBoldClick: () -> Unit,
    onListClick: () -> Unit,
    onTodoClick: () -> Unit,
    onBackgroundClick: () -> Unit,
    onUndoClick: (() -> Unit)? = null,
    onRedoClick: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column {
            /** Undo/Redo 快捷操作行（仅在提供回调时显示） */
            if (onUndoClick != null || onRedoClick != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** ↩️ 撤销按钮 */
                    if (onUndoClick != null) {
                        ToolButton(
                            icon = "↩️",
                            label = "撤销",
                            onClick = onUndoClick,
                            isPrimary = false,
                            enabled = canUndo
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    /** ↪️ 重做按钮 */
                    if (onRedoClick != null) {
                        ToolButton(
                            icon = "↪️",
                            label = "重做",
                            onClick = onRedoClick,
                            isPrimary = false,
                            enabled = canRedo
                        )
                    }
                }
            }

            /** 主工具栏按钮行 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
            /** 📷 照片按钮 - P1 优先级 */
            ToolButton(
                icon = "📷",
                label = "照片",
                onClick = onPhotoClick,
                isPrimary = true
            )

            /** 📝 文本格式按钮 - P2 优先级 */
            ToolButton(
                icon = "📝",
                label = "文本",
                onClick = onTextFormatClick,
                isPrimary = false
            )

            /** **B** 加粗按钮 - P2 优先级 */
            ToolButton(
                icon = "**B**",
                label = "加粗",
                onClick = onBoldClick,
                isPrimary = false
            )

            /** ☰ 列表按钮 - P2 优先级 */
            ToolButton(
                icon = "☰",
                label = "列表",
                onClick = onListClick,
                isPrimary = false
            )

            /** ☑️ 待办子项按钮 - P1 优先级 */
            ToolButton(
                icon = "☑️",
                label = "待办",
                onClick = onTodoClick,
                isPrimary = true
            )

            /** 🎨 背景按钮 - P3 优先级 */
            ToolButton(
                icon = "🎨",
                label = "背景",
                onClick = onBackgroundClick,
                isPrimary = false
            )
        }
        }
    }
}

/**
 * 工具栏按钮组件
 *
 * 单个工具按钮的 UI 实现，垂直排列图标和文本标签。
 * 支持主要/次要两种视觉样式，主要按钮使用暖橙色强调。
 * 支持启用/禁用状态（Undo/Redo 按钮在无历史记录时禁用）。
 *
 * @param icon 按钮图标（支持 Emoji 或文字符号）
 * @param label 按钮下方标签文本
 * @param onClick 点击回调
 * @param isPrimary 是否为主要功能按钮（影响视觉强调程度）
 * @param enabled 是否启用（false 时按钮变灰且不可点击，默认 true）
 */
@Composable
private fun ToolButton(
    icon: String,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val backgroundColor = if (isPrimary) {
        Color(0xFFFFF3E0) // 浅暖橙色背景（主要功能）
    } else {
        Color.Transparent // 透明背景（次要功能）
    }

    val contentColor = if (isPrimary) {
        Color(0xFFE88A4D) // 深暖橙色文字（主要功能）
    } else if (enabled) {
        Color(0xFF666666) // 灰色文字（次要功能，启用状态）
    } else {
        Color(0xFFCCCCCC) // 浅灰色文字（禁用状态）
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        /** 图标 */
        Text(
            text = icon,
            fontSize = if (icon.length > 2) 18.sp else 22.sp, // 根据图标长度调整大小
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        /** 标签 */
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor.copy(alpha = 0.9f) // 略微降低透明度使文字更柔和
        )
    }
}

/**
 * 分隔线组件（用于工具栏分组，可选）
 *
 * 在工具栏的不同功能组之间插入细小分隔线，
 * 增强视觉层次感。
 *
 * @param modifier 外层 Modifier
 */
@Composable
fun ToolbarDivider(
    modifier: Modifier = Modifier
) {
    Spacer(
        modifier = modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}
