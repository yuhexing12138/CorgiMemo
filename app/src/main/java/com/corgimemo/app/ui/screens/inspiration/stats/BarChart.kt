package com.corgimemo.app.ui.screens.inspiration.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.stats.DailyWordCount
import java.time.format.DateTimeFormatter

/**
 * 柱状图组件（用于每日输入字数展示）
 *
 * 视觉规范：
 * - 图表区高度 200dp
 * - 内边距：左 36dp、右 12dp、上 16dp、下 28dp
 * - 柱形宽度：7 天 24dp；30 天 8dp
 * - 柱形颜色：当日柱 primary；其他柱 outlineVariant
 * - 柱顶数值：仅当值 > 0 时显示，10sp #666666
 * - 今日柱 100% 不透明；其他柱 80% 透明度
 *
 * @param points 数据点列表
 * @param modifier Modifier
 */
@Composable
fun BarChart(
    points: List<DailyWordCount>,
    modifier: Modifier = Modifier
) {
    // 无数据时不绘制
    if (points.isEmpty()) return

    // 读取主题色与基础样式
    val primary = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = Color(0xFFEEEEEE)
    // 标签格式：M/d（无前导零），7 天全部展示无重叠，30 天隔位仍清晰
    val labelFormatter = DateTimeFormatter.ofPattern("M/d")

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        // 计算 Y 轴最大值：取当日字数最大值的 1.2 倍，向上取整到 10 的倍数
        val maxValue = points.maxOf { it.dailyChars }.coerceAtLeast(1)
        val yMax = ((maxValue * 1.2f).toInt() + 9) / 10 * 10
        // Y 轴刻度数量
        val yTicks = 5
        // 柱形宽度：数据点 <=7 时 24dp；否则（30 天）8dp
        val barWidth = if (points.size <= 7) 24.dp.toPx() else 8.dp.toPx()
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
                pathEffect = PathEffect.dashPathEffect(
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

        // 2. 计算柱形位置（每个数据点居中分布于一个等分单元中）
        val stepX = plotWidth / points.size
        val valuePaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#666666")
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }
        points.forEachIndexed { index, point ->
            val centerX = plotLeft + stepX * (index + 0.5f)
            val ratio = point.dailyChars.toFloat() / yMax
            // 柱形高度按比例计算，最小为 0
            val barHeight = (plotHeight * ratio).coerceAtLeast(0f)
            val barTop = plotBottom - barHeight
            // 最后一日高亮（主题色 + 不透明），其他日次级（outlineVariant + 80% 透明）
            val isLast = index == points.lastIndex
            val color = if (isLast) primary else barColor
            val alpha = if (isLast) 1f else 0.8f

            // 仅当字数 > 0 时绘制柱形与柱顶数值
            if (point.dailyChars > 0) {
                // 绘制圆角柱形
                drawRoundRect(
                    color = color.copy(alpha = alpha),
                    topLeft = Offset(centerX - barWidth / 2, barTop),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
                // 柱顶数值
                drawContext.canvas.nativeCanvas.drawText(
                    point.dailyChars.toString(),
                    centerX,
                    barTop - 4.dp.toPx(),
                    valuePaint
                )
            }
        }

        // 3. 绘制 X 轴日期标签
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
                val centerX = plotLeft + stepX * (index + 0.5f)
                val label = point.date.format(labelFormatter)
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    centerX,
                    plotBottom + 18.dp.toPx(),
                    xLabelPaint
                )
            }
        }
    }
}
