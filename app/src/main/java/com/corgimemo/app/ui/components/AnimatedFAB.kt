package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.launch

/**
 * 动画浮动操作按钮（Animated FAB）
 *
 * 优化的 FAB 组件，包含以下特性：
 * - 图标改为 ✏️ 铅笔/编辑图标（替代默认的 + 号）
 * - 背景色使用暖橙色 (#FF9A5C)
 * - 点击时触发缩放+旋转动画序列（300ms）
 * - 支持自定义底部边距（默认 88dp）
 *
 * @param onClick 点击回调
 * @param modifier 修饰符
 * @param bottomMargin 底部边距（默认 88.dp）
 * @param containerColor 背景颜色（默认 UiColors.Primary）
 * @param contentColor 内容颜色（默认白色）
 */
@Composable
fun AnimatedFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bottomMargin: Float = 88f,
    containerColor: androidx.compose.ui.graphics.Color = UiColors.Primary,
    contentColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.White
) {
    var isPressed by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.9f
            isAnimating -> 1.05f
            else -> 1.0f
        },
        animationSpec = if (isPressed) {
            spring(
                dampingRatio = Spring.DampingRatioHighBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        },
        label = "fabScale"
    )

    val rotation by animateFloatAsState(
        targetValue = if (isAnimating) 360f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = androidx.compose.animation.core.EaseInOutCubic
        ),
        label = "fabRotation"
    )

    FloatingActionButton(
        onClick = {
            isPressed = true
            coroutineScope.launch {
                kotlinx.coroutines.delay(100)
                isPressed = false
                isAnimating = true
                onClick()
                kotlinx.coroutines.delay(300)
                isAnimating = false
            }
        },
        modifier = modifier
            .padding(bottom = bottomMargin.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
            },
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "编辑",
            tint = contentColor
        )
    }
}
