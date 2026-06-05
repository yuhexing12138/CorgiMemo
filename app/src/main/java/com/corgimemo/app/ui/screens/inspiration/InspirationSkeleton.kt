package com.corgimemo.app.ui.screens.inspiration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.components.SkeletonButton
import com.corgimemo.app.ui.components.SkeletonCard
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonText

/**
 * 灵感页面专属骨架屏组件
 *
 * 模拟灵感页面的完整布局结构，包括：
 * - 搜索栏
 * - 时间线分组标题（日期）
 * - 灵感卡片列表（带标题、内容预览、元信息）
 *
 * 布局与真实 InspirationCard 组件完全一致，
 * 确保从骨架屏到真实内容的平滑过渡。
 *
 * @param groupCount 分组数量，默认 1 个
 * @param itemsPerGroup 每组卡片数量，默认 2 个
 * @param modifier 修饰符
 */
@Composable
fun InspirationSkeleton(
    groupCount: Int = 1,
    itemsPerGroup: Int = 2,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. 搜索栏骨架
        SkeletonSearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 2. 时间线分组列表骨架
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(groupCount) { groupIndex ->
                // 分组标题（日期文字骨架）
                item(key = "group_header_$groupIndex") {
                    TimelineGroupHeaderSkeleton()
                }

                // 该分组下的灵感卡片
                items(itemsPerGroup, key = { "inspiration_skeleton_${groupIndex}_$it" }) {
                    InspirationItemSkeleton()
                }
            }
        }
    }
}

/**
 * 时间线分组标题骨架
 *
 * 模拟 TimelineGroupHeader 的日期显示样式
 */
@Composable
private fun TimelineGroupHeaderSkeleton() {
    SkeletonText(
        width = 0.5f,
        height = 18.dp,
        modifier = Modifier.padding(vertical = 12.dp)
    )
}

/**
 * 单个灵感项骨架屏
 *
 * 模拟 InspirationCard 的布局：
 * ┌─────────────────────────────────────┐
 * │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │  ← 标题行
 * │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │  ← 内容行1
 * │  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭            │  ← 内容行2
 * │  📎▭▭▭  🏷️▭▭▭▭  ⏰▭▭:▭▭       │  ← 元信息行
 * └─────────────────────────────────────┘
 */
@Composable
private fun InspirationItemSkeleton() {
    SkeletonCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 标题行（85% 宽度，18dp 高度）
            SkeletonText(width = 0.85f, height = 18.dp)

            // 内容预览行1（100% 宽度，14dp 高度）
            SkeletonText(width = 1f, height = 14.dp)

            // 内容预览行2（70% 宽度，14dp 高度）
            SkeletonText(width = 0.7f, height = 14.dp)

            Spacer(modifier = Modifier.height(4.dp))

            // 底部元信息行：图片 | 标签 | 时间
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 图片占位符图标骨架
                SkeletonButton(width = 24.dp, height = 24.dp)

                // 标签占位符骨架
                SkeletonButton(width = 50.dp, height = 24.dp)

                Spacer(modifier = Modifier.weight(1f))

                // 时间占位符骨架
                SkeletonText(width = 0.2f, height = 12.dp)
            }
        }
    }
}
