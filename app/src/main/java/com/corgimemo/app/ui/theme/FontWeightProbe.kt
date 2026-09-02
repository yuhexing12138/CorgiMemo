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
 * 用法：在 UI 中用 remember 调用一次 [distinctWeights]，得到真正可用的字重集合，
 * 不在集合内的候选档位按钮应在工具栏中置灰禁用（见 RichTextFormatToolbar）。
 */
internal object FontWeightProbe {

    /**
     * 探测结果缓存：以「排序后的候选字重列表」为键，整进程内只真实绘制一次位图。
     * 工具栏每次重组（打字 / 选区变化 / 展开动画帧）都会调用 [distinctWeights]，
     * 命中缓存即可直接返回，避免重复离屏位图渲染开销。
     * 当前仅主线程调用（工具栏 remember 内），缓存读写已在 [distinctWeights] 上
     * 用 @Synchronized 保证 check-then-put 原子，若日后改为后台线程计算也不会并发写坏。
     */
    private val cache = LinkedHashMap<List<Int>, Set<Int>>()

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
     * 计算候选档位中「有独立字面」的集合（带整进程缓存，每个候选列表只真实绘制一次）。
     *
     * 比较基准采用递增级联：首个候选与基准常规字重比较；其后每个候选与
     * 「上一档已确认有独立字形的位图」比较。这样若某档被量化合并进更轻的可用字面，
     * 将正确判定为无独立字面而置灰。
     *
     * @param candidates 候选字重列表（如 [500, 700, 900]）
     * @param baseWeight 基准常规字重（默认 400），作为首个对比基准
     * @return 真正能渲染出独立字形的字重集合
     */
    @Synchronized
    fun distinctWeights(candidates: List<Int>, baseWeight: Int = FontWeight.Normal.weight): Set<Int> {
        val key = candidates.sorted()
        // 命中缓存直接返回，避免工具栏每次重组重复离屏位图渲染
        cache[key]?.let { return it }
        val result = computeDistinctWeights(candidates, baseWeight)
        cache[key] = result
        return result
    }

    /** 真实探测逻辑（见 [distinctWeights] 的缓存与级联说明） */
    private fun computeDistinctWeights(candidates: List<Int>, baseWeight: Int): Set<Int> {
        if (candidates.isEmpty()) return emptySet()
        // 基准位图：用基准常规字重绘制，作为首个对比基准
        var lastDistinctBitmap: Bitmap = renderBitmap(baseWeight)
        val result = mutableSetOf<Int>()
        for (weight in candidates.sorted()) {
            val bitmap = renderBitmap(weight)
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
     * 字体取系统默认无衬线（与 Compose FontFamily.Default 对应），
     * [Typeface.create] 的 weight 参数由系统量化到最近可用字面，正好用于探测。
     *
     * 兼容说明：带 weight 参数的 [Typeface.create] 重载需 API 28+；
     * 在更低版本（minSdk=26）上 Typeface 不支持任意字重，退化为常规/粗体二值映射——
     * 此时 500 等中间字重本就渲染为常规，探测结果与其一致（置灰）也属正确行为。
     */
    private fun renderBitmap(weight: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SAMPLE_TEXT_SIZE
            color = TEXT_COLOR
            typeface = typefaceForWeight(weight)
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
     * 按字重取对应的 Typeface。
     * API 28+ 用带 weight 的重载，由系统量化到最近可用字面（用于探测）；
     * 更低版本退化为常规/粗体二值，使中间字重探测结果保守（置灰）。
     */
    private fun typefaceForWeight(weight: Int): Typeface {
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
