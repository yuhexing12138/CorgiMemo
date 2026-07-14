package com.corgimemo.app.ui.screens.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.components.SkeletonCircle
import com.corgimemo.app.ui.components.SkeletonSearchBar
import com.corgimemo.app.ui.components.SkeletonSectionHeader
import com.corgimemo.app.ui.components.SkeletonText

/**
 * 特殊日期页面专属骨架屏组件（重构于 2026-07-14）
 *
 * 模拟日期页面的完整布局结构，与最新 UI 完全对齐：
 * - 搜索栏
 * - 置顶日期卡（1 张，与 PinnedDateCard 对应）
 * - 三段分组（倒计时 2 张 / 正计时 1 张 / 已归档 2 张）
 *   - 倒计时：主色 #FF9A5C（与 DateSectionHeader.kt:39 同步）
 *   - 正计时：柔和绿 #7EC8A0（与 DateSectionHeader.kt:40 同步）
 *   - 已归档：提示灰 #999999（与 DateSectionHeader.kt:42 同步）
 *
 * 切换到真实内容时无视觉跳变（容器色、圆角、内边距均与真实卡一致）。
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

        Spacer(modifier = Modifier.size(8.dp))

        // 2. 内容区：置顶卡 + 3 段分组
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 2.1 置顶日期卡
            item(key = "pinned_skeleton") {
                PinnedDateItemSkeleton()
            }

            // 2.2 倒计时分组
            item(key = "header_countdown") {
                SkeletonSectionHeader(
                    label = "倒计时",
                    color = Color(0xFFFF9A5C)
                )
            }
            items(count = 2, key = { "countdown_skeleton_$it" }) {
                DateItemSkeleton()
            }

            // 2.3 正计时分组
            item(key = "header_countup") {
                SkeletonSectionHeader(
                    label = "正计时",
                    color = Color(0xFF7EC8A0)
                )
            }
            items(count = 1, key = { "countup_skeleton_$it" }) {
                DateItemSkeleton()
            }

            // 2.4 已归档分组
            item(key = "header_expired") {
                SkeletonSectionHeader(
                    label = "已归档",
                    color = Color(0xFF999999)
                )
            }
            items(count = 2, key = { "expired_skeleton_$it" }) {
                DateItemSkeleton()
            }
        }
    }
}

/**
 * 置顶日期卡片骨架（新增于 2026-07-14）
 *
 * 模拟 PinnedDateCard 的三区布局：
 * - 左区(widthIn min=70dp)：标题 + 大数字占位
 * - 中区(weight=1f)：单行倒数占位
 * - 右区：56dp 圆形头像占位
 *
 * 容器用 Card(surface 色 + 2dp elevation) 而非 SkeletonCard，
 * 原因：与真实 PinnedDateCard 的容器视觉完全一致，避免骨架→真实内容的颜色跳变。
 */
@Composable
private fun PinnedDateItemSkeleton() {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左区：标题 + 大数字（min 70dp，与真实卡一致）
            Column(modifier = Modifier.widthIn(min = 70.dp)) {
                // 标题（16sp Medium 占位）
                SkeletonText(
                    width = 0.4f,
                    height = 16.dp
                )
                // 大数字（40sp Bold 的视觉占比 → 32dp 高度）
                SkeletonText(
                    width = 0.5f,
                    height = 32.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // 中区：单行倒数（14sp Medium 占位）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SkeletonText(
                    width = 0.6f,
                    height = 14.dp
                )
            }

            // 右区：56dp 圆形头像（与真实卡一致）
            SkeletonCircle(size = 56.dp)
        }
    }
}

/**
 * 普通日期卡片骨架（重构于 2026-07-14）
 *
 * 模拟 SpecialDateCard 的 3 列布局：
 * - 左：48dp 圆形图片区
 * - 中(weight=1f)：标题(16dp) + 时间信息(14dp)
 * - 右：大数字(22dp) + 单位(11dp) 两行
 *
 * 容器用 Card(surface 色 + 2dp elevation) 而非 SkeletonCard，
 * 原因：与真实 SpecialDateCard 的容器视觉完全一致。
 */
@Composable
private fun DateItemSkeleton() {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左：48dp 圆形图（与真实 SpecialDateCard 一致）
            SkeletonCircle(size = 48.dp)

            // 中间间隔
            Spacer(modifier = Modifier.size(12.dp))

            // 中：标题 + 时间
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 标题（16sp Medium 占位）
                SkeletonText(width = 0.5f, height = 16.dp)
                // 时间信息（14sp Medium 占位）
                SkeletonText(width = 0.4f, height = 14.dp)
            }

            // 中右间隔
            Spacer(modifier = Modifier.size(8.dp))

            // 右：大数字 + 单位两行
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 大数字（22sp Bold 占位）
                SkeletonText(
                    width = 0.18f,
                    height = 22.dp
                )
                // 单位（11sp 占位）
                SkeletonText(
                    width = 0.12f,
                    height = 11.dp
                )
            }
        }
    }
}
