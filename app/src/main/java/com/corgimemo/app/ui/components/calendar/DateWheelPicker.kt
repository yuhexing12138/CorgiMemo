package com.corgimemo.app.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

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
fun DateWheelPicker(
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
                key(currentYear) {
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
                key(currentMonth) {
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
                key(currentYear * 10000 + currentMonth * 100 + currentDay) {
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
            delay(50) // 50ms 防抖延迟

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
