package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.MoodHistory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 情绪历史折线图组件
 * 显示近7日情绪值趋势
 *
 * @param historyList 情绪历史记录列表（已按日期升序排列）
 * @param modifier 修饰符
 */
@Composable
fun MoodHistoryChart(
    historyList: List<MoodHistory>,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 800),
        label = "chartAnimation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "近7日情绪趋势",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (historyList.isEmpty()) {
                Text(
                    text = "暂无数据",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            if (historyList.isEmpty()) {
                // 空数据占位
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "📊",
                        fontSize = 32.sp
                    )
                    Text(
                        text = "每日自动记录情绪值",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                // 图表 Canvas
                MoodChartCanvas(
                    historyList = historyList,
                    animatedProgress = animatedProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                // X 轴日期标签
                MoodChartXAxis(
                    historyList = historyList,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

/**
 * 情绪折线图 Canvas 绘制
 *
 * @param historyList 情绪历史列表
 * @param animatedProgress 动画进度（0-1）
 * @param modifier 修饰符
 */
@Composable
private fun MoodChartCanvas(
    historyList: List<MoodHistory>,
    animatedProgress: Float,
    modifier: Modifier = Modifier
) {
    // 构建近7天的数据点
    val dataPoints = remember(historyList) {
        build7DayDataPoints(historyList)
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val paddingBottom = 4.dp.toPx()
        val paddingTop = 8.dp.toPx()
        val chartHeight = height - paddingBottom - paddingTop
        val chartWidth = width - 8.dp.toPx()

        // 绘制背景网格线（25/50/75/100）
        val gridLines = listOf(0f, 25f, 50f, 75f, 100f)
        gridLines.forEach { value ->
            val y = paddingTop + chartHeight * (1 - value / 100f)
            drawLine(
                color = Color(0x22000000),
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // 计算每个数据点的坐标
        val pointCount = 7
        val xStep = chartWidth / (pointCount - 1)

        val validPoints = dataPoints.mapIndexed { index, moodValue ->
            val x = xStep * index
            val y = if (moodValue != null) {
                paddingTop + chartHeight * (1 - moodValue / 100f) * animatedProgress
            } else {
                null
            }
            ChartPoint(index, x, y, moodValue)
        }

        // 过滤有效点（有数据的点）
        val validDataPoints = validPoints.filter { it.y != null && it.moodValue != null }

        if (validDataPoints.size >= 2) {
            // 绘制折线和填充区域
            val path = Path()
            val fillPath = Path()

            val firstPoint = validDataPoints.first()
            path.moveTo(firstPoint.x, firstPoint.y!!)
            fillPath.moveTo(firstPoint.x, paddingTop + chartHeight)
            fillPath.lineTo(firstPoint.x, firstPoint.y)

            for (i in 1 until validDataPoints.size) {
                val prev = validDataPoints[i - 1]
                val curr = validDataPoints[i]

                // 贝塞尔曲线（平滑折线）
                val controlX1 = prev.x + (curr.x - prev.x) * 0.4f
                val controlY1 = prev.y!!
                val controlX2 = prev.x + (curr.x - prev.x) * 0.6f
                val controlY2 = curr.y!!

                path.cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, curr.y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, curr.x, curr.y)
            }

            val lastPoint = validDataPoints.last()
            fillPath.lineTo(lastPoint.x, paddingTop + chartHeight)
            fillPath.close()

            // 填充区域（渐变）
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x334CAF50),
                        Color(0x004CAF50)
                    ),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight
                )
            )

            // 绘制折线
            drawPath(
                path = path,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF81C784),
                        Color(0xFF4CAF50)
                    )
                ),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }

        // 绘制数据点
        dataPoints.forEachIndexed { index, moodValue ->
            val x = xStep * index

            if (moodValue != null) {
                val y = paddingTop + chartHeight * (1 - moodValue / 100f) * animatedProgress

                // 点的光晕
                drawCircle(
                    color = getMoodColor(moodValue).copy(alpha = 0.3f),
                    radius = 8.dp.toPx(),
                    center = Offset(x, y)
                )

                // 数据点
                drawCircle(
                    color = getMoodColor(moodValue),
                    radius = 5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

/**
 * X 轴日期标签
 *
 * @param historyList 情绪历史列表
 * @param modifier 修饰符
 */
@Composable
private fun MoodChartXAxis(
    historyList: List<MoodHistory>,
    modifier: Modifier = Modifier
) {
    val dateFormatter = DateTimeFormatter.ofPattern("dd")
    val today = LocalDate.now()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(7) { index ->
            val daysAgo = 6 - index
            val dateLabel = today.minusDays(daysAgo.toLong()).format(dateFormatter)

            // 检查该日期是否有数据
            val dateStr = today.minusDays(daysAgo.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val hasData = historyList.any { it.date == dateStr }

            Text(
                text = dateLabel,
                fontSize = 10.sp,
                color = if (hasData) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )
        }
    }
}

/**
 * 图表数据点
 */
private data class ChartPoint(
    val index: Int,
    val x: Float,
    val y: Float?,
    val moodValue: Int?
)

/**
 * 构建近7天的数据点
 * 按日期从远到近排列（index 0 = 7天前，index 6 = 今天）
 * 缺失日期填充 null
 *
 * @param historyList 情绪历史列表（已按日期升序）
 * @return 近7天的情绪值数组
 */
private fun build7DayDataPoints(historyList: List<MoodHistory>): List<Int?> {
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val historyMap = historyList.associate { it.date to it.moodValue }

    return (6 downTo 0).map { daysAgo ->
        val date = today.minusDays(daysAgo.toLong()).format(formatter)
        historyMap[date]
    }
}

/**
 * 根据情绪值获取颜色
 *
 * @param moodValue 情绪值（0-100）
 * @return 颜色
 */
private fun getMoodColor(moodValue: Int): Color {
    return when {
        moodValue >= 80 -> Color(0xFF4CAF50)
        moodValue >= 60 -> Color(0xFF8BC34A)
        moodValue >= 40 -> Color(0xFFFFC107)
        moodValue >= 20 -> Color(0xFFFF5722)
        else -> Color(0xFFF44336)
    }
}
