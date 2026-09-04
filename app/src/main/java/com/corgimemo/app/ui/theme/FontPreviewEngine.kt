package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.LruCache
import android.util.TypedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字体预览引擎（OOM 根治，结构层）。
 *
 * 预览只需「白色字形蒙版」Bitmap，无需常驻字体 Typeface。本引擎用**有界** Typeface 池
 * （预览池 [MAX_PREVIEW_TYPEFACE]=2）按字体 id 取 Typeface：把字体资源拷到 cacheDir 后用
 * `Typeface.Builder(String)` 即时构建（刻意绕过 `TypefaceCompat` 的 `LruCache(16)`，
 * 避免与编辑内容/全局正文的 FontFamilyResolver 共享缓存而双计），画进 Bitmap 后即弃。
 *
 * **「一次最多同时加载两种字体」硬约束（v2026-09-04 分离式预览）**：
 * 字体不再「点选即预览」，而是「点选只更新 pending → 点确认才应用」。故运行时常驻字体只有
 * 「应用中的中文字体 1 款 + 英文/数字字体 1 款」两种，预览一律走本引擎位图、不常驻字体。
 * 池按用途拆分为二，互不挤占：
 * - [previewTypefacePool]（容量 2）：预览位图渲染专用，中文字体 1 + 英文/数字字体 1；
 *   预渲染结束/提交字体后由 [clearPreviewTypefaces] 清空 → 常态 0 常驻。
 * - [probeTypefacePool]（容量 3）：[FontWeightProbe] 字重探测专用，三档 B1/B2/B3 同属
 *   **同一款**当前字体（不同字重文件），与「两种字体」约束不冲突。
 *
 * 关键结构约束：编辑内容/全局正文走 Compose `FontFamilyResolver`，只常驻**已应用**的那 1~2 款，
 * 与面板预览**完全隔离**（预览零常驻）。
 *
 * 流程：[prerenderAll] 顺序渲染全部预览位图（同一时刻至多 2 款预览字体常驻），渲染完成后
 * [clearPreviewTypefaces] 清空池 → 面板**常态 0 常驻字体**（位图已在 [bitmapCache]）。
 * 单元格只从 [bitmapCache] 读取，绝不再创建 Typeface。
 */
object FontPreviewEngine {

    /**
     * 预览池容量：同一时刻最多 2 款预览字体（中文字体 1 + 英文/数字字体 1）。
     * 与「一次最多只同时加载两种字体」的约束对齐。
     */
    private const val MAX_PREVIEW_TYPEFACE = 2

    /** 字重探测池容量：同一时刻最多 3 档字重文件（B1/B2/B3），均属同一款当前字体。 */
    private const val MAX_PROBE_TYPEFACE = 3

    /**
     * 预览位图缓存容量（与主题无关、只渲染一次；约数十 KB/张）。
     * 32 ≥ 两个页面各自的 13 张（10 CJK + 3 Latin），避免滚动/来回切页时因缓存淘汰
     * 而重新借用字体重渲染（重渲染虽不常驻字体，但白白耗 IO 与 CPU）。
     */
    private const val BITMAP_CACHE_CAP = 32

    /** 构造按条目计数（每条 Typeface 计 1）的有界池。 */
    private fun boundedPool(max: Int) = object : LruCache<String, Typeface>(max) {
        override fun sizeOf(key: String, value: Typeface): Int = 1
    }

    /** 预览渲染专用有界池（容量 2：中文 1 + 拉丁 1），渲染后由 [clearPreviewTypefaces] 清空。 */
    private val previewTypefacePool = boundedPool(MAX_PREVIEW_TYPEFACE)

    /** 字重探测专用有界池（容量 3：同一款字体的 B1/B2/B3 三档字重文件）。 */
    private val probeTypefacePool = boundedPool(MAX_PROBE_TYPEFACE)

    /** 预览位图缓存（与主题无关、只渲染一次；约数十 KB/张）。 */
    private val bitmapCache = object : LruCache<String, Bitmap>(BITMAP_CACHE_CAP) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    /** 预览位图缓存键（字体 id | 预览文字 | 字号 sp），与单元格一致。 */
    fun bitmapKey(id: String, text: String, fontSize: Int): String = "$id|$text|$fontSize"

    /**
     * 取某字体的预览位图（白色字形蒙版）。优先命中 [bitmapCache]，未命中才取 Typeface 渲染。
     * 系统默认条目（[FontEntry.isSystemDefault]，无内置文件）用 [Typeface.DEFAULT] 渲染；
     * 内置字体取该字体「首项字重」文件，仅首次（cacheDir 无副本）拷贝一次，后续复用临时文件。
     */
    fun getBitmap(context: Context, entry: FontEntry, text: String, fontSize: Int): Bitmap {
        val key = bitmapKey(entry.id, text, fontSize)
        bitmapCache.get(key)?.let { return it }
        val typeface = if (entry.isSystemDefault || entry.resByWeight.isEmpty()) {
            Typeface.DEFAULT
        } else {
            acquireTypeface(previewTypefacePool, context, entry, entry.resByWeight.values.first())
        }
        val bmp = render(typeface, context, text, fontSize)
        bitmapCache.put(key, bmp)
        return bmp
    }

    /**
     * 供 [FontWeightProbe] 像素探测按字重取 Typeface（走探测专用有界池，避免与预览池互相挤占）。
     * 探测对象恒为**当前已应用的那款字体**的 B1/B2/B3 三档，不引入新的字体种类。
     */
    fun typefaceForWeight(context: Context, entry: FontEntry, weight: Int): Typeface {
        if (entry.isSystemDefault) {
            return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                Typeface.create(Typeface.DEFAULT, weight, false)
            } else {
                Typeface.create(Typeface.DEFAULT, if (weight >= 700) Typeface.BOLD else Typeface.NORMAL)
            }
        }
        val chosen = entry.availableWeights.filter { it <= weight }.maxOrNull() ?: entry.availableWeights.first()
        val resId = entry.resByWeight[chosen] ?: return Typeface.DEFAULT
        return acquireTypeface(probeTypefacePool, context, entry, resId)
    }

    /**
     * 从指定有界池取某字体「指定字重资源」的 Typeface：字体资源拷到 cacheDir 后用
     * `Typeface.Builder(String)` 即时构建（绕过 `TypefaceCompat`），画完即弃。
     * 池 key 含 resId（静态多字重：每档一个独立文件），池容量有界。
     *
     * @param pool 目标池（预览走 [previewTypefacePool]，字重探测走 [probeTypefacePool]）
     */
    private fun acquireTypeface(
        pool: LruCache<String, Typeface>,
        context: Context,
        entry: FontEntry,
        resId: Int
    ): Typeface {
        if (entry.isSystemDefault || resId == 0) return Typeface.DEFAULT
        val poolKey = "${entry.id}@$resId"
        pool.get(poolKey)?.let { return it }
        val tf = runCatching {
            val file = File(context.cacheDir, "fp_preview_${entry.id}_$resId.ttf")
            if (!file.exists()) {
                context.resources.openRawResource(resId).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
            Typeface.Builder(file.absolutePath).build() ?: Typeface.DEFAULT
        }.getOrDefault(Typeface.DEFAULT)
        pool.put(poolKey, tf)
        return tf
    }

    private fun render(typeface: Typeface, context: Context, text: String, fontSize: Int): Bitmap {
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, fontSize.toFloat(), context.resources.displayMetrics
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.color = 0xFFFFFFFF.toInt() // 白色蒙版，compose 端再 tint 到目标文字色
            textSize = textSizePx
        }
        val width = (paint.measureText(text) + 4f).toInt().coerceAtLeast(1)
        val fm = paint.fontMetrics
        val height = (fm.descent - fm.ascent + 4f).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 2f, -fm.ascent + 2f, paint)
        return bitmap
    }

    /**
     * 预渲染全部预览位图（IO 线程：拷贝字体 + 构建 Typeface + 绘制）。
     * 顺序取 Typeface、画完即入位图缓存；渲染完成后清空预览池 → 面板常态 0 常驻字体。
     * 编辑页字体面板使用：中文「刻记」26sp / 拉丁「Corgi」19sp。
     */
    suspend fun prerenderAll(context: Context) = withContext(Dispatchers.IO) {
        FontCatalog.entries.forEach { e -> getBitmap(context, e, "刻记", 26) }
        FontCatalog.latinEntries.forEach { e -> getBitmap(context, e, "Corgi", 19) }
        clearPreviewTypefaces()
    }

    /**
     * 预渲染设置页字体行预览（每行以该字体自身名称 18sp 渲染），渲染后清空预览池。
     * 与 [prerenderAll] 的键不同（displayName vs 统一样例文字），按页面实际预览文案分别预热。
     */
    suspend fun prerenderBodyRows(context: Context) = withContext(Dispatchers.IO) {
        (FontCatalog.entries + FontCatalog.latinEntries).forEach { e ->
            getBitmap(context, e, e.displayName, 18)
        }
        clearPreviewTypefaces()
    }

    /**
     * 清空**预览** Typeface 池（位图已缓存，无需 Typeface 常驻）。
     *
     * 「确认应用字体」时必须调用（编辑页「完成」/ 设置页「确定」）：确保预览字体不与
     * 应用中的字体共存，把常驻字体压回「中文 1 + 拉丁 1」两种。
     */
    fun clearPreviewTypefaces() = previewTypefacePool.evictAll()

    /** 清空字重探测池（当前字体切换、探测结果已缓存后可调用）。 */
    fun clearProbeTypefaces() = probeTypefacePool.evictAll()

    /** 清空全部 Typeface 池（预览 + 探测）。 */
    fun clearTypefaces() {
        clearPreviewTypefaces()
        clearProbeTypefaces()
    }

    /** 清空位图缓存（一般无需调用；主题切换/内存压力下可调用）。 */
    fun clearBitmaps() = bitmapCache.evictAll()
}
