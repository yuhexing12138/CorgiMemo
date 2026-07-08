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
