package com.corgimemo.app.ui.components.appdrawer.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CustomDateType

/**
 * 分类操作菜单（BottomSheet）
 *
 * 触发流程：长按侧边栏分组 → CategoryAction.ShowMenu → MainScreen 显示此 sheet
 * 包含 3 个操作：置顶 / 编辑 / 删除（删除用错误色）
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.CategoryOperationSheet` 薄壳转发。
 *
 * @param sheetState BottomSheet 状态（默认 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，
 *                   可由调用方在 Composable 作用域自定义。注意：因本函数本身是 `@Composable`，
 *                   Composable 函数（如 `rememberModalBottomSheetState`）**可以**作为参数默认值。
 *                   Kotlin 编译器会自动在 Composable 作用域内解析该默认值，调用方无需感知）
 * @param category 被操作的分类
 * @param onPin 置顶回调
 * @param onRename 编辑回调
 * @param onDelete 删除回调
 * @param onDismiss 关闭 sheet 回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    category: Category,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 标题：分类名
            Text(
                text = category.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 置顶分组
            OperationOption(
                emoji = "📌",
                text = "置顶分组",
                onClick = {
                    onPin()
                    onDismiss()
                }
            )

            // 编辑分组
            OperationOption(
                emoji = "✏️",
                text = "编辑分组",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )

            // 删除分组（错误色）
            OperationOption(
                emoji = "🗑️",
                text = "删除分组",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 自定义日期类型操作菜单（BottomSheet）
 *
 * 与 CategoryOperationSheet 区别：**仅含编辑/删除两项，不含置顶**
 * 触发流程：长按侧边栏自定义日期类型 → DateTypeAction.ShowMenu → MainScreen 显示此 sheet
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.DateTypeOperationSheet` 薄壳转发。
 *
 * @param sheetState BottomSheet 状态（默认 `rememberModalBottomSheetState(skipPartiallyExpanded = true)`，
 *                   可由调用方在 Composable 作用域自定义）
 * @param customType 被操作的自定义类型
 * @param onRename 编辑回调
 * @param onDelete 删除回调
 * @param onDismiss 关闭 sheet 回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypeOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    customType: CustomDateType,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // 标题行：emoji + 类型名
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = customType.emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = customType.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // 编辑类型
            OperationOption(
                emoji = "✏️",
                text = "编辑类型",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )

            // 删除类型（错误色）
            OperationOption(
                emoji = "🗑️",
                text = "删除类型",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * 共享操作选项（私有，仅本文件内 2 个 sheet 共用）
 *
 * 图标 + 文字 + 点击事件的最小操作项。
 *
 * @param emoji 前置 emoji
 * @param text 文字
 * @param textColor 文字颜色（默认深色，删除操作用错误色）
 * @param onClick 点击回调
 */
@Composable
private fun OperationOption(
    emoji: String,
    text: String,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = textColor
        )
    }
}
