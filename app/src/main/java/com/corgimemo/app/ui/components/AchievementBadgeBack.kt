package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * 成就徽章背面（详情面）
 *
 * 注意：外层 AchievementBadge 已经提供了 Card 容器（背景色/形状/阴影），
 * 本组件不再嵌套 Card，避免双层 Card 导致的圆角/阴影叠加和空间浪费。
 *
 * @param achievement 成就数据
 * @param isUnlocked 是否已解锁
 * @param currentProgress 当前进度（仅未解锁时显示进度条）
 */
@Composable
fun AchievementBadgeBack(
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isUnlocked) {
            // ========== 已解锁：图标 + 名称 + 故事 + 解锁日期 ==========
            Text(
                text = achievement.icon,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "「${achievement.name}」",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = achievement.story,
                fontSize = 10.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFFF97316),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(6.dp))

            achievement.unlockedAt?.let { timestamp ->
                val dateStr = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(timestamp))
                Text(
                    text = "解锁于 $dateStr",
                    fontSize = 9.sp,
                    color = getBackStageColor(achievement.stage),
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            // ========== 未解锁：降权图标 + 名称 + 达成条件 + 奖励装扮 + 进度条 ==========
            // 装扮图标降权显示（半透明 + 灰色），让用户预览解锁后的装扮
            Text(
                text = achievement.icon,
                fontSize = 24.sp,
                color = Color.Gray,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = 0.4f
                    }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "「${achievement.name}」",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "达成条件",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = achievement.description,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 2
            )

            // 装扮奖励预览（仅当成就关联装扮时显示）
            achievement.outfitId?.let {
                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🎁 奖励",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = achievement.icon,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.alpha(0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (currentProgress != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AchievementProgressBar(
                        currentProgress = currentProgress,
                        targetProgress = achievement.threshold,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                val remaining = (achievement.threshold - currentProgress).coerceAtLeast(0)
                Text(
                    text = if (remaining > 0) "还差 $remaining" else "即将达成",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = getBackStageColor(achievement.stage)
                )
            }
        }
    }
}

/**
 * 获取背面阶段主题色（用于进度文字、日期等强调色）
 */
private fun getBackStageColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF10B981)
        AchievementStage.LEAP -> Color(0xFF2563EB)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}
