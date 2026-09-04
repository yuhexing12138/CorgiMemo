package com.corgimemo.app.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.font.emptyCacheFontFamilyResolver
import androidx.core.graphics.TypefaceCompat

/**
 * FontFamilyResolver 硬约束策略（v2026-09-04，「一次最多只同时加载两种字体」的底层保障）。
 *
 * **为什么需要**：Compose 默认 resolver 的 typeface 缓存是**进程级全局单例**
 * （`GlobalTypefaceRequestCache` / `GlobalAsyncTypefaceCache`，compose-ui-text 1.11.2 源码注释
 * 明示 "All instances of FontFamily.Resolver created by createFontFamilyResolver share the same
 * typeface caches"）——**单纯换 resolver 实例不会丢缓存**；这些缓存按 (族,字重) 长期持有
 * 14~19MB 的 CJK Typeface，反复切字体必然累积（本项目 256MB 堆下已多次 OOM）。
 *
 * **硬约束方案**（两步，缺一不可）：
 * 1. [emptyCacheFontFamilyResolver]（@InternalTextApi，官方专为测试/基准做缓存隔离的构造器）
 *    创建**私有缓存**的 resolver：每次字体组合变化都换新实例 → 旧实例连同其缓存整体变成
 *    垃圾，可被 GC 回收；
 * 2. [TypefaceCompat.clearCache]（androidx.core **公开** API）清空 ResourcesCompat 的静态
 *    `LruCache(16)`——Compose 加载 ResourceFont 实际走 `ResourcesCompat.getFont`，不清这里
 *    旧字体仍被静态引用钉死，仅换 resolver 无效。
 *
 * **失败兜底**：若未来 Compose 移除了 [emptyCacheFontFamilyResolver]，[runCatching] 回退
 * 公开的 [createFontFamilyResolver]（共享全局缓存 → 硬约束退化为软约束，功能不受影响）。
 *
 * **已知取舍**：
 * - [emptyCacheFontFamilyResolver] 不带 AndroidFontResolveInterceptor——系统「粗体文字」
 *   无障碍开关的全局字重加成在隔离 resolver 下不生效（兜底路径不受影响）；
 * - 换 resolver 会让全部文本以新实例重新解析（整树重组 + 当前字体文件重读，单次几十毫秒），
 *   仅发生在字体切换/主题组合变化时，可接受；
 * - 列表页多条灵感各自渲染自己的内容字体属于**合法显示需求**，不计入「两种字体」约束
 *   （约束针对的是「切换痕迹」的累积，而非屏幕上正在显示的字体）。
 */
object FontResolverPolicy {

    /**
     * 创建一个「缓存隔离」的 FontFamilyResolver，并清空 androidx.core 的静态 Typeface 缓存。
     *
     * 用法（见 Theme.kt）：`remember(字体组合key) { createIsolatedResolver(context) }`——
     * key（中文字体 + 拉丁字体 + 内容字体组合）变化 → 旧实例（含其全部缓存）整体丢弃，
     * 新实例从零加载当前生效的两款字体，从而把常驻字体压回「中文 1 + 英文/数字 1」。
     *
     * @param context 任意 Context（内部转 applicationContext）
     */
    @OptIn(InternalTextApi::class)
    @SuppressLint("RestrictTo") // emptyCacheFontFamilyResolver 标注 @RestrictTo(LIBRARY_GROUP)，此处有意使用，仅抑制 lint
    fun createIsolatedResolver(context: Context): FontFamily.Resolver {
        // 1) 先清 androidx.core 静态缓存（ResourcesCompat.getFont 的实际存放处）：
        //    否则旧字体的 android.graphics.Typeface 仍被静态 LruCache(16) 强引用，无法 GC。
        runCatching { TypefaceCompat.clearCache() }
        // 2) 再建「空缓存」resolver：私有 TypefaceRequestCache + AsyncTypefaceCache，
        //    与进程级全局缓存完全隔离；失败则回退公开构造器（共享全局缓存，软约束）。
        return runCatching { emptyCacheFontFamilyResolver(context) }
            .getOrElse { createFontFamilyResolver(context) }
    }
}
