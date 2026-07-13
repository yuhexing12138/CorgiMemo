package com.corgimemo.app.ui.screens.date

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.components.SkeletonButton
import com.corgimemo.app.ui.components.SkeletonCircle
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonText

/**
 * 特殊日期页面专属骨架屏组件
 *
 * 模拟日期页面的完整布局结构，包括：
 * - 搜索栏
 * - 三组分类标题（倒计时/正计时/已归档）
 * - 日期卡片列表（带优先级圆点、标题、倒计时）
 *
 * 2026-07-13 重构：第三组标题由"已过期"改为"已归档"（与 DateSectionHeader 保持一致）。
 *
 * 布局与真实 SpecialDateCard 组件完全一致，
 * 确保从骨架屏到真实内容的平滑过渡。
 *
 * @param modifier 修饰符
 */
@Composable
fun SpecialDateSkeleton(
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

        // 2. 三组分类列表骨架
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 倒计时分组
            item { DateGroupTitleSkeleton("倒计时") }
            items(2, key = { "countdown_skeleton_$it" }) {
                DateItemSkeleton(color = MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
            }

            // 正计时分组
            item { DateGroupTitleSkeleton("正计时") }
            item { DateItemSkeleton(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) }

            // 已归档分组（2026-07-13：原"已过期"改为"已归档"）
            item { DateGroupTitleSkeleton("已归档") }
            items(2, key = { "archived_skeleton_$it" }) {
                DateItemSkeleton(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

/**
 * 日期分组标题骨架
 *
 * 模拟 DateGroupHeader 的样式
 *
 * @param title 分组标题文本（用于确定宽度）
 */
@Composable
private fun DateGroupTitleSkeleton(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        SkeletonText(
            width = 0.35f,
            height = 18.dp
        )
    }
}

/**
 * 单个日期项骨架屏
 *
 * 模拟 SpecialDateCard 的布局：
 * ┌─────────────────────────────────────┐
 * │ ●  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭   ▭▭天后     │  ← 标题行
 * │    📂▭▭▭▭▭▭▭▭▭▭▭              │  ← 分类行
 * └─────────────────────────────────────┘
 *
 * @param color 左侧优先级指示圆点的颜色
 */
@Composable
private fun DateItemSkeleton(color: androidx.compose.ui.graphics.Color) {
    com.corgimemo.app.ui.components.SkeletonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧优先级指示圆点（12dp）
            SkeletonCircle(size = 12.dp)

            Spacer(modifier = Modifier.width(12.dp))

            // 右侧内容区域
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 标题行（60% 宽度，18dp 高度）
                SkeletonText(width = 0.6f, height = 18.dp)

                // 分类/副标题行（40% 宽度，14dp 高度）
                SkeletonText(width = 0.4f, height = 14.dp)
            }

            // 右侧倒计时文字骨架
            SkeletonText(
                width = 0.15f,
                height = 14.dp
            )
        }
    }
}
