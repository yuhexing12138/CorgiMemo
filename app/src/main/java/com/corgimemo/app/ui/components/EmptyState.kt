package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.ui.theme.UiColors

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
    CATEGORY,
    /** 搜索无结果 */
    SEARCH_NO_RESULT
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
            animationType = AnimationType.TILT,
            title = "还没有待办~",
            description = "点击下方按钮添加第一个待办吧！",
            buttonText = "➕ 添加待办"
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
        EmptyStateType.SEARCH_NO_RESULT -> EmptyStateConfig(
            animationType = AnimationType.WORRY,
            title = "未找到相关待办~",
            description = "换个关键词试试？",
            buttonText = "清空搜索"
        )
    }
}

/**
 * 空状态组件
 * 当列表为空时显示柯基动画 + 文字提示 + CTA 按钮
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        BasicEmptyStateContent(
            config = config,
            onAction = onAction
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 基础版空状态内容
 * 包含柯基动画 + 文字提示 + CTA 按钮
 *
 * @param config 空状态配置
 * @param onAction 操作按钮回调
 */
@Composable
private fun BasicEmptyStateContent(
    config: EmptyStateConfig,
    onAction: (() -> Unit)?
) {
    // 呼吸动画缩放值
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 2000,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "breathingAnimation"
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(initialAlpha = 0.5f),
        exit = fadeOut()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 柯基动画区域（带呼吸效果）
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = 1f
                        scaleY = 1f + (scale - 1f) * 0.05f
                    }
            ) {
                FrameAnimation(
                    animationType = config.animationType,
                    fps = 8,
                    isLooping = true,
                    modifier = Modifier.size(200.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = config.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = config.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            if (onAction != null) {
                Spacer(modifier = Modifier.height(24.dp))

                var isPressed by remember { mutableStateOf(false) }

                val buttonScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isPressed) 0.95f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioHighBouncy
                    ),
                    label = "buttonScale"
                )

                androidx.compose.material3.Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = UiColors.Primary,
                        contentColor = Color.White
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
