package com.corgimemo.app.ui.components.calendar

import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
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
 * v2026-07-27 v1.19 重构：用自定义 `Layout` 替代 `Row(Alignment.Bottom)`，
 * 实现"月→日→箭头"三个 glyph 底完美对齐到 Box 底。Box 高度 = 30sp
 * （"27"子项高 25sp + 数字 glyph 距 em-box 底 5sp 留白），比 v1.17/18 的
 * 25sp 高 5sp，导航栏相应高 5sp。
 *
 * 设计原理：
 * Compose `Row(verticalAlignment = Alignment.Bottom)` 对齐的是子项 layout
 * box 底部，而非 glyph 底部。即使 v1.17 消除行间距后，数字"27"（25sp Bold
 * 不全高）距 em-box 底仍有 5sp 留白，而中文"07月"和几何"▼"几乎贴 em-box 底。
 * v1.18 尝试用 `padding(top)` 让"27"内容下移，但实测发现 padding 不改变
 * glyph 距 layout 底的距离（glyph 在 layout 内的位置由字体 metrics 决定），
 * 无法解决问题。
 *
 * 改用自定义 `Layout` + `placeRelative` 精确控制每个子项的 placement y：
 * - 月份"07月"（中文方块字 glyph 占满 em-box）→ placement y = boxH - 子项高
 * - 日期"27"（数字 glyph 底 = baseline）→ placement y = boxH - FirstBaseline
 * - 箭头"▼"（几何符号 glyph 占满 em-box）→ placement y = boxH - 子项高
 *
 * 三个 glyph 底都精确等于 boxH = 30sp，视觉完美对齐。
 *
 * 注：日期子项 placement y = 10sp，layout 底 = 35sp（超出 boxH 5sp），
 * 但超出部分是子项 em-box 底部留白（glyph 已结束），视觉上无影响。
 *
 * v2026-07-27 v1.18 微调（已废弃）：曾用 `Modifier.padding(top = 3.dp)` 给"27"
 * 让 glyph 整体下移，但实测发现 padding 不改变 glyph 距 layout 底的距离。
 *
 * v2026-07-27 v1.17 调整：三个 Text 元素都设置 `lineHeight = fontSize`，
 * 消除默认行间距（lineHeight ≈ fontSize × 1.2-1.4），让 layout box
 * 紧贴 glyph 上下边界。
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
    // 使用 remember 避免每次重组都创建 Calendar
    val now = remember { Calendar.getInstance() }
    val monthText = String.format("%02d月", now.get(Calendar.MONTH) + 1)
    val dayText = String.format("%02d", now.get(Calendar.DAY_OF_MONTH))
    val arrowText = if (isExpanded) "▲" else "▼"

    Layout(
        modifier = modifier.clickable(onClick = onClick),
        content = {
            // 0: 月份（16sp 中文方块字，glyph 占满 em-box）
            Text(
                text = monthText,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = Color(0xFF666666)
            )
            // 1: 大号日期（25sp Bold 数字，glyph 底 = baseline）
            Text(
                text = dayText,
                fontSize = 25.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 2: 箭头（8sp 几何符号，glyph 占满 em-box）
            Text(
                text = arrowText,
                fontSize = 8.sp,
                lineHeight = 8.sp,
                color = Color(0xFF666666)
            )
        }
    ) { measurables, constraints ->
        // 测量三个子项
        val monthP = measurables[0].measure(constraints)
        val dayP = measurables[1].measure(constraints)
        val arrowP = measurables[2].measure(constraints)

        val monthW = monthP.width
        val dayW = dayP.width
        val arrowW = arrowP.width

        // 间距（dp → px）
        val gap1Px = 7.dp.toPx()
        val gap2Px = 2.dp.toPx()

        val totalW = monthW + gap1Px + dayW + gap2Px + arrowW

        // Box 高度策略：让所有 glyph 底都贴 Box 底
        // "27" 数字 glyph 距 em-box 底 ~5sp（数字不全高）→ Box 高需 = 子项高 25sp + 5sp = 30sp
        // "07月" 中文方块字 glyph 占满 em-box → glyph 底 = 子项 layout 底
        // "▼" 几何符号 glyph 几乎贴 em-box 底
        val boxHeightPx = 30.sp.toPx()

        // 各子项 placement y 计算
        // 月份/箭头（glyph 占满 em-box）→ y = boxH - 子项 layout 高
        val monthY = (boxHeightPx - monthP.height).toInt()
        val arrowY = (boxHeightPx - arrowP.height).toInt()
        // 日期（数字 glyph 底 = baseline）→ y = boxH - FirstBaseline
        //   FirstBaseline 是 baseline 距子项 layout 顶的距离，由字体实际渲染决定
        //   用此值动态计算 placement y，避免硬编码 20sp 导致不同字体下偏差
        val dayFirstBaseline = dayP[FirstBaseline]
        val dayY = (boxHeightPx - dayFirstBaseline).toInt()

        // 验证（理论上）：
        // 月份 glyph 底 = monthY + monthP.height = boxH ✓
        // 日期 glyph 底 = dayY + dayFirstBaseline = boxH ✓（数字无 descent）
        // 箭头 glyph 底 = arrowY + arrowP.height = boxH ✓
        // 注：日期子项 layout 底 = dayY + dayP.height = boxH + 5sp，超出 boxH 5sp，
        //     但超出部分是子项 em-box 底部留白（glyph 已结束），视觉上无影响。

        layout(totalW.toInt(), boxHeightPx.toInt()) {
            monthP.placeRelative(0, monthY)
            dayP.placeRelative((monthW + gap1Px).toInt(), dayY)
            arrowP.placeRelative(
                (monthW + gap1Px + dayW + gap2Px).toInt(),
                arrowY
            )
        }
    }
}

