package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage

/**
 * 成就徽章组件
 * 用于在成就墙中显示单个成就
 *
 * @param achievement 成就数据
 * @param isUnlocked 是否已解锁
 * @param currentProgress 当前进度（可选，未解锁时显示）
 * @param onClick 点击事件回调
 */
@Composable
fun AchievementBadge(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                getUnlockedBackgroundColor(achievement.stage)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = if (isUnlocked) {
            CardDefaults.cardElevation(defaultElevation = 4.dp)
        } else {
            CardDefaults.cardElevation(defaultElevation = 0.dp)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 成就图标容器
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnlocked) {
                            getUnlockedBackgroundBrush(achievement.stage)
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isUnlocked) achievement.icon else "❓",
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.Center),
                    color = if (isUnlocked) Color.Unspecified else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 成就名称
            Text(
                text = achievement.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 进度或阶段显示
            if (!isUnlocked && currentProgress != null) {
                Text(
                    text = "$currentProgress/${achievement.threshold}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            } else if (isUnlocked) {
                // 显示阶段标签
                Text(
                    text = getStageLabel(achievement.stage),
                    style = MaterialTheme.typography.labelSmall,
                    color = getStageColor(achievement.stage),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * 获取解锁时的背景颜色
 */
private fun getUnlockedBackgroundColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.SPRING -> Color(0xFFD1FAE5)
        AchievementStage.GROWING -> Color(0xFFDBEAFE)
        AchievementStage.MATURE -> Color(0xFFEDE9FE)
    }
}

/**
 * 获取解锁时的渐变背景 Brush
 */
private fun getUnlockedBackgroundBrush(stage: AchievementStage): Brush {
    return when (stage) {
        AchievementStage.SPRING -> Brush.verticalGradient(
            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
        )
        AchievementStage.GROWING -> Brush.verticalGradient(
            colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
        )
        AchievementStage.MATURE -> Brush.verticalGradient(
            colors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED))
        )
    }
}

/**
 * 获取阶段标签文字
 */
private fun getStageLabel(stage: AchievementStage): String {
    return when (stage) {
        AchievementStage.SPRING -> "🌱 萌芽"
        AchievementStage.GROWING -> "🌿 成长"
        AchievementStage.MATURE -> "🌳 成熟"
    }
}

/**
 * 获取阶段颜色
 */
private fun getStageColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.SPRING -> Color(0xFF059669)
        AchievementStage.GROWING -> Color(0xFF2563EB)
        AchievementStage.MATURE -> Color(0xFF7C3AED)
    }
}
