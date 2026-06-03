package com.corgimemo.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * 轻量级 Markdown 行内文本渲染组件
 *
 * 用于待办卡片列表等空间受限场景的富文本预览。
 * 仅渲染行内样式（**粗体**、*斜体*、~~删除线~~），
 * 不渲染块级元素（标题字号变化、列表缩进等）。
 *
 * **与完整 Markdown 渲染的区别**:
 * - 完整渲染：保留标题字号、列表缩进、代码块等（适用于详情页）
 * - 轻量行内：仅保留字符级样式，截断显示（适用于列表卡片摘要）
 *
 * **使用示例**:
 * ```kotlin
 * if (todo.contentFormat.isNotBlank()) {
 *     MarkdownInlineText(
 *         markdown = todo.contentFormat,
 *         maxLines = 2,
 *         overflow = TextOverflow.Ellipsis
 *     )
 * }
 * ```
 *
 * @param markdown Markdown 格式文本字符串
 * @param modifier Modifier（可选）
 * @param maxLines 最大显示行数（默认 2，比原纯文本多 1 行以容纳格式信息）
 * @param overflow 超出处理方式（默认省略号截断）
 * @param style 基础文字样式（颜色、字号等由调用方指定）
 */
@Composable
fun MarkdownInlineText(
    markdown: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    style: TextStyle = TextStyle.Default
) {
    /**
     * 使用复合 remember key 缓存解析结果
     *
     * 缓存命中条件（任一变化时重新解析）：
     * - markdown 字符串内容变化（编辑后返回列表）
     * - maxLines 参数变化（布局调整）
     * - style 的颜色/字号变化（主题切换或背景色变更）
     *
     * **虚拟化优化**:
     * 当 LazyColumn 滚动触发重组时，如果上述 key 均未变化，
     * Compose 会直接返回缓存的 AnnotatedString，跳过 safeParse() 调用。
     * 这对于包含格式化内容的待办卡片列表性能提升显著。
     */
    val cacheKey = Triple(markdown, maxLines, style.color to style.fontSize)
    val cachedAnnotatedString = androidx.compose.runtime.remember(cacheKey) {
        com.corgimemo.app.util.MarkdownParser.safeParse(markdown)
    }

    /**
     * 使用 Compose Text 组件渲染 AnnotatedString
     *
     * Text 组件原生支持 AnnotatedString，
     * 会自动应用其中包含的 SpanStyle（粗体/斜体/删除线等）。
     */
    Text(
        text = if (cachedAnnotatedString.isEmpty()) AnnotatedString(markdown) else cachedAnnotatedString,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow,
        style = style,
        onTextLayout = { textLayoutResult ->
            // 预留：可用于后续实现"展开全文"交互
        }
    )
}
