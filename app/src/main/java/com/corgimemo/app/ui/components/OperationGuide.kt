package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 操作指引组件
 * 显示引导文字、指向 FAB 的箭头动画和语音输入提示
 *
 * @param isVisible 是否显示箭头动画（FAB 被点击后应隐藏）
 * @param modifier 修饰符
 */
@Composable
fun OperationGuide(
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    /** 箭头脉冲透明度动画 */
    val arrowAlpha = remember { Animatable(1f) }

    /** 箭头上下浮动偏移量 */
    val arrowOffset = remember { Animatable(0f) }

    /** 箭头脉冲动画：透明度在 0.4-1.0 之间循环 */
    LaunchedEffect(Unit) {
        if (isVisible) {
            while (true) {
                arrowAlpha.animateTo(
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    /** 箭头上下浮动动画 */
    LaunchedEffect(Unit) {
        if (isVisible) {
            while (true) {
                arrowOffset.animateTo(
                    targetValue = 6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        /** 主引导卡片 */
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👆 点击下方 + 按钮",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "添加你的第一个待办吧",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        /** 向下箭头（带脉冲和浮动效果） */
        if (isVisible) {
            Text(
                text = "↓",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .alpha(arrowAlpha.value)
                    .padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        /** 语音输入提示卡片 */
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = "💡 试试说：「明天开会」\n   柯基会帮你创建待办哦~",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}
