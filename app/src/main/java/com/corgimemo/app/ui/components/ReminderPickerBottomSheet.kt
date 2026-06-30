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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    // 日历开关
    var calendarEnabled by remember { mutableStateOf(initialCalendarEnabled) }

    // 月份导航
    var navMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

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
    // 使用 fillMaxSize() 填满父容器给定的固定高度
    // 父容器会通过自定义 layout 控制弹窗总高度并按 4:1 定位
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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

        // ===== 重复提醒行（胶囊按钮 + ↕箭头，展开时显示背景） =====
        var expanded by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "重复提醒",
                    fontSize = 15.sp,
                    color = Color(0xFF2D2D2D),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                // 胶囊形按钮：展开时显示浅灰圆角背景，收起时透明
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (expanded) Color(0xFFF0F0F0) else Color.Transparent)
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = repeatOptions.firstOrNull { it.first == repeatType }?.second ?: "不重复",
                        fontSize = 14.sp,
                        color = if (expanded) MaterialTheme.colorScheme.primary else Color(0xFF999999)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // 上下双向箭头图标（↕）—— 使用Unicode字符避免图标库依赖
                    Text(
                        text = "\u2195", // ↕
                        fontSize = 14.sp,
                        color = if (expanded) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC)
                    )
                }
            }

            // ===== 下拉选择菜单（以\"不重复\"按钮为锚点，左下弹出但视觉偏右） =====
            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                offset = androidx.compose.ui.unit.DpOffset(x = (-60).dp, y = 4.dp), // 向左偏移较小值+微下移，视觉上偏右对齐
                containerColor = Color.White // 与主弹窗背景色一致
            ) {
                repeatOptions.forEach { (type, name) ->
                    androidx.compose.material3.DropdownMenuItem(
                        onClick = {
                            repeatType = type
                            expanded = false
                        },
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                    )
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

        Spacer(modifier = Modifier.height(16.dp))

        // 分割线
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFEEEEEE))
        )

        // 分割线后间距：统一为16dp，与农历行到分割线距离一致
        // 注意：DateWheelPickerView 标题行不再设置 padding，
        // 所有间距由此外部 Spacer 统一控制，确保日历模式和滚轮模式完全一致
        Spacer(modifier = Modifier.height(16.dp))

        // ===== 内容区域：日期滚轮 或 时间滚轮（弹性填充剩余空间） =====
        when (viewMode) {
            "calendar" -> DateWheelPickerView(
                selectedDate = selectedDate,
                onDateChange = { newDate ->
                    selectedDate = newDate
                    navMonth = YearMonth.from(newDate)
                },
                isExpanded = isCalendarExpanded,
                onToggleExpand = { isCalendarExpanded = !isCalendarExpanded },
                calendarEnabled = calendarEnabled,  // ✅ 传递农历开关状态
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
    calendarEnabled: Boolean = false,  // ✅ 新增：农历开关状态
    modifier: Modifier = Modifier
) {
    // 视图模式："calendar" 网格日历 / "wheel" 滚轮选择器
    var dateViewMode by remember { mutableStateOf("calendar") }

    val currentYear = selectedDate.year
    val currentMonth = selectedDate.monthValue
    val currentDay = selectedDate.dayOfMonth

    // 月份导航状态（日历模式的 < > 按钮共用，提升到外层保证标题行位置一致）
    // 使用 remember(selectedDate) 确保日期变化时重建此 state，
    // 这样从滚轮模式返回日历时能正确跳转到选中日期所在的月份
    var navMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }

    /**
     * 统一日历/滚轮区布局：标题行 + 内容区
     * 使用 Top 对齐避免 SpaceEvenly 导致的过大间距
     */
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top  // ✅ 紧凑排列，避免额外间距
    ) {
        // ===== 第1行：统一标题行 =====
        // 日历模式：[<]   YYYY/MM ▼   [>]   （< > 可点击切换月份）
        // 滚轮模式：[空]   YYYY/MM ▲   [空] （< > 隐藏但同尺寸占位）
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧区域：日历模式显示 < 按钮（点击切上月），滚轮模式用同尺寸占位
            if (dateViewMode == "calendar") {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                            indication = null
                        ) {
                            navMonth = navMonth.minusMonths(1)
                            val newDay = minOf(selectedDate.dayOfMonth, navMonth.lengthOfMonth())
                            onDateChange(navMonth.atDay(newDay))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    @Suppress("DEPRECATION")
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = "上个月",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // 居中的年月标识 + 方向箭头（▼ 日历模式 / ▲ 滚轮模式）
            Row(
                modifier = Modifier
                    .clickable(
                        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                        indication = null
                    ) {
                        dateViewMode = if (dateViewMode == "calendar") "wheel" else "calendar"
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayMonth = if (dateViewMode == "calendar") navMonth else YearMonth.of(currentYear, currentMonth)
                Text(
                    text = "${displayMonth.year}/${String.format("%02d", displayMonth.monthValue)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D2D2D)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (dateViewMode == "calendar") "▼" else "▲",
                    fontSize = if (dateViewMode == "calendar") 11.sp else 12.sp,
                    color = Color(0xFF999999)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 右侧区域：日历模式显示 > 按钮（点击切下月），滚轮模式用同尺寸占位
            if (dateViewMode == "calendar") {
                Box(
                    modifier = Modifier
                        .clickable(
                            interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                            indication = null
                        ) {
                            navMonth = navMonth.plusMonths(1)
                            val newDay = minOf(selectedDate.dayOfMonth, navMonth.lengthOfMonth())
                            onDateChange(navMonth.atDay(newDay))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    @Suppress("DEPRECATION")
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = "下个月",
                        tint = Color(0xFF999999),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(24.dp))
            }
        }

        // ===== 内容区域：根据模式显示不同视图（带动画过渡） =====
        // 注意：此区域包含在 SpaceEvenly 布局中，会与标题行自动等间距
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
                    navMonth = navMonth,
                    onDateSelect = onDateChange,
                    onMonthChange = { navMonth = it },
                    calendarEnabled = calendarEnabled,  // ✅ 传递农历开关状态
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
 * 网格日历视图（图2样式）：星期标题 + 日期网格
 * 使用 Column + SpaceEvenly 实现所有行等间距分布
 * 导航栏（< YYYY/MM ▼ >）已提升至 DateWheelPickerView 统一管理
 * 支持左右滑动手势切换月份
 * 支持农历模式：当 calendarEnabled=true 时显示农历日期和节日
 *
 * ✅ 性能优化：使用月份级缓存预计算整月农历数据，
 *    避免每个日期格子独立调用 tyme4kt（从 ~42次调用降为1次批量计算）
 */
@Composable
private fun CalendarGridView(
    selectedDate: LocalDate,
    navMonth: YearMonth,
    onDateSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit = {},
    calendarEnabled: Boolean = false,  // ✅ 新增：是否启用农历显示
    modifier: Modifier = Modifier
) {
    val daysOfWeek = DayOfWeek.entries.take(7)

    // 计算日历网格数据：上月填充 + 当月日期
    val firstDayOfMonth = (navMonth.atDay(1).dayOfWeek.value - 1 + 7) % 7
    val daysInMonth = navMonth.lengthOfMonth()
    val prevMonthDays = navMonth.minusMonths(1).lengthOfMonth()

    // 滑动手势累积偏移量（必须在 @Composable 上下文中声明）
    var totalDragX by remember { mutableFloatStateOf(0f) }

    // ========== ✅ 性能优化：月份级农历缓存 ==========
    // 当月份变化时才重新计算，避免每个日期格子独立调用 tyme4kt
    val lunarTextCache: Map<LocalDate, String?> = if (calendarEnabled) {
        remember(navMonth) { buildMonthLunarCache(navMonth) }
    } else {
        emptyMap()
    }

    // 构建完整的5行×7列网格数据（固定5行以保持布局稳定）
    val gridItems = mutableListOf<Triple<Int, Boolean, Boolean>>()
    // 上月填充日期
    for (i in firstDayOfMonth downTo 1) {
        gridItems.add(Triple(prevMonthDays - i + 1, true, false))
    }
    // 当月日期
    for (day in 1..daysInMonth) {
        gridItems.add(Triple(day, false, day == selectedDate.dayOfMonth && navMonth == YearMonth.from(selectedDate)))
    }
    // 下月填充日期（补齐到35项=5行×7列，使用正数表示下月日期）
    var nextMonthDay = 1
    while (gridItems.size < 35) {
        gridItems.add(Triple(nextMonthDay++, true, false))
    }

    /**
     * 日历内容区：星期行 + 5行日期网格
     * 所有子项（共7行：1星期+5日期）自动等间距分布
     */
    Column(
        modifier = modifier
            .pointerInput(navMonth) {  // ✅ key=navMonth，每月变化时重新初始化手势检测器
                detectHorizontalDragGestures(
                    onDragStart = {
                        // 新手势开始时重置累积值
                        totalDragX = 0f
                    },
                    onDragEnd = {
                        // ✅ 手势结束时判断方向并切换（每个完整手势只触发一次）
                        if (totalDragX < -50f) { // 向左滑动 → 下个月
                            val nextMonth = navMonth.plusMonths(1)
                            val newDay = minOf(selectedDate.dayOfMonth, nextMonth.lengthOfMonth())
                            onDateSelect(nextMonth.atDay(newDay))
                            onMonthChange(nextMonth)
                        } else if (totalDragX > 50f) { // 向右滑动 → 上个月
                            val prevMonth = navMonth.minusMonths(1)
                            val newDay = minOf(selectedDate.dayOfMonth, prevMonth.lengthOfMonth())
                            onDateSelect(prevMonth.atDay(newDay))
                            onMonthChange(prevMonth)
                        }
                    }
                ) { _, dragAmount ->
                    // 仅累积偏移量，不在此处触发切换
                    totalDragX += dragAmount
                }
            },
        verticalArrangement = Arrangement.SpaceEvenly  // ✅ 星期行与每行日期等间距
    ) {
        // ===== 第1行：星期标题 =====
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
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.width(40.dp),  // ✅ 与日期单元格宽度一致
                        textAlign = TextAlign.Center
                    )
                }
        }

        // ===== 第2-6行：日期网格（5行×7列）=====
        // 每行作为一个独立的 Row 子项，参与 SpaceEvenly 等间距布局
        repeat(5) { weekIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                repeat(7) { dayIndex ->
                    val itemIndex = weekIndex * 7 + dayIndex
                    val (day, isPrevOrNextMonth, isSelected) = gridItems[itemIndex]

                    // ✅ 计算该日期对应的公历日期（用于农历转换和点击事件）
                    val solarDate: LocalDate = when {
                        itemIndex < firstDayOfMonth -> navMonth.minusMonths(1).atDay(day)
                        itemIndex >= firstDayOfMonth + daysInMonth -> navMonth.plusMonths(1).atDay(day)
                        else -> navMonth.atDay(day)
                    }

                    // ✅ 性能优化：直接从月份级缓存查表（O(1)），而非每个格子独立计算
                    val lunarText = lunarTextCache[solarDate]

                    val textColor = when {
                        isPrevOrNextMonth -> Color(0xFFCCCCCC)
                        isSelected -> Color.White
                        else -> Color(0xFF2D2D2D)
                    }
                    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

                    // ✅ 触觉反馈实例
                    val hapticFeedback = LocalHapticFeedback.current

                    // ✅ 正方形圆角盒子：宽度=高度，容纳数字+农历两行内容
                    Box(
                        modifier = Modifier
                            .size(40.dp)  // 正方形：足够容纳14sp数字 + 9-10sp农历 + 间距
                            .clip(RoundedCornerShape(8.dp))  // 方形 + 圆角
                            .background(bgColor)
                            .clickable {
                                // ✅ 震动反馈
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                // 允许点击所有日期（包括上月/下月灰色日期）
                                onDateSelect(solarDate)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // ✅ 统一使用 Column 垂直布局：数字在上，农历在下（参考图二样式）
                        // ✅ 使用 spacedBy 控制行间距，配合 Box 的 Center 对齐实现上下边距相等
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy((-6).dp)  // 负值让农历更靠近阳历数字
                        ) {
                            Text(
                                text = "$day",
                                fontSize = 14.sp,
                                color = textColor,
                                textAlign = TextAlign.Center
                            )
                            // ✅ 农历模式：在数字下方显示农历
                            if (calendarEnabled && lunarText != null) {
                                Text(
                                    text = lunarText,
                                    fontSize = if (isSelected) 10.sp else 9.sp,  // 选中时稍大
                                    color = if (isSelected) textColor.copy(alpha = 0.9f) else Color(0xFF999999),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 批量构建整月的农历文本缓存
 *
 * 性能优化核心：将原来每个日期格子独立调用 tyme4kt（~42次），
 * 改为一次性批量计算整月数据（1次），然后通过 Map 查表（O(1)）。
 *
 * @param navMonth 目标月份
 * @return 日期→农历文本的映射表（null 表示无农历信息）
 */
private fun buildMonthLunarCache(navMonth: YearMonth): Map<LocalDate, String?> {
    val cache = mutableMapOf<LocalDate, String?>()

    // 计算上月、当月、下月的日期范围（覆盖日历网格显示的所有日期）
    val prevMonth = navMonth.minusMonths(1)
    val nextMonth = navMonth.plusMonths(1)

    // 上月填充日期
    val firstDayOfWeek = (navMonth.atDay(1).dayOfWeek.value - 1 + 7) % 7
    val prevMonthDays = prevMonth.lengthOfMonth()
    for (i in firstDayOfWeek downTo 1) {
        val date = prevMonth.atDay(prevMonthDays - i + 1)
        cache[date] = computeLunarText(date)
    }

    // 当月日期
    for (day in 1..navMonth.lengthOfMonth()) {
        val date = navMonth.atDay(day)
        cache[date] = computeLunarText(date)
    }

    // 下月填充日期（补齐到35项）
    var nextDay = 1
    while (cache.size < 35) {
        val date = nextMonth.atDay(nextDay++)
        cache[date] = computeLunarText(date)
    }

    return cache
}

/**
 * 计算单个日期的农历显示文本
 *
 * 优先级：农历节日 > 公历节日 > 农历日期（如"初四"、"廿七"）
 *
 * @param date 公历日期
 * @return 农历显示文本，如果转换失败返回 null
 */
private fun computeLunarText(date: LocalDate): String? {
    return try {
        val lunarDate = com.corgimemo.app.animation.LunarCalendar.solarToLunar(
            date.year,
            date.monthValue,
            date.dayOfMonth
        )
        if (lunarDate != null) {
            // 优先检查节日（农历节日 > 公历节日 > 农历日期）
            val holiday = com.corgimemo.app.animation.HolidayManager.getLunarHoliday(
                date.year,
                date.monthValue,
                date.dayOfMonth
            ) ?: com.corgimemo.app.animation.HolidayManager.getSolarHoliday(
                date.monthValue,
                date.dayOfMonth
            )
            holiday?.displayName ?: lunarDate.dayDisplayName
        } else {
            null
        }
    } catch (e: Exception) {
        null
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
    // 外层容器：整体垂直居中显示（与 TimeWheelView 布局一致）
    // 内部包含：标题行 + 48dp间距 + 滚轮区
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center  // ✅ 整体垂直+水平居中
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 列标题行（与滚轮列宽度对齐）
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "年", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(72.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "月", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "日", fontSize = 13.sp, color = Color(0xFF999999), textAlign = TextAlign.Center, modifier = Modifier.width(56.dp))
            }

            // 标题行到滚轮间距：精确 24dp
            Spacer(modifier = Modifier.height(24.dp))

            // 三列滚轮（紧凑排列，不再 fillMaxSize）
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
        // 年份滚轮（范围：当前年份前后各 5 年）
        // 使用 key(year) 强制在年份变化时重建 WheelColumn 实例
        // 彻底刷新 listState 和滚动位置，避免状态错乱和跳跃感
        androidx.compose.runtime.key(year) {
            val yearRange = (year - 5)..(year + 5)
            WheelColumn(
                items = yearRange.toList(),
                selectedIndex = year - yearRange.first,
                onSelect = { index -> onYearChange(yearRange.elementAt(index)) },
                formatter = { "$it" },
                itemHeight = 48.dp,      // ✅ 与时/分滚轮一致
                visibleItemCount = 5,     // ✅ 显示5行数字
                modifier = Modifier.width(72.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 月份滚轮（1-12）
        // 使用 key(month) 强制在月份变化时重建 WheelColumn 实例
        // 彻底刷新 listState 和滚动位置，解决快速滑动时的跳跃感问题
        androidx.compose.runtime.key(month) {
            WheelColumn(
                items = (1..12).toList(),
                selectedIndex = month - 1,
                onSelect = { index -> onMonthChange(index + 1) },
                formatter = { String.format("%02d", it) },
                itemHeight = 48.dp,      // ✅ 与时/分滚轮一致
                visibleItemCount = 5,     // ✅ 显示5行数字
                modifier = Modifier.width(56.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // 日期滚轮（1-当月天数）
        // 使用 key(year, month, day) 强制在年/月/日任一变化时重建 WheelColumn 实例
        // 彻底刷新 listState 和滚动位置，避免日列数据/状态错乱
        androidx.compose.runtime.key(year * 10000 + month * 100 + day) {
            val daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth()
            WheelColumn(
                items = (1..daysInMonth).toList(),
                selectedIndex = day - 1,
                onSelect = { index -> onDayChange(index + 1) },
                formatter = { String.format("%02d", it) },
                itemHeight = 48.dp,      // ✅ 与时/分滚轮一致
                visibleItemCount = 5,     // ✅ 显示5行数字
                modifier = Modifier.width(56.dp)
            )
        }
            }  // ← 三列滚轮 Row 结束
        }  // ← Column 结束（标题+间距+滚轮）
    }  // ← Box 结束（整体居中容器）
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
        // 使用 key(hour) 强制在小时变化时重建 WheelColumn 实例
        // 彻底刷新 listState 和滚动位置，解决快速滑动时的跳跃感问题
        androidx.compose.runtime.key(hour) {
            WheelColumn(
                items = (0..23).toList(),
                selectedIndex = hour.coerceIn(0, 23),
                onSelect = { onHourChange(it) },
                formatter = { String.format("%02d", it) },
                itemHeight = itemHeightDp,
                visibleItemCount = visibleItemCount,
                modifier = Modifier.width(64.dp)
            )
        }

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
        // 使用 key(minute) 强制在分钟变化时重建 WheelColumn 实例
        // 彻底刷新 listState 和滚动位置，解决快速滑动时的跳跃感问题
        androidx.compose.runtime.key(minute) {
            WheelColumn(
                items = (0..59).toList(),
                selectedIndex = minute.coerceIn(0, 59),
                onSelect = { onMinuteChange(it) },
                formatter = { String.format("%02d", it) },
                itemHeight = itemHeightDp,
                visibleItemCount = visibleItemCount,
                modifier = Modifier.width(64.dp)
            )
        }

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
 * - 防抖机制：避免小数据量时频繁回调导致跳跃感
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

    // ===== 无限循环滚动：使用 Int.MAX_VALUE 模拟无限列表 =====
    // Int.MAX_VALUE ≈ 21亿，用户永远不可能滚到边界，彻底消除边界跳跃问题
    // 通过 index % items.size 将虚拟索引映射回真实数据，实现无缝循环
    val listState = androidx.compose.foundation.lazy.rememberLazyListState(
        initialFirstVisibleItemIndex = kotlin.Int.MAX_VALUE / 2 - (kotlin.Int.MAX_VALUE / 2) % items.size + selectedIndex
    )

    // 使用 Compose 官方 SnapFlingBehavior 实现惯性吸附动画（iOS 风格）
    val snapBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
        lazyListState = listState
    )

    // 记录上次选中的索引，避免重复触发震动和回调
    var lastSnappedIndex by remember { mutableIntStateOf(selectedIndex) }
    // 防抖标记：用于延迟回调，避免快速滑动时的跳跃感
    var pendingIndex by remember { mutableIntStateOf(-1) }

    // 当外部 selectedIndex 变化时（如从日历模式选了新日期再切回滚轮模式），
    // 平滑滚动到新的选中项，同时重置 lastSnappedIndex 避免吸附逻辑误判
    LaunchedEffect(selectedIndex) {
        val targetPosition = kotlin.Int.MAX_VALUE / 2 - (kotlin.Int.MAX_VALUE / 2) % items.size + selectedIndex
        if (listState.firstVisibleItemIndex != targetPosition) {
            listState.animateScrollToItem(targetPosition)
        }
        lastSnappedIndex = selectedIndex
        pendingIndex = -1  // 重置待处理索引
    }

    // 监听滚动状态变化，检测当前居中项并触发回调
    // rememberSnapFlingBehavior 负责吸附动画，此处仅负责通知和反馈
    // 优化：添加防抖延迟，避免小数据量（如月份12项）时频繁回调导致跳跃感
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 滚动停止后，等待一小段时间让吸附动画完成
            kotlinx.coroutines.delay(50)  // 50ms 防抖延迟

            // 再次检查是否仍在滚动（可能用户又开始了新的滑动）
            if (!listState.isScrollInProgress) {
                val firstIndex = listState.firstVisibleItemIndex
                if (firstIndex >= 0 && items.isNotEmpty()) {
                    val offset = listState.firstVisibleItemScrollOffset
                    // 根据偏移量判断实际居中的项
                    val rawTarget = if (offset > itemHeightPx / 2) firstIndex + 1 else firstIndex
                    val targetIndex = ((rawTarget % items.size) + items.size) % items.size

                    // 仅在选中项真正变化时才触发回调和触觉反馈
                    if (targetIndex != lastSnappedIndex && targetIndex != pendingIndex) {
                        onSelect(targetIndex)
                        com.corgimemo.app.animation.HapticFeedbackManager.performHapticFeedback(
                            context,
                            com.corgimemo.app.animation.InteractionType.SINGLE_CLICK
                        )
                        lastSnappedIndex = targetIndex
                        pendingIndex = -1
                    }
                }
            }
        } else {
            // 滚动开始时，记录当前可能的选中项（用于防抖）
            val firstIndex = listState.firstVisibleItemIndex
            if (firstIndex >= 0 && items.isNotEmpty()) {
                val offset = listState.firstVisibleItemScrollOffset
                val rawTarget = if (offset > itemHeightPx / 2) firstIndex + 1 else firstIndex
                pendingIndex = ((rawTarget % items.size) + items.size) % items.size
            }
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

        // 无限循环滚轮列表（使用 SnapFlingBehavior 实现惯性吸附）
        // items(Int.MAX_VALUE) 创建虚拟无限列表，通过取模映射回真实数据
        // LazyColumn 的虚拟化机制确保仅渲染可见项，内存开销极小
        LazyColumn(
            state = listState,
            flingBehavior = snapBehavior,  // iOS 风格：惯性滚动自动吸附到最近项
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                vertical = itemHeight * centerOffset
            )
        ) {
            items(kotlin.Int.MAX_VALUE) { index ->
                // 将虚拟索引映射回原始列表的真实索引和值
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
                        text = formatter(items[realIndex]),  // 取模后直接访问原列表
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCCCCCC),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
