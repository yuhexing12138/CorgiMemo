package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.RichTextState

/**
 * 富文本格式工具栏（使用 compose-rich-editor 库）
 *
 * 提供完整的文本格式化操作，与库的 RichTextState 配合使用。
 * 工具栏分为 4 个功能组，每组用竖线分隔：
 *
 * **功能分组**:
 * 1. **基础样式**: 加粗(B)、斜体(I)、下划线(U)、删除线(S)
 * 2. **列表**: 无序列表、有序列表
 * 3. **对齐**: 左对齐、居中、右对齐（通过 toggleParagraphStyle）
 * 4. **高级**: 链接、代码块
 *
 * 每个按钮支持激活状态显示（暖橙色高亮），
 * 符合项目整体 UI 设计规范（暖橙色主题 #FF9A5C）。
 *
 * @param state 库的 RichTextState 实例
 * @param modifier Modifier
 * @param onToggleBold 加粗回调
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
    onToggleBold: () -> Unit,
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 第一组：基础样式 (B/I/U/S) ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "B",
                isActive = state.currentSpanStyle.fontWeight == FontWeight.Bold,
                onClick = onToggleBold,
                contentDescription = "加粗"
            )
            FormatToolButton(
                text = "I",
                isActive = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                fontStyle = FontStyle.Italic,
                onClick = onToggleItalic,
                contentDescription = "斜体"
            )
            FormatToolButton(
                text = "U",
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true,
                textDecoration = TextDecoration.Underline,
                onClick = onToggleUnderline,
                contentDescription = "下划线"
            )
            FormatToolButton(
                text = "S",
                isActive = state.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true,
                textDecoration = TextDecoration.LineThrough,
                onClick = onToggleStrikethrough,
                contentDescription = "删除线"
            )
        }

        ToolbarDivider()

        /** ====== 第二组：列表 ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "•≡",
                isActive = state.isUnorderedList,
                onClick = onInsertUnorderedList,
                contentDescription = "无序列表"
            )
            FormatToolButton(
                text = "1.",
                isActive = state.isOrderedList,
                onClick = onInsertOrderedList,
                contentDescription = "有序列表"
            )
        }

        ToolbarDivider()

        /** ====== 第三组：对齐方式 ====== */
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

        ToolbarDivider()

        /** ====== 第四组：高级（链接 + 代码块） ====== */
        FormatButtonGroup {
            FormatToolButton(
                text = "🔗",
                isActive = state.isLink,
                isEmoji = true,
                onClick = onInsertLink,
                contentDescription = "插入链接"
            )
            FormatToolButton(
                text = "</>",
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
            .size(width = 1.dp, height = 24.dp)
            .padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
}

/**
 * 单个格式化工具按钮
 *
 * @param text 按钮文本
 * @param isActive 是否激活
 * @param onClick 点击回调
 * @param contentDescription 无障碍描述
 * @param isEmoji 是否为 Emoji（调整字号）
 * @param isSymbol 是否为符号（调整字号）
 * @param fontStyle 斜体样式
 * @param textDecoration 装饰线
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
    textDecoration: TextDecoration? = null
) {
    val backgroundColor = if (isActive) {
        Color(0xFFFFE0C0) // 浅暖橙色背景（激活态）
    } else {
        Color.Transparent
    }

    val textColor = if (isActive) {
        Color(0xFFE88A4D) // 深暖橙色文字（激活态）
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val fontSize = when {
        isEmoji -> 18.sp
        isSymbol -> 16.sp
        else -> 15.sp
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
            textDecoration = textDecoration ?: TextDecoration.None
        )
    }
}
