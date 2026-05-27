package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 灵感空状态组件
 * 当灵感列表为空时显示，包含：
 * - 💡 图标（80sp，带脉冲动画）
 * - 主文案："还没有灵感记录~"
 * - 副文案："点击下方按钮记录你的第一个灵感吧！"
 * - CTA按钮："💡 记录灵感"（渐变背景）
 *
 * @param onAddClick 添加灵感点击回调
 * @param modifier 修饰符
 */
@Composable
fun InspirationEmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 脉冲动画过渡器（无限循环缩放效果） */
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    /** 脉冲动画缩放值（1.0 → 1.15 循环） */
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        /** 💡 图标区域（带脉冲动画） */
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
        ) {
            Text(
                text = "💡",
                fontSize = 80.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        /** 主文案 */
        Text(
            text = "还没有灵感记录~",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333)
        )

        Spacer(modifier = Modifier.height(8.dp))

        /** 副文案 */
        Text(
            text = "点击下方按钮记录你的第一个灵感吧！",
            fontSize = 14.sp,
            color = Color(0xFF999999),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        /** CTA 按钮（暖橙色渐变背景） */
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            UiColors.Primary,
                            Color(0xFFFFB366)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            Text(
                text = "💡 记录灵感",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
