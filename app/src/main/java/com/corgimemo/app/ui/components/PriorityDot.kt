package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 优先级枚举
 *
 * 定义待办事项的三个优先级级别及其对应的颜色
 */
enum class TodoPriority(val color: Color, val displayName: String) {
    /** 高优先级 - 柔红（与 UI 设计规范对齐） */
    HIGH(PriorityColors.High, "高"),

    /** 中优先级 - 柔橙（与 UI 设计规范对齐） */
    MEDIUM(PriorityColors.Medium, "中"),

    /** 低优先级 - 柔蓝（与 UI 设计规范对齐） */
    LOW(PriorityColors.Low, "低")
}

/**
 * 优先级圆点组件
 *
 * 显示待办事项的优先级级别，使用彩色圆点表示。
 * 可选择显示纯圆点或圆点+文字标签。
 *
 * @param priority 优先级级别
 * @param showLabel 是否显示文字标签（默认 false，仅显示圆点）
 * @param modifier 修饰符
 */
@Composable
fun PriorityDot(
    priority: TodoPriority,
    showLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (showLabel) {
        // 显示圆点 + 文字标签
        androidx.compose.foundation.layout.Row(
            modifier = modifier,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(priority.color)
            )
            Spacer(modifier = Modifier.width(4.dp))
            androidx.compose.material3.Text(
                text = priority.displayName,
                fontSize = 12.sp,
                color = priority.color
            )
        }
    } else {
        // 仅显示圆点
        Box(
            modifier = modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(priority.color)
        )
    }
}

/**
 * 根据整数值获取优先级枚举
 *
 * @param priorityValue 优先级值 (0=低, 1=中, 2=高)
 * @return 对应的 TodoPriority 枚举，默认返回 LOW
 */
fun Int.toTodoPriority(): TodoPriority {
    return when (this) {
        2 -> TodoPriority.HIGH
        1 -> TodoPriority.MEDIUM
        else -> TodoPriority.LOW
    }
}
