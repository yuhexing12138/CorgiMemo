package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.corgimemo.app.R

/**
 * 一款已内置、可商用（SIL OFL 1.1）中文字体的注册表条目。
 *
 * 每条目自带：显示名、授权信息（名称 + assets 内授权文档路径 + 版权声明）、
 * 由资源文件构建的 [FontFamily]、供 [FontWeightProbe] 隔离缓存的标签 [tag]、
 * 以及「字重 → 字体资源 id」映射 [resByWeight]（用于像素探测按字重挑 Typeface）。
 *
 * 采用**静态字重字体**（每档一个独立 .ttf/.otf），非可变字体。
 * 因此像素探测必须按字重挑文件，不能用 `Typeface.create(weight)` 量化
 * ——否则得到的是系统字体的字面，与 App 实际渲染字体不符，探测结论会出错。
 */
data class FontEntry(
    val id: String,
    val displayName: String,
    val licenseName: String,
    /** app `assets/` 内授权文档相对路径（相对 assets 根）；无则 null */
    val licenseAsset: String?,
    val copyright: String,
    val family: FontFamily,
    /** 供 [FontWeightProbe] 隔离不同字体探测结果的缓存标签 */
    val tag: String,
    /** 字重 → `R.font.*` 资源 id 映射（必须与 [family] 注册的 [Font] 一一对应） */
    val resByWeight: Map<Int, Int>,
    /**
     * 是否为「英文/数字字体」（拉丁回退层）。
     * true 时该条目不进入「正文字体」列表，而是作为选中正文字体的字形回退层：
     * 中文走正文字体，英文/数字走本字体（见 [FontManager] / Theme.kt 的 FontFamily 合成）。
     */
    val isLatin: Boolean = false
) {
    /** 本字体真实提供的字重档位（升序） */
    val availableWeights: List<Int> get() = resByWeight.keys.sorted()

    /**
     * 由 [resByWeight] 派生的 [Font] 列表（按字重建 [Font]）。
     * 用于与正文字体合成「拉丁在前、中文在后」的字形回退链
     * （[FontManager] / Theme.kt），避免重复维护两份字体清单。
     */
    val fonts: List<Font> get() = resByWeight.map { (w, resId) -> Font(resId, FontWeight(w)) }

    /**
     * 加粗程度候选档位（对应工具栏 B1 / B2 / B3）：
     * 取大于正文常规字重(400)的前三档。各字体均有独立字面，不再撞档。
     */
    val boldTiers: List<Int> get() = availableWeights.filter { it > FontWeight.Normal.weight }.take(3)

    /**
     * 按字重取本字体对应的 [Typeface]（供 [FontWeightProbe] 像素探测）。
     *
     * 就近取档：取 ≤ weight 的最大可用档；无更小档则取最小可用档
     * （与 Compose FontFamily 的字重匹配策略一致）。
     */
    fun typefaceForWeight(context: Context, weight: Int): Typeface {
        val chosen = availableWeights.filter { it <= weight }.maxOrNull() ?: availableWeights.first()
        return runCatching { context.resources.getFont(resByWeight.getValue(chosen)) }
            .getOrDefault(Typeface.DEFAULT)
    }
}

/**
 * 内置可商用中文字体注册表（当前均为 SIL OFL 1.1：允许商用、修改、再分发，
 * 唯一限制是不得单独售卖字体；随附授权文本见 [FontEntry.licenseAsset]）。
 *
 * 来源：本地 `free-font` 库（已核实 name 表版权与 OFL 标记，见仓库根 `licenses/`）。
 * 选定「精选中文多字重」范围（已排除单族近 1GB 的梦源系列与小米/阿里自定义授权族）。
 *
 * **新增字体流程**：拷贝资源到 `res/font/` → 在此登记一条 [FontEntry]，
 * 外观设置页（[com.corgimemo.app.ui.screens.settings.AppearanceScreen]）即自动列出，
 * 无需改动其他代码；[FontManager] 与 [buildTypography] 会自动跟随。
 */
object FontCatalog {

    // ========== 思源黑体（Source Han Sans CN，Adobe，简体黑体）==========
    private val SOURCE_HAN_SANS = FontEntry(
        id = "source_han_sans_cn",
        displayName = "思源黑体",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/SourceHanSansCN-OFL-1.1.txt",
        copyright = "Copyright © 2014 Adobe Systems Incorporated. All Rights Reserved.",
        family = FontFamily(
            Font(R.font.source_hans_sans_cn_regular, FontWeight.Normal),    // 400
            Font(R.font.source_hans_sans_cn_medium, FontWeight.Medium),     // 500
            Font(R.font.source_hans_sans_cn_bold, FontWeight.Bold),         // 700
            Font(R.font.source_hans_sans_cn_heavy, FontWeight.Black)        // 900
        ),
        tag = "source-han-sans-cn",
        resByWeight = mapOf(
            400 to R.font.source_hans_sans_cn_regular,
            500 to R.font.source_hans_sans_cn_medium,
            700 to R.font.source_hans_sans_cn_bold,
            900 to R.font.source_hans_sans_cn_heavy
        )
    )

    // ========== 思源宋体（Source Han Serif CN，Adobe，简体宋体）==========
    private val SOURCE_HAN_SERIF = FontEntry(
        id = "source_han_serif_cn",
        displayName = "思源宋体",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/SourceHanSerifCN-OFL-1.1.txt",
        copyright = "Copyright © 2017 Adobe Systems Incorporated (http://www.adobe.com/), with Reserved Font Name 'Source'.",
        family = FontFamily(
            Font(R.font.source_han_serif_cn_extralight, FontWeight.ExtraLight), // 200
            Font(R.font.source_han_serif_cn_light, FontWeight.Light),           // 300
            Font(R.font.source_han_serif_cn_regular, FontWeight.Normal),        // 400
            Font(R.font.source_han_serif_cn_medium, FontWeight.Medium),         // 500
            Font(R.font.source_han_serif_cn_semibold, FontWeight.SemiBold),     // 600
            Font(R.font.source_han_serif_cn_bold, FontWeight.Bold),             // 700
            Font(R.font.source_han_serif_cn_heavy, FontWeight.Black)            // 900
        ),
        tag = "source-han-serif-cn",
        resByWeight = mapOf(
            200 to R.font.source_han_serif_cn_extralight,
            300 to R.font.source_han_serif_cn_light,
            400 to R.font.source_han_serif_cn_regular,
            500 to R.font.source_han_serif_cn_medium,
            600 to R.font.source_han_serif_cn_semibold,
            700 to R.font.source_han_serif_cn_bold,
            900 to R.font.source_han_serif_cn_heavy
        )
    )

    // ========== 源音黑體（Genne Gothic，MoneMizuno，黑体）==========
    // 源族 Normal 与系统/Regular 重复，仅保留 6 档。
    private val GENNE_GOTHIC = FontEntry(
        id = "genne_gothic",
        displayName = "源音黑體",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/GenneGothic-OFL-1.1.txt",
        copyright = "Copyright © 2017, 2018 MoneMizuno. Copyright © 2014, 2015 Adobe Systems Incorporated. with Reserved Font Name 'Source'.",
        family = FontFamily(
            Font(R.font.genne_gothic_extralight, FontWeight.ExtraLight),  // 200
            Font(R.font.genne_gothic_light, FontWeight.Light),            // 300
            Font(R.font.genne_gothic_regular, FontWeight.Normal),         // 400
            Font(R.font.genne_gothic_medium, FontWeight.Medium),          // 500
            Font(R.font.genne_gothic_bold, FontWeight.Bold),              // 700
            Font(R.font.genne_gothic_heavy, FontWeight.Black)             // 900
        ),
        tag = "genne-gothic",
        resByWeight = mapOf(
            200 to R.font.genne_gothic_extralight,
            300 to R.font.genne_gothic_light,
            400 to R.font.genne_gothic_regular,
            500 to R.font.genne_gothic_medium,
            700 to R.font.genne_gothic_bold,
            900 to R.font.genne_gothic_heavy
        )
    )

    // ========== 獅尾半月字體 SC（Swei Half Moon SC，Chun yu Yao，黑体）==========
    private val SWEI_HALF_MOON = FontEntry(
        id = "swei_half_moon_sc",
        displayName = "獅尾半月字體 SC",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/SweiHalfMoonSC-OFL-1.1.txt",
        copyright = "Copyright (c) 2020, Chun yu Yao, with Reserved Font Name Untitled6.",
        family = FontFamily(
            Font(R.font.swei_half_moon_sc_thin, FontWeight.Thin),         // 100
            Font(R.font.swei_half_moon_sc_light, FontWeight.Light),       // 300
            Font(R.font.swei_half_moon_sc_demilight, FontWeight(350)),    // 350
            Font(R.font.swei_half_moon_sc_regular, FontWeight.Normal),    // 400
            Font(R.font.swei_half_moon_sc_medium, FontWeight.Medium),     // 500
            Font(R.font.swei_half_moon_sc_bold, FontWeight.Bold),         // 700
            Font(R.font.swei_half_moon_sc_black, FontWeight.Black)        // 900
        ),
        tag = "swei-half-moon-sc",
        resByWeight = mapOf(
            100 to R.font.swei_half_moon_sc_thin,
            300 to R.font.swei_half_moon_sc_light,
            350 to R.font.swei_half_moon_sc_demilight,
            400 to R.font.swei_half_moon_sc_regular,
            500 to R.font.swei_half_moon_sc_medium,
            700 to R.font.swei_half_moon_sc_bold,
            900 to R.font.swei_half_moon_sc_black
        )
    )

    // ========== 悠哉字体（Yozai，LXGW，黑体）==========
    private val YOZAI = FontEntry(
        id = "yozai",
        displayName = "悠哉字体",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/Yozai-OFL-1.1.txt",
        copyright = "Copyright (C) 2020 LXGW. Original Font Data Copyright (C) Y.OzVox.",
        family = FontFamily(
            Font(R.font.yozai_light, FontWeight.Light),      // 300
            Font(R.font.yozai_regular, FontWeight.Normal),    // 400
            Font(R.font.yozai_medium, FontWeight.Medium),     // 500
            Font(R.font.yozai_bold, FontWeight.Bold)          // 700
        ),
        tag = "yozai",
        resByWeight = mapOf(
            300 to R.font.yozai_light,
            400 to R.font.yozai_regular,
            500 to R.font.yozai_medium,
            700 to R.font.yozai_bold
        )
    )

    // ========== 初夏明朝體（Early Summer Mincho，明朝体/宋体）==========
    private val EARLY_SUMMER_MINCHO = FontEntry(
        id = "early_summer_mincho",
        displayName = "初夏明朝體",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/EarlySummerMincho-OFL-1.1.txt",
        copyright = "Copyright © 2020 Early Summer Foundry. (OFL 1.1；具体版权头以字体 name 表为准)",
        family = FontFamily(
            Font(R.font.early_summer_mincho_extralight, FontWeight.ExtraLight), // 200
            Font(R.font.early_summer_mincho_light, FontWeight.Light),           // 300
            Font(R.font.early_summer_mincho_regular, FontWeight.Normal),        // 400
            Font(R.font.early_summer_mincho_medium, FontWeight.Medium),         // 500
            Font(R.font.early_summer_mincho_semibold, FontWeight.SemiBold),     // 600
            Font(R.font.early_summer_mincho_bold, FontWeight.Bold),             // 700
            Font(R.font.early_summer_mincho_heavy, FontWeight.Black)            // 900
        ),
        tag = "early-summer-mincho",
        resByWeight = mapOf(
            200 to R.font.early_summer_mincho_extralight,
            300 to R.font.early_summer_mincho_light,
            400 to R.font.early_summer_mincho_regular,
            500 to R.font.early_summer_mincho_medium,
            600 to R.font.early_summer_mincho_semibold,
            700 to R.font.early_summer_mincho_bold,
            900 to R.font.early_summer_mincho_heavy
        )
    )

    // ========== Space Grotesk（拉丁·无衬线，Florian Karsten，英文/数字）==========
    // 现代几何无衬线，数字设计有特色；4 档：Light/Regular/Medium/Bold。
    // 仅含拉丁字形，作为正文字体的「英文/数字回退层」（isLatin=true），不进正文字体列表。
    private val SPACE_GROTESK = FontEntry(
        id = "space_grotesk",
        displayName = "Space Grotesk",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/SpaceGrotesk-OFL-1.1.txt",
        copyright = "Copyright 2020 The Space Grotesk Project Authors (https://github.com/floriankarsten/space-grotesk).",
        family = FontFamily(
            Font(R.font.space_grotesk_light, FontWeight.Light),       // 300
            Font(R.font.space_grotesk_regular, FontWeight.Normal),     // 400
            Font(R.font.space_grotesk_medium, FontWeight.Medium),      // 500
            Font(R.font.space_grotesk_bold, FontWeight.Bold)           // 700
        ),
        tag = "space-grotesk",
        resByWeight = mapOf(
            300 to R.font.space_grotesk_light,
            400 to R.font.space_grotesk_regular,
            500 to R.font.space_grotesk_medium,
            700 to R.font.space_grotesk_bold
        ),
        isLatin = true
    )

    // ========== Maple Mono（拉丁·等宽，Subframe7536，代码/数字对齐）==========
    // 现代等宽字体，8 档 + 斜体；取 5 档直立字（Light/Regular/Medium/SemiBold/Bold）。
    // 等宽特性使数字严格对齐，适合代码块、账目、时间线；作为正文字体英文/数字回退层。
    private val MAPLE_MONO = FontEntry(
        id = "maple_mono",
        displayName = "Maple Mono",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/MapleMono-OFL-1.1.txt",
        copyright = "Copyright 2022 The Maple Mono Project Authors (https://github.com/subframe7536/maple-font).",
        family = FontFamily(
            Font(R.font.maple_mono_light, FontWeight.Light),          // 300
            Font(R.font.maple_mono_regular, FontWeight.Normal),        // 400
            Font(R.font.maple_mono_medium, FontWeight.Medium),         // 500
            Font(R.font.maple_mono_semibold, FontWeight.SemiBold),     // 600
            Font(R.font.maple_mono_bold, FontWeight.Bold)              // 700
        ),
        tag = "maple-mono",
        resByWeight = mapOf(
            300 to R.font.maple_mono_light,
            400 to R.font.maple_mono_regular,
            500 to R.font.maple_mono_medium,
            600 to R.font.maple_mono_semibold,
            700 to R.font.maple_mono_bold
        ),
        isLatin = true
    )

    /** 全部可选正文字体（顺序即设置页展示顺序） */
    val entries: List<FontEntry> = listOf(
        SOURCE_HAN_SANS,
        SOURCE_HAN_SERIF,
        GENNE_GOTHIC,
        SWEI_HALF_MOON,
        YOZAI,
        EARLY_SUMMER_MINCHO
    )

    /** 全部可选「英文/数字字体」（拉丁回退层；空表示不覆盖，英文/数字走正文字体自带拉丁字形） */
    val latinEntries: List<FontEntry> = listOf(
        SPACE_GROTESK,
        MAPLE_MONO
    )

    /** 默认字体 id（思源黑体，内置 4 档、最通用） */
    val DEFAULT_ID: String = SOURCE_HAN_SANS.id

    /** 「英文/数字字体」默认 id（空串 = 系统默认，不叠加拉丁回退层） */
    const val DEFAULT_LATIN_ID: String = ""

    /** 按 id 取正文字体条目；未知 id 回退默认字体，避免空指针 */
    fun get(id: String): FontEntry = entries.firstOrNull { it.id == id } ?: entries.first()

    /** 按 id 取「英文/数字字体」条目；空串/未知 id 返回 null（表示不叠加拉丁回退层） */
    fun getLatin(id: String): FontEntry? = if (id.isBlank()) null else latinEntries.firstOrNull { it.id == id }
}
