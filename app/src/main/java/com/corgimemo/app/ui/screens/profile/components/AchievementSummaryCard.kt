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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement as NewAchievement

/**
 * 成就统计卡
 * 2×2 统计网格 + 底部 5 个成就徽章预览
 *
 * 视觉规范：
 * - 卡片圆角 20dp、内边距 16dp、elevation 2dp
 * - 统计项 12sp，徽章 44dp 方形圆角 10dp
 * - 已解锁徽章 primaryContainer 背景，未解锁灰锁
 *
 * @param totalCompleted 累计完成任务数
 * @param consecutiveDays 连续完成天数
 * @param maxConsecutiveDays 最长连续天数
 * @param unlockedCount 已解锁成就数
 * @param totalCount 成就总数
 * @param achievements 成就列表（取前 5 个展示徽章）
 * @param onAchievementClick 点击单个徽章回调
 * @param onViewAllClick 点击"查看全部"回调
 */
@Composable
fun AchievementSummaryCard(
    totalCompleted: Int,
    consecutiveDays: Int,
    maxConsecutiveDays: Int,
    unlockedCount: Int,
    totalCount: Int,
    achievements: List<Pair<NewAchievement, Boolean>>,
    onAchievementClick: (NewAchievement) -> Unit,
    onViewAllClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

            // 2×2 统计网格（4 行左标签右值）
            Column(modifier = Modifier.padding(top = 12.dp)) {
                StatRow(label = "累计完成任务", value = "$totalCompleted")
                StatRow(label = "连续完成天数", value = "$consecutiveDays")
                StatRow(label = "最长连续天数", value = "$maxConsecutiveDays")
                StatRow(label = "成就解锁", value = "$unlockedCount/$totalCount")
            }

            // 分隔线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // 徽章预览行（取前 5 个）
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(achievements.take(5)) { (achievement, isUnlocked) ->
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
 * 统计行（左标签右值）
 *
 * @param label 左侧标签
 * @param value 右侧数值
 */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 成就徽章预览（44dp 方形）
 * 已解锁：显示成就 icon，primaryContainer 背景
 * 未解锁：显示 🔒，surfaceVariant 背景 + 灰色
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
        Text(
            text = if (isUnlocked) achievement.icon else "🔒",
            fontSize = 18.sp,
            color = if (!isUnlocked) Color.Gray.copy(alpha = 0.5f) else Color.Unspecified
        )
    }
}
