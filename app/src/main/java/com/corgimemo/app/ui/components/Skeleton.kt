package com.corgimemo.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 骨架屏工具对象
 * 提供统一的骨架屏颜色和样式常量
 */
object SkeletonDefaults {
    /** 骨架屏基础颜色（浅灰色，跟随主题） */
    val SkeletonColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    /** Shimmer 高亮颜色 */
    val ShimmerHighlightColor: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)

    /** 默认圆角大小 */
    val DefaultShape = RoundedCornerShape(8.dp)

    /** 卡片圆角大小（遵循 UI 设计规范 20dp） */
    val CardShape = RoundedCornerShape(20.dp)

    /** 按钮圆角大小（遵循 UI 设计规范 16dp） */
    val ButtonShape = RoundedCornerShape(16.dp)

    /** 输入框圆角大小（遵循 UI 设计规范 12dp） */
    val InputShape = RoundedCornerShape(12.dp)

    /** 胶囊形状（用于标签/过滤器） */
    val PillShape = RoundedCornerShape(20.dp)
}

/**
 * 骨架屏修饰符扩展函数
 *
 * 为任意组件添加骨架屏样式（灰色背景 + 可选 shimmer 动画）
 *
 * @param shape 圆角形状，默认使用 [SkeletonDefaults.DefaultShape]
 * @param shimmerEnabled 是否启用 shimmer 扫光动画，默认**开启**
 * @param modifier 基础修饰符
 * @return 应用骨架屏样式的修饰符
 */
fun Modifier.skeleton(
    shape: RoundedCornerShape = SkeletonDefaults.DefaultShape,
    shimmerEnabled: Boolean = true  // ← 默认开启 Shimmer 动画
): Modifier = composed {
    if (shimmerEnabled) {
        // shimmer 动画模式：带扫光效果
        val infiniteTransition = rememberInfiniteTransition(label = "skeleton shimmer")

        val shimmerOffset by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    easing = LinearEasing
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer offset"
        )

        this
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        SkeletonDefaults.SkeletonColor,
                        SkeletonDefaults.ShimmerHighlightColor,
                        SkeletonDefaults.SkeletonColor
                    ),
                    start = Offset(shimmerOffset - 500f, 0f),
                    end = Offset(shimmerOffset + 500f, 0f)
                )
            )
    } else {
        // 无动画模式：纯色背景
        this
            .clip(shape)
            .background(color = SkeletonDefaults.SkeletonColor)
    }
}

/**
 * 骨架屏文本占位组件
 *
 * 模拟单行或多行文本的骨架占位
 *
 * @param width 文本宽度比例（相对于父容器），1.0 = 100%
 * @param height 行高，默认 16dp
 * @param modifier 修饰符
 */
@Composable
fun SkeletonText(
    width: Float = 0.7f,
    height: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth(width)
            .height(height)
            .skeleton()
    )
}

/**
 * 骨架屏卡片容器组件
 *
 * 提供统一的卡片骨架样式（圆角、内边距）
 *
 * @param modifier 修饰符
 * @param content 卡片内容
 */
@Composable
fun SkeletonCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SkeletonDefaults.CardShape)
            .background(SkeletonDefaults.SkeletonColor)
            .padding(16.dp)
    ) {
        content()
    }
}

/**
 * 骨架屏搜索栏占位组件
 *
 * 模拟搜索输入框的骨架样式
 *
 * @param modifier 修饰符
 */
@Composable
fun SkeletonSearchBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .skeleton(shape = SkeletonDefaults.InputShape)
    )
}

/**
 * 骨架屏圆形占位组件
 *
 * 用于模拟头像、复选框等圆形元素
 *
 * @param size 圆形直径
 * @param modifier 修饰符
 */
@Composable
fun SkeletonCircle(
    size: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .skeleton(shape = RoundedCornerShape(50))
    )
}

/**
 * 骨架屏按钮/标签占位组件
 *
 * 用于模拟过滤器按钮、操作按钮等
 *
 * @param width 按钮宽度
 * @param height 按钮高度，默认 36dp
 * @param modifier 修饰符
 */
@Composable
fun SkeletonButton(
    width: Dp,
    height: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .skeleton(shape = SkeletonDefaults.PillShape)
    )
}

/**
 * 可折叠分区头骨架
 *
 * 模拟 [com.corgimemo.app.ui.components.CollapsibleSectionHeader] 的布局：
 * 箭头图标(▼展开态) + 标签文字 + 计数占位块。无点击行为。
 *
 * 布局常量（必须与 CollapsibleSectionHeader 保持一致）：
 * - padding: horizontal=16.dp, vertical=8.dp
 * - 箭头尺寸: 20.dp
 * - 箭头→文字间距: 6.dp
 * - 文字→计数间距: 6.dp
 *
 * @param label 区头标签文字（如"置顶"/"待完成"/"已完成"）
 * @param color 文字与箭头颜色，建议传入 [com.corgimemo.app.ui.components.SectionHeaderColors] 对应常量
 * @param modifier 修饰符
 */
@Composable
fun SkeletonSectionHeader(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 箭头图标（固定展开态 ▼，rotate=0f）
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = color
        )
        Spacer(Modifier.width(6.dp))
        // 标签文字
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(6.dp))
        // 计数占位块 "(▭)"
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(14.dp)
                .skeleton()
        )
    }
}
