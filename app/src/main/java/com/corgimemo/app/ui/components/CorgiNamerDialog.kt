package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 柯基命名对话框组件
 *
 * @param onConfirm 确认命名的回调，接收名字参数
 * @param onDismiss 取消对话框的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorgiNamerDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 输入的名字状态
    var name by remember { mutableStateOf("") }

    // 验证输入是否有效（1-8个字符）
    val isValidName = name.length in 1..8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 可爱的柯基表情
                Text(
                    text = "\uD83D\uDC3A",
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "给你的柯基起个名字吧！",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        // 限制输入长度为8个字符
                        if (it.length <= 8) {
                            name = it
                        }
                    },
                    placeholder = { Text(text = "请输入名字（1-8个字符）") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (isValidName) {
                                onConfirm(name)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "${name.length}/8",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (name.isNotEmpty() && !isValidName) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    isError = name.isNotEmpty() && !isValidName
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = isValidName,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(text = "稍后")
            }
        }
    )
}