package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
        // 不再加水平 padding,与待办页 PinnedSectionHeader 完全一致
        // (CollapsibleSectionHeader 内部已有 padding(horizontal=16.dp))
        modifier = modifier.fillMaxWidth()
    )
}
