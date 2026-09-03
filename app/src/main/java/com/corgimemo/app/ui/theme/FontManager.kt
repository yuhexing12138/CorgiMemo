package com.corgimemo.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前选中的正文字体（反应式单例，镜像 [ThemeManager]）。
 *
 * 默认思源黑体（[FontCatalog.DEFAULT_ID]）。App 启动时由 [SettingsViewModel.loadSettings]
 * 调用 [initFont] 从 ESP 读取用户偏好并同步；设置页切换时调用 [setFont] 同时更新内存与持久化。
 *
 * 消费方：
 * - [CorgiMemoTheme]（Theme.kt）用它生成动态 [Typography]
 * - [FontWeightProbe] / 编辑工具栏用它取探测字体与字重档位
 */
object FontManager {

    private val _currentEntry = MutableStateFlow(FontCatalog.get(FontCatalog.DEFAULT_ID))
    val currentEntry: StateFlow<FontEntry> = _currentEntry.asStateFlow()

    /** 启动时从 ESP 偏好初始化（仅设置内部状态，不重复写盘） */
    fun initFont(fontId: String) {
        _currentEntry.value = FontCatalog.get(fontId)
    }

    /** 运行时切换字体：更新内存状态（持久化由调用方负责，见 [SettingsViewModel.setFontId]） */
    fun setFont(fontId: String) {
        _currentEntry.value = FontCatalog.get(fontId)
    }

    /** 便捷：当前字体标签（供 [FontWeightProbe] 缓存隔离） */
    val tag: String get() = _currentEntry.value.tag

    /** 便捷：当前字体加粗候选档位（B1/B2/B3） */
    val boldTiers: List<Int> get() = _currentEntry.value.boldTiers

    /** 便捷：当前字体按字重取 Typeface（供 [FontWeightProbe] 像素探测） */
    fun typefaceForWeight(context: android.content.Context, weight: Int): android.graphics.Typeface =
        _currentEntry.value.typefaceForWeight(context, weight)

    /** Compose 侧收集当前字体条目（供 Theme.kt 生成 Typography） */
    @Composable
    fun collectCurrentEntry(): FontEntry = _currentEntry.collectAsState().value
}
