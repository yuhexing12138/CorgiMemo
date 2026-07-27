package com.corgimemo.app.ui.components.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
 * 布局：[月份 16sp] [7dp间距] [大号日期 25sp Bold] [2dp间距] [箭头 8sp]
 * 点击整个 Row 触发弹窗展开/收起，箭头方向随弹窗状态同步切换。
 *
 * 待办页、灵感页、日期页的导航栏日期显示复用此组件，仅传入不同的
 * isExpanded 和 onClick 参数即可。
 *
 * v2026-07-27 v1.18 微调：大号日期（25sp Bold 数字）加 `Modifier.padding(top = 3.dp)`，
 * 让"27"内容整体下移 3dp，让数字 glyph 底贴 layout 底（= Row 底），与"月"/"▼"
 * 的 glyph 底精确对齐。
 *
 * v2026-07-27 v1.17 调整：三个 Text 元素都设置 `lineHeight = fontSize`，
 * 消除默认行间距（lineHeight ≈ fontSize × 1.2-1.4），让 layout box
 * 紧贴 glyph 上下边界。配合 `Row(verticalAlignment = Alignment.Bottom)`，
 * 解决"月份"和"日期"因字号/字重不同导致的视觉底部不对齐问题。
 *
 * 原因详解：Compose `Row(Alignment.Bottom)` 对齐的是子项 layout box 底部，
 * 不是 glyph 底部。即使 v1.17 消除了行间距，不同字符集 glyph 距 em-box 底的距离仍不同：
 * 数字"27"（25sp Bold，不全高）glyph 底距 em-box 底 ~3-4sp，中文"月"（16sp 方块字）
 * 几乎贴底，间距差 2-3dp。
 *
 * padding 方向选择（重要！）：
 * - `Modifier.padding(bottom = X.dp)` 在 Text 上是让 layout 盒的下边界**向下扩展 X**，
 *   但 glyph 位置不变 → glyph 距 layout 底 = X（错误方向）
 * - `Modifier.padding(top = X.dp)` 在 Text 上是让 layout 盒的顶边界**向下扩展 X**，
 *   glyph 整体下移 X → glyph 距 layout 底 = 0（正确方向）
 * 故 v1.18 使用 `padding(top = 3.dp)` 而非 `padding(bottom = 3.dp)`。
 *
 * v2026-07-27 v1.16 调整：月份移到左侧、天数移到右侧，月份→天数间距 7dp 保持不变
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
        // 月份（16sp，lineHeight=16sp 消除行间距）— 2026-07-27 v1.16 起改为左侧显示
        Text(
            text = String.format("%02d月", now.get(Calendar.MONTH) + 1),
            fontSize = 16.sp,
            lineHeight = 16.sp,                  // 关键：消除默认行间距
            color = Color(0xFF666666)
        )
        // 月 → 日 水平间距 7dp
        Spacer(modifier = Modifier.width(7.dp))
        // 大号日期数字（25sp Bold，lineHeight=25sp + padding(top=3.dp) 让 glyph 贴 layout 底）
        // — v1.16 起改为右侧显示，v1.18 加 padding 让 glyph 底与"月"/"▼"对齐
        Text(
            text = String.format("%02d", now.get(Calendar.DAY_OF_MONTH)),
            fontSize = 25.sp,
            lineHeight = 25.sp,                  // 关键：消除默认行间距
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 3.dp)  // v1.18 修正: padding(top) 让 glyph 下移贴底
        )
        // 日 → 箭头 间距 2dp
        Spacer(modifier = Modifier.width(2.dp))
        // 箭头方向随弹窗状态切换：展开时向上▲，收起时向下▼
        Text(
            text = if (isExpanded) "▲" else "▼",
            fontSize = 8.sp,
            lineHeight = 8.sp,                   // 关键：消除默认行间距
            color = Color(0xFF666666)
        )
    }
}
