package com.corgimemo.app.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.components.calendar.CalendarMonthView
import com.corgimemo.app.ui.components.calendar.DateWheelPicker
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

/**
 * 待办日历弹窗（从导航栏底部展开的面板）
 *
 * 弹窗顶部紧贴导航栏底边缘，向下展开显示日历和当天待办列表。
 * 结构与 InspirationCalendarDialog 对称，复用共享日历组件。
 *
 * 顶部标题行布局：`[年月 ▼] ... [×]`
 * 底部列表区：当天基于 startDate 的待办列表（含已完成）
 *
 * @param initialDate 初始选中日期
 * @param todoCountByDate 日期 -> 待办条数的映射（用于显示圆点）
 * @param getTodosByDate 获取指定日期待办列表的回调
 * @param onTodoClick 点击待办条目回调
 * @param onDismiss 关闭回调
 * @param topPadding 面板顶部偏移量（紧贴 topBar 底部）
 */
@Composable
fun TodoCalendarDialog(
    initialDate: LocalDate = LocalDate.now(),
    todoCountByDate: (year: Int, month: Int) -> Map<Int, Int>,
    getTodosByDate: (year: Int, month: Int, day: Int) -> List<TodoItem>,
    onTodoClick: (TodoItem) -> Unit,
    onDismiss: () -> Unit,
    topPadding: Dp = 0.dp
) {
    // 当前选中的日期
    var selectedDate by remember { mutableStateOf(initialDate) }
    // 当前显示的月份
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    // 当前选中日期的待办列表
    var dayTodos by remember { mutableStateOf<List<TodoItem>>(emptyList()) }
    // 当前月份的待办计数
    var monthCountMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    // 日期视图模式："calendar" 网格日历 / "wheel" 滚轮选择器
    var dateViewMode by remember { mutableStateOf("calendar") }

    // 月份变化时更新计数
    LaunchedEffect(currentMonth) {
        monthCountMap = todoCountByDate(currentMonth.year, currentMonth.monthValue)
    }

    // 选中日期变化时更新当天待办列表
    LaunchedEffect(selectedDate) {
        dayTodos = getTodosByDate(
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
        val weekdayText = remember(selectedDate, dayTodos.size) {
            val weekdays = arrayOf("日", "一", "二", "三", "四", "五", "六")
            val cal = Calendar.getInstance().apply {
                set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
            }
            "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日 周${weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]}  共 ${dayTodos.size} 条待办"
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

        // 当天待办列表
        if (dayTodos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "今天还没有待办~",
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
                    items = dayTodos,
                    key = { _, todo -> todo.id }
                ) { index, todo ->
                    CalendarTodoItem(
                        todo = todo,
                        onClick = { onTodoClick(todo) }
                    )
                    // 非最后一条后添加灰色分割线
                    if (index < dayTodos.size - 1) {
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
 * 日历弹窗中的待办条目
 *
 * 显示：勾选框 + 标题 + 优先级色块 + 截止时间
 * 已完成项标题加删除线。
 *
 * @param todo 待办数据
 * @param onClick 点击回调
 */
@Composable
private fun CalendarTodoItem(
    todo: TodoItem,
    onClick: () -> Unit
) {
    val isCompleted = todo.status == 1
    // 优先级色块颜色
    val priorityColor = when {
        todo.priority >= 3 -> Color(0xFFE53935) // 高优先级-红
        todo.priority == 2 -> Color(0xFFFFA726) // 中优先级-橙
        else -> Color(0xFF66BB6A)               // 低优先级-绿
    }
    // 截止时间文本
    val dueDateText = todo.dueDate?.let { dueDate ->
        val cal = Calendar.getInstance().apply { timeInMillis = dueDate }
        "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左侧：勾选框
        Text(
            text = if (isCompleted) "✓" else "□",
            fontSize = 16.sp,
            color = if (isCompleted) Color(0xFF7EC8A0) else Color(0xFF999999)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // 中间：标题
        Text(
            text = todo.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (isCompleted) Color(0xFF999999) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )

        // 右侧：优先级色块 + 截止时间
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 优先级色块
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(priorityColor)
            )

            // 截止时间
            if (dueDateText != null) {
                Text(
                    text = dueDateText,
                    fontSize = 11.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}
