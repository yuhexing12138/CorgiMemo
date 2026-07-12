package com.corgimemo.app.ui.components.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * 导航栏中间的日期选择行组件
 *
 * 布局：[大号日期 25sp Bold] [7dp间距] [月份 16sp] [2dp间距] [箭头 8sp]
 * 点击整个 Row 触发弹窗展开/收起，箭头方向随弹窗状态同步切换。
 *
 * 待办页和灵感页的导航栏日期显示复用此组件，仅传入不同的
 * isExpanded 和 onClick 参数即可。
 *
 * @param isExpanded 日历弹窗是否展开（控制箭头方向：▲ / ▼）
 * @param onClick 点击回调（切换弹窗展开/收起）
 * @param modifier 修饰符
 */
@Composable
fun DatePickerRow(
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        val now = Calendar.getInstance()
        // 大号日期数字（25sp Bold）
        Text(
            text = String.format("%02d", now.get(Calendar.DAY_OF_MONTH)),
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        // 日 → 月 水平间距 7dp
        Spacer(modifier = Modifier.width(7.dp))
        // 月份（16sp）
        Text(
            text = String.format("%02d月", now.get(Calendar.MONTH) + 1),
            fontSize = 16.sp,
            color = Color(0xFF666666)
        )
        // 月 → 箭头 间距 2dp
        Spacer(modifier = Modifier.width(2.dp))
        // 箭头方向随弹窗状态切换：展开时向上▲，收起时向下▼
        Text(
            text = if (isExpanded) "▲" else "▼",
            fontSize = 8.sp,
            color = Color(0xFF666666)
        )
    }
}
