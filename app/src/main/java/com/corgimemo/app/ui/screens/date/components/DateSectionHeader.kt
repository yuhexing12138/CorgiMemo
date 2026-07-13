package com.corgimemo.app.ui.screens.date.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.corgimemo.app.ui.components.CollapsibleSectionHeader
import com.corgimemo.app.viewmodel.DateGroup

/**
 * 日期分组折叠按钮（倒计时/正计时/已过期）
 *
 * 视觉规范：
 * - COUNTDOWN（倒计时）：主色 #FF9A5C
 * - COUNTUP  （正计时）：柔和绿 #7EC8A0
 * - EXPIRED  （已过期）：提示色 #999999
 *
 * 包装 CollapsibleSectionHeader，传入对应的标签、颜色与状态文案。
 * 布局对齐：由调用方通过 ReorderableItem 包裹（与待办页 PinnedSectionHeader 结构完全一致），
 * 不在内部加任何水平 padding，避免与外层 ReorderableItem 的 layout 叠加造成偏移。
 *
 * @param group 日期分组
 * @param count 该分组内卡片数量
 * @param isExpanded 是否展开（true=显示卡片列表, false=仅显示按钮）
 * @param onToggle 切换展开/折叠回调
 * @param modifier 外部 Modifier
 */
@Composable
fun DateSectionHeader(
    group: DateGroup,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (group) {
        DateGroup.COUNTDOWN -> "倒计时" to Color(0xFFFF9A5C)
        DateGroup.COUNTUP   -> "正计时" to Color(0xFF7EC8A0)
        DateGroup.EXPIRED   -> "已过期" to Color(0xFF999999)
    }
    CollapsibleSectionHeader(
        label = label,
        count = count,
        isExpanded = isExpanded,
        color = color,
        onClick = onToggle,
        expandedLabel = "收起$label",
        collapsedLabel = "展开$label",
        // 与待办页 PinnedSectionHeader 调用方式完全一致(直接传 modifier,
        // 不外加 fillMaxWidth),让两者 Row 起始 modifier 链完全相同
        modifier = modifier
    )
}
