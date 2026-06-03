package com.corgimemo.app.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * 搜索结果高亮工具
 *
 * 在文本中查找搜索关键词的所有出现位置，
 * 对匹配区间应用背景色高亮样式，用于列表中的搜索结果展示。
 *
 * **自适应高亮颜色策略**:
 * - 默认使用暖黄色 (0xFFFFF59D) 作为高亮背景
 * - 当容器背景偏深或偏黄时，自动切换为橙色 (0xFFFFCC80) 确保可见性
 *
 * **使用示例**:
 * ```kotlin
 * val highlighted = buildHighlightedText(
 *     text = "这是一个重要任务",
 *     searchQuery = "重要",
 *     containerBgColor = todo.backgroundColor.let { Color(it) }
 * )
 * Text(text = highlighted)
 * ```
 */
object HighlightUtil {

    /** 默认高亮颜色：暖黄色（适用于浅色/白色背景） */
    val DEFAULT_HIGHLIGHT_COLOR = Color(0xFFFFF59D)

    /** 备用高亮颜色：橙色（适用于深色/黄色背景） */
    val FALLBACK_HIGHLIGHT_COLOR = Color(0xFFFFCC80)

    /** 亮度阈值：低于此值的背景色被视为"深色"（需切换高亮色） */
    private const val LUMINANCE_THRESHOLD = 0.6f

    /**
     * 构建带搜索高亮的 AnnotatedString
     *
     * 在原始文本中查找所有关键词出现位置（忽略大小写），
     * 对每个匹配区间应用 SpanStyle 背景色。
     *
     * @param text 原始文本内容
     * @param searchQuery 搜索关键词（为空时返回无样式的 AnnotatedString）
     * @param highlightColor 高亮背景色（默认暖黄色）
     * @param containerBgColor 容器背景色（用于自动选择最佳高亮色，可选）
     * @return 带高亮样式的 AnnotatedString；无匹配时返回原始文本的 AnnotatedString
     */
    fun buildHighlightedText(
        text: String,
        searchQuery: String,
        highlightColor: Color = DEFAULT_HIGHLIGHT_COLOR,
        containerBgColor: Color? = null
    ): AnnotatedString {
        /** 关键词为空时直接返回原始文本（无高亮） */
        if (text.isBlank() || searchQuery.isBlank()) {
            return AnnotatedString(text)
        }

        /**
         * 自适应选择高亮颜色
         *
         * 当容器背景色较深或偏黄时，切换到更醒目的橙色高亮。
         * 这确保了在自定义背景色的待办卡片上高亮仍然清晰可见。
         */
        val effectiveColor = if (containerBgColor != null) {
            selectAdaptiveHighlightColor(containerBgColor)
        } else {
            highlightColor
        }

        /**
         * V2.4 多关键词支持：将搜索查询按空格拆分为多个独立关键词
         *
         * 用户输入 "重要 紧急" → ["重要", "紧急"]
         * 每个关键词独立查找匹配位置，所有匹配区间统一高亮。
         * 空白词（连续空格）自动过滤。
         */
        val keywords = searchQuery
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct() /** 去重：避免同一词重复高亮 */

        /** 无有效关键词时返回原始文本 */
        if (keywords.isEmpty()) {
            return AnnotatedString(text)
        }

        /** 使用 buildAnnotatedString 构建带区段样式的富文本 */
        return buildHighlightedTextWithKeywords(text, keywords, effectiveColor)
    }

    /**
     * 多关键词高亮核心实现
     *
     * 对文本中每个关键词的所有出现位置进行背景色高亮。
     * 使用区间收集 + 排序 + 合并重叠区间的策略确保正确性。
     *
     * @param text 原始文本
     * @param keywords 关键词列表（已去重、非空）
     * @param highlightColor 高亮颜色
     * @return 带多关键词高亮的 AnnotatedString
     */
    private fun buildHighlightedTextWithKeywords(
        text: String,
        keywords: List<String>,
        highlightColor: Color
    ): AnnotatedString {
        /** 收集所有匹配区间（可能来自多个关键词，可能有重叠） */
        val matchRanges = mutableListOf<IntRange>()
        val lowerText = text.lowercase()

        for (keyword in keywords) {
            val lowerKeyword = keyword.lowercase()
            var searchFrom = 0
            while (true) {
                val index = lowerText.indexOf(lowerKeyword, startIndex = searchFrom)
                if (index < 0) break
                matchRanges.add(index until index + lowerKeyword.length)
                searchFrom = index + lowerKeyword.length
            }
        }

        /** 无匹配时返回原始文本 */
        if (matchRanges.isEmpty()) {
            return AnnotatedString(text)
        }

        /**
         * 按起始位置排序并合并重叠/相邻区间
         *
         * 例如：[0-3] 和 [2-5] 合并为 [0-5]
         * 避免同一位置被多次应用 SpanStyle 导致渲染异常
         */
        val mergedRanges = mergeOverlappingRanges(matchRanges)

        /** 构建带高亮的 AnnotatedString */
        return buildAnnotatedString {
            var lastIndex = 0
            for (range in mergedRanges) {
                /** 添加区间前的普通文本 */
                if (range.first > lastIndex) {
                    append(text.substring(lastIndex, range.first))
                }
                /** 添加高亮文本 */
                withStyle(style = SpanStyle(background = highlightColor)) {
                    append(text.substring(range.first, range.last.coerceAtMost(text.length)))
                }
                lastIndex = range.last
            }
            /** 添加最后一段剩余文本 */
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }

    /**
     * 合并重叠或相邻的区间
     *
     * @param ranges 原始区间集合（未排序）
     * @return 合并后的不重叠区间列表（按起始位置排序）
     */
    private fun mergeOverlappingRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()

        /** 按起始位置排序 */
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()

        var current = sorted[0]
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.first <= current.last) {
                /** 重叠或相邻 → 合并为更大的区间 */
                current = current.first..maxOf(current.last, next.last)
            } else {
                /** 不重叠 → 保存当前，开始新区间 */
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    /**
     * 根据容器背景色自适应选择最佳高亮颜色
     *
     * **判断逻辑**:
     * 1. 计算背景色的相对亮度（感知亮度公式）
     * 2. 亮度低于阈值 → 使用橙色（更深背景上更醒目）
     * 3. 亮度高于阈值 → 使用黄色（浅色背景上的标准高亮）
     *
     * @param backgroundColor 容器的 ARGB 颜色
     * @return 最适合该背景的高亮颜色
     */
    fun selectAdaptiveHighlightColor(backgroundColor: Color): Color {
        /** 使用相对亮度公式计算感知亮度 */
        val luminance = calculateLuminance(backgroundColor)

        return if (luminance < LUMINANCE_THRESHOLD) {
            /** 深色背景 → 橙色高亮（更高对比度） */
            FALLBACK_HIGHLIGHT_COLOR
        } else {
            /** 浅色背景 → 黄色高亮（标准搜索高亮风格） */
            DEFAULT_HIGHLIGHT_COLOR
        }
    }

    /**
     * 计算颜色的相对亮度（感知亮度）
     *
     * 使用 sRGB 相对亮度公式的简化版本：
     * L = 0.299*R + 0.587*G + 0.114*B（归一化到 0-1）
     *
     * @param color ARGB 颜色值
     * @return 0.0（纯黑）~ 1.0（纯白）之间的亮度值
     */
    private fun calculateLuminance(color: Color): Float {
        return 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    }

    /**
     * 构建带搜索高亮的 AnnotatedString（Markdown 版本）
     *
     * 先解析 Markdown 为 AnnotatedString（保留原有格式），
     * 再在此基础上叠加搜索高亮背景。
     *
     * 高亮样式不会覆盖原有的粗体/斜体/删除线等字符样式，
     * 而是作为额外的背景层叠加显示。
     *
     * @param markdown Markdown 格式文本
     * @param searchQuery 搜索关键词
     * @param containerBgColor 容器背景色（可选）
     * @return 带 Markdown 格式 + 搜索高亮的 AnnotatedString
     */
    fun buildHighlightedMarkdown(
        markdown: String,
        searchQuery: String,
        containerBgColor: Color? = null
    ): AnnotatedString {
        if (markdown.isBlank() || searchQuery.isBlank()) {
            return com.corgimemo.app.util.MarkdownParser.safeParse(markdown)
        }

        /** 先解析 Markdown 为带格式的 AnnotatedString */
        val parsed = com.corgimemo.app.util.MarkdownParser.safeParse(markdown)

        /** 选择自适应高亮颜色 */
        val highlightColor = if (containerBgColor != null) {
            selectAdaptiveHighlightColor(containerBgColor)
        } else {
            DEFAULT_HIGHLIGHT_COLOR
        }

        /**
         * V2.4 多关键词支持：拆分搜索查询为多个关键词
         */
        val keywords = searchQuery
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (keywords.isEmpty()) return parsed

        /** 在纯文本上查找所有关键词的匹配位置并叠加高亮背景 */
        return buildHighlightedAnnotatedStringMulti(parsed, keywords, highlightColor)
    }

    /**
     * 在已有 AnnotatedString 基础上叠加多关键词搜索高亮
     *
     * 保留原有的所有 SpanStyle（粗体、斜体等），
     * 仅在每个关键词匹配区间添加背景色。
     *
     * @param source 已有样式的 AnnotatedString
     * @param keywords 关键词列表（已去重）
     * @param highlightColor 高亮背景色
     * @return 叠加高亮后的 AnnotatedString
     */
    private fun buildHighlightedAnnotatedStringMulti(
        source: AnnotatedString,
        keywords: List<String>,
        highlightColor: Color
    ): AnnotatedString {
        val text = source.text
        val lowerText = text.lowercase()

        /** 收集所有关键词的所有匹配区间 */
        val matchRanges = mutableListOf<IntRange>()
        for (keyword in keywords) {
            val lowerKeyword = keyword.lowercase()
            var searchFrom = 0
            while (true) {
                val index = lowerText.indexOf(lowerKeyword, startIndex = searchFrom)
                if (index < 0) break
                matchRanges.add(index until index + lowerKeyword.length)
                searchFrom = index + lowerKeyword.length
            }
        }

        if (matchRanges.isEmpty()) return source

        /** 合并重叠区间 */
        val mergedRanges = mergeOverlappingRanges(matchRanges)

        /** 反向遍历避免偏移问题，逐个添加背景 SpanStyle */
        var result = source
        for (range in mergedRanges.reversed()) {
            result = addBackgroundSpan(result, range.first, range.last, highlightColor)
        }

        return result
    }

    /**
     * 在已有 AnnotatedString 基础上叠加搜索高亮
     *
     * 保留原有的所有 SpanStyle（粗体、斜体等），
     * 仅在每个匹配区间添加背景色。
     *
     * @param source 已有样式的 AnnotatedString
     * @param searchQuery 搜索关键词
     * @param highlightColor 高亮背景色
     * @return 叠加高亮后的 AnnotatedString
     */
    private fun buildHighlightedAnnotatedString(
        source: AnnotatedString,
        searchQuery: String,
        highlightColor: Color
    ): AnnotatedString {
        val text = source.text
        val lowerText = text.lowercase()
        val lowerQuery = searchQuery.lowercase()

        /** 收集所有匹配区间 */
        val matchRanges = mutableListOf<IntRange>()
        var searchFrom = 0
        while (true) {
            val index = lowerText.indexOf(lowerQuery, startIndex = searchFrom)
            if (index < 0) break
            matchRanges.add(index until index + lowerQuery.length)
            searchFrom = index + lowerQuery.length
        }

        /** 无匹配时返回原文本 */
        if (matchRanges.isEmpty()) return source

        /** 构建结果：保留原有样式 + 叠加高亮背景 */
        var result = source
        for (range in matchRanges.reversed()) { /** 反向遍历避免偏移问题 */
            result = addBackgroundSpan(result, range.first, range.last, highlightColor)
        }

        return result
    }

    /**
     * 为 AnnotatedString 的指定区间添加背景色 SpanStyle
     *
     * @param source 原始 AnnotatedString
     * @param start 起始索引（含）
     * @param end 结束索引（不含）
     * @param color 背景颜色
     * @return 添加背景色后的新 AnnotatedString
     */
    private fun addBackgroundSpan(
        source: AnnotatedString,
        start: Int,
        end: Int,
        color: Color
    ): AnnotatedString {
        val safeStart = start.coerceIn(0, source.length)
        val safeEnd = end.coerceIn(safeStart, source.length)

        return AnnotatedString(
            text = source.text,
            spanStyles = source.spanStyles +
                    androidx.compose.ui.text.AnnotatedString.Range(
                        SpanStyle(background = color),
                        safeStart,
                        safeEnd
                    ),
            paragraphStyles = source.paragraphStyles
        )
    }

    // ==================== V2.5 逐区间高亮数据结构 ====================

    /**
     * 高亮区间数据（用于逐区间独立动画渲染）
     *
     * 将文本拆分为普通文本和高亮文本交替的区间列表，
     * 每个区间可独立应用透明度动画，实现交错淡入效果。
     *
     * @property text 区间内的文本内容
     * @property isHighlight 是否为高亮区间（true = 需要背景色）
     * @property startIndex 在原始文本中的起始位置（用于计算动画延迟）
     */
    data class HighlightRange(
        val text: String,
        val isHighlight: Boolean,
        val startIndex: Int
    )

    /**
     * 构建高亮区间列表（纯文本版本）
     *
     * 将搜索结果拆分为 [HighlightRange] 列表，
     * 普通文本和高亮文本交替排列，每个区间携带其在原文中的位置信息。
     *
     * **与 buildHighlightedText() 的区别**:
     * - buildHighlightedText 返回单个 AnnotatedString（整体渲染）
     * - buildHighlightRanges 返回拆分后的区间列表（逐个独立渲染+动画）
     *
     * @param text 原始文本内容
     * @param searchQuery 搜索关键词（支持空格分隔多关键词）
     * @param highlightColor 高亮背景色
     * @param containerBgColor 容器背景色（用于自适应颜色选择，可选）
     * @return 拆分后的高亮区间列表；无匹配时返回单个普通文本区间
     */
    fun buildHighlightRanges(
        text: String,
        searchQuery: String,
        highlightColor: Color = DEFAULT_HIGHLIGHT_COLOR,
        containerBgColor: Color? = null
    ): Pair<List<HighlightRange>, Color> {
        /** 选择自适应高亮颜色 */
        val effectiveColor = containerBgColor?.let { selectAdaptiveHighlightColor(it) } ?: highlightColor

        /** 解析多关键词 */
        val keywords = searchQuery.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        /** 无关键词或无匹配时返回原始文本作为单一区间 */
        if (keywords.isEmpty()) {
            return Pair(listOf(HighlightRange(text, false, 0)), effectiveColor)
        }

        /** 收集所有匹配区间并合并重叠 */
        val matchRanges = mutableListOf<IntRange>()
        val lowerText = text.lowercase()
        for (keyword in keywords) {
            val lowerKeyword = keyword.lowercase()
            var searchFrom = 0
            while (true) {
                val index = lowerText.indexOf(lowerKeyword, startIndex = searchFrom)
                if (index < 0) break
                matchRanges.add(index until index + lowerKeyword.length)
                searchFrom = index + lowerKeyword.length
            }
        }

        if (matchRanges.isEmpty()) {
            return Pair(listOf(HighlightRange(text, false, 0)), effectiveColor)
        }

        val mergedRanges = mergeOverlappingRanges(matchRanges)

        /** 将合并后的区间拆分为 HighlightRange 列表（普通+高亮交替） */
        val ranges = mutableListOf<HighlightRange>()
        var lastIndex = 0

        for (range in mergedRanges) {
            /** 添加区间前的普通文本 */
            if (range.first > lastIndex) {
                ranges.add(HighlightRange(
                    text = text.substring(lastIndex, range.first),
                    isHighlight = false,
                    startIndex = lastIndex
                ))
            }
            /** 添加高亮文本区间 */
            ranges.add(HighlightRange(
                text = text.substring(range.first, range.last.coerceAtMost(text.length)),
                isHighlight = true,
                startIndex = range.first
            ))
            lastIndex = range.last
        }
        /** 添加最后一段剩余的普通文本 */
        if (lastIndex < text.length) {
            ranges.add(HighlightRange(
                text = text.substring(lastIndex),
                isHighlight = false,
                startIndex = lastIndex
            ))
        }

        return Pair(ranges, effectiveColor)
    }

    // ==================== V2.6 带样式的搜索高亮（保留Markdown行内格式） ====================

    /**
     * 带样式的搜索高亮区间数据
     *
     * 在 HighlightRange 基础上扩展，携带 Markdown 解析后的 SpanStyle 信息。
     * 用于搜索高亮时保留粗体、斜体、删除线等行内样式。
     *
     * @property text 区间内的文本内容
     * @property isHighlight 是否为搜索高亮区间（true = 需要背景色）
     * @property startIndex 在原始文本中的起始位置（用于计算动画延迟）
     * @property spanStyle Markdown 解析出的行内样式（粗体/斜体/删除线等），null 表示无特殊样式
     */
    data class StyledHighlightRange(
        val text: String,
        val isHighlight: Boolean,
        val startIndex: Int,
        val spanStyle: SpanStyle? = null
    )

    /**
     * 构建带 Markdown 样式的高亮区间列表（V2.6 增强版）
     *
     * 与 buildHighlightedMarkdown() 的区别：
     * - buildHighlightedMarkdown() 返回单个 AnnotatedString（无法逐区间动画）
     * - buildStyledHighlightRanges() 返回拆分后的区间列表（每个区间可独立动画+保留样式）
     *
     * **核心算法**:
     * 1. 使用 MarkdownParser.safeParse() 将 markdown 解析为带样式的 AnnotatedString
     * 2. 在纯文本上执行多关键词匹配，收集所有高亮区间
     * 3. 按「SpanStyle 边界 ∪ 高亮边界」的联合切分点拆分文本
     * 4. 每个区间同时携带：是否高亮 + 对应的 SpanStyle
     *
     * @param markdown Markdown 格式的原始文本
     * @param searchQuery 搜索关键词（支持空格分隔多关键词）
     * @param highlightColor 高亮背景色
     * @param containerBgColor 容器背景色（用于自适应颜色选择，可选）
     * @return Pair(带样式的区间列表, 有效高亮颜色)
     */
    fun buildStyledHighlightRanges(
        markdown: String,
        searchQuery: String,
        highlightColor: Color = DEFAULT_HIGHLIGHT_COLOR,
        containerBgColor: Color? = null
    ): Pair<List<StyledHighlightRange>, Color> {
        /** 选择自适应高亮颜色 */
        val effectiveColor = containerBgColor?.let { selectAdaptiveHighlightColor(it) } ?: highlightColor

        /** 解析多关键词 */
        val keywords = searchQuery.split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        /** 无关键词时返回原始 Markdown 的样式化区间（无高亮） */
        if (keywords.isEmpty()) {
            return parseMarkdownToStyledRanges(markdown, emptySet(), effectiveColor)
        }

        /** Step 1: 解析 Markdown 为带样式的 AnnotatedString */
        val parsed = com.corgimemo.app.util.MarkdownParser.safeParse(markdown)

        /** Step 2: 在纯文本上收集所有高亮匹配区间 */
        val highlightBoundaries = mutableSetOf<Int>()
        val text = parsed.text
        val lowerText = text.lowercase()

        for (keyword in keywords) {
            val lowerKeyword = keyword.lowercase()
            var searchFrom = 0
            while (true) {
                val index = lowerText.indexOf(lowerKeyword, startIndex = searchFrom)
                if (index < 0) break
                highlightBoundaries.add(index)
                highlightBoundaries.add(index + lowerKeyword.length)
                searchFrom = index + lowerKeyword.length
            }
        }

        if (highlightBoundaries.isEmpty()) {
            /** 无匹配：返回原始 Markdown 样式化区间（无高亮标记） */
            return parseMarkdownToStyledRanges(markdown, emptySet(), effectiveColor)
        }

        /** Step 3: 收集 SpanStyle 边界（每个 span 的 start 和 end） */
        val styleBoundaries = mutableSetOf<Int>()
        for (span in parsed.spanStyles) {
            styleBoundaries.add(span.start)
            styleBoundaries.add(span.end)
        }
        /** 添加首尾边界确保覆盖全文 */
        styleBoundaries.add(0)
        styleBoundaries.add(text.length)

        /** Step 4: 合并所有边界并排序（SpanStyle边界 ∪ 高亮边界） */
        val allBoundaries = (styleBoundaries + highlightBoundaries).sorted()

        /** Step 5: 按边界依次切分，构建 StyledHighlightRange 列表 */
        val ranges = mutableListOf<StyledHighlightRange>()

        for (i in 0 until allBoundaries.size - 1) {
            val start = allBoundaries[i]
            val end = allBoundaries[i + 1]
            if (start >= end || start >= text.length) continue

            val segmentText = text.substring(start, end.coerceAtMost(text.length))

            /** 判断此区间是否在高亮范围内 */
            val isHighlight = highlightBoundaries.any { boundary ->
                boundary > start && boundary <= end &&
                        highlightBoundaries.any { it <= start && it < boundary }
            } || run {
                /** 更精确的判断：检查区间的起始位置是否落在某个高亮区间内 */
                var inHighlight = false
                val sortedHighlights = highlightBoundaries.sorted().chunked(2) { pair ->
                    if (pair.size == 2) pair[0] to pair[1] else null
                }
                for (range in sortedHighlights.filterNotNull()) {
                    if (start >= range.first && start < range.second) {
                        inHighlight = true
                        break
                    }
                }
                inHighlight
            }

            /** 获取此区间的 SpanStyle（取该区间内所有 span styles 的合并） */
            val segmentStyle = mergeSpanStylesForRange(parsed, start, end)

            ranges.add(StyledHighlightRange(
                text = segmentText,
                isHighlight = isHighlight,
                startIndex = start,
                spanStyle = segmentStyle.takeIf { it != SpanStyle() }
            ))
        }

        return Pair(ranges, effectiveColor)
    }

    /**
     * 将 Markdown 文本解析为带样式的区间列表（无搜索高亮时的降级路径）
     *
     * @param markdown Markdown 文本
     * @param highlightSet 高亮边界集合（空集合表示无高亮）
     * @param highlightColor 高亮颜色
     * @return 带样式的区间列表
     */
    private fun parseMarkdownToStyledRanges(
        markdown: String,
        highlightSet: Set<Int>,
        highlightColor: Color
    ): Pair<List<StyledHighlightRange>, Color> {
        val parsed = com.corgimemo.app.util.MarkdownParser.safeParse(markdown)
        val text = parsed.text
        if (text.isBlank()) return Pair(emptyList(), highlightColor)

        val ranges = mutableListOf<StyledHighlightRange>()

        /** 收集所有 SpanStyle 边界 */
        val boundaries = mutableSetOf(0, text.length)
        for (span in parsed.spanStyles) {
            boundaries.add(span.start)
            boundaries.add(span.end)
        }

        val sortedBoundaries = boundaries.sorted()
        for (i in 0 until sortedBoundaries.size - 1) {
            val start = sortedBoundaries[i]
            val end = sortedBoundaries[i + 1].coerceAtMost(text.length)
            if (start >= end) continue

            ranges.add(StyledHighlightRange(
                text = text.substring(start, end),
                isHighlight = false,
                startIndex = start,
                spanStyle = mergeSpanStylesForRange(parsed, start, end).takeIf { it != SpanStyle() }
            ))
        }

        return Pair(ranges, highlightColor)
    }

    /**
     * 获取 AnnotatedString 指定区间内合并后的 SpanStyle
     *
     * 当一个区间跨越多个 SpanStyle 时，
     * 取区间起点处最内层的样式作为代表（通常是最具体的样式）。
     *
     * @param source 带 SpanStyle 的 AnnotatedString
     * @param start 区间起始（含）
     * @param end 区间结束（不含）
     * @return 合并后的 SpanStyle（无样式时返回默认 SpanStyle）
     */
    private fun mergeSpanStylesForRange(
        source: AnnotatedString,
        start: Int,
        end: Int
    ): SpanStyle {
        var mergedStyle = SpanStyle()

        for (item in source.spanStyles) {
            /** 仅处理与当前区间有交集的 span */
            if (item.end > start && item.start < end) {
                /** 合并非空属性：fontWeight、fontStyle、textDecoration、color 等 */
                mergedStyle = mergedStyle.merge(item.item)
            }
        }

        return mergedStyle
    }
}
