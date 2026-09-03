package com.corgimemo.app.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontWeight

/**
 * 加粗档位「是否有独立字面」的运行时像素探测器。
 *
 * Compose 没有公开 API 能在运行时枚举某 FontFamily 实际支持哪些字面重
 * （系统默认字体往往只含 400 / 700 / 900 等少量字面，请求 500 时会被 CSS 字体匹配
 * 量化合并进 400 或 700，导致该档视觉与常规/加粗无异）。因此改用**像素探测**：
 * 对每个候选字重离屏把同一段文字绘制到 Bitmap，与「已确认有独立字形的最轻档位」
 * 逐像素比较，不同才算该档有独立字形。
 *
 * **⚠️ 探测所用字体必须与 App 实际渲染的字体一致**：
 * 项目已内置思源黑体（[SourceHanSansCN]），其 400/500/700/900 各有独立字体文件。
 * 若仍拿 `Typeface.DEFAULT` 探测，会得出「500 无独立字面」的**错误结论**——那是系统字体的
 * 情况，内置字体并非如此——导致本该可用的 B1 档被误置灰，内置字体白做。
 * 因此调用方**必须**通过 `typefaceOf` 传入与应用渲染相同的字体，并用 `fontTag` 隔离缓存。
 *
 * 用法：在 UI 中用 remember 调用一次 [distinctWeights]，得到真正可用的字重集合，
 * 不在集合内的候选档位按钮应在工具栏中置灰禁用（见 RichTextFormatToolbar）。
 */
internal object FontWeightProbe {

    /** 系统默认字体的缓存标签。传入自定义字体时必须另给 fontTag，否则会串读到本字体的探测结果。 */
    const val FONT_TAG_DEFAULT = "default"

    /**
     * 探测结果缓存：以「字体标签 + 排序后的候选字重列表」为键，整进程内只真实绘制一次位图。
     * 工具栏每次重组（打字 / 选区变化 / 展开动画帧）都会调用 [distinctWeights]，
     * 命中缓存即可直接返回，避免重复离屏位图渲染开销。
     * 当前仅主线程调用（工具栏 remember 内），缓存读写已在 [distinctWeights] 上
     * 用 @Synchronized 保证 check-then-put 原子，若日后改为后台线程计算也不会并发写坏。
     */
    private val cache = LinkedHashMap<String, Set<Int>>()

    /** 探测使用的样例文字（同时含中文与拉丁字符：中文覆盖用户真实输入内容、拉丁含横/竖/曲线笔画，放大对比差异） */
    private const val SAMPLE_TEXT = "字重Hg"

    /** 探测绘制字号（框架层单位为 px，放大更易分辨字形粗细差异） */
    private const val SAMPLE_TEXT_SIZE = 64f

    /** 离屏位图内边距，避免裁切字形 */
    private const val PADDING = 16

    /** 背景色（白）与文字色（黑），仅用于像素差异比较 */
    private const val BG_COLOR = -0x1 // 0xFFFFFFFF 的不透明白
    private const val TEXT_COLOR = -0x1000000 // 0xFF000000 的不透明黑

    /**
     * 计算候选档位中「有独立字面」的集合（带整进程缓存，每个「字体 + 候选列表」只真实绘制一次）。
     *
     * 比较基准采用递增级联：首个候选与基准常规字重比较；其后每个候选与
     * 「上一档已确认有独立字形的位图」比较。这样若某档被量化合并进更轻的可用字面，
     * 将正确判定为无独立字面而置灰。
     *
     * @param candidates 候选字重列表（如 [500, 700, 900]）
     * @param baseWeight 基准常规字重（默认 400），作为首个对比基准
     * @param fontTag 字体标识，用于隔离缓存；换字体必须换 tag，否则读到旧字体的探测结果
     * @param typefaceOf 字重 → Typeface 的映射，**必须返回应用实际渲染的字体**；
     *                   默认走系统默认字体（[defaultTypefaceForWeight]）
     * @return 真正能渲染出独立字形的字重集合
     */
    @Synchronized
    fun distinctWeights(
        candidates: List<Int>,
        baseWeight: Int = FontWeight.Normal.weight,
        fontTag: String = FONT_TAG_DEFAULT,
        typefaceOf: (Int) -> Typeface = ::defaultTypefaceForWeight
    ): Set<Int> {
        val key = "$fontTag|${candidates.sorted()}"
        // 命中缓存直接返回，避免工具栏每次重组重复离屏位图渲染
        cache[key]?.let { return it }
        val result = computeDistinctWeights(candidates, baseWeight, typefaceOf)
        cache[key] = result
        return result
    }

    /** 真实探测逻辑（见 [distinctWeights] 的缓存与级联说明） */
    private fun computeDistinctWeights(
        candidates: List<Int>,
        baseWeight: Int,
        typefaceOf: (Int) -> Typeface
    ): Set<Int> {
        if (candidates.isEmpty()) return emptySet()
        // 基准位图：用基准常规字重绘制，作为首个对比基准
        var lastDistinctBitmap: Bitmap = renderBitmap(baseWeight, typefaceOf)
        val result = mutableSetOf<Int>()
        for (weight in candidates.sorted()) {
            val bitmap = renderBitmap(weight, typefaceOf)
            if (!bitmapsEqual(bitmap, lastDistinctBitmap)) {
                // 与上一已确认档位像素不同 → 有独立字形
                result.add(weight)
                // 根因修复（v2026-09-02）：刚创建的 bitmap 将成为下一轮的对比基准，
                // 必须保留、绝不能在本轮回收；改为回收“上一档”位图（previous），
                // 保证 lastDistinctBitmap 始终指向存活位图，避免对回收位图调 getPixels 崩溃。
                val previous = lastDistinctBitmap
                lastDistinctBitmap = bitmap
                previous.recycle()
            } else {
                // 与上一档像素相同 → 无独立字面，本候选位图不再使用，直接回收
                bitmap.recycle()
            }
        }
        // 循环结束，lastDistinctBitmap 指向最后一个有独立字形的位图（或基准位图），回收之
        lastDistinctBitmap.recycle()
        return result
    }

    /**
     * 用指定字重离屏绘制样例文字到 Bitmap。
     *
     * @param weight 要探测的字重
     * @param typefaceOf 字重 → Typeface 映射；由调用方决定「用哪个字体探测」。
     *                   默认（[defaultTypefaceForWeight]）取系统默认无衬线，
     *                   其 [Typeface.create] 的 weight 参数由系统量化到最近可用字面，正好用于探测。
     */
    private fun renderBitmap(weight: Int, typefaceOf: (Int) -> Typeface): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SAMPLE_TEXT_SIZE
            color = TEXT_COLOR
            typeface = typefaceOf(weight)
            // 左对齐，保证不同字重在同一基线绘制，仅字形粗细不同
            textAlign = Paint.Align.LEFT
        }
        val fm = paint.fontMetrics
        val textWidth = paint.measureText(SAMPLE_TEXT)
        val width = (textWidth + PADDING * 2).toInt().coerceAtLeast(1)
        val height = ((fm.descent - fm.ascent) + PADDING * 2).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BG_COLOR)
        val baselineY = PADDING - fm.ascent
        canvas.drawText(SAMPLE_TEXT, PADDING.toFloat(), baselineY, paint)
        return bitmap
    }

    /** 逐像素比较两张位图是否完全一致（尺寸不同直接判不同） */
    private fun bitmapsEqual(a: Bitmap, b: Bitmap): Boolean {
        if (a.width != b.width || a.height != b.height) return false
        val w = a.width
        val h = a.height
        val pixelsA = IntArray(w * h)
        val pixelsB = IntArray(w * h)
        a.getPixels(pixelsA, 0, w, 0, 0, w, h)
        b.getPixels(pixelsB, 0, w, 0, 0, w, h)
        return pixelsA.contentEquals(pixelsB)
    }

    /**
     * 按字重取**系统默认字体**的 Typeface（探测的默认字体来源）。
     *
     * API 28+ 用带 weight 的重载，由系统量化到最近可用字面（用于探测）；
     * 更低版本退化为常规/粗体二值，使中间字重探测结果保守（置灰）。
     *
     * ⚠️ 仅适用于「App 使用系统字体」的场景。项目已内置思源黑体，
     * 调用方应传入对应内置字体文件的 Typeface，否则探测结论与真实渲染不符。
     */
    private fun defaultTypefaceForWeight(weight: Int): Typeface {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(Typeface.DEFAULT, weight, false)
        } else {
            Typeface.create(
                Typeface.DEFAULT,
                if (weight >= 700) Typeface.BOLD else Typeface.NORMAL
            )
        }
    }
}
