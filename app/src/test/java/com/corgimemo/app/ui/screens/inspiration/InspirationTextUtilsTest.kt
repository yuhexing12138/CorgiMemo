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
}
