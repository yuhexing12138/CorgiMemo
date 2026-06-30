package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 富文本编辑器格式化工具栏组件
 *
 * 提供完整的文本格式化操作界面，与 RichTextEditor 配合使用。
 * 工具栏分为 4 个功能组，每组用竖线分隔：
 *
 * **功能分组**:
 * 1. **字体样式组**: 加粗(B)、斜体(I)、下划线(U)、删除线(S)
 * 2. **列表格式组**: 无序列表(•)、有序列表(1.)、待办子项(☑️)
 * 3. **对齐方式组**: 左对齐、居中、右对齐
 * 4. **插入功能组**: 文字颜色、图片插入
 *
 * 每个按钮支持激活状态显示（暖橙色高亮），
 * 符合项目整体 UI 设计规范（暖橙色主题）。
 *
 * 使用示例：
 * ```kotlin
 * val editorState = remember { RichTextEditorState() }
 *
 * Column {
 *     TextFormatToolbar(
 *         state = editorState,
 *         onToggleBold = { applyBoldFormat(editorState) },
 *         onToggleItalic = { applyItalicFormat(editorState) },
 *         onInsertList = { insertUnorderedList(editorState) },
 *         onInsertImage = { showImagePicker() },
 *         onSelectColor = { showColorPicker() }
 *     )
 *
 *     RichTextEditor(
 *         state = editorState,
 *         onValueChange = { /* 更新状态 */ }
 *     )
 * }
 * ```
 *
 * @param state 编辑器状态对象（包含格式化状态）
 * @param modifier Modifier（可选）
 * @param onToggleBold 加粗按钮点击回调
 * @param onToggleItalic 斜体按钮点击回调
 * @param onToggleUnderline 下划线按钮点击回调
 * @param onToggleStrikethrough 删除线按钮点击回调
 * @param onInsertUnorderedList 无序列表按钮点击回调
 * @param onInsertOrderedList 有序列表按钮点击回调
 * @param onInsertTodoItem 待办子项按钮点击回调
 * @param onAlignLeft 左对齐按钮点击回调
 * @param onAlignCenter 居中对齐按钮点击回调
 * @param onAlignRight 右对齐按钮点击回调
 * @param onSelectColor 文字颜色选择按钮点击回调
 * @param onInsertImage 图片插入按钮点击回调
 */
@Composable
fun TextFormatToolbar(
    state: RichTextEditorState,
    modifier: Modifier = Modifier,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onInsertUnorderedList: () -> Unit,
    onInsertOrderedList: () -> Unit,
    onInsertTodoItem: () -> Unit,
    onAlignLeft: () -> Unit = {},
    onAlignCenter: () -> Unit = {},
    onAlignRight: () -> Unit = {},
    onSelectColor: () -> Unit = {},
    onInsertImage: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 第一组：字体样式 (B/I/U/S) ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "B",
                isActive = state.isBold,
                onClick = onToggleBold,
                contentDescription = "加粗"
            )
            
            FormatToolButton(
                text = "I",
                isActive = state.isItalic,
                fontStyle = FontStyle.Italic,
                onClick = onToggleItalic,
                contentDescription = "斜体"
            )
            
            FormatToolButton(
                text = "U",
                isActive = state.isUnderline,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                onClick = onToggleUnderline,
                contentDescription = "下划线"
            )
            
            FormatToolButton(
                text = "S",
                isActive = state.isStrikethrough,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                onClick = onToggleStrikethrough,
                contentDescription = "删除线"
            )
        }

        /** 竖线分隔符 */
        ToolbarDivider()

        /** ====== 第二组：列表格式 (•/1./☑️) ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "•≡",
                isActive = false,
                onClick = onInsertUnorderedList,
                contentDescription = "无序列表"
            )
            
            FormatToolButton(
                text = "1.",
                isActive = false,
                onClick = onInsertOrderedList,
                contentDescription = "有序列表"
            )
            
            FormatToolButton(
                text = "☑️",
                isActive = false,
                isEmoji = true,
                onClick = onInsertTodoItem,
                contentDescription = "待办子项"
            )
        }

        /** 竖线分隔符 */
        ToolbarDivider()

        /** ====== 第三组：对齐方式 (左/中/右) ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "⬅",
                isActive = false,
                isSymbol = true,
                onClick = onAlignLeft,
                contentDescription = "左对齐"
            )
            
            FormatToolButton(
                text = "⬌",
                isActive = false,
                isSymbol = true,
                onClick = onAlignCenter,
                contentDescription = "居中对齐"
            )
            
            FormatToolButton(
                text = "➡",
                isActive = false,
                isSymbol = true,
                onClick = onAlignRight,
                contentDescription = "右对齐"
            )
        }

        /** 竖线分隔符 */
        ToolbarDivider()

        /** ====== 第四组：插入功能 (颜色/图片) ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "🎨",
                isActive = false,
                isEmoji = true,
                onClick = onSelectColor,
                contentDescription = "文字颜色"
            )
            
            FormatToolButton(
                text = "📷",
                isActive = false,
                isEmoji = true,
                onClick = onInsertImage,
                contentDescription = "插入图片"
            )
        }
    }
}

/**
 * 格式化工具栏按钮组容器
 *
 * 用于将一组相关的格式化按钮包裹在一起，
 * 自动添加内边距和可选的右侧分隔线。
 *
 * @param content 子 Composable lambda（放置按钮）
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
 *
 * 在不同功能的按钮组之间插入视觉分隔线，
 * 帮助用户快速识别不同的功能区域。
 */
@Composable
private fun ToolbarDivider() {
    HorizontalDivider(
        modifier = Modifier
            .height(24.dp)
            .width(1.dp)
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

/**
 * 单个格式化工具按钮组件
 *
 * 支持多种文本展示模式：
 * - **普通文本**: 如 "B"、"I" 等（用于字体样式）
 * - **符号图标**: 如 "⬅"、"⬌" 等（用于对齐方式）
 * - **Emoji 图标**: 如 "☑️"、"🎨" 等（用于特殊功能）
 *
 * 激活状态时显示暖橙色背景和深色文字，
 * 未激活状态时为透明背景。
 *
 * @param text 按钮显示的文本或符号
 * @param isActive 是否处于激活状态
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述（TalkBack 朗读内容）
 * @param isEmoji 是否为 Emoji 图标（调整字号）
 * @param isSymbol 是否为符号图标（调整字号）
 * @param fontStyle 斜体样式（仅 I 按钮）
 * @param textDecoration 装饰线（U/S 按钮）
 */
@Composable
private fun FormatToolButton(
    text: String,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    isEmoji: Boolean = false,
    isSymbol: Boolean = false,
    fontStyle: FontStyle? = null,
    textDecoration: androidx.compose.ui.text.style.TextDecoration? = null
) {
    val backgroundColor = if (isActive) {
        Color(0xFFFFE0C0) // 浅暖橙色背景（激活态）
    } else {
        Color.Transparent // 透明背景（未激活）
    }

    val textColor = if (isActive) {
        Color(0xFFE88A4D) // 深暖橙色文字（激活态）
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant // 默认灰色文字
    }

    val fontSize = when {
        isEmoji -> 18.sp // Emoji 字号稍大
        isSymbol -> 16.sp // 符号字号中等
        else -> 15.sp // 默认字号
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (!isEmoji && !isSymbol && isActive) FontWeight.Bold else FontWeight.Normal,
            fontStyle = fontStyle ?: FontStyle.Normal,
            textDecoration = textDecoration ?: androidx.compose.ui.text.style.TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
