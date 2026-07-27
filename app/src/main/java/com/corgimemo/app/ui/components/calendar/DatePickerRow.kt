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
 * 布局：[月份数字"07" 16sp] [0dp] [月份中文"月" 16sp] [7dp间距] [大号日期"27" 25sp Bold] [2dp间距] [箭头 8sp]
 * 点击整个 Layout 触发弹窗展开/收起，箭头方向随弹窗状态同步切换。
 *
 * 待办页、灵感页、日期页的导航栏日期显示复用此组件，仅传入不同的
 * isExpanded 和 onClick 参数即可。
 *
 * v2026-07-27 v1.22 关键变更：让水波纹以"27"为中心（上下距离一样）。
 * v1.19-v1.21 让 4 个字符 glyph 底都贴 Layout 底（30sp），
 * 但导致"27"中心（23.5sp 距 layout 顶）远离 Layout 中心（15sp 距 layout 顶），
 * 点击"27"中央时水波纹看起来偏向底部。
 *
 * v1.22 修复：让 Layout 高度 = "27" 子项高（25sp），
 * - "27" 占据 0~25sp，"27" 中心 = 12.5sp = Layout 中心 ✓
 * - 水波纹从 Layout 中心扩散，触摸点（点击"27"中央）= Layout 中心，看起来居中
 * - 4 个字符 glyph 底都 = "27" baseline = 距 layout 顶 19sp（不再贴 Layout 底）
 * - 导航栏高度从 30sp → 25sp（矮 5sp，恢复到 v1.17/18 高度）
 *
 * v2026-07-27 v1.21 修复：v1.20 误判中文/几何符号的 glyph 底位置。
 * 实际所有字符（数字 / 中文 / 几何符号）的 glyph 底 = baseline 距 layout 顶，
 * 不是 layout 底。v1.20 用 `boxH - 子项高` 算中文/几何符号 placement y，
 * 导致"月"和"▲"的 glyph 底比"07"和"27"高 1-3sp。
 *
 * 修复：所有 4 个 Text 统一用 `boxH - FirstBaseline` 算 placement y。
 * 不管字符类别，glyph 底 = placement y + FirstBaseline = boxH ✓
 *
 * v2026-07-27 v1.20 修正：v1.19 误判"07月"为全方块字字符串（整体 glyph 占满 em-box），
 * 实际是"0" "7" 数字（不全高）+ "月" 中文（占满 em-box）的混合，glyph 高度不同。
 *
 * v1.19 用 `boxH - monthP.height` 算整个"07月" placement y，导致"0" "7" 数字
 * glyph 底比"27"日期底高 4sp，视觉上"0" "7" 浮在 box 中间而不是贴底。
 *
 * 修复：把"07月"拆成两个独立 Text，分别计算 placement y（v1.20 的拆分方案），
 * v1.21 进一步统一所有字符都用 FirstBaseline。
 *
 * v2026-07-27 v1.19 重构：用自定义 `Layout` 替代 `Row(Alignment.Bottom)`，
 * 实现"月→日→箭头"三个 glyph 底完美对齐到 Box 底。Box 高度 = 30sp
 * （"27"子项高 25sp + 数字 glyph 距 em-box 底 5sp 留白），比 v1.17/18 的
 * 25sp 高 5sp，导航栏相应高 5sp。**v1.22 已改回 25sp（让水波纹以"27"为中心）**
 *
 * 设计原理：
 * Compose `Row(verticalAlignment = Alignment.Bottom)` 对齐的是子项 layout
 * box 底部，而非 glyph 底部。即使 v1.17 消除行间距后，数字"27"（25sp Bold
 * 不全高）距 em-box 底仍有 5sp 留白，而中文"月"和几何"▼"几乎贴 em-box 底。
 * v1.18 尝试用 `padding(top)` 让"27"内容下移，但实测发现 padding 不改变
 * glyph 距 layout 底的距离（glyph 在 layout 内的位置由字体 metrics 决定），
 * 无法解决问题。
 *
 * 改用自定义 `Layout` + `placeRelative` 精确控制每个子项的 placement y。
 * **核心规则（v1.21 统一）**：所有字符的 glyph 底 = baseline 距 layout 顶，
 * 所以 `placement y = boxH - FirstBaseline`，glyph 底 = boxH ✓
 *
 * v1.21 字符分类 FirstBaseline 经验值（仅供参考，实际以运行时测量为准）：
 * | 字符类别         | fontSize | FirstBaseline（距 layout 顶）| glyph 底距 layout 底 |
 * |----------------|---------|---------------------------|---------------------|
 * | 数字 Regular   | 16sp    | ~12sp                    | ~4sp                |
 * | 数字 Bold      | 25sp    | ~19sp                    | ~6sp                |
 * | 中文方块字     | 16sp    | ~12-14sp                 | ~2-4sp              |
 * | 几何符号       | 8sp     | ~6-7sp                   | ~1-2sp              |
 *
 * 关键教训（v1.20→v1.21 修正）：
 * - v1.20 假设"中文/几何符号 glyph 占满 em-box" → 错！实际 glyph 底 = baseline
 * - v1.21 所有字符统一用 `boxH - FirstBaseline` 算 placement y
 * - FirstBaseline 是 Compose 提供的 `LayoutCoordinates` 扩展属性，
 *   反映第一个字符 baseline 距 layout 顶的距离，由字体 metrics 决定
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
    val monthNumText = String.format("%02d", now.get(Calendar.MONTH) + 1)  // "07"
    val dayText = String.format("%02d", now.get(Calendar.DAY_OF_MONTH))    // "27"
    val arrowText = if (isExpanded) "▲" else "▼"

    Layout(
        modifier = modifier.clickable(onClick = onClick),
        content = {
            // 0: 月份数字 "07"（16sp 数字，glyph 不全高，用 FirstBaseline 算）
            Text(
                text = monthNumText,
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = Color(0xFF666666)
            )
            // 1: 月份中文 "月"（16sp 中文方块字，glyph 占满 em-box，用 boxH - 子项高 算）
            Text(
                text = "月",
                fontSize = 16.sp,
                lineHeight = 16.sp,
                color = Color(0xFF666666)
            )
            // 2: 大号日期 "27"（25sp Bold 数字，glyph 底 = baseline，用 FirstBaseline 算）
            Text(
                text = dayText,
                fontSize = 25.sp,
                lineHeight = 25.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // 3: 箭头 "▼"/"▲"（8sp 几何符号，glyph 占满 em-box，用 boxH - 子项高 算）
            Text(
                text = arrowText,
                fontSize = 8.sp,
                lineHeight = 8.sp,
                color = Color(0xFF666666)
            )
        }
    ) { measurables, constraints ->
        // 测量 4 个子项
        val monthNumP = measurables[0].measure(constraints)
        val monthCnP = measurables[1].measure(constraints)
        val dayP = measurables[2].measure(constraints)
        val arrowP = measurables[3].measure(constraints)

        val monthNumW = monthNumP.width
        val monthCnW = monthCnP.width
        val dayW = dayP.width
        val arrowW = arrowP.width

        // 间距（dp → px，toInt() 避免 Int + Float = Float 的类型错误）
        val gapMonthInnerPx = 0.dp.toPx().toInt()    // "07" → "月" 0dp（视觉连续）
        val gapMonthDayPx = 7.dp.toPx().toInt()      // "月" → "27" 7dp
        val gapDayArrowPx = 2.dp.toPx().toInt()      // "27" → "▼" 2dp

        val totalW = monthNumW + gapMonthInnerPx + monthCnW + gapMonthDayPx + dayW + gapDayArrowPx + arrowW

        // Box 高度策略（v1.22）：让 Layout 高度 = "27" 子项高（25sp）
        //   水波纹中心 = Layout 中心 = "27" 中心（用户要求水波纹以天数为基准，上下距离一样）
        //   4 个字符 glyph 底都对齐到 "27" baseline（距 layout 顶 ~19sp），
        //   不再贴 Layout 底（v1.19-v1.21 的目标）
        //
        // v1.22 关键变化：
        //   - boxHeightPx = dayP.height（不再硬编码 30sp）
        //   - targetGlyphBottomY = dayP[FirstBaseline] = "27" baseline 距 layout 顶
        //   - 各字符 placement y = targetGlyphBottomY - 该字符 FirstBaseline
        //   - 导航栏高度从 30sp → 25sp（矮 5sp，恢复到 v1.17/18 高度）
        val boxHeightPx = dayP.height.toFloat()
        val dayFirstBaseline = dayP[FirstBaseline]
        val targetGlyphBottomY = dayFirstBaseline.toFloat()  // "27" baseline 位置（所有字符 glyph 底都对齐到这里）

        // 各子项 placement y（v1.22 统一规则）
        // 所有字符的 glyph 底 = targetGlyphBottomY = "27" baseline
        // placement y = targetGlyphBottomY - FirstBaseline
        val monthNumY = (targetGlyphBottomY - monthNumP[FirstBaseline]).toInt()
        val monthCnY = (targetGlyphBottomY - monthCnP[FirstBaseline]).toInt()
        val dayY = (targetGlyphBottomY - dayFirstBaseline).toInt()  // = 0
        val arrowY = (targetGlyphBottomY - arrowP[FirstBaseline]).toInt()

        // 验证（v1.22，4 个字符 glyph 底都 = "27" baseline = 距 layout 顶 19sp）：
        // "07" 数字  → y=7,  占据 7~23sp,  glyph 底 = 7  + 12 = 19sp ✓
        // "月" 中文  → y=6,  占据 6~22sp,  glyph 底 = 6  + 13 = 19sp ✓
        // "27" 数字  → y=0,  占据 0~25sp,  glyph 底 = 0  + 19 = 19sp ✓
        // "▲" 几何   → y=12, 占据 12~20sp, glyph 底 = 12 + 7  = 19sp ✓
        //
        // 水波纹中心 = Layout 中心 = 25/2 = 12.5sp = "27" 中心 ✓
        //   "27" 中心 = (0 + 25) / 2 = 12.5sp 距 layout 顶

        layout(totalW.toInt(), boxHeightPx.toInt()) {
            val x0 = 0
            val x1 = x0 + monthNumW + gapMonthInnerPx
            val x2 = x1 + monthCnW + gapMonthDayPx
            val x3 = x2 + dayW + gapDayArrowPx

            monthNumP.placeRelative(x0, monthNumY)
            monthCnP.placeRelative(x1, monthCnY)
            dayP.placeRelative(x2, dayY)
            arrowP.placeRelative(x3, arrowY)
        }
    }
}
