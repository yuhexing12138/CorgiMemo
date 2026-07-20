package com.corgimemo.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 柯基打哈欠浮层
 *
 * 半透明白色圆角气泡 + "Zzz 💤" 文字 + 上下浮动动画，
 * 用于在 CorgiAnimationSection 内部 Box 的右上角（TopEnd）覆盖显示。
 *
 * ## 设计意图
 *
 * 区分两种易混淆的状态：
 * - `BehaviorType.YAWNING`（空闲打哈欠，临时）：浮层出现
 * - `BehaviorType.SLEEPING_NIGHT`（深夜入睡，持久）：问候语气泡已显示"困了..."
 *
 * 两者 `currentPose` 都是 `CorgiPose.SLEEP`，仅靠本浮层做视觉差异化。
 *
 * ## 使用模式
 *
 * ```
 * Box(...) {
 *     InteractiveCorgi(...)
 *     if (currentBehavior == BehaviorType.YAWNING) {
 *         YawnOverlay(modifier = Modifier.align(Alignment.TopEnd))
 *     }
 *     // 问候语气泡
 * }
 * ```
 *
 * @param modifier 父 Box 对齐修饰符（通常用 `Modifier.align(Alignment.TopEnd)`）
 */
@Composable
fun YawnOverlay(
    modifier: Modifier = Modifier
) {
    // 上下浮动动画（1200ms 一周期，往返）
    val infiniteTransition = rememberInfiniteTransition(label = "yawnFloat")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "yawnOffsetY"
    )

    Box(
        modifier = modifier
            .graphicsLayer { translationY = offsetY }
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Zzz 💤",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333)
        )
    }
}
