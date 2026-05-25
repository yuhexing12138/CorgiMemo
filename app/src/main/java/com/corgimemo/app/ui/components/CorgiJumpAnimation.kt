package com.corgimemo.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import com.corgimemo.app.data.model.AchievementStage
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class JumpParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    val size: Float,
    val color: Color,
    val speedX: Float,
    val speedY: Float,
    val lifetime: Float
)

@Composable
fun CorgiJumpAnimation(
    modifier: Modifier = Modifier,
    durationMs: Long = 3000,
    stage: AchievementStage = AchievementStage.BEGINNER
) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(durationMs)
        isVisible = false
    }

    if (isVisible) {
        val infiniteTransition = rememberInfiniteTransition(label = "CorgiJump")
        val jumpProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, easing = LinearEasing)
            ),
            label = "JumpProgress"
        )

        val bodyColor = when (stage) {
            AchievementStage.PEAK -> Color(0xFFF59E0B)
            AchievementStage.LEAP -> Color(0xFF3B82F6)
            AchievementStage.GROWTH -> Color(0xFF34D399)
            AchievementStage.BEGINNER -> Color(0xFF94A3B8)
        }

        val random = remember { Random(System.currentTimeMillis()) }
        val particles = remember {
            List(12) { index ->
                JumpParticle(
                    id = index,
                    x = 0f,
                    y = 0f,
                    size = 2f + random.nextFloat() * 4f,
                    color = listOf(
                        Color(0xFFF97316),
                        Color(0xFFF59E0B),
                        Color(0xFF10B981),
                        Color(0xFF3B82F6),
                        Color(0xFFEC4899),
                        Color(0xFF8B5CF6)
                    )[random.nextInt(6)],
                    speedX = -0.8f + random.nextFloat() * 1.6f,
                    speedY = -1.2f + random.nextFloat() * 0.4f,
                    lifetime = 0.5f + random.nextFloat() * 0.8f
                )
            }
        }

        Canvas(modifier = modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val centerX = canvasWidth / 2f
            val centerY = canvasHeight * 0.5f

            val jumpOffset = sin(jumpProgress * Math.PI.toFloat() * 2) * 60f
            val squish = 1f + cos(jumpProgress * Math.PI.toFloat() * 4) * 0.15f

            translate(
                left = centerX,
                top = centerY - jumpOffset
            ) {
                val bodyWidth = 70f * (1f / squish)
                val bodyHeight = 50f * squish

                drawOval(
                    color = bodyColor,
                    topLeft = Offset(-bodyWidth / 2, -bodyHeight / 2),
                    size = Size(bodyWidth, bodyHeight)
                )

                val earOffsetY = -bodyHeight / 2 - 15f * squish
                drawOval(
                    color = Color(0xFF92400E),
                    topLeft = Offset(-30f, earOffsetY),
                    size = Size(18f, 22f)
                )
                drawOval(
                    color = Color(0xFF92400E),
                    topLeft = Offset(12f, earOffsetY),
                    size = Size(18f, 22f)
                )

                val faceY = -8f
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(-12f, faceY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(12f, faceY)
                )
                drawCircle(
                    color = Color(0xFF1C1917),
                    radius = 4f,
                    center = Offset(-12f, faceY)
                )
                drawCircle(
                    color = Color(0xFF1C1917),
                    radius = 4f,
                    center = Offset(12f, faceY)
                )

                drawOval(
                    color = Color(0xFF292524),
                    topLeft = Offset(-6f, 4f),
                    size = Size(12f, 8f)
                )

                val legSpread = 16f + sin(jumpProgress * Math.PI.toFloat() * 4) * 6f
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(-legSpread, bodyHeight / 2 - 5f),
                    size = Size(10f, 18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                )
                drawRoundRect(
                    color = bodyColor,
                    topLeft = Offset(legSpread - 10f, bodyHeight / 2 - 5f),
                    size = Size(10f, 18f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f)
                )
            }

            particles.forEach { particle ->
                val particleProgress = (jumpProgress + particle.id * 0.05f) % 1f
                if (particleProgress < particle.lifetime) {
                    val px = centerX + particle.speedX * particleProgress * canvasWidth * 0.3f
                    val py = centerY + particle.speedY * particleProgress * canvasHeight * 0.4f + jumpOffset * 0.3f
                    val alpha = 1f - particleProgress / particle.lifetime
                    drawCircle(
                        color = particle.color.copy(alpha = alpha),
                        radius = particle.size * (1f - particleProgress * 0.5f),
                        center = Offset(px, py)
                    )
                }
            }
        }
    }
}