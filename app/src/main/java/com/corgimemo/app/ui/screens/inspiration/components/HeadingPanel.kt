package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/* ===== 标题面板排版常量（v2026-09-21）===== */

/** 标题格子之间的横向间距（6 格等分整行时的间隙） */
private val HeadingCellGap = 8.dp

/** 标题格子高度：44dp 保证触摸目标够大（Material 建议 ≥48dp，此处按面板空间折中） */
private val HeadingCellHeight = 44.dp

/** 面板内容左右内边距：与面板头标题左边缘对齐 */
private val HeadingPanelHorizontalPadding = 12.dp

/**
 * 「普通标题」条目：显示标签 → 下行 action（v2026-09-21 由工具栏「组三 Headings」迁入）
 *
 * action 与桥协议既有取值一致（见 [RichTextFormatToolbar] 的 `onTransform` KDoc）：
 * `heading1`–`heading6`。
 */
private val NormalHeadingItems = listOf(
    "H1" to "heading1",
    "H2" to "heading2",
    "H3" to "heading3",
    "H4" to "heading4",
    "H5" to "heading5",
    "H6" to "heading6"
)

/**
 * 「可折叠标题」条目：显示标签 → 下行 action（v2026-09-21 由工具栏「组四 Subheadings」迁入）
 *
 * ⚠️ 标签只用数字而非工具栏原有的 `▸1`：真机上 `▸` 字形渲染异常（截图中显示为 `-1`），
 * 且本面板已有「可折叠标题」分区标题说明语义，数字更干净。
 * action 取值沿用原实现：`toggleHeading` / `toggleHeading2` / `toggleHeading3`
 * （注意 1 级没有后缀数字，是 BlockNote 侧的既有约定，别"顺手改整齐"）。
 */
private val CollapsibleHeadingItems = listOf(
    "1" to "toggleHeading",
    "2" to "toggleHeading2",
    "3" to "toggleHeading3"
)

/**
 * 编辑页「标题」面板（内联面板；v2026-09-21 新增）
 *
 * 由 [InspirationEditBottomBar] 插入在「格式工具栏」与「相机行」之间，与
 * [FontPickerPanel]（T）、[FontSizeColorPanel]（Aa）、[ColorStylePanel]（A）
 * **四者互斥、占同一槽位、同高度**（面板高度 = 键盘高度，互斥切换不跳动）。
 *
 * **来源**：原工具栏里的「组三 Headings（RiH1–RiH6）」与「组四 Subheadings（▸1–▸3）」
 * 九个按钮占用格式工具栏大量横向空间，按需求整体移入本面板并分两类呈现。
 *
 * **布局**：
 * - 面板头（40dp）：左「标题」标题 + 「点选即转换」提示，右「完成」文字按钮
 * - 分隔线（1dp，outlineVariant 50%）
 * - 内容区（`weight(1f)` + 纵向滚动）：
 *   - 「普通标题」：H1–H6 **6 格等分整行**
 *   - 「可折叠标题」：1 / 2 / 3，**格子宽度与上一行一致**（右侧用等权 Spacer 占位对齐网格）
 *
 * **生效语义**（与 Aa / A 面板一致）：点选**即时生效且不收起面板**，收起由面板头
 * 「完成」或再点一次工具栏「H」按钮触发。转换结果为块级属性，重复点同一项即"再转换一次"。
 *
 * **不做激活态回显**：宿主从 `blockState` 只能拿到当前块类型（如 `heading`），
 * **拿不到标题级别**（桥未上行 level），无法判断"当前块是不是 H3"，故九个格子一律不高亮。
 * 若要回显，需要 JS 侧在 `blockState` 里补 heading level 字段（见方案建议）。
 *
 * @param panelHeight 面板总高度（= 键盘高度；内容超出纵向滚动）
 * @param onTransform 块类型转换回调（参数为 action：heading1–6 / toggleHeading / toggleHeading2 / 3）
 * @param onDone 点击面板头「完成」（收起面板）
 * @param enabled 面板**内容区**是否可用（v2026-09-21 新增）：宿主锁定态传 false——
 *   九个标题格子整片降到 38% 不透明度，并在 `PointerEventPass.Initial` 阶段拦截点击
 *   （与格式工具栏的 `toolbarEnabled` 口径一致）。⚠️ 面板头「完成」不受影响，
 *   锁定态下仍可收起面板，否则面板会"关不掉"。
 * @param modifier Modifier
 */
@Composable
internal fun HeadingPanel(
    panelHeight: Dp,
    onTransform: (String) -> Unit,
    onDone: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    /**
     * 禁用态拦截（仅内容区）：锁定态下九个标题格子不接受点击。
     *
     * ⚠️ 必须用 `PointerEventPass.Initial`：本阶段事件**由父级流向子级**，
     * 而格子的 `clickable` 在 `Main` 阶段（子级→父级）响应，在 `Main` 阶段拦不住子级
     * ——与 [RichTextFormatToolbar] 的 `disabledBlocker` 同一范式。
     */
    val contentBlocker = if (enabled) {
        Modifier
    } else {
        Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight)
        ) {
            /** ---- 面板头：标题 + 提示 | 完成（40dp = TextButton 最小触摸高度，与另三个面板同规格） ---- */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "标题",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "点选即转换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDone) {
                    Text(
                        text = "完成",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HeadingPanelHorizontalPadding)
                    /** 锁定态视觉降级：与格式工具栏 disabled 态同款 38% 不透明度 */
                    .alpha(if (enabled) 1f else 0.38f)
                    .then(contentBlocker)
            ) {
                /** ---- 第一类：普通标题（H1–H6，6 格等分整行） ---- */
                HeadingSectionTitle(text = "普通标题")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HeadingCellGap)
                ) {
                    NormalHeadingItems.forEach { (label, action) ->
                        HeadingCell(
                            label = label,
                            onClick = { onTransform(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                /**
                 * ---- 第二类：可折叠标题（1/2/3） ----
                 *
                 * 格子宽度刻意与上一行保持一致（用户要求"格式与普通标题保持一致"）：
                 * 三个格子各占 1 份、再补三个等权 [Spacer] 占位，使整行仍是 6 列网格，
                 * 于是本行格宽 = 上一行格宽，且靠左对齐、右侧留白。
                 */
                HeadingSectionTitle(text = "可折叠标题")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HeadingCellGap)
                ) {
                    CollapsibleHeadingItems.forEach { (label, action) ->
                        HeadingCell(
                            label = label,
                            onClick = { onTransform(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    /** 占位格：数量 = 普通标题格数 − 可折叠标题格数（当前 6 − 3 = 3） */
                    repeat(NormalHeadingItems.size - CollapsibleHeadingItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/**
 * 分区标题（如「普通标题」/「可折叠标题」）
 *
 * @param text 分区名称
 */
@Composable
private fun HeadingSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, start = 2.dp)
    )
}

/**
 * 单个标题格子（等宽）
 *
 * 视觉对齐面板其余部分：圆角 8dp、1dp 描边、surfaceVariant 半透明底，
 * 文字居中；点击整格可点（44dp 高度保证触摸目标）。
 *
 * @param label 格子文字（"H1"…"H6" / "1"…"3"）
 * @param onClick 点击回调（转换为对应标题块类型）
 * @param modifier Modifier（由调用方传 `weight(1f)` 决定格宽）
 */
@Composable
private fun HeadingCell(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(HeadingCellHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
