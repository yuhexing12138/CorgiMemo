package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.screens.probe.BlockNotePlusMenuIcons

/* ===== 标题面板排版常量（v2026-09-21）===== */

/** 标题格子之间的横向间距（6 格等分整行时的间隙） */
private val HeadingCellGap = 8.dp

/** 标题格子高度：44dp 保证触摸目标够大（Material 建议 ≥48dp，此处按面板空间折中） */
private val HeadingCellHeight = 44.dp

/** 面板内容左右内边距：与面板头标题左边缘对齐（亦与「A」面板的色板行同值 12dp） */
private val HeadingPanelHorizontalPadding = 12.dp

/**
 * 分区标题的上下内边距（v2026-09-21 新增）
 *
 * 取 6dp，与「A」面板里 [BlockColorRow] 的 vertical padding **同值**——于是
 * - 「分区标题 → 内容」= 6dp
 * - 「相邻分区之间」= 上 6 + 下 6 = 12dp
 * 与色板行的节奏逐项一致（用户要求"完全按 A 面板节奏"），故分区之间**不再需要额外 Spacer**。
 */
private val SectionTitleVerticalPadding = 6.dp

/** 面板内容区上下留白（与「A」面板 [ColorStylePanel] 内容区一致，v2026-09-21） */
private val HeadingPanelVerticalPadding = 4.dp

/* ===== 正文字号档位（v2026-09-21 由 FontSizeColorPanel 迁入）===== */

/**
 * 正文字号候选档位（sp，对照已审核原型 8 档），默认档 = [DEFAULT_BODY_SP]。
 *
 * 原属「字号与颜色」面板（Aa）；该面板已整体删除（其颜色部分与新「A」面板的
 * 「选中文字色」重叠），字号部分迁入本面板，作为**正文字号**设置入口。
 */
val FONT_SIZE_TIERS = listOf(12, 14, 16, 18, 20, 24, 28, 32)

/**
 * 正文默认字号（MaterialTheme bodyLarge = 16sp，与原型「正文默认 16sp」一致）。
 *
 * ⚠️ 本常量被 [com.corgimemo.app.ui.screens.inspiration.InspirationEditScreen]
 * 直接引用（判断"是否默认档" → 下发 `default` 清除而不是写死 px），
 * 故**保持 public**，随字号功能一起从 FontSizeColorPanel 迁来。
 */
const val DEFAULT_BODY_SP = 16

/** 「正文字号」格子的图标名（Remix `RiFontSize`，与其余图标走同一渲染管线） */
private const val TextSizeIconName = "RiFontSize"

/**
 * 「普通标题」条目：Ri 图标名 → 下行 action（v2026-09-21 由工具栏「组三 Headings」迁入）
 *
 * 图标名与工具栏移动前**逐字一致**（`RiH1`–`RiH6`，取自
 * [BlockNotePlusMenuIcons]；与浮层/工具栏渲染逐字节同款），
 * 面板里用同一套图标渲染，视觉与移动前保持连续（用户要求）。
 * action 与桥协议既有取值一致（见 [RichTextFormatToolbar] 的 `onTransform` KDoc）：
 * `heading1`–`heading6`。
 */
private val NormalHeadingItems = listOf(
    "RiH1" to "heading1",
    "RiH2" to "heading2",
    "RiH3" to "heading3",
    "RiH4" to "heading4",
    "RiH5" to "heading5",
    "RiH6" to "heading6"
)

/**
 * 「可折叠标题」条目：Ri 图标名 → 下行 action（v2026-09-21 由工具栏「组四 Subheadings」迁入）
 *
 * 图标同样用 `RiH1`–`RiH3`（用户要求：不用文字标签、也不用工具栏原有的 `▸1`
 * —— 后者真机渲染异常，截图中显示成 `-1`）；两类标题的区分靠分区标题
 * 「普通标题 / 可折叠标题」，图标保持一致。
 *
 * action 取值沿用原实现：`toggleHeading` / `toggleHeading2` / `toggleHeading3`
 * （注意 1 级没有后缀数字，是 BlockNote 侧的既有约定，别"顺手改整齐"）。
 */
private val CollapsibleHeadingItems = listOf(
    "RiH1" to "toggleHeading",
    "RiH2" to "toggleHeading2",
    "RiH3" to "toggleHeading3"
)

/**
 * 编辑页「标题与字号」面板（内联面板；v2026-09-21 新增）
 *
 * 由 [InspirationEditBottomBar] 插入在「格式工具栏」与「相机行」之间，与
 * [FontPickerPanel]（T）、[ColorStylePanel]（A）
 * **三者互斥、占同一槽位、同高度**（面板高度 = 键盘高度，互斥切换不跳动）。
 *
 * **来源**：
 * - 原工具栏「组三 Headings（RiH1–RiH6）」与「组四 Subheadings（▸1–▸3）」共 9 个按钮
 *   占用格式工具栏大量横向空间，按需求整体移入本面板（分「普通标题 / 可折叠标题」两类）；
 * - 原「Aa 字号与颜色」面板的**字号**部分迁入本面板，作为**正文字号**设置
 *   （该面板 v2026-09-21 整体删除：字号迁此，颜色与「A」面板重叠）。
 *
 * **布局**：
 * - 面板头（40dp）：左「标题与字号」标题 + 「点选即生效」提示，右「完成」文字按钮
 * - 分隔线（1dp，outlineVariant 50%）
 * - 内容区（`weight(1f)` + 纵向滚动），三个分区依次为：
 *   - 「正文字号」：8 档一行 `SpaceBetween` 等分，每格 = [TextSizeIconName] 图标 + 档位数值
 *   - 「普通标题」：H1–H6 **6 格等分整行**
 *   - 「可折叠标题」：与上一行**同格宽**（右侧用等权 Spacer 占位对齐 6 列网格）
 *
 * **行距节奏**（v2026-09-21 用户要求"完全按「A」面板节奏"）：分区标题上下各
 * [SectionTitleVerticalPadding]（6dp，与 [BlockColorRow] 的 vertical padding 同值）→
 * 「标签 → 内容」6dp、「相邻分区之间」12dp；内容区上下留白 [HeadingPanelVerticalPadding]（4dp）；
 * 因此**分区之间不放额外 Spacer**（放了就比 A 面板松）。
 *
 * **生效语义**（与 T / A 面板一致）：点选**即时生效且不收起面板**，收起由面板头
 * 「完成」或再点一次工具栏「H」按钮触发。转换结果为块级属性，重复点同一项即"再转换一次"。
 *
 * **选中态回显**（v2026-09-21 已实现）：字号档位来自 `richTextState`（见 [currentFontSize]）；
 * 标题格子来自 JS 上行的 `blockState.headingLevel` + `blockType`
 * （⚠️ 两类标题必须靠 blockType 区分，级别数字 1/2/3 在两类里都有）。
 *
 * @param panelHeight 面板总高度（= 键盘高度；内容超出纵向滚动）
 * @param onTransform 块类型转换回调（参数为 action：heading1–6 / toggleHeading / toggleHeading2 / 3）
 * @param onFontSizeSelect **正文字号**档位点选回调（参数为档位 sp 值，v2026-09-21 由 Aa 面板迁入）
 * @param onDone 点击面板头「完成」（收起面板）
 * @param currentBlockType 当前光标块类型（JS 侧 `blockState` 上行，v2026-09-21 起用于回显）：
 *   与 [currentHeadingLevel] 配合区分两类标题——`heading` = 普通标题、`toggleHeading*` = 可折叠标题。
 *   **同一级别数字在两类里含义不同，必须靠 blockType 区分，不能只看 level**
 * @param currentHeadingLevel 当前光标块的标题级别（普通标题 1–6 / 可折叠标题 1–3；
 *   0 = 非标题块 → 不高亮任何格子）
 * @param currentFontSize 当前生效字号（sp；未指定时回落 [DEFAULT_BODY_SP]，用于字号档位高亮）
 * @param enabled 面板**内容区**是否可用（v2026-09-21 新增）：宿主锁定态传 false——
 *   所有格子整片降到 38% 不透明度，并在 `PointerEventPass.Initial` 阶段拦截点击
 *   （与格式工具栏的 `toolbarEnabled` 口径一致）。⚠️ 面板头「完成」不受影响，
 *   锁定态下仍可收起面板，否则面板会"关不掉"。
 * @param modifier Modifier
 */
@Composable
internal fun HeadingPanel(
    panelHeight: Dp,
    onTransform: (String) -> Unit,
    onFontSizeSelect: (Int) -> Unit,
    onDone: () -> Unit,
    currentBlockType: String = "",
    currentHeadingLevel: Int = 0,
    currentFontSize: Int = DEFAULT_BODY_SP,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    /**
     * 当前块对应的标题级别（0 = 两类都不高亮）
     *
     * 两类标题在 JS 侧是**不同块类型**（`heading` vs `toggleHeading*`），
     * 而级别数字（1/2/3）在两类里都出现，故必须用 blockType 分流，
     * 否则"当前是 H2"会让两类的 H2 格子同时亮起。
     */
    val currentNormalLevel =
        if (currentBlockType == "heading") currentHeadingLevel else 0
    val currentCollapsibleLevel =
        if (currentBlockType.startsWith("toggleHeading")) currentHeadingLevel else 0

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
                    text = "标题与字号",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "点选即生效",
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
                    /** 左右 12dp 与「A」面板同值；上下 4dp 对齐其内容区留白（v2026-09-21） */
                    .padding(
                        horizontal = HeadingPanelHorizontalPadding,
                        vertical = HeadingPanelVerticalPadding
                    )
                    /** 锁定态视觉降级：与格式工具栏 disabled 态同款 38% 不透明度 */
                    .alpha(if (enabled) 1f else 0.38f)
                    .then(contentBlocker)
            ) {
                /**
                 * ---- 第一类：正文字号（v2026-09-21 由 Aa 面板迁入） ----
                 *
                 * 8 档**一字排开**（一行 8 等分）。字号无法只靠图标表达档位，故每格 =
                 * [TextSizeIconName] 图标 + 档位数值；若不排两行是因为 8 档两行会把面板
                 * 撑到必须滚动（面板高 ≈ 键盘高度）。
                 *
                 * 用 `SpaceBetween` 而非 `spacedBy(gap)`：格子无底色/无边框，视觉间距由
                 * 居中的内容本身决定，等分即天然均匀，无需再留固定间隙。
                 */
                HeadingSectionTitle(text = "正文字号")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FONT_SIZE_TIERS.forEach { tier ->
                        TextSizeCell(
                            tier = tier,
                            selected = currentFontSize == tier,
                            onClick = { onFontSizeSelect(tier) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                /** ---- 第二类：普通标题（H1–H6，6 格等分整行） ---- */
                HeadingSectionTitle(text = "普通标题")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HeadingCellGap)
                ) {
                    NormalHeadingItems.forEachIndexed { index, (riName, action) ->
                        HeadingCell(
                            riName = riName,
                            label = "H${index + 1}",
                            selected = currentNormalLevel == index + 1,
                            onClick = { onTransform(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                /**
                 * ---- 第三类：可折叠标题（图标沿用 RiH1–RiH3） ----
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
                    CollapsibleHeadingItems.forEachIndexed { index, (riName, action) ->
                        HeadingCell(
                            riName = riName,
                            label = "H${index + 1}",
                            selected = currentCollapsibleLevel == index + 1,
                            onClick = { onTransform(action) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    /** 占位格：数量 = 普通标题格数 − 可折叠标题格数（当前 6 − 3 = 3） */
                    repeat(NormalHeadingItems.size - CollapsibleHeadingItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 分区标题（如「正文字号」/「普通标题」/「可折叠标题」）
 *
 * 间距节奏对齐「A」面板（v2026-09-21 用户要求，参照 [ColorStylePanel] 里 [BlockColorRow] 的行结构）：
 * - 标签字号同为 11sp、标签色同为 onSurfaceVariant；
 * - 上下各 [SectionTitleVerticalPadding]（6dp，与色板行的 vertical padding 同值）→
 *   「标签 → 内容」6dp、「相邻分区之间」12dp，逐项一致；
 * - 因此分区之间**不放额外 Spacer**（放了就比 A 面板松）。
 *
 * @param text 分区名称
 */
@Composable
private fun HeadingSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            top = SectionTitleVerticalPadding,
            bottom = SectionTitleVerticalPadding,
            start = 2.dp
        )
    )
}

/**
 * 单个标题格子
 *
 * 内容为 **Ri 图标**（与工具栏移动前同款矢量图标，用户要求保持连续）。
 * v2026-09-21 按用户要求**去掉底色与格子框**：面板里只留图标本体，视觉更轻；
 * 选中态改用**图标颜色**标识（暖橙），与工具栏按钮的激活态语义完全一致。
 *
 * 格子仍保留 [HeadingCellHeight] 高度且**整格可点**（`clickable` 挂在 Box 上），
 * 触摸目标不受影响，只是不再绘制可见的框。
 *
 * @param riName Ri 图标名（`RiH1`–`RiH6`，取自 [BlockNotePlusMenuIcons]）
 * @param label 无障碍描述 / 图标缺失时的兜底文字（"H1"…"H6"）
 * @param selected 是否为当前光标块对应的标题（true = 图标转暖橙）
 * @param onClick 点击回调（转换为对应标题块类型）
 * @param modifier Modifier（由调用方传 `weight(1f)` 决定格宽）
 */
@Composable
private fun HeadingCell(
    riName: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 激活色与工具栏/色板统一（暖橙）；未选中取与工具栏未激活按钮一致的 onSurfaceVariant */
    val accent = Color(0xFFFF9A5C)
    val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val vector = remember(riName) { BlockNotePlusMenuIcons.vectorFor(riName) }

    Box(
        modifier = modifier
            .height(HeadingCellHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (vector != null) {
            Icon(
                imageVector = vector,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        } else {
            /** 图标缺失兜底：退回文字标签（与工具栏 [RichTextFormatToolbar] 的 RiFormatButton 同策略） */
            Text(
                text = label,
                fontSize = 13.sp,
                color = contentColor
            )
        }
    }
}

/**
 * 正文字号档位格子（图标 + 数值竖排，v2026-09-21 新增）
 *
 * 与 [HeadingCell] 同一视觉语言：**无底色、无格子框**，只有内容本体 +
 * [HeadingCellHeight] 高的整格点击区；选中态 = 图标与数值一并转暖橙。
 *
 * 为什么还要数值：字号档位（12–32sp）无法只靠图标表达，数值是唯一的档位标识；
 * 图标只承担"这是字号设置"的语义（用户要求从 Ri 里选合适的图标）。
 *
 * @param tier 档位字号（sp）
 * @param selected 是否为当前生效档位
 * @param onClick 点选回调（参数为档位 sp，由调用方写 SpanStyle）
 * @param modifier Modifier（由调用方传 `weight(1f)` 决定格宽）
 */
@Composable
private fun TextSizeCell(
    tier: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = Color(0xFFFF9A5C)
    val contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val vector = remember { BlockNotePlusMenuIcons.vectorFor(TextSizeIconName) }

    Box(
        modifier = modifier
            .height(HeadingCellHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        /** 图标与数值竖排：8 等分后每格约 42dp 宽，竖排比横排更省横向空间 */
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (vector != null) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = tier.toString(),
                fontSize = 10.sp,
                color = contentColor
            )
        }
    }
}
