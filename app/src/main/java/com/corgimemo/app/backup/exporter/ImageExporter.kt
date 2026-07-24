package com.corgimemo.app.backup.exporter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.corgimemo.app.R
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
    private val timeWithYearFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())

    /**
     * 创建待办分享卡片图片（v2 重设计）
     *
     * 布局结构（从上到下）：
     * 1. 品牌顶部条：App 启动图标（柯基歪头图）+ "刻记⁺"（上标+）+ "治愈系柯基记录应用" + 分享时间胶囊（含年份）
     * 2. 主待办卡片：还原 App TodoListItem 样式
     *    - 左侧 4dp 优先级竖条 + 1.5dp 边框
     *    - 圆形复选框 + 标题 + 进度
     *    - 元数据行（分类 + 附件数 + 关联数）
     *    - 时间行（提醒 + 截止，已合并为左下角单标签）
     *    - 子待办列表（复选框 + 标题）
     *    - 图片缩略图行
     * 3. 品牌水印：分割线 + "来自 刻记⁺ · 让待办变得可爱"
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

        // 预加载 App 启动图标（corgi_tilt_2frames_01.png），用于品牌顶部条
        val appIconBitmap = loadAppIconBitmap(context, density)

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
        currentY = drawBrandHeader(canvas, width, padding, currentY, density, appIconBitmap)

        currentY += cardSpacing

        // 2. 主待办卡片
        currentY = drawTodoCard(canvas, width, padding, currentY, todo, category, subTodos, imagePaths, relationCount, density)

        currentY += cardSpacing

        // 3. 品牌水印
        drawBrandFooter(canvas, width, padding, currentY, density)

        bitmap
    }

    /**
     * 加载 App 启动图标（corgi_tilt_2frames_01.png）并缩放至目标尺寸
     * 共享给品牌头部条使用
     */
    private fun loadAppIconBitmap(context: Context, density: Float): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val original = BitmapFactory.decodeResource(context.resources, R.drawable.corgi_tilt_2frames_01, options)
                ?: return null
            val targetSize = (32 * density).toInt()
            val scaled = Bitmap.createScaledBitmap(original, targetSize, targetSize, true)
            if (scaled !== original) original.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
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
     * 布局：[App 启动图标 32dp] [10dp] ["刻记⁺" + "治愈系柯基记录应用"] ... [分享时间胶囊(含年)]
     */
    private fun drawBrandHeader(
        canvas: Canvas,
        width: Int,
        padding: Int,
        startY: Int,
        density: Float,
        appIconBitmap: Bitmap?
    ): Int {
        val headerHeight = (44 * density).toInt()
        val y = startY

        // App 启动图标容器（圆角矩形）
        val logoSize = (32 * density).toInt()
        val logoLeft = padding
        val logoTop = (y + 6 * density).toInt()
        val logoRight = logoLeft + logoSize
        val logoBottom = logoTop + logoSize
        val logoBgRadius = (10 * density)

        // 1. 画白色背景圆角矩形（避免透明 PNG 区域透出页面背景）
        val logoBgPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        canvas.drawRoundRect(
            RectF(logoLeft.toFloat(), logoTop.toFloat(),
                logoRight.toFloat(), logoBottom.toFloat()),
            logoBgRadius, logoBgRadius, logoBgPaint
        )

        // 2. 叠加 App 启动图标（corgi_tilt_2frames_01.png，柯基歪头图）
        if (appIconBitmap != null) {
            val iconSrcRect = android.graphics.Rect(0, 0, appIconBitmap.width, appIconBitmap.height)
            val iconDstRect = android.graphics.Rect(logoLeft, logoTop, logoRight, logoBottom)
            canvas.drawBitmap(appIconBitmap, iconSrcRect, iconDstRect, null)
        } else {
            // 兜底：图标加载失败时显示文字
            val fallbackPaint = TextPaint().apply {
                textSize = (18 * density)
                color = Color.parseColor("#FF9A5C")
                isAntiAlias = true
            }
            val fallbackText = "柯"
            val textWidth = fallbackPaint.measureText(fallbackText)
            canvas.drawText(
                fallbackText,
                logoLeft + (logoSize - textWidth) / 2,
                logoTop + (logoSize / 2 + 6 * density),
                fallbackPaint
            )
        }

        // "刻记⁺"（+ 号用上标 Unicode U+207A，与 strings.xml app_name 严格一致）
        val nameX = logoRight + (10 * density)
        val namePaint = TextPaint().apply {
            textSize = (15 * density)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#2D2D2D")
            isAntiAlias = true
        }
        canvas.drawText("刻记\u207A", nameX.toFloat(), (y + 22 * density).toFloat(), namePaint)

        // 副标题："治愈系柯基记录应用"
        val subPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#999999")
            isAntiAlias = true
        }
        canvas.drawText("治愈系柯基记录应用", nameX.toFloat(), (y + 36 * density).toFloat(), subPaint)

        // 分享时间胶囊（含年份，MM/dd HH:mm + 年份副标）
        val timeText = timeWithYearFormat.format(Date())
        val datePaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#5D4030")
            isAntiAlias = true
        }
        val dateWidth = datePaint.measureText(timeText)
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
        canvas.drawText(timeText, dateRight - dateWidth - 10 * density, (y + 23 * density).toFloat(), datePaint)

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
        // 卡片主圆角 9dp（与角标圆角一致）
        // 边框 path 圆角 = 主圆角 - stroke/2 = 9 - 0.75 = 8.25dp，
        //   让边框外侧 = path + 0.75 = 9dp（与角标视觉一致）
        // 原因：Android Canvas 的 stroke 是中心线（half in / half out），与 HTML/CSS border 外侧不同
        val cornerRadius = (9f * density)
        val borderRadius = (8.25f * density)
        // 边框内侧圆角 = borderRadius - stroke/2 = 8.25 - 0.75 = 7.5dp
        // 用作角标 clipPath，让角标被边框内侧"切掉"超出部分 → 边框 1.5dp 范围 [7.5dp, 9dp] 内完全无角标
        // 视觉效果：边框完全压住角标（角标只在 0~7.5dp 圆角范围内可见，不透出边框）
        val borderInnerRadius = (7.5f * density)
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

        // ===== 顶部两角角标 =====
        // 用边框内侧（borderInnerRadius 7.5dp）作为 clipPath，让角标被边框"切掉"超出部分
        // 这样边框 1.5dp [7.5dp, 9dp] 范围内完全没有角标 → 边框完全压住角标，不透出
        // 角标 0~7.5dp 圆角范围内完整显示；[7.5dp, 9dp] 范围被边框内侧"切掉"
        canvas.save()
        val badgeClipPath = android.graphics.Path().apply {
            addRoundRect(
                RectF(padding.toFloat(), startY.toFloat(),
                    (width - padding).toFloat(), (startY + contentHeight).toFloat()),
                borderInnerRadius, borderInnerRadius, android.graphics.Path.Direction.CW
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

        // 底部时间标签（贴卡片左下角，与分类角标对称——仅右上角圆角 9dp）
        drawBottomTimeChip(canvas, width, padding, startY, contentHeight, todo, density)

        // ===== 优先级边框（在所有角标和时间标签之上，覆盖它们的背景色边缘） =====
        // 边框线 1.5dp 描边，在卡片边缘 0.75dp 位置 → 覆盖角标顶部 0.75dp 外侧
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
            borderRadius, borderRadius, borderPaint
        )

        // ===== 左侧 4dp 优先级竖条（最上层，覆盖边框和所有角标背景色） =====
        // 关键：竖条左边缘严格对齐优先级外框的外侧边缘
        //   边框 stroke 1.5dp 中心线在 padding 位置 → 外侧 = padding - 0.75dp, 内侧 = padding + 0.75dp
        //   竖条左边缘 = padding - 0.75dp = 边框外侧 (严格对齐)
        //   竖条右边缘 = padding + 4dp (与分类角标 left 对齐，保持无缝拼接)
        // 关键：竖条在圆角处紧贴外侧边缘
        //   clipPath 圆心必须与边框外侧圆心严格对齐
        //     边框外侧 = stroke 中心线 (path 圆角 8.25dp) + 0.75dp = 9dp 圆弧
        //     边框外侧圆心 = path 圆心 = (padding + 8.25dp, startY + 8.25dp)
        //     clipPath 圆心 = (clipPath left + 9dp, clipPath top + 9dp)
        //     → clipPath left = padding + 8.25dp - 9dp = padding - 0.75dp = padding - barOffset ✓
        //     → clipPath top = startY + 8.25dp - 9dp = startY - 0.75dp = startY - barOffset ✓
        //   clipPath 范围上下左右各扩 barOffset，让圆心与边框外侧圆心对齐
        val barColor = getPriorityBarColor(todo.priority)
        val barPaint = Paint().apply {
            color = barColor
            isAntiAlias = true
        }
        val barOffset = 0.75f * density // strokeWidth/2 = 边框外侧偏移量
        canvas.save()
        val cardClipPath = android.graphics.Path().apply {
            addRoundRect(
                // 范围左右各扩 barOffset，上下各扩 barOffset → clipPath 圆心 = 边框外侧圆心
                RectF((padding - barOffset), (startY - barOffset),
                    (width - padding + barOffset), (startY + contentHeight + barOffset)),
                cornerRadius, cornerRadius, android.graphics.Path.Direction.CW
            )
        }
        canvas.clipPath(cardClipPath)
        // 竖条：上下延伸充满整个卡片高度，左右用 clipPath 圆角裁剪
        //   竖条左边缘 = padding - barOffset = 边框外侧 (严格对齐)
        //   竖条右边缘 = padding + 4dp = 距卡片左外边 4dp (与分类角标 left 对齐)
        //   竖条顶部/底部超出 clipPath 圆角范围的部分被自动裁剪 → 在圆角处紧贴外侧边缘
        canvas.drawRect(
            RectF(
                (padding - barOffset),  // 竖条左边缘 = 边框外侧
                startY.toFloat(),
                (padding + 4 * density).toFloat(),  // 竖条右边缘 = 距卡片左外边 4dp (与分类角标 left 对齐)
                (startY + contentHeight).toFloat()
            ),
            barPaint
        )
        canvas.restore()

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
        // 角标圆角与卡片严格一致（9dp，与边框外侧圆角视觉一致）
        val cornerRadius = (9f * density)
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
        // 关键：Path.arcTo(left, top, right, bottom, ...) 的 (left, top, right, bottom)
        //   是椭圆的边界框，椭圆半径 = (width/2, height/2)。
        //   要让圆角半径 = cornerRadius = 9dp，椭圆边界框必须是 2*cornerRadius × 2*cornerRadius = 18×18。
        //   原 bug：椭圆边界框 = cornerRadius × cornerRadius = 9×9，实际圆角 = 4.5dp（半圆），
        //   这就是为什么角标圆角视觉上比卡片小一半的原因。
        val bgPath = android.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom - cornerRadius)
            arcTo(
                right - 2 * cornerRadius, bottom - 2 * cornerRadius,
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
        // 角标圆角与卡片严格一致（9dp，与边框外侧圆角视觉一致）
        val cornerRadius = (9f * density)
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
        // 关键：Path.arcTo 的椭圆边界框必须是 2*cornerRadius × 2*cornerRadius = 18×18，
        //   这样椭圆半径 = cornerRadius = 9dp，圆角与卡片严格一致。
        //   原 bug：椭圆边界框 = cornerRadius × cornerRadius = 9×9，实际圆角 = 4.5dp。
        val bgPath = android.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(left + cornerRadius, bottom)
            arcTo(
                left, bottom - 2 * cornerRadius,
                left + 2 * cornerRadius, bottom,
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
     * 绘制底部时间标签（卡片左下角贴边，与分类角标对称）
     *
     * 内容合并：
     *   - 都有提醒 + 截止：MM/dd HH:mm - MM/dd HH:mm
     *   - 只有提醒或截止：仅显示一个
     * 不使用 emoji，日期之间用"-"连接
     *
     * 圆角设计：仅右上角圆角 9dp（与卡片圆角严格一致）
     * 左边贴卡片内（从竖条内侧开始，不覆盖竖条），底边贴卡片外
     * 由卡片圆角 clipPath 自动裁剪底部超出部分
     *
     * @param canvas 画布
     * @param width 画布宽度（用于卡片圆角 clipPath）
     * @param padding 卡片左外边距
     * @param cardStartY 卡片顶部 Y 坐标
     * @param contentHeight 卡片内容高度
     * @param todo 待办项
     * @param density 屏幕密度
     */
    private fun drawBottomTimeChip(
        canvas: Canvas,
        width: Int,
        padding: Int,
        cardStartY: Int,
        contentHeight: Int,
        todo: TodoItem,
        density: Float
    ) {
        val reminderTime = todo.reminderTime
        val dueDate = todo.dueDate
        if (reminderTime == null && dueDate == null) return

        val barWidth = 4 * density
        val chipPaddingH = (10 * density)
        val chipPaddingV = (4 * density)
        // 圆角与卡片圆角严格一致（9dp，与边框外侧圆角视觉一致）
        val cornerRadius = (9f * density)
        // 边框内侧圆角 7.5dp = 8.25dp - stroke/2
        // 用作时间标签 clipPath，让时间标签被边框"切掉"超出部分 → 边框 1.5dp [7.5dp, 9dp] 内完全无时间标签
        val borderInnerRadius = (7.5f * density)

        val textPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#666666")
            isAntiAlias = true
        }
        // 合并两个时间，用"-"连接，去 emoji
        val text = when {
            reminderTime != null && dueDate != null -> {
                "${timeWithYearFormat.format(Date(reminderTime))} - ${timeWithYearFormat.format(Date(dueDate))}"
            }
            reminderTime != null -> {
                timeWithYearFormat.format(Date(reminderTime))
            }
            else -> {
                timeWithYearFormat.format(Date(dueDate!!))
            }
        }
        val textWidth = textPaint.measureText(text)
        val badgeWidth = textWidth + chipPaddingH * 2
        val badgeHeight = (11 * density) + chipPaddingV * 2

        // 位置：卡片左下角，贴边（底边贴卡片外，左边从竖条内侧开始）
        val left = padding + barWidth
        val cardBottom = (cardStartY + contentHeight).toFloat()
        val top = cardBottom - badgeHeight
        val right = left + badgeWidth

        // 用边框内侧（borderInnerRadius 7.5dp）作为 clipPath，让时间标签被边框"切掉"
        // 边框 1.5dp [7.5dp, 9dp] 范围内完全没有时间标签 → 边框完全压住时间标签，不透出
        canvas.save()
        val cardClipPath = android.graphics.Path().apply {
            addRoundRect(
                RectF(padding.toFloat(), cardStartY.toFloat(),
                    (width - padding).toFloat(), (cardStartY + contentHeight).toFloat()),
                borderInnerRadius, borderInnerRadius, android.graphics.Path.Direction.CW
            )
        }
        canvas.clipPath(cardClipPath)

        // 背景路径：仅右上角圆角（与卡片圆角一致 9dp）
        // 关键：Path.arcTo 椭圆边界框必须是 2*cornerRadius × 2*cornerRadius = 18×18
        val bgPath = android.graphics.Path().apply {
            moveTo(left, top)
            lineTo(right - cornerRadius, top)
            arcTo(
                right - 2 * cornerRadius, top,
                right, top + 2 * cornerRadius,
                270f, 90f, false
            )
            lineTo(right, cardBottom)
            lineTo(left, cardBottom)
            lineTo(left, top)
            close()
        }
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FFF0E0")  // 暖色背景
            isAntiAlias = true
        }
        canvas.drawPath(bgPath, bgPaint)

        // 文字
        canvas.drawText(
            text,
            left + chipPaddingH,
            top + chipPaddingV + (11 * density) - (1 * density),
            textPaint
        )

        canvas.restore()
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
     * 布局：分割线 + "来自 刻记⁺ · 让待办变得可爱"
     * - 删除 emoji 柯基图标
     * - "CorgiMemo" 改为 "刻记⁺"（"+" 在右上角，用 Unicode U+207A 上标）
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

        // 水印文字（无 emoji 柯基图标，app 名称用上标 "⁺"）
        val footerPaint = TextPaint().apply {
            textSize = (11 * density)
            color = Color.parseColor("#999999")
            isAntiAlias = true
        }
        val footerText = "来自 刻记\u207A · 让待办变得可爱"
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
