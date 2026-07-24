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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.corgimemo.app.ui.components.GlobalSnackbarController
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
 * 提醒弹窗中当前编辑的目标
 */
private enum class EditTarget {
    REMINDER,   // 提醒时间
    DUE_DATE    // 截止日期
}

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
    showAdvancedOptions: Boolean = true,
    title: String = "设置提醒时间",
    rowLabel: String = "提醒时间",
    calendarRowSpacing: Dp? = null,
    inspirationPreview: @Composable ((date: LocalDate, hour: Int, minute: Int) -> Unit)? = null,
    initialDueDateMillis: Long? = null,
    onDismiss: () -> Unit,
    onConfirm: (dateMillis: Long?, hour: Int, minute: Int, repeatType: Int, calendarEnabled: Boolean, dueDateMillis: Long?) -> Unit
) {
    // 当前选中的日期和时间
    val context = LocalContext.current
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

    // 编辑目标：提醒时间 或 截止日期
    var editTarget by remember { mutableStateOf(EditTarget.REMINDER) }

    // 截止时间自动修正后的短暂高亮（1.5秒后恢复）
    var isDueDateAutoFixed by remember { mutableStateOf(false) }
    LaunchedEffect(isDueDateAutoFixed) {
        if (isDueDateAutoFixed) {
            kotlinx.coroutines.delay(1500)
            isDueDateAutoFixed = false
        }
    }

    // ===== 截止日期状态 =====
    // 截止日期开关：默认关闭（不选择截止日期）；仅当传入已有截止日期时默认打开
    var dueDateEnabled by remember { mutableStateOf(initialDueDateMillis != null) }

    // 截止时间默认与提醒时间一致（日期 + 时分完全相同）
    val initDueZoned = if (initialDueDateMillis != null) {
        java.time.Instant.ofEpochMilli(initialDueDateMillis)
            .atZone(java.time.ZoneId.systemDefault())
    } else {
        // 未传入截止日期时，使用提醒时间的时区日期（与 selectedDate/selectedHour/Minute 一致）
        initLocalDate.atTime(initialHour, initialMinute)
            .atZone(java.time.ZoneId.systemDefault())
    }
    var dueDate by remember { mutableStateOf(initDueZoned.toLocalDate()) }
    var dueHour by remember { mutableIntStateOf(initDueZoned.hour) }
    var dueMinute by remember { mutableIntStateOf(initDueZoned.minute) }

    // 重复类型选项
    val repeatOptions = listOf(
        Pair(0, "不重复"),
        Pair(1, "每天"),
        Pair(4, "周一至周五"),
        Pair(6, "周六至周日"),
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
        // 标题（动态：仅截止日期时显示"设置截止日期"）
        val displayTitle = if (initialDateMillis == null && initialDueDateMillis != null) "设置截止日期" else title
        Text(
            text = displayTitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 20.dp)
        )

        // ===== 提醒时间行 =====
        DateTimeRow(
            label = rowLabel,
            isActive = editTarget == EditTarget.REMINDER,
            dateText = "${selectedDate.year}/${String.format("%02d", selectedDate.monthValue)}/${String.format("%02d", selectedDate.dayOfMonth)}",
            timeText = "${String.format("%02d", selectedHour)}:${String.format("%02d", selectedMinute)}",
            isCalendarActive = viewMode == "calendar",
            isTimeActive = viewMode == "time",
            onDateClick = {
                editTarget = EditTarget.REMINDER
                viewMode = "calendar"
            },
            onTimeClick = {
                editTarget = EditTarget.REMINDER
                viewMode = "time"
            },
            onClear = null
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 截止日期行（开关滑块，农历同款）=====
        // 默认关闭：不选择截止日期；打开后下方展开日期+时间芯片供用户选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "截止日期",
                fontSize = 15.sp,
                color = Color(0xFF2D2D2D),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = dueDateEnabled,
                onCheckedChange = { newValue ->
                    dueDateEnabled = newValue
                    if (newValue) {
                        // 打开：激活截止日期编辑，切换到日历视图供用户选择
                        editTarget = EditTarget.DUE_DATE
                        viewMode = "calendar"
                    } else {
                        // 关闭：清空已设截止日期（重置为提醒时间），切回提醒时间编辑
                        dueDate = initLocalDate
                        dueHour = initialHour
                        dueMinute = initialMinute
                        isDueDateAutoFixed = false
                        if (editTarget == EditTarget.DUE_DATE) {
                            editTarget = EditTarget.REMINDER
                            viewMode = "calendar"
                        }
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFFFFFF),
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color(0xFFFFFFFF),
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                ),
                modifier = Modifier.height(28.dp)
            )
        }

        // ===== 截止日期展开区（开关打开时显示日期+时间芯片）=====
        // 复用 DateTimeRow，label 留空让芯片右对齐与上方提醒时间行对齐
        androidx.compose.animation.AnimatedVisibility(
            visible = dueDateEnabled,
            enter = androidx.compose.animation.expandVertically(
                expandFrom = androidx.compose.ui.Alignment.Top
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(
                shrinkTowards = androidx.compose.ui.Alignment.Top
            ) + androidx.compose.animation.fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                DateTimeRow(
                    label = "",
                    isActive = editTarget == EditTarget.DUE_DATE,
                    dateText = "${dueDate.year}/${String.format("%02d", dueDate.monthValue)}/${String.format("%02d", dueDate.dayOfMonth)}",
                    timeText = "${String.format("%02d", dueHour)}:${String.format("%02d", dueMinute)}",
                    isCalendarActive = viewMode == "calendar",
                    isTimeActive = viewMode == "time",
                    onDateClick = {
                        editTarget = EditTarget.DUE_DATE
                        viewMode = "calendar"
                    },
                    onTimeClick = {
                        editTarget = EditTarget.DUE_DATE
                        viewMode = "time"
                    },
                    onClear = null,
                    highlightEnabled = isDueDateAutoFixed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ===== 重复提醒行（仅 showAdvancedOptions=true 时显示） =====
        if (showAdvancedOptions) {

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
        }

        // ===== 农历行（仅 showAdvancedOptions=true 时显示） =====
        if (showAdvancedOptions) {
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

        // ===== 内容区域：日期滚轮 或 时间滚轮 =====
        // 有预览区时不使用 weight(1f)，让日历区自适应高度，预览区紧贴日历区底部
        val hasPreview = inspirationPreview != null
        val contentModifier = if (hasPreview) Modifier else Modifier.weight(1f)

        // 根据 editTarget 确定滚轮当前应使用哪组日期/时间
        val activeDate = if (editTarget == EditTarget.DUE_DATE) dueDate else selectedDate
        val activeHour = if (editTarget == EditTarget.DUE_DATE) dueHour else selectedHour
        val activeMinute = if (editTarget == EditTarget.DUE_DATE) dueMinute else selectedMinute

        when (viewMode) {
            "calendar" -> DateWheelPickerView(
                selectedDate = activeDate,
                onDateChange = { newDate ->
                    if (editTarget == EditTarget.DUE_DATE) {
                        dueDate = newDate
                    } else {
                        selectedDate = newDate
                        navMonth = YearMonth.from(newDate)
                    }
                },
                isExpanded = isCalendarExpanded,
                onToggleExpand = { isCalendarExpanded = !isCalendarExpanded },
                calendarEnabled = calendarEnabled && showAdvancedOptions,
                rowSpacing = calendarRowSpacing,
                modifier = contentModifier
            )
            "time" -> TimeWheelView(
                hour = activeHour,
                minute = activeMinute,
                onHourChange = {
                    if (editTarget == EditTarget.DUE_DATE) {
                        dueHour = it.coerceIn(0, 23)
                    } else {
                        selectedHour = it.coerceIn(0, 23)
                    }
                },
                onMinuteChange = {
                    if (editTarget == EditTarget.DUE_DATE) {
                        dueMinute = it.coerceIn(0, 59)
                    } else {
                        selectedMinute = it.coerceIn(0, 59)
                    }
                },
                modifier = contentModifier
            )
        }

        // ===== 灵感预览区（仅 inspirationPreview != null 时显示）=====
        if (hasPreview) {
            // 日历区/时间区到预览区 4dp 间距（需求2）
            Spacer(modifier = Modifier.height(4.dp))
            inspirationPreview.invoke(selectedDate, selectedHour, selectedMinute)
            // 弹性占位，推底部按钮到底部
            Spacer(modifier = Modifier.weight(1f))
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
                        // 计算提醒时间戳（始终有值，因为默认为今天当前时间）
                        val reminderMillis = selectedDate.atTime(selectedHour, selectedMinute)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()

                        // 截止日期：开关关闭时不设置（传 null）；打开时计算时间戳并校验
                        if (dueDateEnabled) {
                            // 计算截止日期时间戳
                            val dueDateMillis = dueDate.atTime(dueHour, dueMinute)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()

                            // 截止时间自动修正：早于提醒时间时自动调整为提醒时间
                            // 修正后不立即提交，切换到截止日期行高亮让用户确认
                            if (dueDateMillis < reminderMillis) {
                                val adjustedDateTime = java.time.Instant.ofEpochMilli(reminderMillis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                dueDate = adjustedDateTime.toLocalDate()
                                dueHour = adjustedDateTime.hour
                                dueMinute = adjustedDateTime.minute
                                editTarget = EditTarget.DUE_DATE
                                isDueDateAutoFixed = true
                                // 截止时间被自动调整：通过全局 Snackbar 提示用户确认
                                GlobalSnackbarController.showMessage("截止时间已自动调整为提醒时间，请确认")
                                return@clickable
                            }

                            onConfirm(
                                reminderMillis,
                                selectedHour,
                                selectedMinute,
                                repeatType,
                                calendarEnabled && showAdvancedOptions,
                                dueDateMillis
                            )
                        } else {
                            // 截止日期开关关闭：传 null 表示不设置截止日期
                            onConfirm(
                                reminderMillis,
                                selectedHour,
                                selectedMinute,
                                repeatType,
                                calendarEnabled && showAdvancedOptions,
                                null
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isDueDateAutoFixed) {
                    // 修正后显示橙色感叹号图标引导用户确认
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "已自动修正",
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFFF9A5C)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
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

// ==================== 日期时间行组件 ====================

/**
 * 提醒/截止日期通用行组件
 *
 * 包含标签、日期芯片、时间芯片、清除按钮，用于 ReminderPickerBottomSheet 中的
 * 提醒时间行和截止日期行，减少重复代码。
 *
 * @param label 行标签文字（如"提醒时间"、"截止日期"）
 * @param isActive 是否为当前编辑目标（高亮标签颜色）
 * @param dateText 日期芯片显示文字
 * @param timeText 时间芯片显示文字，null 时隐藏时间芯片
 * @param isCalendarActive 日历视图是否为当前激活视图
 * @param isTimeActive 时间视图是否为当前激活视图
 * @param onDateClick 日期芯片点击回调
 * @param onTimeClick 时间芯片点击回调
 * @param onClear 清除按钮点击回调，null 时不显示清除按钮
 * @param highlightEnabled 是否高亮显示（自动修正后短暂高亮提示）
 */
@Composable
private fun DateTimeRow(
    label: String,
    isActive: Boolean,
    dateText: String,
    timeText: String?,
    isCalendarActive: Boolean,
    isTimeActive: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    onClear: (() -> Unit)?,
    highlightEnabled: Boolean = false
) {
    // 高亮背景色：自动修正后短暂使用橙色淡背景
    val rowBackground = if (highlightEnabled) Color(0xFFFFF3E8) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBackground)
            .padding(vertical = if (highlightEnabled) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 标签固定宽度，避免压缩右侧芯片区域
        Text(
            text = label,
            fontSize = 15.sp,
            color = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF2D2D2D),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(64.dp)
        )

        // 右侧芯片区域整体 weight(1f) 右对齐
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 日期芯片
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isActive && isCalendarActive) MaterialTheme.colorScheme.primary else Color(0xFFFFE4CC))
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateText,
                    fontSize = 13.sp,
                    color = if (isActive && isCalendarActive) Color.White else Color(0xFF2D2D2D),
                    fontWeight = if (isActive && isCalendarActive) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // 时间芯片（仅在 timeText 非 null 时显示）
            if (timeText != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isActive && isTimeActive) MaterialTheme.colorScheme.primary else Color(0xFFFFE4CC))
                        .clickable(onClick = onTimeClick)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeText,
                        fontSize = 13.sp,
                        color = if (isActive && isTimeActive) Color.White else Color(0xFF2D2D2D),
                        fontWeight = if (isActive && isTimeActive) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 清除按钮（仅在 onClear 非 null 时显示）
            if (onClear != null) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清除",
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onClear)
                        .padding(2.dp),
                    tint = Color(0xFF999999)
                )
            }
        }
    }
}

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
    calendarEnabled: Boolean = false,  // ✅ 新增：是否启用农历显示
    rowSpacing: Dp? = null,            // 行间距：null=SpaceEvenly, 非null=spacedBy
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
                    calendarEnabled = calendarEnabled,  // 已在调用方处理 showAdvancedOptions
                    rowSpacing = rowSpacing,  // 传入行间距参数
                    // 灵感场景(rowSpacing!=null)：只填满宽度，高度自适应内容，让预览区紧贴日历区底部
                    // 待办场景(rowSpacing==null)：填满整个空间，日历均匀分布
                    modifier = if (rowSpacing != null) Modifier.fillMaxWidth() else Modifier.fillMaxSize()
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
    rowSpacing: Dp? = null,            // 行间距：null=SpaceEvenly, 非null=spacedBy
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

    // 计算需要的行数：当月首日偏移 + 天数 > 35 时需要 6 行，否则 5 行
    // 例：2026/08 首日周六(firstDayOfMonth=5) + 31天 = 36 > 35 → 需 6 行(42格)
    // 最大 firstDayOfMonth(6) + daysInMonth(31) = 37 ≤ 42，6 行(42格)足够覆盖
    val rowsNeeded = if (firstDayOfMonth + daysInMonth > 35) 6 else 5
    val targetCells = rowsNeeded * 7

    // 构建完整的网格数据（rowsNeeded 行 × 7 列）
    val gridItems = mutableListOf<Triple<Int, Boolean, Boolean>>()
    // 上月填充日期
    for (i in firstDayOfMonth downTo 1) {
        gridItems.add(Triple(prevMonthDays - i + 1, true, false))
    }
    // 当月日期
    for (day in 1..daysInMonth) {
        gridItems.add(Triple(day, false, day == selectedDate.dayOfMonth && navMonth == YearMonth.from(selectedDate)))
    }
    // 下月填充日期（补齐到 targetCells 项 = rowsNeeded 行 × 7 列）
    var nextMonthDay = 1
    while (gridItems.size < targetCells) {
        gridItems.add(Triple(nextMonthDay++, true, false))
    }

    /**
     * 日历内容区：星期行 + 日期网格（5或6行）
     * 行数动态：首日偏移+当月天数 > 35 时为 6 行（如 2026/08），否则 5 行
     * 所有子项自动等间距分布
     */
    // 灵感场景：日历区水平边距 16dp（与 InspirationCalendarDialog 一致）
    // 主 Column padding 24dp，需要每侧溢出 8dp（24-16=8）实现 16dp 边距
    val finalModifier = if (rowSpacing != null) {
        val overflowPx = with(LocalDensity.current) { 8.dp.toPx().toInt() }
        modifier.layout { measurable, constraints ->
            // 扩展最大宽度（每侧 8dp，共 16dp）
            val newConstraints = constraints.copy(
                maxWidth = (constraints.maxWidth + overflowPx * 2).coerceAtLeast(0)
            )
            val placeable = measurable.measure(newConstraints)
            layout(placeable.width, placeable.height) {
                // 向左偏移 8dp，实现每侧 8dp 溢出
                placeable.placeRelative(-overflowPx, 0)
            }
        }
    } else {
        modifier
    }

    Column(
        modifier = finalModifier
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
        verticalArrangement = if (rowSpacing != null) {
            Arrangement.spacedBy(rowSpacing)  // 灵感场景：固定间距（如 4.dp）
        } else {
            Arrangement.SpaceEvenly  // 待办场景：默认等间距
        }
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

        // ===== 日期网格（rowsNeeded 行 × 7 列）=====
        // 每行作为一个独立的 Row 子项，参与 SpaceEvenly 等间距布局
        // 行数动态：5 行或 6 行（当首日偏移+天数 > 35 时，如 2026/08）
        repeat(rowsNeeded) { weekIndex ->
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

                    // 日期格尺寸：
                    // - 灵感场景(rowSpacing!=null)：40×34dp，与 InspirationCalendarDialog 日历区一致
                    // - 待办场景(rowSpacing==null)：40×40dp 正方形，容纳农历文字
                    val cellHeight = if (rowSpacing != null) 34.dp else 40.dp
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = cellHeight)
                            .clip(RoundedCornerShape(8.dp))  // 圆角
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

    // 下月填充日期（补齐到 targetCells，与 CalendarGridView 的 rowsNeeded 保持一致）
    // 当 firstDayOfWeek + 当月天数 > 35 时需 6 行(42格)，否则 5 行(35格)
    val daysInMonth = navMonth.lengthOfMonth()
    val targetCells = if (firstDayOfWeek + daysInMonth > 35) 42 else 35
    var nextDay = 1
    while (cache.size < targetCells) {
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
internal fun TimeWheelView(
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
