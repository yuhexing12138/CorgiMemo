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
 * v2026-07-27 v1.18 微调：大号日期（25sp Bold 数字）加 `Modifier.padding(bottom = 3.dp)`，
 * 补偿数字"27" glyph 距 em-box 底 ~3-4sp 的留白（数字不全高，底部留白比中文方块字
 * "月"和几何符号"▼"都大）。配合 v1.17 的 `lineHeight = fontSize` 消除行间距后，
 * 三者 glyph 底部现在视觉精确对齐到同一水平线。
 *
 * v2026-07-27 v1.17 调整：三个 Text 元素都设置 `lineHeight = fontSize`，
 * 消除默认行间距（lineHeight ≈ fontSize × 1.2-1.4），让 layout box
 * 紧贴 glyph 上下边界。配合 `Row(verticalAlignment = Alignment.Bottom)`，
 * 解决"月份"和"日期"因字号/字重不同导致的视觉底部不对齐问题。
 *
 * 原因详解：Compose `Row(Alignment.Bottom)` 对齐的是子项 layout box 底部，
 * 不是 glyph 底部。默认 lineHeight 包含 ~2-3sp 上下行间距，字号越大留白越
 * 明显。25sp Bold "09" 距盒底 ~7sp，16sp "月" 距盒底 ~2sp，肉眼可见偏差。
 * 设置 `lineHeight = fontSize` 后留白归零，layout box 紧贴 glyph 上下边界。
 * 但仍存在 glyph 距 em-box 底的差异：数字"27"（25sp Bold）距底 ~3-4sp、
 * 中文"月"（16sp）距底 ~1sp、几何"▼"（8sp）距底 ~1sp，需要给大号数字
 * 加 3dp 底部 padding 让三者 glyph 底精确对齐。
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
        // 大号日期数字（25sp Bold，lineHeight=25sp + padding(bottom=3.dp) 补偿数字不全高的留白）
        // — v1.16 起改为右侧显示，v1.18 加 padding 微调让 glyph 底与"月"/"▼"对齐
        Text(
            text = String.format("%02d", now.get(Calendar.DAY_OF_MONTH)),
            fontSize = 25.sp,
            lineHeight = 25.sp,                  // 关键：消除默认行间距
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 3.dp)  // v1.18: 补偿数字 glyph 距 em-box 底 ~3sp
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
