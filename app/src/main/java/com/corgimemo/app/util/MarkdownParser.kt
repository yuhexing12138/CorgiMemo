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
     * **字重语义（与库侧 markdown 对齐）**：
     * - `**` 表示标准粗体，映射为 `FontWeight.Bold(700)`，与 compose-rich-editor
     *   库 `setMarkdown` 对 `**` 的解析保持一致（避免同份 `**` 在编辑态=700、
     *   预览/加载态=800 的不一致）。
     * - 非 700 字重（如 ExtraBold 800）不靠 `**` 表达，而是经 `parseSpanWeight`
     *   由 `<span style="font-weight:N">` 还原，详见 [parseSpanWeight]。
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

                /** 处理粗体内容（内部可能包含斜体）；`**` 映射标准 Bold(700) */
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
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

    /**
     * Markdown → 纯文本（**灵感正文的标准转换入口**，v2026-09-22 新增）
     *
     * **为什么要有它**：BlockNote 迁到 WebView 后，灵感正文本体是 markdown
     * （`contentFormat`），而首页摘要、搜索、字数统计都依赖纯文本字段
     * （`Inspiration.content`）。此前这些地方各自调 [stripMarkdown]，但
     * `stripMarkdown` **只处理 markdown 语法**，既不剥 HTML 标签、也不剥本项目
     * 自编码的内部占位 token——于是 `<span style="color:…">`、
     * `@@@CORGI_BC_TC_red@@@` 这类标记会原样漏进摘要（真机已复现）。
     * 本方法把完整口径收敛到一处，避免调用方各写一份、漏一处就漏一处。
     *
     * **剥离规则**（去除语法与结构标记、只留可见文字）：
     * - 图片 `![alt](path)` → 移除（图片不计入字数）
     * - 内联媒体 / 标签 token：`#标签` / `@提及` / `🎤语音` / 旧图（`trigger:xxx`）→ 整段移除
     * - 普通链接 `[文字](url)` → 保留「文字」
     * - 引用符 `>`、ATX 标题 `#`、分割线 `---`（`stripMarkdown` 不处理的这几类）→ 移除
     * - 可折叠标题的独占行标记 `<details>` / `<summary>` → 整行移除
     * - 行内色 `<span style="…">` / `</span>` → **只删标签本体、保留标签之间的文字**
     * - 项目内部占位 token `@@@CORGI_…@@@`（块级色、分割线等自编码）→ 整段移除
     * - 其余行内 / 块级标记交给 [stripMarkdown]
     *
     * 各移除处统一用空格占位，避免相邻词被拼成一团。
     *
     * ⚠️ 适用范围：面向**灵感**正文（含"标签 token 不计入正文"的口径）。
     * 待办正文若直接复用本方法，会连 `#标签` 文字一起丢掉——待办有独立口径，勿混用。
     *
     * @param markdown 正文 Markdown 字符串
     * @return 去除所有标记后的纯文本（空白由调用方自行去除）
     */
    fun toPlainText(markdown: String): String {
        // 空文本直接返回，避免无谓的正则开销
        if (markdown.isBlank()) return ""
        var text = markdown
        // 1) 图片（不计入字数）
        text = text.replace(Regex("""!\[[^\]]*\]\([^)]*\)"""), " ")
        // 2) 内联媒体 / 标签 token：整段移除
        text = text.replace(Regex("""\[#[^]]*\]\(trigger:hashtag:[^)]*\)"""), " ")
        text = text.replace(Regex("""\[@[^]]*\]\(trigger:mention:[^)]*\)"""), " ")
        text = text.replace(Regex("""\[[^\]]*\]\(trigger:voice:[^)]*\)"""), " ")
        text = text.replace(Regex("""\[[^\]]*\]\(trigger:image:[^)]*\)"""), " ")
        // 3) 普通链接 [文字](url) → 保留「文字」
        text = text.replace(Regex("""\[([^\]]*)\]\([^)]*\)"""), "$1")
        // 4) 引用符（stripMarkdown 不处理，单独去除）
        text = text.replace(Regex("""(?m)^\s*>\s?"""), "")
        // 5) 标题（覆盖 stripMarkdown 仅支持 1~4 级的限制，深标题 5~6 级也去除）
        text = text.replace(Regex("""(?m)^\s{0,3}#{1,6}\s+"""), "")
        // 6) 分割线 --- / *** / ___（stripMarkdown 不处理，单独去除）
        text = text.replace(Regex("""(?m)^\s*([-*_])(\s*\1){2,}\s*$"""), " ")
        // 7) 可折叠标题的标记行 + 项目内部占位 token（v2026-09-22 新增）
        //    —— 两类"纯结构"标记的识别收敛在 [stripStructuralMarkers]，供纯文本抽取与
        //    Compose 侧只读渲染共用，避免口径漂移（真机两次泄漏都源于各写一份正则）。
        text = stripStructuralMarkers(text)
        // 8) 行内色 span（v2026-09-22 新增）：标签本体移除、**文字保留**
        //    行内色以原生 HTML 持久化（`<span style="color:#FF9A5C">文字</span>`），
        //    标签不承载可见文字必须去掉，但**不能整行删**——文字就在标签之间。
        //    ⚠️ 本步只属于"纯文本"口径：Compose 侧渲染要**保留** span（库的 markdown
        //    解析器会读 style 里的 font-size/color/background-color 还原排版）。
        text = text.replace(Regex("""</?span[^>\n]*>"""), "")
        // 9) 其余 Markdown 标记（粗斜体 / 删除线 / 列表 / 待办）
        text = stripMarkdown(text)
        return text
    }

    /* ===== 项目内部标记的识别（单点真相，v2026-09-22 新增）===== */

    /**
     * 项目自编码的占位 token（由 WebView 侧 `converter.ts` 写入正文 markdown）
     *
     * 形态如 `@@@CORGI_BC_TC_red@@@`（块级色）/ `@@@CORGI_DIVIDER_dashed@@@`（分割线）。
     * ⚠️ 用统一前缀通配，后续新增同类 token 无需再改这里。
     */
    private val INTERNAL_TOKEN_REGEX = Regex("""@@@CORGI_[A-Za-z0-9_#]*@@@""")

    /**
     * 可折叠标题的**独占行**结构标记
     *
     * 折叠标题（`heading` + `isToggleable`）在 markdown 里没有原生语法，正文以
     * `<details><summary>…</summary></details>` 三段标记包裹持久化（见 converter.ts）。
     * ⚠️ 只匹配**独占一行的标记**（`[^>\n]*` 限定不跨行），标题文本与子块行必须保留。
     */
    private val DETAILS_MARKER_LINE_REGEX =
        Regex("""(?m)^[ \t]*</?(?:details|summary)[^>\n]*>[ \t]*\r?$""")

    /**
     * 剥离"项目结构标记"（`@@@CORGI_…@@@` 占位 token + 折叠标题的 `<details>` 标记行）
     *
     * **保留**一切渲染型 HTML 与 markdown 语法（`<span style>` 承载字号/颜色、
     * `**粗体**`、`### 标题`、`- [ ]` 待办等），只清掉**纯结构、无渲染语义**的两类：
     * - `@@@CORGI_…@@@`（WebView 侧 converter.ts 自编码的块级色等占位 token）；
     * - **独占一行**的 `<details>` / `<summary>` / 闭合标签（折叠标题的包裹标记）。
     *
     * **两个消费方共用本方法**（口径只此一份，真机两次泄漏都源于各写一份正则）：
     * - `toPlainText`：再叠加"删 span 标签 / 去 markdown 语法"成为纯文本；
     * - Compose 详情卡只读渲染（`InspirationViewCard`）：库的解析器不认识这两类标记，
     *   会原样当文字渲染（真机现象：正文出现 `@@@CORGI_BC_TC_red@@@`）。
     *   ⚠️ 折叠标题在详情页的口径是"**显示为普通标题即可**"（用户确认）——
     *   标记行整行剥掉后，标题行照常按 `### 文本` 渲染，详情页不做折叠交互。
     *
     * @param markdown 正文 markdown（整篇或切分后的单段均可）
     * @return 去掉项目结构标记后的 markdown
     */
    fun stripStructuralMarkers(markdown: String): String {
        if (markdown.isEmpty()) return markdown
        return markdown
            .replace(INTERNAL_TOKEN_REGEX, "")
            .replace(DETAILS_MARKER_LINE_REGEX, "")
    }

    /**
     * 取文本**开头连续**的项目内部 token（[stripStructuralMarkers] 的逆操作，用于回写保色）
     *
     * **为什么需要**：详情卡的复选框勾选会把该段的 markdown 交给库重新编码后回写整篇
     * （`paragraphs[pIdx] = newPara` → join）。库重新编码出的段落**不含**我们插入的
     * 行首 token，若不补回，一次勾选就会把该段的块级色标记从库里抹掉。
     *
     * @param markdown 原始（未剥离的）段落 markdown
     * @return 段首连续 token 的拼接串；没有则返回空串
     */
    fun leadingInternalTokens(markdown: String): String {
        if (markdown.isEmpty()) return ""
        var rest = markdown
        val tokens = StringBuilder()
        while (true) {
            val matched = INTERNAL_TOKEN_REGEX.find(rest, 0)
            if (matched == null || matched.range.first != 0) break
            tokens.append(matched.value)
            rest = rest.substring(matched.range.last + 1)
        }
        return tokens.toString()
    }

    /**
     * 段首**块级色**标记的解析结果（v2026-09-22 新增）
     *
     * 取值为 BlockNote 预设**色名**（A 面板「段落文字色 / 段落背景色」下行传的就是色名，
     * 见 `BlockColorPicker`），交由宿主色板映射成实际颜色。
     *
     * @param textColor 段落文字色名；null = 该维度未设置
     * @param backgroundColor 段落背景色名；null = 该维度未设置
     */
    data class BlockColorMarkers(
        val textColor: String? = null,
        val backgroundColor: String? = null,
    )

    /**
     * 段首块级色 token 的逐个匹配：`@@@CORGI_BC_TC_<色名>@@@` / `@@@CORGI_BC_BG_<色名>@@@`
     *
     * 值字符集与写入侧一致（`[0-9A-Za-z#]+`），`@` 不在集内故不会吞掉结尾的 `@@@`。
     */
    private val BLOCK_COLOR_TOKEN_REGEX = Regex("""@@@CORGI_BC_(TC|BG)_([0-9A-Za-z#]+)@@@""")

    /**
     * 解析并剥离段首的**块级色**标记
     *
     * **为什么需要**：块级色是块的 props，官方 markdown 导出会把它写成块元素上的
     * `data-text-color` / `data-background-color` 属性，而 markdown 的块序列化器**不读元素属性**
     * —— 于是它只能靠 converter.ts 写入的段首 token 承载。
     * WebView 编辑器载入时会消费这些 token 写回 props，而 **Compose 详情页**不走那条路，
     * 需要自己解析：不解析就会把颜色信息连同 token 一起丢掉（表现为"段落文字色/背景色在
     * 详情页不显示"）；只剥不解析更是必丢。
     *
     * @param markdown 段落 markdown（整篇亦可，但语义上是"段首"）
     * @return Pair(解析出的色值, 剥离 token 后的 markdown)；无 token 时色值为 null
     */
    fun extractBlockColorMarkers(markdown: String): Pair<BlockColorMarkers?, String> {
        if (markdown.isEmpty()) return null to markdown
        var rest = markdown
        var textColor: String? = null
        var backgroundColor: String? = null
        while (true) {
            val matched = BLOCK_COLOR_TOKEN_REGEX.find(rest, 0)
            if (matched == null || matched.range.first != 0) break
            if (matched.groupValues[1] == "TC") textColor = matched.groupValues[2]
            else backgroundColor = matched.groupValues[2]
            rest = rest.substring(matched.range.last + 1)
        }
        val markers = if (textColor == null && backgroundColor == null) {
            null
        } else {
            BlockColorMarkers(textColor = textColor, backgroundColor = backgroundColor)
        }
        return markers to rest
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
