package com.corgimemo.app.ui.components.appdrawer.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 删除分类/类型确认对话框（侧边栏专用）
 *
 * 复用场景：
 * - 待办分组删除（默认 title="删除分组"，默认 message 提到"分组下的待办将变为未分类"）
 * - 特殊日期类型删除（MainScreen 调用时传 `title="删除类型"` + 自定义 message）
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.DeleteCategoryConfirmDialog` 薄壳转发。
 *
 * @param categoryName 分类/类型名称（用于默认 message 的文案插值）
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消回调
 * @param title 对话框标题（默认"删除分组"，日期类型调用时传"删除类型"）
 * @param message 正文提示（默认待办分组文案，日期类型调用时传专属文案）
 */
@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删除分组",
    message: String = "确定要删除分组「$categoryName」吗？\n该分组下的待办将变为未分类状态。"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFF1C1B1F)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}
