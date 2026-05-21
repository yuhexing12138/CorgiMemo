package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.model.AchievementStage

/**
 * 成就阶段进度条组件
 * 显示成就完成进度和四个阶段（初见期/成长期/飞跃期/巅峰期）
 *
 * @param currentProgress 当前进度值
 * @param targetProgress 目标进度值
 * @param modifier 修饰符
 * @param showLabel 是否显示阶段标签
 */
@Composable
fun AchievementProgressBar(
    currentProgress: Int,
    targetProgress: Int,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    // 进度百分比（0f 到 1f）
    val progressPercent = if (targetProgress > 0) {
        (currentProgress.toFloat() / targetProgress.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // 动画后的进度值
    var animatedProgress by remember { mutableFloatStateOf(0f) }
    val animatedProgressState by animateFloatAsState(
        targetValue = animatedProgress,
        animationSpec = tween(durationMillis = 800),
        label = "ProgressAnimation"
    )

    LaunchedEffect(progressPercent) {
        animatedProgress = progressPercent
    }

    // 确定当前阶段（基于进度百分比）
    val currentStage = when {
        progressPercent >= 1f -> AchievementStage.PEAK
        progressPercent >= 0.66f -> AchievementStage.LEAP
        progressPercent >= 0.33f -> AchievementStage.GROWTH
        else -> AchievementStage.BEGINNER
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 提前获取主题颜色，避免在 Canvas 中调用 Composable 函数
        val outlineColor = MaterialTheme.colorScheme.outline

        // 阶段标签行
        if (showLabel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageLabel(
                    stage = AchievementStage.BEGINNER,
                    isActive = currentStage == AchievementStage.BEGINNER
                )
                StageLabel(
                    stage = AchievementStage.GROWTH,
                    isActive = currentStage == AchievementStage.GROWTH
                )
                StageLabel(
                    stage = AchievementStage.LEAP,
                    isActive = currentStage == AchievementStage.LEAP
                )
                StageLabel(
                    stage = AchievementStage.PEAK,
                    isActive = currentStage == AchievementStage.PEAK
                )
            }
        }

        // 进度条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val width = size.width
                val height = size.height
                val barHeight = height * 0.6f
                val barY = (height - barHeight) / 2

                // 绘制已完成的进度部分
                val progressWidth = width * animatedProgressState
                if (progressWidth > 0) {
                    drawProgressBar(
                        width = progressWidth,
                        height = barHeight,
                        y = barY,
                        color = getProgressColor(currentStage)
                    )
                }

                // 绘制阶段节点（25%、50%、75% 位置）
                val stagePositions = listOf(0.25f, 0.5f, 0.75f)
                val stages = listOf(AchievementStage.BEGINNER, AchievementStage.GROWTH, AchievementStage.LEAP, AchievementStage.PEAK)
                stagePositions.forEachIndexed { index, position ->
                    val nodeX = width * position
                    val isPassed = animatedProgressState >= position
                    val nodeColor = if (isPassed) {
                        getProgressColor(stages.getOrElse(index) { AchievementStage.BEGINNER })
                    } else {
                        outlineColor
                    }

                    drawCircle(
                        color = nodeColor,
                        radius = 6.dp.toPx(),
                        center = Offset(nodeX, height / 2)
                    )
                }
            }
        }

        // 进度数值（12/30 格式）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "$currentProgress / $targetProgress",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * 在 Canvas 上绘制进度条
 */
private fun DrawScope.drawProgressBar(
    width: Float,
    height: Float,
    y: Float,
    color: Color
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2)
    )
}

/**
 * 获取阶段对应的颜色
 */
private fun getProgressColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF10B981)
        AchievementStage.LEAP -> Color(0xFF2563EB)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}

/**
 * 阶段标签组件
 */
@Composable
private fun StageLabel(
    stage: AchievementStage,
    isActive: Boolean
) {
    val label = when (stage) {
        AchievementStage.BEGINNER -> "🌱 初见"
        AchievementStage.GROWTH -> "🌿 成长"
        AchievementStage.LEAP -> "🚀 飞跃"
        AchievementStage.PEAK -> "🏆 巅峰"
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (isActive) {
            getProgressColor(stage)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
    )
}
