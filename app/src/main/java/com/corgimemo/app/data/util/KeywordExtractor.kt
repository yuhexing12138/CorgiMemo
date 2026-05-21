package com.corgimemo.app.data.util

object KeywordExtractor {

    private val stopWords = setOf(
        "明天", "今天", "后天", "昨天", "前天", "大前天", "大后天",
        "早上", "上午", "中午", "下午", "晚上", "傍晚", "凌晨", "深夜", "半夜",
        "周一", "周二", "周三", "周四", "周五", "周六", "周日", "星期日",
        "星期", "周末", "工作日",
        "的", "了", "着", "啊", "吧", "吗", "呢", "哦", "呀", "嘛",
        "很", "非常", "太", "真", "就", "都", "还", "也", "又", "再",
        "请", "帮", "要", "去", "做", "来", "把", "给", "让", "叫",
        "我", "你", "他", "她", "它", "我们", "你们", "他们",
        "是", "不", "没", "有", "在", "和", "的话", "一下", "一个",
        "可以", "需要", "必须", "应该", "可能", "大概", "差不多",
        "一会", "一会儿", "很快", "马上", "立刻", "赶紧",
        "几点", "什么时候", "时间", "日期", "几号", "周几"
    )

    fun extractKeywords(text: String, minLength: Int = 2, maxLength: Int = 4): List<String> {
        if (text.isBlank()) return emptyList()

        val cleanText = text.trim()
        if (cleanText.length < minLength) return listOf(cleanText)

        val candidates = mutableSetOf<String>()

        if (cleanText.length <= maxLength) {
            if (!containsStopWord(cleanText)) {
                candidates.add(cleanText)
            }
            return candidates.toList()
        }

        for (i in minLength..maxLength) {
            var j = 0
            while (j + i <= cleanText.length) {
                val ngram = cleanText.substring(j, j + i)
                if (ngram.isNotBlank() && !containsStopWord(ngram)) {
                    candidates.add(ngram)
                }
                j++
            }
        }

        if (candidates.isEmpty() && cleanText.length <= maxLength) {
            candidates.add(cleanText)
        }

        return candidates.toList().sortedByDescending { it.length }
    }

    fun containsStopWord(text: String): Boolean {
        return stopWords.any { text.contains(it) }
    }
}
