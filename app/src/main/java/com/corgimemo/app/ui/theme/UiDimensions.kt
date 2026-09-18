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

    // ========== 灵感编辑页头部（标题/时间行/正文三段式） ==========
    /**
     * 标题 → 时间行 的**布局盒间隙**（v2026-09-18 定版）。
     *
     * ⚠️ 此值不是文字墨迹（肉眼可见）间隙——墨迹间隙 =
     * 本值 + 上方行盒的字形下沉空间(标题 36sp 行盒 ≈7.8dp) +
     * 下方行盒的墨迹上方空间(时间行 12sp/16sp 行盒 ≈1.8dp)
     * ≈ 15.5dp。灵感编辑页两段头部墨迹间距已按真机截图逐像素
     * 复测对齐（均 ≈15.5dp），调整任一值须同步复测另一段。
     */
    val inspirationTitleToMetaGap: Dp = 6.dp

    /**
     * 时间行 → 正文 的**布局盒间隙**。
     *
     * 比标题段多 2dp 是**墨迹补偿**：正文首行 1.5 倍行距的行盒上方
     * 死空间(≈4.3dp)大于时间行的(≈1.8dp)，盒间隙多 2dp 才能使两段
     * 墨迹间距相等（8 + 3.2 + 4.3 ≈ 15.5dp，与标题段一致）。
     */
    val inspirationMetaToBodyGap: Dp = 8.dp

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
