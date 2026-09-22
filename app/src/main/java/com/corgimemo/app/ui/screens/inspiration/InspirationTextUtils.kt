// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationTextUtils.kt
package com.corgimemo.app.ui.screens.inspiration

import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.util.MarkdownParser
import org.json.JSONArray

/**
 * 灵感卡片展示页文本工具类
 * 提供纯文本拼接、字数统计、标签 JSON 解析等纯函数（无 Android 依赖，便于单测）
 */
object InspirationTextUtils {

    /**
     * 构建灵感纯文本（用于复制到剪贴板）
     * 格式：标题 / 日期+时间 / 空行 / 正文 / 空行 / #标签1 #标签2
     * 四部分用换行分隔，结尾 trimEnd 去除末尾空行
     *
     * @param inspiration 灵感实体
     * @param formattedDate 格式化后的日期时间字符串（如 "2019.07.29 09:00"）
     * @return 纯文本字符串
     */
    fun buildInspirationPlainText(inspiration: Inspiration, formattedDate: String): String {
        // 解析标签列表
        val tagsList = parseTags(inspiration.tags)
        // 拼接为 "#标签1 #标签2" 格式
        val tagsStr = tagsList.joinToString(" ") { "#$it" }
        // 按格式拼接四部分（无标签时不输出末尾空行+标签行）
        return buildString {
            appendLine(inspiration.title)
            appendLine(formattedDate)
            appendLine()
            appendLine(inspiration.content)
            if (tagsStr.isNotBlank()) {
                appendLine()
                append(tagsStr)
            }
        }.trimEnd()
    }

    /**
     * 统计灵感总字数（标题 + 正文 + 标签，去除所有空白字符）
     * 中英文均按 1 个字符计数
     *
     * @param inspiration 灵感实体
     * @return 字符数
     */
    fun countInspirationChars(inspiration: Inspiration): Int {
        // 合并三部分文本
        val allText = buildString {
            append(inspiration.title)
            append(inspiration.content)
            append(parseTags(inspiration.tags).joinToString(""))
        }
        // 去除所有空白字符后计数
        return allText.count { !it.isWhitespace() }
    }

    /**
     * 统计灵感正文字符数（仅 content 字段，去除所有空白字符）
     *
     * v2026-07-31 新增：用于灵感编辑页"标题和正文之间"的字数显示、
     * 灵感详情页卡片右上角字数徽章。**只统计正文 content**，不包含：
     * - 标题（title）
     * - 标签（tags，关联到正文的 #关键词）
     * - 关联卡片（relations，与正文无文本关系）
     *
     * 标点符号按 1 个字符计入（含中英文标点、换行、HTML 标签等），与
     * [countInspirationChars] 一致。中英文均按 1 个字符计数。
     *
     * @param content 灵感正文（可能含 Markdown / HTML 标签）
     * @return 正文字符数（去除所有空白字符后）
     */
    fun countInspirationContentChars(content: String): Int {
        // 直接对原文去除所有空白字符后计数，不展开任何标签
        return content.count { !it.isWhitespace() }
    }

    /**
     * 将 BlockNote 导出的 Markdown 转换为纯文本（用于字数统计 / 搜索 / 列表摘要）
     *
     * **背景**：灵感正文现已统一以 `contentFormat`(Markdown) 存储，编辑态由 BlockNote WebView
     * 承载。原先字数统计依赖的 [Inspiration.content]（纯文本）在新流程下不会被 BlockNote 回写，
     * 导致编辑页与详情页都"统计不到字数"。本方法从 Markdown 抽取可读文字，
     * 作为 [Inspiration.content] 的等价来源。
     *
     * **剥离规则**（去除 Markdown 语法、只留可见文字）：
     * - 图片 `![alt](path)` → 移除（图片不计入字数）
     * - 内联媒体 / 标签 token：`#标签` / `@提及` / `🎤语音` / 旧图（`trigger:xxx`）→ 整段移除
     *   （正文计数不含标签 / 提及，与 [countInspirationContentChars] 口径一致）
     * - 普通链接 `[文字](url)` → 保留「文字」
     * - 引用符 `>` → 移除
     * - 可折叠标题的结构标记行 `<details>` / `<summary>` / `</summary>` / `</details>`
     *   → 整行移除（v2026-09-22；纯结构、无可见文字）
     * - 其余行内 / 块级标记（粗体 `**`、斜体 `*`、删除线 `~~`、标题 `#`、列表 / 待办符号）
     *   交给 [MarkdownParser.stripMarkdown] 去除
     *
     * 各移除处统一用空格占位，避免相邻词被拼成一团。
     *
     * @param markdown 正文 Markdown 字符串
     * @return 去除所有 Markdown 语法后的纯文本（空白由调用方去除）
     */
    fun markdownToPlainText(markdown: String): String {
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
        // 7) 可折叠标题的 HTML 编码标记行（v2026-09-22 新增）：整行移除
        //    背景：BlockNote 的折叠标题（heading + isToggleable）在 markdown 里没有原生语法，
        //    正文以 `<details><summary>…</summary></details>` 三段标记包裹持久化（见 WebView 侧
        //    converter.ts）。这三行是纯结构、不承载任何可见文字，若不剥离会污染列表摘要、
        //    字数统计与搜索关键词。
        //    ⚠️ 只删**独占一行的标记**（`[^>\n]*` 限定不跨行匹配），标题文本与其子块行必须保留。
        text = text.replace(Regex("""(?m)^[ \t]*</?(?:details|summary)[^>\n]*>[ \t]*\r?$"""), " ")
        // 8) 其余 Markdown 标记（粗斜体 / 删除线 / 列表 / 待办）
        text = MarkdownParser.stripMarkdown(text)
        return text
    }

    /**
     * 解析标签 JSON 数组字符串
     *
     * @param tagsJson tags 字段的 JSON 字符串（如 `["产品","设计"]`），空字符串返回空列表
     * @return 标签列表
     */
    fun parseTags(tagsJson: String): List<String> {
        // 空字符串直接返回空列表
        if (tagsJson.isBlank()) return emptyList()
        // 解析失败时返回空列表（防止崩溃）
        return try {
            val jsonArray = JSONArray(tagsJson)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
