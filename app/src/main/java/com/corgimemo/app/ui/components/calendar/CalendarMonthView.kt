package com.corgimemo.app.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * 日历月份视图（网格日历，支持左右滑动切换月份）
 *
 *
 * 仅包含星期标题行 + 日期网格（6行×7列）。
 * 月份导航已提升至外层 InspirationCalendarDialog 顶部标题行。
 * 在日期网格的 Column 上添加 pointerInput 检测水平滑动：
 * - 向左滑动 → 下个月
 * - 向右滑动 → 上个月
 *
 * @param currentMonth 当前显示的年月
 * @param selectedDate 当前选中的日期
 * @param countMap 当月各日期的灵感条数映射
 * @param onMonthChange 月份切换回调（滑动手势触发）
 * @param onDateSelect 日期选择回调
 * @param dotColorMap 各日期对应的圆点颜色映射（可选）
 *
 * key = day of month，value = 该日期圆点颜色。
 * 不传或对应 key 不存在时，回退到 MaterialTheme.colorScheme.primary（保持原行为）。
 * 用途：日期页弹窗按 DateCategory 颜色区分生日/纪念日/节日。
 */
@Composable
fun CalendarMonthView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    countMap: Map<Int, Int>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    dotColorMap: Map<Int, Color>? = null
) {
    val daysOfWeek = DayOfWeek.entries
    // 当月第一天在网格中的列索引（0-based，周一为首列）
    // dayOfWeek.value: 周一=1, 周二=2, ..., 周日=7，减1得到列索引
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value - 1
    val daysInMonth = currentMonth.lengthOfMonth()
    val prevMonthDays = currentMonth.minusMonths(1).lengthOfMonth()

    // 滑动手势累积偏移量（必须在 @Composable 上下文中声明）
    var totalDragX by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            // 检测水平滑动切换月份：key=currentMonth，每月变化时重新初始化手势检测器
            .pointerInput(currentMonth) {
                detectHorizontalDragGestures(
                    onDragStart = { totalDragX = 0f },
                    onDragEnd = {
                        if (totalDragX < -50f) { // 向左滑动 → 下个月
                            onMonthChange(currentMonth.plusMonths(1))
                        } else if (totalDragX > 50f) { // 向右滑动 → 上个月
                            onMonthChange(currentMonth.minusMonths(1))
                        }
                    }
                ) { _, dragAmount -> totalDragX += dragAmount }
            }
    ) {
        // 星期标题（周一到周日）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, Locale.SIMPLIFIED_CHINESE),
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 日期网格：只渲染到当月最后一天所在行，避免显示多余的下月日期行
        // 例如2026年7月（31天，1号周四）：最后一天31号在第4行（0-based），则只渲染5行
        // 行0前缀的6月日期(28,29,30)和行4后缀的8月1号仍保留灰色显示
        val lastDayRow = (firstDayOfMonth + daysInMonth - 1) / 7
        repeat(lastDayRow + 1) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                repeat(7) { dayIndex ->
                    val itemIndex = weekIndex * 7 + dayIndex
                    val day: Int
                    val isPrevOrNextMonth: Boolean
                    val isSelected: Boolean

                    when {
                        itemIndex < firstDayOfMonth -> {
                            day = prevMonthDays - firstDayOfMonth + itemIndex + 1
                            isPrevOrNextMonth = true
                            isSelected = false
                        }
                        itemIndex >= firstDayOfMonth + daysInMonth -> {
                            day = itemIndex - firstDayOfMonth - daysInMonth + 1
                            isPrevOrNextMonth = true
                            isSelected = false
                        }
                        else -> {
                            day = itemIndex - firstDayOfMonth + 1
                            isPrevOrNextMonth = false
                            isSelected = selectedDate.year == currentMonth.year &&
                                    selectedDate.monthValue == currentMonth.monthValue &&
                                    selectedDate.dayOfMonth == day
                        }
                    }

                    val hasInspiration = countMap.containsKey(day) && !isPrevOrNextMonth
                    val textColor = when {
                        isPrevOrNextMonth -> Color(0xFFCCCCCC)
                        isSelected -> Color.White
                        else -> Color(0xFF2D2D2D)
                    }
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .size(width = 40.dp, height = 34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable {
                                if (!isPrevOrNextMonth) {
                                    onDateSelect(
                                        LocalDate.of(
                                            currentMonth.year,
                                            currentMonth.monthValue,
                                            day
                                        )
                                    )
                                }
                            },
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = day.toString(),
                            fontSize = 14.sp,
                            color = textColor
                        )
                        // 灵感条数圆点
                        if (hasInspiration) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(
                                        color = if (isSelected) Color.White
                                                else dotColorMap?.get(day) ?: MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
            if (weekIndex < 5) {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
