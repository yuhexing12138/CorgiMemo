package com.corgimemo.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UI 设计规范尺寸定义
 *
 * 统一管理应用中所有间距和尺寸常量，替代硬编码的 .dp 值
 */
object UiDimensions {
    // ========== 间距 ==========
    /** 页面边距 */
    val spacingPageMargin: Dp = 16.dp

    /** 卡片间距 */
    val spacingCardGap: Dp = 12.dp

    /** 卡片内边距 */
    val spacingCardPadding: Dp = 16.dp

    /** 小间距 */
    val spacingSmall: Dp = 8.dp

    /** 微间距 */
    val spacingTiny: Dp = 4.dp

    // ========== 尺寸 ==========
    /** 列表项高度 */
    val sizeListItemHeight: Dp = 72.dp

    /** 按钮高度 */
    val sizeButtonHeight: Dp = 48.dp

    /** 大图标 */
    val iconLarge: Dp = 24.dp

    /** 中图标 */
    val iconMedium: Dp = 20.dp

    /** 小图标 */
    val iconSmall: Dp = 16.dp

    // ========== 圆角 ==========
    /** 大圆角（卡片） */
    val cornerRadiusLarge: Dp = 16.dp

    /** 中圆角 */
    val cornerRadiusMedium: Dp = 12.dp

    /** 小圆角（按钮/输入框） */
    val cornerRadiusSmall: Dp = 8.dp
}
