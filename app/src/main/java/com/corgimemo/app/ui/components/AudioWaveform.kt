package com.corgimemo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sin

/**
 * 音频波形可视化组件
 * 使用 Canvas 绘制实时音频波形动画，支持录制和播放两种模式
 *
 * @param amplitude 当前音量振幅 (0.0 - 1.0)
 * @param isRecording 是否正在录制（影响波形样式）
 * @param modifier 修饰符
 * @param barCount 波形柱数量（默认 40）
 * @param barSpacing 波形柱间距（默认 4dp）
 * @param activeColor 激活状态颜色（默认暖橙色）
 * @param inactiveColor 未激活状态颜色（默认灰色）
 * @param waveHeight 波形最大高度（默认 100dp）
 */
@Composable
fun AudioWaveform(
    amplitude: Float,
    isRecording: Boolean = false,
    modifier: Modifier = Modifier,
    barCount: Int = 40,
    barSpacing: Dp = 4.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.3f),
    waveHeight: Dp = 100.dp
) {
    // 存储每个柱的目标高度值
    val barHeights = remember { mutableStateListOf<Float>() }

    // 初始化高度列表
    if (barHeights.size != barCount) {
        barHeights.clear()
        repeat(barCount) { barHeights.add(0f) }
    }

    // 动画更新每个柱的高度
    LaunchedEffect(amplitude, isRecording) {
        while (isActive) {
            // 根据当前振幅和时间生成动态波形
            val time = System.currentTimeMillis() / 100.0

            for (i in 0 until barCount) {
                // 使用正弦波 + 随机因子创建自然波动效果
                val phase = (i.toFloat() / barCount) * 2 * Math.PI
                val baseAmplitude = if (isRecording) amplitude else amplitude * 0.7f

                // 组合多个频率的波形，创造更自然的视觉效果
                val wave1 = sin(time + phase).toFloat() * 0.5f
                val wave2 = sin(time * 1.5 + phase * 2).toFloat() * 0.3f
                val wave3 = sin(time * 0.8 + phase * 0.5).toFloat() * 0.2f

                // 计算目标高度（基于位置、时间和输入振幅）
                val positionFactor = 1.0f - abs(i.toFloat() / barCount - 0.5f) * 1.5f
                val targetHeight = ((wave1 + wave2 + wave3) * 0.5f + 0.5f) *
                        baseAmplitude *
                        positionFactor.coerceIn(0.3f, 1.0f)

                // 平滑过渡到目标高度
                val currentHeight = barHeights[i]
                val newHeight = currentHeight + (targetHeight - currentHeight) * 0.3f
                barHeights[i] = newHeight.coerceIn(0f, 1f)
            }

            // 更新间隔（约 30fps）
            delay(33L)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(waveHeight)
    ) {
        // 计算可用宽度和单个柱子的宽度
        val canvasWidth = size.width
        val canvasHeight = size.height
        val spacingPx = barSpacing.toPx()
        val totalSpacing = spacingPx * (barCount - 1)
        val barWidth = (canvasWidth - totalSpacing) / barCount

        // 绘制每个波形柱
        for (i in 0 until barCount) {
            val height = barHeights.getOrNull(i) ?: 0f
            val barHeightPx = height * canvasHeight * 0.9f // 留 10% 边距

            // 计算柱子位置（居中对齐）
            val x = i * (barWidth + spacingPx)
            val y = (canvasHeight - barHeightPx) / 2

            // 根据高度计算透明度（越高的柱子越明显）
            val alpha = 0.4f + height * 0.6f

            // 绘制圆角矩形柱子
            drawRoundRect(
                color = if (height > 0.05f) activeColor.copy(alpha = alpha)
                        else inactiveColor.copy(alpha = 0.3f),
                topLeft = Offset(x, y),
                size = Size(width = barWidth, height = barHeightPx),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

/**
 * 简化版静态波形显示
 * 用于播放时显示已录制的波形快照
 *
 * @param amplitudes 预计算的振幅数组 (0.0 - 1.0)
 * @param progress 当前播放进度 (0.0 - 1.0)
 * @param modifier 修饰符
 * @param activeColor 已播放部分的颜色
 * @param inactiveColor 未播放部分的颜色
 */
@Composable
fun StaticWaveform(
    amplitudes: List<Float>,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color.Gray.copy(alpha = 0.4f),
    waveHeight: Dp = 60.dp
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(waveHeight)
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barCount = amplitudes.size
        val spacingPx = 2.dp.toPx()
        val totalSpacing = spacingPx * (barCount - 1)
        val barWidth = (canvasWidth - totalSpacing) / barCount

        // 计算当前进度对应的索引
        val progressIndex = (progress * barCount).toInt()

        for (i in 0 until barCount) {
            val amplitude = amplitudes.getOrElse(i) { 0f }
            val barHeightPx = amplitude * canvasHeight * 0.8f

            val x = i * (barWidth + spacingPx)
            val y = (canvasHeight - barHeightPx) / 2

            // 根据是否在已播放范围内选择颜色
            val color = if (i <= progressIndex) activeColor else inactiveColor
            val alpha = 0.6f + amplitude * 0.4f

            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(width = barWidth, height = barHeightPx),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}
