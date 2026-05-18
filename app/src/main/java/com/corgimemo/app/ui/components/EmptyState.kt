package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import kotlinx.coroutines.delay

/**
 * 空状态类型枚举
 * 定义不同场景下的空状态
 */
enum class EmptyStateType {
    /** 待办列表为空 */
    PENDING,
    /** 已完成列表为空 */
    COMPLETED,
    /** 分类列表为空 */
    CATEGORY
}

/**
 * 空状态数据配置
 *
 * @param animationType 柯基动画类型
 * @param title 标题文字
 * @param description 描述文字
 * @param buttonText 引导按钮文字
 */
private data class EmptyStateConfig(
    val animationType: AnimationType,
    val title: String,
    val description: String,
    val buttonText: String
)

/**
 * 获取空状态配置
 *
 * @param type 空状态类型
 * @param categoryName 分类名称（仅 CATEGORY 类型使用）
 * @return 配置对象
 */
private fun getEmptyStateConfig(
    type: EmptyStateType,
    categoryName: String?
): EmptyStateConfig {
    return when (type) {
        EmptyStateType.PENDING -> EmptyStateConfig(
            animationType = AnimationType.WAG,
            title = "还没有待办~",
            description = "添加第一个待办来和柯基互动吧！",
            buttonText = "添加待办"
        )
        EmptyStateType.COMPLETED -> EmptyStateConfig(
            animationType = AnimationType.SIT,
            title = "还没有已完成的待办~",
            description = "完成任务就能在这里看到啦！",
            buttonText = "去添加"
        )
        EmptyStateType.CATEGORY -> EmptyStateConfig(
            animationType = AnimationType.LIE,
            title = if (categoryName != null) {
                "「$categoryName」还没有待办~"
            } else {
                "这个分类还没有待办~"
            },
            description = "在分类下添加待办试试？",
            buttonText = "添加待办"
        )
    }
}

/**
 * 空状态组件
 * 当列表为空时显示，包含柯基动画、引导文案和操作按钮
 *
 * @param emptyType 空状态类型
 * @param categoryName 分类名称（仅 CATEGORY 类型使用）
 * @param onAction 引导按钮点击回调
 * @param modifier 修饰符
 */
@Composable
fun EmptyState(
    emptyType: EmptyStateType = EmptyStateType.PENDING,
    categoryName: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val config = getEmptyStateConfig(emptyType, categoryName)

    // 轻微的上下浮动动画
    val floatOffset = remember { Animatable(0f) }

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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 柯基动画区域
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(initialAlpha = 0.5f),
            exit = fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FrameAnimation(
                    animationType = config.animationType,
                    fps = 8,
                    isLooping = true,
                    modifier = Modifier
                        .size(140.dp)
                        .offset {
                            IntOffset(
                                x = 0,
                                y = floatOffset.value.toInt()
                            )
                        }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 标题
                Text(
                    text = config.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 描述
                Text(
                    text = config.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                // 引导按钮
                if (onAction != null) {
                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth(0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = config.buttonText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
