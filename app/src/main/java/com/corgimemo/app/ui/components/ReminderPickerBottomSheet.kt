package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 提醒设置底部弹窗（完整版）
 *
 * 包含：日期选择、时间选择、重复提醒、日历开关
 * 交互方式与设计图一致：
 * - 点击日期芯片 → 显示日历视图
 * - 点击时间芯片 → 显示时间滚轮视图
 * - 点击重复提醒 → 下拉选项列表
 * - 底部取消/确定按钮
 */
@Composable
fun ReminderPickerBottomSheet(
    initialDateMillis: Long? = null,
    initialHour: Int = 13,
    initialMinute: Int = 35,
    initialRepeatType: Int = 0,
    initialCalendarEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (dateMillis: Long?, hour: Int, minute: Int, repeatType: Int, calendarEnabled: Boolean) -> Unit
) {
    // 当前选中的日期和时间
    val now = System.currentTimeMillis()
    val initLocalDate = if (initialDateMillis != null) {
        java.time.Instant.ofEpochMilli(initialDateMillis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
    } else {
        LocalDate.now()
    }
    var selectedDate by remember { mutableStateOf(initLocalDate) }
    var selectedHour by remember { mutableIntStateOf(initialHour) }
    var selectedMinute by remember { mutableIntStateOf(initialMinute) }

    // 视图模式：calendar=日历, time=时间滚轮
    var viewMode by remember { mutableStateOf("calendar") }

    // 重复提醒
    var repeatType by remember { mutableIntStateOf(initialRepeatType) }
    var showRepeatMenu by remember { mutableStateOf(false) }

    // 日历开关
    var calendarEnabled by remember { mutableStateOf(initialCalendarEnabled) }

    // 月份导航
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    // 日历展开/收起状态（默认展开）
    var isCalendarExpanded by remember { mutableStateOf(true) }

    // 重复类型选项
    val repeatOptions = listOf(
        Pair(0, "不重复"),
        Pair(1, "每天"),
        Pair(4, "周一至周五"),
        Pair(2, "每周"),
        Pair(3, "每月"),
        Pair(5, "每年")
    )

    // 主内容区 + 浮动弹窗层（重复提醒选择）
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 限制弹窗最大高度：确保 6 行日历完整显示 + 上部区域 + 底部按钮
                .heightIn(max = 700.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(horizontal = 24.dp)
        ) {
        // 标题
        Text(
            text = "设置提醒时间",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp)
        )

        // ===== 提醒时间行 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "提醒时间",
                fontSize = 15.sp,
                color = Color(0xFF2D2D2D),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))

            // 日期芯片
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (viewMode == "calendar") MaterialTheme.colorScheme.primary else Color(0xFFFFE4CC))
                    .clickable { viewMode = "calendar" }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedDate.year}/${String.format("%02d", selectedDate.monthValue)}/${String.format("%02d", selectedDate.dayOfMonth)}",
                    fontSize = 13.sp,
                    color = if (viewMode == "calendar") Color.White else Color(0xFF666666),
                    fontWeight = if (viewMode == "calendar") FontWeight.Medium else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 时间芯片
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (viewMode == "time") MaterialTheme.colorScheme.primary else Color(0xFFFFE4CC))
                    .clickable { viewMode = "time" }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${String.format("%02d", selectedHour)}:${String.format("%02d", selectedMinute)}",
                    fontSize = 13.sp,
                    color = if (viewMode == "time") Color.White else Color(0xFF666666),
                    fontWeight = if (viewMode == "time") FontWeight.Medium else FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 重复提醒行（点击弹出下拉选择菜单） =====
        // 使用 Box 包裹以获取按钮位置信息，用于定位下拉弹窗
        var repeatButtonPosition by remember { mutableStateOf(androidx.compose.ui.geometry.IntOffset.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    repeatButtonPosition = coordinates.positionInParent()
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRepeatMenu = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "重复提醒",
                    fontSize = 15.sp,
                    color = Color(0xFF2D2D2D),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = repeatOptions.firstOrNull { it.first == repeatType }?.second ?: "不重复",
                    fontSize = 14.sp,
                    color = Color(0xFF999999)
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFFCCCCCC),
                    modifier = Modifier.size(18.dp)
                )
            }

            // ===== 下拉选择菜单（无遮罩，定位在按钮左下方） =====
            if (showRepeatMenu) {
                androidx.compose.ui.window.Popup(
                    alignment = Alignment.TopStart,
                    offset = androidx.compose.ui.unit.IntOffset(
                        x = repeatButtonPosition.x - 40.dp.roundToPx(),  // 向左偏移，对齐右侧
                        y = repeatButtonPosition.y + 36.dp.roundToPx()   // 按钮下方
                    ),
                    onDismissRequest = { showRepeatMenu = false }
                ) {
                    Column(
                        modifier = Modifier
                            .width(200.dp)
                            .shadow(8.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(vertical = 4.dp)
                    ) {
                        repeatOptions.forEach { (type, name) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        repeatType = type
                                        showRepeatMenu = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 15.sp,
                                    color = if (repeatType == type) MaterialTheme.colorScheme.primary else Color(0xFF2D2D2D)
                                )
                                if (repeatType == type) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 农历行 =====
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "农历",
                fontSize = 15.sp,
                color = Color(0xFF2D2D2D),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = calendarEnabled,
                onCheckedChange = { calendarEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFFFFF),
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color(0xFFFFFFFF),
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                ),
                modifier = Modifier.height(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE))
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 内容区域：日期滚轮 或 时间滚轮（弹性填充剩余空间） =====
        when (viewMode) {
            "calendar" -> DateWheelPickerView(
                selectedDate = selectedDate,
                onDateChange = { newDate ->
                    selectedDate = newDate
                    currentMonth = YearMonth.from(newDate)
                },
                isExpanded = isCalendarExpanded,
                onToggleExpand = { isCalendarExpanded = !isCalendarExpanded },
                modifier = Modifier.weight(1f)
            )
            "time" -> TimeWheelView(
                hour = selectedHour,
                minute = selectedMinute,
                onHourChange = { selectedHour = it.coerceIn(0, 23) },
                onMinuteChange = { selectedMinute = it.coerceIn(0, 59) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ===== 底部按钮行 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 取消
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFFF8F6F3))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "取消",
                    fontSize = 15.sp,
                    color = Color(0xFF666666)
                )
            }

            // 确定
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        val zonedDateTime = selectedDate.atTime(selectedHour, selectedMinute)
                            .atZone(java.time.ZoneId.systemDefault())
                        onConfirm(
                            zonedDateTime.toInstant().toEpochMilli(),
                            selectedHour,
                            selectedMinute,
                            repeatType,
                            calendarEnabled
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "确定",
                    fontSize = 15.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } // Column 结束
} // 外层 Box 结束
} // ReminderPickerBottomSheet 函数结束

// ==================== 日历视图（双模式：网格日历 ↔ 滚轮选择器） ====================

/**
 * 双模式日期选择器
 *
 * 默认显示**网格日历视图**（图2：月份导航 + 日期网格），
 * 点击标题行切换为**年/月/日滚轮选择器**（图3：三列垂直滚轮）。
 * 再次点击标题切回日历网格视图。
 */
@Composable
private fun DateWheelPickerView(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 视图模式："calendar" 网格日历 / "wheel" 滚轮选择器
    var dateViewMode by remember { mutableStateOf("calendar") }

    val currentYear = selectedDate.year
    val currentMonth = selectedDate.monthValue
    val currentDay = selectedDate.dayOfMonth

    Column(modifier = modifier) {
        // ===== 标题行：YYYY/MM + 模式切换箭头（仅滚轮模式显示，日历模式由 CalendarGridView 自带导航栏） =====
        if (dateViewMode == "wheel") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dateViewMode = "calendar" }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${currentYear}/${String.format("%02d", currentMonth)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D2D2D)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "▲",
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
        }

        // ===== 内容区域：根据模式显示不同视图（带动画过渡） =====
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically(
                expandFrom = androidx.compose.ui.Alignment.Top
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(
                shrinkTowards = androidx.compose.ui.Alignment.Top
            ) + androidx.compose.animation.fadeOut()
        ) {
            when (dateViewMode) {
                "calendar" -> CalendarGridView(
                    selectedDate = selectedDate,
                    onDateSelect = onDateChange,
                    onSwitchToWheel = { dateViewMode = "wheel" },
                    modifier = Modifier.fillMaxSize()
                )
                "wheel" -> DateWheelContent(
                    year = currentYear,
                    month = currentMonth,
                    day = currentDay,
                    onYearChange = { newYear ->
                        val maxDay = java.time.YearMonth.of(newYear, currentMonth).lengthOfMonth()
                        val validDay = minOf(currentDay, maxDay)
                        onDateChange(LocalDate.of(newYear, currentMonth, validDay))
                    },
                    onMonthChange = { newMonth ->
                        val maxDay = java.time.YearMonth.of(currentYear, newMonth).lengthOfMonth()
                        val validDay = minOf(currentDay, maxDay)
                        onDateChange(LocalDate.of(currentYear, newMonth, validDay))
                    },
                    onDayChange = { newDay ->
                        onDateChange(LocalDate.of(currentYear, currentMonth, newDay))
                    },
                    modifier = Modifier.fillMaxSize()
                )
                else -> {}
            }
        }
    }
}

/**
 * 网格日历视图（图2样式）：月份导航栏 + 星期标题 + 日期网格
 */
@Composable
private fun CalendarGridView(
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    onSwitchToWheel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val daysOfWeek = DayOfWeek.entries.take(7)
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    Column(modifier = modifier) {
        // 月份导航栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = "上个月",
                tint = Color(0xFF999999),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { currentMonth = currentMonth.minusMonths(1) }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${currentMonth.year}/${String.format("%02d", currentMonth.monthValue)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2D2D2D),
                modifier = Modifier.clickable { onSwitchToWheel() }
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "下个月",
                tint = Color(0xFF999999),
                modifier = Modifier
                    .size(24.dp)
                    .clickable { currentMonth = currentMonth.plusMonths(1) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 星期标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            daysOfWeek.forEach { dow ->
                Text(
                    text = when (dow) {
                        DayOfWeek.MONDAY -> "一"
                        DayOfWeek.TUESDAY -> "二"
                        DayOfWeek.WEDNESDAY -> "三"
                        DayOfWeek.THURSDAY -> "四"
                        DayOfWeek.FRIDAY -> "五"
                        DayOfWeek.SATURDAY -> "六"
                        DayOfWeek.SUNDAY -> "日"
                        else -> ""
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 日期网格（弹性填充剩余空间）
        val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7
        val daysInMonth = currentMonth.lengthOfMonth()
        val prevMonthDays = currentMonth.minusMonths(1).lengthOfMonth()

        val gridItems = mutableListOf<Triple<Int, Boolean, Boolean>>()
        for (i in firstDayOfMonth downTo 1) {
            gridItems.add(Triple(prevMonthDays - i + 1, true, false)) // 上月日期（灰色）
        }
        for (day in 1..daysInMonth) {
            gridItems.add(Triple(day, false, day == selectedDate.dayOfMonth && currentMonth == YearMonth.from(selectedDate)))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            items(gridItems) { (day, isPrevMonth, isSelected) ->
                val textColor = when {
                    isPrevMonth -> Color(0xFFCCCCCC)
                    isSelected -> Color.White
                    else -> Color(0xFF2D2D2D)
                }
                val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(bgColor)
                        .clickable(enabled = !isPrevMonth) { onDateSelect(currentMonth.atDay(day)) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$day",
                        fontSize = 14.sp,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 日期滚轮内容区：年/月/日三列滚轮（复用 WheelColumn）
 */
@Composable
private fun DateWheelContent(
    year: Int,
    month: Int,
    day: Int,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 列标题行（与滚轮列宽度对齐）
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(text = "年", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "月", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "日", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
    }

    Spacer(modifier = Modifier.height(8.dp))

    // 三列滚轮（居中对齐）
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 年份滚轮（范围：当前年份前后各 5 年）
        val yearRange = (year - 5)..(year + 5)
        WheelColumn(
            items = yearRange.toList(),
            selectedIndex = year - yearRange.first,
            onSelect = { index -> onYearChange(yearRange.elementAt(index)) },
            formatter = { "$it" },
            itemHeight = 44.dp,
            visibleItemCount = 3,
            modifier = Modifier.width(72.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 月份滚轮（1-12）
        WheelColumn(
            items = (1..12).toList(),
            selectedIndex = month - 1,
            onSelect = { index -> onMonthChange(index + 1) },
            formatter = { String.format("%02d", it) },
            itemHeight = 44.dp,
            visibleItemCount = 3,
            modifier = Modifier.width(56.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 日期滚轮（1-当月天数）
        val daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth()
        WheelColumn(
            items = (1..daysInMonth).toList(),
            selectedIndex = day - 1,
            onSelect = { index -> onDayChange(index + 1) },
            formatter = { String.format("%02d", it) },
            itemHeight = 44.dp,
            visibleItemCount = 3,
            modifier = Modifier.width(56.dp)
        )
    }
}

// ==================== 时间滚轮视图 ====================

/**
 * 24小时制时间滚轮选择器
 *
 * 使用 LazyColumn + rememberLazyListState 实现真正的惯性滚动选择器，
 * 用户可通过上下滑动设置时和分，无上午/下午切换。
 */
@Composable
private fun TimeWheelView(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 每个选项的高度（dp）
    val itemHeightDp = 48.dp
    // 可见行数（奇数，中间行为选中项）
    val visibleItemCount = 5

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "时" 列标题（与滚轮同宽，文字居中）
        Text(
            text = "时",
            fontSize = 13.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 小时滚轮（00-23，共24项）
        WheelColumn(
            items = (0..23).toList(),
            selectedIndex = hour.coerceIn(0, 23),
            onSelect = { onHourChange(it) },
            formatter = { String.format("%02d", it) },
            itemHeight = itemHeightDp,
            visibleItemCount = visibleItemCount,
            modifier = Modifier.width(64.dp)
        )

        // 冒号分隔符（固定宽度居中）
        Text(
            text = ":",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(16.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 分钟滚轮（00-59，共60项）
        WheelColumn(
            items = (0..59).toList(),
            selectedIndex = minute.coerceIn(0, 59),
            onSelect = { onMinuteChange(it) },
            formatter = { String.format("%02d", it) },
            itemHeight = itemHeightDp,
            visibleItemCount = visibleItemCount,
            modifier = Modifier.width(64.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // "分" 列标题（与滚轮同宽，文字居中）
        Text(
            text = "分",
            fontSize = 13.sp,
            color = Color(0xFF999999),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )
    }
}

/**
 * 单列滚轮选择器组件（iOS 风格）
 *
 * 特性：
 * - 循环滚动：滑到首项继续滑回到末项，反之亦然
 * - 惯性吸附：滚动停止后自动吸附到最近选项
 * - 渐变透明度：越远离中心行越透明
 * - 触觉反馈：选中项切换时触发轻微震动
 */
@Composable
private fun <T> WheelColumn(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    formatter: (T) -> String,
    itemHeight: Dp,
    visibleItemCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val totalHeight = itemHeight * visibleItemCount
    // 中心行的偏移索引（用于渐变透明度和选中高亮计算）
    val centerOffset = visibleItemCount / 2

    // ===== 循环滚动：将列表扩展为 3 倍长度，实现无缝循环 =====
    // 扩展列表 = [原始...原始...原始]，中间段对应实际数据
    val repeatCount = 3
    val expandedItems = items.repeat(repeatCount)
    // 初始位置定位到中间段的选中项（使其居中显示）
    val initialIndex = selectedIndex + items.size  // 中间段起始 + 选中偏移

    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = initialIndex
    )

    // 跟踪是否正在滚动，用于在滚动停止后吸附
    var isScrolling by remember { mutableStateOf(false) }
    // 记录上次选中的索引，避免重复触发震动
    var lastSnappedIndex by remember { mutableIntStateOf(selectedIndex) }

    // 滚动停止后自动吸附到最近的选项（支持循环）
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress && isScrolling) {
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset

            if (firstIndex >= 0 && items.isNotEmpty()) {
                // 根据偏移量判断应该吸附到哪一项
                val rawTarget = if (offset > itemHeightPx / 2) firstIndex + 1 else firstIndex
                // 取模映射回原始列表索引，实现循环
                val targetIndex = ((rawTarget % items.size) + items.size) % items.size

                // 平滑滚动到中间段的对应位置（视觉上居中）
                val scrollToPos = targetIndex + items.size
                listState.animateScrollToItem(scrollToPos)

                // 触发选中回调 + 触觉反馈
                onSelect(targetIndex)
                if (targetIndex != lastSnappedIndex) {
                    com.corgimemo.app.animation.HapticFeedbackManager.performHapticFeedback(
                        context,
                        com.corgimemo.app.animation.InteractionType.SINGLE_CLICK
                    )
                    lastSnappedIndex = targetIndex
                }
            }
            isScrolling = false
        } else if (listState.isScrollInProgress) {
            isScrolling = true
        }
    }

    Box(modifier = modifier.height(totalHeight)) {
        // 选中区域高亮背景（居中显示）
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = itemHeight * centerOffset)
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                .clip(RoundedCornerShape(8.dp))
        )

        // 循环滚轮列表
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = itemHeight * centerOffset
            )
        ) {
            items(expandedItems.size) { index ->
                // 映射回原始列表的真实索引和值
                val realIndex = index % items.size
                val isSelected = realIndex == selectedIndex

                // 使用 Box 确保文字在 itemHeight 内水平和垂直居中
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .then(
                            if (!isSelected) Modifier.clickable { onSelect(realIndex) } else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatter(expandedItems[index]),
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        // 不使用透明度，所有数字清晰可见
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 将列表重复指定次数的辅助函数（用于循环滚轮实现）
 * 例：[1,2,3].repeat(3) → [1,2,3,1,2,3,1,2,3]
 */
private fun <T> List<T>.repeat(times: Int): List<T> = (1..times).flatMap { this }
