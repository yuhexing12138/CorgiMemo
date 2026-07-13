package com.corgimemo.app.backup.exporter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.components.PriorityColors
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片导出器
 * 使用 Canvas 直接绘制分享卡片，避免 ComposeView 需要 Window 的问题
 */
object ImageExporter {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 创建待办分享卡片图片
     *
     * @param context 上下文
     * @param todo 待办项
     * @param category 分类
     * @return Bitmap 对象
     */
    suspend fun createTodoShareCard(
        context: Context,
        todo: TodoItem,
        category: Category?
    ): Bitmap = withContext(Dispatchers.Default) {
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val height = (560 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(Color.WHITE)

        drawCardBackground(canvas, width, height)

        val padding = (16 * density).toInt()
        var currentY = padding

        currentY = drawHeader(canvas, width, padding, currentY, density)

        currentY += (16 * density).toInt()

        currentY = drawTodoContent(canvas, width, padding, currentY, todo, category, density)

        currentY += (16 * density).toInt()

        drawFooter(canvas, width, padding, currentY, density)

        bitmap
    }

    /**
     * 绘制卡片背景（带圆角和阴影效果）
     */
    private fun drawCardBackground(canvas: Canvas, width: Int, height: Int) {
        val cardPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }

        val shadowPaint = Paint().apply {
            color = Color.parseColor("#1A000000")
            isAntiAlias = true
        }

        val cornerRadius = (24f * canvas.width / 360)

        canvas.drawRoundRect(
            RectF(4f, 4f, width.toFloat() - 4f, height.toFloat() - 4f),
            cornerRadius,
            cornerRadius,
            shadowPaint
        )

        canvas.drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            cornerRadius,
            cornerRadius,
            cardPaint
        )
    }

    /**
     * 绘制顶部标题区域
     */
    private fun drawHeader(canvas: Canvas, width: Int, padding: Int, startY: Int, density: Float): Int {
        var y = startY

        val orangeGradient = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f,
                width.toFloat(), 0f,
                Color.parseColor("#FF9A5C"),
                Color.parseColor("#FFD4B8"),
                android.graphics.Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }

        val headerHeight = (80 * density).toInt()
        val cornerRadius = (16f * density)

        canvas.drawRoundRect(
            RectF(padding.toFloat(), y.toFloat(), (width - padding).toFloat(), (y + headerHeight).toFloat()),
            cornerRadius,
            cornerRadius,
            orangeGradient
        )

        val dogEmojiPaint = TextPaint().apply {
            textSize = (28 * density)
            isAntiAlias = true
        }
        canvas.drawText("🐕", padding + 20 * density, y + headerHeight / 2 + 10 * density, dogEmojiPaint)

        val titlePaint = TextPaint().apply {
            textSize = (16 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawText("CorgiMemo", padding + 56 * density, y + 32 * density, titlePaint)

        val subtitlePaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#FFF5E6")
            isAntiAlias = true
        }
        canvas.drawText("柯基备忘录", padding + 56 * density, y + 50 * density, subtitlePaint)

        val datePaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#4A2C1A")
            isAntiAlias = true
        }
        val todayText = displayDateFormat.format(Date())
        val dateWidth = datePaint.measureText(todayText)
        val dateBgWidth = dateWidth + (20 * density)

        canvas.drawRoundRect(
            RectF(
                width - padding - dateBgWidth,
                y + headerHeight - 28 * density,
                (width - padding).toFloat(),
                y + headerHeight - 8 * density
            ),
            (6 * density),
            (6 * density),
            Paint().apply {
                color = Color.parseColor("#FFF0E0")
                isAntiAlias = true
            }
        )
        canvas.drawText(
            todayText,
            width - padding - dateWidth - 10 * density,
            y + headerHeight - 14 * density,
            datePaint
        )

        y += headerHeight
        return y
    }

    /**
     * 绘制待办内容区域
     */
    private fun drawTodoContent(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        todo: TodoItem,
        category: Category?,
        density: Float
    ): Int {
        var y = startY

        val bgPaint = Paint().apply {
            shader = android.graphics.LinearGradient(
                0f, 0f,
                width.toFloat(), 0f,
                Color.parseColor("#FFF0E0"),
                Color.parseColor("#FFFAF5"),
                android.graphics.Shader.TileMode.CLAMP
            )
            isAntiAlias = true
        }

        val contentWidth = width - padding * 2
        val contentHeight = (200 * density).toInt()

        canvas.drawRoundRect(
            RectF(padding.toFloat(), y.toFloat(), (width - padding).toFloat(), (y + contentHeight).toFloat()),
            (16 * density),
            (16 * density),
            bgPaint
        )

        y += (16 * density).toInt()

        val categoryName = category?.name ?: "默认"
        val categoryBgPaint = Paint().apply {
            color = Color.WHITE
            alpha = 200
            isAntiAlias = true
        }

        val categoryTextPaint = TextPaint().apply {
            textSize = (12 * density)
            color = Color.parseColor("#4A2C1A")
            isAntiAlias = true
        }
        val categoryWidth = categoryTextPaint.measureText(categoryName) + (20 * density)

        canvas.drawRoundRect(
            RectF(
                padding + 16 * density,
                y.toFloat(),
                padding + 16 * density + categoryWidth,
                y + 24 * density
            ),
            (6 * density),
            (6 * density),
            categoryBgPaint
        )
        canvas.drawText(
            categoryName,
            padding + 26 * density,
            y + 18 * density,
            categoryTextPaint
        )

        val priorityBadge = getPriorityBadge(todo.priority)
        val priorityPaint = TextPaint().apply {
            textSize = (11 * density)
            color = priorityBadge.color
            isAntiAlias = true
        }
        val priorityWidth = priorityPaint.measureText(priorityBadge.text) + (16 * density)

        canvas.drawRoundRect(
            RectF(
                width - padding - priorityWidth,
                y.toFloat(),
                (width - padding).toFloat(),
                y + 22 * density
            ),
            (6 * density),
            (6 * density),
            Paint().apply {
                color = priorityBadge.bgColor
                isAntiAlias = true
            }
        )
        canvas.drawText(
            priorityBadge.text,
            width - padding - priorityWidth + 8 * density,
            y + 16 * density,
            priorityPaint
        )

        y += (32 * density).toInt()

        val titlePaint = TextPaint().apply {
            textSize = (22 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#2D1B0E")
            isAntiAlias = true
        }
        val titleWidth = titlePaint.measureText(todo.title)
        val titleX = if (titleWidth > contentWidth - (32 * density)) {
            padding + (16 * density).toInt()
        } else {
            (width - titleWidth) / 2
        }
        canvas.drawText(todo.title, titleX.toFloat(), y.toFloat(), titlePaint)

        y += (28 * density).toInt()

        if (!todo.content.isNullOrBlank()) {
            val contentPaint = TextPaint().apply {
                textSize = (14 * density)
                color = Color.parseColor("#5D4030")
                isAntiAlias = true
            }

            val maxContentWidth = contentWidth - (32 * density)
            val layout = StaticLayout.Builder.obtain(
                todo.content, 0, todo.content.length,
                contentPaint, maxContentWidth.toInt()
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing((4 * density), 1.0f)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(padding + 16 * density, y.toFloat())
            layout.draw(canvas)
            canvas.restore()

            y += (layout.height + 12 * density).toInt()
        }

        if (todo.startDate != null) {
            val startText = "🕐 开始: ${displayDateFormat.format(Date(todo.startDate))}"
            val startPaint = TextPaint().apply {
                textSize = (13 * density)
                color = Color.parseColor("#5D4030")
                isAntiAlias = true
            }
            canvas.drawText(startText, padding + 16 * density, y.toFloat(), startPaint)
            y += (20 * density).toInt()
        }

        if (todo.estimatedDurationMinutes != null) {
            val durationText = "⏱️ 预计: ${formatDuration(todo.estimatedDurationMinutes)}"
            val durationPaint = TextPaint().apply {
                textSize = (13 * density)
                color = Color.parseColor("#5D4030")
                isAntiAlias = true
            }
            canvas.drawText(durationText, padding + 16 * density, y.toFloat(), durationPaint)
        }

        y += contentHeight - (y - startY)
        return y
    }

    /**
     * 绘制底部水印区域
     */
    private fun drawFooter(canvas: Canvas, width: Int, padding: Int, startY: Int, density: Float): Int {
        val footerPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#A39078")
            isAntiAlias = true
        }
        val footerText = "🐕 来自 CorgiMemo - 让待办变得可爱"
        val footerWidth = footerPaint.measureText(footerText)
        val footerX = (width - footerWidth) / 2

        canvas.drawText(footerText, footerX, startY + (16 * density), footerPaint)

        return startY + (24 * density).toInt()
    }

    /**
     * 获取优先级徽章配置
     */
    private fun getPriorityBadge(priority: Int): BadgeConfig {
        return when (priority) {
            2 -> BadgeConfig("高", PriorityColors.High.toArgb(), Color.parseColor("#FFE4E6"))
            1 -> BadgeConfig("中", PriorityColors.Medium.toArgb(), Color.parseColor("#FFF3E0"))
            else -> BadgeConfig("低", PriorityColors.Low.toArgb(), Color.parseColor("#ECFDF5"))
        }
    }

    data class BadgeConfig(val text: String, val color: Int, val bgColor: Int)

    /**
     * 保存 Bitmap 到缓存目录
     */
    suspend fun saveBitmapToCache(
        context: Context,
        bitmap: Bitmap,
        fileName: String? = null
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "share_images")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val actualFileName = fileName ?: "corgimemo_share_${dateFormat.format(Date())}.png"
        val file = File(cacheDir, actualFileName)

        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
        }

        file
    }

    /**
     * 清理旧的分享图片
     */
    fun cleanOldShareImages(context: Context, keepHours: Int = 24) {
        val cacheDir = File(context.cacheDir, "share_images")
        if (!cacheDir.exists()) return

        val cutoffTime = System.currentTimeMillis() - (keepHours * 60 * 60 * 1000)

        cacheDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }

    /**
     * 格式化预计完成时长为显示文本
     *
     * @param minutes 预计完成时长（分钟）
     * @return 格式化后的时长文本，如 "1小时30分钟"、"2小时"、"45分钟"
     */
    private fun formatDuration(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}小时${mins}分钟"
            hours > 0 -> "${hours}小时"
            else -> "${mins}分钟"
        }
    }

    /**
     * 拼图数量超过上限异常
     *
     * @param count 实际拼图数量
     */
    class TooManyBitmapsException(val count: Int) :
        IllegalArgumentException("拼图数量 $count 超过上限 10，请选择一条条分享")

    /**
     * 拼图策略枚举
     */
    private enum class MergeStrategy {
        GRID_2_COL,  // 2-6 张：2 列网格
        VERTICAL     // 7-10 张：垂直拼接
    }

    /**
     * 合并多张待办分享图片为一张
     *
     * 智能拼接策略：
     * - 2-6 张：2 列网格布局
     * - 7-10 张：垂直拼接
     * - >10 张：抛出 [TooManyBitmapsException]（调用方需禁用合并按钮）
     * - 0 张：抛出 [IllegalArgumentException]
     *
     * 所有图片宽度按比例缩放至 outputWidthPx，高度按比例计算。
     * 拼接背景为纯白，网格布局子图间隔 spacingPx，垂直布局直接堆叠（无间隔）。
     *
     * @param bitmaps 待合并的 Bitmap 列表（已按顺序排列）
     * @param outputWidthPx 每列目标宽度（像素），默认 720
     * @param spacingPx 网格子图间隔像素，默认 24（垂直布局忽略此参数）
     * @return 合并后的 Bitmap
     * @throws TooManyBitmapsException 当 count > 10
     * @throws IllegalArgumentException 当 count == 0
     */
    suspend fun mergeBitmaps(
        bitmaps: List<Bitmap>,
        outputWidthPx: Int = 720,
        spacingPx: Int = 24
    ): Bitmap = withContext(Dispatchers.Default) {
        require(bitmaps.isNotEmpty()) { "拼图列表不能为空" }
        if (bitmaps.size > 10) {
            throw TooManyBitmapsException(bitmaps.size)
        }

        val strategy = if (bitmaps.size <= 6) MergeStrategy.GRID_2_COL else MergeStrategy.VERTICAL

        // 每列宽度固定为 outputWidthPx，等比缩放源图
        val columnWidth = outputWidthPx
        val rowScaledBitmaps = bitmaps.map { scaleBitmapToWidth(it, columnWidth) }

        val columnsCount = if (strategy == MergeStrategy.GRID_2_COL) 2 else 1
        val rowsCount = (rowScaledBitmaps.size + columnsCount - 1) / columnsCount

        // 垂直布局无间距，网格布局使用 spacingPx 间距
        val effectiveSpacing = if (strategy == MergeStrategy.VERTICAL) 0 else spacingPx

        val rowHeights = IntArray(rowsCount) { rowIdx ->
            val start = rowIdx * columnsCount
            val end = minOf(start + columnsCount, rowScaledBitmaps.size)
            (start until end).maxOf { rowScaledBitmaps[it].height }
        }

        val totalHeight = rowHeights.sum() + (rowsCount - 1) * effectiveSpacing
        val totalWidth = columnsCount * columnWidth + (columnsCount - 1) * effectiveSpacing

        val output = Bitmap.createBitmap(totalWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)

        var currentY = 0
        for (rowIdx in 0 until rowsCount) {
            val start = rowIdx * columnsCount
            val end = minOf(start + columnsCount, rowScaledBitmaps.size)
            val rowHeight = rowHeights[rowIdx]

            for (colIdx in 0 until (end - start)) {
                val bmp = rowScaledBitmaps[start + colIdx]
                val x = colIdx * (columnWidth + effectiveSpacing)
                val y = currentY + (rowHeight - bmp.height) / 2
                canvas.drawBitmap(bmp, x.toFloat(), y.toFloat(), null)
            }
            currentY += rowHeight + effectiveSpacing
        }

        output
    }

    /**
     * 按宽度等比缩放 Bitmap
     *
     * @param source 源 Bitmap
     * @param targetWidth 目标宽度
     * @return 缩放后的 Bitmap
     */
    private fun scaleBitmapToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        if (source.width == targetWidth) return source
        val ratio = targetWidth.toFloat() / source.width
        val targetHeight = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
