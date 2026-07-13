package com.corgimemo.app.ui.screens.date.components.cardstyle

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
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
 * @param textSize 单元格数字字号(默认 14sp)
 * @param targetRingColor 目标日圆圈描边颜色(默认 #FFFF8A80 现有红色;由 DateCardColor 透传)
 */
@Composable
fun MiniCalendar(
    year: Int,
    month: Int,
    targetDay: Int,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 14.sp,
    targetRingColor: Color = Color(0xFFFF8A80)  // ← 新增,默认保持现有红色
) {
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
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        // 5 行日期
        repeat(5) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
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
                            .height(if (textSize == 14.sp) 24.dp else 16.dp)
                            .then(
                                if (isTarget) Modifier.border(
                                    width = 1.5.dp,
                                    color = targetRingColor,  // ← 改用参数(默认 #FFFF8A80)
                                    shape = CircleShape
                                ) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = textSize,
                            color = textColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
