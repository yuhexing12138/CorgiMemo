package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用色板：一行「默认（斜杠）+ BlockNote 官方 9 个预设色」
 *
 * 历史：v1.11 引入时是 RichTextFormatToolbar 内的 private 实现，只服务于 ⋮ 菜单的
 * 「背景色 / 文字色」两项**块级**颜色。v2026-09-21 按需求把块级颜色入口整体移入
 * 底部工具栏「A」按钮弹出的颜色对话框，且用户明确要求该对话框里的**四组**色板
 * 「统一按 ⋮ 里的样式设置，包括颜色图标样式」——于是抽到本文件，供两处共用。
 *
 * 对外提供三样东西（`internal`，同 module 内可见）：
 * - [BlockColorPalette]：色名 ↔ 色值映射（BlockNote 官方明暗两套）
 * - [blockColorHexOf]：色名 → "#RRGGBB" 文本（行内色下行需要 hex）
 * - [BlockColorRow]：一行色板（标签 + 默认色点 + 9 个预设色点）
 */

/**
 * BlockNote 官方块级色板（v1.11）
 *
 * 色名与色值**逐条对应** `@blocknote/core/src/editor/defaultColors.ts`：
 * - 亮色 → `COLORS_DEFAULT`
 * - 暗色 → `COLORS_DARK_MODE_DEFAULT`
 *
 * 为何在宿主硬编码而非由 JS 上行：色板必须在弹出的**同一帧**就渲染出来，
 * 不能等一次上下行往返；而色名属于桥协议的一部分（稳定），色值极少变动。
 * 若 BlockNote 升级调整了色值，同步本表即可——**改前先读上述源文件确认**，
 * 不要凭印象填色。
 *
 * 注意 `default`（清除）不是色板成员而是调用方传的特殊值，故不在 [names] 中。
 */
internal object BlockColorPalette {
    /** 预设色名，顺序与官方 ColorPicker 一致 */
    val names = listOf(
        "gray", "brown", "red", "orange", "yellow", "green", "blue", "purple", "pink"
    )

    /** 亮色模式：色名 → (文本色 hex, 背景色 hex) */
    private val lightMap = mapOf(
        "gray" to ("#9b9a97" to "#ebeced"),
        "brown" to ("#64473a" to "#e9e5e3"),
        "red" to ("#e03e3e" to "#fbe4e4"),
        "orange" to ("#d9730d" to "#f6e9d9"),
        "yellow" to ("#dfab01" to "#fbf3db"),
        "green" to ("#4d6461" to "#ddedea"),
        "blue" to ("#0b6e99" to "#ddebf1"),
        "purple" to ("#6940a5" to "#eae4f2"),
        "pink" to ("#ad1a72" to "#f4dfeb")
    )

    /** 暗色模式：色名 → (文本色 hex, 背景色 hex) */
    private val darkMap = mapOf(
        "gray" to ("#bebdb8" to "#9b9a97"),
        "brown" to ("#8e6552" to "#64473a"),
        "red" to ("#ec4040" to "#be3434"),
        "orange" to ("#e3790d" to "#b7600a"),
        "yellow" to ("#dfab01" to "#b58b00"),
        "green" to ("#6b8b87" to "#4d6461"),
        "blue" to ("#0e87bc" to "#0b6e99"),
        "purple" to ("#8552d7" to "#6940a5"),
        "pink" to ("#da208f" to "#ad1a72")
    )

    /** 取某色名的**文本色**（色板"文字色"维度用） */
    fun text(name: String, isDark: Boolean): Color = pick(name, isDark, 0)

    /** 取某色名的**背景色**（色板"背景色"维度用） */
    fun background(name: String, isDark: Boolean): Color = pick(name, isDark, 1)

    /**
     * 未知色名回落透明色。
     * 刻意不抛异常——色板是纯展示层，某个色名对不上不应该让整行渲染失败。
     */
    private fun pick(name: String, isDark: Boolean, index: Int): Color {
        val pair = (if (isDark) darkMap else lightMap)[name] ?: return Color.Transparent
        return hexToColor(if (index == 0) pair.first else pair.second)
    }

    /** "#RRGGBB" → Compose Color（自行解析，避免与 Compose 的 Color 撞名而需别名 import） */
    private fun hexToColor(hex: String): Color {
        val v = hex.removePrefix("#").toLongOrNull(16) ?: return Color.Transparent
        return Color(0xFF000000L or v)
    }
}

/**
 * 色名 → 该维度色值的 "#RRGGBB" 文本（v2026-09-21 新增）
 *
 * **为什么需要它**：块级颜色下行传的是**色名**（`setBlockColor`），而行内颜色
 * （`format("textColor"/"backgroundColor", value)`）下行的是**自由 hex 值**。
 * 但两种色板现在共用同一张 [BlockColorPalette]，所以点选行内色时要把色名换成
 * hex 再下发——这样"色板显示的颜色"与"编辑器实际渲染的颜色"必然同源，
 * 也避免再维护第二份硬编码色值表。
 *
 * @param name BlockNote 预设色名
 * @param isDark 是否暗色主题（决定取哪套色值）
 * @param background true = 取背景色维度；false = 取文字色维度
 */
internal fun blockColorHexOf(name: String, isDark: Boolean, background: Boolean): String =
    composeColorToHex(
        if (background) {
            BlockColorPalette.background(name, isDark)
        } else {
            BlockColorPalette.text(name, isDark)
        }
    )

/**
 * 行内色 hex → 色名（[blockColorHexOf] 的**反函数**，v2026-09-22 新增）
 *
 * **用途**：A 面板两个「选中色」行的选中回显。行内色的状态上行是 **hex**（因为下发就是 hex），
 * 而 [BlockColorRow] 判定选中靠**色名**（`current == name`），故必须反查。
 *
 * **三种取值语义**（返回串直接喂给 `BlockColorRow.current`）：
 * - `hex` 为空 → 返回 `""` → 调用方判定为「默认」→ **高亮第一个「/」清除块**
 *   （这正是"未设置行内色"的期望表现）；
 * - `hex` 命中当前主题色板 → 返回对应**色名** → 该色点高亮；
 * - `hex` 不在色板里（如从外部粘贴的自定义色，或切换主题后色板值已变）→ **原样返回 hex**：
 *   此时既非空串也非 `"default"`，`BlockColorRow` 不会去点亮「默认」块，也不会误点亮任何
 *   色点 —— 即"无高亮"，比谎报成"默认"更诚实。
 *
 * ⚠️ 需传入**当前主题**的 `isDark`：色板明暗两套色值不同，用错主题会反查失败。
 *
 * @param hex 行内色值（JS 侧 `getActiveStyles()` 的原始串）
 * @param isDark 是否暗色主题（决定用哪套色值反查）
 * @param background true = 背景色维度；false = 文字色维度
 */
internal fun blockColorNameOf(hex: String, isDark: Boolean, background: Boolean): String {
    val value = hex.trim()
    if (value.isEmpty()) return ""
    return BlockColorPalette.names.firstOrNull { name ->
        blockColorHexOf(name, isDark, background).equals(value, ignoreCase = true)
    } ?: value
}

/** Compose Color → "#RRGGBB"（忽略 alpha；与宿主其余 hex 工具语义一致） */
private fun composeColorToHex(c: Color): String = String.format(
    java.util.Locale.US,
    "#%02X%02X%02X",
    (c.red * 255).toInt(),
    (c.green * 255).toInt(),
    (c.blue * 255).toInt()
)

/* ===== 色板排版常量（v2026-09-21）===== */

/**
 * 色点行的左右边距
 *
 * 即**首尾色点距屏幕边缘的距离**：按用户要求保持现状值——自适应只作用于"点与点之间"，
 * 首尾留白不变（同时也是与面板头标题左对齐的视觉基准）。
 */
private val ColorRowHorizontalPadding = 12.dp

/** 色点自适应边长的下限：低于 24dp 触摸目标过小 */
private val MinColorDotSize = 24.dp

/** 色点自适应边长的上限：平板等宽屏下避免变成大色块 */
private val MaxColorDotSize = 36.dp

/** 色点之间的最小间距（折行时同时作为行间距） */
private val ColorDotGap = 8.dp

/**
 * 色板选择行（v1.11 引入，v2026-09-21 迁出并两次调整）
 *
 * 一行色点：最左为「默认」（清除该维度颜色），其后是 9 个 BlockNote 预设色。
 * 当前生效色以暖橙粗描边标记，与工具栏其余按钮的激活态配色保持一致。
 *
 * v2026-09-21 调整：
 * 1. 色点行由 `Row`（强制单行）改为 [FlowRow]（可换行）：原在 AlertDialog 内容区里
 *    单行放不下会被裁掉最后一个；FlowRow 在窄容器里自动折行、在宽容器里保持单行。
 * 2. 新增 [showSelection]：行内色两组**没有**状态上行（宿主拿不到"光标处已有的
 *    行内颜色"），传 false 关闭选中回显，否则"默认"项会恒定高亮造成误导。
 * 3. **占满横向空间并均匀分布**（用户要求）：色点边长按可用宽度自适应
 *    （见 [MinColorDotSize] / [MaxColorDotSize]），分布改为 `SpaceBetween`——
 *    首尾色点各距屏幕边缘 [ColorRowHorizontalPadding]（保持原值），中间的点等距铺满。
 *
 * @param label 行标题（如"段落背景色"）
 * @param current 当前生效色名（空串或 "default" 视为默认色）
 * @param isDark 是否暗色主题（决定取哪套色值）
 * @param picker 色名 → Compose Color（背景色与文字色取不同维度，故由调用方注入）
 * @param onPick 点选回调（参数为色名；"default" = 清除）
 * @param showSelection 是否回显当前选中色（false = 全部不显示选中态，仅作取色用）
 * @param modifier Modifier（作用于整行容器，供容器控制宽度/间距）
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BlockColorRow(
    label: String,
    current: String,
    isDark: Boolean,
    picker: (String, Boolean) -> Color,
    onPick: (String) -> Unit,
    showSelection: Boolean = true,
    modifier: Modifier = Modifier
) {
    /** 空串与 "default" 都表示"未设置该维度颜色"（前者来自未上报，后者是 BlockNote 的清除值） */
    val isDefault = current.isEmpty() || current == "default"

    /** 色点总数 = 「默认（清除）」1 个 + BlockNote 官方预设色 9 个 */
    val dotCount = BlockColorPalette.names.size + 1

    Column(
        modifier = modifier.padding(horizontal = ColorRowHorizontalPadding, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        /**
         * [BoxWithConstraints] 只为拿到本行的**实际可用宽度**（外层 padding 之后），
         * 用屏宽常量硬算会在分屏 / 折叠屏 / 平板等场景下失真。
         */
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            val available = maxWidth
            /**
             * 自适应色点边长：让 dotCount 个点 + (dotCount−1) 个间距**恰好铺满**可用宽度。
             *
             * clamp 到 [MinColorDotSize] / [MaxColorDotSize] 两端：
             * 极窄屏不缩到难以点中，平板不撑成色块；被 clamp 截断时（点变小/变大后
             * 与可用宽度不再相等）剩余空间交由 `SpaceBetween` 平均吸收，**观感仍均匀**。
             */
            val dotSize = ((available - ColorDotGap * (dotCount - 1)) / dotCount)
                .coerceIn(MinColorDotSize, MaxColorDotSize)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                /** 首尾贴容器两端（= 距屏幕边缘各 [ColorRowHorizontalPadding]），中间等距 */
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(ColorDotGap)
            ) {
                BlockColorDot(
                    size = dotSize,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    selected = showSelection && isDefault,
                    onClick = { onPick("default") },
                    contentDescription = "$label 默认",
                    showSlash = true
                )
                BlockColorPalette.names.forEach { name ->
                    BlockColorDot(
                        size = dotSize,
                        color = picker(name, isDark),
                        selected = showSelection && !isDefault && current == name,
                        onClick = { onPick(name) },
                        contentDescription = "$label $name"
                    )
                }
            }
        }
    }
}

/**
 * 单个色点（v1.11；v2026-09-21 起尺寸由调用方按可用宽度自适应传入）
 *
 * @param size 色点边长（由 [BlockColorRow] 按屏宽/容器宽度动态计算）
 * @param color 填充色
 * @param selected 是否当前选中（暖橙 2dp 描边）
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 * @param showSlash 是否画一条斜杠表示「不设置颜色」（用于"默认"项，避免与纯白底色混淆）
 */
@Composable
private fun BlockColorDot(
    size: Dp,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    showSlash: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    Color(0xFFFF9A5C)
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (showSlash) {
            Text(
                text = "／",
                /**
                 * 斜杠随色点等比缩放（保持原 24dp : 12sp 的比例），
                 * 否则点变大时斜杠会缩在圆心显得不协调。
                 */
                fontSize = (size.value * 0.5f).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
