package com.corgimemo.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.corgimemo.app.R
import com.corgimemo.app.ui.components.SkeletonButton
import com.corgimemo.app.ui.components.SkeletonCircle
import com.corgimemo.app.ui.components.SkeletonDefaults
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonSectionHeader
import com.corgimemo.app.ui.components.SkeletonText
import com.corgimemo.app.ui.components.SectionHeaderColors

/**
 * 待办页面专属骨架屏组件
 *
 * 模拟待办页面的完整布局结构，包括：
 * - 搜索栏（horizontal=20.dp，bottom=ui_search_bar_bottom_margin）
 * - 3 个可折叠分区头（置顶/待完成/已完成）
 * - 4 个待办卡片占位（置顶区 1 个 + 待完成区 2 个 + 已完成区 1 个）
 *
 * 布局与真实 HomeScreen 完全一致：
 * - 列表水平内边距 8.dp（← ZonedReorderableLazyColumn modifier）
 * - 卡片间距 8.dp（← itemSpacing = 8.dp）
 * - 卡片圆角 16.dp（← SwipeableTodoBox.cornerRadiusDp）
 * - 分区头颜色：置顶=primary，待完成=#5A8DEE，已完成=#7EC8A0（← SectionHeaderColors）
 * - 底部留白 80.dp（← footerContent）
 *
 * @param modifier 修饰符
 */
@Composable
fun TodoSkeleton(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // 1. 搜索栏骨架（与 HomeScreen SearchBar padding 一致）
        SkeletonSearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin))
        )

        // 2. 列表骨架（含分区头 + 卡片）
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),  // ← 必须与 ZonedReorderableLazyColumn padding 一致
            verticalArrangement = Arrangement.spacedBy(8.dp)  // ← 必须与 itemSpacing 一致
        ) {
            // 置顶区
            item(key = "skeleton_header_pinned") {
                SkeletonSectionHeader(
                    label = "置顶",
                    color = SectionHeaderColors.Pinned
                )
            }
            item(key = "skeleton_card_pinned_1") {
                TodoItemSkeleton()
            }

            // 待完成区
            item(key = "skeleton_header_pending") {
                SkeletonSectionHeader(
                    label = "待完成",
                    color = SectionHeaderColors.Pending
                )
            }
            item(key = "skeleton_card_pending_1") {
                TodoItemSkeleton()
            }
            item(key = "skeleton_card_pending_2") {
                TodoItemSkeleton()
            }

            // 已完成区
            item(key = "skeleton_header_completed") {
                SkeletonSectionHeader(
                    label = "已完成",
                    color = SectionHeaderColors.Completed
                )
            }
            item(key = "skeleton_card_completed_1") {
                TodoItemSkeleton()
            }

            // 底部留白（与 HomeScreen footerContent 一致，避免 FAB 遮挡）
            item(key = "skeleton_footer") {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

/**
 * 单个待办项骨架屏
 *
 * 模拟 SwipeableTodoBox + TodoListItem 的布局：
 * ┌─────────────────────────────────────┐
 * │ ○  ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭         │  ← 标题行
 * │    ▭▭▭▭▭▭▭▭▭▭▭▭▭▭▭             │  ← 内容行
 * │    🏷️▭▭▭  ⏰▭▭▭▭              │  ← 元信息行
 * └─────────────────────────────────────┘
 *
 * 卡片圆角 16.dp 匹配 SwipeableTodoBox.cornerRadiusDp，
 * 不复用 SkeletonCard（其固定 20.dp）。
 */
@Composable
private fun TodoItemSkeleton() {
    // 独立常量（方案 A，注释锚点指向真实组件）
    val cardCornerRadius = 16.dp  // ← 必须与 SwipeableTodoBox.cornerRadiusDp 保持一致

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCornerRadius))
            .background(SkeletonDefaults.SkeletonColor)
            .padding(16.dp)
    ) {
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
