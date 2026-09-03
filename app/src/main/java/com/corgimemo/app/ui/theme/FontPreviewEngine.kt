package com.corgimemo.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import android.util.TypedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 字体预览引擎（OOM 根治，结构层）。
 *
 * 预览只需「白色字形蒙版」Bitmap，无需常驻字体 Typeface。本引擎用**有界** Typeface 池
 * （[MAX_TYPEFACE]=3）按字体 id 取 Typeface：把字体资源拷到 cacheDir 后用
 * `Typeface.Builder(String)` 即时构建（刻意绕过 `TypefaceCompat` 的 `LruCache(16)`，
 * 避免与编辑内容/全局正文的 FontFamilyResolver 共享缓存而双计），画进 Bitmap 后即弃。
 *
 * 关键结构约束：编辑内容/全局正文走 Compose `FontFamilyResolver`，按点选字体各常驻一款
 * （≤12 款 CJK ≈ 204MB），与面板预览**完全隔离**。面板只留极小位图 —— 故整体常驻 ≈
 * 内容 12 款(204MB) + 面板 0 ≈ 204MB < 256MB，彻底消除此前「面板 12 + 内容逐次」双计导致的 OOM。
 *
 * 流程：[prerenderAll] 顺序渲染全部预览位图（池容量有界、同一时刻至多 [MAX_TYPEFACE] 款 Typeface
 * 常驻），渲染完成后 [clearTypefaces] 清空池 → 面板**常态 0 常驻字体**（位图已在 [bitmapCache]）。
 * 单元格只从 [bitmapCache] 读取，绝不再创建 Typeface。
 */
object FontPreviewEngine {

    private const val MAX_TYPEFACE = 3
    private const val BITMAP_CACHE_CAP = 16

    /** 有界 Typeface 池：仅预览渲染瞬时借用，渲染后清空。 */
    private val typefacePool = object : LruCache<String, Typeface>(MAX_TYPEFACE) {
        override fun sizeOf(key: String, value: Typeface): Int = 1
    }

    /** 预览位图缓存（与主题无关、只渲染一次；约数十 KB/张）。容量 16 ≥ 12 CJK + 3 Latin，避免滚动重渲染。 */
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
            acquireTypeface(context, entry, entry.resByWeight.values.first())
        }
        val bmp = render(typeface, context, text, fontSize)
        bitmapCache.put(key, bmp)
        return bmp
    }

    /** 供 [FontWeightProbe] 像素探测按字重取 Typeface（同样走有界池，避免额外常驻）。 */
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
        return acquireTypeface(context, entry, resId)
    }

    /**
     * 从有界池取某字体「指定字重资源」的 Typeface：字体资源拷到 cacheDir 后用
     * `Typeface.Builder(String)` 即时构建（绕过 `TypefaceCompat`），画完即弃。
     * 池 key 含 resId（静态多字重：每档一个独立文件），容量 [MAX_TYPEFACE] 有界。
     */
    private fun acquireTypeface(context: Context, entry: FontEntry, resId: Int): Typeface {
        if (entry.isSystemDefault || resId == 0) return Typeface.DEFAULT
        val poolKey = "${entry.id}@$resId"
        typefacePool.get(poolKey)?.let { return it }
        val tf = runCatching {
            val file = File(context.cacheDir, "fp_preview_${entry.id}_$resId.ttf")
            if (!file.exists()) {
                context.resources.openRawResource(resId).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
            }
            Typeface.Builder(file.absolutePath).build() ?: Typeface.DEFAULT
        }.getOrDefault(Typeface.DEFAULT)
        typefacePool.put(poolKey, tf)
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
     * 顺序取 Typeface、画完即入位图缓存；渲染完成后清空 Typeface 池 → 面板常态 0 常驻字体。
     * 编辑页字体面板使用：中文「刻记」26sp / 拉丁「Corgi」19sp。
     */
    suspend fun prerenderAll(context: Context) = withContext(Dispatchers.IO) {
        FontCatalog.entries.forEach { e -> getBitmap(context, e, "刻记", 26) }
        FontCatalog.latinEntries.forEach { e -> getBitmap(context, e, "Corgi", 19) }
        clearTypefaces()
    }

    /**
     * 预渲染设置页字体行预览（每行以该字体自身名称 18sp 渲染），渲染后清空 Typeface 池。
     * 与 [prerenderAll] 的键不同（displayName vs 统一样例文字），按页面实际预览文案分别预热。
     */
    suspend fun prerenderBodyRows(context: Context) = withContext(Dispatchers.IO) {
        (FontCatalog.entries + FontCatalog.latinEntries).forEach { e ->
            getBitmap(context, e, e.displayName, 18)
        }
        clearTypefaces()
    }

    /** 清空 Typeface 池（位图已缓存，无需 Typeface 常驻）。 */
    fun clearTypefaces() = typefacePool.evictAll()

    /** 清空位图缓存（一般无需调用；主题切换/内存压力下可调用）。 */
    fun clearBitmaps() = bitmapCache.evictAll()

    // ========== 编辑内容「点选即预览」多行渲染（不常驻任何 Typeface） ==========

    /** 正文预览最大采样字符数（够判断观感即可，避免渲染过宽位图）。 */
    private const val MAX_PREVIEW_CHARS = 1200

    /** 正文预览位图最大高度（px，超出截断显示顶部；避免超大头图占内存）。 */
    private const val MAX_PREVIEW_HEIGHT_PX = 2048

    /** 面板点选时异步渲染：把一段正文按所选字体画成多行「白色蒙版」位图（IO/Default 线程）。 */
    suspend fun contentPreviewAsync(
        context: Context,
        cjkFontId: String,
        text: String,
        textSizeSp: Float,
        maxWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.Default) {
        contentPreviewBitmap(context, cjkFontId, text, textSizeSp, maxWidthPx)
    }

    /**
     * 把正文文本按指定字体渲染成多行白色蒙版位图（StaticLayout 换行）。
     * 字体经有界池取 Typeface（画完即弃、不常驻）；Compose 端 tint 到文字色。
     * 系统默认条目/[FontCatalog.get] 兜底 → [Typeface.DEFAULT]。失败一律返回 null。
     */
    fun contentPreviewBitmap(
        context: Context,
        cjkFontId: String,
        text: String,
        textSizeSp: Float,
        maxWidthPx: Int
    ): Bitmap? {
        if (maxWidthPx <= 0 || text.isBlank()) return null
        val sample = if (text.length > MAX_PREVIEW_CHARS) text.take(MAX_PREVIEW_CHARS) else text
        return runCatching {
            val entry = FontCatalog.get(cjkFontId)
            val typeface = if (entry.isSystemDefault || entry.resByWeight.isEmpty()) {
                Typeface.DEFAULT
            } else {
                acquireTypeface(context, entry, entry.resByWeight.values.first())
            }
            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.color = 0xFFFFFFFF.toInt() // 白色蒙版，compose 端 tint
                this.textSize = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, textSizeSp, context.resources.displayMetrics
                )
            }
            val layout = StaticLayout.Builder
                .obtain(sample, 0, sample.length, textPaint, maxWidthPx)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.3f)
                .setIncludePad(false)
                .build()
            val height = layout.height.coerceIn(1, MAX_PREVIEW_HEIGHT_PX)
            val bitmap = Bitmap.createBitmap(maxWidthPx, height, Bitmap.Config.ARGB_8888)
            // 超出 bitmap 高度的行由 Canvas clip 自动截断，只保留顶部预览
            layout.draw(Canvas(bitmap))
            bitmap
        }.getOrNull()
    }
}
