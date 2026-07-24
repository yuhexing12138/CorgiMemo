package com.corgimemo.app.ui.components.appdrawer.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 重命名分组弹窗（侧边栏专用）
 *
 * 复用场景：
 * - 待办分组重命名（CategoryGroupSection 长按 → Rename 触发）
 * - 特殊日期类型重命名（DateTypeFilterSection 长按 → Rename 触发）
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.RenameCategoryDialog` 薄壳转发。
 *
 * @param currentName 当前名称（用于初始化输入框）
 * @param onConfirm 确认回调，返回新名称（已 trim）
 * @param onDismiss 取消 / 关闭弹窗回调
 */
@Composable
fun RenameCategoryDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // 状态管理：编辑中的名称（默认填充当前名）
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名分组",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiColors.Primary,
                    focusedLabelColor = UiColors.Primary,
                    cursorColor = UiColors.Primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}
