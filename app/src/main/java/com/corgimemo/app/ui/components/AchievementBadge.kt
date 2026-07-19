package com.corgimemo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AchievementBadge(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int? = null,
    onClick: () -> Unit = {}
) {
    var isFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "BadgeFlip"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.9f)
            .clickable {
                isFlipped = !isFlipped
                onClick()
            },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
        ) {
            if (rotation <= 90f) {
                BadgeFrontSide(
                    achievement = achievement,
                    isUnlocked = isUnlocked,
                    currentProgress = currentProgress
                )
            } else {
                BadgeBackSide(
                    achievement = achievement,
                    isUnlocked = isUnlocked,
                    currentProgress = currentProgress
                )
            }
        }
    }
}

@Composable
private fun BadgeFrontSide(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
            if (isUnlocked) {
                // 已完成：装扮图标正常显示（保留原始彩色）
                Text(
                    text = achievement.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // 未完成：装扮图标降权显示（半透明 + 灰色着色）
                // 视觉上让用户能预览解锁后的装扮样子，但明显是"未获得"状态
                Text(
                    text = achievement.icon,
                    fontSize = 32.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            alpha = 0.35f
                        }
                )
                // 右下角锁标，强化"未解锁"语义
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF64748B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔒",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        if (!isUnlocked && currentProgress != null) {
            val remaining = (achievement.threshold - currentProgress).coerceAtLeast(0)
            Text(
                text = if (remaining > 0) "还差 $remaining 个" else "即将达成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        } else if (isUnlocked && achievement.unlockedAt != null) {
            val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
            val unlockDate = dateFormat.format(achievement.unlockedAt)
            Text(
                text = unlockDate,
                style = MaterialTheme.typography.labelSmall,
                color = getStageColor(achievement.stage),
                fontWeight = FontWeight.Medium
            )
        } else if (isUnlocked) {
            Text(
                text = getStageLabel(achievement.stage),
                style = MaterialTheme.typography.labelSmall,
                color = getStageColor(achievement.stage),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BadgeBackSide(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = 180f
            }
    ) {
        AchievementBadgeBack(
            achievement = achievement,
            isUnlocked = isUnlocked,
            currentProgress = currentProgress
        )
    }
}

private fun getUnlockedBackgroundColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFFF1F5F9)
        AchievementStage.GROWTH -> Color(0xFFD1FAE5)
        AchievementStage.LEAP -> Color(0xFFDBEAFE)
        AchievementStage.PEAK -> Color(0xFFEDE9FE)
    }
}

private fun getUnlockedBackgroundBrush(stage: AchievementStage): Brush {
    return when (stage) {
        AchievementStage.BEGINNER -> Brush.verticalGradient(
            colors = listOf(Color(0xFF94A3B8), Color(0xFF64748B))
        )
        AchievementStage.GROWTH -> Brush.verticalGradient(
            colors = listOf(Color(0xFF34D399), Color(0xFF10B981))
        )
        AchievementStage.LEAP -> Brush.verticalGradient(
            colors = listOf(Color(0xFF3B82F6), Color(0xFF2563EB))
        )
        AchievementStage.PEAK -> Brush.verticalGradient(
            colors = listOf(Color(0xFFF97316), Color(0xFFEA580C))
        )
    }
}

private fun getStageLabel(stage: AchievementStage): String {
    return when (stage) {
        AchievementStage.BEGINNER -> "🌱 初见"
        AchievementStage.GROWTH -> "🌿 成长"
        AchievementStage.LEAP -> "🚀 飞跃"
        AchievementStage.PEAK -> "🏆 巅峰"
    }
}

private fun getStageColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF10B981)
        AchievementStage.LEAP -> Color(0xFF2563EB)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}