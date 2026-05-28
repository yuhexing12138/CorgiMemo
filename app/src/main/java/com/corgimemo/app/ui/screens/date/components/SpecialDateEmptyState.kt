package com.corgimemo.app.ui.screens.date.components

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

/**
 * 特殊日期空状态组件
 * 当特殊日期列表为空时显示，包含：
 * - 📅 图标容器（100dp，圆角24dp，橙粉渐变背景）
 * - 脉冲动画效果（无限循环，2秒周期）
 * - 主标题："还没有特殊日期~"
 * - 副标题："记录重要的日子，\n不错过每个纪念！"
 * - CTA按钮："📅 添加日期"（渐变背景，白色文字）
 *
 * @param onAddClick 添加日期点击回调
 * @param modifier 修饰符
 */
@Composable
fun SpecialDateEmptyState(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 脉冲动画过渡器（无限循环缩放效果） */
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    /** 脉冲动画缩放值（1.0 → 1.15 循环，2秒周期） */
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
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
        /** 📅 图标容器（带脉冲动画和渐变背景） */
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF9A5C),
                            Color(0xFFFF6B9D)
                        )
                    )
                )
                .scale(pulseScale)
        ) {
            Text(
                text = "📅",
                fontSize = 48.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        /** 主标题文本 */
        Text(
            text = "还没有特殊日期~",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D2D2D)
        )

        Spacer(modifier = Modifier.height(8.dp))

        /** 副标题文本（支持换行） */
        Text(
            text = "记录重要的日子，\n不错过每个纪念！",
            fontSize = 14.sp,
            color = Color(0xFF999999),
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        /** CTA 按钮（橙粉渐变背景，白色文字，圆角24dp） */
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 4.dp
            ),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF9A5C),
                            Color(0xFFFF6B9D)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .clip(RoundedCornerShape(24.dp))
        ) {
            Text(
                text = "📅 添加日期",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
