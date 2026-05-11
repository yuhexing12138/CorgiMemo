package com.corgimemo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 浅色模式颜色配置
 * 使用暖橙色 #FF9A5C 作为主色调
 */
private val LightColorScheme = lightColorScheme(
    primary = OrangePrimary,
    onPrimary = OrangeOnPrimary,
    primaryContainer = OrangePrimaryContainer,
    onPrimaryContainer = OrangeOnPrimaryContainer,
    secondary = OrangeSecondary,
    onSecondary = OrangeOnSecondary,
    secondaryContainer = OrangeSecondaryContainer,
    onSecondaryContainer = OrangeOnSecondaryContainer,
    tertiary = OrangeTertiary,
    onTertiary = OrangeOnTertiary,
    tertiaryContainer = OrangeTertiaryContainer,
    onTertiaryContainer = OrangeOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
)

/**
 * 深色模式颜色配置
 */
private val DarkColorScheme = darkColorScheme(
    primary = OrangePrimary,
    onPrimary = DarkOrangeOnPrimary,
    primaryContainer = DarkOrangePrimaryContainer,
    onPrimaryContainer = DarkOrangeOnPrimaryContainer,
    secondary = DarkOrangeSecondary,
    onSecondary = DarkOrangeOnSecondary,
    secondaryContainer = DarkOrangeSecondaryContainer,
    onSecondaryContainer = DarkOrangeOnSecondaryContainer,
    tertiary = DarkOrangeTertiary,
    onTertiary = DarkOrangeOnTertiary,
    tertiaryContainer = DarkOrangeTertiaryContainer,
    onTertiaryContainer = DarkOrangeOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
)

/**
 * 应用主题组合函数
 * 
 * @param darkTheme 是否使用深色模式（默认为系统设置）
 * @param dynamicColor 是否启用动态颜色（Android 12+）
 * @param content 应用内容
 */
@Composable
fun CorgiMemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // 选择颜色方案
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // 获取当前视图
    val view = LocalView.current
    
    // 如果视图还没有依附到窗口，跳过状态栏配置
    if (!view.isInEditMode) {
        SideEffect {
            // 获取 Activity 的窗口
            val window = (view.context as Activity).window
            
            // 设置状态栏颜色为当前主题的背景色
            window.statusBarColor = colorScheme.background.toArgb()
            
            // 配置状态栏图标颜色（根据背景色自动调整）
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // 应用 Material3 主题
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}