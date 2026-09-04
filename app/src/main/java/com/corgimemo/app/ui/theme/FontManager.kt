package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前选中的正文字体（反应式单例，镜像 [ThemeManager]）。
 *
 * 默认系统默认字体（[FontCatalog.DEFAULT_ID] = "system_default"，渲染走设备系统字体）。
 * App 启动时由 [SettingsViewModel.loadSettings]
 * 调用 [initFont] 从 ESP 读取用户偏好并同步；设置页切换时调用 [setFont] 同时更新内存与持久化。
 *
 * 另持有「英文/数字字体」回退层（[latinEntry]，可为 null）。合成规则见 [combinedFamily]：
 * 拉丁字形优先用该字体，中文走正文字体，二者互不打架（[CorgiMemoTheme] 据此生成 [Typography]）。
 *
 * 消费方：
 * - [CorgiMemoTheme]（Theme.kt）用 [combinedFamily] 生成动态 [Typography]（App chrome 用「正文字体」）
 * - [ContentFontManager] 承载**内容字体**（用户编辑内容，默认系统字体），[LocalContentTypography]
 *   与编辑工具栏据此取排版与字重档位；设置页「正文字体」切换不影响内容。
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
     * 合成最终正文字体族：拉丁回退层优先（拉丁字形走拉丁字体），中文走中文字体，二者按字形各取所需。
     *
     * - [latin] 为 null：直接返回正文字体族 [FontEntry.family]（中文单一字体，行为不变）。
     * - [latin] 非 null：交由 [FontCatalog.combinedFamilyFonts] 生成**按字重回退链**的字体列表，
     *   每个字重单元格用 `Typeface.CustomFallbackBuilder` 把拉丁作 base、中文作 `addCustomFallback` 串成单一按字形回退的 Typeface，
     *   从而拉丁字形走拉丁字体、中文字形走中文字体（修复旧实现同字重只解析单一 Typeface、中文落到系统兜底的问题）。
     */
    fun combinedFamily(cjk: FontEntry, latin: FontEntry?): FontFamily =
        if (latin == null) cjk.family else FontFamily(FontCatalog.combinedFamilyFonts(cjk, latin))

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

/**
 * 内容字体（用户编辑的内容：灵感编辑/详情/主页，**每条灵感单独记录**）。
 *
 * 与 [FontManager.currentEntry]（设置页「正文字体」，仅影响 App chrome）解耦：
 * 默认系统默认字体，设置页切换正文字体**不会**影响此处。
 *
 * **按灵感隔离**（v58）：字体选择落在 `inspirations.fontId / latinFontId` 两列，
 * 编辑页打开某条灵感时调 [setFonts] 装载该条的字体（即时生效于编辑内容与字重探测），
 * 保存时由 VM 把选择写回实体；列表/详情页则用 [contentFontFamily] 按条目解析渲染，
 * 不经过本单例的「当前」状态（避免互相串扰）。
 *
 * 消费方：
 * - [LocalContentTypography]（Theme.kt）据此生成内容排版（编辑页）
 * - [com.corgimemo.app.ui.screens.inspiration.components.RichTextFormatToolbar] 据此探测字重档位
 * - [com.corgimemo.app.ui.screens.inspiration.components.FontPickerPanel] 据此回显当前选择
 */
object ContentFontManager {
    private val _currentEntry = MutableStateFlow(FontCatalog.systemDefault)
    val currentEntry: StateFlow<FontEntry> = _currentEntry.asStateFlow()

    /**
     * 当前英文/数字字体 id（拉丁回退层）；空串 = 跟随中文字体
     * （不叠加拉丁层，对应 `inspirations.latinFontId` 的默认值语义）。
     */
    private val _currentLatinId = MutableStateFlow("")
    val currentLatinId: StateFlow<String> = _currentLatinId.asStateFlow()

    /**
     * 装载某条灵感的内容字体（编辑页打开/切换灵感时调用）：
     * 中文字体 + 英文/数字字体一次设置，编辑排版与字重探测自动跟随。
     */
    fun setFonts(cjkId: String, latinId: String) {
        _currentEntry.value = FontCatalog.get(cjkId)
        _currentLatinId.value = latinId
    }

    /** 仅更新中文字体（字体选择面板中文组回调）。 */
    fun setCjkFont(fontId: String) {
        _currentEntry.value = FontCatalog.get(fontId)
    }

    /** 仅更新英文/数字字体（字体选择面板拉丁组回调；空串 = 跟随中文）。 */
    fun setLatinFont(latinId: String) {
        _currentLatinId.value = latinId
    }

    /** 复位为默认（中文 = 系统默认字体，拉丁 = 跟随中文）。新建灵感进入编辑页时调用。 */
    fun resetToDefault() {
        _currentEntry.value = FontCatalog.systemDefault
        _currentLatinId.value = ""
    }

    /**
     * 按条目解析内容字体族（**纯函数，无状态**）：列表卡片 / 时间线 / 详情页据此渲染，
     * 不读取本单例的「当前」状态。
     *
     * @param fontId 灵感记录的中文字体 id（空 = 系统默认字体）
     * @param latinFontId 灵感记录的英文/数字字体 id（空 = 跟随中文）
     */
    fun contentFontFamily(fontId: String, latinFontId: String): FontFamily =
        FontManager.combinedFamily(FontCatalog.get(fontId), FontCatalog.getLatin(latinFontId))

    /** 当前内容字体的组合字体族（中文 + 拉丁回退层；Theme.kt 生成 LocalContentTypography 用）。 */
    fun currentFamily(cjk: FontEntry, latinId: String): FontFamily =
        FontManager.combinedFamily(cjk, FontCatalog.getLatin(latinId))

    /** 当前内容字体标签（供 [FontWeightProbe] 缓存隔离；探测基准为中文字体） */
    val tag: String get() = _currentEntry.value.tag

    /** 当前内容字体加粗候选档位（B1/B2/B3） */
    val boldTiers: List<Int> get() = _currentEntry.value.boldTiers

    /** 当前内容字体按字重取 Typeface（供 [FontWeightProbe] 像素探测） */
    fun typefaceForWeight(context: Context, weight: Int): Typeface =
        _currentEntry.value.typefaceForWeight(context, weight)
}
