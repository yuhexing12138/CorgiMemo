package com.corgimemo.app.ui.components.appdrawer.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 添加/编辑分组弹窗（侧边栏专用）
 *
 * 同时支持两种调用场景：
 * 1. 普通添加分组（[showEmojiPicker] = false）：仅输入名称，回调 [onConfirmName]
 * 2. 完整添加分组（[showEmojiPicker] = true）：输入名称 + 选择 emoji，回调 [onConfirm]
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.AddCategoryDialog` 薄壳转发
 * 保持 MainScreen 等调用方的调用方式不变。
 *
 * @param onConfirmName 仅返回名称的简化回调（showEmojiPicker = false 时使用）
 * @param onConfirm 返回 (name, emoji) 的完整回调（showEmojiPicker = true 时使用）
 * @param onDismiss 关闭弹窗
 * @param title 弹窗标题
 * @param label 输入框标签
 * @param showEmojiPicker 是否显示 emoji 选择器
 */
@Composable
fun AddCategoryDialog(
    onConfirmName: (String) -> Unit = {},
    onConfirm: (String, String) -> Unit = { name, _ -> onConfirmName(name) },
    onDismiss: () -> Unit,
    title: String = "新建分组",
    label: String = "分组名称",
    showEmojiPicker: Boolean = false
) {
    // 状态管理：输入框名称 + 选中 emoji
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📅") }

    val presetEmojis = listOf("📅", "🎂", "💕", "🎉", "🌱", "📚", "💼", "🎮", "✈️", "🐹", "🏠", "⭐")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UiColors.Primary,
                        focusedLabelColor = UiColors.Primary,
                        cursorColor = UiColors.Primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showEmojiPicker) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "选择图标",
                        fontSize = 14.sp,
                        color = Color(0xFF79747E),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Emoji 选择网格（横向滚动）
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presetEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (emoji == selectedEmoji) UiColors.PrimaryLight
                                        else Color(0xFFF5F5F5)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (showEmojiPicker) {
                            onConfirm(name.trim(), selectedEmoji)
                        } else {
                            onConfirmName(name.trim())
                        }
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
