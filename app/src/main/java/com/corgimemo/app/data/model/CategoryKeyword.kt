package com.corgimemo.app.data.model

import com.corgimemo.app.data.local.db.CategoryKeywordEntity

/**
 * 匹配类型
 *
 * @property weight 匹配权重
 */
enum class MatchType(val weight: Int) {
    EXACT(10),
    FUZZY(3)
}

/**
 * 分类关键词
 * 用于智能分类推荐
 *
 * @property id 关键词 ID
 * @property keyword 关键词文本
 * @property categoryType 关联分类类型（CategoryType）
 * @property matchType 匹配类型
 * @property isUserDefined 是否用户自定义
 */
data class CategoryKeyword(
    val id: Long = 0,
    val keyword: String,
    val categoryType: Int,
    val matchType: MatchType,
    val isUserDefined: Boolean
) {
    /**
     * 关键词权重
     * 用户自定义关键词权重是预设关键词的 2 倍
     */
    val weight: Int
        get() = if (isUserDefined) matchType.weight * 2 else matchType.weight
}

fun CategoryKeywordEntity.toDomainModel(): CategoryKeyword {
    return CategoryKeyword(
        id = id,
        keyword = keyword,
        categoryType = categoryType,
        matchType = if (matchType == 0) MatchType.EXACT else MatchType.FUZZY,
        isUserDefined = isUserDefined
    )
}
