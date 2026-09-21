package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.components.longPressRepeat
import com.corgimemo.app.ui.screens.probe.BlockState
import com.corgimemo.app.ui.theme.ContentFontManager
import com.corgimemo.app.ui.theme.FontWeightProbe
import com.mohamedrejeb.richeditor.model.RichTextState
import compose.icons.LucideIcons
import compose.icons.lucideicons.CaseSensitive
import compose.icons.lucideicons.SeparatorHorizontal
import compose.icons.lucideicons.SquareCheck
import compose.icons.lucideicons.Type

/**
 * 加粗字重候选档位（B1 / B2 / B3 对应各字体 [com.corgimemo.app.ui.theme.FontEntry.boldTiers]，
 * 当前字体由 [com.corgimemo.app.ui.theme.FontManager] 持有）。但某个候选档位是否真能渲染出独立字形，
 * 无法靠常量静态判断（系统字体常缺 500 字面、被量化合并），故用 [FontWeightProbe] 运行时像素探测：
 * 探测不到独立字形的档位，其按钮置灰禁用，避免用户选中却「视觉无变化」的困惑。
 * 本文件只消费档位与探测结果、不持有字体知识，换字体无需改动此处。
 */

/**
 * 富文本格式工具栏（使用 compose-rich-editor 库）
 *
 * 提供完整的文本格式化操作，与库的 RichTextState 配合使用（BlockNote 模式经回调桥接 WebView）。
 * 分类区之间以竖线分隔，分类区内部不设分割线：
 *
 * **功能分组**（v2026-09-17 按浮层格式工具栏重桥；分组内容 v2026-09-21 有调整）:
 * 1. **第一分类区（浮动格式工具栏 11 键全量桥接，图标与顺序严格一致）**:
 *    T(字体面板)、Aa(字号颜色面板)、B(RiBold)、I(RiItalic)、U(RiUnderline)、S(RiStrikethrough)、
 *    对齐×3(RiAlignLeft/Center/Right)、Color(A)、Nest(RiIndentIncrease)、UnNest(RiIndentDecrease)、Link(RiLink)
 *    ＋ v2026-09-21 增补 H(标题面板)，插在 Aa 与 A 之间
 * 2. **Basic blocks**: 有序/无序/任务列表、段落、代码块、分割线、引用、折叠列表、分页
 * 3. **Advanced / Media / Others**: 表格；图片/视频/音频/文件；表情
 *
 * ⚠️ v2026-09-21 移除「Headings（RiH1–RiH6）」与「Subheadings（可折叠标题 ▸1–▸3）」两组：
 * 这 9 个按键整体移入 H 按钮展开的 [HeadingPanel]（分「普通标题 / 可折叠标题」两类）。
 *
 * 加粗按钮交互：点击 B 展开同行 B1/B2/B3 子按钮（候选档位由当前字体 [com.corgimemo.app.ui.theme.FontEntry.boldTiers] 给出），其余按钮被推开；
 * 档位是否真正可用由 [FontWeightProbe] 运行时像素探测决定，探测不到独立字形的档位按钮置灰禁用；
 * 选中某档后子按钮自动收起，B 变为对应的 B1/B2/B3 并高亮（选中的档位）。
 * 再次点击当前已选档位可取消加粗（回到常规字重）。
 *
 * 每个按钮支持激活状态显示（图标变主题 primary 暖橙色，无背景色块——
 * v2026-09-04 按用户要求去掉激活态的浅暖橙背景块，改为按钮图标本身变色），
 * 符合项目整体 UI 设计规范（暖橙色主题 #FF9A5C）。
 *
 * @param state 库的 RichTextState 实例
 * @param modifier Modifier
 * @param openPanel 当前展开的内联面板（null = 全部收起）；T / Aa / H / A 四个按钮的激活态
 *   直接由它派生，互斥由状态本身保证（v2026-09-21 由三个 boolean 收敛而来，见 [EditBottomPanel]）
 * @param onFontPickerClick 字体选择按钮回调（展开/收起字体面板；同时由调用方收起软键盘）
 * @param onSizeColorPanelClick 字号与颜色按钮回调（展开/收起字号与颜色面板；同时由调用方收起软键盘）
 * @param onHeadingPanelClick 标题按钮回调（展开/收起标题面板；同时由调用方收起软键盘）
 * @param onSetFontWeight 设置字重档位回调（参数为当前字体 [com.corgimemo.app.ui.theme.FontEntry.boldTiers] 候选档位；
 *      其中经像素探测无独立字形的档位在工具栏中置灰禁用，不会回调）
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
 */
@Composable
fun RichTextFormatToolbar(
    state: RichTextState,
    modifier: Modifier = Modifier,
    /**
     * 当前展开的内联面板（null = 全部收起）
     *
     * v2026-09-21 由 `isFontPanelOpen` / `isSizeColorPanelOpen` / `isColorPanelOpen`
     * 三个 boolean **收敛为单一状态**：T / Aa / A 三个按钮的激活态直接由它派生，
     * 互斥由状态本身保证（见 [EditBottomPanel]）。
     */
    openPanel: EditBottomPanel? = null,
    onFontPickerClick: () -> Unit = {},
    onSizeColorPanelClick: () -> Unit = {},
    /**
     * 标题按钮（H）回调（v2026-09-21 新增）：切换**底部内联「标题」面板**
     * （[HeadingPanel]）展开/收起。
     *
     * 面板内的 9 个标题键（普通标题 H1–H6 / 可折叠标题 1–3）经既有的
     * [onTransform] 下发 action，故本回调只负责开关面板。
     */
    onHeadingPanelClick: () -> Unit = {},
    onSetFontWeight: (Int) -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onInsertUnorderedList: () -> Unit,
    onInsertOrderedList: () -> Unit,
    /** 增加缩进回调（v2026-09-05）：列表行层级 +1，普通文本行自动转列表项；可连续加 */
    onIncreaseIndent: () -> Unit = {},
    /** 减少缩进回调（v2026-09-05）：列表行层级 -1，一级再减退出列表；可连续减 */
    onDecreaseIndent: () -> Unit = {},
    /** 插入分割线回调（v2026-09-07）：在聚焦块光标处拆块插入分割线（可撤销） */
    onInsertDivider: () -> Unit = {},
    /** 复选框回调（v2026-09-07）：聚焦块在 复选框块 ↔ 普通块 间切换（可撤销） */
    onToggleCheckbox: () -> Unit = {},
    /** 是否可增加缩进（v2026-09-05 视觉降级）：列表到顶（6 级）时置灰禁用 */
    canIncreaseIndent: Boolean = true,
    /** 是否可减少缩进（v2026-09-05 视觉降级）：非列表行置灰禁用（减缩进无效果） */
    canDecreaseIndent: Boolean = true,
    /**
     * BlockNote 迁移（P1）：加粗单档模式——true 时 B 按钮点击直接 toggle 加粗
     * （onSetFontWeight(700)），不展开 B1/B2/B3 字重菜单（HTML 无多档字重概念）。
     */
    boldSingleTier: Boolean = false,
    /** 聚焦块是否为复选框块（v2026-09-07 视觉降级）：复选框按钮激活态高亮用 */
    isCheckboxActive: Boolean = false,
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onInsertLink: () -> Unit = {},
    onToggleCodeSpan: () -> Unit = {},
    /**
     * 块类型转换/插入（BlockNote 迁移 P1-S10 补充）：
     * action = heading1–heading6 / toggleHeading / toggleHeading2 / toggleHeading3
     * （这三组标题 action 现由「标题面板」[HeadingPanel] 调用，共用本通道）
     * / quote / codeBlock / table / pageBreak / paragraph / toggleList。
     * Compose 模式（useBlockNote=false）传空实现即可——按钮由调用方置灰。
     */
    onTransform: (String) -> Unit = {},
    /** 普通段落转换（+ 菜单 Paragraph；BlockNote 模式专用） */
    onTransformParagraph: () -> Unit = {},
    /**
     * 颜色按钮（A）回调（v2026-09-21 改名，原 onOpenColorStyleDialog）：
     * 切换**底部内联「颜色」面板**（[ColorStylePanel]）展开/收起。
     *
     * ⚠️ 语义变化：原名与弹窗模式绑定（打开 AlertDialog），现改为与 T / Aa 一致的面板切换；
     * 三面板互斥已由上层收敛的单一 [openPanel] 状态保证，本回调无需关心另两个面板。
     */
    onColorPanelClick: () -> Unit = {},
    /** 块类型按钮可用性（BlockNote 模式 true；Compose 模式 false 置灰） */
    onTransformEnabled: Boolean = false,
    /** BlockNote 迁移（P1.5）：媒体插入请求（"image"/"video"/"audio"/"file" → 宿主选择器） */
    onInsertMedia: (String) -> Unit = {},
    /** BlockNote 迁移（P1.5）：打开表情选择面板 */
    onOpenEmojiPicker: () -> Unit = {},
    /**
     * 删除当前块（v1.11）：原 BlockNote 侧边菜单（⋮⋮ 手柄）点击菜单的「删除」项。
     *
     * 背景：该手柄的点击菜单原有 4 项，按用户决策全部移入本工具栏，手柄本身
     * 只保留拖拽重排（原生手势，无法按钮化）。命中口径由 JS 侧决定——当前选区
     * 若包含光标块则删整个选区，否则只删光标块，故本回调无需传参。
     */
    onDeleteBlock: () -> Unit = {},
    /**
     * ⚠️ v2026-09-21 已删除 `onSetBlockColor` 参数：
     * 「背景色 / 文字色」两行**块级**色板由 ⋮ 菜单整体移入底部工具栏「A」按钮的
     * 颜色面板（更名为「段落背景色 / 段落文字色」，见 [ColorStylePanel]），
     * 本菜单不再保留块级颜色入口，故该回调一并移除。
     */
    /**
     * 切换表头行 / 表头列（v1.11）：原 ⋮⋮ 手柄点击菜单的「表头行 / 表头列」项。
     * @param target "row" = 表头行，"column" = 表头列
     * @param enabled true = 开启，false = 关闭
     */
    onSetTableHeader: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * 块上移 / 下移（v1.11.5）：**替代原 ⋮⋮ 手柄的拖拽重排**。
     *
     * 该手柄的拖拽纯用 HTML5 原生 Drag & Drop，在 Android WebView 的触摸下不触发
     * （真机"按住无反应"），故手柄已整体删除，块移动改由这两个按钮承担。
     *
     * ⚠️ 不做置灰：BlockNote 的 `moveBlocksUp/Down` 到达首/末块时是安全 no-op，
     * 但 API 未暴露"能否移动"的判定，故点了没反应即代表已在边界。
     */
    onMoveBlockUp: () -> Unit = {},
    onMoveBlockDown: () -> Unit = {},
    /**
     * 当前光标块状态（v1.11）：驱动「块操作」菜单的可用态与选中回显
     * （色板高亮当前色、表头项显隐与勾选）。由 JS 侧经 `blockState` 上行。
     */
    blockState: BlockState = BlockState(),
    /**
     * 整条格式工具栏的可用性（v1.11.1）：宿主锁定态（`isLocked`）时传 false。
     *
     * ⚠️ 为什么需要**遮罩**而不只是视觉变淡：块操作走的是 `editor.removeBlocks()` /
     * `updateBlock()` 这类**程序化 API**，它们**不受编辑器只读状态限制**——只把按钮
     * 画灰仍会真的生效。故禁用态除降低不透明度外，还叠一层透明遮罩吞掉全部触摸。
     */
    enabled: Boolean = true
) {
    /** 加粗字重菜单的展开状态（纯 UI 局部状态，置于函数体顶层，不在条件分支内） */
    var boldExpanded by remember { mutableStateOf(false) }
    /** 当前光标/选中区的字重档位（null 表示未处于候选档之一） */
    val currentWeight = state.currentSpanStyle.fontWeight?.weight
    /**
     * 当前**内容**字体（[ContentFontManager] 反应式持有；默认系统默认字体）。
     * 必须在 `currentTier` 之前声明，否则 Kotlin 报「Unresolved reference 'contentEntry'」——
     * 局部 `val` 不可前置引用。
     *
     * 注意：这里取的是**内容字体**而非设置页「正文字体」([FontManager])——
     * 编辑页用户内容默认用系统字体（请求 M：设置字体只影响 App chrome），
     * 工具栏加粗档位必须与实际渲染的内容字体一致，否则探测字体与渲染字体不符。
     */
    val contentEntry by ContentFontManager.currentEntry.collectAsState()
    val currentTier = when (currentWeight) {
        in contentEntry.boldTiers -> contentEntry.boldTiers.indexOf(currentWeight) + 1
        else -> null
    }
    /**
     * 运行时像素探测得到的「有独立字面」字重集合（remember 仅算一次）。
     * 不在集合内的候选档位按钮将置灰禁用，避免选中却无视觉变化。
     *
     * **必须传入应用实际渲染的字体**：内容字体由 [ContentFontManager] 持有
     * （默认系统默认字体），用 [ContentFontManager.typefaceForWeight] 提供 Typeface、
     * 用 [ContentFontManager.tag] 隔离缓存；内容字体切换时 tag 变化 → 重新探测。
     */
    val context = LocalContext.current
    val distinctWeights = remember(context, contentEntry.tag) {
        FontWeightProbe.distinctWeights(
            candidates = contentEntry.boldTiers,
            fontTag = contentEntry.tag,
            typefaceOf = { weight -> contentEntry.typefaceForWeight(context, weight) }
        )
    }

    /**
     * 禁用态拦截：在 `PointerEventPass.Initial` 阶段消费全部指针事件。
     *
     * 为什么必须用 `Initial`（本阶段事件**由父级流向子级**，与外层相反）：
     * `clickable` / `IconButton` 都在 `Main` 阶段（由子级流向父级）响应，
     * 若在 Row 的 `Main` 阶段拦截，子按钮早已先一步处理完，拦不住。
     * 而在 `Initial` 阶段父级先拿到事件并 consume，子按钮便收不到有效点击。
     */
    val disabledBlocker = if (enabled) {
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            /** 锁定态整条降到 38% 不透明度（与 FormatIconButton 的禁用态同款视觉） */
            .alpha(if (enabled) 1f else 0.38f)
            .then(disabledBlocker),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /**
         * ====== 第一分类区：WebView「浮动格式工具栏」11 键全量重桥（v2026-09-17） ======
         * 按用户要求：先彻底删除旧 11 键，再严格按浮层顺序与图标重建：
         * T(字体面板) → Aa(字号颜色面板) → A(颜色面板，v2026-09-21 移入) →
         * B(RiBold) → I(RiItalic) → U(RiUnderline) → S(RiStrikethrough)
         * → 对齐×3(RiAlignLeft/Center/Right) → Nest(RiIndentIncrease) → UnNest(RiIndentDecrease) → Link(RiLink)。
         * 图标取自 [com.corgimemo.app.ui.screens.probe.BlockNotePlusMenuIcons]（react-icons/ri 5.6.0，
         * 与浮层渲染逐字节同款）；区内不设分割线，竖线只出现在分类区之间；T / Aa 暂沿用原按钮不动。
         *
         * v2026-09-21 调整：原位于"对齐×3 之后"的 Color(A) 移到 Aa 正右侧，
         * 使 T / Aa / A 三个"展开内联面板"的按钮相邻成组（浮层原始顺序则不再严格保持）。
         */
        FormatButtonGroup {
            /** T（字体面板，暂不动） */
            FormatIconButton(
                imageVector = LucideIcons.Type,
                isActive = openPanel == EditBottomPanel.FONT,
                onClick = onFontPickerClick,
                contentDescription = "字体"
            )
            /** Aa（字号与颜色面板，暂不动） */
            FormatIconButton(
                imageVector = LucideIcons.CaseSensitive,
                isActive = openPanel == EditBottomPanel.SIZE_COLOR,
                onClick = onSizeColorPanelClick,
                contentDescription = "字号与颜色"
            )
            /**
             * H（标题面板，v2026-09-21 新增）
             *
             * 原工具栏「组三 Headings（RiH1–RiH6）」与「组四 Subheadings（▸1–▸3）」
             * 共 9 个按钮整体移入本按钮展开的 [HeadingPanel]（分「普通标题 / 可折叠标题」两类）；
             * 图标沿用浮层的「H」字母样式（[FormatTextButton]）。
             */
            FormatTextButton(
                label = "H",
                isActive = openPanel == EditBottomPanel.HEADING,
                onClick = onHeadingPanelClick,
                contentDescription = "标题"
            )
            /**
             * A（颜色面板，v2026-09-21 由"对齐×3 之后"移到 Aa 右侧——现位于 H 的右侧）
             *
             * 与 T / Aa / H 一样是"展开底部内联面板"的同类按钮，四个相邻便于识别
             * （顺序 T → Aa → H → A）；面板形态也统一为内联面板（原为 AlertDialog 弹窗，已删除）。
             * 图标沿用浮层 ColorStyleButton 的「A」字母样式（[FormatTextButton]）。
             */
            FormatTextButton(
                label = "A",
                isActive = openPanel == EditBottomPanel.COLOR,
                onClick = onColorPanelClick,
                contentDescription = "颜色"
            )
            /** B（浮层 BasicTextStyleButton 同款 RiBold）：BlockNote 单档模式直接 toggle 加粗；
             *  Compose 模式保留 B1/B2/B3 字重菜单展开逻辑 */
            if (boldSingleTier) {
                RiFormatButton(
                    "RiBold",
                    onClick = { onSetFontWeight(700) },
                    contentDescription = "加粗"
                )
            } else {
                FormatWeightButton(
                    tier = currentTier,
                    expanded = boldExpanded,
                    isActive = currentTier != null,
                    onClick = { boldExpanded = !boldExpanded },
                    contentDescription = "加粗"
                )
                /** 展开态：同行显示 B1/B2/B3 子按钮（经像素探测无独立字形的档位置灰禁用），选中后自动收起 */
                AnimatedVisibility(
                    visible = boldExpanded,
                    enter = expandHorizontally(),
                    exit = shrinkHorizontally()
                ) {
                    Row {
                        contentEntry.boldTiers.forEachIndexed { index, weight ->
                            val tier = index + 1
                            FormatWeightTierButton(
                                tier = tier,
                                isActive = currentWeight == weight,
                                enabled = weight in distinctWeights,
                                onClick = {
                                    onSetFontWeight(weight)
                                    boldExpanded = false
                                }
                            )
                        }
                    }
                }
            }
            /** I / U / S（浮层 BasicTextStyleButton 同款图标） */
            RiFormatButton(
                "RiItalic",
                isActive = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                onClick = onToggleItalic,
                contentDescription = "斜体"
            )
            RiFormatButton(
                "RiUnderline",
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                onClick = onToggleUnderline,
                contentDescription = "下划线"
            )
            RiFormatButton(
                "RiStrikethrough",
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                onClick = onToggleStrikethrough,
                contentDescription = "删除线"
            )
            /** 对齐×3（浮层 TextAlignButton 同款 RiAlignLeft / RiAlignCenter / RiAlignRight） */
            RiFormatButton("RiAlignLeft", onClick = onAlignLeft, contentDescription = "左对齐")
            RiFormatButton("RiAlignCenter", onClick = onAlignCenter, contentDescription = "居中对齐")
            RiFormatButton("RiAlignRight", onClick = onAlignRight, contentDescription = "右对齐")
            /**
             * ⚠️ v2026-09-21：原「A」颜色按钮（浮层 ColorStyleButton 同款「A」字母样式）
             * 已从本分类区移出，改到 Aa 右侧（见第一分类区的 T → Aa → A 三连）——
             * 三者同为"展开内联面板"的同类按钮，相邻放置更易识别；
             * 且其展示形态已由 AlertDialog 弹窗改为与 T / Aa 一致的内联面板。
             * 原位置不再保留副本（颜色入口保持唯一）。
             */
            /**
             * Nest / UnNest（浮层 NestBlockButton / UnNestBlockButton 同款图标）
             *
             * v2026-09-17 追加：启用**长按连发**——缩进常需连按多级（最多 6 级），
             * 逐次点击很累。速度对齐 BlockNote 浮层按钮（450ms 后每 150ms）。
             * canRepeat 复用既有的 canIncreaseIndent / canDecreaseIndent：
             * 到顶（6 级）/ 非列表行时不仅按钮置灰，连发也会立即停止，不发无效命令。
             */
            RiFormatButton(
                "RiIndentIncrease",
                onClick = onIncreaseIndent,
                contentDescription = "增加缩进",
                enabled = canIncreaseIndent,
                canRepeat = canIncreaseIndent,
            )
            RiFormatButton(
                "RiIndentDecrease",
                onClick = onDecreaseIndent,
                contentDescription = "减少缩进",
                enabled = canDecreaseIndent,
                canRepeat = canDecreaseIndent,
            )
            /** Link（浮层 CreateLinkButton 同款 RiLink）：BlockNote 模式弹 URL 对话框 → format createLink */
            RiFormatButton(
                "RiLink",
                isActive = state.isLink,
                onClick = onInsertLink,
                contentDescription = "插入链接"
            )
        }

        ToolbarDivider()

        /**
         * ⚠️ v2026-09-21：原本此处是「组三 Headings（RiH1–RiH6）」与
         * 「组四 Subheadings（▸1–▸3）」两组共 9 个按钮，已**整体移入工具栏「H」按钮
         * 展开的标题面板**（[HeadingPanel]，分「普通标题 / 可折叠标题」两类呈现）。
         * 原因：这 9 键占用本行大量横向空间，而块类型转换属低频操作，
         * 收进面板后格式工具栏明显变短、常用格式键更易触达。
         * 原来的两组分隔线随之一并删除，此处只保留组二 ↔ 组五之间的一道。
         */

        /** ====== 组五：Basic blocks（+ 菜单 Basic blocks 分类；图标与 + 菜单同款并按其顺序排列） ====== */
        FormatButtonGroup {
            /** Numbered List（+ 菜单同款 RiListOrdered；激活态跟随光标块类型） */
            RiFormatButton(
                "RiListOrdered",
                isActive = state.isOrderedList,
                onClick = onInsertOrderedList,
                contentDescription = "有序列表"
            )
            /** Bullet List */
            RiFormatButton(
                "RiListUnordered",
                isActive = state.isUnorderedList,
                onClick = onInsertUnorderedList,
                contentDescription = "无序列表"
            )
            /** Check List（复用原复选框回调与激活态） */
            RiFormatButton(
                "RiListCheck3",
                isActive = isCheckboxActive,
                onClick = onToggleCheckbox,
                contentDescription = "任务列表"
            )
            /** Paragraph（+ 菜单 Paragraph；BlockNote 模式转普通段落） */
            RiFormatButton(
                "RiText",
                onClick = onTransformParagraph,
                contentDescription = "普通段落",
                enabled = onTransformEnabled
            )
            /** Code Block（整块代码，toggle 语义） */
            RiFormatButton(
                "RiCodeBlock",
                onClick = { onTransform("codeBlock") },
                contentDescription = "代码块",
                enabled = onTransformEnabled
            )
            /** Divider（分割线：插入后点击可切样式） */
            RiFormatButton(
                "RiSubtractLine",
                onClick = onInsertDivider,
                contentDescription = "分割线"
            )
            /** Quote（+ 菜单 Quote） */
            RiFormatButton("RiQuoteText", onClick = { onTransform("quote") }, contentDescription = "引用", enabled = onTransformEnabled)
            /** Toggle List（+ 菜单 Toggle List） */
            RiFormatButton("RiPlayList2Fill", onClick = { onTransform("toggleList") }, contentDescription = "折叠列表", enabled = onTransformEnabled)
            /** Page Break（+ 菜单 Page Break） */
            RiFormatButton("RiFile2Line", onClick = { onTransform("pageBreak") }, contentDescription = "分页", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组六：Advanced（+ 菜单 Advanced 分类） ====== */
        FormatButtonGroup {
            RiFormatButton("RiTable2", onClick = { onTransform("table") }, contentDescription = "表格", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组七：Media（+ 菜单 Media 分类；宿主选择器 → Bridge 插入） ====== */
        FormatButtonGroup {
            RiFormatButton("RiImage2Fill", onClick = { onInsertMedia("image") }, contentDescription = "图片", enabled = onTransformEnabled)
            RiFormatButton("RiFilmLine", onClick = { onInsertMedia("video") }, contentDescription = "视频", enabled = onTransformEnabled)
            RiFormatButton("RiVolumeUpFill", onClick = { onInsertMedia("audio") }, contentDescription = "音频", enabled = onTransformEnabled)
            RiFormatButton("RiFile2Line", onClick = { onInsertMedia("file") }, contentDescription = "文件", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组八：Others（+ 菜单 Others 分类） ====== */
        FormatButtonGroup {
            RiFormatButton("RiEmotionFill", onClick = onOpenEmojiPicker, contentDescription = "表情", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /**
         * ====== 组九：块操作（v1.11）======
         *
         * 收纳原 BlockNote 侧边菜单（⋮⋮ 手柄）**点击菜单**的剩余项：删除块、上移/下移、
         * 表头行、表头列。手柄本身已退化为纯拖拽把手（其拖拽重排是原生手势，无法按钮化），
         * 故这些项在此提供唯一入口。
         *
         * ⚠️ v2026-09-21：「块颜色」已移出本菜单——块级颜色入口整体迁入底部工具栏
         * 「A」按钮的颜色面板（见 [ColorStylePanel] 的「段落文字色 / 段落背景色」），
         * 因为颜色类操作集中在一处更符合直觉，也避免"两个地方都能改颜色"的困惑。
         *
         * 之所以合成**一个入口按钮 + 下拉菜单**而非多个独立按钮：表头需要两个开关、
         * 移动需要上下两项，直接铺开会挤占工具栏；且这些都是低频块级操作，
         * 合并后语义与官方原菜单一一对应，用户认知成本最低。
         */
        FormatButtonGroup {
            BlockOpsMenuButton(
                blockState = blockState,
                enabled = onTransformEnabled,
                onDeleteBlock = onDeleteBlock,
                onSetTableHeader = onSetTableHeader,
                onMoveBlockUp = onMoveBlockUp,
                onMoveBlockDown = onMoveBlockDown
            )
        }
    }
}

/**
 * BlockNote「+」菜单同款图标按钮（P1-S10/S11）：Ri 矢量图标渲染。
 * 图标缺失时降级为文字（contentDescription 前两字）。
 *
 * v2026-09-17 追加：新增 [canRepeat]——传入非 null 时启用**长按连发**
 * （按下即执行一次 → 450ms 后每 150ms 重复 → 抬手即停），手感对齐 BlockNote
 * 浮层按钮。适配「连续操作」类按钮（缩进/反缩进需连按多级）。
 * 传 null（默认）则保持原「即点即走」行为，零回归。
 *
 * @param canRepeat 非 null 时启用长按连发，且该值实时决定「是否继续连发」；
 *                  null = 不启用连发（普通点击）
 */
@Composable
private fun RiFormatButton(
    riName: String,
    isActive: Boolean = false,
    enabled: Boolean = true,
    canRepeat: Boolean? = null,
    contentDescription: String,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isActive -> Color(0xFFFF9A5C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vector = remember(riName) {
        com.corgimemo.app.ui.screens.probe.BlockNotePlusMenuIcons.vectorFor(riName)
    }
    /**
     * 连发开关：仅当调用方显式传入 canRepeat（非 null）时才挂手势 Modifier。
     * 否则保持原样，避免给所有按钮都引入 pointerInput 的额外开销。
     */
    val repeatModifier = if (canRepeat != null) {
        Modifier.longPressRepeat(
            onAction = onClick,
            enabled = enabled,
            canRepeat = canRepeat,
        )
    } else {
        Modifier
    }
    IconButton(
        /** 启用连发时动作交给手势 Modifier（否则会双执行）；未启用时走常规 onClick */
        onClick = if (canRepeat != null) ({}) else onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .then(repeatModifier)
    ) {
        if (vector != null) {
            Icon(
                imageVector = vector,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Text(
                text = contentDescription.take(2),
                color = tint,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * 工具栏竖线分隔符（P1.5 修复：HorizontalDivider 内部强制 fillMaxWidth + height(thickness)，
 * 会把给定的 1dp 宽/28dp 高覆盖成「全宽 1dp 横线」——改用 Box 自绘，精确 1×28 竖线）
 */
@Composable
private fun ToolbarDivider() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .width(1.dp)
            .height(28.dp)
            .background(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    )
}

/**
 * 单个格式化图标按钮
 *
 * 统一使用 IconButton 40dp + Icon 22dp，与下层 BottomBarButton 一致。
 * 激活态：主题 primary 图标色（v2026-09-04 去掉浅暖橙背景色块，改为按钮图标本身变色）。
 * 禁用态（enabled=false）：图标 38% 透明度置灰 + 不可点击（与加粗档位子按钮同款视觉降级）。
 *
 * @param imageVector Material Icon 图标
 * @param isActive 是否激活
 * @param enabled 是否可点击（false 时置灰禁用，默认 true）
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 */
@Composable
private fun FormatIconButton(
    imageVector: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    /**
     * 激活态 = 图标本身变色（主题 primary），无背景色块
     * （v2026-09-04 用户要求：原激活态的浅暖橙背景块视觉似「橙色阴影」，改为图标变色）。
     * 禁用态 = 38% 透明度置灰（与 [FormatWeightTierButton] 同款）。
     */
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isActive -> Color(0xFFFF9A5C) // 主题 primary（激活态，与下层 ⋮ 按钮一致）
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
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

/**
 * 文字标签格式按钮（BlockNote 迁移 P1-S10 补充）：H1/H2/H3、引用等以文字为图标的按钮。
 * 样式与 [FormatIconButton] 一致（40dp 点击区、22dp 内容、激活/禁用视觉同款）。
 */
@Composable
private fun FormatTextButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    enabled: Boolean = true,
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isActive -> Color(0xFFFF9A5C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Text(
            text = label,
            color = tint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * 格式化工具栏按钮组容器（组间以竖线分隔）
 */
@Composable
private fun FormatButtonGroup(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * 加粗主按钮（可展开字重菜单）
 *
 * 显示「B」+ 可选右下标档位数字（B1/B2/B3）+ 展开方向箭头：
 * - 收起态（未展开或未选中档位）：右箭头，暗示可展开；
 * - 选中某档位后：显示对应档位下标并保持右箭头，激活态高亮；
 * - 展开态：左箭头，暗示可收起。
 *
 * @param tier 当前选中档位（1/2/3 对应 B1/B2/B3），null 表示未选中任何档位
 * @param expanded 字重菜单是否展开
 * @param isActive 是否激活（选中了三档之一）
 * @param singleTier 单档模式（BlockNote 迁移 P1.5）：显示纯「B」无下标无箭头，
 *        点击直接切换加粗（由调用方把 onClick 换成加粗 toggle）
 * @param onClick 点击回调（展开/收起菜单，或单档加粗切换）
 * @param contentDescription 无障碍描述
 */
@Composable
private fun FormatWeightButton(
    tier: Int?,
    expanded: Boolean,
    isActive: Boolean,
    singleTier: Boolean = false,
    onClick: () -> Unit,
    contentDescription: String
) {
    /** 激活态 = 图标/文字变色（v2026-09-04 去掉浅暖橙背景色块，与 FormatIconButton 一致） */
    val tint = if (isActive) {
        Color(0xFFFF9A5C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "B",
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 15.sp
            )
            /** 选中档位时显示右下标数字（1/2/3）；单档模式不显示 */
            if (tier != null && !singleTier) {
                Text(
                    text = tier.toString(),
                    color = tint,
                    fontSize = 9.sp,
                    style = LocalTextStyle.current.copy(baselineShift = BaselineShift.Subscript)
                )
            }
            /** 展开方向箭头：展开时左箭头，收起时右箭头；单档模式不显示 */
            if (!singleTier) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 加粗字重子按钮（B1/B2/B3）
 *
 * 显示「B」+ 右下标档位数字（固定 1/2/3），激活态高亮当前选中的档位。
 * 当该档位经 [FontWeightProbe] 探测无独立字面时（[enabled]=false）置灰禁用，
 * 点击无效，提示用户该档在当前字体下与更轻档位视觉一致、无需可选。
 *
 * @param tier 档位数字（1/2/3 对应 B1/B2/B3）
 * @param isActive 是否为当前选中档位
 * @param enabled 是否有独立字面（运行时像素探测结果），false 时置灰禁用
 * @param onClick 点击回调（设置对应字重并收起菜单）
 */
@Composable
private fun FormatWeightTierButton(
    tier: Int,
    isActive: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    /** 激活态 = 图标/文字变色（v2026-09-04 去掉浅暖橙背景色块，与 FormatIconButton 一致） */
    val tint = when {
        // 无独立字面：置灰（采用 Material 标准禁用透明度），避免误操作
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        isActive -> Color(0xFFFF9A5C)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "B",
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 15.sp
            )
            Text(
                text = tier.toString(),
                color = tint,
                fontSize = 9.sp,
                style = LocalTextStyle.current.copy(baselineShift = BaselineShift.Subscript)
            )
        }
    }
}

/**
 * 「块操作」菜单按钮（v1.11 → v1.11.5 增「上移 / 下移」→ v2026-09-21 移除块颜色）
 *
 * 收纳原 BlockNote 侧边菜单（⋮⋮ 手柄）**点击菜单**的剩余项，外加 v1.11.5 的
 * 「上移 / 下移」。菜单顺序刻意与官方 `DragHandleMenu` 对齐（删除块 → 表头），
 * 降低从原手柄迁移过来的认知成本；「上移 / 下移」紧跟删除块之后，因为二者都与
 * "块的位置"相关，且它们是**原手柄拖拽重排的替代品**。
 *
 * v2026-09-21：**块颜色两项已移出**——块级颜色入口整体迁入底部工具栏「A」按钮的
 * 颜色面板（[ColorStylePanel] 的「段落文字色 / 段落背景色」），故本菜单不再接收
 * `onSetBlockColor`，也不再自行判定明暗主题取色板色值。
 *
 * 可用态与回显全部取自 [blockState]（JS 侧判定后经 `blockState` 上行）：
 * - 表头两项**仅在表格块内出现**，与官方 `TableHeadersItem` 的 `return null` 同语义，
 *   避免在普通块上展示永远点不动的条目；
 * - 上移 / 下移**不置灰**——BlockNote 未暴露"能否移动"的判定，到顶/到底时是安全 no-op。
 *
 * @param blockState 当前光标块状态（可用态与选中回显）
 * @param enabled 整体可用性（BlockNote 模式 true；Compose 模式 false 置灰）
 * @param onDeleteBlock 删除块回调
 * @param onSetTableHeader 切换表头回调（target, enabled）
 * @param onMoveBlockUp 块上移回调（替代原拖拽重排）
 * @param onMoveBlockDown 块下移回调（替代原拖拽重排）
 */
@Composable
private fun BlockOpsMenuButton(
    blockState: BlockState,
    enabled: Boolean,
    onDeleteBlock: () -> Unit,
    onSetTableHeader: (String, Boolean) -> Unit,
    onMoveBlockUp: () -> Unit,
    onMoveBlockDown: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        FormatIconButton(
            imageVector = Icons.Default.MoreHoriz,
            isActive = menuOpen,
            onClick = { menuOpen = true },
            contentDescription = "块操作",
            enabled = enabled
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            /**
             * 删除块：用 error 色标示破坏性语义。
             * 不额外加二次确认——BlockNote 历史栈可撤销，误触有兜底；
             * 且「从菜单里显式选中一个红色条目」本身已构成确认动作。
             */
            DropdownMenuItem(
                text = {
                    Text(
                        text = "删除块",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                },
                onClick = {
                    menuOpen = false
                    onDeleteBlock()
                }
            )

            /**
             * 上移 / 下移（v1.11.5）：**替代原 ⋮⋮ 手柄的拖拽重排**。
             *
             * 该手柄的拖拽依赖 HTML5 原生 Drag & Drop，而该 API 在 Android WebView /
             * iOS Safari 的触摸下不触发（真机表现为"按住没反应"），故手柄已整体删除，
             * 这两项成为块移动的唯一入口。
             *
             * 设计取舍：
             * - **不置灰**——BlockNote 未暴露"能否移动"的判定（到达首/末块时
             *   `moveBlocksUp/Down` 是安全 no-op），点了没反应即代表已在边界；
             * - **不关闭菜单**——便于连续点击把块一路移上去
             *   （原「色板行保持打开」可连续微调的手感，v2026-09-21 色板移出本菜单后继续沿用）。
             */
            DropdownMenuItem(
                text = { Text("上移", fontSize = 13.sp) },
                onClick = { onMoveBlockUp() }
            )
            DropdownMenuItem(
                text = { Text("下移", fontSize = 13.sp) },
                onClick = { onMoveBlockDown() }
            )

            /**
             * ⚠️ v2026-09-21：原本此处有 HorizontalDivider + 「背景色 / 文字色」两行
             * **块级**色板，现已整体移出本菜单，迁入底部工具栏「A」按钮的颜色面板
             * （更名为「段落背景色 / 段落文字色」，见 [ColorStylePanel]）。
             * 菜单里不再保留块级颜色入口，故此处的分隔线与两行色板一并删除。
             */

            if (blockState.canToggleHeader) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (blockState.isHeaderRow) "✓ 表头行" else "表头行",
                            fontSize = 13.sp
                        )
                    },
                    onClick = { onSetTableHeader("row", !blockState.isHeaderRow) }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (blockState.isHeaderCol) "✓ 表头列" else "表头列",
                            fontSize = 13.sp
                        )
                    },
                    onClick = { onSetTableHeader("column", !blockState.isHeaderCol) }
                )
            }
        }
    }
}

