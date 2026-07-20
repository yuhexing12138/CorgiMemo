package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
     * - [shadow]：卡片阴影基色（**深色版优先级色** + alpha=1.0，可直接传入 Modifier.shadow）
     *
     * **shadow 颜色设计（v2026-07-20 v5 关键修复）**：
     * - 旧版本：`base.copy(alpha = 0.3f)` → 用浅色优先级色（200 系列）+ 30% alpha
     *   → 与浅色背景 #F8F6F3 混合后色差仅 95 → 阴影几乎不可见
     * - 新版本：`lerp(base, Color.Black, 0.4f)` → 60% 优先级色 + 40% 黑色
     *   → 深红棕 #993530 / 深橙棕 #996E30 / 深蓝 #56778F / 深绿灰 #7A8E7B
     *   → 与背景色差提升到 110-130 → 阴影明显可见
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
        // v2026-07-20 v5 关键修复：用深色版优先级色作 shadow
        // 旧版 base.copy(alpha=0.3f) 颜色对比度严重不足，混合到 #F8F6F3 背景后几乎不可见
        // 新版 lerp(base, Color.Black, 0.4f) = 60% 优先级色 + 40% 黑色，得到深色版
        //   - HIGH (#FF8A80) → #993530（深红棕）色差 ~130
        //   - MEDIUM (#FFB74D) → #996E30（深橙棕）色差 ~110
        //   - LOW (#90CAF9) → #56778F（深蓝）色差 ~130
        //   - NONE (#C8E6C9) → #7A8E7B（深绿灰）色差 ~95
        // 不透明 alpha=1.0，让调用方根据长按状态决定 alpha
        val deepShadow = lerp(base, Color.Black, 0.4f)
        return PriorityVisual(
            bar = base,
            border = base,
            shadow = deepShadow
        )
    }
}
