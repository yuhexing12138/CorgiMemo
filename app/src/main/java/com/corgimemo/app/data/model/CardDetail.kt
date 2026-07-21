package com.corgimemo.app.data.model

/**
 * 关联卡片详情统一模型（sealed class）
 *
 * 用于 [com.corgimemo.app.ui.components.LinkedCardPreviewDialog] 按类型差异化展示。
 * 由 [com.corgimemo.app.data.repository.CardRelationRepository.loadCardDetail] 异步加载。
 *
 * 三种类型各自承载预览所需的最小字段集合：
 * - [TodoDetail]：截止时间 + 分类名 + 优先级 + 子任务进度
 * - [InspirationDetail]：创建时间 + 内容预览 + 标签列表 + 图片路径列表
 * - [DateDetail]：目标日期 + 备注 + 分类
 *
 * @property cardType 卡片类型常量（"todo" / "inspiration" / "date"）
 * @property cardId 卡片数据库 ID
 * @property title 卡片标题
 */
sealed class CardDetail {
    abstract val cardType: String
    abstract val cardId: Long
    abstract val title: String

    /**
     * 待办详情
     *
     * @property dueDate 截止时间戳（ms），null 表示未设置
     * @property categoryName 分类名，null 表示未分类
     * @property priority 优先级：0=无, 1=低, 2=中, 3=高
     * @property subTaskTotal 子任务总数（0 表示无子任务）
     * @property subTaskCompleted 已完成子任务数
     */
    data class TodoDetail(
        override val cardId: Long,
        override val title: String,
        val dueDate: Long?,
        val categoryName: String?,
        val priority: Int,
        val subTaskTotal: Int,
        val subTaskCompleted: Int
    ) : CardDetail() {
        override val cardType: String = "todo"
    }

    /**
     * 灵感详情
     *
     * @property createdAt 创建时间戳（ms）
     * @property content 纯文本内容（用于预览前几行）
     * @property tags 标签列表（已解析 JSON 数组）
     * @property imagePaths 图片本地路径列表（已解析 JSON 数组，UI 用 Coil 加载显示真实缩略图）
     */
    data class InspirationDetail(
        override val cardId: Long,
        override val title: String,
        val createdAt: Long,
        val content: String,
        val tags: List<String>,
        val imagePaths: List<String>
    ) : CardDetail() {
        override val cardType: String = "inspiration"
    }

    /**
     * 特殊日期详情
     *
     * @property targetDate 目标日期时间戳（ms）
     * @property content 备注内容
     * @property category 分类：BIRTHDAY / ANNIVERSARY / HOLIDAY / OTHER
     */
    data class DateDetail(
        override val cardId: Long,
        override val title: String,
        val targetDate: Long,
        val content: String,
        val category: String
    ) : CardDetail() {
        override val cardType: String = "date"
    }
}
