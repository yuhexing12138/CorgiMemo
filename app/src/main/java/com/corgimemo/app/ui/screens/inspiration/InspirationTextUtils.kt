// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationTextUtils.kt
package com.corgimemo.app.ui.screens.inspiration

import com.corgimemo.app.data.model.Inspiration
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
