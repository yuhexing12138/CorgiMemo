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
     * **实现已下沉到 [MarkdownParser.toPlainText]**（v2026-09-22）：本方法只是保持既有
     * 调用点不变的薄委托。原实现只在本类里，而 ViewModel / Repository 的保存与回填路径
     * 各自调的是 [MarkdownParser.stripMarkdown]（不剥 HTML 与内部 token）——同一个
     * "markdown → 纯文本"需求存在两份口径，结果就是摘要里漏出 `@@@CORGI_…@@@`
     * 与 `<span style="…">`。现已收敛为单点真相：
     * **凡灵感正文取纯文本，一律走 [MarkdownParser.toPlainText]**。
     *
     * 剥离规则（图片 / 标签 token / 链接 / 引用 / 标题 / 分割线 / details 标记行 /
     * 行内 span / `@@@CORGI_…@@@` 占位 token / 其余 markdown 语法）详见该方法 KDoc。
     *
     * ⚠️ 本方法**保留段落间的空行**（`\n\n`）——这是正文本来的段落结构，字数统计、
     * 剪贴板复制、搜索都依赖它。若要用于**单块文本摘要渲染**（Compose `Text` 会把
     * 每个 `\n` 画成一整行空行、导致行距翻倍），必须先过一层 [collapseBlankLines]。
     * 本方法**不**内置折叠是刻意的：折叠会改变字数口径（见 [collapseBlankLines] KDoc）。
     *
     * @param markdown 正文 Markdown 字符串
     * @return 去除所有 Markdown 语法后的纯文本（空白由调用方去除）
     */
    fun markdownToPlainText(markdown: String): String = MarkdownParser.toPlainText(markdown)

    /**
     * 摘要渲染专用：**删除所有空行**，只保留有内容的行（v2026-09-23 新增）
     *
     * **为什么需要**：BlockNote 里每按一次回车就生成一个独立**段落块**，导出 markdown
     * 时官方以 `\n\n` 连接块。于是在编辑页里视觉上紧贴的两行文字，其 markdown 实际是
     * `行1\n\n行2`。而 Compose 的 `Text` **会把每一个 `\n` 都渲染成一整行**（不像
     * HTML 会折叠空白），于是一个段间空行就变成一整行空白 ⇒ **行距翻倍**。
     *
     * 真机症状（2026-09-23 用户报）：编辑页「链接1:」…「链接9:」共 9 个独立段落、
     * 视觉无空隙；首页时间线摘要却每行之间空一格，且 `maxLines = 6` 只够显示 3 条
     * （9 行文字 + 8 个空行 = 17 行），第 4 条位置正好出现 `…` 省略号。
     * 另：「测试分割线」卡片里分割线被剥离后，其上下两行之间也多出一格空隙。
     *
     * **口径（用户确认）：摘要里不出现任何空行** —— 既包含块间自动产生的空行，
     * 也包含用户在编辑页**手动敲出**的空行，一律**整行删除**、不做保留。
     *
     * **为何不在 [MarkdownParser.toPlainText] / [markdownToPlainText] 里折叠**：
     * 那是**贯穿全项目的纯文本口径**，同时喂给：
     * - `countInspirationContentChars`（字数统计，`content` 里每个 `\n` 都计入长度）；
     * - `buildInspirationPlainText`（复制到剪贴板，段落分隔是有意义的结构）；
     * - `repairInspirationPlainText` / 搜索等数据清洗路径。
     * 在底层折叠会**改变这些口径**，并要求重新清洗全部存量数据。折叠只对
     * 「单块 `Text` 摘要渲染」有意义，故独立成方法、只由摘要渲染点显式调用。
     *
     * **同时去掉首尾空白行**：`filter` 只保留有内容的行，首尾空行自然一并消失
     * （尾部换行在 `maxLines` 截断场景下会白占一行配额）。
     *
     * ⚠️ **纯空白行也算空行**（v2026-09-23 补）：`toPlainText` 里有多处移除规则用
     * **空格占位**（图片 / 标签 token / 分割线等），被移除元素若**独占一行**，剥离后
     * 该行就变成"只含一个空格的行"——它**不是空行**，若只按 `\n{2,}` 折叠会漏掉它
     * （`\n \n` 不匹配），于是上下两行之间仍会多出一整行空白。真机症状：
     * 「测试分割线」卡片里分割线上下两行之间出现一格空隙。故此处按**整行**判定，
     * 行内仅含空白字符即整行丢弃。
     *
     * ⚠️ 只删除**空行**（含纯空白行），不触碰有内容的行——列表项内部、
     * 行内软换行、以及行首缩进都不受影响。
     *
     * @param plainText 已是纯文本的内容（通常来自 [markdownToPlainText]）
     * @return 仅保留有内容行的文本（行序不变，无任何空行）
     */
    fun collapseBlankLines(plainText: String): String {
        // 先归一换行符，避免 \r\n / \r 残留导致按行切分错位
        val normalized = plainText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        // 逐行过滤：**空行（含纯空白行）整行丢弃**，只保留有内容的行。
        // 口径是"删除所有空行"而非"把连续空行压成一个"——目标是让摘要里每一行
        // 都是真实内容（Compose 的 Text 每个 \n 都占一整行）。
        // 用 filter 而非正则，让"整行判定"的语义直白可读、便于后续维护。
        return normalized
            .split("\n")
            .filter { it.isNotBlank() }
            .joinToString("\n")
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
