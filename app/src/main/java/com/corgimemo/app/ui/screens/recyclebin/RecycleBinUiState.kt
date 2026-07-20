package com.corgimemo.app.ui.screens.recyclebin

/**
 * 回收站 Tab 枚举
 */
enum class RecycleBinTab { TODO, INSPIRATION, DATE }

/**
 * 时间分组枚举（待办、灵感、日期共用）
 */
enum class DeletedGroupKind { TODAY, YESTERDAY, THIS_WEEK, EARLIER }

/**
 * 回收站页面 UI 状态
 *
 * @property todoGroups 待办按时间分组列表
 * @property inspirationGroups 灵感按时间分组列表
 * @property dateGroups 日期按时间分组列表
 * @property todoTotalCount 待办总数
 * @property inspirationTotalCount 灵感总数
 * @property dateTotalCount 日期总数
 * @property selectedTab 当前选中的 Tab
 * @property showClearAllDialog 是否显示清空全部确认弹窗
 * @property isLoading 是否处于初始加载状态
 */
data class RecycleBinUiState(
    val todoGroups: List<DeletedTodoGroup> = emptyList(),
    val inspirationGroups: List<DeletedInspirationGroup> = emptyList(),
    val dateGroups: List<DeletedDateGroup> = emptyList(),
    val todoTotalCount: Int = 0,
    val inspirationTotalCount: Int = 0,
    val dateTotalCount: Int = 0,
    val selectedTab: RecycleBinTab = RecycleBinTab.TODO,
    val showClearAllDialog: Boolean = false,
    val isLoading: Boolean = true
)

/** 待办时间分组 */
data class DeletedTodoGroup(
    val kind: DeletedGroupKind,
    val title: String,
    val items: List<DeletedTodoListItem>
)

/** 灵感时间分组 */
data class DeletedInspirationGroup(
    val kind: DeletedGroupKind,
    val title: String,
    val items: List<DeletedInspirationListItem>
)

/** 待办列表项 */
data class DeletedTodoListItem(
    val id: Long,
    val title: String,
    val originalCategoryId: Long?,
    val categoryName: String?,
    val deletedAt: Long,
    val relativeTime: String,
    /**
     * 优先级数值（0=无、1=低、2=中、3=高），用于卡片视觉标识
     *
     * v2026-07-20 新增：与首页 TodoListItem 视觉标识体系保持一致。
     * 默认值 = 0 保证向后兼容（如有任何其他代码构造此数据类不会编译失败）。
     */
    val priority: Int = 0
)

/** 灵感列表项 */
data class DeletedInspirationListItem(
    val id: Long,
    val title: String,
    val tags: List<String>,
    val deletedAt: Long,
    val relativeTime: String
)

/** 日期时间分组 */
data class DeletedDateGroup(
    val kind: DeletedGroupKind,
    val title: String,
    val items: List<DeletedDateListItem>
)

/** 日期列表项 */
data class DeletedDateListItem(
    val id: Long,
    val title: String,
    val category: String,
    val deletedAt: Long,
    val relativeTime: String
)
