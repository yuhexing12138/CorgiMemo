package com.corgimemo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

@Composable
fun CorgiMemoTheme(
    darkTheme: Boolean,
    themeColor: String = "orange",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getColorScheme(themeColor, darkTheme)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // 状态栏外观由 WindowInsetsControllerCompat 统一管理（statusBarColor 在 API 35 已废弃）。
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    val fontEntry by FontManager.currentEntry.collectAsState()
    val latinEntry by FontManager.latinEntry.collectAsState()
    val bodyFamily = remember(fontEntry, latinEntry) {
        FontManager.combinedFamily(fontEntry, latinEntry)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildTypography(bodyFamily),
        content = content
    )
}