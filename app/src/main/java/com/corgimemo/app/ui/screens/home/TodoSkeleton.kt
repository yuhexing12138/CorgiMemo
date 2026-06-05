package com.corgimemo.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.components.SkeletonButton
import com.corgimemo.app.ui.components.SkeletonCircle
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonText

/**
 * 待办页面专属骨架屏组件
 *
 * 模拟待办页面的完整布局结构，包括：
 * - 搜索栏
 * - 过滤器按钮（全部/待办/已完成）
 * - 待办卡片列表（带复选框、标题、副标题、标签）
 *
 * 布局与真实 TodoListItem 组件完全一致，
 * 确保从骨架屏到真实内容的平滑过渡。
 *
 * @param itemCount 显示的骨架卡片数量，默认 4 个
 * @param modifier 修饰符
 */
@Composable
fun TodoSkeleton(
    itemCount: Int = 4,
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

        // 2. 过滤器按钮骨架（全部 | 待办 | 已完成）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(3) {
                SkeletonButton(width = 60.dp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. 待办卡片列表骨架
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(itemCount) { index ->
                TodoItemSkeleton()
            }
        }
    }
}

/**
 * 单个待办项骨架屏
 *
 * 模拟 TodoListItem 的布局：
 * ┌─────────────────────────────────────┐
 * │ ○  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │  ← 标题行
 * │    ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭  │  ← 内容行
 * │    🏷️▭▭▭  ⏰▭▭:▭▭              │  ← 元信息行
 * └─────────────────────────────────────┘
 */
@Composable
private fun TodoItemSkeleton() {
    com.corgimemo.app.ui.components.SkeletonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧复选框骨架（圆形，24dp）
            SkeletonCircle(size = 24.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // 右侧内容区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 标题行（70% 宽度，20dp 高度）
                SkeletonText(width = 0.7f, height = 20.dp)

                // 内容/副标题行（90% 宽度，14dp 高度）
                SkeletonText(width = 0.9f, height = 14.dp)

                // 底部元信息行：标签 + 时间
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 分类标签骨架
                    SkeletonButton(width = 50.dp, height = 24.dp)

                    // 时间占位符骨架
                    SkeletonText(width = 0.25f, height = 12.dp)
                }
            }
        }
    }
}
