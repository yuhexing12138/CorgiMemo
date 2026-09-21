package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.screens.probe.BlockState
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * 底部栏内联面板标识（v2026-09-21 新增）
 *
 * 「T / Aa / H / A」四个按钮各自展开一个面板，四者**互斥且共用同一槽位**
 * （见 [InspirationEditBottomBar] 的"上行二 / 二b / 二c / 二d"）。
 *
 * **为什么用单一枚举而不是多个 boolean**：多个 boolean 之间的互斥只能靠调用方
 * "记得把其余一并置 false"来维持——v2026-09-21 就漏过一次（从 A 切到 T / Aa 时
 * 两个面板同时可见、把下方按钮行顶下去）。收敛为「当前展开的是哪一个（或 null）」
 * 之后，**互斥由状态本身保证**：任何时刻只可能有一个值，无处可漏。
 *
 * @property FONT 字体选择面板（工具栏 T，[FontPickerPanel]）
 * @property SIZE_COLOR 字号与颜色面板（工具栏 Aa，[FontSizeColorPanel]）
 * @property COLOR 颜色面板（工具栏 A，[ColorStylePanel]）
 * @property HEADING 标题面板（工具栏 H，[HeadingPanel]；v2026-09-21 新增，
 *   收纳原工具栏「Headings H1–H6」与「Subheadings 可折叠标题 1–3」共 9 键）
 */
enum class EditBottomPanel { FONT, SIZE_COLOR, COLOR, HEADING }

/**
 * 灵感编辑页底部导航栏
 *
 * 布局结构：
 * - 上行一（可折叠）：RichTextFormatToolbar（仅当 isFormatExpanded=true 时显示）
 * - 上行二（可折叠）：FontPickerPanel 字体选择面板（仅当 openPanel == FONT 时显示，
 *   高度 = 软键盘高度；展开时相机行被向下推开，见原型「工具栏/灵感编辑页字体选择面板.html」）
 * - 上行二b（可折叠）：FontSizeColorPanel 字号与颜色面板（v2026-09-04 新增，
 *   仅当 openPanel == SIZE_COLOR 时显示；与前后面板**互斥、占同一槽位、同高度**；
 *   字号/颜色点选即时生效，面板头只保留「完成」收起）
 * - 上行二c（可折叠）：ColorStylePanel 颜色面板（v2026-09-21 新增，
 *   仅当 openPanel == COLOR 时显示；与另两个面板**互斥、占同一槽位、同高度**；
 *   四组色板＝选中文字色/选中背景色（行内）+ 段落文字色/段落背景色（块级），
 *   点选即时生效且不收起面板，面板头只保留「完成」收起。
 *   由原「A」按钮的 AlertDialog 弹窗改造而来，展示形态与 T / Aa 统一）
 * - 上行二d（可折叠）：HeadingPanel 标题面板（v2026-09-21 新增，
 *   仅当 openPanel == HEADING 时显示；与另三个面板**互斥、占同一槽位、同高度**；
 *   分「普通标题 H1–H6」「可折叠标题 1–3」两类，点选即转换为对应块类型且不收起面板。
 *   收纳原格式工具栏里的 9 个标题键，使工具栏变短）
 * - 下行（始终显示）：6 个核心按钮
 *   - 📷 相机（onPhotoClick）
 *   - 🎤 麦克风（onVoiceClick）
 *   - # 标签（onTagClick）—— v2026-07-22 改造：原"位置"按钮改为"添加标签"功能
 *   - @ 关联（onMentionClick）—— v2026-07-22 改造：触发 RelationPickerBottomSheet 多选弹窗（与待办编辑页一致）
 *   - 📍 位置（onLocationClick）—— v2026-07-22 新增：独立位置按钮，使用 Icons.Default.LocationOn 图标
 *   - ⋮ 格式（onFormatToggleClick，切换上行展开/折叠）
 *
 * **交互规则**：
 * - 只有 ⋮ 按钮切换工具栏展开/折叠
 * - 字体选择按钮（工具栏 T）切换字体面板展开/收起，同时由调用方收起软键盘
 * - 字号与颜色按钮（工具栏 Aa，位于 T 与 H 之间）切换字号颜色面板展开/收起，与其余面板互斥，同时由调用方收起软键盘
 * - 标题按钮（工具栏 H，位于 Aa 与 A 之间，v2026-09-21 新增）切换标题面板展开/收起，同样互斥
 * - 颜色按钮（工具栏 A，位于 H 与 B 之间）切换颜色面板展开/收起，同样互斥；
 *   四个按钮连排（T → Aa → H → A），都是"展开底部内联面板"的同类入口
 * - 四个面板展开期间**键盘让位**（v2026-09-21）：调用方据此抑制正文 WebView 与顶部标题
 *   重新唤起软键盘，避免键盘把面板顶走、并压缩 WebView 视口；面板收起后仅恢复
 *   "可唤起"能力，不主动弹回键盘
 * - 其他按钮的操作不影响工具栏状态
 * - 默认折叠（isFormatExpanded=false）
 *
 * @param isFormatExpanded 格式工具栏是否展开
 * @param openPanel 当前展开的内联面板（null = 全部收起）；T / Aa / A 三者互斥占同一槽位，
 *   由单一可空枚举表达，见 [EditBottomPanel]
 * @param currentCjkId 当前灵感的中文字体 id（面板回显选中态；空 = 系统默认字体）
 * @param currentLatinId 当前灵感的英文/数字字体 id（空 = 跟随中文，无选中高亮）
 * @param hasPendingChange 字体面板是否存在「已点选未应用」的改动
 *   （true = 面板头按钮显示「应用」且点击后不收起；false = 显示「完成」且点击收起）
 * @param currentFontSize 当前生效字号（sp；字号颜色面板高亮回显，未指定 = DEFAULT_BODY_SP）
 * @param currentColorIdx 当前生效的预设色下标（0 = 默认；自定义色生效时高亮让位）
 * @param customColorHex 当前生效的自定义色（"#RRGGBB"）；null = 无自定义色
 * @param richTextState 库的 RichTextState 实例（传给 RichTextFormatToolbar）
 * @param onPhotoClick 相机按钮回调
 * @param onVoiceClick 麦克风按钮回调
 * @param onTagClick 标签按钮回调（v2026-07-22 新增：原 onLocationClick 拆分而来，触发 TagPickerSheet）
 * @param onMentionClick 关联按钮回调（v2026-07-22 改造：触发 RelationPickerBottomSheet）
 * @param onLocationClick 位置按钮回调（v2026-07-22 新增：触发位置提醒弹窗）
 * @param onFormatToggleClick 格式按钮回调（切换展开/折叠）
 * @param onFontPickerClick 字体选择按钮回调（切换面板展开/收起；调用方同时收起软键盘）
 * @param onFontPanelDismiss 字体面板头按钮回调（「应用」= 应用字体不收起；「完成」= 收起面板）
 * @param onSizeColorPanelClick 字号与颜色按钮回调（切换面板展开/收起；调用方同时收起软键盘、关字体面板）
 * @param onSizeColorPanelDismiss 字号颜色面板头「完成」回调（收起面板）
 * @param onHeadingPanelClick 标题按钮回调（v2026-09-21 新增：切换标题面板展开/收起；调用方同时收起软键盘）
 * @param onHeadingPanelDismiss 标题面板头「完成」回调（收起面板）
 * @param onFontSizeSelect 字号点选回调（参数为档位 sp；调用方写 fontSize SpanStyle，点选即时生效）
 * @param onPresetColorSelect 预设色点选回调（参数为 TEXT_COLORS 下标；调用方写 color SpanStyle）
 * @param onCustomColorSelect 自定义取色回调（拖动每帧回调，参数 "#RRGGBB"）
 * @param onCjkFontSelect 中文字体选择回调（参数为字体 id）
 * @param onLatinFontSelect 英文/数字字体选择回调；再点已选项时回调空串表示取消（跟随中文）
 * @param onSetFontWeight 设置字重档位回调（参数为当前字体 FontEntry.boldTiers 候选档位；
 *      其中经像素探测无独立字形的档位在工具栏中置灰禁用）
 * @param onDecreaseIndent 减少缩进回调（透传格式工具栏）
 * @param onInsertDivider 插入分割线回调（v2026-09-07 新增：聚焦块光标处拆块插入，透传格式工具栏）
 * @param onToggleCheckbox 复选框回调（v2026-09-07 新增：聚焦块在 复选框块 ↔ 普通块 间切换，透传格式工具栏）
 * @param isCheckboxActive 聚焦块是否为复选框块（v2026-09-07 新增：复选框按钮激活态高亮，透传格式工具栏）
 * @param onToggleItalic 斜体回调
 * @param onToggleUnderline 下划线回调
 * @param onToggleStrikethrough 删除线回调
 * @param onInsertUnorderedList 无序列表回调
 * @param onInsertOrderedList 有序列表回调
 * @param onAlignLeft 左对齐回调
 * @param onAlignCenter 居中回调
 * @param onAlignRight 右对齐回调
 * @param onInsertLink 插入链接回调
 * @param onToggleCodeSpan 代码块回调
 * @param modifier Modifier
 * @param backgroundColor 工具栏背景色
 */
@Composable
fun InspirationEditBottomBar(
    isFormatExpanded: Boolean,
    /**
     * 当前展开的内联面板（null = 全部收起）
     *
     * v2026-09-21 由原先的 `isFontPanelOpen` / `isSizeColorPanelOpen` / `isColorPanelOpen`
     * 三个 boolean **收敛为单一状态**：三个面板互斥占同一槽位（"上行二 / 二b / 二c"），
     * 用可空枚举表达后互斥由状态本身保证，调用方无需再逐个置 false
     * ——见 [EditBottomPanel] 的说明。
     */
    openPanel: EditBottomPanel?,
    currentCjkId: String,
    currentLatinId: String,
    hasPendingChange: Boolean,
    currentFontSize: Int,
    currentColorIdx: Int,
    customColorHex: String?,
    /**
     * 格式工具栏激活态用的富文本状态（聚焦块）。
     * BlockNote 接管正文后仅用于按钮 isActive 回显，不参与内容读写。
     */
    richTextState: RichTextState,
    onPhotoClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onTagClick: () -> Unit,
    onMentionClick: () -> Unit,
    onLocationClick: () -> Unit,
    onFormatToggleClick: () -> Unit,
    onFontPickerClick: () -> Unit,
    onFontPanelDismiss: () -> Unit,
    onSizeColorPanelClick: () -> Unit,
    onSizeColorPanelDismiss: () -> Unit,
    /** 标题按钮（H）回调（v2026-09-21 新增）：切换**底部内联标题面板**（[HeadingPanel]）展开/收起 */
    onHeadingPanelClick: () -> Unit = {},
    /** 标题面板头「完成」回调（收起面板；标题点选即转换块类型，无 pending 两段式） */
    onHeadingPanelDismiss: () -> Unit = {},
    onFontSizeSelect: (Int) -> Unit,
    onPresetColorSelect: (Int) -> Unit,
    onCustomColorSelect: (String) -> Unit,
    onCjkFontSelect: (String) -> Unit,
    onLatinFontSelect: (String) -> Unit,
    onSetFontWeight: (Int) -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onInsertUnorderedList: () -> Unit,
    onInsertOrderedList: () -> Unit,
    /** 增加缩进回调（v2026-09-05）：透传给 [RichTextFormatToolbar] */
    onIncreaseIndent: () -> Unit = {},
    /** 减少缩进回调（v2026-09-05）：透传给 [RichTextFormatToolbar] */
    onDecreaseIndent: () -> Unit = {},
    /** 插入分割线回调（v2026-09-07）：透传给 [RichTextFormatToolbar]，聚焦块光标处拆块插入 */
    onInsertDivider: () -> Unit = {},
    /** 复选框回调（v2026-09-07）：透传给 [RichTextFormatToolbar]，聚焦块切换复选框属性 */
    onToggleCheckbox: () -> Unit = {},
    /** BlockNote 迁移（P1-S10）：块类型转换/插入（heading1-3/quote/codeBlock/table/pageBreak/paragraph），透传格式工具栏 */
    onTransform: (String) -> Unit = {},
    /** 普通段落转换（BlockNote 模式专用） */
    onTransformParagraph: () -> Unit = {},
    /** 块类型按钮可用性（BlockNote 模式 true，Compose 模式 false 置灰） */
    onTransformEnabled: Boolean = false,
    /**
     * 颜色面板（A 按钮）切换回调（v2026-09-21 新增，取代原 `onOpenColorStyleDialog`）
     *
     * ⚠️ 语义变化：原为「打开色板 AlertDialog」，现改为与 T / Aa 一致的**内联面板**切换
     * （[ColorStylePanel]）——调用方需同时收起另两个面板（三者互斥占同一槽位）并收起软键盘。
     */
    onColorPanelClick: () -> Unit = {},
    /** 颜色面板头「完成」回调（收起面板；颜色点选即时生效，无 pending 两段式） */
    onColorPanelDismiss: () -> Unit = {},
    /** 选中文字色点选（行内，v2026-09-21）：参数 "#RRGGBB"，null = 恢复默认 */
    onInlineTextColorSelect: (String?) -> Unit = {},
    /** 选中背景色点选（行内，v2026-09-21）：参数 "#RRGGBB"，null = 恢复默认 */
    onInlineBackgroundColorSelect: (String?) -> Unit = {},
    /** 段落文字色点选（块级，v2026-09-21）：参数 BlockNote 色名，null = 清除 */
    onBlockTextColorSelect: (String?) -> Unit = {},
    /** 段落背景色点选（块级，v2026-09-21）：参数 BlockNote 色名，null = 清除 */
    onBlockBackgroundColorSelect: (String?) -> Unit = {},
    /** BlockNote 迁移（P1.5）：媒体插入请求（"image"/"video"/"audio"/"file" → 宿主选择器） */
    onInsertMedia: (String) -> Unit = {},
    /** BlockNote 迁移（P1.5）：打开表情选择面板 */
    onOpenEmojiPicker: () -> Unit = {},
    /** BlockNote 模式加粗单档（点击直接 toggle，不展开 B1/B2/B3 菜单） */
    boldSingleTier: Boolean = false,
    /** 是否可增加缩进（v2026-09-05 视觉降级）：列表到顶时置灰，透传给 [RichTextFormatToolbar] */
    canIncreaseIndent: Boolean = true,
    /** 是否可减少缩进（v2026-09-05 视觉降级）：非列表行置灰，透传给 [RichTextFormatToolbar] */
    canDecreaseIndent: Boolean = true,
    /** 聚焦块是否为复选框块（v2026-09-07）：复选框按钮激活态，透传给 [RichTextFormatToolbar] */
    isCheckboxActive: Boolean = false,
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onInsertLink: () -> Unit = {},
    onToggleCodeSpan: () -> Unit = {},
    /**
     * 删除当前块（v1.11）：原 BlockNote 侧边菜单（⋮⋮ 手柄）点击菜单的「删除」项，
     * 现移入格式工具栏的「块操作」菜单。命中口径由 JS 侧决定，无需传参。
     */
    onDeleteBlock: () -> Unit = {},
    /**
     * ⚠️ v2026-09-21 已删除 `onSetBlockColor` 参数：
     * 块级（段落）颜色入口由 ⋮ 菜单整体移入「A」按钮的颜色对话框
     * （「段落文字色 / 段落背景色」，见 [ColorStylePanel]），底部栏不再需要透传它。
     */
    /**
     * 切换表头行 / 表头列（v1.11）：原 ⋮⋮ 菜单的「表头行 / 表头列」项。
     * @param target "row" = 表头行，"column" = 表头列
     */
    onSetTableHeader: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * 块上移 / 下移（v1.11.5）：**替代原 ⋮⋮ 手柄的拖拽重排**。
     * 该手柄的拖拽在 Android WebView 触摸下不工作（HTML5 原生 DnD 限制），故已删除。
     */
    onMoveBlockUp: () -> Unit = {},
    onMoveBlockDown: () -> Unit = {},
    /** 当前光标块状态（v1.11）：透传给 [RichTextFormatToolbar] 驱动「块操作」菜单 */
    blockState: BlockState = BlockState(),
    /**
     * 格式工具栏整体可用性（v1.11.1）：宿主锁定态传 false。
     * 透传给 [RichTextFormatToolbar]，锁定态下整条置灰并拦截点击。
     */
    toolbarEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.background
) {
    /**
     * 软键盘高度记录（字体面板高度 = 键盘高度，用户决策 2）。
     *
     * `WindowInsets.ime` 在键盘收起时归零，为保留「最近一次完整键盘高度」，
     * 只在 ime 增大时更新（取 max）：键盘弹出动画递增→记录完整值；
     * 收起动画递减→不覆盖，面板高度保持稳定。键盘从未弹出过时兜底 291dp
     * （Android 中文键盘典型高度，与原型一致）。
     */
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var keyboardHeight by remember { mutableStateOf(291.dp) }
    if (imeBottomPx > 0) {
        val imeDp = with(density) { imeBottomPx.toDp() }
        if (imeDp > keyboardHeight) keyboardHeight = imeDp
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        shadowElevation = 4.dp,
        color = backgroundColor,
        tonalElevation = 1.dp
    ) {
        Column {
            /** 上行一：可折叠的格式工具栏 */
            AnimatedVisibility(
                visible = isFormatExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                RichTextFormatToolbar(
                    state = richTextState,
                    /** 单一状态直接透传：四个面板按钮的激活态由 openPanel 派生（v2026-09-21） */
                    openPanel = openPanel,
                    onFontPickerClick = onFontPickerClick,
                    onSizeColorPanelClick = onSizeColorPanelClick,
                    onHeadingPanelClick = onHeadingPanelClick,
                    onSetFontWeight = onSetFontWeight,
                    onToggleItalic = onToggleItalic,
                    onToggleUnderline = onToggleUnderline,
                    onToggleStrikethrough = onToggleStrikethrough,
                    onInsertUnorderedList = onInsertUnorderedList,
                    onInsertOrderedList = onInsertOrderedList,
                    onIncreaseIndent = onIncreaseIndent,
                    onDecreaseIndent = onDecreaseIndent,
                    onInsertDivider = onInsertDivider,
                    onToggleCheckbox = onToggleCheckbox,
                    onTransform = onTransform,
                    onTransformParagraph = onTransformParagraph,
                    onTransformEnabled = onTransformEnabled,
                    onInsertMedia = onInsertMedia,
                    onOpenEmojiPicker = onOpenEmojiPicker,
                    onColorPanelClick = onColorPanelClick,
                    boldSingleTier = boldSingleTier,
                    canIncreaseIndent = canIncreaseIndent,
                    canDecreaseIndent = canDecreaseIndent,
                    isCheckboxActive = isCheckboxActive,
                    onAlignLeft = onAlignLeft,
                    onAlignCenter = onAlignCenter,
                    onAlignRight = onAlignRight,
                    onInsertLink = onInsertLink,
                    onToggleCodeSpan = onToggleCodeSpan,
                    onDeleteBlock = onDeleteBlock,
                    onSetTableHeader = onSetTableHeader,
                    onMoveBlockUp = onMoveBlockUp,
                    onMoveBlockDown = onMoveBlockDown,
                    blockState = blockState,
                    enabled = toolbarEnabled
                )
            }

            /**
             * 上行二：字体选择面板（v2026-09-03 新增，用户决策后按原型落地）。
             *
             * 展开时占据「格式工具栏与相机行之间」，把相机行向下推开：
             * 键盘收起让出 ime inset + 面板占据自身高度，相机行从键盘上方
             * 下移到屏幕底部（位移 = 键盘高度），与原型交互一致。
             */
            AnimatedVisibility(
                visible = openPanel == EditBottomPanel.FONT,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FontPickerPanel(
                    panelHeight = keyboardHeight,
                    currentCjkId = currentCjkId,
                    currentLatinId = currentLatinId,
                    hasPendingChange = hasPendingChange,
                    onCjkSelect = onCjkFontSelect,
                    onLatinSelect = onLatinFontSelect,
                    onDone = onFontPanelDismiss
                )
            }

            /**
             * 上行二b：字号与颜色面板（v2026-09-04 新增，按已审核原型落地）。
             *
             * 与字体面板**互斥、占同一槽位、同高度**（同用 keyboardHeight，互斥切换不跳动）；
             * 字号/颜色点选即时生效（SpanStyle 由调用方写入），面板头只保留「完成」收起。
             */
            AnimatedVisibility(
                visible = openPanel == EditBottomPanel.SIZE_COLOR,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                FontSizeColorPanel(
                    panelHeight = keyboardHeight,
                    currentFontSize = currentFontSize,
                    currentColorIdx = currentColorIdx,
                    customColorHex = customColorHex,
                    onFontSizeSelect = onFontSizeSelect,
                    onPresetColorSelect = onPresetColorSelect,
                    onCustomColorSelect = onCustomColorSelect,
                    onDone = onSizeColorPanelDismiss
                )
            }

            /**
             * 上行二c：颜色面板（v2026-09-21 新增）
             *
             * 与字体面板（T）、字号颜色面板（Aa）**三者互斥、占同一槽位、同高度**
             * （同用 keyboardHeight，互斥切换不跳动）。
             *
             * 来源：原「A」按钮的 AlertDialog 弹窗——按需求把展示形态改为与 T / Aa 一致的内联面板；
             * 内容为四组色板：选中文字色 / 选中背景色（行内）与 段落文字色 / 段落背景色（块级），
             * 后两组原先在 ⋮ 菜单的「背景色 / 文字色」，v2026-09-21 一并移入。
             * 点选即时生效且**不收起面板**（与 Aa 一致，便于连续微调），
             * 收起由面板头「完成」或再点一次 A 按钮触发。
             */
            AnimatedVisibility(
                visible = openPanel == EditBottomPanel.COLOR,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ColorStylePanel(
                    panelHeight = keyboardHeight,
                    /* 段落色回显来自 JS 上行的当前光标块状态（行内色无状态上行，不回显） */
                    currentBlockTextColor = blockState.blockTextColor,
                    currentBlockBackgroundColor = blockState.blockBackgroundColor,
                    onPickInlineTextColor = onInlineTextColorSelect,
                    onPickInlineBackgroundColor = onInlineBackgroundColorSelect,
                    onPickBlockTextColor = onBlockTextColorSelect,
                    onPickBlockBackgroundColor = onBlockBackgroundColorSelect,
                    onDone = onColorPanelDismiss,
                    /** 锁定态内容区置灰并拦点击（v2026-09-21）：与格式工具栏同一口径 */
                    enabled = toolbarEnabled
                )
            }

            /**
             * 上行二d：标题面板（v2026-09-21 新增）
             *
             * 与 T / Aa / A 三个面板**四者互斥、占同一槽位、同高度**（同用 keyboardHeight）。
             *
             * 来源：原格式工具栏的「组三 Headings（RiH1–RiH6）」与「组四 Subheadings
             * （▸1–▸3）」共 9 个按键——它们占满工具栏一行、挤走常用格式键，按需求整体移入本面板，
             * 并按「普通标题 / 可折叠标题」两类呈现。
             *
             * 点选即转换块类型（走既有的 [onTransform]，无需新增下行通道）且**不收起面板**，
             * 与 Aa / A 的交互一致；收起由面板头「完成」或再点一次 H 按钮触发。
             */
            AnimatedVisibility(
                visible = openPanel == EditBottomPanel.HEADING,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                HeadingPanel(
                    panelHeight = keyboardHeight,
                    /** 9 个标题键复用既有的块类型转换通道（action = heading1–6 / toggleHeading 系列） */
                    onTransform = onTransform,
                    onDone = onHeadingPanelDismiss,
                    /** 锁定态内容区置灰并拦点击（v2026-09-21）：与格式工具栏同一口径 */
                    enabled = toolbarEnabled
                )
            }

            /**
             * 下行：6 个核心按钮（始终显示）
             *
             * v2026-07-22 改造：Row 内部加 navigationBarsPadding()，
             * 让按钮自动上移避开系统手势条（与 safeAreaForEditBar 配套）。
             * Surface 容器本身紧贴屏幕底端，圆角矩形（米黄色背景）填满到屏幕底边缘。
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarButton(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "照片",
                    onClick = onPhotoClick
                )
                BottomBarButton(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "语音",
                    onClick = onVoiceClick
                )
                /**
                 * # 标签按钮（v2026-07-22 改造）：
                 * - 原"位置"按钮：图标沿用 Icons.Default.Tag，contentDescription 由"位置"改为"标签"
                 * - 回调由 onLocationClick 重命名为 onTagClick
                 * - 触发灵感独有功能 TagPickerSheet（添加/编辑标签）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.Tag,
                    contentDescription = "标签",
                    onClick = onTagClick
                )
                /**
                 * @ 关联按钮（v2026-07-22 改造）：
                 * - 由 MentionTriggerPopup（单选）升级为 RelationPickerBottomSheet（多选）
                 * - 行为与待办编辑页 @ 按钮保持一致
                 */
                BottomBarButton(
                    imageVector = Icons.Default.AlternateEmail,
                    contentDescription = "关联",
                    onClick = onMentionClick
                )
                /**
                 * 📍 位置按钮（v2026-07-22 新增）：
                 * - 从原 # 位置按钮中独立出来，使用 Icons.Default.LocationOn
                 * - 触发位置提醒弹窗（LocationPicker + Geofence）
                 */
                BottomBarButton(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "位置",
                    onClick = onLocationClick
                )
                /** 格式按钮：高亮显示当工具栏展开时 */
                BottomBarButton(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "格式",
                    onClick = onFormatToggleClick,
                    tint = if (isFormatExpanded) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 底部栏图标按钮
 *
 * @param imageVector 图标
 * @param contentDescription 无障碍描述
 * @param onClick 点击回调
 * @param tint 图标颜色
 */
@Composable
private fun BottomBarButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
