package com.corgimemo.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前选中的正文字体（反应式单例，镜像 [ThemeManager]）。
 *
 * 默认思源黑体（[FontCatalog.DEFAULT_ID]）。App 启动时由 [SettingsViewModel.loadSettings]
 * 调用 [initFont] 从 ESP 读取用户偏好并同步；设置页切换时调用 [setFont] 同时更新内存与持久化。
 *
 * 另持有「英文/数字字体」回退层（[latinEntry]，可为 null）。合成规则见 [combinedFamily]：
 * 拉丁字形优先用该字体，中文走正文字体，二者互不打架（[CorgiMemoTheme] 据此生成 [Typography]）。
 *
 * 消费方：
 * - [CorgiMemoTheme]（Theme.kt）用 [combinedFamily] 生成动态 [Typography]
 * - [FontWeightProbe] / 编辑工具栏用 [currentEntry]（正文字体）取探测字体与字重档位
 */
object FontManager {

    private val _currentEntry = MutableStateFlow(FontCatalog.get(FontCatalog.DEFAULT_ID))
    val currentEntry: StateFlow<FontEntry> = _currentEntry.asStateFlow()

    /**
     * 当前选中的「英文/数字字体」回退层（拉丁字体）；null 表示不叠加，
     * 英文/数字沿用正文字体自带的拉丁字形。
     */
    private val _latinEntry = MutableStateFlow<FontEntry?>(null)
    val latinEntry: StateFlow<FontEntry?> = _latinEntry.asStateFlow()

    /** 启动时从 ESP 偏好初始化（仅设置内部状态，不重复写盘） */
    fun initFont(fontId: String) {
        _currentEntry.value = FontCatalog.get(fontId)
    }

    /** 运行时切换正文字体：更新内存状态（持久化由调用方负责，见 [SettingsViewModel.setFontId]） */
    fun setFont(fontId: String) {
        _currentEntry.value = FontCatalog.get(fontId)
    }

    /** 启动时从 ESP 偏好初始化拉丁回退层（仅设置内部状态，不重复写盘） */
    fun initLatin(latinId: String) {
        _latinEntry.value = FontCatalog.getLatin(latinId)
    }

    /** 运行时切换拉丁回退层：更新内存状态（持久化由调用方负责，见 [SettingsViewModel.setLatinFontId]） */
    fun setLatin(latinId: String) {
        _latinEntry.value = FontCatalog.getLatin(latinId)
    }

    /**
     * 合成最终正文字体族：拉丁回退层在前（优先拉丁字形），正文字体在后（CJK 回退）。
     * null 拉丁层时直接返回正文字体族。
     */
    fun combinedFamily(cjk: FontEntry, latin: FontEntry?): FontFamily =
        if (latin == null) cjk.family else FontFamily(latin.fonts + cjk.fonts)

    /** 便捷：当前正文字体标签（供 [FontWeightProbe] 缓存隔离） */
    val tag: String get() = _currentEntry.value.tag

    /** 便捷：当前正文字体加粗候选档位（B1/B2/B3） */
    val boldTiers: List<Int> get() = _currentEntry.value.boldTiers

    /** 便捷：当前正文字体按字重取 Typeface（供 [FontWeightProbe] 像素探测） */
    fun typefaceForWeight(context: android.content.Context, weight: Int): android.graphics.Typeface =
        _currentEntry.value.typefaceForWeight(context, weight)

    /** Compose 侧收集当前正文字体条目（供 Theme.kt 生成 Typography 基线） */
    @Composable
    fun collectCurrentEntry(): FontEntry = _currentEntry.collectAsState().value
}
