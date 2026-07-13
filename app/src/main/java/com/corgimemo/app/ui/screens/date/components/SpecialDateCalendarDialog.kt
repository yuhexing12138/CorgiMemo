package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.components.calendar.CalendarMonthView
import com.corgimemo.app.ui.components.calendar.DateWheelPicker
import com.corgimemo.app.viewmodel.DisplayDate
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

/**
 * 特殊日期日历弹窗（从导航栏底部展开的面板）
 *
 * 弹窗顶部紧贴导航栏底边缘，向下展开显示日历和当天纪念日列表。
 * 结构与 TodoCalendarDialog / InspirationCalendarDialog 完全对称：
 *
 * 顶部标题行布局：`[年月 ▼] ... [×]`
 * - 左侧：年月文本 + 下箭头（点击切换日历/滚轮模式）
 * - 右侧：× 关闭按钮
 *
 * 卡片区：选中日期完全匹配年月日（SpecialDate.targetDate 的 year/month/day 全等）的纪念日列表。
 * 卡片复用 SpecialDateCard 组件，仅支持点击跳转到详情页（不支持左滑/置顶等操作）。
 *
 * @param initialDate 初始选中日期
 * @param dateCountByDate 日期 -> 圆点颜色的映射（用于日历上按 DateCategory 颜色区分生日/纪念日/节日）
 * @param getDatesByDate 获取指定日期特殊日期列表的回调
 * @param onDateClick 点击特殊日期条目回调（跳转详情页）
 * @param onDismiss 关闭回调
 * @param topPadding 面板顶部偏移量（紧贴 topBar 底部）
 */
@Composable
fun SpecialDateCalendarDialog(
    initialDate: LocalDate = LocalDate.now(),
    dateCountByDate: (year: Int, month: Int) -> Map<Int, Color>,
    getDatesByDate: (year: Int, month: Int, day: Int) -> List<DisplayDate>,
    onDateClick: (DisplayDate) -> Unit,
    onDismiss: () -> Unit,
    topPadding: Dp = 0.dp
) {
    // 当前选中的日期
    var selectedDate by remember { mutableStateOf(initialDate) }
    // 当前显示的月份
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    // 当前选中日期的特殊日期列表
    var dayDates by remember { mutableStateOf<List<DisplayDate>>(emptyList()) }
    // 当前月份的圆点颜色映射（day of month -> Color）
    var monthColorMap by remember { mutableStateOf<Map<Int, Color>>(emptyMap()) }
    // 日期视图模式："calendar" 网格日历 / "wheel" 滚轮选择器
    var dateViewMode by remember { mutableStateOf("calendar") }

    // 月份变化时更新圆点颜色映射
    // 注意：LaunchedEffect 以 currentMonth 为 key，月份变化时自动重新执行，
    // 内部读取的 currentMonth 是最新值（State 委托属性），不存在快照滞后问题
    LaunchedEffect(currentMonth) {
        monthColorMap = dateCountByDate(currentMonth.year, currentMonth.monthValue)
    }

    // 选中日期变化时更新当天特殊日期列表
    // 注意：LaunchedEffect 以 selectedDate 为 key，日期变化时自动重新执行
    LaunchedEffect(selectedDate) {
        dayDates = getDatesByDate(
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
                // countMap 用于控制圆点是否显示（containsKey 判断），
                // 从 monthColorMap 的 keys 派生，确保有颜色的日期同时显示圆点
                countMap = monthColorMap.mapValues { 1 },
                onMonthChange = { currentMonth = it },
                onDateSelect = { date ->
                    selectedDate = date
                },
                // dotColorMap 按日期分类颜色区分生日/纪念日/节日
                dotColorMap = monthColorMap
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

        // 选中日期信息栏：显示日期、星期、当天纪念日条数
        val weekdayText = remember(selectedDate, dayDates.size) {
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val cal = Calendar.getInstance().apply {
                set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
            }
            "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 周${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}  共 ${dayDates.size} 条"
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

        // 当天特殊日期列表
        if (dayDates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "今天还没有纪念日~",
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
                    items = dayDates,
                    key = { _, date -> date.id }
                ) { index, date ->
                    CalendarSpecialDateItem(
                        date = date,
                        onClick = { onDateClick(date) }
                    )
                    // 非最后一条后添加灰色分割线
                    if (index < dayDates.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFEEEEEE))
                        )
                    }
                }

                // 底部留白
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 日历弹窗中的特殊日期条目
 *
 * 复用 SpecialDateCard 组件，仅支持点击跳转到详情页（不支持左滑/置顶等操作）。
 * - isClickBlocked = false：不屏蔽点击（无左滑面板）
 * - isSimpleMode / isBatchMode / isSelected / onSelectClick 使用默认值
 *
 * @param date 特殊日期展示数据
 * @param onClick 点击回调（跳转详情页）
 */
@Composable
private fun CalendarSpecialDateItem(
    date: DisplayDate,
    onClick: () -> Unit
) {
    SpecialDateCard(
        date = date,
        nowMs = System.currentTimeMillis(),
        isClickBlocked = false,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    )
}
