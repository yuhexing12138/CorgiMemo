package com.corgimemo.app.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextDecoration

/**
 * AnnotatedString 序列化/反序列化工具
 *
 * 将 Compose 的 AnnotatedString（含文本 + SpanStyle 样式信息）
 * 转换为 JSON 字符串以便持久化存储，支持完整的双向转换。
 *
 * **使用场景**:
 * - Undo/Redo 栈跨编辑会话持久化（存储到 DataStore）
 * - 富文本内容备份/恢复
 * - 跨进程传递格式化文本
 *
 * **JSON 格式示例**:
 * ```json
 * {
 *   "text": "Hello **World**",
 *   "spans": [
 *     {"start": 0, "end": 5, "style": {"fontWeight": "bold"}},
 *     {"start": 6, "end": 11, "style": {"fontStyle": "italic"}}
 *   ]
 * }
 * ```
 *
 * **支持的 SpanStyle 属性**:
 * - fontWeight: Bold / Normal
 * - fontStyle: Italic / Normal
 * - textDecoration: LineThrough / Underline / None
 * - color: ARGB 整数值
 */
object AnnotatedStringSerializer {

    /**
     * 将单个 AnnotatedString 序列化为 JSON 字符串
     *
     * @param annotatedString 待序列化的富文本
     * @return JSON 字符串
     */
    fun serialize(annotatedString: AnnotatedString): String {
        val json = org.json.JSONObject()

        /** 存储纯文本内容 */
        json.put("text", annotatedString.text)

        /** 提取并序列化所有 SpanStyle 区间 */
        val spansArray = org.json.JSONArray()
        val spanStyles = annotatedString.spanStyles

        for (range in spanStyles) {
            val spanObj = org.json.JSONObject()
            spanObj.put("start", range.start)
            spanObj.put("end", range.end)

            /** 序列化 SpanStyle 属性 */
            val styleObj = org.json.JSONObject()
            val style = range.item

            // 粗体
            if (style.fontWeight == FontWeight.Bold) {
                styleObj.put("fontWeight", "bold")
            }
            // 斜体
            if (style.fontStyle == FontStyle.Italic) {
                styleObj.put("fontStyle", "italic")
            }
            // 删除线 / 下划线
            style.textDecoration?.let { decoration ->
                val decorations = mutableListOf<String>()
                if (TextDecoration.LineThrough in decoration) {
                    decorations.add("lineThrough")
                }
                if (TextDecoration.Underline in decoration) {
                    decorations.add("underline")
                }
                if (decorations.isNotEmpty()) {
                    styleObj.put("textDecoration", org.json.JSONArray(decorations))
                }
            }
            // 颜色（仅非默认颜色时保存，减少数据量）
            if (style.color != Color.Unspecified && style.color != Color.Black) {
                styleObj.put("color", style.color.toArgb())
            }

            spanObj.put("style", styleObj)
            spansArray.put(spanObj)
        }

        json.put("spans", spansArray)
        return json.toString()
    }

    /**
     * 从 JSON 字符串反序列化为 AnnotatedString
     *
     * @param json JSON 字符串（由 serialize() 生成）
     * @return 还原的 AnnotatedString；JSON 格式异常时返回纯文本版本
     */
    fun deserialize(json: String): AnnotatedString {
        return try {
            val obj = org.json.JSONObject(json)
            val text = obj.getString("text")

            /** 无样式区间时直接返回纯文本 */
            if (!obj.has("spans")) {
                return AnnotatedString(text)
            }

            val spansArray = obj.getJSONArray("spans")
            if (spansArray.length() == 0) {
                return AnnotatedString(text)
            }

            /** 构建带样式的 AnnotatedString */
            var result = AnnotatedString(text)

            for (i in 0 until spansArray.length()) {
                val spanObj = spansArray.getJSONObject(i)
                val start = spanObj.getInt("start").coerceIn(0, text.length)
                val end = spanObj.getInt("end").coerceIn(start, text.length)

                if (!spanObj.has("style")) continue
                val styleObj = spanObj.getJSONObject("style")

                /** 解析 SpanStyle 属性 */
                val spanStyle = SpanStyle(
                    fontWeight = if (styleObj.has("fontWeight") && styleObj.getString("fontWeight") == "bold") FontWeight.Bold else null,
                    fontStyle = if (styleObj.has("fontStyle") && styleObj.getString("fontStyle") == "italic") FontStyle.Italic else null,
                    textDecoration = if (styleObj.has("textDecoration")) {
                        val decos = styleObj.getJSONArray("textDecoration")
                        var decoration = TextDecoration.None
                        for (j in 0 until decos.length()) {
                            when (decos.getString(j)) {
                                "lineThrough" -> decoration += TextDecoration.LineThrough
                                "underline" -> decoration += TextDecoration.Underline
                            }
                        }
                        if (decoration == TextDecoration.None) null else decoration
                    } else null,
                    color = if (styleObj.has("color")) Color(styleObj.getInt("color")) else Color.Unspecified
                )

                /** 应用样式到指定区间 */
                result = AnnotatedString(
                    text = result.text,
                    spanStyles = result.spanStyles + androidx.compose.ui.text.AnnotatedString.Range(spanStyle, start, end),
                    paragraphStyles = result.paragraphStyles
                )
            }

            result
        } catch (e: Exception) {
            android.util.Log.w("AnnotatedStringSerializer", "反序列化异常，回退纯文本", e)
            /** 异常时尝试提取纯文本字段 */
            try {
                AnnotatedString(org.json.JSONObject(json).getString("text"))
            } catch (e2: Exception) {
                AnnotatedString("")
            }
        }
    }

    /**
     * 将 List<AnnotatedString> 批量序列化为 JSON 数组字符串
     *
     * 用于 Undo/Redo 栈的整体持久化。
     *
     * @param list AnnotatedString 列表
     * @return JSON 数组字符串
     */
    fun serializeList(list: List<AnnotatedString>): String {
        val array = org.json.JSONArray()
        for (item in list) {
            array.put(serialize(item))
        }
        return array.toString()
    }

    /**
     * 从 JSON 数组字符串反序列化为 List<AnnotatedString>
     *
     * @param json JSON 数组字符串（由 serializeList() 生成）
     * @return 还原的 AnnotatedString 列表；异常时返回空列表
     */
    fun deserializeList(json: String): List<AnnotatedString> {
        return try {
            val array = org.json.JSONArray(json)
            val result = mutableListOf<AnnotatedString>()
            for (i in 0 until array.length()) {
                result.add(deserialize(array.getString(i)))
            }
            result
        } catch (e: Exception) {
            android.util.Log.w("AnnotatedStringSerializer", "反序列化列表异常", e)
            emptyList()
        }
    }

    /**
     * 持久化用最大栈深度（限制 DataStore 写入大小）
     *
     * 比内存中的 MAX_UNDO_DEPTH(50) 更小，
     * 避免单个 DataStore value 过大导致性能问题。
     */
    const val PERSISTENCE_MAX_DEPTH = 20
}
