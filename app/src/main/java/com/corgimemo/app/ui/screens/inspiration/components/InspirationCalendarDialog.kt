package com.corgimemo.app.ui.screens.inspiration.components

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
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.calendar.CalendarMonthView
import com.corgimemo.app.ui.components.calendar.DateWheelPicker
import com.corgimemo.app.ui.theme.UiColors
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
internal fun CalendarInspirationItem(
    inspiration: Inspiration,
    onClick: () -> Unit,
    dynamicDate: LocalDate? = null,
    dynamicHour: Int? = null,
    dynamicMinute: Int? = null
) {
    // 解码标签和图片路径（与 InspirationViewModel.decodeTags/decodePaths 逻辑一致）
    val tags = remember(inspiration.tags) { decodeTagsJson(inspiration.tags) }
    val imagePaths = remember(inspiration.imagePaths) { decodePathsJson(inspiration.imagePaths) }
    // 格式化时分时间（HH:mm）
    // dynamicHour/Minute 非 null 时用动态值，否则用灵感原始 createdAt
    val formattedTime = remember(inspiration.createdAt, dynamicHour, dynamicMinute) {
        if (dynamicHour != null && dynamicMinute != null) {
            String.format("%02d:%02d", dynamicHour, dynamicMinute)
        } else {
            val cal = Calendar.getInstance().apply { timeInMillis = inspiration.createdAt }
            String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        }
    }

    // 中文字间距（与灵感页统一）
    val chineseLetterSpacing = 0.5.sp

    // 动态日期格式化（仅 dynamicDate != null 时使用）
    val formattedDynamicDate = remember(dynamicDate) {
        if (dynamicDate != null) {
            val weekday = dynamicDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINESE)
            "${dynamicDate.year}年${dynamicDate.monthValue}月${dynamicDate.dayOfMonth}日 $weekday"
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        // ===== 动态日期行（仅 dynamicDate != null 时显示，标题上方）=====
        if (formattedDynamicDate != null) {
            Text(
                text = formattedDynamicDate,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = chineseLetterSpacing
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
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
internal fun decodeTagsJson(tagsJson: String): List<String> {
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
internal fun decodePathsJson(pathsJson: String): List<String> {
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
