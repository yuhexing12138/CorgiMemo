package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import android.util.LruCache
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import android.os.Build
import android.graphics.fonts.Font as GFont
import android.graphics.fonts.FontFamily as GFontFamily
import android.graphics.fonts.FontStyle as GFontStyle
import com.corgimemo.app.R
import java.io.File

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
/**
 * 以「文件路径 + 内存映射」加载字体，取代默认的 ResourceFont 路径。
 *
 * **为什么必须绕开 ResourceFont**：Compose 解析 `Font(resId)` 走
 * `AndroidFontLoader` → `TypefaceCompat.createFromResources` → `Typeface.createFromInputStream`，
 * 会把整份字体文件（14~19MB）读进一个 Java 堆 `byte[]`。每个字体被 chrome resolver、
 * content resolver、框架字体缓存各留一份，常驻 3~4 份 → 连续换字体累积 ~500MB 致 OOM
 * （堆转储实证：byte[] 总量 ≈ 502MB，最大项均为 13~18MB，恰与字体文件尺寸吻合）。
 *
 * 本加载器把 resId 拷到 cacheDir 后用 `Typeface.Builder(path).build()`（native mmap，不占 Java 堆），
 * 从根上消除该 byte[]，与 [FontPreviewEngine] 预览路径一致。
 */
private val fileTypefaceCache = object : LruCache<Long, Typeface>(64) {
    override fun sizeOf(key: Long, value: Typeface): Int = 1
}

/**
 * cacheDir 字体副本的文件名版本后缀：取 App versionName，资源/版本更新后旧副本自动失效，避免 stale 字体。
 * 本项目 BuildConfig 不生成，故改从 PackageManager 读取 versionName（与 SettingsScreen 一致）。仅首次加载时读取并缓存。
 */
private var fontCacheVersion: String? = null
private fun fontCacheVersion(context: Context): String {
    fontCacheVersion?.let { return it }
    val v = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
    }.getOrDefault("0").replace(Regex("[^A-Za-z0-9._-]"), "_")
    fontCacheVersion = v
    return v
}

/**
 * 自定义 [AndroidFont.TypefaceLoader]：按 resId 把字体拷到 cacheDir（仅首次、幂等），
 * 再以 `Typeface.Builder(path)` 内存映射方式构建 Typeface，规避整文件读入 Java 堆 byte[]。
 * 同一 resId 的 Typeface 在本进程内复用 [fileTypefaceCache]，避免重复 mmap。
 */
private class FilePathTypefaceLoader(private val resId: Int) : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        fileTypefaceCache.get(resId.toLong())?.let { return it }
        val file = File(context.cacheDir, "ff_font_${resId}_v${fontCacheVersion(context)}.ttf")
        if (!file.exists()) {
            runCatching {
                context.resources.openRawResource(resId).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }.onFailure { return Typeface.DEFAULT }
        }
        val tf = Typeface.Builder(file.absolutePath).build() ?: Typeface.DEFAULT
        fileTypefaceCache.put(resId.toLong(), tf)
        return tf
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
        loadBlocking(context, font)
}

/**
 * [AndroidFont] 的私有子类：按 resId 走文件路径 mmap 加载，替代 ResourceFont 以根除 18MB byte[] 泄漏。
 * [AndroidFont] 为抽象类，此处承载 [Font] 接口要求的 [weight]/[style]；
 * 加载策略用 [FontLoadingStrategy.Blocking]（mmap 同步、首帧即就绪，适合本地小字体）。
 */
private class FilePathAndroidFont(
    resId: Int,
    weight: FontWeight,
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = FilePathTypefaceLoader(resId),
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
) {
    override val weight: FontWeight = weight
    override val style: FontStyle = FontStyle.Normal
}

/** 由 resId 构建走文件路径 mmap 加载的 [Font]，替代 ResourceFont，根除 18MB byte[] 泄漏。 */
private fun filePathFont(resId: Int, weight: FontWeight): Font =
    FilePathAndroidFont(resId, weight)

/**
 * 把 resId 字体按版本化文件名拷到 cacheDir（仅首次、幂等），返回缓存文件；失败返回 null。
 * 与 [FilePathTypefaceLoader] 共用同一命名规则与版本后缀，避免重复实现文件拷贝逻辑。
 */
private fun cacheFontFile(context: Context, resId: Int): File? {
    val file = File(context.cacheDir, "ff_font_${resId}_v${fontCacheVersion(context)}.ttf")
    if (!file.exists()) {
        runCatching {
            context.resources.openRawResource(resId).use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }.onFailure { return null }
    }
    return file
}

/**
 * 合成「拉丁优先 + 中文兜底」的单一 Typeface 加载器（按字重）。
 *
 * **根因**：Compose 的 [androidx.compose.ui.text.font.FontListFontFamilyTypefaceAdapter] 对每个
 * (字重,字型) 单元格只会从合成族里取**一个** Typeface（[FontMatcher] 同字重精确匹配返回两者时取列表首位），
 * 缺失字形交给 Android 的**系统**兜底链，而非族内另一个 App 字体。旧实现 `FontFamily(latin.fonts + cjk.fonts)`
 * 把拉丁放前列，导致同字重中文被拉丁字体覆盖后落到系统字体，表现为「选了中文但中文不变、选了英文英文变了」。
 *
 * **修复方式（字形级回退，而非族级合并）**：用 [android.graphics.Typeface.CustomFallbackBuilder]
 * （API 29+）把拉丁 [GFontFamily] 作为 base、中文 [GFontFamily] 作为自定义兜底，构建**单一按字形回退的
 * Typeface**——拉丁字形走拉丁字体、中文字形走中文字体，二者互不打架。
 * 注意：不能用 `Typeface.Builder.setFallback(String)`，该重载只接受**系统已注册的字族名**（如 "sans-serif"），
 * 传文件路径会找不到家族导致回退链失效（中文落系统字体，正是上一版 bug 的真正成因）。
 * 改用 `CustomFallbackBuilder.addCustomFallback` 才能把**任意资源字体**挂为回退层。
 * [cjkResId] 为 0（中文=系统默认、无内置文件）时只加载拉丁字体，中文回落设备系统字体（符合预期）；
 * [latinResId] 为 null 时只加载中文字体（等价于旧 `latin==null` 分支）。
 * 单档 400 书法体在标题 Bold(700) 请求下，[buildFontFamily] 改为按**文件真实（自然）字重**构建回退族
 * （不再把 400 档文件声明为 700 字重），单字体回退族天然覆盖所有字重，确保中文回退层不被字重不匹配跳过
 * （详见 [buildFontFamily] 注释）。
 * 运行时按 [Build.VERSION.SDK_INT] 降级：API 26–28 无公开自定义回退 API，退化到单一字体、优先中文。
 */
private class CombinedTypefaceLoader(
    private val latinResId: Int?,
    private val cjkResId: Int,
    private val weight: Int
) : AndroidFont.TypefaceLoader {
    override fun loadBlocking(context: Context, font: AndroidFont): Typeface? {
        // 合成键：拉丁 resId + 中文 resId + 字重三者组合，确保不同组合/字重互不串缓存
        val key = combinedKey(latinResId, cjkResId, weight)
        fileTypefaceCache.get(key)?.let { return it }
        val tf = buildCombinedTypeface(context, latinResId, cjkResId, weight) ?: Typeface.DEFAULT
        fileTypefaceCache.put(key, tf)
        return tf
    }

    override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface? =
        loadBlocking(context, font)
}

/** 合成缓存键：拉丁 resId、中文 resId、字重三者组合成 Long，避免不同组合/字重串缓存或 Int 溢出。 */
private fun combinedKey(latinResId: Int?, cjkResId: Int, weight: Int): Long {
    val l = (latinResId ?: 0).toLong()
    val c = (if (cjkResId > 0) cjkResId else 0).toLong()
    return (l * 1_000_003L + c) * 1_000_003L + weight.toLong()
}

/**
 * 构建「拉丁优先 + 中文兜底」的单一按字形回退 Typeface。
 * - API 29+：用 [android.graphics.Typeface.CustomFallbackBuilder] 串成「拉丁 base + 中文兜底」。
 * - API 26–28：无公开自定义回退 API，退化到单一字体（优先中文，保证中文渲染在所选中文字体内；
 *   拉丁字形会沿用该中文字体自带的拉丁字形，属可接受的降级）。
 */
private fun buildCombinedTypeface(
    context: Context,
    latinResId: Int?,
    cjkResId: Int,
    weight: Int
): Typeface? = runCatching {
    val style = GFontStyle(weight, GFontStyle.FONT_SLANT_UPRIGHT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val latinFamily = latinResId?.let { buildFontFamily(context, it) }
        val cjkFamily = if (cjkResId > 0) buildFontFamily(context, cjkResId) else null
        // 优先级：拉丁+中文合成 > 仅中文 > 仅拉丁；任一构建失败都不静默丢弃中文
        // （cjk 一旦构建成功必被使用，避免单档书法体在粗体标题下回退层被跳过而落到系统字体）。
        when {
            latinFamily != null && cjkFamily != null ->
                Typeface.CustomFallbackBuilder(latinFamily).addCustomFallback(cjkFamily).setStyle(style).build()
            cjkFamily != null ->
                Typeface.CustomFallbackBuilder(cjkFamily).setStyle(style).build()
            latinFamily != null ->
                Typeface.CustomFallbackBuilder(latinFamily).setStyle(style).build()
            // 兜底：中文资源有效却双双构建失败，直接用中文文件构建，绝不静默丢弃中文
            else -> if (cjkResId > 0) cacheFontFile(context, cjkResId)?.let { Typeface.createFromFile(it) } else null
        }
    } else {
        // 低于 API 29：无公开自定义回退链，退化为单一字体（优先中文）
        val file = when {
            cjkResId > 0 -> cacheFontFile(context, cjkResId)
            latinResId != null -> cacheFontFile(context, latinResId)
            else -> null
        }
        file?.let { Typeface.createFromFile(it) }
    }
}.getOrNull()

/**
 * 由 resId 构建单个 [GFontFamily]（缓存文件 → [GFont] → [GFontFamily]）。
 *
 * **关键修复（单档字体 + 粗体标题）**：旧实现用 `GFont.Builder(file).setWeight(请求字重)` 把 400 档文件
 * 声明为 700 字重挂进回退族。单档书法体在标题 Bold(700) 请求下，因「声明字重 ≠ 文件真实字重」
 * 导致 CustomFallbackBuilder 的中文回退层被跳过（正文 400 因精确匹配正常），且上一轮「再加一份真实字重」
 * 仍失败——因为 matcher 在 700 请求下优先命中那份「声明 700」的主字体而跳过真实字重副本。
 * 现改为**只按文件真实（自然）字重**构建：单字体回退族天然覆盖所有 (字重,字型) 请求，
 * 任意字重（含粗体 700）都能命中中文回退；多档字体由字体文件自身的多档保证精确匹配，不受影响。
 */
private fun buildFontFamily(context: Context, resId: Int): GFontFamily? = runCatching {
    val file = cacheFontFile(context, resId) ?: return null
    GFontFamily.Builder(GFont.Builder(file).build()).build()
}.getOrNull()

/** 合成「拉丁优先 + 中文兜底」的 [AndroidFont]：每个字重串成单一按字形回退的 Typeface。 */
private class CombinedAndroidFont(
    weight: FontWeight,
    latinResId: Int?,
    cjkResId: Int
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = CombinedTypefaceLoader(latinResId, cjkResId, weight.weight),
    variationSettings = FontVariation.Settings(weight, FontStyle.Normal)
) {
    override val weight: FontWeight = weight
    override val style: FontStyle = FontStyle.Normal
}

/**
 * 取目标字重对应资源（resId）：精确命中优先，否则取最近字重（先上方档后下方档，与 Compose [FontMatcher] 一致）；
 * 映射为空返回 null。
 *
 * **注意返回值语义**：返回的是 `map[key]` 的 **resId value**，不是字重 key 本身。
 * 单档书法体 `resByWeight = mapOf(400 to R.font.xxx)` 在粗体标题(700) 请求下无精确 key，
 * 此时 `closestRes` 必须返回 `map[400]`（真实 resId），而非把字重 key(400) 当 resId 传给 [buildFontFamily]
 * —— 否则会拿错误 resId 加载字体失败、中文回退层被丢弃。这是标题中文不生效的根因修复点。
 */
private fun closestRes(map: Map<Int, Int>, target: Int): Int? {
    if (map.isEmpty()) return null
    map[target]?.let { return it }
    val above = map.keys.filter { it > target }.minOrNull()
    val below = map.keys.filter { it < target }.maxOrNull()
    return above?.let { map[it] } ?: below?.let { map[it] } ?: map.values.first()
}

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
    val isLatin: Boolean = false,
    /**
     * 是否为「系统默认字体」占位条目：渲染用 [FontFamily.Default]（设备系统字体），
     * 不内置字体文件、不依赖 [resByWeight]；加粗档位与字重探测走系统字体
     * （[Typeface.DEFAULT]，见 [typefaceForWeight] / [boldTiers]）。
     */
    val isSystemDefault: Boolean = false
) {
    /** 本字体真实提供的字重档位（升序）。系统默认条目返回标准候选档位集合。 */
    val availableWeights: List<Int>
        get() = if (isSystemDefault) listOf(400, 500, 700, 900)
                else resByWeight.keys.sorted()

    /**
     * 由 [resByWeight] 派生的 [Font] 列表（按字重建 [AndroidFont]，走文件路径 mmap 加载，
     * 不把字体文件读入 Java 堆 byte[]），用于与正文字体合成「拉丁在前、中文在后」的字形回退链
     * （[FontManager] / Theme.kt）。系统默认条目无内置字体文件，返回空。
     */
    val fonts: List<Font>
        get() = if (isSystemDefault) emptyList()
                else resByWeight.map { (w, resId) -> filePathFont(resId, FontWeight(w)) }

    /**
     * 加粗程度候选档位（对应工具栏 B1 / B2 / B3）：取大于正文常规字重(400)的前三档。
     * 系统默认条目用标准候选 [500, 700, 900]，实际可用档位由 [FontWeightProbe]
     * 按系统字体像素探测决定（通常 500 被量化合并而置灰）。
     */
    val boldTiers: List<Int>
        get() = if (isSystemDefault) listOf(500, 700, 900)
                else availableWeights.filter { it > FontWeight.Normal.weight }.take(3)

    /**
     * 按字重取本字体对应的 [Typeface]（供 [FontWeightProbe] 像素探测）。
     *
     * 系统默认条目：直接取设备系统字体在指定字重的 Typeface（由系统量化到最近可用字面，
     * 正好用于探测），不读取 [resByWeight]。
     * 内置字体：就近取档（取 ≤ weight 的最大可用档；无更小档取最小可用档），
     * 与 Compose FontFamily 的字重匹配策略一致。
     */
    fun typefaceForWeight(context: Context, weight: Int): Typeface {
        // 统一走有界字体池（FontPreviewEngine），避免探测再额外常驻一套字体文件
        return FontPreviewEngine.typefaceForWeight(context, this, weight)
    }
}

/**
 * 内置可商用中文字体注册表（当前均为 SIL OFL 1.1：允许商用、修改、再分发，
 * 唯一限制是不得单独售卖字体；随附授权文本见 [FontEntry.licenseAsset]）。
 * 首个条目 [SYSTEM_DEFAULT] 为**非内置占位项**（渲染走设备系统字体，受 `isSystemDefault` 特殊处理），
 * 不随 APK 分发、无 OFL 授权，其余条目均为内置 OFL-1.1 字体。
 *
 * 来源：本地 `free-font` 库（已核实 name 表版权与 OFL 标记，见仓库根 `licenses/`）。
 * 选定「精选中文多字重」范围（已排除单族近 1GB 的梦源系列与小米/阿里自定义授权族）。
 *
 * **新增字体流程**：拷贝资源到 `res/font/` → 在此登记一条 [FontEntry]，
 * 外观设置页（[com.corgimemo.app.ui.screens.settings.AppearanceScreen]）即自动列出，
 * 无需改动其他代码；[FontManager] 与 [buildTypography] 会自动跟随。
 */
object FontCatalog {

    // ========== 系统默认字体（设备系统字体占位条目，非内置）==========
    // 渲染走 FontFamily.Default；加粗档位/字重探测走系统字体（见 FontEntry.isSystemDefault）。
    // 作为「正文字体」分组首项与全局默认（DEFAULT_ID），满足「默认即系统字体」诉求，
    // 同时保留其他内置字体为可选项。无内置字体文件、不随 APK 分发。
    private val SYSTEM_DEFAULT = FontEntry(
        id = "system_default",
        displayName = "系统默认字体",
        licenseName = "系统字体",
        licenseAsset = null,
        copyright = "使用设备系统默认字体（随设备/厂商定制，版权归设备厂商，App 不作内置分发）",
        family = FontFamily.Default,
        tag = "system-default",
        resByWeight = emptyMap(),
        isSystemDefault = true
    )

    // ========== 思源黑体（Source Han Sans CN，Adobe，简体黑体）==========
    private val SOURCE_HAN_SANS = FontEntry(
        id = "source_han_sans_cn",
        displayName = "思源黑体",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/SourceHanSansCN-OFL-1.1.txt",
        copyright = "Copyright © 2014 Adobe Systems Incorporated. All Rights Reserved.",
        family = FontFamily(
            filePathFont(R.font.source_hans_sans_cn_regular, FontWeight.Normal),    // 400
            filePathFont(R.font.source_hans_sans_cn_medium, FontWeight.Medium),     // 500
            filePathFont(R.font.source_hans_sans_cn_bold, FontWeight.Bold),         // 700
            filePathFont(R.font.source_hans_sans_cn_heavy, FontWeight.Black)        // 900
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
            filePathFont(R.font.source_han_serif_cn_extralight, FontWeight.ExtraLight), // 200
            filePathFont(R.font.source_han_serif_cn_light, FontWeight.Light),           // 300
            filePathFont(R.font.source_han_serif_cn_regular, FontWeight.Normal),        // 400
            filePathFont(R.font.source_han_serif_cn_medium, FontWeight.Medium),         // 500
            filePathFont(R.font.source_han_serif_cn_semibold, FontWeight.SemiBold),     // 600
            filePathFont(R.font.source_han_serif_cn_bold, FontWeight.Bold),             // 700
            filePathFont(R.font.source_han_serif_cn_heavy, FontWeight.Black)            // 900
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
            filePathFont(R.font.genne_gothic_extralight, FontWeight.ExtraLight),  // 200
            filePathFont(R.font.genne_gothic_light, FontWeight.Light),            // 300
            filePathFont(R.font.genne_gothic_regular, FontWeight.Normal),         // 400
            filePathFont(R.font.genne_gothic_medium, FontWeight.Medium),          // 500
            filePathFont(R.font.genne_gothic_bold, FontWeight.Bold),              // 700
            filePathFont(R.font.genne_gothic_heavy, FontWeight.Black)             // 900
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
            filePathFont(R.font.swei_half_moon_sc_thin, FontWeight.Thin),         // 100
            filePathFont(R.font.swei_half_moon_sc_light, FontWeight.Light),       // 300
            filePathFont(R.font.swei_half_moon_sc_demilight, FontWeight(350)),    // 350
            filePathFont(R.font.swei_half_moon_sc_regular, FontWeight.Normal),    // 400
            filePathFont(R.font.swei_half_moon_sc_medium, FontWeight.Medium),     // 500
            filePathFont(R.font.swei_half_moon_sc_bold, FontWeight.Bold),         // 700
            filePathFont(R.font.swei_half_moon_sc_black, FontWeight.Black)        // 900
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
            filePathFont(R.font.yozai_light, FontWeight.Light),      // 300
            filePathFont(R.font.yozai_regular, FontWeight.Normal),    // 400
            filePathFont(R.font.yozai_medium, FontWeight.Medium),     // 500
            filePathFont(R.font.yozai_bold, FontWeight.Bold)          // 700
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
            filePathFont(R.font.early_summer_mincho_extralight, FontWeight.ExtraLight), // 200
            filePathFont(R.font.early_summer_mincho_light, FontWeight.Light),           // 300
            filePathFont(R.font.early_summer_mincho_regular, FontWeight.Normal),        // 400
            filePathFont(R.font.early_summer_mincho_medium, FontWeight.Medium),         // 500
            filePathFont(R.font.early_summer_mincho_semibold, FontWeight.SemiBold),     // 600
            filePathFont(R.font.early_summer_mincho_bold, FontWeight.Bold),             // 700
            filePathFont(R.font.early_summer_mincho_heavy, FontWeight.Black)            // 900
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
            filePathFont(R.font.space_grotesk_light, FontWeight.Light),       // 300
            filePathFont(R.font.space_grotesk_regular, FontWeight.Normal),     // 400
            filePathFont(R.font.space_grotesk_medium, FontWeight.Medium),      // 500
            filePathFont(R.font.space_grotesk_bold, FontWeight.Bold)           // 700
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
            filePathFont(R.font.maple_mono_light, FontWeight.Light),          // 300
            filePathFont(R.font.maple_mono_regular, FontWeight.Normal),        // 400
            filePathFont(R.font.maple_mono_medium, FontWeight.Medium),         // 500
            filePathFont(R.font.maple_mono_semibold, FontWeight.SemiBold),     // 600
            filePathFont(R.font.maple_mono_bold, FontWeight.Bold)              // 700
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

    // ========== 马善政毛笔楷书（Ma Shan Zheng，Google Fonts，中文手写·毛笔楷书）==========
    // 单档 400 Regular；中文笔记手写体，作「正文字体」可选项（标题/摘录手写感）。
    private val MA_SHAN_ZHENG = FontEntry(
        id = "ma_shan_zheng",
        displayName = "马善政毛笔楷书",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/MaShanZheng-OFL-1.1.txt",
        copyright = "Copyright 2018 The Ma Shan Zheng Project Authors (https://github.com/googlefonts/ma-shan-zheng).",
        family = FontFamily(
            filePathFont(R.font.ma_shan_zheng_regular, FontWeight.Normal)        // 400
        ),
        tag = "ma-shan-zheng",
        resByWeight = mapOf(
            400 to R.font.ma_shan_zheng_regular
        )
    )

    // ========== 钟齐志莽行书（Zhi Mang Xing，Google Fonts，中文手写·行书）==========
    // 单档 400 Regular；潇洒行书，作「正文字体」可选项。
    private val ZHI_MANG_XING = FontEntry(
        id = "zhi_mang_xing",
        displayName = "钟齐志莽行书",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/ZhiMangXing-OFL-1.1.txt",
        copyright = "Copyright 2018 The Liu Jian Mao Cao Project Authors (https://github.com/googlefonts/liu-jian-mao-cao).",
        family = FontFamily(
            filePathFont(R.font.zhi_mang_xing_regular, FontWeight.Normal)        // 400
        ),
        tag = "zhi-mang-xing",
        resByWeight = mapOf(
            400 to R.font.zhi_mang_xing_regular
        )
    )

    // ========== 寒蝉·龙藏楷书（Long Cang，ChillType，中文手写·楷书）==========
    // 单档 400 Regular；清秀楷书，作「正文字体」可选项。
    private val CHILL_LONG_CANG_KAISHU = FontEntry(
        id = "chill_long_cang_kaishu",
        displayName = "寒蝉·龙藏楷书",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/LongCangKaiShu-OFL-1.1.txt",
        copyright = "Copyright (c) 2018-2022 ChillType.",
        family = FontFamily(
            filePathFont(R.font.chill_long_cang_kaishu_regular, FontWeight.Normal)   // 400
        ),
        tag = "chill-long-cang-kaishu",
        resByWeight = mapOf(
            400 to R.font.chill_long_cang_kaishu_regular
        )
    )

    // ========== Caveat（拉丁·手写，Pabla Stanley，英文手写体）==========
    // 取 2 档（Regular/Bold）；作为「英文/数字」拉丁回退层，中文手写正文里的英文/数字走手写感。
    private val CAVEAT = FontEntry(
        id = "caveat",
        displayName = "Caveat",
        licenseName = "SIL OFL 1.1",
        licenseAsset = "licenses/Caveat-OFL-1.1.txt",
        copyright = "Copyright 2014 The Caveat Project Authors (https://github.com/googlefonts/caveat).",
        family = FontFamily(
            filePathFont(R.font.caveat_regular, FontWeight.Normal),     // 400
            filePathFont(R.font.caveat_bold, FontWeight.Bold)           // 700
        ),
        tag = "caveat",
        resByWeight = mapOf(
            400 to R.font.caveat_regular,
            700 to R.font.caveat_bold
        ),
        isLatin = true
    )

    /** 全部可选正文字体（顺序即设置页展示顺序；首项为系统默认字体并作为全局默认） */
    val entries: List<FontEntry> = listOf(
        SYSTEM_DEFAULT,
        SOURCE_HAN_SANS,
        SOURCE_HAN_SERIF,
        GENNE_GOTHIC,
        SWEI_HALF_MOON,
        YOZAI,
        EARLY_SUMMER_MINCHO,
        MA_SHAN_ZHENG,
        ZHI_MANG_XING,
        CHILL_LONG_CANG_KAISHU
    )

    /** 全部可选「英文/数字字体」（拉丁回退层；空表示不覆盖，英文/数字走正文字体自带拉丁字形） */
    val latinEntries: List<FontEntry> = listOf(
        SPACE_GROTESK,
        MAPLE_MONO,
        CAVEAT
    )

    /** 默认字体 id（系统默认字体；不内置字体文件，渲染走设备系统字体） */
    val DEFAULT_ID: String = SYSTEM_DEFAULT.id

    /** 「英文/数字字体」默认 id（空串 = 系统默认，不叠加拉丁回退层） */
    const val DEFAULT_LATIN_ID: String = ""

    /** 系统默认字体占位条目（渲染走设备系统字体）；内容字体与工具栏字重探测的默认基准。 */
    val systemDefault: FontEntry get() = SYSTEM_DEFAULT

    /** 按 id 取正文字体条目；未知 id 回退默认字体，避免空指针 */
    fun get(id: String): FontEntry = entries.firstOrNull { it.id == id } ?: entries.first()

    /** 按 id 取「英文/数字字体」条目；空串/未知 id 返回 null（表示不叠加拉丁回退层） */
    fun getLatin(id: String): FontEntry? = if (id.isBlank()) null else latinEntries.firstOrNull { it.id == id }

    /**
     * 把「中文主体 + 拉丁回退层」合成**按字重的回退链字体列表**，供 [FontManager.combinedFamily] 构建合成族。
     *
     * 每个字重单元格取「拉丁字体(优先) + 中文字体(兜底)」串成单一 Typeface（见 [CombinedAndroidFont]），
     * 由 [CombinedTypefaceLoader] 用 `Typeface.CustomFallbackBuilder.addCustomFallback` 实现**按字形回退**：
     * 拉丁字形走拉丁字体、中文字形走中文字体，二者互不打架（修复「同字重只解析单一 Typeface、
     * 中文被拉丁覆盖后落到系统兜底」的中文不生效问题）。
     *
     * 中文为主体覆盖其全部字重，拉丁取其可覆盖的字重；中文为系统默认（无内置资源）时仅取拉丁字重、
     * 中文回落设备系统字体。返回空列表时（如中文=系统默认且未选拉丁）调用方应回退 [FontFamily.Default]。
     *
     * @param cjk 中文字体条目（可为系统默认占位条目）
     * @param latin 拉丁回退层条目；null 表示不叠加拉丁层（此时调用方应直接用 [FontEntry.family]）
     */
    fun combinedFamilyFonts(cjk: FontEntry, latin: FontEntry?): List<Font> {
        val cjkIsDefault = cjk.isSystemDefault
        val cjkByWeight = cjk.resByWeight
        val latinByWeight = latin?.resByWeight ?: emptyMap()
        if (cjkIsDefault && latinByWeight.isEmpty()) return emptyList()
        // 覆盖字重：中文系统默认时无内置资源，仅取拉丁字重；否则以中文字重为主、并上拉丁独有字重
        val weights = if (cjkIsDefault) latinByWeight.keys else (cjkByWeight.keys + latinByWeight.keys)
        return weights.sorted().mapNotNull { w ->
            val cjkRes = if (cjkIsDefault) null else closestRes(cjkByWeight, w)
            val latinRes = if (latin != null) closestRes(latinByWeight, w) else null
            when {
                cjkRes != null && latinRes != null -> CombinedAndroidFont(FontWeight(w), latinRes, cjkRes)
                cjkRes != null -> CombinedAndroidFont(FontWeight(w), null, cjkRes)
                latinRes != null -> CombinedAndroidFont(FontWeight(w), latinRes, 0)
                else -> null
            }
        }
    }
}
