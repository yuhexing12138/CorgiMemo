// 最近删除页面的 UI 状态、列表分组、列表项数据类
package com.corgimemo.app.ui.screens.recentlydeleted

/**
 * 最近删除页面的 UI 状态（不可变）
 *
 * 通过 ViewModel 的 StateFlow 暴露给 UI 层，
 * UI 层只读取 + 调用 ViewModel 写方法。
 *
 * @property groups 按时间分组的列表（已排序：组按时间倒序，组内按 deletedAt 倒序）
 * @property totalCount 总条数（用于空态判断）
 * @property showClearAllDialog 是否显示"清空全部"二次确认弹窗
 * @property isLoading 是否处于初始加载状态
 */
data class RecentlyDeletedUiState(
    val groups: List<DeletedTodoGroup> = emptyList(),
    val totalCount: Int = 0,
    val showClearAllDialog: Boolean = false,
    val isLoading: Boolean = true
)

/**
 * 一个时间分组下的所有最近删除项
 *
 * @property kind 分组类型
 * @property title 分组标题（"今天"/"昨天"/"本周"/"更早"）
 * @property items 分组内待办（按 deletedAt 倒序）
 */
data class DeletedTodoGroup(
    val kind: DeletedTodoGroupKind,
    val title: String,
    val items: List<DeletedTodoListItem>
)

/**
 * 列表项的 UI 投影（不直接暴露 DeletedTodo Entity）
 *
 * 包含渲染所需的全部字段，避免 UI 层访问数据库概念。
 */
data class DeletedTodoListItem(
    val id: Long,
    val title: String,
    val originalCategoryId: Long?,
    val categoryName: String?,
    val deletedAt: Long,
    val relativeTime: String
)
