package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.ContentFontManager
import com.corgimemo.app.ui.theme.FontWeightProbe
import com.mohamedrejeb.richeditor.model.RichTextState
import compose.icons.LucideIcons
import compose.icons.lucideicons.CaseSensitive
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
 * 提供完整的文本格式化操作，与库的 RichTextState 配合使用。
 * 工具栏分为 4 个功能组，每组用竖线分隔：
 *
 * **功能分组**:
 * 1. **基础样式**: 加粗(B，可展开字重菜单 B1/B2/B3)、斜体(I)、下划线(U)、删除线(S)
 * 2. **列表**: 无序列表、有序列表
 * 3. **对齐**: 左对齐、居中、右对齐（通过 toggleParagraphStyle）
 * 4. **高级**: 链接、代码块
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
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onInsertLink: () -> Unit = {},
    onToggleCodeSpan: () -> Unit = {}
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 第一组：基础样式 (字体选择 / 加粗字重菜单 / 斜体 / 下划线 / 删除线) ====== */
        FormatButtonGroup {
            /**
             * 字体选择按钮（位于加粗 B 左侧）：
             * v2026-09-04 图标由 Material FontDownload 换为 Lucide「Type」（描边风格，
             * 与字号颜色按钮的 CaseSensitive 配套，视觉更轻盈统一）。
             * 点击展开/收起字体选择面板（面板由 [InspirationEditBottomBar] 插入在工具栏与相机行之间，
             * 展开时收起软键盘并把相机行向下推开）；激活态 = 图标变主题 primary 色。
             */
            FormatIconButton(
                imageVector = LucideIcons.Type,
                isActive = isFontPanelOpen,
                onClick = onFontPickerClick,
                contentDescription = "字体"
            )
            /**
             * 字号与颜色按钮（v2026-09-04 新增，位于字体按钮与加粗 B 之间）：
             * Lucide「CaseSensitive」图标（与已审核原型一致）；点击展开/收起字号与颜色面板
             * （与字体面板互斥、占同一槽位，见 [InspirationEditBottomBar]）；激活态与字体按钮同款。
             */
            FormatIconButton(
                imageVector = LucideIcons.CaseSensitive,
                isActive = isSizeColorPanelOpen,
                onClick = onSizeColorPanelClick,
                contentDescription = "字号与颜色"
            )
            /** 加粗主按钮：点击展开/收起字重菜单；展开时显示左箭头，收起时显示右箭头 */
            FormatWeightButton(
                tier = currentTier,
                expanded = boldExpanded,
                isActive = currentTier != null,
                onClick = { boldExpanded = !boldExpanded },
                contentDescription = "加粗字重"
            )
            /** 展开态：同行显示 B1/B2/B3 子按钮（候选档位 500/700/900；经像素探测无独立字形的档位置灰禁用），选中后自动收起 */
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
            FormatIconButton(
                imageVector = Icons.Default.FormatItalic,
                isActive = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                onClick = onToggleItalic,
                contentDescription = "斜体"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatUnderlined,
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                onClick = onToggleUnderline,
                contentDescription = "下划线"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatStrikethrough,
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                onClick = onToggleStrikethrough,
                contentDescription = "删除线"
            )
        }

        ToolbarDivider()

        /** ====== 第二组：列表 ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                isActive = state.isUnorderedList,
                onClick = onInsertUnorderedList,
                contentDescription = "无序列表"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatListNumbered,
                isActive = state.isOrderedList,
                onClick = onInsertOrderedList,
                contentDescription = "有序列表"
            )
        }

        ToolbarDivider()

        /** ====== 第三组：对齐方式 ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                isActive = false,
                onClick = onAlignLeft,
                contentDescription = "左对齐"
            )
            FormatIconButton(
                imageVector = Icons.Default.FormatAlignCenter,
                isActive = false,
                onClick = onAlignCenter,
                contentDescription = "居中对齐"
            )
            FormatIconButton(
                imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                isActive = false,
                onClick = onAlignRight,
                contentDescription = "右对齐"
            )
        }

        ToolbarDivider()

        /** ====== 第四组：高级（链接 + 代码块） ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = Icons.Default.Link,
                isActive = state.isLink,
                onClick = onInsertLink,
                contentDescription = "插入链接"
            )
            FormatIconButton(
                imageVector = Icons.Default.Code,
                isActive = state.isCodeSpan,
                onClick = onToggleCodeSpan,
                contentDescription = "代码块"
            )
        }
    }
}

/**
 * 格式化工具栏按钮组容器
 */
@Composable
private fun FormatButtonGroup(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 6.dp)
    ) {
        content()
    }
}

/**
 * 工具栏竖线分隔符
 */
@Composable
private fun ToolbarDivider() {
    HorizontalDivider(
        modifier = Modifier
            .size(width = 1.dp, height = 28.dp)
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

/**
 * 单个格式化图标按钮
 *
 * 统一使用 IconButton 40dp + Icon 22dp，与下层 BottomBarButton 一致。
 * 激活态：主题 primary 图标色（v2026-09-04 去掉浅暖橙背景色块，改为按钮图标本身变色）。
 *
 * @param imageVector Material Icon 图标
 * @param isActive 是否激活
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 */
@Composable
private fun FormatIconButton(
    imageVector: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String
) {
    /**
     * 激活态 = 图标本身变色（主题 primary），无背景色块
     * （v2026-09-04 用户要求：原激活态的浅暖橙背景块视觉似「橙色阴影」，改为图标变色）。
     */
    val tint = if (isActive) {
        Color(0xFFFF9A5C) // 主题 primary（激活态，与下层 ⋮ 按钮一致）
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

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
 * @param onClick 点击回调（展开/收起菜单）
 * @param contentDescription 无障碍描述
 */
@Composable
private fun FormatWeightButton(
    tier: Int?,
    expanded: Boolean,
    isActive: Boolean,
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
            /** 选中档位时显示右下标数字（1/2/3） */
            if (tier != null) {
                Text(
                    text = tier.toString(),
                    color = tint,
                    fontSize = 9.sp,
                    style = LocalTextStyle.current.copy(baselineShift = BaselineShift.Subscript)
                )
            }
            /** 展开方向箭头：展开时左箭头，收起时右箭头 */
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
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
