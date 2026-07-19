package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AchievementBadgeBack(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                getBackUnlockedColor(achievement.stage)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isUnlocked) {
                Text(
                    text = achievement.icon,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "「${achievement.name}」",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = achievement.story,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFFF97316),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                achievement.unlockedAt?.let { timestamp ->
                    val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(timestamp))
                    Text(
                        text = "解锁于 $dateStr",
                        fontSize = 10.sp,
                        color = getBackStageColor(achievement.stage),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // 未解锁：装扮图标降权显示（半透明 + 灰色），让用户预览解锁后的装扮
                Text(
                    text = achievement.icon,
                    fontSize = 28.sp,
                    color = Color.Gray,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = 0.4f
                        }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "「${achievement.name}」",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "达成条件：",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = achievement.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp
                )

                // 装扮奖励预览（仅当成就关联装扮时显示）
                // 让用户提前知道解锁这个成就能获得什么装扮
                achievement.outfitId?.let {
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🎁 奖励装扮",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        // 装扮图标降权显示（与正面一致的降权风格）
                        Text(
                            text = achievement.icon,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.alpha(0.5f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (currentProgress != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AchievementProgressBar(
                            currentProgress = currentProgress,
                            targetProgress = achievement.threshold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val remaining = (achievement.threshold - currentProgress).coerceAtLeast(0)
                    Text(
                        text = if (remaining > 0) "还差 $remaining 个" else "即将达成",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = getBackStageColor(achievement.stage)
                    )
                }
            }
        }
    }
}

private fun getBackUnlockedColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFFF1F5F9)
        AchievementStage.GROWTH -> Color(0xFFD1FAE5)
        AchievementStage.LEAP -> Color(0xFFDBEAFE)
        AchievementStage.PEAK -> Color(0xFFEDE9FE)
    }
}

private fun getBackStageColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF10B981)
        AchievementStage.LEAP -> Color(0xFF2563EB)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}