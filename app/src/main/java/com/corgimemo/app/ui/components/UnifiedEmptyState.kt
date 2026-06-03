package com.corgimemo.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.ui.theme.UiColors

/**
 * 统一空状态组件（柯基帧动画版）
 *
 * 用于待办、灵感、特殊日期三个页面的空状态展示，保持视觉风格统一。
 * 包含：柯基帧动画（corgi_sit 趴姿）、emoji小标签、主文案、副文案、CTA按钮。
 * 柯基大小适中（100dp），与页面风格协调。
 *
 * @param icon emoji图标字符串（如 "💡"、"📅"、"📝"），用于柯基下方的小标签
 * @param title 主文案（如 "还没有灵感记录~"）
 * @param subtitle 副文案（如 "点击下方按钮记录你的第一个灵感吧！"）
 * @param ctaText CTA按钮文字（如 "💡 记录灵感"）
 * @param onCtaClick CTA按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun UnifiedEmptyState(
    icon: String,
    title: String,
    subtitle: String,
    ctaText: String,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "unified_pulse")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "unifiedPulseScale"
    )

    val primaryColor = UiColors.Primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FrameAnimation(
            frames = listOf(
                R.drawable.corgi_tilt_2frames_01,
                R.drawable.corgi_tilt_2frames_02
            ),
            fps = 4,
            isLooping = true,
            modifier = Modifier
                .size(160.dp)
                .scale(pulseScale),
            contentScale = ContentScale.Fit
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = icon,
            fontSize = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCtaClick,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor
            ),
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(52.dp)
        ) {
            Text(
                text = ctaText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
