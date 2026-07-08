package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R

/**
 * 分享方式选择弹窗
 *
 * 当用户需要分享多个待办时显示，让用户在"合并为一张"和"一条条"之间选择。
 *
 * @param count 待分享的待办数量
 * @param enableMerge 是否启用"合并分享"按钮（>10 时为 false）
 * @param onDismiss 关闭弹窗
 * @param onMerge 选择"合并分享"
 * @param onOneByOne 选择"一条条分享"
 */
@Composable
fun ShareModeDialog(
    count: Int,
    enableMerge: Boolean,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onOneByOne: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.share_mode_title)) },
        text = {
            Column {
                Text(
                    text = if (enableMerge) {
                        stringResource(id = R.string.share_mode_subtitle_normal, count)
                    } else {
                        stringResource(id = R.string.share_mode_subtitle_too_many, count)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onMerge,
                enabled = enableMerge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(text = stringResource(id = R.string.share_mode_merge))
            }
        },
        dismissButton = {
            Column {
                OutlinedButton(onClick = onOneByOne) {
                    Text(text = stringResource(id = R.string.share_mode_one_by_one))
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(id = R.string.share_mode_cancel))
                }
            }
        }
    )
}

/**
 * 部分未保存分组时的确认弹窗
 *
 * 场景：用户点分享时，有 N 个分组未保存。
 * 让用户选择"仅分享已保存的 M 条"还是"先去保存"。
 *
 * @param totalGroups 总分组数（含未保存的）
 * @param unsavedCount 未保存分组数
 * @param savedCount 已保存分组数（含主 todo）
 * @param onDismiss 关闭弹窗
 * @param onShareSavedOnly 确认"仅分享已保存"，之后会再走 ShareModeDialog
 */
@Composable
fun PartialSaveConfirmDialog(
    totalGroups: Int,
    unsavedCount: Int,
    savedCount: Int,
    onDismiss: () -> Unit,
    onShareSavedOnly: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.partial_save_title)) },
        text = {
            Text(
                text = stringResource(
                    id = R.string.partial_save_subtitle,
                    totalGroups,
                    unsavedCount,
                    savedCount
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(onClick = onShareSavedOnly) {
                Text(text = stringResource(id = R.string.partial_save_share_saved_only))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.partial_save_go_save))
            }
        }
    )
}
