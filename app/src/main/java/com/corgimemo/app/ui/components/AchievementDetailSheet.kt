package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage

/**
 * 成就详情底部弹窗
 * 显示成就完整信息，支持查看进度和故事文案
 *
 * @param sheetState 底部弹窗状态
 * @param achievement 成就数据
 * @param isUnlocked 是否已解锁
 * @param currentProgress 当前进度（可选）
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementDetailSheet(
    sheetState: SheetState,
    achievement: Achievement,
    isUnlocked: Boolean,
    currentProgress: Int? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部拖动条
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 16.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 成就图标
            Box(
                modifier = Modifier
                    .size(96.dp)
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
                    fontSize = 48.sp,
                    modifier = Modifier.align(Alignment.Center),
                    color = if (isUnlocked) Color.Unspecified else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 成就名称
            Text(
                text = achievement.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 阶段标签
            Text(
                text = getStageFullLabel(achievement.stage),
                style = MaterialTheme.typography.labelMedium,
                color = getStageColor(achievement.stage),
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 分隔线
            androidx.compose.material3.HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 成就描述
            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 进度条（未解锁时显示）
            if (!isUnlocked && currentProgress != null) {
                AchievementProgressBar(
                    currentProgress = currentProgress,
                    targetProgress = achievement.threshold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }

            // 已解锁状态
            if (isUnlocked) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 解锁时间
                    achievement.unlockedAt?.let { unlockedTime ->
                        val dateText = formatTime(unlockedTime)
                        Text(
                            text = "🎉 解锁于 $dateText",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 分隔线
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 故事文案
                    Text(
                        text = "「${achievement.story}」",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFFF97316),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
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
 * 获取完整阶段标签文字
 */
private fun getStageFullLabel(stage: AchievementStage): String {
    return when (stage) {
        AchievementStage.SPRING -> "🌱 萌芽期"
        AchievementStage.GROWING -> "🌿 成长期"
        AchievementStage.MATURE -> "🌳 成熟期"
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

/**
 * 格式化时间戳为日期字符串
 */
private fun formatTime(timestamp: Long): String {
    try {
        val sdf = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    } catch (e: Exception) {
        return ""
    }
}
