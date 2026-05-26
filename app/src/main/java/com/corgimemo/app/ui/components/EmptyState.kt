package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.data.model.TodoTemplate
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
 * 增强版空状态组件
 * 当待办列表为空时显示，包含：
 * - 柯基引导动画（TILT/WAG 交替 + 气泡文字）
 * - 操作指引（文字提示 + 箭头动画 + 语音输入提示）
 * - 模板预设（横向滚动模板卡片）
 *
 * @param emptyType 空状态类型
 * @param categoryName 分类名称（仅 CATEGORY 类型使用）
 * @param onAction 引导按钮点击回调
 * @param onFabClicked FAB 按钮被点击时的回调（用于隐藏箭头）
 * @param onTemplateSelected 模板被选中时的回调
 * @param showEnhanced 是否显示增强版引导（仅 PENDING 类型且为 true 时显示完整引导）
 * @param abGroup A/B 测试组别（"A" 或 "B"），影响引导文案
 * @param modifier 修饰符
 */
@Composable
fun EmptyState(
    emptyType: EmptyStateType = EmptyStateType.PENDING,
    categoryName: String? = null,
    onAction: (() -> Unit)? = null,
    onFabClicked: () -> Unit = {},
    onTemplateSelected: (TodoTemplate) -> Unit = {},
    showEnhanced: Boolean = true,
    abGroup: String = "A",
    modifier: Modifier = Modifier
) {
    val config = getEmptyStateConfig(emptyType, categoryName)

    /** 是否显示操作指引箭头（FAB 被点击后隐藏） */
    var showArrow by remember { mutableStateOf(true) }

    /** 监听 FAB 点击事件，隐藏箭头 */
    androidx.compose.runtime.LaunchedEffect(Unit) {
        // 这里可以通过其他方式监听 FAB 点击，暂时使用回调方式
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        /** 判断是否显示增强版引导 */
        if (showEnhanced && emptyType == EmptyStateType.PENDING) {
            EnhancedEmptyStateContent(
                onFabClicked = {
                    showArrow = false
                    onFabClicked()
                },
                onTemplateSelected = onTemplateSelected,
                showArrow = showArrow,
                abGroup = abGroup
            )
        } else {
            BasicEmptyStateContent(
                config = config,
                onAction = onAction
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * 增强版空状态内容
 * 包含柯基引导动画、操作指引和模板轮播
 *
 * @param onFabClicked FAB 点击回调
 * @param onTemplateSelected 模板选择回调
 * @param showArrow 是否显示箭头
 * @param abGroup A/B 测试组别
 */
@Composable
private fun EnhancedEmptyStateContent(
    onFabClicked: () -> Unit,
    onTemplateSelected: (TodoTemplate) -> Unit,
    showArrow: Boolean,
    abGroup: String = "A"
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 1. 柯基引导动画区域 */
        CorgiGuideAnimation(
            abGroup = abGroup,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        /** 2. 操作指引区域 */
        OperationGuide(
            isVisible = showArrow,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        /** 3. 模板轮播区域 */
        TemplateCarousel(
            onTemplateSelected = onTemplateSelected,
            modifier = Modifier.fillMaxWidth()
        )
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
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = 1f
                        scaleY = 1f + (scale - 1f) * 0.05f // 轻微的 Y 轴缩放模拟呼吸
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
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = com.corgimemo.app.ui.theme.UiColors.Primary,
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text(
                        text = config.buttonText,
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                }
            }
        }
    }
}
