// app/src/test/java/com/corgimemo/app/ui/screens/inspiration/InspirationTextUtilsTest.kt
package com.corgimemo.app.ui.screens.inspiration

import com.corgimemo.app.data.model.Inspiration
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 灵感展示页文本工具类单元测试
 * 覆盖：纯文本拼接、字数统计、标签 JSON 解析三个核心方法
 */
class InspirationTextUtilsTest {

    /**
     * 测试：完整灵感（标题+日期+正文+标签）应正确拼接并换行分隔
     */
    @Test
    fun `buildInspirationPlainText 拼接四部分并换行`() {
        // 给定：完整灵感
        val inspiration = Inspiration(
            id = 1,
            title = "就这样，《简记事App》诞生了！",
            content = "今天，持续3天的高密度多方方向的努力。",
            tags = "[\"简记事\",\"闲笔\"]",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val formattedDate = "2019.07.29 09:00"

        // 当：调用工具函数
        val result = InspirationTextUtils.buildInspirationPlainText(inspiration, formattedDate)

        // 那么：四部分用换行分隔
        val expected = """
            就这样，《简记事App》诞生了！
            2019.07.29 09:00

            今天，持续3天的高密度多方方向的努力。

            #简记事 #闲笔
        """.trimIndent()
        assertEquals(expected, result)
    }

    /**
     * 测试：无标签时不应显示空行，结尾无多余换行
     */
    @Test
    fun `buildInspirationPlainText 无标签时省略标签行`() {
        // 给定：无标签
        val inspiration = Inspiration(
            id = 1,
            title = "无标签标题",
            content = "正文内容",
            tags = "",
            createdAt = 0,
            updatedAt = 0
        )

        // 当
        val result = InspirationTextUtils.buildInspirationPlainText(inspiration, "2026.07.10 10:00")

        // 那么：结尾无空行
        val expected = """
            无标签标题
            2026.07.10 10:00

            正文内容
        """.trimIndent()
        assertEquals(expected, result)
    }

    /**
     * 测试：字数统计应合并标题+正文+标签并去除所有空白字符
     */
    @Test
    fun `countInspirationChars 合并标题正文标签并去除空白`() {
        // 给定
        val inspiration = Inspiration(
            id = 1,
            title = "Hello World",   // 11 字符（去空白后 10）
            content = "你好世界",       // 4 字符
            tags = "[\"标签1\",\"标签2\"]",
            createdAt = 0,
            updatedAt = 0
        )

        // 当
        val count = InspirationTextUtils.countInspirationChars(inspiration)

        // 那么：标题 10 + 正文 4 + 标签 4 = 18
        assertEquals(18, count)
    }

    /**
     * 测试：标签 JSON 数组字符串应正确解析为 List<String>
     */
    @Test
    fun `parseTags 解析 JSON 数组字符串`() {
        // 给定
        val jsonArray = "[\"产品\",\"设计\",\"开发\"]"

        // 当
        val result = InspirationTextUtils.parseTags(jsonArray)

        // 那么
        assertEquals(listOf("产品", "设计", "开发"), result)
    }

    /**
     * 测试：空字符串应返回空列表
     */
    @Test
    fun `parseTags 空字符串返回空列表`() {
        assertEquals(emptyList<String>(), InspirationTextUtils.parseTags(""))
    }

    // ==================== 摘要纯文本口径（v2026-09-23） ====================
    //
    // 背景：首页时间线 / 列表卡片 / 日历弹窗三处曾各自用私有 `removeHtmlTags()` 处理
    // `Inspiration.content`——它只剥 HTML 标签、**不认 markdown**，导致正文里的分割线
    // `---` / `***` 原样漏进摘要（用户报"首页列表不需要渲染分割线"）。
    // 三处已统一改走 [InspirationTextUtils.markdownToPlainText]。
    // 以下用例钉死"摘要中不出现分割线"这一契约。

    /**
     * 测试：分割线（三种样式 + 三种符号）必须被剥离，摘要里不得出现
     */
    @Test
    fun `markdownToPlainText 剥离分割线（--- 与 *** 与样式后缀）`() {
        val cases = listOf(
            "上\n\n---\n\n下",            // 实线（App 序列化载体）
            "上\n\n--- dashed\n\n下",     // 虚线
            "上\n\n--- wavy\n\n下",       // 波浪线
            "上\n\n***\n\n下",            // 官方导出形态（历史数据）
            "上\n\n___\n\n下",            // 下划线变体
            "上\n@@@@CORGI_DIVIDER_wavy@@@\n下"  // 内部 token（WebView 中间态）
        )
        for (md in cases) {
            val out = InspirationTextUtils.markdownToPlainText(md)
            // 三条断言：不含连字符分割线、不含星号分割线、不含内部 token
            assertEquals("用例应剥净连字符分割线: $md", false, out.contains("---"))
            assertEquals("用例应剥净星号分割线: $md", false, out.contains("***"))
            assertEquals("用例应剥净内部 token: $md", false, out.contains("@@@CORGI"))
            // 上下正文必须保留（不能连内容一起删）
            assertEquals("正文应保留: $md", true, out.contains("上"))
            assertEquals("正文应保留: $md", true, out.contains("下"))
        }
    }

    /**
     * 测试：幂等性——对已是纯文本的 `content` 再剥一次不应改变结果
     *
     * 这是渲染端"无条件再剥一道"成立的依据：正常数据（保存时已按同口径生成）
     * 经过本方法后内容不变，故渲染端兜底不会破坏内容。
     */
    @Test
    fun `markdownToPlainText 对纯文本幂等`() {
        val plain = "今天天气很好，出门散步。"
        assertEquals(plain, InspirationTextUtils.markdownToPlainText(plain))
    }

    /**
     * 测试：行内粗体等 markdown 语法不受分割线规则误伤
     */
    @Test
    fun `markdownToPlainText 行内粗体不被当分割线剥掉`() {
        val out = InspirationTextUtils.markdownToPlainText("这是**重点**内容")
        assertEquals(true, out.contains("重点"))
        assertEquals(false, out.contains("**"))
    }

    // ==================== 摘要空行折叠（v2026-09-23） ====================
    //
    // 背景：BlockNote 里每按一次回车生成一个独立段落块，导出 markdown 时块间以 `\n\n`
    // 连接。Compose 的 `Text` **把每个 `\n` 都画成一整行**（不像 HTML 会折叠空白），
    // 于是一个段间空行就变成一整行空白 ⇒ 行距翻倍、`maxLines` 配额被空行吃掉一半。
    // 真机症状：编辑页「链接1:」…「链接9:」视觉紧贴；首页时间线摘要却每行之间空一格，
    // 且只显示 3 条就出现 `…`。
    // 口径（用户确认）：**摘要里不出现任何空行**，含用户手动敲出的空行，一律整行删除。
    // 以下用例钉死该契约。

    /**
     * 测试：块间空行（`\n\n`）必须删除 —— 这是真机「链接1~9」故障的复现
     */
    @Test
    fun `collapseBlankLines 删除块间空行`() {
        // 给定：BlockNote 导出的 9 个段落（块间 \n\n）
        val md = (1..9).joinToString("\n\n") { "链接$it: https://example$it.com" }
        // 当：走完整的摘要渲染口径
        val out = InspirationTextUtils.collapseBlankLines(
            InspirationTextUtils.markdownToPlainText(md)
        )
        // 那么：9 行紧贴、不含任何空行，且 9 条全部保留
        assertEquals(false, out.contains("\n\n"))
        assertEquals(9, out.split("\n").size)
        assertEquals(true, out.startsWith("链接1:"))
        assertEquals(true, out.endsWith("链接9: https://example9.com"))
    }

    /**
     * 测试：用户**手动敲出**的连续空行同样删除（摘要不保留任何空行）
     */
    @Test
    fun `collapseBlankLines 删除手动空行`() {
        assertEquals("上\n下", InspirationTextUtils.collapseBlankLines("上\n\n\n\n下"))
    }

    /**
     * 测试：单换行不受影响（列表项 / 行内软换行必须保留为独立行）
     */
    @Test
    fun `collapseBlankLines 不触碰单换行`() {
        val out = InspirationTextUtils.collapseBlankLines("第一行\n第二行\n第三行")
        assertEquals("第一行\n第二行\n第三行", out)
    }

    /**
     * 测试：首尾空白行被去除 —— 避免摘要顶部/底部凭空多占一行
     * （尾部换行在 `maxLines` 截断场景下同样会白吃一行配额）
     */
    @Test
    fun `collapseBlankLines 去除首尾空白行`() {
        assertEquals("正文", InspirationTextUtils.collapseBlankLines("\n\n正文\n\n"))
        assertEquals("", InspirationTextUtils.collapseBlankLines("\n\n\n"))
    }

    /**
     * 测试：CRLF / CR 换行归一后一并删除（防历史数据里的 Windows 换行漏网）
     */
    @Test
    fun `collapseBlankLines 归一 CRLF 后删除`() {
        assertEquals("上\n下", InspirationTextUtils.collapseBlankLines("上\r\n\r\n下"))
    }

    /**
     * 测试：**纯空白行也要删**（不止空行）
     *
     * `toPlainText` 里有多处移除规则用**空格占位**（图片 / 标签 token 等），被移除元素
     * 若独占一行，该行会变成"只含一个空格的行"——它**不是空行**，若只按 `\n{2,}`
     * 折叠会漏掉它。本用例守住"整行判定"这一口径。
     */
    @Test
    fun `collapseBlankLines 删除纯空白行`() {
        assertEquals("上\n下", InspirationTextUtils.collapseBlankLines("上\n \n\t\n下"))
    }

    /**
     * 测试：**分割线上下不留空隙**（真机「测试分割线」卡片故障的复现）
     *
     * 分割线剥离后若用空格占位，会留下"只含一个空格的行" ⇒ 上下两行之间多出一整行。
     * 六种载体形态（实线/虚线/波浪线/星号/下划线/带缩进）都必须收敛为 `上\n下`。
     */
    @Test
    fun `markdownToPlainText 分割线上下不留空行`() {
        val cases = listOf(
            "上\n\n---\n\n下",              // 实线（App 序列化载体）
            "上\n\n--- dashed\n\n下",       // 虚线
            "上\n\n--- wavy\n\n下",         // 波浪线
            "上\n\n***\n\n下",              // 官方导出形态（历史数据）
            "上\n\n___\n\n下",              // 下划线变体
            "上\n\n  ---  \n\n下"           // 带缩进与行尾空格
        )
        for (md in cases) {
            val out = InspirationTextUtils.collapseBlankLines(
                InspirationTextUtils.markdownToPlainText(md)
            )
            assertEquals("分割线上下不得有空行: $md", "上\n下", out)
        }
    }

    /**
     * 测试：**多条分割线**同样不残留空行
     */
    @Test
    fun `markdownToPlainText 多条分割线不留空行`() {
        val out = InspirationTextUtils.collapseBlankLines(
            InspirationTextUtils.markdownToPlainText("上\n\n---\n\n中\n\n--- dashed\n\n下")
        )
        assertEquals("上\n中\n下", out)
    }

    /**
     * 测试：**不改动底层纯文本口径** —— `markdownToPlainText` 必须保留段落空行
     *
     * 这是「折叠独立成方法」的依据：字数统计（`countInspirationContentChars` 把每个
     * `\n` 都计入长度）、剪贴板复制、搜索都依赖 `content` 的原始段落结构。
     * 若有人把折叠直接塞进 `toPlainText`，本用例会立刻变红。
     */
    @Test
    fun `markdownToPlainText 保留段落空行不被折叠`() {
        val out = InspirationTextUtils.markdownToPlainText("上\n\n下")
        assertEquals("上\n\n下", out)
    }
}
