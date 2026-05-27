package com.corgimemo.app.ui.components.navigation

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 气泡菜单覆盖层组件
 * 显示半透明遮罩和三个扇形排列的气泡按钮
 *
 * @param isExpanded 是否展开
 * @param onDismiss 关闭回调（点击遮罩或 ✕ 按钮）
 * @param onBubbleClick 气泡点击回调
 * @param modifier 修饰符
 */
@Composable
fun BubbleMenuOverlay(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    onBubbleClick: (BubbleType) -> Unit,
    modifier: Modifier = Modifier
) {
    // 半透明遮罩层
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onDismiss)
    ) {
        // 气泡容器
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 推动内容到上方
            Spacer(modifier = Modifier.weight(1f))

            // 气泡1: 创建待办（上方）
            AnimatedBubble(
                isVisible = isExpanded,
                delayMillis = 0  // 第一个立即弹出
            ) {
                BubbleItem(
                    icon = "📝",
                    text = "待办",
                    onClick = { onBubbleClick(BubbleType.CREATE_TODO) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 气泡行：左 - 中 - 右
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 气泡2: 记录灵感（左侧）
                AnimatedBubble(
                    isVisible = isExpanded,
                    delayMillis = 150  // 延迟150ms弹出
                ) {
                    BubbleItem(
                        icon = "💡",
                        text = "灵感",
                        onClick = { onBubbleClick(BubbleType.RECORD_INSPIRE) }
                    )
                }

                // 中央 ✕ 按钮（展开状态）
                CenterEditButton(
                    isExpanded = true,
                    onClick = onDismiss
                )

                // 气泡3: 特殊日期（右侧）
                AnimatedBubble(
                    isVisible = isExpanded,
                    delayMillis = 300  // 延迟300ms弹出
                ) {
                    BubbleItem(
                        icon = "📅",
                        text = "日期",
                        onClick = { onBubbleClick(BubbleType.SPECIAL_DATE) }
                    )
                }
            }

            // 为底部导航栏留空间
            Spacer(modifier = Modifier.height(120.dp))
        }
    }
}

/**
 * 动画气泡组件
 * 控制单个气泡的弹出/收起动画效果
 * 使用 GPU 加速的 graphicsLayer 确保流畅性能
 *
 * @param isVisible 是否可见（控制展开/收起）
 * @param delayMillis 延迟时间（毫秒），用于错开多个气泡的弹出时机
 * @param content 气泡内容
 */
@Composable
private fun AnimatedBubble(
    isVisible: Boolean,
    delayMillis: Long,
    content: @Composable () -> Unit
) {
    // 性能优化：使用 Animatable 实现更精细的动画控制
    // Animatable 比 animateFloatAsState 更适合复杂动画序列
    val animatable = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            // 展开动画：先等待延迟时间，再执行弹出
            delay(delayMillis)
            animatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 200,  // 弹出时长200ms
                    easing = EaseOutCubic   // 先快后慢
                )
            )
        } else {
            // 收起动画：所有气泡同时收起，无延迟（性能优化：快速释放资源）
            animatable.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = 150,  // 收起时长150ms（比弹出快）
                    easing = EaseInCubic    // 先慢后快
                )
            )
        }
    }

    // 性能优化：使用 graphicsLayer 进行 GPU 加速渲染
    // 避免触发重新布局（layout），仅修改绘制属性
    Box(
        modifier = Modifier.graphicsLayer {
            scaleX = animatable.value
            scaleY = animatable.value
            alpha = animatable.value
            translationY = (1f - animatable.value) * 20f  // 向上位移效果
        }
    ) {
        content()
    }
}

/**
 * 气泡项 UI 组件
 * 显示单个可点击的气泡按钮
 *
 * @param icon 图标（emoji）
 * @param text 标签文字
 * @param onClick 点击回调
 */
@Composable
private fun BubbleItem(
    icon: String,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 2.dp,
            color = androidx.compose.ui.graphics.Color(0xFFFF9A5C)
        ),
        color = androidx.compose.ui.graphics.Color.White,
        shadowElevation = 6.dp,
        modifier = Modifier
            .height(48.dp)
            .semantics {
                contentDescription = "创建${text}"
                role = Role.Button
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = androidx.compose.ui.graphics.Color(0xFF333333)
            )
        }
    }
}
