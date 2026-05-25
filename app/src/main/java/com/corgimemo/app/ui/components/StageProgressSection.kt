package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.AchievementStage

@Composable
fun StageProgressSection(
    currentStage: AchievementStage,
    stageDisplayName: String,
    stageUnlockedCount: Int,
    stageTotalCount: Int,
    modifier: Modifier = Modifier
) {
    val progressColor = when (currentStage) {
        AchievementStage.BEGINNER -> Color(0xFF94A3B8)
        AchievementStage.GROWTH -> Color(0xFF34D399)
        AchievementStage.LEAP -> Color(0xFF3B82F6)
        AchievementStage.PEAK -> Color(0xFFF97316)
    }

    val containerColor = when (currentStage) {
        AchievementStage.BEGINNER -> Color(0xFFF8FAFC)
        AchievementStage.GROWTH -> Color(0xFFECFDF5)
        AchievementStage.LEAP -> Color(0xFFEFF6FF)
        AchievementStage.PEAK -> Color(0xFFFFF7ED)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stageDisplayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "：${stageUnlockedCount}/${stageTotalCount} 个成就已解锁",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            StageSectionProgressBar(
                currentProgress = stageUnlockedCount,
                targetProgress = stageTotalCount,
                progressColor = progressColor,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            val progressPercent = if (stageTotalCount > 0) {
                stageUnlockedCount.toFloat() / stageTotalCount.toFloat()
            } else {
                0f
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${(progressPercent * 100).toInt()}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = progressColor
                )
            }
        }
    }
}

@Composable
private fun StageSectionProgressBar(
    currentProgress: Int,
    targetProgress: Int,
    modifier: Modifier = Modifier,
    progressColor: Color = Color(0xFF34D399)
) {
    val progress = if (targetProgress > 0) {
        (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    androidx.compose.material3.LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.height(8.dp),
        color = progressColor,
        trackColor = progressColor.copy(alpha = 0.15f),
        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
    )
}