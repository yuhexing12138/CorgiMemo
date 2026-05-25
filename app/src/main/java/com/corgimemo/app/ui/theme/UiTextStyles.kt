package com.corgimemo.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * UI 设计规范文字样式定义
 *
 * 提供符合设计规范的预定义 TextStyle
 */
object UiTextStyles {
    /**
     * 页面标题样式
     * 字号: 22sp, 字重: Bold, 行高: 28sp
     */
    val PageTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp
    )

    /**
     * 卡片标题样式
     * 字号: 16sp, 字重: Medium, 行高: 24sp
     */
    val CardTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp
    )

    /**
     * 正文样式
     * 字号: 14sp, 字重: Regular, 行高: 20sp
     */
    val Body = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )

    /**
     * 辅助文字样式
     * 字号: 12sp, 字重: Regular, 行高: 16sp
     */
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )

    /**
     * 按钮文字样式
     * 字号: 14sp, 字重: Medium, 行高: 20sp
     */
    val Button = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    )
}
