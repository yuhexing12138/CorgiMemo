package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 圆形复选框组件
 *
 * 替代 Material3 默认的方形 Checkbox，符合设计规范的圆形样式。
 * 支持点击弹性动画效果。
 *
 * @param checked 是否选中（已完成状态）
 * @param onCheckedChange 状态变更回调
 * @param modifier 修饰符
 * @param enabled 是否启用交互（默认 true）
 */
@Composable
fun CircularCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scale by animateFloatAsState(
        targetValue = if (checked) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "checkboxScale"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
            .then(
                if (checked) {
                    Modifier.background(UiColors.Primary)
                } else {
                    Modifier
                        .background(Color.Transparent)
                        .border(
                            width = 2.dp,
                            color = UiColors.Outline,
                            shape = CircleShape
                        )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已完成",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
