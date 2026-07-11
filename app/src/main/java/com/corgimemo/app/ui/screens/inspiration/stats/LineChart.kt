package com.corgimemo.app.ui.screens.inspiration.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.stats.DailyWordCount
import java.time.format.DateTimeFormatter

/**
 * 折线图组件（用于累计总字数展示）
 *
 * 视觉规范：
 * - 图表区高度 200dp
 * - 内边距：左 36dp（Y轴标签）、右 12dp、上 16dp、下 28dp（X轴标签）
 * - 折线 primary 2dp，5dp 数据点（最后点 6dp + 12dp 光晕）
 * - 折线下方 primary 30%→0% 垂直渐变填充
 * - 横向虚线网格 #EEEEEE 1dp
 *
 * @param points 数据点列表
 * @param modifier Modifier
 */
@Composable
fun LineChart(
    points: List<DailyWordCount>,
    modifier: Modifier = Modifier
) {
    // 无数据时不绘制
    if (points.isEmpty()) return

    // 读取主题色与基础样式
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = Color(0xFFEEEEEE)
    // 标签格式：M/d（无前导零），7 天全部展示无重叠，30 天隔位仍清晰
    val labelFormatter = DateTimeFormatter.ofPattern("M/d")

    // 计算 Y 轴最大值：取累计最大值的 1.2 倍，向上取整到 10 的倍数
    val maxValue = points.maxOf { it.cumulativeChars }.coerceAtLeast(1)
    val yMax = ((maxValue * 1.2f).toInt() + 9) / 10 * 10
    // Y 轴刻度数量
    val yTicks = 5

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // 计算绘图区矩形
        val plotLeft = 36.dp.toPx()
        val plotRight = size.width - 12.dp.toPx()
        val plotTop = 16.dp.toPx()
        val plotBottom = size.height - 28.dp.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        // 1. 绘制 Y 轴刻度与横向网格线
        val yLabelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#999999")
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        for (i in 0..yTicks) {
            val ratio = i.toFloat() / yTicks
            val y = plotBottom - plotHeight * ratio
            // 虚线网格
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(4.dp.toPx(), 4.dp.toPx())
                )
            )
            // Y 轴刻度文字
            val labelValue = (yMax * ratio).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                labelValue.toString(),
                plotLeft - 6.dp.toPx(),
                y + 4.dp.toPx(),
                yLabelPaint
            )
        }

        if (points.isEmpty()) return@Canvas

        // 2. 计算每个数据点对应的屏幕坐标
        val stepX = plotWidth / (points.size - 1).coerceAtLeast(1)
        val coords = points.mapIndexed { index, point ->
            val x = plotLeft + stepX * index
            val ratio = point.cumulativeChars.toFloat() / yMax
            val y = plotBottom - plotHeight * ratio
            Offset(x, y)
        }

        // 3. 绘制折线下方渐变面积
        val areaPath = Path().apply {
            moveTo(coords.first().x, plotBottom)
            coords.forEach { lineTo(it.x, it.y) }
            lineTo(coords.last().x, plotBottom)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    primary.copy(alpha = 0.3f),
                    primary.copy(alpha = 0f)
                ),
                startY = plotTop,
                endY = plotBottom
            )
        )

        // 4. 绘制折线
        val linePath = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            coords.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.dp.toPx())
        )

        // 5. 绘制数据点（最后一点带光晕高亮）
        coords.forEachIndexed { index, offset ->
            val isLast = index == coords.lastIndex
            if (isLast) {
                // 外圈光晕
                drawCircle(
                    color = primary.copy(alpha = 0.2f),
                    radius = 12.dp.toPx(),
                    center = offset
                )
            }
            // 白底
            drawCircle(
                color = Color.White,
                radius = if (isLast) 6.dp.toPx() else 5.dp.toPx(),
                center = offset
            )
            // 主题色实心点
            drawCircle(
                color = primary,
                radius = if (isLast) 4.dp.toPx() else 3.5.dp.toPx(),
                center = offset
            )
        }

        // 6. 绘制 X 轴日期标签
        // 7 天：全部显示（4字符 M/d 格式无重叠）
        // 30 天：每隔一天显示（15 个标签，避免拥挤）
        val xLabelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#999999")
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val showAll = points.size <= 7
        points.forEachIndexed { index, point ->
            // 7 天全部展示；30 天仅展示偶数索引（0, 2, 4, ..., 28）
            if (showAll || index % 2 == 0) {
                val x = plotLeft + stepX * index
                val label = point.date.format(labelFormatter)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    plotBottom + 18.dp.toPx(),
                    xLabelPaint
                )
            }
        }
    }
}
