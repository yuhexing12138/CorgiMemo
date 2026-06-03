package com.corgimemo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FolderCopy // 使用 Folder 图标表示移动到分组
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable

/**
 * 待办操作底部弹窗 (Todo Action Sheet)
 *
 * 长按待办项时弹出的 ModalBottomSheet，提供以下操作：
 * - 📌 置顶待办
 * - ✏️ 编辑待办
 * - 📂 移动到分组（新增功能）
 * - 🖼️ 分享为图片
 * - 📋 批量选择
 * - ────────────────
 * - 🗑️ 删除待办（红色警告）
 *
 * 该组件内部使用 ActionBottomSheet 实现，保持接口简洁。
 *
 * @param sheetState 底部弹窗状态
 * @param onDismiss 关闭回调
 * @param onPin 置顶回调
 * @param onEdit 编辑回调
 * @param onMoveToCategory 移动到分组回调（新增）
 * @param onShare 分享回调
 * @param onBatchSelect 批量选择回调
 * @param onDelete 删除回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoActionSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPin: () -> Unit = {},
    onEdit: () -> Unit = {},
    onMoveToCategory: () -> Unit = {}, /** 新增：移动到分组回调 */
    onShare: () -> Unit = {},
    onBatchSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    /** 定义操作项列表 */
    val actions = listOf(
        ActionItem(
            icon = Icons.Default.PushPin,
            text = "📌 置顶待办",
            onClick = onPin
        ),
        ActionItem(
            icon = Icons.Default.Edit,
            text = "✏️ 编辑待办",
            onClick = onEdit
        ),
        ActionItem(
            icon = Icons.Default.FolderCopy, // 使用文件夹图标
            text = "📂 移动到分组",
            onClick = onMoveToCategory
        ),
        ActionItem(
            icon = Icons.Default.Share,
            text = "🖼️ 分享为图片",
            onClick = onShare
        ),
        ActionItem(
            icon = Icons.Default.SelectAll,
            text = "📋 批量选择",
            onClick = onBatchSelect
        ),
        ActionItem(
            icon = Icons.Default.Delete,
            text = "🗑️ 删除待办",
            isDestructive = true, /** 标记为破坏性操作，显示为红色 */
            onClick = onDelete
        )
    )

    /** 调用通用 ActionBottomSheet 组件 */
    ActionBottomSheet(
        sheetState = sheetState,
        title = null, // 不显示标题
        actions = actions,
        dividerIndex = 4, // 在第 5 个操作项（批量选择）之后插入分割线
        onDismiss = onDismiss
    )
}
