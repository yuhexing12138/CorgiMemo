package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 彩色纸屑动画组件
 * 用于成就解锁时的庆祝效果
 *
 * @param durationMs 动画持续时间（毫秒），默认 3000ms
 */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    durationMs: Long = 3000
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(durationMs)
        isVisible = false
    }

    if (isVisible) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            ConfettiCanvas()
        }
    }
}

/**
 * 纸屑绘制 Canvas
 * 绘制 50+ 个彩色纸屑，每个纸屑有随机颜色、位置、大小、下落速度
 */
@Composable
private fun ConfettiCanvas() {
    val infiniteTransition = rememberInfiniteTransition(label = "ConfettiTransition")
    val random = Random(System.currentTimeMillis())

    // 纸屑颜色列表（使用 MaterialTheme 颜色 + 暖橙色）
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFF97316),
        Color(0xFFF59E0B),
        Color(0xFF10B981),
        Color(0xFF3B82F6),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899)
    )

    // 创建 60 个纸屑
    val confettiList = remember {
        List(60) { index ->
            ConfettiParticle(
                id = index,
                startX = random.nextFloat(),
                startY = -0.2f + random.nextFloat() * 0.2f,
                size = 4.dp.value + random.nextFloat() * 8.dp.value,
                color = colors[random.nextInt(colors.size)],
                speed = 0.5f + random.nextFloat() * 1.0f,
                horizontalDrift = -0.3f + random.nextFloat() * 0.6f,
                rotationSpeed = -180f + random.nextFloat() * 360f
            )
        }
    }

    // 动画进度（0 到 1）
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500)
        ),
        label = "ConfettiProgress"
    )

    Canvas(
        modifier = Modifier.fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        confettiList.forEach { particle ->
            // 计算当前位置
            val currentProgress = (progress + particle.id * 0.01f) % 1f
            val x = (particle.startX + currentProgress * particle.horizontalDrift) * canvasWidth
            val y = (particle.startY + currentProgress * particle.speed) * canvasHeight

            // 只绘制在画布范围内的纸屑
            if (y >= 0 && y <= canvasHeight) {
                drawIntoCanvas {
                    // 绘制纸屑（小圆点）
                    drawCircle(
                        color = particle.color,
                        radius = particle.size / 2,
                        center = Offset(x, y),
                        alpha = 0.8f
                    )
                }
            }
        }
    }
}

/**
 * 单个纸屑粒子数据类
 */
private data class ConfettiParticle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val size: Float,
    val color: Color,
    val speed: Float,
    val horizontalDrift: Float,
    val rotationSpeed: Float
)
