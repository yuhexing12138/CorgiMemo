package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 已完成态视觉降权色值统一源
 *
 * 应用于已完成（status=1）的待办卡片元素。所有彩色元素降权为灰色系，
 * 弱于正常态的次要文字（#666666），实现"完成项更弱"的视觉层级。
 *
 * 颜色来源：[UI 设计规范] §12.1.2.4 状态色 - 已完成
 *   - 已完成-文字：#888888（亮）/ #6E6E6E（暗）
 *   - 已完成-勾选背景：#BDBDBD（亮）/ #5A5A5A（暗）
 *
 * 注意：优先级竖线不使用本文件，详见 [PriorityColors.dimColorOf] 的浅色版色值
 *   （#FFCDD2 / #FFE0B2 / #BBDEFB，保留原优先级色相但降低饱和度）。
 */
object CompletedColors {
    /**
     * 已完成态文字色
     *
     * 应用于：标题、描述、完成时间、分类文字等所有彩色文字元素
     * 亮色模式：#888888（中灰，弱于次要文字 #666666）
     * 深色模式：#6E6E6E（中深灰）
     */
    val Text = Color(0xFF888888)
    val TextDark = Color(0xFF6E6E6E)

    /**
     * 已完成态勾选框背景色
     *
     * 应用于：SubTaskCheckbox 已勾选且父待办已完成时
     * 亮色模式：#BDBDBD（浅灰，不抢焦点）
     * 深色模式：#5A5A5A（中深灰）
     */
    val CheckboxBg = Color(0xFFBDBDBD)
    val CheckboxBgDark = Color(0xFF5A5A5A)

    /**
     * 已完成态勾选框背景色（变淡橙色，非灰色）
     *
     * 应用于：父/子待办勾选框的"已完成"态
     *
     * 设计意图：用户要求"保持橙色系配色方案，不得更改为灰色，
     * 实现颜色深度降低效果（即颜色变淡处理）"。因此本字段是固定
     * 浅橙色 #FFCCAB，而非 CheckboxBg 的灰色。
     *
     * 视觉语义：
     * - 保留"完成 = 橙色任务"的色彩联想
     * - 通过降低饱和度/提亮，建立"完成项更弱"的层级
     * - 跨主题固定色，避免不同主题下色彩不一致
     */
    val CheckboxBgDim = Color(0xFFFFCCAB)
}
