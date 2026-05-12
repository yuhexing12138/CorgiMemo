package com.corgimemo.app.ui.theme

import androidx.compose.ui.graphics.Color

val OrangePrimary = Color(0xFFFF9A5C)
val OrangePrimaryLight = Color(0xFFFFB88A)
val OrangePrimaryDark = Color(0xFFE68044)
val OrangeSecondary = Color(0xFFFFC9A0)
val OrangeTertiary = Color(0xFFFFE4D0)

val DarkOrangePrimary = Color(0xFFFFB380)
val DarkOrangePrimaryLight = Color(0xFFFFD4B3)
val DarkOrangePrimaryDark = Color(0xFFE68044)

val LightColorScheme = androidx.compose.material3.lightColorScheme(
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

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
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