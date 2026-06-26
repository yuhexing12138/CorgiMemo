package com.corgimemo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PushPin
// 注意：Material Icons 中没有 "LightbulbOutline"，实际对应的是
// androidx.compose.material.icons.outlined.Lightbulb
// 这里按 Task 4 说明使用 Outlined 版本，与弹窗内其他图标风格协调
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable

/**
 * 更多选项底部弹窗（More Options Sheet）
 *
 * 多选模式底部 ⋮ 按钮触发的 6 项菜单：
 * 1. 完成 — 触发批量完成并退出多选
 * 2. 置顶 — 批量置顶
 * 3. 优先级 — 弹 PriorityPickerSheet
 * 4. 提醒时间 — 弹 ReminderPickerBottomSheet
 * 5. 创建副本 — 批量复制
 * 6. 转换为灵感 — Toast "功能开发中"（暂未实现）
 *
 * 复用现有 [ActionBottomSheet] 组件实现，无需新建 ModalBottomSheet 容器。
 *
 * @param sheetState 弹窗状态
 * @param onDismiss 关闭弹窗回调
 * @param onComplete 完成回调（批量完成 + 退出多选）
 * @param onPin 置顶回调
 * @param onPriority 优先级回调（弹 PriorityPickerSheet）
 * @param onReminder 提醒时间回调（弹 ReminderPickerBottomSheet）
 * @param onDuplicate 创建副本回调
 * @param onConvertToInspiration 转换为灵感回调（暂为 Toast）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onPin: () -> Unit,
    onPriority: () -> Unit,
    onReminder: () -> Unit,
    onDuplicate: () -> Unit,
    onConvertToInspiration: () -> Unit
) {
    /**
     * 菜单项列表（按从上到下顺序排列）
     *
     * 顺序与设计文档一致：
     * 1. 完成（Check）
     * 2. 置顶（PushPin）
     * 3. 优先级（Flag）
     * 4. 提醒时间（Alarm）
     * 5. 创建副本（ContentCopy）
     * 6. 转换为灵感（Lightbulb）
     */
    val actions = listOf(
        ActionItem(
            icon = Icons.Default.Check,
            text = "完成",
            onClick = onComplete
        ),
        ActionItem(
            icon = Icons.Default.PushPin,
            text = "置顶",
            onClick = onPin
        ),
        ActionItem(
            icon = Icons.Default.Flag,
            text = "优先级",
            onClick = onPriority
        ),
        ActionItem(
            icon = Icons.Default.Alarm,
            text = "提醒时间",
            onClick = onReminder
        ),
        ActionItem(
            icon = Icons.Default.ContentCopy,
            text = "创建副本",
            onClick = onDuplicate
        ),
        ActionItem(
            icon = Icons.Outlined.Lightbulb,
            text = "转换为灵感",
            onClick = onConvertToInspiration
        )
    )

    /**
     * 复用 ActionBottomSheet，不插入分割线
     *
     * - title = null：不显示标题
     * - dividerIndex = null：菜单项之间不插入分割线，保持紧凑列表
     */
    ActionBottomSheet(
        sheetState = sheetState,
        title = null,
        actions = actions,
        dividerIndex = null,
        onDismiss = onDismiss
    )
}
