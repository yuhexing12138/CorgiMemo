package com.corgimemo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

val OrangePrimary = Color(0xFFFF9A5C)
val OrangePrimaryLight = Color(0xFFFFB88A)
val OrangePrimaryDark = Color(0xFFE68044)
val OrangeSecondary = Color(0xFFFFC9A0)
val OrangeTertiary = Color(0xFFFFE4D0)

val DarkOrangePrimary = Color(0xFFFFB380)
val DarkOrangePrimaryLight = Color(0xFFFFD4B3)
val DarkOrangePrimaryDark = Color(0xFFE68044)

val MintPrimary = Color(0xFF4ECDC4)
val MintPrimaryLight = Color(0xFF7EE8E1)
val MintPrimaryDark = Color(0xFF2AB3AA)
val MintSecondary = Color(0xFFB5EBE6)
val MintTertiary = Color(0xFFE0F7F5)

val DarkMintPrimary = Color(0xFF6FEDE4)
val DarkMintPrimaryLight = Color(0xFF9FF5F0)
val DarkMintPrimaryDark = Color(0xFF2AB3AA)

val SkyPrimary = Color(0xFF5BA8E0)
val SkyPrimaryLight = Color(0xFF8FC5F0)
val SkyPrimaryDark = Color(0xFF3A8BC8)
val SkySecondary = Color(0xFFBDD9F2)
val SkyTertiary = Color(0xFFDFEBFA)

val DarkSkyPrimary = Color(0xFF8CC8F5)
val DarkSkyPrimaryLight = Color(0xFFB5DDF8)
val DarkSkyPrimaryDark = Color(0xFF3A8BC8)

val SakuraPrimary = Color(0xFFFF9AA2)
val SakuraPrimaryLight = Color(0xFFFFBFC5)
val SakuraPrimaryDark = Color(0xFFF07882)
val SakuraSecondary = Color(0xFFFFD4D8)
val SakuraTertiary = Color(0xFFFFEAEB)

val DarkSakuraPrimary = Color(0xFFFFBFC5)
val DarkSakuraPrimaryLight = Color(0xFFFFD4D8)
val DarkSakuraPrimaryDark = Color(0xFFF07882)

val LavenderPrimary = Color(0xFFB39DDB)
val LavenderPrimaryLight = Color(0xFFD0BFFF)
val LavenderPrimaryDark = Color(0xFF9575CD)
val LavenderSecondary = Color(0xFFE0CEFC)
val LavenderTertiary = Color(0xFFF3EAFE)

val DarkLavenderPrimary = Color(0xFFD0BFFF)
val DarkLavenderPrimaryLight = Color(0xFFE0CEFC)
val DarkLavenderPrimaryDark = Color(0xFF9575CD)

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

val MintLightColorScheme = lightColorScheme(
    primary = MintPrimary,
    onPrimary = Color.White,
    primaryContainer = MintSecondary,
    onPrimaryContainer = Color(0xFF1A4F49),
    secondary = MintTertiary,
    onSecondary = Color(0xFF2A5F58),
    secondaryContainer = MintSecondary,
    onSecondaryContainer = Color(0xFF1A4F49),
    background = Color(0xFFF5FBFA),
    onBackground = Color(0xFF1A2F2B),
    surface = Color.White,
    onSurface = Color(0xFF1A2F2B),
    surfaceVariant = MintTertiary,
    onSurfaceVariant = Color(0xFF3A6862),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6BA099),
    outlineVariant = Color(0xFFB5DBD6),
    scrim = Color(0xFF000000)
)

val MintDarkColorScheme = darkColorScheme(
    primary = DarkMintPrimary,
    onPrimary = Color(0xFF1A4F49),
    primaryContainer = MintPrimaryDark,
    onPrimaryContainer = Color(0xFFE0F7F5),
    secondary = Color(0xFF5ABFB6),
    onSecondary = Color(0xFFE0F7F5),
    secondaryContainer = Color(0xFF2A7872),
    onSecondaryContainer = Color(0xFFE0F7F5),
    background = Color(0xFF0F2421),
    onBackground = Color(0xFFE0F7F5),
    surface = Color(0xFF1A332F),
    onSurface = Color(0xFFE0F7F5),
    surfaceVariant = Color(0xFF2A504A),
    onSurfaceVariant = Color(0xFFB5DBD6),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8CBFB8),
    outlineVariant = Color(0xFF2A504A),
    scrim = Color(0xFF000000)
)

val SkyLightColorScheme = lightColorScheme(
    primary = SkyPrimary,
    onPrimary = Color.White,
    primaryContainer = SkySecondary,
    onPrimaryContainer = Color(0xFF1A3A5C),
    secondary = SkyTertiary,
    onSecondary = Color(0xFF2A4F6E),
    secondaryContainer = SkySecondary,
    onSecondaryContainer = Color(0xFF1A3A5C),
    background = Color(0xFFF5FAFE),
    onBackground = Color(0xFF1A2836),
    surface = Color.White,
    onSurface = Color(0xFF1A2836),
    surfaceVariant = SkyTertiary,
    onSurfaceVariant = Color(0xFF3A6080),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6B9BC8),
    outlineVariant = Color(0xFFBDD4E8),
    scrim = Color(0xFF000000)
)

val SkyDarkColorScheme = darkColorScheme(
    primary = DarkSkyPrimary,
    onPrimary = Color(0xFF1A3A5C),
    primaryContainer = SkyPrimaryDark,
    onPrimaryContainer = Color(0xFFDFEBFA),
    secondary = Color(0xFF5A9AC8),
    onSecondary = Color(0xFFDFEBFA),
    secondaryContainer = Color(0xFF2A6088),
    onSecondaryContainer = Color(0xFFDFEBFA),
    background = Color(0xFF0F1A26),
    onBackground = Color(0xFFDFEBFA),
    surface = Color(0xFF1A2838),
    onSurface = Color(0xFFDFEBFA),
    surfaceVariant = Color(0xFF2A4058),
    onSurfaceVariant = Color(0xFFBDD4E8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8CB8D8),
    outlineVariant = Color(0xFF2A4058),
    scrim = Color(0xFF000000)
)

val SakuraLightColorScheme = lightColorScheme(
    primary = SakuraPrimary,
    onPrimary = Color.White,
    primaryContainer = SakuraSecondary,
    onPrimaryContainer = Color(0xFF4A1A20),
    secondary = SakuraTertiary,
    onSecondary = Color(0xFF5A2A30),
    secondaryContainer = SakuraSecondary,
    onSecondaryContainer = Color(0xFF4A1A20),
    background = Color(0xFFFFF5F6),
    onBackground = Color(0xFF361A1E),
    surface = Color.White,
    onSurface = Color(0xFF361A1E),
    surfaceVariant = SakuraTertiary,
    onSurfaceVariant = Color(0xFF6A3840),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFC88890),
    outlineVariant = Color(0xFFFFD4D8),
    scrim = Color(0xFF000000)
)

val SakuraDarkColorScheme = darkColorScheme(
    primary = DarkSakuraPrimary,
    onPrimary = Color(0xFF4A1A20),
    primaryContainer = SakuraPrimaryDark,
    onPrimaryContainer = Color(0xFFFFEAEB),
    secondary = Color(0xFFCC8890),
    onSecondary = Color(0xFFFFEAEB),
    secondaryContainer = Color(0xFF8A4A52),
    onSecondaryContainer = Color(0xFFFFEAEB),
    background = Color(0xFF1A0E10),
    onBackground = Color(0xFFFFEAEB),
    surface = Color(0xFF2E1820),
    onSurface = Color(0xFFFFEAEB),
    surfaceVariant = Color(0xFF4A2830),
    onSurfaceVariant = Color(0xFFFFD4D8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFFB88890),
    outlineVariant = Color(0xFF4A2830),
    scrim = Color(0xFF000000)
)

val LavenderLightColorScheme = lightColorScheme(
    primary = LavenderPrimary,
    onPrimary = Color.White,
    primaryContainer = LavenderSecondary,
    onPrimaryContainer = Color(0xFF2A1A4A),
    secondary = LavenderTertiary,
    onSecondary = Color(0xFF4A3068),
    secondaryContainer = LavenderSecondary,
    onSecondaryContainer = Color(0xFF2A1A4A),
    background = Color(0xFFFAF8FD),
    onBackground = Color(0xFF2A1A36),
    surface = Color.White,
    onSurface = Color(0xFF2A1A36),
    surfaceVariant = LavenderTertiary,
    onSurfaceVariant = Color(0xFF5A4878),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF9980BB),
    outlineVariant = Color(0xFFE0CEFC),
    scrim = Color(0xFF000000)
)

val LavenderDarkColorScheme = darkColorScheme(
    primary = DarkLavenderPrimary,
    onPrimary = Color(0xFF2A1A4A),
    primaryContainer = LavenderPrimaryDark,
    onPrimaryContainer = Color(0xFFF3EAFE),
    secondary = Color(0xFFA080C8),
    onSecondary = Color(0xFFF3EAFE),
    secondaryContainer = Color(0xFF6A4A98),
    onSecondaryContainer = Color(0xFFF3EAFE),
    background = Color(0xFF14101E),
    onBackground = Color(0xFFF3EAFE),
    surface = Color(0xFF241A30),
    onSurface = Color(0xFFF3EAFE),
    surfaceVariant = Color(0xFF3A2850),
    onSurfaceVariant = Color(0xFFE0CEFC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFB4AB),
    outline = Color(0xFF8870A8),
    outlineVariant = Color(0xFF3A2850),
    scrim = Color(0xFF000000)
)

fun getColorScheme(colorName: String, darkTheme: Boolean): ColorScheme {
    return when (colorName) {
        "mint" -> if (darkTheme) MintDarkColorScheme else MintLightColorScheme
        "sky" -> if (darkTheme) SkyDarkColorScheme else SkyLightColorScheme
        "sakura" -> if (darkTheme) SakuraDarkColorScheme else SakuraLightColorScheme
        "lavender" -> if (darkTheme) LavenderDarkColorScheme else LavenderLightColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }
}