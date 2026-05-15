package com.corgimemo.app.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * 爱心粒子数据
 */
data class HeartParticle(
    val id: Int,
    val startX: Float,
    val startY: Float,
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val delayMs: Long
)

/**
 * 爱心粒子特效组件
 * 从指定位置向上飘散的爱心粒子
 *
 * @param isActive 是否激活特效
 * @param particleCount 粒子数量
 * @param modifier 修饰符
 */
@Composable
fun HeartParticleEffect(
    isActive: Boolean,
    particleCount: Int = 15,
    modifier: Modifier = Modifier
) {
    var particles by remember { mutableStateOf<List<HeartParticle>>(emptyList()) }
    var showParticles by remember { mutableStateOf(false) }

    // 当 isActive 变为 true 时生成粒子
    LaunchedEffect(isActive) {
        if (isActive) {
            // 生成随机粒子
            particles = (0 until particleCount).map { i ->
                HeartParticle(
                    id = i,
                    startX = (Math.random() * 100 + 50).toFloat(),
                    startY = (Math.random() * 30 + 60).toFloat(),
                    angle = (Math.random() * 60 - 30).toFloat(),
                    speed = (Math.random() * 2 + 1).toFloat(),
                    size = (Math.random() * 8 + 8).toFloat(),
                    color = when ((Math.random() * 3).toInt()) {
                        0 -> Color(0xFFFF6B6B)
                        1 -> Color(0xFFFF4757)
                        else -> Color(0xFFFF6B6B).copy(alpha = 0.8f)
                    },
                    delayMs = (Math.random() * 300).toLong()
                )
            }
            showParticles = true

            // 2秒后隐藏粒子
            delay(2000)
            showParticles = false
            particles = emptyList()
        }
    }

    if (showParticles && particles.isNotEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            particles.forEach { particle ->
                AnimatedHeartParticle(particle = particle)
            }
        }
    }
}

/**
 * 单个动画的爱心粒子
 */
@Composable
private fun AnimatedHeartParticle(particle: HeartParticle) {
    val progress = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(particle) {
        // 等待延迟
        delay(particle.delayMs)

        // 开始动画（向上飘散 + 淡出）
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
        )

        // 淡出效果
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 500)
        )
    }

    val yOffset = -progress.value * 150 * particle.speed
    val xOffset = kotlin.math.sin(progress.value * 3.14f) * 20

    Canvas(
        modifier = Modifier.size(50.dp)
    ) {
        val centerX = size.width / 2 + xOffset
        val centerY = size.height / 2 + yOffset

        rotate(degrees = particle.angle, pivot = Offset(centerX, centerY)) {
            drawHeart(
                center = Offset(centerX, centerY),
                size = particle.size,
                color = particle.color.copy(alpha = alpha.value)
            )
        }
    }
}

/**
 * 持续模式的爱心粒子特效
 * 只要 isActive 为 true，动画就持续
 *
 * @param isActive 是否激活特效
 * @param particleCount 粒子数量
 * @param modifier 修饰符
 */
@Composable
fun HeartParticleEffectContinuous(
    isActive: Boolean,
    particleCount: Int = 15,
    modifier: Modifier = Modifier
) {
    var particles by remember { mutableStateOf<List<HeartParticle>>(emptyList()) }
    var showParticles by remember { mutableStateOf(false) }

    // 当 isActive 变为 true 时生成粒子并持续播放
    LaunchedEffect(isActive) {
        if (isActive) {
            // 持续生成粒子
            while (isActive) {
                particles = (0 until particleCount).map { i ->
                    HeartParticle(
                        id = i,
                        startX = (Math.random() * 100 + 50).toFloat(),
                        startY = (Math.random() * 30 + 60).toFloat(),
                        angle = (Math.random() * 60 - 30).toFloat(),
                        speed = (Math.random() * 2 + 1).toFloat(),
                        size = (Math.random() * 8 + 8).toFloat(),
                        color = when ((Math.random() * 3).toInt()) {
                            0 -> Color(0xFFFF6B6B)
                            1 -> Color(0xFFFF4757)
                            else -> Color(0xFFFF6B6B).copy(alpha = 0.8f)
                        },
                        delayMs = (Math.random() * 300).toLong()
                    )
                }
                showParticles = true

                // 每1.5秒重新生成一批粒子，形成持续效果
                delay(1500)
            }
        } else {
            // isActive 变为 false 时停止
            showParticles = false
            particles = emptyList()
        }
    }

    if (showParticles && particles.isNotEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            particles.forEach { particle ->
                AnimatedHeartParticle(particle = particle)
            }
        }
    }
}

/**
 * 绘制爱心形状
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        val x = center.x
        val y = center.y

        // 爱心路径
        moveTo(x, y + size * 0.3f)
        cubicTo(
            x - size * 1.0f, y - size * 0.5f,
            x - size * 1.0f, y - size * 1.2f,
            x, y - size * 0.3f
        )
        cubicTo(
            x + size * 1.0f, y - size * 1.2f,
            x + size * 1.0f, y - size * 0.5f,
            x, y + size * 0.3f
        )
        close()
    }

    drawPath(path = path, color = color)
}
