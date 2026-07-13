package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

/**
 * 单月小日历(日历样式专用)
 *
 * 固定显示一个月,无月份导航。目标日用红色圆圈高亮。
 * 上月/下月填充日用浅灰显示。
 *
 * @param year 年份
 * @param month 月份(1-12)
 * @param targetDay 目标日(高亮显示;0 表示不高亮)
 * @param textSize 日期数字字号
 * @param weekHeaderTextSize 星期表头字号(默认等于 textSize * 0.9)
 * @param targetRingColor 目标日圆圈描边颜色(默认 #FFFF8A80 现有红色;由 DateCardColor 透传)
 * @param targetTextColor 目标日数字颜色(默认主题 primary 橙红色;由 DateCardColor 透传,与圆圈描边同色)
 * @param showDividers 是否显示分割虚线
 * @param dividerColor 分割线颜色
 */
@Composable
fun MiniCalendar(
    year: Int,
    month: Int,
    targetDay: Int,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    weekHeaderTextSize: TextUnit = TextUnit.Unspecified,
    targetRingColor: Color = Color(0xFFFF8A80),
    targetTextColor: Color = Color.Unspecified,
    showDividers: Boolean = true,
    dividerColor: Color = Color(0xFFCCCCCC)
) {
    val effectiveWeekHeaderTextSize = if (weekHeaderTextSize != TextUnit.Unspecified) {
        weekHeaderTextSize
    } else {
        12.sp
    }
    // 1 号是星期几(0=周一 ... 6=周日),用于计算上月填充日数
    val firstDayOfWeek = (YearMonth.of(year, month).atDay(1).dayOfWeek.value - 1 + 7) % 7
    val daysInMonth = YearMonth.of(year, month).lengthOfMonth()
    val prevMonth = YearMonth.of(year, month).minusMonths(1)
    val prevMonthDays = prevMonth.lengthOfMonth()

    // 构建 5x7 网格数据 Triple<day, isPrevOrNextMonth, isTarget>
    val gridItems = mutableListOf<Triple<Int, Boolean, Boolean>>()
    for (i in firstDayOfWeek downTo 1) {
        gridItems.add(Triple(prevMonthDays - i + 1, true, false))
    }
    for (day in 1..daysInMonth) {
        gridItems.add(Triple(day, false, day == targetDay))
    }
    var nextDay = 1
    while (gridItems.size < 35) {
        gridItems.add(Triple(nextDay++, true, false))
    }

    Column(modifier = modifier) {
        // 星期表头(一二三四五六日)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { dow ->
                Text(
                    text = dow,
                    fontSize = effectiveWeekHeaderTextSize,
                    color = Color(0xFF999999),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        if (showDividers) {
            Canvas(modifier = Modifier.fillMaxWidth()) {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        // 5 行日期
        repeat(5) { weekIndex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(7) { dayIndex ->
                    val (day, isPrevOrNext, isTarget) = gridItems[weekIndex * 7 + dayIndex]
                    val textColor = when {
                        isTarget -> MaterialTheme.colorScheme.primary
                        isPrevOrNext -> Color(0xFFCCCCCC)
                        else -> Color(0xFF2D2D2D)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isTarget) Modifier.border(
                                    width = 1.5.dp,
                                    color = targetRingColor,
                                    shape = CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = textSize,
                            // 目标日数字颜色:未指定时用主题 primary(原行为),指定时用参数值
                            color = if (isTarget && targetTextColor != Color.Unspecified) {
                                targetTextColor  // 跟随 cardColor
                            } else {
                                textColor
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            if (showDividers && weekIndex < 4) {
                Spacer(Modifier.height(2.dp))
                Canvas(modifier = Modifier.fillMaxWidth()) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}
