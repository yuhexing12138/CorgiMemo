package com.corgimemo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

/**
 * CorgiMemo 主题配色定义
 *
 * 6 种主题配色（key 顺序与 [com.corgimemo.app.ui.screens.profile.components.ThemePresets] 一致）：
 * | key      | 名称   | 主色     | 浅色     |
 * |----------|--------|----------|----------|
 * | orange   | 暖阳橙 | #FF9A5C | #FFE4D0 |
 * | pink     | 樱花粉 | #FFB5C2 | #FFE0E6 |
 * | green    | 薄荷绿 | #7EC8A0 | #D4F0E0 |
 * | blue     | 天空蓝 | #7EB8DA | #D4E8F5 |
 * | purple   | 薰衣紫 | #B8A0D4 | #E8DFF5 |
 * | brown    | 奶茶棕 | #C4A882 | #F0E6D8 |
 *
 * 主色值与 UI 设计规范 12.1.3 一致；其余派生色（Light/Dark/Secondary/Tertiary）按 Material 3
 * 调色板规则衍生，亮色模式 = 同色系淡化 20-30%，深色模式 = 同色系提亮 15-20%。
 *
 * 调用入口：[getColorScheme]
 */

// ============ 暖阳橙（默认）============
val OrangePrimary = Color(0xFFFF9A5C)
val OrangePrimaryLight = Color(0xFFFFB88A)
val OrangePrimaryDark = Color(0xFFE68044)
val OrangeSecondary = Color(0xFFFFC9A0)
val OrangeTertiary = Color(0xFFFFE4D0)

val DarkOrangePrimary = Color(0xFFFFB380)
val DarkOrangePrimaryLight = Color(0xFFFFD4B3)
val DarkOrangePrimaryDark = Color(0xFFE68044)

// ============ 樱花粉 ============
val PinkPrimary = Color(0xFFFFB5C2)
val PinkPrimaryLight = Color(0xFFFFD4DC)
val PinkPrimaryDark = Color(0xFFE8959F)
val PinkSecondary = Color(0xFFFFE0E6)
val PinkTertiary = Color(0xFFFFF0F2)

val DarkPinkPrimary = Color(0xFFFFD4DC)
val DarkPinkPrimaryLight = Color(0xFFFFE5EA)
val DarkPinkPrimaryDark = Color(0xFFE8959F)

// ============ 薄荷绿 ============
val GreenPrimary = Color(0xFF7EC8A0)
val GreenPrimaryLight = Color(0xFFA8DCC0)
val GreenPrimaryDark = Color(0xFF5CAA82)
val GreenSecondary = Color(0xFFD4F0E0)
val GreenTertiary = Color(0xFFE8F7EE)

val DarkGreenPrimary = Color(0xFFA8DCC0)
val DarkGreenPrimaryLight = Color(0xFFC8E8D5)
val DarkGreenPrimaryDark = Color(0xFF5CAA82)

// ============ 天空蓝 ============
val BluePrimary = Color(0xFF7EB8DA)
val BluePrimaryLight = Color(0xFFA8D0E8)
val BluePrimaryDark = Color(0xFF5C95BD)
val BlueSecondary = Color(0xFFD4E8F5)
val BlueTertiary = Color(0xFFE8F2FA)

val DarkBluePrimary = Color(0xFFA8D0E8)
val DarkBluePrimaryLight = Color(0xFFC8E0F0)
val DarkBluePrimaryDark = Color(0xFF5C95BD)

// ============ 薰衣紫 ============
val PurplePrimary = Color(0xFFB8A0D4)
val PurplePrimaryLight = Color(0xFFD0BFE5)
val PurplePrimaryDark = Color(0xFF9580B5)
val PurpleSecondary = Color(0xFFE8DFF5)
val PurpleTertiary = Color(0xFFF3EDFA)

val DarkPurplePrimary = Color(0xFFD0BFE5)
val DarkPurplePrimaryLight = Color(0xFFE0D5F0)
val DarkPurplePrimaryDark = Color(0xFF9580B5)

// ============ 奶茶棕 ============
val BrownPrimary = Color(0xFFC4A882)
val BrownPrimaryLight = Color(0xFFD8C2A0)
val BrownPrimaryDark = Color(0xFFA68A60)
val BrownSecondary = Color(0xFFF0E6D8)
val BrownTertiary = Color(0xFFF8F0E4)

val DarkBrownPrimary = Color(0xFFD8C2A0)
val DarkBrownPrimaryLight = Color(0xFFE8D5B8)
val DarkBrownPrimaryDark = Color(0xFFA68A60)

// ============ 暖阳橙 ColorScheme（默认）============
val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = Color.White,
    primaryContainer = OrangeSecondary,
    onPrimaryContainer = Color(0xFF4A2C1A),
    secondary = OrangeTertiary,
    onSecondary = Color(0xFF5D3A1A),
    secondaryContainer = OrangeSecondary,
    onSecondaryContainer = Color(0xFF4A2C1A),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF2D1B0E),
    surface = Color.White,
    onSurface = Color(0xFF2D1B0E),
    surfaceVariant = OrangeTertiary,
    onSurfaceVariant = Color(0xFF5D4030),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8B7355),
    outlineVariant = Color(0xFFD4C4B0),
    scrim = Color(0xFF000000)
)

val DarkColorScheme = darkColorScheme(
    primary = DarkOrangePrimary,
    onPrimary = Color(0xFF4A2C1A),
    primaryContainer = DarkOrangePrimaryDark,
    onPrimaryContainer = Color(0xFFFFE4D0),
    secondary = Color(0xFFCC8855),
    onSecondary = Color(0xFFFFE4D0),
    secondaryContainer = Color(0xFF8B5A35),
    onSecondaryContainer = Color(0xFFFFE4D0),
    background = Color(0xFF1A0F08),
    onBackground = Color(0xFFFFE4D0),
    surface = Color(0xFF2D1B0E),
    onSurface = Color(0xFFFFE4D0),
    surfaceVariant = Color(0xFF4A3525),
    onSurfaceVariant = Color(0xFFD4C4B0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFFA39078),
    outlineVariant = Color(0xFF4A3525),
    scrim = Color(0xFF000000)
)

// ============ 樱花粉 ColorScheme ============
val PinkLightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PinkSecondary,
    onPrimaryContainer = Color(0xFF4A1A22),
    secondary = PinkTertiary,
    onSecondary = Color(0xFF5A2A32),
    secondaryContainer = PinkSecondary,
    onSecondaryContainer = Color(0xFF4A1A22),
    background = Color(0xFFFFF8F9),
    onBackground = Color(0xFF361A20),
    surface = Color.White,
    onSurface = Color(0xFF361A20),
    surfaceVariant = PinkTertiary,
    onSurfaceVariant = Color(0xFF6A3840),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFB58090),
    outlineVariant = Color(0xFFFFD4DC),
    scrim = Color(0xFF000000)
)

val PinkDarkColorScheme = darkColorScheme(
    primary = DarkPinkPrimary,
    onPrimary = Color(0xFF4A1A22),
    primaryContainer = PinkPrimaryDark,
    onPrimaryContainer = Color(0xFFFFF0F2),
    secondary = Color(0xFFCC8A95),
    onSecondary = Color(0xFFFFF0F2),
    secondaryContainer = Color(0xFF8A4858),
    onSecondaryContainer = Color(0xFFFFF0F2),
    background = Color(0xFF1A0E12),
    onBackground = Color(0xFFFFF0F2),
    surface = Color(0xFF2E1820),
    onSurface = Color(0xFFFFF0F2),
    surfaceVariant = Color(0xFF4A2830),
    onSurfaceVariant = Color(0xFFFFD4DC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFFB88895),
    outlineVariant = Color(0xFF4A2830),
    scrim = Color(0xFF000000)
)

// ============ 薄荷绿 ColorScheme ============
val GreenLightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenSecondary,
    onPrimaryContainer = Color(0xFF1A4A30),
    secondary = GreenTertiary,
    onSecondary = Color(0xFF2A5A40),
    secondaryContainer = GreenSecondary,
    onSecondaryContainer = Color(0xFF1A4A30),
    background = Color(0xFFF8FDF9),
    onBackground = Color(0xFF1A2A22),
    surface = Color.White,
    onSurface = Color(0xFF1A2A22),
    surfaceVariant = GreenTertiary,
    onSurfaceVariant = Color(0xFF3A6850),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6BAA85),
    outlineVariant = Color(0xFFD4F0E0),
    scrim = Color(0xFF000000)
)

val GreenDarkColorScheme = darkColorScheme(
    primary = DarkGreenPrimary,
    onPrimary = Color(0xFF1A4A30),
    primaryContainer = GreenPrimaryDark,
    onPrimaryContainer = Color(0xFFE8F7EE),
    secondary = Color(0xFF5CAA85),
    onSecondary = Color(0xFFE8F7EE),
    secondaryContainer = Color(0xFF2A6850),
    onSecondaryContainer = Color(0xFFE8F7EE),
    background = Color(0xFF0E1A14),
    onBackground = Color(0xFFE8F7EE),
    surface = Color(0xFF182E22),
    onSurface = Color(0xFFE8F7EE),
    surfaceVariant = Color(0xFF2A4A38),
    onSurfaceVariant = Color(0xFFD4F0E0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8CB8A0),
    outlineVariant = Color(0xFF2A4A38),
    scrim = Color(0xFF000000)
)

// ============ 天空蓝 ColorScheme ============
val BlueLightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = Color.White,
    primaryContainer = BlueSecondary,
    onPrimaryContainer = Color(0xFF1A3A5A),
    secondary = BlueTertiary,
    onSecondary = Color(0xFF2A4A6A),
    secondaryContainer = BlueSecondary,
    onSecondaryContainer = Color(0xFF1A3A5A),
    background = Color(0xFFF8FBFE),
    onBackground = Color(0xFF1A2638),
    surface = Color.White,
    onSurface = Color(0xFF1A2638),
    surfaceVariant = BlueTertiary,
    onSurfaceVariant = Color(0xFF3A6080),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6B95C0),
    outlineVariant = Color(0xFFD4E8F5),
    scrim = Color(0xFF000000)
)

val BlueDarkColorScheme = darkColorScheme(
    primary = DarkBluePrimary,
    onPrimary = Color(0xFF1A3A5A),
    primaryContainer = BluePrimaryDark,
    onPrimaryContainer = Color(0xFFE8F2FA),
    secondary = Color(0xFF5C95C0),
    onSecondary = Color(0xFFE8F2FA),
    secondaryContainer = Color(0xFF2A6088),
    onSecondaryContainer = Color(0xFFE8F2FA),
    background = Color(0xFF0E1A26),
    onBackground = Color(0xFFE8F2FA),
    surface = Color(0xFF182838),
    onSurface = Color(0xFFE8F2FA),
    surfaceVariant = Color(0xFF2A4058),
    onSurfaceVariant = Color(0xFFD4E8F5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8CB5D5),
    outlineVariant = Color(0xFF2A4058),
    scrim = Color(0xFF000000)
)

// ============ 薰衣紫 ColorScheme ============
val PurpleLightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleSecondary,
    onPrimaryContainer = Color(0xFF2A1A4A),
    secondary = PurpleTertiary,
    onSecondary = Color(0xFF4A3068),
    secondaryContainer = PurpleSecondary,
    onSecondaryContainer = Color(0xFF2A1A4A),
    background = Color(0xFFFAF8FD),
    onBackground = Color(0xFF2A1A36),
    surface = Color.White,
    onSurface = Color(0xFF2A1A36),
    surfaceVariant = PurpleTertiary,
    onSurfaceVariant = Color(0xFF5A4878),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF9580B5),
    outlineVariant = Color(0xFFE8DFF5),
    scrim = Color(0xFF000000)
)

val PurpleDarkColorScheme = darkColorScheme(
    primary = DarkPurplePrimary,
    onPrimary = Color(0xFF2A1A4A),
    primaryContainer = PurplePrimaryDark,
    onPrimaryContainer = Color(0xFFF3EDFA),
    secondary = Color(0xFF9580B8),
    onSecondary = Color(0xFFF3EDFA),
    secondaryContainer = Color(0xFF6A4A98),
    onSecondaryContainer = Color(0xFFF3EDFA),
    background = Color(0xFF14101E),
    onBackground = Color(0xFFF3EDFA),
    surface = Color(0xFF241A30),
    onSurface = Color(0xFFF3EDFA),
    surfaceVariant = Color(0xFF3A2850),
    onSurfaceVariant = Color(0xFFE8DFF5),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8870A8),
    outlineVariant = Color(0xFF3A2850),
    scrim = Color(0xFF000000)
)

// ============ 奶茶棕 ColorScheme ============
val BrownLightColorScheme = lightColorScheme(
    primary = BrownPrimary,
    onPrimary = Color.White,
    primaryContainer = BrownSecondary,
    onPrimaryContainer = Color(0xFF3A2A1A),
    secondary = BrownTertiary,
    onSecondary = Color(0xFF4A3A2A),
    secondaryContainer = BrownSecondary,
    onSecondaryContainer = Color(0xFF3A2A1A),
    background = Color(0xFFFCF9F4),
    onBackground = Color(0xFF2A201A),
    surface = Color.White,
    onSurface = Color(0xFF2A201A),
    surfaceVariant = BrownTertiary,
    onSurfaceVariant = Color(0xFF6A5A48),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFA68A60),
    outlineVariant = Color(0xFFF0E6D8),
    scrim = Color(0xFF000000)
)

val BrownDarkColorScheme = darkColorScheme(
    primary = DarkBrownPrimary,
    onPrimary = Color(0xFF3A2A1A),
    primaryContainer = BrownPrimaryDark,
    onPrimaryContainer = Color(0xFFF8F0E4),
    secondary = Color(0xFFA68A60),
    onSecondary = Color(0xFFF8F0E4),
    secondaryContainer = Color(0xFF6A5030),
    onSecondaryContainer = Color(0xFFF8F0E4),
    background = Color(0xFF1A140E),
    onBackground = Color(0xFFF8F0E4),
    surface = Color(0xFF2E241A),
    onSurface = Color(0xFFF8F0E4),
    surfaceVariant = Color(0xFF4A3A28),
    onSurfaceVariant = Color(0xFFF0E6D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8C7458),
    outlineVariant = Color(0xFF4A3A28),
    scrim = Color(0xFF000000)
)

/**
 * 根据主题色 key 获取对应 ColorScheme
 *
 * 6 种主题色 key 与 `ThemePresets`（Profile 页"我的"主题配色卡）保持一致：
 * orange / pink / green / blue / purple / brown
 *
 * 未知 key 兜底为暖阳橙（向后兼容旧数据 "mint" / "sky" / "sakura" / "lavender" 也走兜底）。
 *
 * @param colorName 主题色 key
 * @param darkTheme 是否深色模式
 * @return 对应的 ColorScheme
 */
fun getColorScheme(colorName: String, darkTheme: Boolean): ColorScheme {
    return when (colorName) {
        "pink" -> if (darkTheme) PinkDarkColorScheme else PinkLightColorScheme
        "green" -> if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        "blue" -> if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        "purple" -> if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        "brown" -> if (darkTheme) BrownDarkColorScheme else BrownLightColorScheme
        // 默认 & 向后兼容（"orange" / 旧 key "mint" / "sky" / "sakura" / "lavender"）
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
}
