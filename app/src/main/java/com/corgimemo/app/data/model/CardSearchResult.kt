package com.corgimemo.app.data.model

/**
 * 卡片搜索结果数据类
 * 用于关联选择器中展示搜索到的卡片信息
 * 跨三种卡片类型（待办/灵感/日期）统一返回
 */
data class CardSearchResult(
    /** 卡片类型: "todo" | "inspiration" | "date" */
    val cardType: String,

    /** 卡片ID */
    val cardId: Long,

    /** 卡片标题 */
    val title: String,

    /** 分类名称（待办有分类，灵感/日期可为null） */
    val categoryName: String? = null,

    /** 分类图标emoji */
    val categoryIcon: String? = null
) {
    /** 获取类型图标emoji */
    val typeIcon: String
        get() = when (cardType) {
            "todo" -> "📝"
            "inspiration" -> "💡"
            "date" -> "📅"
            else -> "📎"
        }

    /** 获取类型中文名 */
    val typeName: String
        get() = when (cardType) {
            "todo" -> "待办"
            "inspiration" -> "灵感"
            "date" -> "日期"
            else -> "未知"
        }
}
