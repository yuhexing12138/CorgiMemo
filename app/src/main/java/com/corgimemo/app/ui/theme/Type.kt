package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.corgimemo.app.R

/**
 * 应用全局正文字体：思源黑体简体中文（Source Han Sans CN）。
 *
 * 来源：`free-font/docs/fonts/思源字体系列/思源黑体/`（Adobe 官方 Source Han Sans CN，
 * PostScript 名 `SourceHanSansCN-*`，字形数 30,888），授权 **SIL OFL 1.1，允许商用**。
 *
 * **为什么内置**：系统默认字体（FontFamily.Default）在多数设备上只含 400 / 700 / 900 三个字面，
 * 请求 Medium(500) 时会被字体匹配量化合并进 700，导致灵感编辑页 B1(500) 与 B2(700) 视觉完全相同。
 * 内置本字体后 400 / 500 / 700 / 900 四档各有独立字面，B1/B2/B3 三档才真正区分得开。
 *
 * **体积**：4 档共约 32.8 MB（Regular 8.0 / Medium 8.1 / Bold 8.3 / Heavy 8.4）。
 * 若需瘦身，可只保留 Regular + Medium + Bold（约 24.4 MB），但会丢失 900 档（B3）。
 *
 * **维护**：换字体只需改本 FontFamily 与下方 [APP_FONT_AVAILABLE_WEIGHTS]，
 * 其余（Typography、加粗档位）自动跟随。
 */
val SourceHanSansCN = FontFamily(
    Font(R.font.source_hans_sans_cn_regular, FontWeight.Normal),    // 400
    Font(R.font.source_hans_sans_cn_medium, FontWeight.Medium),     // 500
    Font(R.font.source_hans_sans_cn_bold, FontWeight.Bold),         // 700
    Font(R.font.source_hans_sans_cn_heavy, FontWeight.Black)        // 900
)

/** 内置字体的缓存标签（供 [FontWeightProbe] 隔离不同字体的探测结果） */
internal const val APP_FONT_TAG = "source-han-sans-cn"

/**
 * 按字重取内置思源黑体对应的 `android.graphics.Typeface`（供 [FontWeightProbe] 像素探测使用）。
 *
 * 思源黑体是**静态字重字体**（每档一个独立文件），不是可变字体，因此必须按字重挑选文件，
 * 不能像系统字体那样用 `Typeface.create(Typeface.DEFAULT, weight, false)` 让系统量化
 * ——那样得到的是系统字体的字面，与 App 实际渲染的思源黑体不符，探测结论会出错
 * （例如误判 500 无独立字面而把 B1 置灰）。
 *
 * 字重按「就近取档」映射：请求值落到哪个区间就用哪个文件（与 Compose FontFamily 的
 * 字重匹配策略一致）。
 *
 * @param context 用于加载字体资源
 * @param weight 请求的字重（100–1000）
 * @return 对应的 Typeface；资源缺失时回退系统默认字体
 */
internal fun appTypefaceForWeight(context: Context, weight: Int): Typeface {
    val resId = when {
        weight <= 450 -> R.font.source_hans_sans_cn_regular   // 400 及以下 → Regular
        weight <= 600 -> R.font.source_hans_sans_cn_medium    // 500 / 600 → Medium
        weight <= 800 -> R.font.source_hans_sans_cn_bold      // 700 / 800 → Bold
        else -> R.font.source_hans_sans_cn_heavy              // 900+ → Heavy
    }
    return runCatching { context.resources.getFont(resId) }.getOrDefault(Typeface.DEFAULT)
}

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SourceHanSansCN,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/**
 * 当前应用字体（[SourceHanSansCN]）**真实提供**的字重档位。
 *
 * 与旧版「系统默认字体」不同：内置字体是自己挑选的，每个档位都有对应的独立字体文件，
 * 因此这些字重**一定**有独立字面、不会被字体匹配量化合并（这正是内置字体要解决的问题）。
 *
 * **维护约定（重要）**：本列表必须与 [SourceHanSansCN] 里实际注册的 [Font] 一一对应。
 * 增删字体文件时同步改这里，否则加粗档位与实际字体不匹配——少写会浪费可用档位，
 * 多写会让该档渲染时找不到对应字面而回退。
 *
 * 若日后换回系统字体（`FontFamily.Default`），本列表应改为「假设候选」（如 500/700/900），
 * 并依赖 [FontWeightProbe] 运行时像素探测过滤掉无独立字面的档位——系统字体字面不可预知。
 */
internal val APP_FONT_AVAILABLE_WEIGHTS: List<Int> = listOf(
    FontWeight.Normal.weight,   // 400 Regular
    FontWeight.Medium.weight,   // 500 Medium
    FontWeight.Bold.weight,     // 700 Bold
    FontWeight.Black.weight     // 900 Heavy
)

/**
 * 灵感编辑页「加粗程度」档位（对应工具栏 B1 / B2 / B3）。
 *
 * 取 [APP_FONT_AVAILABLE_WEIGHTS] 中大于正文默认字重(400)的前三档 → 当前为 **500 / 700 / 900**，
 * 三档在思源黑体下**均有独立字面**，视觉分明，不再撞档（此前系统字体下 B1 与 B2 视觉相同）。
 *
 * 工具栏仍会用 [FontWeightProbe] 做一次运行时像素探测兜底，防止字体文件缺失、
 * 或日后换字体时出现无独立字面的档位；探测不通过的档位按钮置灰禁用。
 * **注意**：探测必须使用与应用渲染**相同的字体**，否则结论无效——
 * 详见 [FontWeightProbe] 的 `fontTag` / `typefaceOf` 参数说明。
 *
 * 700 走 markdown `**`；其余档走 `<span style="font-weight:N">` 保留数值
 * （库侧 `parseCssFontWeight` 已支持任意整数字重兜底，往返不丢）。
 */
internal val BOLD_WEIGHT_TIERS: List<Int> =
    APP_FONT_AVAILABLE_WEIGHTS.filter { it > FontWeight.Normal.weight }.take(3)