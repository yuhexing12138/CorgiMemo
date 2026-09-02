package com.corgimemo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 当前所用字体的真实字重集合（字面重列表）。
 *
 * **维护约定（重要）**：Compose 无法在运行时枚举某 FontFamily 实际支持哪些字面重
 * （无公开 API），因此「当前字体真实字重」必须显式维护。本常量刻意与字体定义
 * [FontFamily.Default]（见上方 Typography 各 TextStyle）放在同一文件，
 * 以便换字体时一眼看到、同步修改，避免漏改导致加粗档位与实际字体不匹配。
 *
 * 灵感编辑页加粗档位 [BOLD_WEIGHT_TIERS] 由此派生：取集合中大于默认字重(400)的前三档。
 *
 * 当前字体 = FontFamily.Default（系统字体，典型为 Roboto），真实字面约
 * 100 / 300 / 400 / 500 / 700 / 900，故派生档位 = [500, 700, 900]（三档当前设备视觉分明）。
 *
 * 引入自定义字体（如 Inter / Noto Sans 可变字体）时：
 * 1. 上方 Typography 各 TextStyle 的 `fontFamily = FontFamily.Default` 换成自定义 FontFamily；
 * 2. 把本集合改为该字体真实字重（如 100..900 全阶梯），档位即自动变为 500 / 600 / 700。
 */
internal val FONT_AVAILABLE_WEIGHTS: List<Int> = listOf(100, 300, 400, 500, 700, 900)

/**
 * 灵感编辑页「加粗程度」档位（对应工具栏 B1 / B2 / B3 三档）。
 *
 * 取 [FONT_AVAILABLE_WEIGHTS] 中大于默认字重(400)的前三档，随字体切换自动调整，
 * 字体无关。该常量同时作为「清除已选字重、避免叠加」的遍历来源。
 *
 * 700 走 markdown `**`；其余档走 `<span style="font-weight:N">` 保留数值
 * （库侧 `parseCssFontWeight` 已支持任意整数字重兜底，往返不丢）。
 */
internal val BOLD_WEIGHT_TIERS: List<Int> =
    FONT_AVAILABLE_WEIGHTS.filter { it > FontWeight.Normal.weight }.take(3)