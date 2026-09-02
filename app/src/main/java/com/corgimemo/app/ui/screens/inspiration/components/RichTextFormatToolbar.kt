package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * 加粗字重档位：取「当前字体默认字重（Normal=400）往上紧邻的三个更大字重」。
 *
 * 设计要点：Compose 无法在运行时枚举字体实际支持哪些字面重（无公开 API），
 * 故用 [FONT_AVAILABLE_WEIGHTS] 表示「当前所用字体真实支持的字重集合」，
 * 档位 = 该集合中 `> FontWeight.Normal.weight` 的前三档。换字体时只需更新
 * [FONT_AVAILABLE_WEIGHTS] 一个常量，档位派生逻辑不动。
 *
 * 当前字体：FontFamily.Default（系统字体，典型为 Roboto）实际字面为
 * 100 / 300 / 400 / 500 / 700 / 900，故派生结果 = [500, 700, 900]，三档在当前设备上
 * 视觉明显区分。若某设备系统字体字面更少（如仅 400/700），take(3) 会少于三档，
 * 工具栏按实际档位数渲染子按钮（不强行凑三档，避免制造视觉相同的重复档）。
 *
 * 兼容性：
 * - 700 走 markdown `**`；其余档走 `<span style="font-weight:N">` 保留数值
 *   （库侧 `parseCssFontWeight` 已支持任意整数字重兜底）。
 * - 引入含完整字重的自定义字体（如 Inter / Noto Sans 100~900）时，把
 *   [FONT_AVAILABLE_WEIGHTS] 改为该字体真实字重，档位即自动变为 500 / 600 / 700。
 *
 * 该常量同时作为「清除已选字重、避免叠加」的遍历来源。
 */
private val FONT_AVAILABLE_WEIGHTS = listOf(100, 300, 400, 500, 700, 900)
internal val BOLD_WEIGHT_TIERS: List<Int> =
    FONT_AVAILABLE_WEIGHTS.filter { it > FontWeight.Normal.weight }.take(3)

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
 * 加粗按钮交互：点击 B 展开同行 B1/B2/B3 子按钮（档位由 BOLD_WEIGHT_TIERS 动态派生：默认字重往上紧邻的三个更大字重），其余按钮被推开；
 * 选中某档后子按钮自动收起，B 变为对应的 B1/B2/B3 并高亮（选中的档位）。
 * 再次点击当前已选档位可取消加粗（回到常规字重）。
 *
 * 每个按钮支持激活状态显示（暖橙色高亮），
 * 符合项目整体 UI 设计规范（暖橙色主题 #FF9A5C）。
 *
 * @param state 库的 RichTextState 实例
 * @param modifier Modifier
 * @param onSetFontWeight 设置字重档位回调（参数为 BOLD_WEIGHT_TIERS 动态档位，当前字体下为 500/700/900）
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
    /** 当前光标/选中区的字重档位（null 表示未处于三档之一） */
    val currentWeight = state.currentSpanStyle.fontWeight?.weight
    val currentTier = when (currentWeight) {
        in BOLD_WEIGHT_TIERS -> BOLD_WEIGHT_TIERS.indexOf(currentWeight) + 1
        else -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 第一组：基础样式 (加粗字重菜单 / 斜体 / 下划线 / 删除线) ====== */
        FormatButtonGroup {
            /** 加粗主按钮：点击展开/收起字重菜单；展开时显示左箭头，收起时显示右箭头 */
            FormatWeightButton(
                tier = currentTier,
                expanded = boldExpanded,
                isActive = currentTier != null,
                onClick = { boldExpanded = !boldExpanded },
                contentDescription = "加粗字重"
            )
            /** 展开态：同行显示 B1/B2/B3 子按钮（档位由 BOLD_WEIGHT_TIERS 动态派生，当前字体下为 500/700/900），选中后自动收起 */
            AnimatedVisibility(
                visible = boldExpanded,
                enter = expandHorizontally(),
                exit = shrinkHorizontally()
            ) {
                Row {
                    BOLD_WEIGHT_TIERS.forEachIndexed { index, weight ->
                        val tier = index + 1
                        FormatWeightTierButton(
                            tier = tier,
                            isActive = currentWeight == weight,
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
 * 激活态：浅暖橙背景 + 主题 primary 图标色。
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
    val backgroundColor = if (isActive) {
        Color(0xFFFFE0C0) // 浅暖橙色背景（激活态）
    } else {
        Color.Transparent
    }

    val tint = if (isActive) {
        Color(0xFFFF9A5C) // 主题 primary（激活态，与下层 ⋮ 按钮一致）
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
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
    val backgroundColor = if (isActive) {
        Color(0xFFFFE0C0)
    } else {
        Color.Transparent
    }
    val tint = if (isActive) {
        Color(0xFFFF9A5C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
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
 *
 * @param tier 档位数字（1/2/3 对应 B1/B2/B3）
 * @param isActive 是否为当前选中档位
 * @param onClick 点击回调（设置对应字重并收起菜单）
 */
@Composable
private fun FormatWeightTierButton(
    tier: Int,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        Color(0xFFFFE0C0)
    } else {
        Color.Transparent
    }
    val tint = if (isActive) {
        Color(0xFFFF9A5C)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
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
