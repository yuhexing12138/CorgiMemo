package com.corgimemo.app.ui.screens.inspiration.components

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
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** ====== 第一组：基础样式 (B/I/U/S) ====== */
        FormatButtonGroup {
            FormatIconButton(
                imageVector = Icons.Default.FormatBold,
                isActive = state.currentSpanStyle.fontWeight == FontWeight.Bold,
                onClick = onToggleBold,
                contentDescription = "加粗"
            )
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
                imageVector = Icons.Default.FormatListBulleted,
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
                imageVector = Icons.Default.FormatAlignLeft,
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
                imageVector = Icons.Default.FormatAlignRight,
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
