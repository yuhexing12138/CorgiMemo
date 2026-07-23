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
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.SubTask
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
 *
 * 使用 Canvas 直接绘制分享卡片，还原 App 内 TodoListItem 样式。
 * v2 重设计：品牌条 + App 卡片复刻 + 水印，高度自适应内容。
 */
object ImageExporter {

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

    /**
     * 创建待办分享卡片图片（v2 重设计）
     *
     * 布局结构（从上到下）：
     * 1. 品牌顶部条：🐕 logo + "刻记+" + "柯基备忘录" + 日期胶囊
     * 2. 主待办卡片：还原 App TodoListItem 样式
     *    - 左侧 4dp 优先级竖条 + 1.5dp 边框
     *    - 圆形复选框 + 标题 + 进度
     *    - 元数据行（分类 + 附件数 + 关联数）
     *    - 时间行（提醒📅 + 截止🏁）
     *    - 子待办列表（复选框 + 标题）
     *    - 图片缩略图行
     * 3. 品牌水印：分割线 + "🐕来自 CorgiMemo · 让待办变得可爱"
     *
     * @param context 上下文
     * @param todo 主待办项
     * @param category 分类
     * @param subTodos 子待办列表（默认空）
     * @param imageCount 图片附件数量（默认0）
     * @param relationCount 关联数量（默认0）
     * @return Bitmap 对象
     */
    suspend fun createTodoShareCard(
        context: Context,
        todo: TodoItem,
        category: Category?,
        subTodos: List<SubTask> = emptyList(),
        imagePaths: List<String> = emptyList(),
        relationCount: Int = 0
    ): Bitmap = withContext(Dispatchers.Default) {
        val density = context.resources.displayMetrics.density
        val width = (360 * density).toInt()
        val padding = (16 * density).toInt()

        // ===== 第一遍：测量内容高度（不绘制） =====
        val measureCanvas = Canvas()
        val contentHeight = measureCardContent(measureCanvas, width, padding, todo, category, subTodos, imagePaths.size, relationCount, density)
        val headerHeight = (44 * density).toInt()    // 品牌条高度
        val footerHeight = (40 * density).toInt()    // 水印区高度
        val cardSpacing = (12 * density).toInt()     // 品牌条/卡片/水印间距
        val totalHeight = headerHeight + cardSpacing + contentHeight + cardSpacing + footerHeight + padding * 2

        // ===== 第二遍：绘制到 Bitmap =====
        val bitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#F8F6F3")) // 页面背景暖白

        var currentY = padding

        // 1. 品牌顶部条
        currentY = drawBrandHeader(canvas, width, padding, currentY, density)

        currentY += cardSpacing

        // 2. 主待办卡片
        currentY = drawTodoCard(canvas, width, padding, currentY, todo, category, subTodos, imagePaths, relationCount, density)

        currentY += cardSpacing

        // 3. 品牌水印
        drawBrandFooter(canvas, width, padding, currentY, density)

        bitmap
    }

    /**
     * 测量卡片内容高度（第一遍扫描）
     */
    private fun measureCardContent(
        canvas: Canvas,
        width: Int,
        padding: Int,
        todo: TodoItem,
        category: Category?,
        subTodos: List<SubTask>,
        imageCount: Int,
        relationCount: Int,
        density: Float
    ): Int {
        val contentWidth = width - padding * 2
        val cardPadding = (16 * density).toInt()
        val leftOffset = (20 * density).toInt() // 竖条 4dp + 内容 padding 16dp
        val textAreaWidth = contentWidth - leftOffset - cardPadding

        var y = cardPadding // 顶部 padding

        // 标题行
        y += (24 * density).toInt()

        // 元数据行（分类/附件/关联）
        val hasCategory = category != null
        val hasAttachment = imageCount > 0
        val hasRelation = relationCount > 0
        if (hasCategory || hasAttachment || hasRelation) {
            y += (28 * density).toInt()
        }

        // 时间行
        val hasReminder = todo.reminderTime != null
        val hasDueDate = todo.dueDate != null
        if (hasReminder || hasDueDate) {
            y += (28 * density).toInt()
        }

        // 内容文本
        if (!todo.content.isNullOrBlank()) {
            val contentPaint = TextPaint().apply {
                textSize = (14 * density)
                color = Color.parseColor("#2D2D2D")
                isAntiAlias = true
            }
            val layout = StaticLayout.Builder.obtain(
                todo.content, 0, todo.content.length,
                contentPaint, textAreaWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing((4 * density), 1.0f)
                .setIncludePad(false)
                .build()
            y += layout.height + (8 * density).toInt()
        }

        // 子待办列表
        if (subTodos.isNotEmpty()) {
            y += (8 * density).toInt() // 顶部间距
            for (sub in subTodos) {
                y += (32 * density).toInt() // 每项高度
            }
        }

        // 图片缩略图行
        if (imageCount > 0) {
            y += (8 * density).toInt() + (72 * density).toInt() + (8 * density).toInt()
        }

        y += cardPadding // 底部 padding

        return y
    }

    /**
     * 绘制品牌顶部条
     *
     * 布局：[🐕 logo 32dp] [10dp] ["刻记+" + "柯基备忘录"] ... [日期胶囊]
     */
    private fun drawBrandHeader(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        density: Float
    ): Int {
        val headerHeight = (44 * density).toInt()
        val y = startY

        // 🐕 logo 圆角矩形
        val logoSize = (32 * density).toInt()
        val logoPaint = Paint().apply {
            color = Color.parseColor("#FF9A5C") // 主色
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(padding.toFloat(), (y + 6 * density).toFloat(),
                (padding + logoSize).toFloat(), (y + 6 * density + logoSize).toFloat()),
            (10 * density), (10 * density), logoPaint
        )
        // 🐕 emoji
        val emojiPaint = TextPaint().apply { textSize = (18 * density); isAntiAlias = true }
        canvas.drawText("🐕", padding + 5 * density, y + 28 * density, emojiPaint)

        // "刻记+"
        val nameX = padding + logoSize + (10 * density)
        val namePaint = TextPaint().apply {
            textSize = (15 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#2D2D2D")
            isAntiAlias = true
        }
        canvas.drawText("刻记+", nameX.toFloat(), (y + 22 * density).toFloat(), namePaint)

        // "柯基备忘录"
        val subPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#999999")
            isAntiAlias = true
        }
        canvas.drawText("柯基备忘录", nameX.toFloat(), (y + 36 * density).toFloat(), subPaint)

        // 日期胶囊
        val todayText = shortDateFormat.format(Date())
        val datePaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#5D4030")
            isAntiAlias = true
        }
        val dateWidth = datePaint.measureText(todayText)
        val dateBgWidth = dateWidth + (20 * density)
        val dateRight = (width - padding).toFloat()
        val dateBgPaint = Paint().apply {
            color = Color.parseColor("#FFF0E0")
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(dateRight - dateBgWidth, (y + 10 * density).toFloat(),
                dateRight, (y + 30 * density).toFloat()),
            (12 * density), (12 * density), dateBgPaint
        )
        canvas.drawText(todayText, dateRight - dateWidth - 10 * density, (y + 23 * density).toFloat(), datePaint)

        return y + headerHeight
    }

    /**
     * 绘制主待办卡片（还原 App TodoListItem 样式）
     *
     * 包含：白色圆角卡片背景 + 左侧4dp优先级竖条 + 1.5dp优先级边框
     * 内部：复选框 + 标题 + 进度 + 元数据 + 时间 + 子待办 + 图片
     */
    private fun drawTodoCard(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        todo: TodoItem,
        category: Category?,
        subTodos: List<SubTask>,
        imagePaths: List<String>,
        relationCount: Int,
        density: Float
    ): Int {
        val contentWidth = width - padding * 2
        val cardPadding = (16 * density).toInt()
        val leftOffset = (20 * density).toInt() // 竖条4dp + 内容padding16dp
        val textAreaWidth = contentWidth - leftOffset - cardPadding
        val cornerRadius = (16 * density)
        val imageCount = imagePaths.size

        // 测量内容高度
        val contentHeight = measureCardContent(canvas, width, padding, todo, category, subTodos, imageCount, relationCount, density)

        // 卡片白色背景
        val cardBgPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(padding.toFloat(), startY.toFloat(),
                (width - padding).toFloat(), (startY + contentHeight).toFloat()),
            cornerRadius, cornerRadius, cardBgPaint
        )

        // 优先级边框 (1.5dp)
        val priorityBorderColor = getPriorityBorderColor(todo.priority)
        val borderPaint = Paint().apply {
            color = priorityBorderColor
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = (1.5 * density).toFloat()
        }
        canvas.drawRoundRect(
            RectF(padding.toFloat(), startY.toFloat(),
                (width - padding).toFloat(), (startY + contentHeight).toFloat()),
            cornerRadius, cornerRadius, borderPaint
        )

        // 左侧 4dp 优先级竖条
        val barColor = getPriorityBarColor(todo.priority)
        val barPaint = Paint().apply {
            color = barColor
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(padding.toFloat(), startY.toFloat(),
                (padding + 4 * density).toFloat(), (startY + contentHeight).toFloat()),
            (4 * density), 0f, barPaint
        )
        // 竖条左侧圆角修正：画一个矩形覆盖左下角的圆角
        canvas.drawRect(
            RectF(padding.toFloat(), (startY + contentHeight - 4 * density).toFloat(),
                (padding + 4 * density).toFloat(), (startY + contentHeight).toFloat()),
            barPaint
        )

        // ===== 内容区域 =====
        var y = startY + cardPadding

        // 标题行：圆形复选框 + 标题 + 进度
        val checkboxRadius = (11 * density)
        val checkboxCenterX = padding + leftOffset.toFloat() - cardPadding + (11 * density)
        val checkboxCenterY = y + (12 * density)

        // 圆形复选框
        if (todo.status == 1) {
            // 已完成：橙色填充 + 白色 ✓
            val checkFillPaint = Paint().apply {
                color = Color.parseColor("#FF9A5C")
                isAntiAlias = true
            }
            canvas.drawCircle(checkboxCenterX, checkboxCenterY, checkboxRadius, checkFillPaint)
            val checkMarkPaint = TextPaint().apply {
                textSize = (14 * density)
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            canvas.drawText("✓", checkboxCenterX - (4 * density), checkboxCenterY + (5 * density), checkMarkPaint)
        } else {
            // 未完成：空心圆
            val checkEmptyPaint = Paint().apply {
                color = Color.parseColor("#CCCCCC")
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = (2 * density)
            }
            canvas.drawCircle(checkboxCenterX, checkboxCenterY, checkboxRadius, checkEmptyPaint)
        }

        // 标题
        val titleX = padding + leftOffset.toFloat()
        val titlePaint = TextPaint().apply {
            textSize = (16 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = if (todo.status == 1) Color.parseColor("#888888") else Color.parseColor("#2D2D2D")
            isAntiAlias = true
        }
        // 标题截断：进度文本预留约 60dp 空间
        val titleMaxWidth = textAreaWidth - (60 * density)
        val displayTitle = ellipsizeText(titlePaint, todo.title, titleMaxWidth)
        canvas.drawText(displayTitle, titleX, y + (16 * density), titlePaint)

        // 删除线（已完成态）
        if (todo.status == 1) {
            val titleWidth = titlePaint.measureText(displayTitle)
            val strikethroughPaint = Paint().apply {
                color = Color.parseColor("#888888")
                strokeWidth = (1 * density)
                isAntiAlias = true
            }
            canvas.drawLine(titleX, y + (10 * density), titleX + titleWidth, y + (10 * density), strikethroughPaint)
        }

        // 进度文本（有子待办时）
        if (subTodos.isNotEmpty()) {
            val completedCount = subTodos.count { it.isCompleted }
            val progressText = "($completedCount/${subTodos.size})"
            val progressPaint = TextPaint().apply {
                textSize = (13 * density)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                color = Color.parseColor("#FF9A5C")
                isAntiAlias = true
            }
            val progressWidth = progressPaint.measureText(progressText)
            canvas.drawText(progressText, (width - padding - cardPadding - progressWidth).toFloat(), y + (16 * density), progressPaint)
        }

        y += (24 * density).toInt()

        // 元数据行：分类 + 附件数 + 关联数
        val hasCategory = category != null
        val hasAttachment = imageCount > 0
        val hasRelation = relationCount > 0
        if (hasCategory || hasAttachment || hasRelation) {
            var metaX = titleX
            val metaPaint = TextPaint().apply {
                textSize = (12 * density)
                color = Color.parseColor("#666666")
                isAntiAlias = true
            }
            val metaBgPaint = Paint().apply { isAntiAlias = true }
            val metaChipPadding = (8 * density)

            // 分类胶囊
            if (hasCategory) {
                val catName = category!!.name
                val catWidth = metaPaint.measureText(catName) + metaChipPadding * 2
                metaBgPaint.color = Color.parseColor("#F5F5F5")
                canvas.drawRoundRect(
                    RectF(metaX, (y - 4 * density).toFloat(), metaX + catWidth, (y + 20 * density).toFloat()),
                    (8 * density), (8 * density), metaBgPaint
                )
                canvas.drawText(catName, metaX + metaChipPadding, y + (14 * density), metaPaint)
                metaX += catWidth + (8 * density).toInt()
            }

            // 附件数
            if (hasAttachment) {
                val attachText = "📎 $imageCount"
                val attachWidth = metaPaint.measureText(attachText) + metaChipPadding * 2
                metaBgPaint.color = Color.parseColor("#FFE4CC")
                canvas.drawRoundRect(
                    RectF(metaX, (y - 4 * density).toFloat(), metaX + attachWidth, (y + 20 * density).toFloat()),
                    (8 * density), (8 * density), metaBgPaint
                )
                canvas.drawText(attachText, metaX + metaChipPadding, y + (14 * density), metaPaint)
                metaX += attachWidth + (8 * density).toInt()
            }

            // 关联数
            if (hasRelation) {
                val relText = "🔗 $relationCount"
                val relWidth = metaPaint.measureText(relText) + metaChipPadding * 2
                metaBgPaint.color = Color.parseColor("#FFE4CC")
                canvas.drawRoundRect(
                    RectF(metaX, (y - 4 * density).toFloat(), metaX + relWidth, (y + 20 * density).toFloat()),
                    (8 * density), (8 * density), metaBgPaint
                )
                canvas.drawText(relText, metaX + metaChipPadding, y + (14 * density), metaPaint)
            }

            y += (28 * density).toInt()
        }

        // 时间行：提醒📅 + 截止🏁
        val hasReminder = todo.reminderTime != null
        val hasDueDate = todo.dueDate != null
        if (hasReminder || hasDueDate) {
            var timeX = titleX
            val timeChipPaint = TextPaint().apply {
                textSize = (12 * density)
                color = Color.parseColor("#2D2D2D")
                isAntiAlias = true
            }
            val timeChipPadding = (10 * density)
            val timeBgPaint = Paint().apply { isAntiAlias = true }

            if (hasReminder) {
                val reminderText = "📅 ${shortDateFormat.format(Date(todo.reminderTime!!))}"
                val reminderWidth = timeChipPaint.measureText(reminderText) + timeChipPadding * 2
                timeBgPaint.color = Color.parseColor("#FFE4CC")
                canvas.drawRoundRect(
                    RectF(timeX, (y - 4 * density).toFloat(), timeX + reminderWidth, (y + 20 * density).toFloat()),
                    (8 * density), (8 * density), timeBgPaint
                )
                canvas.drawText(reminderText, timeX + timeChipPadding, y + (14 * density), timeChipPaint)
                timeX += reminderWidth + (8 * density).toInt()
            }

            if (hasDueDate) {
                val dueText = "🏁 ${shortDateFormat.format(Date(todo.dueDate!!))}"
                val dueWidth = timeChipPaint.measureText(dueText) + timeChipPadding * 2
                timeBgPaint.color = Color.parseColor("#FFF0E0")
                canvas.drawRoundRect(
                    RectF(timeX, (y - 4 * density).toFloat(), timeX + dueWidth, (y + 20 * density).toFloat()),
                    (8 * density), (8 * density), timeBgPaint
                )
                canvas.drawText(dueText, timeX + timeChipPadding, y + (14 * density), timeChipPaint)
            }

            y += (28 * density).toInt()
        }

        // 内容文本
        if (!todo.content.isNullOrBlank()) {
            val contentPaint = TextPaint().apply {
                textSize = (14 * density)
                color = Color.parseColor("#2D2D2D")
                isAntiAlias = true
            }
            val layout = StaticLayout.Builder.obtain(
                todo.content, 0, todo.content.length,
                contentPaint, textAreaWidth
            )
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing((4 * density), 1.0f)
                .setIncludePad(false)
                .build()
            canvas.save()
            canvas.translate(titleX, y.toFloat())
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + (8 * density).toInt()
        }

        // 子待办列表
        if (subTodos.isNotEmpty()) {
            y += (8 * density).toInt()

            for (sub in subTodos) {
                val subCheckboxCenterX = titleX + (9 * density)
                val subCheckboxCenterY = y + (12 * density)
                val subCheckboxRadius = (9 * density)

                // 子待办复选框
                if (sub.isCompleted) {
                    val subCheckFill = Paint().apply {
                        color = Color.parseColor("#FF9A5C")
                        isAntiAlias = true
                    }
                    canvas.drawCircle(subCheckboxCenterX, subCheckboxCenterY, subCheckboxRadius, subCheckFill)
                    val subCheckMark = TextPaint().apply {
                        textSize = (11 * density)
                        color = Color.WHITE
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        isAntiAlias = true
                    }
                    canvas.drawText("✓", subCheckboxCenterX - (3 * density), subCheckboxCenterY + (4 * density), subCheckMark)
                } else {
                    val subCheckEmpty = Paint().apply {
                        color = Color.parseColor("#CCCCCC")
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeWidth = (1.5 * density).toFloat()
                    }
                    canvas.drawCircle(subCheckboxCenterX, subCheckboxCenterY, subCheckboxRadius, subCheckEmpty)
                }

                // 子待办标题（截断超长文本）
                val subTitlePaint = TextPaint().apply {
                    textSize = (14 * density)
                    color = if (sub.isCompleted) Color.parseColor("#888888") else Color.parseColor("#2D2D2D")
                    isAntiAlias = true
                }
                val subMaxWidth = textAreaWidth - (34 * density)
                val displaySubTitle = ellipsizeText(subTitlePaint, sub.title, subMaxWidth)
                canvas.drawText(displaySubTitle, subCheckboxCenterX + (16 * density), y + (16 * density), subTitlePaint)

                // 删除线
                if (sub.isCompleted) {
                    val subTitleWidth = subTitlePaint.measureText(displaySubTitle)
                    val stStartX = subCheckboxCenterX + (16 * density)
                    val strikethroughPaint2 = Paint().apply {
                        color = Color.parseColor("#888888")
                        strokeWidth = (1 * density)
                        isAntiAlias = true
                    }
                    canvas.drawLine(stStartX, y + (10 * density), stStartX + subTitleWidth, y + (10 * density), strikethroughPaint2)
                }

                // 分割线（非最后一项）
                if (sub != subTodos.last()) {
                    val divPaint = Paint().apply {
                        color = Color.parseColor("#EEEEEE")
                        isAntiAlias = true
                    }
                    canvas.drawLine(
                        titleX.toFloat(), (y + 32 * density).toFloat(),
                        (width - padding - cardPadding).toFloat(), (y + 32 * density).toFloat(),
                        divPaint
                    )
                }

                y += (32 * density).toInt()
            }
        }

        // 图片缩略图行（加载真实图片，最多显示3张，超过显示"+N"）
        if (imageCount > 0) {
            y += (8 * density).toInt()
            val thumbSize = (72 * density).toInt()   // 与编辑页一致 72dp
            val thumbSpacing = (8 * density).toInt()
            val thumbRadius = (8 * density)
            val fallbackPaint = Paint().apply {
                color = Color.parseColor("#F5F5F5")
                isAntiAlias = true
            }
            val morePaint = TextPaint().apply {
                textSize = (14 * density)
                color = Color.parseColor("#999999")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val displayCount = minOf(imageCount, 3)
            for (i in 0 until displayCount) {
                val thumbX = titleX + i * (thumbSize + thumbSpacing)

                if (i < imagePaths.size) {
                    // 尝试加载真实图片
                    val imagePath = imagePaths[i]
                    val imageFile = java.io.File(imagePath)
                    val srcBitmap = if (imageFile.exists()) {
                        try {
                            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 2  // 降采样节省内存
                                inJustDecodeBounds = false
                            })
                        } catch (_: Exception) { null }
                    } else null

                    if (srcBitmap != null) {
                        // 居中裁剪（CenterCrop）后绘制
                        val cropBitmap = centerCropBitmap(srcBitmap, thumbSize, thumbSize)
                        val srcRect = android.graphics.Rect(0, 0, cropBitmap.width, cropBitmap.height)
                        val dstRect = RectF(thumbX.toFloat(), y.toFloat(), (thumbX + thumbSize).toFloat(), (y + thumbSize).toFloat())
                        // 先绘制圆角裁剪背景
                        canvas.drawRoundRect(dstRect, thumbRadius, thumbRadius, fallbackPaint)
                        // 用 Canvas.save/restore + clipPath 实现圆角裁剪
                        canvas.save()
                        val clipPath = android.graphics.Path().apply {
                            addRoundRect(dstRect, thumbRadius, thumbRadius, android.graphics.Path.Direction.CW)
                        }
                        canvas.clipPath(clipPath)
                        canvas.drawBitmap(cropBitmap, srcRect, dstRect, Paint().apply { isAntiAlias = true })
                        canvas.restore()
                        if (cropBitmap != srcBitmap) cropBitmap.recycle()
                        srcBitmap.recycle()
                    } else {
                        // 加载失败：显示灰色占位
                        canvas.drawRoundRect(
                            RectF(thumbX.toFloat(), y.toFloat(), (thumbX + thumbSize).toFloat(), (y + thumbSize).toFloat()),
                            thumbRadius, thumbRadius, fallbackPaint
                        )
                    }
                } else {
                    // 超过图片数量：显示"+N"
                    val moreText = "+${imageCount - 2}"
                    canvas.drawRoundRect(
                        RectF(thumbX.toFloat(), y.toFloat(), (thumbX + thumbSize).toFloat(), (y + thumbSize).toFloat()),
                        thumbRadius, thumbRadius, fallbackPaint
                    )
                    val moreWidth = morePaint.measureText(moreText)
                    canvas.drawText(moreText, thumbX + (thumbSize - moreWidth) / 2, y + thumbSize / 2 + (5 * density), morePaint)
                }
            }
            y += thumbSize + (8 * density).toInt()
        }

        y += cardPadding // 底部 padding

        return startY + contentHeight
    }

    /**
     * 安全测量子待办标题宽度（截断超长文本）
     */
    private fun sub_title_width_safe(paint: TextPaint, text: String, maxWidth: Int): Float {
        val measured = paint.measureText(text)
        return if (measured > maxWidth) maxWidth.toFloat() else measured
    }

    /**
     * CenterCrop 裁剪 Bitmap（与 Compose ContentScale.Crop 等效）
     *
     * 将源图按比例缩放至完全覆盖目标尺寸，然后居中裁剪。
     * 若源图尺寸已等于目标尺寸则直接返回源图（不创建新 Bitmap）。
     *
     * @param source 源 Bitmap
     * @param targetWidth 目标宽度（像素）
     * @param targetHeight 目标高度（像素）
     * @return 裁剪后的 Bitmap（可能与 source 同一对象）
     */
    private fun centerCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) return source

        val scale = maxOf(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height
        )
        val scaledWidth = (source.width * scale).toInt()
        val scaledHeight = (source.height * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)

        val startX = (scaledWidth - targetWidth) / 2
        val startY = (scaledHeight - targetHeight) / 2

        return Bitmap.createBitmap(scaled, startX, startY, targetWidth, targetHeight)
    }

    /**
     * 截断超长文本并添加省略号
     *
     * 使用 TextPaint.measureText 逐字符测量，超过 maxWidth 时截断并追加"…"。
     *
     * @param paint 文本画笔
     * @param text 原始文本
     * @param maxWidth 最大宽度（像素）
     * @return 截断后的文本
     */
    private fun ellipsizeText(paint: TextPaint, text: String, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val ellipsisWidth = paint.measureText(ellipsis)
        val availableWidth = maxWidth - ellipsisWidth
        if (availableWidth <= 0) return ellipsis

        var end = text.length
        while (end > 0 && paint.measureText(text, 0, end) > availableWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }

    /**
     * 绘制品牌水印
     *
     * 分割线 + "🐕来自 CorgiMemo · 让待办变得可爱"
     */
    private fun drawBrandFooter(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        density: Float
    ): Int {
        var y = startY

        // 分割线
        val divPaint = Paint().apply {
            color = Color.parseColor("#EEEEEE")
            isAntiAlias = true
        }
        canvas.drawLine(padding.toFloat(), y.toFloat(), (width - padding).toFloat(), y.toFloat(), divPaint)
        y += (16 * density).toInt()

        // 水印文字
        val footerPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#999999")
            isAntiAlias = true
        }
        val footerText = "🐕 来自 CorgiMemo · 让待办变得可爱"
        val footerWidth = footerPaint.measureText(footerText)
        canvas.drawText(footerText, ((width - footerWidth) / 2).toFloat(), y.toFloat(), footerPaint)

        return y + (16 * density).toInt()
    }

    /**
     * 获取优先级竖条颜色（与 PriorityColors 一致）
     */
    private fun getPriorityBarColor(priority: Int): Int {
        return when (priority) {
            3 -> PriorityColors.High.toArgb()
            2 -> PriorityColors.Medium.toArgb()
            1 -> PriorityColors.Low.toArgb()
            else -> PriorityColors.None.toArgb()
        }
    }

    /**
     * 获取优先级边框颜色（alpha=0.6，与 TodoListItem 一致）
     */
    private fun getPriorityBorderColor(priority: Int): Int {
        val baseColor = when (priority) {
            3 -> PriorityColors.High.toArgb()
            2 -> PriorityColors.Medium.toArgb()
            1 -> PriorityColors.Low.toArgb()
            else -> PriorityColors.None.toArgb()
        }
        // 应用 0.6f alpha
        val r = android.graphics.Color.red(baseColor)
        val g = android.graphics.Color.green(baseColor)
        val b = android.graphics.Color.blue(baseColor)
        return android.graphics.Color.argb((255 * 0.6f).toInt(), r, g, b)
    }

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
     * 拼图数量超过上限异常
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

        val columnWidth = outputWidthPx
        val rowScaledBitmaps = bitmaps.map { scaleBitmapToWidth(it, columnWidth) }

        val columnsCount = if (strategy == MergeStrategy.GRID_2_COL) 2 else 1
        val rowsCount = (rowScaledBitmaps.size + columnsCount - 1) / columnsCount

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
     */
    private fun scaleBitmapToWidth(source: Bitmap, targetWidth: Int): Bitmap {
        if (source.width == targetWidth) return source
        val ratio = targetWidth.toFloat() / source.width
        val targetHeight = (source.height * ratio).toInt()
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }
}
