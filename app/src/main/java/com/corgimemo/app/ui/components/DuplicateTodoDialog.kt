package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R
import com.corgimemo.app.viewmodel.HomeViewModel.DuplicateRange

/**
 * "创建待办副本" 中心弹窗
 *
 * 触发场景：待办页 → 三点菜单 → 创建待办副本
 *
 * 提供 3 个 RadioButton 复制范围选项：
 * - PENDING_ONLY：只复制未完成任务（status == 0）—— 默认
 * - KEEP_STATUS：复制全部，保留原 status（含 completedAt）
 * - RESET_STATUS：复制全部，重置 status 为 0
 *
 * UI 规范遵循：
 * - 容器：Material3 AlertDialog 默认（圆角 16dp、阴影 8dp），不显式设置 containerColor / shape
 * - 标题：FontWeight.SemiBold
 * - 确认按钮：MaterialTheme.colorScheme.primary（橙色 #FF9A5C）
 * - 取消按钮：MaterialTheme.colorScheme.onSurface
 * - RadioButton 选中色：MaterialTheme.colorScheme.primary
 * - 不加 icon 槽位（与 DeleteConfirmDialog 等保持一致）
 *
 * @param showDialog 是否显示弹窗
 * @param onDismiss 关闭弹窗回调（点取消 / 点遮罩 / 系统返回）
 * @param onConfirm 确认按钮回调，传入用户选中的 [DuplicateRange]
 * @param initialRange 弹窗打开时的默认选中项（默认 PENDING_ONLY）
 */
@Composable
fun DuplicateTodoDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DuplicateRange) -> Unit,
    initialRange: DuplicateRange = DuplicateRange.PENDING_ONLY,
) {
    // 弹窗每次重新打开时重置 selectedRange（用 showDialog 作为 key 触发 remember 重置）
    var selectedRange by remember(showDialog) { mutableStateOf(initialRange) }
    if (!showDialog) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.duplicate_dialog_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            // selectableGroup 让无障碍服务（如 TalkBack）能识别这是一组互斥选项
            Column(modifier = Modifier.selectableGroup()) {
                DuplicateRange.entries.forEach { range ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .selectable(
                                selected = (selectedRange == range),
                                // 点击整行触发选中（不只 RadioButton 圆点）
                                onClick = { selectedRange = range },
                                role = Role.RadioButton,
                            )
                            .padding(end = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (selectedRange == range),
                            // onClick 置空：点击由外层 Row.selectable 处理
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                            ),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(
                                when (range) {
                                    DuplicateRange.PENDING_ONLY -> R.string.duplicate_range_pending_only
                                    DuplicateRange.KEEP_STATUS -> R.string.duplicate_range_keep_status
                                    DuplicateRange.RESET_STATUS -> R.string.duplicate_range_reset_status
                                }
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedRange) }) {
                Text(
                    text = stringResource(R.string.common_confirm),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.common_cancel),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    )
}
