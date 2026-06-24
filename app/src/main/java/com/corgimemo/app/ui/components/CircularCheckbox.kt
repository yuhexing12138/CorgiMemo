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
import com.corgimemo.app.ui.components.CompletedColors

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
 * @param dimmed 是否变淡（默认 false）。true 时用 [CompletedColors.CheckboxBgDim] 浅橙
 *              替代 [UiColors.Primary]，用于"已完成"态的视觉降权。
 *              保持橙色系，仅降低颜色深度，不改为灰色。
 */
@Composable
fun CircularCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    dimmed: Boolean = false
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
                    /**
                     * 已勾选背景色：
                     * - 正常态：UiColors.Primary（深橙）
                     * - 变淡态：CompletedColors.CheckboxBgDim（浅橙，保持橙色系仅降深度）
                     */
                    Modifier.background(
                        if (dimmed) CompletedColors.CheckboxBgDim else UiColors.Primary
                    )
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
