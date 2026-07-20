package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 优先级颜色统一源（与 UI 设计规范对齐）
 *
 * 数值约定（与待办编辑页保持一致）：
 * 0 = 无优先级（浅绿 #C8E6C9，区别于高/中/低，传递"无需特殊处理"的低压力感）
 * 1 = 低
 * 2 = 中
 * 3 = 高
 *
 * 颜色来源：[UI 设计规范] §12.1.2.3 功能色
 *   - 高优先级：#FF8A80（柔红）
 *   - 中优先级：#FFB74D（柔橙）
 *   - 低优先级：#90CAF9（柔蓝）
 *   - 无优先级：#C8E6C9（浅绿，Material Green 200）[v2026-07-20]
 */
object PriorityColors {
    /** 高优先级 - 柔红（避免焦虑） */
    val High = Color(0xFFFF8A80)

    /** 中优先级 - 柔橙 */
    val Medium = Color(0xFFFFB74D)

    /** 低优先级 - 柔蓝 */
    val Low = Color(0xFF90CAF9)

    /**
     * 无优先级 - 浅绿（Material Green 200）
     *
     * 设计意图：与现有高/中/低（红/橙/蓝）色调区分，传递"无需特殊处理"的低压力感，
     * 避免对用户造成紧迫焦虑（与项目"治愈、温暖"理念一致）。
     *
     * 颜色来源：[UI 设计规范] §12.1.2.3 功能色 → §12.1.10 待办卡片优先级视觉标识
     */
    val None = Color(0xFFC8E6C9)

    /**
     * 数值 → 颜色
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @return 对应颜色；0 返回 None 浅绿，其他非法值兜底返回 None 浅绿
     */
    fun colorOf(priority: Int): Color = when (priority) {
        3 -> High
        2 -> Medium
        1 -> Low
        else -> None
    }

    /**
     * 已完成态浅色版（Material Design 200/50 色调色板）
     *
     * 应用于已完成（status=1）待办的优先级竖线 / 边框 / 阴影。
     * 在原 priority 色基础上大幅降低饱和度、提亮，
     * 保留原色色相但视觉上"淡化"，建立"完成项更弱"层级。
     *
     * 颜色来源：[UI 设计规范] §12.1.2.4 状态色 - 已完成
     *   - 高优先级淡化：#FFCDD2（Material Red 200）
     *   - 中优先级淡化：#FFE0B2（Material Orange 200）
     *   - 低优先级淡化：#BBDEFB（Material Blue 200）
     *   - 无优先级淡化：#E8F5E9（Material Green 50）[v2026-07-20]
     */
    val HighDim = Color(0xFFFFCDD2)
    val MediumDim = Color(0xFFFFE0B2)
    val LowDim = Color(0xFFBBDEFB)

    /**
     * 无优先级淡化色 - 极浅绿（Material Green 50）
     *
     * 已完成态的"无优先级"竖线/边框/阴影使用此色，比未完成态的 #C8E6C9 更浅，
     * 符合"完成项更弱"的降权原则。
     */
    val NoneDim = Color(0xFFE8F5E9)

    /**
     * 数值 → 已完成态浅色版颜色
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @return 对应浅色；0 返回 NoneDim 极浅绿，其他非法值兜底返回 NoneDim
     */
    fun dimColorOf(priority: Int): Color = when (priority) {
        3 -> HighDim
        2 -> MediumDim
        1 -> LowDim
        else -> NoneDim
    }

    /**
     * 优先级三联视觉数据类（用于待办卡片三处装饰）
     *
     * 统一封装一个优先级在卡片上的 3 种视觉元素：
     * - [bar]：左侧 4dp 竖条（不透明 alpha=1.0）
     * - [border]：卡片边框基色（调用方再 .copy(alpha = 0.6f)）
     * - [shadow]：卡片阴影基色（已带 alpha=0.3f，可直接传入 Modifier.shadow）
     *
     * 数值约定（与 [colorOf] 一致）：
     * 0 = 无（浅绿），1 = 低（柔蓝），2 = 中（柔橙），3 = 高（柔红）
     */
    data class PriorityVisual(
        val bar: Color,
        val border: Color,
        val shadow: Color
    )

    /**
     * 数值 → 优先级三联视觉
     *
     * 已完成态（isCompleted=true）时三联颜色全部使用 dim 浅色版（保留色相、降饱和），
     * 与现有优先级竖线降权规则保持一致，让"边框 + 阴影 + 竖条"三层视觉同步降权。
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @param isCompleted 是否已完成（true 时三联全部用 dim 色）
     * @return PriorityVisual 三联色值
     */
    fun priorityVisualOf(priority: Int, isCompleted: Boolean = false): PriorityVisual {
        val base = if (isCompleted) dimColorOf(priority) else colorOf(priority)
        return PriorityVisual(
            bar = base,
            border = base,
            shadow = base.copy(alpha = 0.3f)
        )
    }
}
