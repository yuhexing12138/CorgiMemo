package com.corgimemo.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.corgimemo.app.ui.theme.ContentFontManager

/**
 * 内容字体排版（用户编辑的内容：灵感编辑/详情/主页）。
 *
 * 与 [MaterialTheme.typography]（设置页「正文字体」，仅影响 App chrome）解耦：
 * 默认系统默认字体，设置页切换正文字体**不会**影响内容。
 *
 * **每条灵感单独记字体（v58）**：编辑页打开灵感时由 VM 调 [ContentFontManager.setFonts]
 * 装载该条的字体（空 = 系统默认 / 拉丁跟随中文），本排版随其自动更新——
 * 编辑页正文/标题消费此 Local；列表卡片、时间线、详情页则改用
 * [ContentFontManager.contentFontFamily] 按各自条目解析，不读这里的「当前」状态。
 */
val LocalContentTypography = compositionLocalOf<Typography> { buildTypography(FontFamily.Default) }

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

    // 内容字体（用户编辑内容，每条灵感单独记录）独立于设置「正文字体」：
    // 编辑页由 ContentFontManager 按当前灵感装载（默认系统默认/跟随中文），设置切换不影响。
    // 组合规则与 App chrome 一致：拉丁回退层在前（英文/数字），中文字体在后。
    val contentEntry by ContentFontManager.currentEntry.collectAsState()
    val contentLatinId by ContentFontManager.currentLatinId.collectAsState()
    val contentTypography = remember(contentEntry, contentLatinId) {
        buildTypography(ContentFontManager.currentFamily(contentEntry, contentLatinId))
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildTypography(bodyFamily),
        content = {
            CompositionLocalProvider(LocalContentTypography provides contentTypography) {
                content()
            }
        }
    )
}