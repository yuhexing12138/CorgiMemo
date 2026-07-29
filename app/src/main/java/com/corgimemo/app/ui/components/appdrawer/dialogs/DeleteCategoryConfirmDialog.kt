package com.corgimemo.app.ui.components.appdrawer.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/**
 * 删除分类/类型确认对话框（侧边栏专用）
 *
 * 复用场景：
 * - 待办分组删除（默认 title="删除分组"，message 显示关联 todo 数量）
 * - 特殊日期类型删除（MainScreen 调用时传 `title="删除类型"` + 自定义 message）
 *
 * 外部访问方式：通过 `com.corgimemo.app.ui.components.DeleteCategoryConfirmDialog` 薄壳转发。
 *
 * **v2026-07-29 改造**：按 `docs/superpowers/specs/UI设计规范.md` 12.1.11 章节重写
 * - 容器：Material3 `AlertDialog` 默认样式（不显式指定 containerColor/shape）
 * - title：`FontWeight.SemiBold`，不显式指定颜色（跟随 onSurface）
 * - text：不显式指定颜色（跟随 onSurfaceVariant）
 * - confirmButton：`TextButton`，文字颜色 `Color(0xFFFF6B6B)`（破坏性操作警示色）
 * - dismissButton：`TextButton`，不显式指定颜色（跟随 onSurfaceVariant）
 * - 新增 `todoCount` 参数，正文动态显示"该分组下的 N 条待办将变为未分组状态"
 *
 * @param categoryName 分类/类型名称（用于 message 的文案插值）
 * @param todoCount 关联待办数量（v2026-07-29 新增，仅待办分组删除时传入，>0 时显示具体数量）
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消回调
 * @param title 对话框标题（默认"删除分组"，日期类型调用时传"删除类型"）
 * @param message 正文提示（默认 null → 自动按 todoCount 生成待办分组文案；
 *        日期类型调用时传专属文案，覆盖默认逻辑）
 */
@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    todoCount: Int = 0,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删除分组",
    message: String? = null
) {
    // v2026-07-29 改造：按 todoCount 动态生成正文文案（仅在 message 未显式指定时）
    val finalMessage = message ?: when {
        todoCount > 0 ->
            "确定要删除分组「$categoryName」吗？\n该分组下的 $todoCount 条待办将变为未分组状态。"
        else ->
            "确定要删除分组「$categoryName」吗？\n该分组下无待办，删除后不可恢复。"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            // v2026-07-29 改造：按 UI 规范 12.1.11.3 — SemiBold 字重，不显式指定颜色
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            // v2026-07-29 改造：按 UI 规范 12.1.11.3 — 不显式指定颜色（跟随 onSurfaceVariant）
            Text(text = finalMessage)
        },
        confirmButton = {
            // v2026-07-29 改造：按 UI 规范 12.1.11.3 — 破坏性操作警示色 Color(0xFFFF6B6B)
            TextButton(onClick = onConfirm) {
                Text("删除", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            // v2026-07-29 改造：按 UI 规范 12.1.11.3 — 不显式指定颜色（跟随 onSurfaceVariant）
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
