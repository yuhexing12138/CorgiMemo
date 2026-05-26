package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay

/**
 * 完成待办动画包装器
 *
 * 当待办被标记为已完成时，触发以下动画序列：
 * 1. 卡片向右滑出（200ms）
 * 2. 绿色 ✓ 弹出（spring 弹性）
 * 3. 延迟后调用 onComplete 回调
 *
 * @param isCompleted 是否已完成
 * @param content 待办项内容
 * @param onComplete 动画完成回调（用于实际移除/更新列表项）
 */
@Composable
fun CompleteTodoAnimation(
    isCompleted: Boolean,
    onComplete: () -> Unit,
    content: @Composable () -> Unit
) {
    val cardOffsetX by animateFloatAsState(
        targetValue = if (isCompleted) 300f else 0f,
        animationSpec = tween(
            durationMillis = 200,
            easing = androidx.compose.animation.core.EaseIn
        ),
        label = "completeSlideOut"
    )

    val checkScale by animateFloatAsState(
        targetValue = if (isCompleted) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkPop"
    )

    Box(
        modifier = Modifier
            .offset(x = cardOffsetX.dp)
    ) {
        content()

        // 绿色 ✓ 弹出层
        if (isCompleted && checkScale > 0.5f) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF4CAF50), // 成功绿色
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                        // 弹性过冲效果：先放大到 1.2x 再回弹到 1.0x
                        if (checkScale > 0.8f) {
                            scaleX = 1.2f - (checkScale - 0.8f) * 0.25f
                            scaleY = 1.2f - (checkScale - 0.8f) * 0.25f
                        }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已完成",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    // 监听动画完成，触发回调
    LaunchedEffect(isCompleted) {
        if (isCompleted) {
            delay(400) // 等待滑出 + ✓ 弹出动画完成
            onComplete()
        }
    }
}

/**
 * 删除待办动画包装器
 *
 * 当待办被删除时，触发以下动画序列：
 * 1. 卡片向左滑出（250ms）+ 淡出
 * 2. 红 × 弹出（spring 弹性）
 * 3. 延迟后调用 onDelete 回调
 *
 * @param isDeleting 是否正在删除
 * @param content 待办项内容
 * @param onDelete 删除完成回调
 */
@Composable
fun DeleteTodoAnimation(
    isDeleting: Boolean,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val cardOffsetX by animateFloatAsState(
        targetValue = if (isDeleting) -300f else 0f,
        animationSpec = tween(
            durationMillis = 250,
            easing = androidx.compose.animation.core.EaseIn
        ),
        label = "deleteSlideLeft"
    )

    val cardAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "deleteFade"
    )

    val crossScale by animateFloatAsState(
        targetValue = if (isDeleting) 1f else 0f,
        animationSpec = spring(
            stiffness = Spring.StiffnessHigh
        ),
        label = "crossPop"
    )

    Box(
        modifier = Modifier
            .offset(x = cardOffsetX.dp)
            .graphicsLayer { alpha = cardAlpha }
    ) {
        content()

        // 红 × 弹出层
        if (isDeleting && crossScale > 0.5f) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEF4444), // 错误红色
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = crossScale.coerceAtMost(1.1f) // 轻微过冲
                        scaleY = crossScale.coerceAtMost(1.1f)
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    // 监听动画完成，触发回调
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            delay(300) // 等待滑出 + × 弹出动画完成
            onDelete()
        }
    }
}
