package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * 灵感编辑页底部导航栏
 *
 * 布局结构：
 * - 上行（可折叠）：RichTextFormatToolbar（仅当 isFormatExpanded=true 时显示）
 * - 下行（始终显示）：6 个核心按钮
 *   - 📷 相机（onPhotoClick）
 *   - 🎤 麦克风（onVoiceClick）
 *   - # 标签（onTagClick）—— v2026-07-22 改造：原"位置"按钮改为"添加标签"功能
 *   - @ 关联（onMentionClick）—— v2026-07-22 改造：触发 RelationPickerBottomSheet 多选弹窗（与待办编辑页一致）
 *   - 📍 位置（onLocationClick）—— v2026-07-22 新增：独立位置按钮，使用 Icons.Default.LocationOn 图标
 *   - ⋮ 格式（onFormatToggleClick，切换上行展开/折叠）
 *
 * **交互规则**：
 * - 只有 ⋮ 按钮切换工具栏展开/折叠
 * - 其他按钮的操作不影响工具栏状态
 * - 默认折叠（isFormatExpanded=false）
 *
 * @param isFormatExpanded 格式工具栏是否展开
 * @param richTextState 库的 RichTextState 实例（传给 RichTextFormatToolbar）
 * @param onPhotoClick 相机按钮回调
 * @param onVoiceClick 麦克风按钮回调
 * @param onTagClick 标签按钮回调（v2026-07-22 新增：原 onLocationClick 拆分而来，触发 TagPickerSheet）
 * @param onMentionClick 关联按钮回调（v2026-07-22 改造：触发 RelationPickerBottomSheet）
 * @param onLocationClick 位置按钮回调（v2026-07-22 新增：触发位置提醒弹窗）
 * @param onFormatToggleClick 格式按钮回调（切换展开/折叠）
 * @param onToggleBold 加粗回调
 * @param onToggleItalic 斜体回调
 * @param onToggleUnderline 下划线回调
 * @param onToggleStrikethrough 删除线回调
 * @param onInsertUnorderedList 无序列表回调
 * @param onInsertOrderedList 有序列表回调
 * @param onAlignLeft 左对齐回调
 * @param onAlignCenter 居中回调
 * @param onAlignRight 右对齐回调
 * @param onInsertLink 插入链接回调
 * @param onToggleCodeSpan 代码块回调
 * @param modifier Modifier
 * @param backgroundColor 工具栏背景色
 */
@Composable
fun InspirationEditBottomBar(
    isFormatExpanded: Boolean,
    richTextState: RichTextState,
    onPhotoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onTagClick: () -> Unit,
    onMentionClick: () -> Unit,
    onLocationClick: () -> Unit,
    onFormatToggleClick: () -> Unit,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onInsertUnorderedList: () -> Unit,
    onInsertOrderedList: () -> Unit,
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onInsertLink: () -> Unit = {},
    onToggleCodeSpan: () -> Unit = {},
    modifier: Modifier = Modifier,
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
            /** 上行：可折叠的格式工具栏 */
            AnimatedVisibility(
                visible = isFormatExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RichTextFormatToolbar(
                    state = richTextState,
                    onToggleBold = onToggleBold,
                    onToggleItalic = onToggleItalic,
                    onToggleUnderline = onToggleUnderline,
                    onToggleStrikethrough = onToggleStrikethrough,
                    onInsertUnorderedList = onInsertUnorderedList,
                    onInsertOrderedList = onInsertOrderedList,
                    onAlignLeft = onAlignLeft,
                    onAlignCenter = onAlignCenter,
                    onAlignRight = onAlignRight,
                    onInsertLink = onInsertLink,
                    onToggleCodeSpan = onToggleCodeSpan
                )
            }

            /** 下行：6 个核心按钮（始终显示） */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarButton(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "照片",
                    onClick = onPhotoClick
                )
                BottomBarButton(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音",
                    onClick = onVoiceClick
                )
                /**
                 * # 标签按钮（v2026-07-22 改造）：
                 * - 原"位置"按钮：图标沿用 Icons.Default.Tag，contentDescription 由"位置"改为"标签"
                 * - 回调由 onLocationClick 重命名为 onTagClick
                 * - 触发灵感独有功能 TagPickerSheet（添加/编辑标签）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "标签",
                    onClick = onTagClick
                )
                /**
                 * @ 关联按钮（v2026-07-22 改造）：
                 * - 由 MentionTriggerPopup（单选）升级为 RelationPickerBottomSheet（多选）
                 * - 行为与待办编辑页 @ 按钮保持一致
                 */
                BottomBarButton(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "关联",
                    onClick = onMentionClick
                )
                /**
                 * 📍 位置按钮（v2026-07-22 新增）：
                 * - 从原 # 位置按钮中独立出来，使用 Icons.Default.LocationOn
                 * - 触发位置提醒弹窗（LocationPicker + Geofence）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "位置",
                    onClick = onLocationClick
                )
                /** 格式按钮：高亮显示当工具栏展开时 */
                BottomBarButton(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "格式",
                    onClick = onFormatToggleClick,
                    tint = if (isFormatExpanded) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 底部栏图标按钮
 *
 * @param imageVector 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param tint 图标颜色
 */
@Composable
private fun BottomBarButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
