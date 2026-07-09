package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.theme.UiColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

/**
 * 灵感日历弹窗（从导航栏底部展开的面板）
 *
 * 弹窗顶部紧贴导航栏底边缘，向下展开显示日历和当天灵感列表。
 * 由于此组件在 InspirationScreen（Scaffold content 区域）内渲染，
 * 面板顶部自然对齐 topBar 底部。
 *
 * 顶部标题行布局：`[年月 ▼] ... [×]`
 * - 左侧：年月文本 + 下箭头（▼ 点击切换日历/滚轮模式）
 * - 右侧：× 关闭按钮
 *
 * 支持两种日期选择模式：
 * - calendar：网格日历，支持左右滑动切换月份
 * - wheel：年/月/日三列滚轮选择器
 *
 * @param initialDate 初始选中日期
 * @param inspirationCountByDate 日期 -> 灵感条数的映射（用于显示圆点）
 * @param getInspirationsByDate 获取指定日期灵感列表的回调
 * @param onInspirationClick 点击灵感条目回调
 * @param onDismiss 关闭回调
 * @param topPadding 面板顶部偏移量（紧贴 topBar 底部）
 */
@Composable
fun InspirationCalendarDialog(
    initialDate: LocalDate = LocalDate.now(),
    inspirationCountByDate: (year: Int, month: Int) -> Map<Int, Int>,
    getInspirationsByDate: (year: Int, month: Int, day: Int) -> List<Inspiration>,
    onInspirationClick: (Inspiration) -> Unit,
    onDismiss: () -> Unit,
    topPadding: Dp = 0.dp
) {
    // 当前选中的日期
    var selectedDate by remember { mutableStateOf(initialDate) }
    // 当前显示的月份
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    // 当前选中日期的灵感列表
    var dayInspirations by remember { mutableStateOf<List<Inspiration>>(emptyList()) }
    // 当前月份的灵感计数
    var monthCountMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    // 日期视图模式："calendar" 网格日历 / "wheel" 滚轮选择器
    var dateViewMode by remember { mutableStateOf("calendar") }

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

    // 面板直接占满全屏，从 topPadding（导航栏底部）开始向下展开
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .background(MaterialTheme.colorScheme.surface)
    ) {
            // 顶部标题行：[年月 ▼] ... [×]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：年月文本 + 下箭头（点击切换日历/滚轮模式）
                Row(
                    modifier = Modifier
                        .clickable {
                            dateViewMode = if (dateViewMode == "calendar") "wheel" else "calendar"
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentMonth.year}年${currentMonth.monthValue}月",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // ▼ 日历模式 / ▲ 滚轮模式
                    Text(
                        text = if (dateViewMode == "calendar") "▼" else "▲",
                        fontSize = 12.sp,
                        color = Color(0xFF999999)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 右侧：× 关闭按钮
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFF666666),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 日期选择内容区：根据模式显示网格日历或滚轮选择器
            when (dateViewMode) {
                "calendar" -> CalendarMonthView(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    countMap = monthCountMap,
                    onMonthChange = { currentMonth = it },
                    onDateSelect = { date ->
                        selectedDate = date
                    }
                )
                "wheel" -> DateWheelPicker(
                    selectedDate = selectedDate,
                    onDateChange = { newDate ->
                        selectedDate = newDate
                        currentMonth = YearMonth.from(newDate)
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                        .padding(horizontal = 20.dp)
                ) {
                    itemsIndexed(
                        items = dayInspirations,
                        key = { _, inspiration -> inspiration.id }
                    ) { index, inspiration ->
                        CalendarInspirationItem(
                            inspiration = inspiration,
                            onClick = { onInspirationClick(inspiration) }
                        )
                        // 非最后一条灵感后添加灰色分割线
                        if (index < dayInspirations.size - 1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFEEEEEE))
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

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
 */
@Composable
private fun CalendarMonthView(
    currentMonth: YearMonth,
    selectedDate: LocalDate,
    countMap: Map<Int, Int>,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit
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
 * 日期滚轮选择器：年/月/日三列滚轮
 *
 * 使用 LazyColumn + rememberSnapFlingBehavior 实现 iOS 风格的惯性吸附滚动。
 * - 年份范围：当前选中年份±5
 * - 月份：1-12
 * - 日期：1-当月天数（随年月自动调整）
 *
 * 选中项变化时通过 onDateChange 回调通知外部，自动处理跨月天数溢出
 * （例如 1月31日 → 2月时自动调整为 2月28日）。
 *
 * @param selectedDate 当前选中的日期
 * @param onDateChange 日期变化回调
 */
@Composable
private fun DateWheelPicker(
    selectedDate: LocalDate,
    onDateChange: (LocalDate) -> Unit
) {
    val currentYear = selectedDate.year
    val currentMonth = selectedDate.monthValue
    val currentDay = selectedDate.dayOfMonth

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 列标题行（与滚轮列宽度对齐）
            Row(
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "年",
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(72.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "月",
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "日",
                    fontSize = 13.sp,
                    color = Color(0xFF999999),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 三列滚轮：年 / 月 / 日
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                // 年份滚轮（范围：当前年份前后各 5 年）
                // 使用 key(year) 强制在年份变化时重建 WheelColumn 实例，
                // 彻底刷新 listState 和滚动位置，避免状态错乱和跳跃感
                androidx.compose.runtime.key(currentYear) {
                    val yearRange = (currentYear - 5)..(currentYear + 5)
                    WheelColumn(
                        items = yearRange.toList(),
                        selectedIndex = currentYear - yearRange.first,
                        onSelect = { index ->
                            val newYear = yearRange.elementAt(index)
                            val maxDay = YearMonth.of(newYear, currentMonth).lengthOfMonth()
                            val validDay = minOf(currentDay, maxDay)
                            onDateChange(LocalDate.of(newYear, currentMonth, validDay))
                        },
                        formatter = { "$it" },
                        itemHeight = 48.dp,
                        visibleItemCount = 5,
                        modifier = Modifier.width(72.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 月份滚轮（1-12）
                // 使用 key(month) 强制在月份变化时重建 WheelColumn 实例
                androidx.compose.runtime.key(currentMonth) {
                    WheelColumn(
                        items = (1..12).toList(),
                        selectedIndex = currentMonth - 1,
                        onSelect = { index ->
                            val newMonth = index + 1
                            val maxDay = YearMonth.of(currentYear, newMonth).lengthOfMonth()
                            val validDay = minOf(currentDay, maxDay)
                            onDateChange(LocalDate.of(currentYear, newMonth, validDay))
                        },
                        formatter = { String.format("%02d", it) },
                        itemHeight = 48.dp,
                        visibleItemCount = 5,
                        modifier = Modifier.width(56.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // 日期滚轮（1-当月天数）
                // 使用 key(year, month, day) 强制在年/月/日任一变化时重建 WheelColumn 实例
                androidx.compose.runtime.key(currentYear * 10000 + currentMonth * 100 + currentDay) {
                    val daysInMonth = YearMonth.of(currentYear, currentMonth).lengthOfMonth()
                    WheelColumn(
                        items = (1..daysInMonth).toList(),
                        selectedIndex = currentDay - 1,
                        onSelect = { index ->
                            onDateChange(LocalDate.of(currentYear, currentMonth, index + 1))
                        },
                        formatter = { String.format("%02d", it) },
                        itemHeight = 48.dp,
                        visibleItemCount = 5,
                        modifier = Modifier.width(56.dp)
                    )
                }
            }
        }
    }
}

/**
 * 单列滚轮选择器组件（iOS 风格）
 *
 * 特性：
 * - 无限循环滚动：使用 Int.MAX_VALUE 模拟无限列表，通过取模映射回真实数据
 * - 惯性吸附：使用 SnapFlingBehavior 实现滚动停止后自动吸附到最近选项
 * - 选中项高亮：更大字号 + 主色 + 加粗
 * - 触觉反馈：选中项切换时触发轻微震动
 *
 * @param items 数据列表
 * @param selectedIndex 当前选中项索引
 * @param onSelect 选中项变化回调（返回真实索引）
 * @param formatter 文本格式化函数
 * @param itemHeight 单项高度
 * @param visibleItemCount 可见行数（奇数，中间行为选中项）
 * @param modifier 修饰符
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
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current // 触觉反馈实例
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val totalHeight = itemHeight * visibleItemCount
    // 中心行的偏移索引（用于渐变透明度和选中高亮计算）
    val centerOffset = visibleItemCount / 2

    // ===== 无限循环滚动：使用 Int.MAX_VALUE 模拟无限列表 =====
    // Int.MAX_VALUE ≈ 21亿，用户永远不可能滚到边界，彻底消除边界跳跃问题
    // 通过 index % items.size 将虚拟索引映射回真实数据，实现无缝循环
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % items.size + selectedIndex
    )

    // 使用 Compose 官方 SnapFlingBehavior 实现惯性吸附动画（iOS 风格）
    val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    // 记录上次选中的索引，避免重复触发震动和回调
    var lastSnappedIndex by remember { mutableIntStateOf(selectedIndex) }
    // 防抖标记：用于延迟回调，避免快速滑动时的跳跃感
    var pendingIndex by remember { mutableIntStateOf(-1) }

    // 当外部 selectedIndex 变化时（如从日历模式选了新日期再切回滚轮模式），
    // 平滑滚动到新的选中项，同时重置 lastSnappedIndex 避免吸附逻辑误判
    LaunchedEffect(selectedIndex) {
        val targetPosition = Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % items.size + selectedIndex
        if (listState.firstVisibleItemIndex != targetPosition) {
            listState.animateScrollToItem(targetPosition)
        }
        lastSnappedIndex = selectedIndex
        pendingIndex = -1 // 重置待处理索引
    }

    // 监听滚动状态变化，检测当前居中项并触发回调
    // rememberSnapFlingBehavior 负责吸附动画，此处仅负责通知和反馈
    // 优化：添加防抖延迟，避免小数据量（如月份12项）时频繁回调导致跳跃感
    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            // 滚动停止后，等待一小段时间让吸附动画完成
            kotlinx.coroutines.delay(50) // 50ms 防抖延迟

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
                        // 触觉反馈：选中项切换时触发轻微震动
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
            flingBehavior = snapBehavior, // iOS 风格：惯性滚动自动吸附到最近项
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * centerOffset)
        ) {
            items(Int.MAX_VALUE) { index ->
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
                        text = formatter(items[realIndex]), // 取模后直接访问原列表
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

/**
 * 日历弹窗中的灵感条目
 *
 * 内容格式与灵感页 TimelineInspirationItem 的右侧内容区完全一致，
 * 包含：标题、时分时间、正文、标签、图片缩略图。
 * 不包含左侧时间栏（年月+大号日期）、时间线节点和竖线。
 *
 * 字号/间距体系（与 TimelineInspirationItem 同步）：
 * - 标题 16sp Medium / 时分时间 11sp / 正文 14sp（行高21sp）/ 标签 11sp
 * - 标题→时分时间 4dp / 时分时间→正文 9dp / 正文→标签 7dp / 标签→图片 4dp
 * - 中文字间距 +0.5sp
 *
 * @param inspiration 灵感数据
 * @param onClick 点击回调
 */
@Composable
private fun CalendarInspirationItem(
    inspiration: Inspiration,
    onClick: () -> Unit
) {
    // 解码标签和图片路径（与 InspirationViewModel.decodeTags/decodePaths 逻辑一致）
    val tags = remember(inspiration.tags) { decodeTagsJson(inspiration.tags) }
    val imagePaths = remember(inspiration.imagePaths) { decodePathsJson(inspiration.imagePaths) }
    // 格式化时分时间（HH:mm）
    val formattedTime = remember(inspiration.createdAt) {
        val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
        String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    // 中文字间距（与灵感页统一）
    val chineseLetterSpacing = 0.5.sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        // ===== 标题行（置顶图标 + 标题 16sp Medium）=====
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (inspiration.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "已置顶",
                    tint = Color(0xFFFF9A5C),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = inspiration.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = chineseLetterSpacing
            )
        }

        // 标题 → 时分时间 间距 4dp
        Spacer(modifier = Modifier.height(4.dp))

        // ===== 时分时间（11sp 灰色）=====
        Text(
            text = formattedTime,
            fontSize = 11.sp,
            color = Color(0xFF999999),
            letterSpacing = chineseLetterSpacing
        )

        // 时分时间 → 正文 间距 9dp
        Spacer(modifier = Modifier.height(9.dp))

        // ===== 正文（14sp，行高 21sp）=====
        if (inspiration.content.isNotBlank()) {
            val plainContent = removeHtmlTags(inspiration.content)
            Text(
                text = plainContent,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color(0xFF666666),
                letterSpacing = chineseLetterSpacing
            )
        }

        // ===== 标签（最多3个 +#tag，11sp，橙色背景圆角）=====
        if (tags.isNotEmpty()) {
            // 正文 → 标签 间距 7dp
            Spacer(modifier = Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.take(3).forEach { tag ->
                    Text(
                        text = "#$tag",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = UiColors.Primary,
                        letterSpacing = chineseLetterSpacing,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 1.dp, vertical = 0.dp)
                    )
                }
                if (tags.size > 3) {
                    Text(
                        text = "+${tags.size - 3}",
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        color = Color(0xFF999999),
                        letterSpacing = chineseLetterSpacing,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 1.dp, vertical = 0.dp)
                    )
                }
            }
        }

        // ===== 图片缩略图（最多2个+剩余数量，28dp 圆角）=====
        if (imagePaths.isNotEmpty()) {
            // 标签 → 图片 间距 4dp
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                imagePaths.take(2).forEach { _ ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🖼️", fontSize = 12.sp)
                    }
                }
                if (imagePaths.size > 2) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEEEEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${imagePaths.size - 2}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 解码标签JSON字符串为列表
 *
 * 与 InspirationViewModel.decodeTags 逻辑一致，
 * 用于在日历弹窗中复用标签解析而无需依赖 ViewModel。
 *
 * @param tagsJson JSON字符串（如 ["标签1","标签2"]）
 * @return 标签列表
 */
private fun decodeTagsJson(tagsJson: String): List<String> {
    if (tagsJson.isBlank()) return emptyList()
    return try {
        tagsJson
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 解码图片路径JSON字符串为列表
 *
 * 与 InspirationViewModel.decodePaths 逻辑一致，
 * 用于在日历弹窗中复用图片路径解析而无需依赖 ViewModel。
 *
 * @param pathsJson JSON字符串（如 ["/path1.jpg","/path2.jpg"]）
 * @return 路径列表
 */
private fun decodePathsJson(pathsJson: String): List<String> {
    if (pathsJson.isBlank()) return emptyList()
    return try {
        pathsJson
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * 去除HTML标签
 *
 * @param html 包含HTML标签的字符串
 * @return 纯文本字符串
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
