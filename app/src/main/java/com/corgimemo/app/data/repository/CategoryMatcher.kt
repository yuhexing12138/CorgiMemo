package com.corgimemo.app.data.repository

import com.corgimemo.app.data.model.CategoryKeyword
import com.corgimemo.app.data.model.MatchType
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryMatcher @Inject constructor(
    private val categoryKeywordRepository: CategoryKeywordRepository
) {

    data class MatchResult(
        val categoryType: Int,
        val score: Int,
        val matchedKeywords: List<String>
    )

    data class Recommendation(
        val categoryType: Int,
        val score: Int,
        val matchedKeywords: List<String>
    )

    fun recommendCategory(title: String, content: String? = null): Recommendation? {
        val text = if (content.isNullOrBlank()) {
            title.lowercase(Locale.getDefault())
        } else {
            "${title.lowercase(Locale.getDefault())} ${content.lowercase(Locale.getDefault())}"
        }

        if (text.isBlank()) return null

        val allKeywords = categoryKeywordRepository.getAllKeywordsSync()

        if (allKeywords.isEmpty()) return null

        val categoryScores = mutableMapOf<Int, MatchResult>()
        val textNormalized = text.replace("\\s+".toRegex(), "")

        allKeywords.forEach { keyword ->
            val keywordText = keyword.keyword.lowercase(Locale.getDefault())

            val matched = when (keyword.matchType) {
                MatchType.EXACT -> textNormalized.contains(keywordText)
                MatchType.FUZZY -> text.contains(keywordText)
            }

            if (matched) {
                val current = categoryScores.getOrDefault(
                    keyword.categoryType,
                    MatchResult(keyword.categoryType, 0, emptyList())
                )
                categoryScores[keyword.categoryType] = current.copy(
                    score = current.score + keyword.weight,
                    matchedKeywords = current.matchedKeywords + keyword.keyword
                )
            }
        }

        if (categoryScores.isEmpty()) return null

        val sortedResults = categoryScores.values
            .sortedByDescending { it.score }

        val topScore = sortedResults[0].score
        val topResults = sortedResults.filter { it.score == topScore }

        if (topResults.size > 1) return null

        val best = topResults[0]
        return Recommendation(
            categoryType = best.categoryType,
            score = best.score,
            matchedKeywords = best.matchedKeywords
        )
    }
}
