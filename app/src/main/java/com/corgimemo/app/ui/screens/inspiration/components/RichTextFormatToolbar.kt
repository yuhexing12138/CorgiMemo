package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.corgimemo.app.ui.theme.ThemeManager
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
 * **功能分组**（v2026-09-17 按浮层格式工具栏重桥）:
 * 1. **第一分类区（浮动格式工具栏 11 键全量桥接，图标与顺序严格一致）**:
 *    T(字体面板)、Aa(字号颜色面板)、B(RiBold)、I(RiItalic)、U(RiUnderline)、S(RiStrikethrough)、
 *    对齐×3(RiAlignLeft/Center/Right)、Color(A)、Nest(RiIndentIncrease)、UnNest(RiIndentDecrease)、Link(RiLink)
 * 2. **Headings**: H1–H6（+ 菜单同款 Ri 图标）
 * 3. **Subheadings**: 可折叠标题 ▸1–3
 * 4. **Basic blocks**: 有序/无序/任务列表、段落、代码块、分割线、引用、折叠列表、分页
 * 5. **Advanced / Media / Others**: 表格；图片/视频/音频/文件；表情
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
 * @param isFontPanelOpen 字体选择面板是否展开（字体按钮激活态高亮用）
 * @param onFontPickerClick 字体选择按钮回调（展开/收起字体面板；同时由调用方收起软键盘）
 * @param isSizeColorPanelOpen 字号与颜色面板是否展开（字号颜色按钮激活态高亮用；与字体面板互斥）
 * @param onSizeColorPanelClick 字号与颜色按钮回调（展开/收起字号与颜色面板；同时由调用方收起软键盘）
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
    isFontPanelOpen: Boolean = false,
    onFontPickerClick: () -> Unit = {},
    isSizeColorPanelOpen: Boolean = false,
    onSizeColorPanelClick: () -> Unit = {},
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
     * action = heading1/heading2/heading3/quote/codeBlock/table/pageBreak/paragraph。
     * Compose 模式（useBlockNote=false）传空实现即可——按钮由调用方置灰。
     */
    onTransform: (String) -> Unit = {},
    /** 普通段落转换（+ 菜单 Paragraph；BlockNote 模式专用） */
    onTransformParagraph: () -> Unit = {},
    /** 颜色按钮打开色板对话框（浮层 ColorStyleButton 桥接） */
    onOpenColorStyleDialog: () -> Unit = {},
    /** 显示色板对话框（受控态，由宿主持有） */
    showColorStyleDialog: Boolean = false,
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
     * 设置当前块的**块级**颜色（v1.11）：原 ⋮⋮ 手柄点击菜单的「颜色」项。
     *
     * ⚠️ 与 [onOpenColorStyleDialog]（行内文字色）是**不同维度**，别混：
     * 本回调写**块 props**（整个块生效），后者写**行内 span 样式**（仅选区文字生效）。
     *
     * @param textColor 块级文本色（BlockNote 预设色名；"default" 清除）；传 null = 不改动该维度
     * @param backgroundColor 块级背景色（预设色名；"default" 清除）；传 null = 不改动该维度
     */
    onSetBlockColor: (String?, String?) -> Unit = { _, _ -> },
    /**
     * 切换表头行 / 表头列（v1.11）：原 ⋮⋮ 手柄点击菜单的「表头行 / 表头列」项。
     * @param target "row" = 表头行，"column" = 表头列
     * @param enabled true = 开启，false = 关闭
     */
    onSetTableHeader: (String, Boolean) -> Unit = { _, _ -> },
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
         * T(字体面板) → Aa(字号颜色面板) → B(RiBold) → I(RiItalic) → U(RiUnderline) → S(RiStrikethrough)
         * → 对齐×3(RiAlignLeft/Center/Right) → Color(A) → Nest(RiIndentIncrease) → UnNest(RiIndentDecrease) → Link(RiLink)。
         * 图标取自 [com.corgimemo.app.ui.screens.probe.BlockNotePlusMenuIcons]（react-icons/ri 5.6.0，
         * 与浮层渲染逐字节同款）；区内不设分割线，竖线只出现在分类区之间；T / Aa 暂沿用原按钮不动。
         */
        FormatButtonGroup {
            /** T（字体面板，暂不动） */
            FormatIconButton(
                imageVector = LucideIcons.Type,
                isActive = isFontPanelOpen,
                onClick = onFontPickerClick,
                contentDescription = "字体"
            )
            /** Aa（字号与颜色面板，暂不动） */
            FormatIconButton(
                imageVector = LucideIcons.CaseSensitive,
                isActive = isSizeColorPanelOpen,
                onClick = onSizeColorPanelClick,
                contentDescription = "字号与颜色"
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
            /** Color（浮层 ColorStyleButton 即「A」字母样式，文字按钮保持一致）：打开文字/背景色板对话框 */
            FormatTextButton(
                label = "A",
                isActive = showColorStyleDialog,
                onClick = onOpenColorStyleDialog,
                contentDescription = "颜色"
            )
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

        /** ====== 组三：Headings（+ 菜单 Headings 分类，Ri 同款图标） ====== */
        FormatButtonGroup {
            RiFormatButton("RiH1", onClick = { onTransform("heading1") }, contentDescription = "标题 1", enabled = onTransformEnabled)
            RiFormatButton("RiH2", onClick = { onTransform("heading2") }, contentDescription = "标题 2", enabled = onTransformEnabled)
            RiFormatButton("RiH3", onClick = { onTransform("heading3") }, contentDescription = "标题 3", enabled = onTransformEnabled)
            RiFormatButton("RiH4", onClick = { onTransform("heading4") }, contentDescription = "标题 4", enabled = onTransformEnabled)
            RiFormatButton("RiH5", onClick = { onTransform("heading5") }, contentDescription = "标题 5", enabled = onTransformEnabled)
            RiFormatButton("RiH6", onClick = { onTransform("heading6") }, contentDescription = "标题 6", enabled = onTransformEnabled)
        }

        ToolbarDivider()

        /** ====== 组四：Subheadings（可折叠标题，+ 菜单 Subheadings 分类） ====== */
        FormatButtonGroup {
            FormatTextButton("▸1", isActive = false, onClick = { onTransform("toggleHeading") }, contentDescription = "可折叠标题 1", enabled = onTransformEnabled)
            FormatTextButton("▸2", isActive = false, onClick = { onTransform("toggleHeading2") }, contentDescription = "可折叠标题 2", enabled = onTransformEnabled)
            FormatTextButton("▸3", isActive = false, onClick = { onTransform("toggleHeading3") }, contentDescription = "可折叠标题 3", enabled = onTransformEnabled)
        }

        ToolbarDivider()

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
         * 收纳原 BlockNote 侧边菜单（⋮⋮ 手柄）**点击菜单**的 4 项：删除块、块颜色、
         * 表头行、表头列。手柄本身已退化为纯拖拽把手（其拖拽重排是原生手势，无法按钮化），
         * 故这 4 项在此提供唯一入口。
         *
         * 之所以合成**一个入口按钮 + 下拉菜单**而非 4 个独立按钮：块颜色需要色板、
         * 表头需要两个开关，直接铺开会挤占工具栏；且这 4 项都是低频块级操作，
         * 合并后语义与官方原菜单一一对应，用户认知成本最低。
         */
        FormatButtonGroup {
            BlockOpsMenuButton(
                blockState = blockState,
                enabled = onTransformEnabled,
                onDeleteBlock = onDeleteBlock,
                onSetBlockColor = onSetBlockColor,
                onSetTableHeader = onSetTableHeader
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
 * 「块操作」菜单按钮（v1.11）
 *
 * 收纳原 BlockNote 侧边菜单（⋮⋮ 手柄）**点击菜单**的 4 项，是这 4 项的唯一入口
 * （手柄本身已退化为纯拖拽把手）。菜单顺序刻意与官方 `DragHandleMenu` 对齐
 * （删除块 → 颜色 → 表头），降低从原手柄迁移过来的认知成本。
 *
 * 可用态与回显全部取自 [blockState]（JS 侧判定后经 `blockState` 上行）：
 * - 块颜色行始终显示——色板点击在 JS 侧对不支持的块类型静默忽略，UI 保持结构稳定；
 * - 表头两项**仅在表格块内出现**，与官方 `TableHeadersItem` 的 `return null` 同语义，
 *   避免在普通块上展示永远点不动的条目。
 *
 * @param blockState 当前光标块状态（可用态与选中回显）
 * @param enabled 整体可用性（BlockNote 模式 true；Compose 模式 false 置灰）
 * @param onDeleteBlock 删除块回调
 * @param onSetBlockColor 设置块级颜色回调（textColor, backgroundColor）；null = 不改动该维度
 * @param onSetTableHeader 切换表头回调（target, enabled）
 */
@Composable
private fun BlockOpsMenuButton(
    blockState: BlockState,
    enabled: Boolean,
    onDeleteBlock: () -> Unit,
    onSetBlockColor: (String?, String?) -> Unit,
    onSetTableHeader: (String, Boolean) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    /**
     * 色板要按当前主题取明/暗两套色值——BlockNote 内部同样分两套
     * （见 defaultColors.ts 的 COLORS_DEFAULT / COLORS_DARK_MODE_DEFAULT），
     * 取错会让色点在暗色主题下显得过亮而失真。
     * 判定口径与 [com.corgimemo.app.ui.screens.probe.BlockNoteEditorWebView] 一致，
     * 保证色板与编辑器实际渲染颜色同源。
     */
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

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

            HorizontalDivider()

            /** 块背景色 / 块文字色：两行色板，点选后菜单保持打开，便于连续微调 */
            BlockColorRow(
                label = "背景色",
                current = blockState.blockBackgroundColor,
                isDark = isDark,
                picker = { name, dark -> BlockColorPalette.background(name, dark) },
                onPick = { name -> onSetBlockColor(null, name) }
            )
            BlockColorRow(
                label = "文字色",
                current = blockState.blockTextColor,
                isDark = isDark,
                picker = { name, dark -> BlockColorPalette.text(name, dark) },
                onPick = { name -> onSetBlockColor(name, null) }
            )

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

/**
 * 块颜色选择行（v1.11）
 *
 * 一行色点：最左为「默认」（清除块级颜色），其后是 9 个 BlockNote 预设色。
 * 当前生效色以暖橙粗描边标记，与工具栏其余按钮的激活态配色保持一致。
 *
 * @param label 行标题（"背景色" / "文字色"）
 * @param current 当前生效色名（空串或 "default" 视为默认色）
 * @param isDark 是否暗色主题（决定取哪套色值）
 * @param picker 色名 → Compose Color（背景色与文字色取不同维度，故由调用方注入）
 * @param onPick 点选回调（参数为色名；"default" = 清除）
 */
@Composable
private fun BlockColorRow(
    label: String,
    current: String,
    isDark: Boolean,
    picker: (String, Boolean) -> Color,
    onPick: (String) -> Unit
) {
    /** 空串与 "default" 都表示"未设置块级颜色"（前者来自未上报，后者是 BlockNote 的清除值） */
    val isDefault = current.isEmpty() || current == "default"

    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BlockColorDot(
                color = MaterialTheme.colorScheme.surfaceVariant,
                selected = isDefault,
                onClick = { onPick("default") },
                contentDescription = "$label 默认",
                showSlash = true
            )
            BlockColorPalette.names.forEach { name ->
                BlockColorDot(
                    color = picker(name, isDark),
                    selected = !isDefault && current == name,
                    onClick = { onPick(name) },
                    contentDescription = "$label $name"
                )
            }
        }
    }
}

/**
 * 单个色点（v1.11）
 *
 * @param color 填充色
 * @param selected 是否当前选中（暖橙 2dp 描边）
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 * @param showSlash 是否画一条斜杠表示「不设置颜色」（用于"默认"项，避免与纯白底色混淆）
 */
@Composable
private fun BlockColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    showSlash: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(24.dp)
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
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * BlockNote 官方块级色板（v1.11）
 *
 * 色名与色值**逐条对应** `@blocknote/core/src/editor/defaultColors.ts`：
 * - 亮色 → `COLORS_DEFAULT`
 * - 暗色 → `COLORS_DARK_MODE_DEFAULT`
 *
 * 为何在宿主硬编码而非由 JS 上行：色板必须在菜单弹出的**同一帧**就渲染出来，
 * 不能等一次上下行往返；而色名属于桥协议的一部分（稳定），色值极少变动。
 * 若 BlockNote 升级调整了色值，同步本表即可——**改前先读上述源文件确认**，
 * 不要凭印象填色。
 *
 * 注意 `default`（清除）不是色板成员而是调用方传的特殊值，故不在 [names] 中。
 */
private object BlockColorPalette {
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

    /** 取某色名的**文本色**（色板"文字色"行用） */
    fun text(name: String, isDark: Boolean): Color = pick(name, isDark, 0)

    /** 取某色名的**背景色**（色板"背景色"行用） */
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
