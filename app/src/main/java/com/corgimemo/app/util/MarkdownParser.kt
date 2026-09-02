package com.corgimemo.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

/**
 * Markdown 解析器工具类
 *
 * 提供 Markdown 文本与 AnnotatedString 之间的双向转换功能，
 * 用于富文本编辑器的导入导出操作。
 *
 * **支持的 Markdown 语法**:
 * - **粗体**: `**text**` 或 `__text__`
 * - **斜体**: `*text*` 或 `_text_`
 * - **删除线**: `~~text~~`
 * - **字重档位**: `<span style="font-weight:800">text</span>`（非标准粗体如 ExtraBold，与 `**` 并存，用于往返保留字重数值）
 * - **无序列表**: `- item` 或 `* item`
 * - **有序列表**: `1. item`
 * - **待办列表**: `- [ ] 未完成` / `- [x] 已完成`
 * - **标题**: `# H1` / `## H2` / `### H3`
 *
 * **使用示例**:
 * ```kotlin
 * // 解析 Markdown → AnnotatedString
 * val annotatedString = MarkdownParser.parse("**粗体** _斜体_ ~~删除线~~")
 *
 * // 导出 AnnotatedString → Markdown
 * val markdown = MarkdownParser.export(annotatedString)
 * ```
 *
 * **设计原则**:
 * - 使用正则表达式进行高效的模式匹配
 * - 支持嵌套格式（如 **粗体_斜体_**）
 * - 保持原始文本的完整性（不丢失任何字符）
 * - 线程安全：所有方法均为纯函数，无状态依赖
 */
object MarkdownParser {

    /**
     * 正则表达式模式常量
     *
     * 定义各种 Markdown 语法的匹配模式，
     * 使用原始字符串（raw string）避免转义字符问题。
     */
    private object Patterns {
        /** 粗体模式：**text** 或 __text__ */
        val BOLD = Regex("""(\*\*|__)(?=\S)(.+?)(?<=\S)\1""")

        /** 斜体模式：*text* 或 _text_（排除已匹配的粗体） */
        val ITALIC = Regex("""(?<!\*)(\*|_)(?=\S)(.+?)(?<=\S)\1(?!\*)""")

        /** 字重 span 模式：<span style="font-weight:800">text</span>
         *  用于保留非标准粗体档位（如 ExtraBold 800），与库侧 markdown 往返一致：
         *  标准 Bold(700) 走 `**`，非 700 字重（含 750/800/900 任意数值）走 HTML span 表达。 */
        val FONT_WEIGHT_SPAN = Regex(
            """<span\s+style\s*=\s*["']font-weight\s*:\s*(\d+)\s*["']\s*>([\s\S]+?)</span>""",
            RegexOption.IGNORE_CASE
        )

        /** 删除线模式：~~text~~ */
        val STRIKETHROUGH = Regex("""(~~)(?=\S)(.+?)(?<=\S)\1""")

        /** 无序列表模式：- item 或 * item */
        val UNORDERED_LIST = Regex("""^(\s*)[-*]\s+(.*)$""", RegexOption.MULTILINE)

        /** 有序列表模式：1. item */
        val ORDERED_LIST = Regex("""^(\s*)\d+\.\s+(.*)$""", RegexOption.MULTILINE)

        /** 待办列表模式：- [x] 或 - [ ] */
        val TODO_LIST = Regex("""^(\s*)-\s+\[([ x])\]\s+(.*)$""", RegexOption.MULTILINE)

        /** 标题模式：# ## ### #### */
        val HEADING = Regex("""^(#{1,4})\s+(.*)$""", RegexOption.MULTILINE)
    }

    /**
     * 将 Markdown 文本解析为 AnnotatedString
     *
     * 按照以下优先级顺序应用样式（避免冲突）：
     * 1. 标题样式（ParagraphStyle）
     * 2. 列表标记处理
     * 3. 删除线样式
     * 4. 粗体样式
     * 5. 斜体样式
     *
     * @param markdown Markdown 格式的文本字符串
     * @return 带有样式的 AnnotatedString 对象
     */
    fun parse(markdown: String): AnnotatedString {
        if (markdown.isBlank()) {
            return AnnotatedString("")
        }

        return buildAnnotatedString {
            /** 按行分割文本，逐行处理 */
            val lines = markdown.split("\n")

            lines.forEachIndexed { index, line ->
                /** 处理标题样式 */
                if (Patterns.HEADING.containsMatchIn(line)) {
                    val matchResult = Patterns.HEADING.find(line)!!
                    val level = matchResult.groupValues[1].length
                    val content = matchResult.groupValues[2]

                    withStyle(style = ParagraphStyle()) {
                        appendLine(parseInlineFormats(content))
                    }
                }
                /** 处理待办列表 */
                else if (Patterns.TODO_LIST.containsMatchIn(line)) {
                    val matchResult = Patterns.TODO_LIST.find(line)!!
                    val isChecked = matchResult.groupValues[2].trim() == "x"
                    val content = matchResult.groupValues[3]
                    val checkbox = if (isChecked) "☑ " else "☐ "

                    append(checkbox)
                    append(parseInlineFormats(content))
                    append("\n")
                }
                /** 处理无序列表 */
                else if (Patterns.UNORDERED_LIST.containsMatchIn(line)) {
                    val matchResult = Patterns.UNORDERED_LIST.find(line)!!
                    val content = matchResult.groupValues[2]

                    append("• ")
                    append(parseInlineFormats(content))
                    append("\n")
                }
                /** 处理有序列表 */
                else if (Patterns.ORDERED_LIST.containsMatchIn(line)) {
                    val matchResult = Patterns.ORDERED_LIST.find(line)!!
                    val content = matchResult.groupValues[2]

                    append("1. ")
                    append(parseInlineFormats(content))
                    append("\n")
                }
                /** 普通文本：仅处理行内格式 */
                else {
                    append(parseInlineFormats(line))
                    if (index < lines.size - 1) {
                        append("\n")
                    }
                }
            }
        }
    }

    /**
     * 解析行内格式（删除线、字重 span、粗体、斜体）
     *
     * 处理单行文本中的字符级样式标记，
     * 支持嵌套和重叠的格式组合。
     * 层级顺序（由外到内）：删除线 > 字重 span > 粗体 > 斜体。
     *
     * @param line 单行文本
     * @return 带有行内样式的 AnnotatedString
     */
    private fun parseInlineFormats(line: String): AnnotatedString {
        if (line.isBlank()) {
            return AnnotatedString(line)
        }

        return buildAnnotatedString {
            var remainingText = line

            /** 查找并应用删除线样式（优先级最高），内部仍支持字重 span/粗体/斜体 */
            var strikethroughMatches = Patterns.STRIKETHROUGH.findAll(remainingText).toList()
            strikethroughMatches.forEach { match ->
                val before = remainingText.substring(0, match.range.first)
                val content = match.groupValues[2]
                val after = remainingText.substring(match.range.last + 1)

                append(parseSpanWeight(before))

                withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(parseSpanWeight(content))
                }

                remainingText = after
            }

            /** 处理剩余未匹配的文本（含字重 span/粗体/斜体） */
            append(parseSpanWeight(remainingText))
        }
    }

    /**
     * 解析粗体和斜体样式
     *
     * 处理文本中的 **bold** 和 *italic* 标记，
     * 支持两种语法的混合使用。
     *
     * @param text 输入文本
     * @return 应用粗体/斜体样式的 AnnotatedString
     */
    private fun parseBoldAndItalic(text: String): AnnotatedString {
        if (text.isBlank()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            var remainingText = text

            /** 查找所有粗体匹配 */
            var boldMatches = Patterns.BOLD.findAll(remainingText).toList()

            boldMatches.forEach { match ->
                val before = remainingText.substring(0, match.range.first)
                val content = match.groupValues[2]
                val after = remainingText.substring(match.range.last + 1)

                /** 处理粗体前的普通文本（可能包含斜体） */
                append(parseItalicOnly(before))

                /** 处理粗体内容（内部可能包含斜体） */
                withStyle(style = SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(parseItalicOnly(content))
                }

                remainingText = after
            }

            /** 处理剩余文本 */
            append(parseItalicOnly(remainingText))
        }
    }

    /**
     * 解析字重 span 标记：<span style="font-weight:N">text</span>
     *
     * 还原经 markdown 往返保留的非标准字重档位（如 ExtraBold 800），
     * 与库侧 RichTextStateMarkdownParser 的 encode/decode 保持一致：
     * 标准 Bold(700) 用 `**`，非 700 字重（含 750/800/900 任意数值）用 HTML `<span style>` 表达。
     * span 内部仍可嵌套粗体/斜体，故委托 parseBoldAndItalic 处理其内容。
     *
     * @param text 输入文本
     * @return 应用对应字重样式的 AnnotatedString
     */
    private fun parseSpanWeight(text: String): AnnotatedString {
        val spanPattern = Patterns.FONT_WEIGHT_SPAN
        if (!spanPattern.containsMatchIn(text)) {
            return parseBoldAndItalic(text)
        }

        return buildAnnotatedString {
            var remainingText = text

            /** 查找所有字重 span 匹配 */
            spanPattern.findAll(remainingText).toList().forEach { match ->
                val before = remainingText.substring(0, match.range.first)
                val weight = match.groupValues[1].toIntOrNull() ?: 400
                val content = match.groupValues[2]
                val after = remainingText.substring(match.range.last + 1)

                /** span 前的文本（可能含粗体/斜体） */
                append(parseBoldAndItalic(before))

                /** span 内容按字重渲染（内部仍支持粗体/斜体） */
                withStyle(style = SpanStyle(fontWeight = FontWeight(weight))) {
                    append(parseBoldAndItalic(content))
                }

                remainingText = after
            }

            /** 处理剩余文本 */
            append(parseBoldAndItalic(remainingText))
        }
    }

    /**
     * 仅解析斜体样式（在已确定非粗体的文本中查找）
     *
     * 避免与粗体正则表达式冲突，
     * 只匹配单个 * 或 _ 包裹的文本。
     *
     * @param text 输入文本
     * @return 应用斜体样式的 AnnotatedString
     */
    private fun parseItalicOnly(text: String): AnnotatedString {
        if (text.isBlank()) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            var remainingText = text

            /** 查找所有斜体匹配 */
            var italicMatches = Patterns.ITALIC.findAll(remainingText).toList()

            italicMatches.forEach { match ->
                val before = remainingText.substring(0, match.range.first)
                val content = match.groupValues[2]
                val after = remainingText.substring(match.range.last + 1)

                /** 斜体前的纯文本 */
                append(AnnotatedString(before))

                /** 斜体内容 */
                withStyle(style = SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(AnnotatedString(content))
                }

                remainingText = after
            }

            /** 剩余纯文本 */
            append(AnnotatedString(remainingText))
        }
    }

    /**
     * 将 AnnotatedString 导出为 Markdown 格式文本
     *
     * 遍历 AnnotatedString 的所有 SpanStyle，
     * 将其转换为对应的 Markdown 标记。
     *
     * **导出规则**:
     * - Bold(700) + Italic → `***text***`
     * - Bold(700) → `**text**`
     * - 非 700 字重（如 ExtraBold 800）+ 斜体 → `<span style="font-weight:800">*text*</span>`
     * - 非 700 字重（如 ExtraBold 800）→ `<span style="font-weight:800">text</span>`
     * - Italic → `*text*`
     * - Strikethrough → `~~text~~`
     * - 组合样式按上述顺序叠加（字重数值 700/750/800/900 等任意档位均保留）
     *
     * @param annotatedString 带样式的 AnnotatedString 对象
     * @return Markdown 格式的字符串
     */
    fun export(annotatedString: AnnotatedString): String {
        if (annotatedString.isEmpty()) {
            return ""
        }

        val text = annotatedString.text
        val spanStyles = annotatedString.spanStyles

        /** 如果没有任何样式，直接返回纯文本 */
        if (spanStyles.isEmpty()) {
            return text
        }

        /** 构建结果字符串 */
        val result = StringBuilder()
        var lastIndex = 0

        spanStyles.sortedBy { it.start }.forEach { spanStyle ->
            /** 添加样式前的普通文本 */
            if (spanStyle.start > lastIndex) {
                result.append(text.substring(lastIndex, spanStyle.start))
            }

            /** 提取样式范围内的文本 */
            val styledText = text.substring(spanStyle.start, spanStyle.end)

            /** 根据 SpanStyle 计算 Markdown 包裹标记（支持 700/800 多字重档位）
             *  标准 Bold(700) 用 `**` 兼容通用 markdown；非 700 字重（如 ExtraBold 800）
             *  改用 HTML `<span style="font-weight:N">` 表达，使字重数值在 markdown 往返中保留。 */
            val fw = spanStyle.item.fontWeight
            val isItalic = spanStyle.item.fontStyle == FontStyle.Italic
            val isStrike = spanStyle.item.textDecoration?.contains(TextDecoration.LineThrough) == true

            val (openTag, closeTag) = when {
                // 非 700 字重 + 斜体：外层 span 包裹内层斜体标记
                fw != null && fw.weight > 400 && fw.weight != 700 && isItalic ->
                    "<span style=\"font-weight:${fw.weight}\">*" to "*</span>"
                // 非 700 字重（如 ExtraBold 800）：HTML span 表达字重数值
                fw != null && fw.weight > 400 && fw.weight != 700 ->
                    "<span style=\"font-weight:${fw.weight}\">" to "</span>"
                // 标准 Bold(700) + 斜体
                fw != null && fw.weight > 400 && isItalic -> "***" to "***"
                // 标准 Bold(700)
                fw != null && fw.weight > 400 -> "**" to "**"
                isItalic -> "*" to "*"
                isStrike -> "~~" to "~~"
                else -> "" to ""
            }

            /** 包裹样式标记 */
            if (openTag.isNotEmpty()) {
                result.append(openTag)
                result.append(styledText)
                result.append(closeTag)
            } else {
                result.append(styledText)
            }

            lastIndex = spanStyle.end
        }

        /** 添加剩余的普通文本 */
        if (lastIndex < text.length) {
            result.append(text.substring(lastIndex))
        }

        return result.toString()
    }

    /**
     * 检查文本是否包含 Markdown 格式标记
     *
     * 用于判断是否需要调用 parse() 方法进行解析，
     * 避免对纯文本进行不必要的正则匹配操作。
     *
     * @param text 待检查的文本
     * @return 如果包含任意 Markdown 标记返回 true，否则 false
     */
    fun containsMarkdown(text: String): Boolean {
        return Patterns.BOLD.containsMatchIn(text) ||
                Patterns.ITALIC.containsMatchIn(text) ||
                Patterns.STRIKETHROUGH.containsMatchIn(text) ||
                Patterns.FONT_WEIGHT_SPAN.containsMatchIn(text) ||
                Patterns.HEADING.containsMatchIn(text) ||
                Patterns.TODO_LIST.containsMatchIn(text) ||
                Patterns.UNORDERED_LIST.containsMatchIn(text) ||
                Patterns.ORDERED_LIST.containsMatchIn(text)
    }

    /**
     * 移除所有 Markdown 标记，返回纯文本
     *
     * 用于搜索、统计字数等需要纯文本的场景。
     *
     * @param markdown 包含 Markdown 标记的文本
     * @return 移除所有标记后的纯文本字符串
     */
    fun stripMarkdown(markdown: String): String {
        var result = markdown

        /** 按照从复杂到简单的顺序移除标记（避免冲突） */
        result = result.replace(Patterns.STRIKETHROUGH, "$2")
        result = result.replace(Patterns.BOLD, "$2")
        result = result.replace(Patterns.FONT_WEIGHT_SPAN, "$2")
        result = result.replace(Patterns.ITALIC, "$2")
        result = result.replace(Patterns.HEADING, "$2")
        result = result.replace(Patterns.TODO_LIST, "$3")
        result = result.replace(Patterns.UNORDERED_LIST, "$2")
        result = result.replace(Patterns.ORDERED_LIST, "$2")

        return result.trim()
    }

    // ==================== 校验与安全解析方法 ====================

    /**
     * 修复未闭合的成对标记
     *
     * 检测文本末尾是否存在未闭合的指定标记（如 `**`、`~~`），
     * 如果存在则剥离尾部不完整的标记字符。
     *
     * **处理策略**:
     * - 统计标记在文本中出现的总次数
     * - 如果为奇数，说明有一个未闭合的标记
     * - 从文本末尾开始剥离标记字符，直到计数变为偶数
     *
     * @param text 待检测的文本
     * @param tag 成对标记字符串（如 "**"、"~~"）
     * @return 修复后的文本
     */
    private fun fixUnclosedTag(text: String, tag: String): String {
        if (text.isBlank() || tag.isBlank()) return text

        val tagLength = tag.length
        var result = text

        /** 统计标记出现的次数（非正则简单计数） */
        var count = 0
        var index = 0
        while (index <= result.length - tagLength) {
            if (result.substring(index, index + tagLength) == tag) {
                count++
                index += tagLength
            } else {
                index++
            }
        }

        /** 奇数次出现 → 存在未闭合标记 → 从末尾逐字剥离 */
        while (count % 2 != 0 && result.endsWith(tag[0])) {
            result = result.dropLast(1)
            count--
        }

        return result
    }

    /**
     * 校验并清理 Markdown 文本中的常见损坏模式
     *
     * 检测并自动修复以下问题：
     * 1. 未闭合的粗体标记 (`**text` → `**text**` 或剥离尾部 `*`)
     * 2. 未闭合的删除线标记 (`~~text` → `~~text~~` 或剥离尾部 `~`)
     * 3. 未闭合的斜体标记（单星号，需排除已匹配的双星号）
     *
     * **调用时机**: 保存前由 performSave() 调用，确保存储的 Markdown 合法。
     *
     * @param markdown 待校验的 Markdown 文本
     * @return 修复后的安全 Markdown 文本
     */
    fun validateAndSanitize(markdown: String): String {
        if (markdown.isBlank()) return ""

        var result = markdown.trimEnd()

        /** 按从长到短的顺序修复（避免短标记误匹配长标记的一部分） */
        result = fixUnclosedTag(result, "**")   // 粗体（4 字符，优先检查）
        result = fixUnclosedTag(result, "~~")   // 删除线（2 字符）
        result = fixUnclosedTag(result, "*")    // 斜体/粗体残留（1 字符）

        return result
    }

    /**
     * 安全解析 Markdown（带异常容错）
     *
     * 在 parse() 基础上增加 try-catch 异常保护，
     * 当解析过程出现任何异常时，自动回退为剥离所有标记的纯文本。
     *
     * **使用场景**: TodoEditScreen 的 LaunchedEffect 中加载已有待办时调用，
     * 确保即使数据库中存储了损坏的 Markdown 数据也不会崩溃。
     *
     * @param markdown Markdown 格式文本
     * @return 解析成功的 AnnotatedString；异常时返回纯文本版本
     */
    fun safeParse(markdown: String): AnnotatedString {
        if (markdown.isBlank()) return AnnotatedString("")

        return try {
            val sanitized = validateAndSanitize(markdown)
            if (sanitized.isBlank()) AnnotatedString("")
            else parse(sanitized)
        } catch (e: Exception) {
            android.util.Log.w("MarkdownParser", "safeParse 异常，回退纯文本", e)
            AnnotatedString(stripMarkdown(markdown))
        }
    }
}
