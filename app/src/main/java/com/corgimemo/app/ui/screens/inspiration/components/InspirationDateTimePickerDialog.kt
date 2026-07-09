package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.TimeWheelView
import java.time.LocalDate
import java.time.YearMonth
import java.util.Calendar

/**
 * 灵感日期时间修改专用弹窗
 *
 * 复用 InspirationCalendarDialog 的 CalendarMonthView/DateWheelPicker/CalendarInspirationItem，
 * 以及 ReminderPickerBottomSheet 的 TimeWheelView。
 *
 * 布局结构：
 * - 标题"修改日期时间"
 * - 日期时间行（日期芯片 + 时间芯片）
 * - 分割线
 * - 内容区：日期网格/日期滚轮/时间滚轮（根据 viewMode 和 dateViewMode 切换）
 * - 灰色分隔线（日历区与灵感区之间）
 * - 灵感区（CalendarInspirationItem 带动态参数，始终显示）
 * - 取消/确定按钮
 *
 * 使用 Dialog 窗口层级渲染，逃脱父容器约束，实现整屏遮罩覆盖。
 * 弹窗高度为屏幕 90%，按 4:1 比例定位（上边距:下边距=4:1）。
 *
 * @param inspiration 待修改的灵感数据
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调（dateMillis=日期时间毫秒值, hour=时, minute=分）
 */
@Composable
fun InspirationDateTimePickerDialog(
    inspiration: Inspiration,
    onDismiss: () -> Unit,
    onConfirm: (dateMillis: Long, hour: Int, minute: Int) -> Unit
) {
    // 初始值：系统当前时间
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val now = remember { Calendar.getInstance() }
    var selectedHour by remember { mutableIntStateOf(now.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableIntStateOf(now.get(Calendar.MINUTE)) }

    // 顶层视图模式："calendar"(日期) vs "time"(时间) — 日期/时间芯片切换
    var viewMode by remember { mutableStateOf("calendar") }

    // 日期子模式："grid"(网格) vs "wheel"(滚轮) — 年月▼切换
    var dateViewMode by remember { mutableStateOf("grid") }

    // 月份导航
    var navMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 计算屏幕高度用于 4:1 比例定位
        val configuration = LocalConfiguration.current
        val screenDensity = LocalDensity.current
        val screenHeightPx = remember(configuration) {
            with(screenDensity) { configuration.screenHeightDp.dp.toPx().toInt() }
        }

        // 全屏半透明遮罩，点击关闭
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .background(Color(0x99000000))
                .clickable { onDismiss() }
        ) {
            // 弹窗容器：固定高度屏幕90%，4:1比例定位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .layout { measurable, constraints ->
                        val screenH = if (constraints.maxHeight != androidx.compose.ui.unit.Constraints.Infinity) {
                            constraints.maxHeight
                        } else {
                            screenHeightPx
                        }
                        val dialogHeight = if (screenH > 0) (screenH * 0.9).toInt() else 900
                        val bottomSpace = (screenH - dialogHeight) / 5
                        val topSpace = bottomSpace * 4
                        val fixedConstraints = constraints.copy(
                            minHeight = dialogHeight,
                            maxHeight = dialogHeight
                        )
                        val placeable = measurable.measure(fixedConstraints)
                        layout(placeable.width, dialogHeight) {
                            placeable.placeRelative(0, topSpace.coerceAtLeast(0))
                        }
                    }
            ) {
                // 弹窗内容：淡入+缩放动画
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                ) {
                    // 弹窗主体
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ===== 标题 =====
                        Text(
                            text = "修改日期时间",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2D2D2D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 20.dp)
                        )

                        // ===== 日期时间行 =====
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "日期时间",
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

                        // ===== 分割线（日期时间行与内容区之间）=====
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFEEEEEE))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== 内容区：日期网格/日期滚轮/时间滚轮 =====
                        when (viewMode) {
                            "calendar" -> {
                                // 年月标题行（仅日期模式显示，点击切换 grid/wheel）
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dateViewMode = if (dateViewMode == "grid") "wheel" else "grid"
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${navMonth.year}年${navMonth.monthValue}月",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (dateViewMode == "grid") "▼" else "▲",
                                        fontSize = 12.sp,
                                        color = Color(0xFF999999)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // 日期网格 or 日期滚轮
                                when (dateViewMode) {
                                    "grid" -> CalendarMonthView(
                                        currentMonth = navMonth,
                                        selectedDate = selectedDate,
                                        countMap = emptyMap(),  // 无圆点
                                        onMonthChange = { navMonth = it },
                                        onDateSelect = { date -> selectedDate = date }
                                    )
                                    "wheel" -> DateWheelPicker(
                                        selectedDate = selectedDate,
                                        onDateChange = { newDate ->
                                            selectedDate = newDate
                                            navMonth = YearMonth.from(newDate)
                                        }
                                    )
                                }
                            }
                            "time" -> {
                                // 时间滚轮
                                TimeWheelView(
                                    hour = selectedHour,
                                    minute = selectedMinute,
                                    onHourChange = { selectedHour = it },
                                    onMinuteChange = { selectedMinute = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== 灰色分隔线（日历区与灵感区之间）=====
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFEEEEEE))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== 灵感区（始终显示，带动态日期时间）=====
                        CalendarInspirationItem(
                            inspiration = inspiration,
                            onClick = {},  // 纯预览，无点击行为
                            dynamicDate = selectedDate,
                            dynamicHour = selectedHour,
                            dynamicMinute = selectedMinute
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ===== 底部按钮 =====
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 取消按钮
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF5F5F5))
                                    .clickable { onDismiss() }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "取消",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF666666)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // 确定按钮
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable {
                                        // 组合日期 + 时间 → 毫秒值
                                        val cal = Calendar.getInstance().apply {
                                            set(
                                                selectedDate.year,
                                                selectedDate.monthValue - 1,
                                                selectedDate.dayOfMonth,
                                                selectedHour,
                                                selectedMinute,
                                                0
                                            )
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        onConfirm(cal.timeInMillis, selectedHour, selectedMinute)
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "确定",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
