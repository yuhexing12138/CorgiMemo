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
     *
     * v5 改造：分类/优先级角标移至卡片顶部两角，底部仅留时间标签。
     * - 顶部 padding 36dp（给两角角标留白）
     * - 末尾元数据区简化为底部时间标签（无分割线，无 chip 高度）
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
        val topCardPadding = (36 * density).toInt()   // 顶部 36dp（角标占位）
        val bottomCardPadding = (16 * density).toInt() // 底部 16dp

        var y = topCardPadding

        // 标题行
        y += (24 * density).toInt()

        // 主待办图片（行内联，紧跟标题下方）
        if (imageCount > 0) {
            y += (8 * density).toInt() + (56 * density).toInt() + (8 * density).toInt()
        }

        // 子待办列表
        if (subTodos.isNotEmpty()) {
            y += (8 * density).toInt() // 顶部间距
            for (sub in subTodos) {
                y += (32 * density).toInt() // 每项高度
                // 子待办附件（行内联）
                val subImageCount = parseImagePaths(sub.imagePaths).size
                if (subImageCount > 0) {
                    y += (4 * density).toInt() + (56 * density).toInt() + (4 * density).toInt()
                }
            }
        }

        // 底部时间标签（独立靠左显示，无分割线）
        val hasReminder = todo.reminderTime != null
        val hasDueDate = todo.dueDate != null
        if (hasReminder || hasDueDate) {
            y += (10 * density).toInt()  // 顶部间距
            y += (20 * density).toInt()  // 时间芯片高度
        }

        y += bottomCardPadding // 底部 padding

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
        val topCardPadding = (36 * density).toInt()   // 顶部 36dp（给两角角标留白）
        val bottomCardPadding = (16 * density).toInt() // 底部 16dp
        val leftOffset = (20 * density).toInt() // 竖条4dp + 内容padding16dp
        val textAreaWidth = contentWidth - leftOffset - bottomCardPadding
        // 卡片圆角 = 角标最大可装圆角（= 角标 height / 2 = 9.5dp），保证圆角视觉一致
        // 注意：9.5f 必须用 Float literal，否则 9.5 * density 推断为 Double，drawRoundRect 会报类型不匹配
        val cornerRadius = (9.5f * density)
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

        // 左侧 4dp 优先级竖条（不内缩，用卡片圆角 clipPath 自动裁剪上下超出部分）
        val barColor = getPriorityBarColor(todo.priority)
        val barPaint = Paint().apply {
            color = barColor
            isAntiAlias = true
        }
        // 用卡片圆角矩形作为 clipPath，竖条会被自然裁剪
        canvas.save()
        val cardClipPath = android.graphics.Path().apply {
            addRoundRect(
                RectF(padding.toFloat(), startY.toFloat(),
                    (width - padding).toFloat(), (startY + contentHeight).toFloat()),
                cornerRadius, cornerRadius, android.graphics.Path.Direction.CW
            )
        }
        canvas.clipPath(cardClipPath)
        // 竖条充满整个卡片高度（左右边缘会与卡片圆角自然对齐）
        canvas.drawRect(
            RectF(padding.toFloat(), startY.toFloat(),
                (padding + 4 * density).toFloat(), (startY + contentHeight).toFloat()),
            barPaint
        )
        canvas.restore()

        // ===== 顶部两角角标 =====
        // 用卡片圆角矩形作为 clipPath，让两个角标超出卡片圆角范围的部分被自动裁剪
        canvas.save()
        val badgeClipPath = android.graphics.Path().apply {
            addRoundRect(
                RectF(padding.toFloat(), startY.toFloat(),
                    (width - padding).toFloat(), (startY + contentHeight).toFloat()),
                cornerRadius, cornerRadius, android.graphics.Path.Direction.CW
            )
        }
        canvas.clipPath(badgeClipPath)
        // 分类角标：左上角贴边（从竖条内侧开始），clipPath 自动裁剪顶部超出
        drawCategoryBadge(canvas, padding, startY, category, density)
        // 优先级角标：右上角贴边，clipPath 自动裁剪右/顶部超出
        drawPriorityBadge(canvas, width, padding, startY, todo.priority, density)
        canvas.restore()

        // ===== 内容区域 =====
        var y = startY + topCardPadding

        // 标题行：圆形复选框 + 标题 + 进度
        // 布局：竖条(4dp) + 间距(6dp) + 复选框(22dp) + 间距(12dp) + 标题
        val checkboxDiameter = 22 * density
        val checkboxRadius = checkboxDiameter / 2
        val checkboxGap = 6 * density
        val titleGap = 12 * density
        val barInsetF = 1.5f * density
        // 复选框中心 X = padding + 竖条内缩 + 竖条宽度 + 间距 + 复选框半径
        val checkboxCenterX = padding + barInsetF + (4 * density) + checkboxGap + checkboxRadius
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
        val titleX = checkboxCenterX + checkboxRadius + titleGap
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
            // 进度文本右对齐到卡片右内边缘（width - padding - bottomCardPadding）
            canvas.drawText(progressText, (width - padding - bottomCardPadding - progressWidth).toFloat(), y + (16 * density), progressPaint)
        }

        y += (24 * density).toInt()

        // 主待办图片（行内联，紧跟标题下方，与待办编辑页一致）
        if (imagePaths.isNotEmpty()) {
            y += (8 * density).toInt()
            y = drawImageRow(canvas, titleX, y, imagePaths, density)
            y += (8 * density).toInt()
        }

        // 子待办列表（每个子待办可独立 inline 图片）
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

                y += (32 * density).toInt()

                // 子待办附件（行内联，紧跟子待办标题下方，与待办编辑页一致）
                val subImagePaths = parseImagePaths(sub.imagePaths)
                if (subImagePaths.isNotEmpty()) {
                    // 缩进对齐：子待办缩进 28dp
                    val subImageX = titleX + (28 * density)
                    y += (4 * density).toInt()
                    y = drawImageRow(canvas, subImageX, y, subImagePaths, density)
                    y += (4 * density).toInt()
                }

                // 分割线（非最后一项）
                if (sub != subTodos.last()) {
                    val divPaint = Paint().apply {
                        color = Color.parseColor("#EEEEEE")
                        isAntiAlias = true
                    }
                    canvas.drawLine(
                        titleX.toFloat(), y.toFloat(),
                        (width - padding - bottomCardPadding).toFloat(), y.toFloat(),
                        divPaint
                    )
                }
            }
        }

        // 底部时间标签（独立靠左显示，无分割线，无分类/优先级 chip）
        y = drawBottomTimeChip(canvas, padding, startY, contentHeight, y, todo, density)

        return startY + contentHeight
    }

    /**
     * 绘制图片缩略图行（行内联）
     *
     * @param canvas 画布
     * @param x 起始 X 坐标
     * @param y 起始 Y 坐标
     * @param imagePaths 图片路径列表
     * @param density 屏幕密度
     * @return 绘制结束后的 Y 坐标（已加上行高）
     */
    private fun drawImageRow(
        canvas: Canvas,
        x: Float,
        y: Int,
        imagePaths: List<String>,
        density: Float
    ): Int {
        val thumbSize = (56 * density).toInt()   // 行内联缩略图 56dp
        val thumbSpacing = (6 * density).toInt()
        val thumbRadius = (6 * density)
        val fallbackPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            isAntiAlias = true
        }
        val morePaint = TextPaint().apply {
            textSize = (12 * density)
            color = Color.parseColor("#999999")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val imageCount = imagePaths.size
        val displayCount = minOf(imageCount, 3)
        var currentX = x
        for (i in 0 until displayCount) {
            val imagePath = imagePaths[i]
            val imageFile = java.io.File(imagePath)
            val srcBitmap = if (imageFile.exists()) {
                try {
                    android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                } catch (_: Exception) { null }
            } else null

            if (srcBitmap != null) {
                val cropBitmap = centerCropBitmap(srcBitmap, thumbSize, thumbSize)
                val srcRect = android.graphics.Rect(0, 0, cropBitmap.width, cropBitmap.height)
                val dstRect = RectF(currentX, y.toFloat(), (currentX + thumbSize).toFloat(), (y + thumbSize).toFloat())
                canvas.drawRoundRect(dstRect, thumbRadius, thumbRadius, fallbackPaint)
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
                // 加载失败：灰色占位
                canvas.drawRoundRect(
                    RectF(currentX, y.toFloat(), (currentX + thumbSize).toFloat(), (y + thumbSize).toFloat()),
                    thumbRadius, thumbRadius, fallbackPaint
                )
            }
            currentX += thumbSize + thumbSpacing
        }

        // 超过 3 张：显示 "+N"
        if (imageCount > 3) {
            val moreText = "+${imageCount - 3}"
            val moreWidth = morePaint.measureText(moreText)
            canvas.drawRoundRect(
                RectF(currentX, y.toFloat(), (currentX + thumbSize).toFloat(), (y + thumbSize).toFloat()),
                thumbRadius, thumbRadius, fallbackPaint
            )
            canvas.drawText(moreText, currentX + (thumbSize - moreWidth) / 2, y + thumbSize / 2 + (4 * density), morePaint)
        }

        return y + thumbSize
    }

    /**
     * 解析 imagePaths JSON 字符串为 List<String>
     *
     * @param imagePathsJson JSON 数组字符串，空字符串返回空列表
     * @return 图片路径列表
     */
    private fun parseImagePaths(imagePathsJson: String?): List<String> {
        if (imagePathsJson.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(imagePathsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) { emptyList() }
    }

    /**
     * 绘制分类角标（卡片左上角贴边）
     *
     * 圆角设计：左边贴边于竖条内侧（不覆盖竖条）+ 仅右下角加圆角（与卡片圆角一致 16dp）
     * 仿灵感详情页字数徽章风格：半透明灰背景 + 11sp 文字 + 4×10dp 内边距
     * 由 drawTodoCard 中的卡片圆角 clipPath 自动裁剪顶部超出部分
     *
     * @param canvas 画布
     * @param padding 卡片左外边距
     * @param startY 卡片顶部 Y 坐标
     * @param category 分类（null 时跳过绘制）
     * @param density 屏幕密度
     */
    private fun drawCategoryBadge(
        canvas: Canvas,
        padding: Int,
        startY: Int,
        category: Category?,
        density: Float
    ) {
        if (category == null) return
        // 角标圆角与卡片严格一致（= 9.5dp = 角标 height/2，保证完整 90° 圆弧）
        // 注意：9.5f 必须用 Float literal，否则 9.5 * density 推断为 Double
        val cornerRadius = (9.5f * density)
        val badgePaddingH = (10 * density)
        val badgePaddingV = (4 * density)
        val barWidth = 4 * density // 左侧竖条宽度，分类角标从竖条内侧开始
        val textPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#666666")
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val text = category.name
        val textWidth = textPaint.measureText(text)
        val badgeWidth = textWidth + badgePaddingH * 2
        val badgeHeight = (11 * density) + badgePaddingV * 2
        // left 从竖条内侧（padding + 4dp）开始，不再覆盖竖条
        val left = padding + barWidth
        val top = startY.toFloat()
        val right = left + badgeWidth
        val bottom = top + badgeHeight

        // 背景路径：仅右下角圆角
        val bgPath = android.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom - cornerRadius)
            arcTo(
                right - cornerRadius, bottom - cornerRadius,
                right, bottom,
                0f, 90f, false
            )
            lineTo(left, bottom)
            lineTo(left, top)
            close()
        }
        val bgPaint = Paint().apply {
            color = Color.argb((255 * 0.6f).toInt(), 224, 224, 224)
            isAntiAlias = true
        }
        canvas.drawPath(bgPath, bgPaint)

        // 文字
        canvas.drawText(text, left + badgePaddingH, top + badgePaddingV + (11 * density) - (1 * density), textPaint)
    }

    /**
     * 绘制优先级角标（卡片右上角贴边）
     *
     * 圆角设计：三边贴边（顶/右/底 0 圆角）+ 仅左下角加圆角（与卡片圆角一致 16dp）
     * 背景色随优先级变化：半透明 + 圆点 + 完整优先级文字
     *
     * @param canvas 画布
     * @param width 卡片外宽
     * @param padding 卡片右外边距（即 width - rightOffset）
     * @param startY 卡片顶部 Y 坐标
     * @param priority 优先级（0=无, 1=低, 2=中, 3=高）
     * @param density 屏幕密度
     */
    private fun drawPriorityBadge(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        priority: Int,
        density: Float
    ) {
        // 角标圆角与卡片严格一致（= 9.5dp = 角标 height/2，保证完整 90° 圆弧）
        // 注意：9.5f 必须用 Float literal，否则 9.5 * density 推断为 Double
        val cornerRadius = (9.5f * density)
        val badgePaddingH = (10 * density)
        val badgePaddingV = (4 * density)
        val dotRadius = (3 * density)
        val dotGap = (4 * density)
        val textPaint = TextPaint().apply {
            textSize = (11 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val priorityName = getPriorityName(priority)
        textPaint.color = getPriorityTextColor(priority)
        val textWidth = textPaint.measureText(priorityName)
        val badgeWidth = badgePaddingH * 2 + dotRadius * 2 + dotGap + textWidth
        val badgeHeight = (11 * density) + badgePaddingV * 2

        val right = (width - padding).toFloat()
        val left = right - badgeWidth
        val top = startY.toFloat()
        val bottom = top + badgeHeight

        // 背景路径：仅左下角圆角
        val bgPath = android.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(left + cornerRadius, bottom)
            arcTo(
                left, bottom - cornerRadius,
                left + cornerRadius, bottom,
                90f, 90f, false
            )
            lineTo(left, top)
            close()
        }
        val bgColor = getPriorityBgColor(priority)
        val bgPaint = Paint().apply {
            color = bgColor
            isAntiAlias = true
        }
        canvas.drawPath(bgPath, bgPaint)

        // 圆点
        val dotCenterX = left + badgePaddingH + dotRadius
        val dotCenterY = top + badgeHeight / 2
        val dotColor = getPriorityBarColor(priority)
        val dotPaint = Paint().apply {
            color = dotColor
            isAntiAlias = true
        }
        canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, dotPaint)

        // 文字
        canvas.drawText(
            priorityName,
            dotCenterX + dotRadius + dotGap,
            top + badgePaddingV + (11 * density) - (1 * density),
            textPaint
        )
    }

    /**
     * 绘制底部时间标签（卡片底部靠左显示）
     *
     * 显示提醒时间或截止时间（优先显示提醒时间），无分割线、无分类/优先级 chip
     *
     * @param canvas 画布
     * @param padding 卡片左外边距
     * @param cardStartY 卡片顶部 Y 坐标
     * @param contentHeight 卡片内容高度
     * @param startY 当前 Y 坐标（子待办列表末尾）
     * @param todo 待办项
     * @param density 屏幕密度
     * @return 绘制结束后的 Y 坐标
     */
    private fun drawBottomTimeChip(
        canvas: Canvas,
        padding: Int,
        cardStartY: Int,
        contentHeight: Int,
        startY: Int,
        todo: TodoItem,
        density: Float
    ): Int {
        val hasReminder = todo.reminderTime != null
        val hasDueDate = todo.dueDate != null
        if (!hasReminder && !hasDueDate) return startY

        val barInset = 1.5f * density
        val leftOffset = padding + barInset + (4 * density)  // 与标题对齐

        val chipHeight = (20 * density)
        val chipPaddingH = (10 * density)
        val chipRadius = (8 * density)
        var y = startY + (10 * density).toInt()

        val textPaint = TextPaint().apply {
            textSize = (12 * density)
            color = Color.parseColor("#2D2D2D")
            isAntiAlias = true
        }
        val text = if (hasReminder) {
            "📅 ${shortDateFormat.format(Date(todo.reminderTime!!))}"
        } else {
            "🏁 ${shortDateFormat.format(Date(todo.dueDate!!))}"
        }
        val textWidth = textPaint.measureText(text)
        val chipWidth = textWidth + chipPaddingH * 2

        val bgPaint = Paint().apply {
            color = if (hasReminder) Color.parseColor("#FFE4CC") else Color.parseColor("#FFF0E0")
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(leftOffset, y.toFloat(), (leftOffset + chipWidth).toFloat(), (y + chipHeight)),
            chipRadius, chipRadius, bgPaint
        )
        canvas.drawText(text, (leftOffset + chipPaddingH).toFloat(), y + (14 * density), textPaint)

        return y + chipHeight.toInt()
    }

    /**
     * 获取优先级完整文字（与原型一致）
     */
    private fun getPriorityName(priority: Int): String {
        return when (priority) {
            3 -> "高优先级"
            2 -> "中优先级"
            1 -> "低优先级"
            else -> "无优先级"
        }
    }

    /**
     * 获取优先级标签背景色（与原型 rgba(..., 0.15) 一致）
     */
    private fun getPriorityBgColor(priority: Int): Int {
        val baseColor = when (priority) {
            3 -> Color.parseColor("#FF8A80")
            2 -> Color.parseColor("#FFB74D")
            1 -> Color.parseColor("#90CAF9")
            else -> Color.parseColor("#C8E6C9")
        }
        // 应用 0.15 alpha 与原型 rgba(..., 0.15) 一致
        val r = android.graphics.Color.red(baseColor)
        val g = android.graphics.Color.green(baseColor)
        val b = android.graphics.Color.blue(baseColor)
        return android.graphics.Color.argb((255 * 0.15f).toInt(), r, g, b)
    }

    /**
     * 获取优先级文字颜色
     */
    private fun getPriorityTextColor(priority: Int): Int {
        return when (priority) {
            3 -> Color.parseColor("#C62828")
            2 -> Color.parseColor("#E65100")
            1 -> Color.parseColor("#1565C0")
            else -> Color.parseColor("#2E7D32")
        }
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
