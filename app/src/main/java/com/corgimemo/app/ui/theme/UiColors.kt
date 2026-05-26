package com.corgimemo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * UI 设计规范颜色定义
 *
 * 统一管理应用中所有颜色常量，替代硬编码的 Color(0x...)
 * 可与 MaterialTheme.colorScheme 配合使用
 */
object UiColors {
    // ========== 主色系 ==========
    /** 品牌主色 - 暖橙色 */
    val Primary = Color(0xFFFF9A5C)

    /** 选中态背景 */
    val PrimaryLight = Color(0xFFFFE0C0)

    /** 按压态 */
    val PrimaryDark = Color(0xFFE88A4D)

    // ========== 背景色 ==========
    /** 页面背景 */
    val Background = Color.White

    /** 卡片/表面 */
    val Surface = Color.White

    /** 搜索栏背景 - 暖橙色浅色 */
    val SearchBackground = Color(0xFFFFF3E8)

    // ========== 语义色 ==========
    /** 成功状态 */
    val Success = Color(0xFF4CAF50)

    /** 警告状态 */
    val Warning = Color(0xFF9800)

    /** 错误/危险 */
    val Error = Color(0xFFF44336)

    // ========== 文字色 ==========
    /** 主文字 */
    val TextPrimary = Color(0xFF1C1B1F)

    /** 次要文字 */
    val TextSecondary = Color(0xFF79747E)

    // ========== 分割线/边框 ==========
    /** 分割线 */
    val Divider = Color(0xFFE0E0E0)

    /** 边框 */
    val Outline = Color(0xFFBDBDBD)
}
