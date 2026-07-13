// 回收站中日期删除项卡片
package com.corgimemo.app.ui.screens.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.DateCategory

/**
 * 回收站中日期删除项卡片
 *
 * 展示：标题 / 分类 + 相对时间 / 行内按钮（恢复 + 永久删除）
 *
 * 设计要点：
 * - 卡片圆角 12dp，阴影 1dp（与 DeletedTodoCard / DeletedInspirationCard 风格一致）
 * - 标题最多 2 行，超出 ellipsis
 * - 按钮靠右对齐，使用 spacedBy 8dp 间距
 * - 永久删除按钮使用 error 红色
 * - 分类字段尝试解析为 DateCategory.displayName，无法解析时显示原始字符串
 */
@Composable
fun DeletedDateCard(
    item: DeletedDateListItem,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题
            Text(
                text = item.title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // 副标题：分类 + 相对时间
            Text(
                text = "${resolveCategoryDisplay(item.category)} · ${item.relativeTime}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
            // 行内显式按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onRestore) {
                    Text("↩ 恢复")
                }
                TextButton(onClick = onPermanentDelete) {
                    Text(
                        "🗑 永久删除",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 解析日期分类的显示名称
 *
 * 数据库中 category 字段可能是：
 * - DateCategory 枚举名（如 "BIRTHDAY"）→ 返回对应 displayName（如 "生日"）
 * - 自定义字符串（如 "CUSTOM:xxx" 或其他）→ 返回原始字符串
 */
private fun resolveCategoryDisplay(category: String): String {
    return try {
        DateCategory.valueOf(category).displayName
    } catch (e: IllegalArgumentException) {
        category.ifBlank { "未分类" }
    }
}
