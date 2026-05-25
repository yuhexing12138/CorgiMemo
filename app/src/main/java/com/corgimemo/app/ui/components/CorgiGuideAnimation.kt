package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import kotlinx.coroutines.delay

/**
 * 柯基引导动画组件
 * 在空状态页面显示柯基帧动画和对话气泡，吸引用户注意
 *
 * @param abGroup A/B 测试组别（"A" 或 "B"），影响气泡文案
 * @param modifier 修饰符
 */
@Composable
fun CorgiGuideAnimation(
    abGroup: String = "A",
    modifier: Modifier = Modifier
) {
    /** 根据 A/B 组别选择不同的气泡文案 */
    val bubbleText = if (abGroup == "B") "🌟 开始你的高效之旅！" else "🐕 柯基等你很久啦！"

    /** 当前播放的动画类型（TILT 或 WAG） */
    var currentAnimationType by remember { mutableIntStateOf(0) }

    /** 动画类型列表：TILT（歪头）和 WAG（摇尾巴）交替 */
    val animationTypes = listOf(AnimationType.TILT, AnimationType.WAG)

    /** 浮动动画偏移量 */
    val floatOffset = remember { Animatable(0f) }

    /** 每 3 秒切换一次动画类型 */
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            currentAnimationType = (currentAnimationType + 1) % animationTypes.size
        }
    }

    /** 整体上下浮动效果 */
    LaunchedEffect(Unit) {
        while (true) {
            floatOffset.animateTo(
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            delay(50)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 对话气泡组件 */
        SpeechBubble(
            text = bubbleText,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        /** 柯基帧动画区域（带浮动效果） */
        Box(
            modifier = Modifier.offset {
                IntOffset(
                    x = 0,
                    y = floatOffset.value.toInt()
                )
            }
        ) {
            FrameAnimation(
                animationType = animationTypes[currentAnimationType],
                fps = 8,
                isLooping = true,
                modifier = Modifier.size(140.dp)
            )
        }
    }
}

/**
 * 对话气泡组件
 * 显示在柯基头部上方的提示文字
 *
 * @param text 气泡文字内容
 * @param modifier 修饰符
 */
@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 气泡背景 */
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 4.dp
                    )
                )
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        /** 气泡小三角指示器 */
        Box(
            modifier = Modifier
                .size(12.dp, 8.dp)
                .padding(start = 60.dp)
                .clip(TriangleShape())
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f))
        )
    }
}

/**
 * 三角形形状
 * 用于绘制气泡的指示箭头
 */
class TriangleShape : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}
