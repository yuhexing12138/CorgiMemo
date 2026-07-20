// 最近删除单条卡片
package com.corgimemo.app.ui.screens.recyclebin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color  // v3 新增：与首页 ambient shadow 同步使用 Color.Black
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.components.PriorityColors

/**
 * 最近删除单条卡片
 *
 * 展示：标题 / 分组 + 相对时间 / 行内显式按钮（恢复 + 永久删除）
 *
 * 视觉设计要点（v2026-07-20 升级）：
 * - 卡片圆角 12dp，1.5dp 优先级边框 + 2dp 彩色阴影
 * - 左侧 4dp 优先级竖条（与首页 TodoListItem 完全一致）
 * - 标题最多 2 行，超出 ellipsis
 * - 按钮靠右对齐，使用 spacedBy 8dp 间距
 * - 永久删除按钮使用 error 红色
 *
 * 优先级三联视觉来源：详见 [PriorityColors.priorityVisualOf]
 */
@Composable
fun DeletedTodoCard(
    item: DeletedTodoListItem,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit
) {
    /**
     * 优先级三联视觉（v2026-07-20 新增）
     *
     * 回收站待办视为未完成态（已删除非主页完成态），保持原始优先级色。
     */
    val priorityVisual = PriorityColors.priorityVisualOf(
        priority = item.priority,
        isCompleted = false
    )

    /** 左侧竖条宽度，与首页 PriorityBar 保持一致的 4dp */
    val barWidth = 4.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)  // v3: vertical 4→6dp 给 4dp shadow 留空间
            // 优先级边框：1.5dp + 优先级色 alpha 0.6f
            .border(
                width = 1.5.dp,
                color = priorityVisual.border.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            )
            // 优先级阴影：4dp + 浅黑环境阴影 + 优先级色 spot 阴影
            // v2026-07-20 v3 改动（与 TodoListItem 保持一致）：
            //   1) elevation 2→4dp，与首页 TodoListItem 静态阴影保持一致
            //   2) ambientColor 改为浅黑（alpha 0.12f）保证基础"底"阴影
            //   3) spotColor 用优先级色 alpha 0.6f 形成明显"边缘抬升感"
            //   4) ambient 与 spot 解耦（与首页同源）→ 阴影在浅色卡片上可见
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp),
                // v2026-07-20 v5 同步修复：spot color 现在是深色版优先级色（lerp 40% 黑色）
                // - 旧版 priorityVisual.shadow 是浅色 + 30% alpha，对比度严重不足
                // - 新版 priorityVisual.shadow 已经是深色不透明色，直接用 alpha 0.85 即可
                // - ambientColor 0.12→0.20 与首页同步
                ambientColor = Color.Black.copy(alpha = 0.20f),
                spotColor = priorityVisual.shadow.copy(alpha = 0.85f)  // v5: 0.6→0.85
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        // v2026-07-20 改动：让出默认阴影给外层 Modifier.shadow，避免双层阴影叠加
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        // 内部布局：左侧 4dp 竖条 + 右侧内容
        // v2026-07-20 新增：与首页 TodoListItem 形成统一的"竖条+内容"结构
        Row(modifier = Modifier.fillMaxWidth()) {
            // 左侧 4dp 竖条（自适应卡片高度）
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight()
                    .background(priorityVisual.bar)
            )

            // 右侧内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
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
