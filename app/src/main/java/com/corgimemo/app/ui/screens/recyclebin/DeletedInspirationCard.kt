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
 * 回收站中灵感删除项卡片
 *
 * 展示：标题 / 标签 + 相对时间 / 行内按钮（恢复 + 永久删除）
 */
@Composable
fun DeletedInspirationCard(
    item: DeletedInspirationListItem,
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
            Text(
                text = item.title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            // 副标题：标签 + 相对时间
            val tagText = if (item.tags.isNotEmpty()) item.tags.joinToString(" · ") else "无标签"
            Text(
                text = "$tagText · ${item.relativeTime}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
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
