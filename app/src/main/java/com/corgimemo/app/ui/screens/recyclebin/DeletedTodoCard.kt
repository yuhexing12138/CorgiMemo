// 最近删除单条卡片
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

/**
 * 最近删除单条卡片
 *
 * 展示：标题 / 分组 + 相对时间 / 行内显式按钮（恢复 + 永久删除）
 *
 * 设计要点：
 * - 卡片圆角 12dp，阴影 1dp（与 HomeScreen 卡片风格保持一致）
 * - 标题最多 2 行，超出 ellipsis
 * - 按钮靠右对齐，使用 spacedBy 8dp 间距
 * - 永久删除按钮使用 error 红色
 */
@Composable
fun DeletedTodoCard(
    item: DeletedTodoListItem,
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
            // 副标题：分组 + 相对时间
            Text(
                text = buildSubtitle(item),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
 * 构造副标题："分组 · 相对时间"
 *
 * 分组为空（未分类）时显示"未分类"。
 */
private fun buildSubtitle(item: DeletedTodoListItem): String {
    val category = item.categoryName ?: "未分类"
    return "$category · ${item.relativeTime}"
}
