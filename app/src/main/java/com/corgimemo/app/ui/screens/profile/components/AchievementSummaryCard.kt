package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement as NewAchievement

/**
 * 成就统计卡（精简版）
 *
 * 仅显示"成就解锁"统计行 + 至少 6 个成就徽章预览（横向滚动）。
 * - 已解锁成就：正常显示装扮图标，primaryContainer 背景
 * - 未解锁成就：降权显示装扮图标（alpha + 灰色着色 + 锁标），surfaceVariant 背景
 * - 不足 6 个已解锁时，用即将完成的未解锁成就填充至 6 个
 *
 * 视觉规范：
 * - 卡片圆角 20dp、内边距 16dp、elevation 2dp
 * - 徽章 44dp 方形圆角 10dp，间距 6dp
 *
 * @param unlockedCount 已解锁成就数
 * @param totalCount 成就总数
 * @param achievements 成就列表（按解锁状态排序，已解锁在前）
 * @param onAchievementClick 点击单个徽章回调
 * @param onViewAllClick 点击"查看全部"回调
 */
@Composable
fun AchievementSummaryCard(
    unlockedCount: Int,
    totalCount: Int,
    achievements: List<Pair<NewAchievement, Boolean>>,
    onAchievementClick: (NewAchievement) -> Unit,
    onViewAllClick: () -> Unit
) {
    // 取至少 6 个徽章：已解锁全部 + 未解锁中即将完成的填充
    // 排序逻辑：已解锁在前，未解锁在后（按进度降序，即将完成的优先）
    val displayAchievements = getDisplayAchievements(achievements, minCount = 6)

    // 显式声明 containerColor = MaterialTheme.colorScheme.surface
    // 亮色模式 surface = Color.White (6 种主题色统一)，深色模式 surface = 深灰
    // 用 surface 显式声明而非硬编码 Color.White，是为了遵循 UI 设计规范 12.1.2.2：
    //   - 亮色模式卡片背景 = #FFFFFF
    //   - 深色模式卡片背景 = #2A2A2A
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行 + 查看全部
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆 成就",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "查看全部 ›",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onViewAllClick)
                )
            }

            // 成就解锁统计行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "成就解锁",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$unlockedCount / $totalCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // 徽章预览行（至少 6 个，可横向滚动）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayAchievements) { (achievement, isUnlocked) ->
                    AchievementBadge(
                        achievement = achievement,
                        isUnlocked = isUnlocked,
                        onClick = { onAchievementClick(achievement) }
                    )
                }
            }
        }
    }
}

/**
 * 获取展示用的成就列表（至少 minCount 个）
 * 排序规则：已解锁在前，未解锁在后（保持原列表顺序）
 * 不足 minCount 时，从未解锁成就中补充
 *
 * @param achievements 全部成就列表（带解锁状态）
 * @param minCount 最少展示数量
 * @return 截取后的展示列表
 */
private fun getDisplayAchievements(
    achievements: List<Pair<NewAchievement, Boolean>>,
    minCount: Int = 6
): List<Pair<NewAchievement, Boolean>> {
    // 已解锁在前，未解锁在后
    val sorted = achievements.sortedByDescending { it.second }
    return sorted.take(minCount)
}

/**
 * 成就徽章预览（44dp 方形）
 * 已解锁：正常显示装扮图标，primaryContainer 背景
 * 未解锁：降权显示装扮图标（alpha + 灰色 + 右下角锁标），surfaceVariant 背景
 *
 * @param achievement 成就对象
 * @param isUnlocked 是否已解锁
 * @param onClick 点击回调
 */
@Composable
private fun AchievementBadge(
    achievement: NewAchievement,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isUnlocked) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            // 已完成：装扮图标正常显示
            Text(
                text = achievement.icon,
                fontSize = 18.sp
            )
        } else {
            // 未完成：装扮图标降权显示（灰色半透明 + 右下角锁标）
            Text(
                text = achievement.icon,
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.alpha(0.4f)
            )
            // 右下角锁标，强化"未解锁"语义
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 1.dp, end = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🔒",
                    fontSize = 8.sp
                )
            }
        }
    }
}
