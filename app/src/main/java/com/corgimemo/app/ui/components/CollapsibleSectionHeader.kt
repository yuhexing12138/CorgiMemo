package com.corgimemo.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 通用可折叠区头组件
 *
 * 用于"置顶"和"已完成"区域的可折叠按钮。设计要点：
 * - 无背景色（透明背景，与其他元素风格统一）
 * - 箭头位于文字左侧（▼ 展开 / ▶ 折叠，通过 rotate -90° 实现）
 * - 无水波纹（indication = null + 透明度反馈）
 * - 250ms FastOutSlowInEasing 平滑旋转动画
 *
 * @param label 标签，如"置顶"、"已完成"
 * @param count 实时数量
 * @param isExpanded 是否展开
 * @param color 文字与箭头颜色（置顶 = primary，已完成 = onSurfaceVariant）
 * @param onClick 点击回调
 * @param modifier 外部 Modifier
 * @param expandedLabel 可选：展开时显示的文字（如"收起置顶"），为 null 时使用 "label (count)"
 * @param collapsedLabel 可选：折叠时显示的文字（如"展开置顶"），为 null 时使用 "label (count)"
 */
@Composable
fun CollapsibleSectionHeader(
    label: String,
    count: Int,
    isExpanded: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedLabel: String? = null,
    collapsedLabel: String? = null,
) {
    // 箭头旋转动画：展开为 0°（▼），折叠为 -90°（▶），250ms FastOutSlowInEasing 缓动
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "${label}_arrow_rotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 主动去除水波纹（ripple = null），与 EnhancedTopBar.BACK 模式一致，
            // 避免点击时出现 Material 默认的圆形涟漪，视觉上更克制
            .clickable(
                onClick = onClick,
                role = Role.Button,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) "折叠$label" else "展开$label",
            modifier = Modifier
                .size(20.dp)
                .rotate(arrowRotation),
            tint = color
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isExpanded) {
                expandedLabel?.let { "$it ($count)" } ?: "$label ($count)"
            } else {
                collapsedLabel?.let { "$it ($count)" } ?: "$label ($count)"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}
