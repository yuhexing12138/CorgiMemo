package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/**
 * 灵感日历弹窗
 * 上方显示日历（带灵感条数圆点），下方显示选中日期的灵感列表
 *
 * @param initialDate 初始选中日期
 * @param inspirationCountByDate 日期 -> 灵感条数的映射（用于显示圆点）
 * @param getInspirationsByDate 获取指定日期灵感列表的回调
 * @param onInspirationClick 点击灵感条目回调
 * @param onDismiss 关闭回调
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InspirationCalendarDialog(
    initialDate: LocalDate = LocalDate.now(),
    inspirationCountByDate: (year: Int, month: Int) -> Map<Int, Int>,
    getInspirationsByDate: (year: Int, month: Int, day: Int) -> List<Inspiration>,
    onInspirationClick: (Inspiration) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    // 当前选中的日期
    var selectedDate by remember { mutableStateOf(initialDate) }
    // 当前显示的月份
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    // 当前选中日期的灵感列表
    var dayInspirations by remember { mutableStateOf<List<Inspiration>>(emptyList()) }
    // 当前月份的灵感计数
    var monthCountMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }

    // 月份变化时更新计数
    LaunchedEffect(currentMonth) {
        monthCountMap = inspirationCountByDate(currentMonth.year, currentMonth.monthValue)
    }

    // 选中日期变化时更新当天灵感列表
    LaunchedEffect(selectedDate) {
        dayInspirations = getInspirationsByDate(
            selectedDate.year,
            selectedDate.monthValue,
            selectedDate.dayOfMonth
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismiss()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "选择日期",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp)) // 占位，保持标题居中
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 日历部分
            CalendarMonthView(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                countMap = monthCountMap,
                onPreviousMonth = { currentMonth = currentMonth.minusMonths(1) },
                onNextMonth = { currentMonth = currentMonth.plusMonths(1) },
                onDateSelect = { date ->
                    selectedDate = date
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 选中日期信息栏
            val weekdayText = remember(selectedDate, dayInspirations.size) {
                val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
                val cal = Calendar.getInstance().apply {
                    set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
                }
                "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 周${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}  共 ${dayInspirations.size} 条灵感"
            }
            Text(
                text = weekdayText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 分割线
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFEEEEEE))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 当天灵感列表
            if (dayInspirations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "今天还没有灵感记录~",
                        fontSize = 14.sp,
                        color = Color(0xFF999999)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = dayInspirations,
                        key = { it.id }
                    ) { inspiration ->
                        CalendarInspirationItem(
                            inspiration = inspiration,
                            onClick = { onInspirationClick(inspiration) }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * 日历月份视图
 */
@Composable
private fun CalendarMonthView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    countMap: Map<Int, Int>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelect: (LocalDate) -> Unit
) {
    val daysOfWeek = DayOfWeek.entries
    val firstDayOfMonth = (currentMonth.atDay(1).dayOfWeek.value % 7)
    val daysInMonth = currentMonth.lengthOfMonth()
    val prevMonthDays = currentMonth.minusMonths(1).lengthOfMonth()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // 月份导航
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上个月",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "${currentMonth.year}年${currentMonth.monthValue}月",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下个月",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

        // 日期网格（6行，覆盖所有可能情况）
        repeat(6) { weekIndex ->
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
                            .size(40.dp)
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
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.primary,
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

/**
 * 日历中的灵感条目
 */
@Composable
private fun CalendarInspirationItem(
    inspiration: Inspiration,
    onClick: () -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
    val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = timeStr,
            fontSize = 13.sp,
            color = Color(0xFF999999),
            modifier = Modifier.width(52.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = inspiration.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (inspiration.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = removeHtmlTags(inspiration.content).take(50),
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 去除HTML标签
 */
private fun removeHtmlTags(html: String): String {
    return html
        .replace("<[^>]*>".toRegex(), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}
