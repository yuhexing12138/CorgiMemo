package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 优先级颜色统一源（与 UI 设计规范对齐）
 *
 * 数值约定（与待办编辑页保持一致）：
 * 0 = 无优先级（不显示色条）
 * 1 = 低
 * 2 = 中
 * 3 = 高
 *
 * 颜色来源：[UI 设计规范] §12.1.2.3 功能色
 *   - 高优先级：#FF8A80（柔红）
 *   - 中优先级：#FFB74D（柔橙）
 *   - 低优先级：#90CAF9（柔蓝）
 */
object PriorityColors {
    /** 高优先级 - 柔红（避免焦虑） */
    val High = Color(0xFFFF8A80)

    /** 中优先级 - 柔橙 */
    val Medium = Color(0xFFFFB74D)

    /** 低优先级 - 柔蓝 */
    val Low = Color(0xFF90CAF9)

    /** 无优先级 - 透明 */
    val None = Color.Transparent

    /**
     * 数值 → 颜色
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @return 对应颜色；0 或其他非法值返回 None（透明）
     */
    fun colorOf(priority: Int): Color = when (priority) {
        3 -> High
        2 -> Medium
        1 -> Low
        else -> None
    }
}
