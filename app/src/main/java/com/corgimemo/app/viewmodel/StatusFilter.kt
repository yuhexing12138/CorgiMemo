package com.corgimemo.app.viewmodel

/**
 * 状态过滤器（v2026-07-27 新增）
 *
 * 侧滑栏"状态管理"分区专用。用于在 [HomeViewModel.filteredTodos] 中应用状态维度过滤。
 * 与"分组管理"中的 `_selectedCategoryId` 是**组合关系**（AND），可同时生效。
 *
 * **设计决策**（参见 .trae/documents/侧滑栏添加状态管理切换功能实施计划.md）：
 * - 用户可同时选中一个状态（如"已过期"）和一个分类（如"未分类"），列表显示所有过期的未分类待办
 * - 默认值是 [ALL]，表示不过滤（与"全部状态"项对应）
 *
 * **数据源**（HomeViewModel 中对应计数）：
 * - ALL → `totalTodoCount`（所有 todo 数）
 * - PINNED → `pinnedCount`（isPinned=true）
 * - PENDING → `pendingCount`（!isPinned && status=0）
 * - COMPLETED → `completedCount`（status=1）
 * - OVERDUE → `overdueCount`（status=0 且 reminderTime/dueDate 任一过期，见 HomeViewModel.isOverdueTodo）
 * - REPEAT_REMINDER → `repeatReminderCount`（repeatType != 0）
 */
enum class StatusFilter {
    /** 全部（默认）— 不过滤，显示所有待办 */
    ALL,

    /** 置顶（isPinned=true） */
    PINNED,

    /** 待完成（!isPinned && status=0） */
    PENDING,

    /** 已完成（status=1） */
    COMPLETED,

    /**
     * 已过期（status=0 且 reminderTime/dueDate 任一过期）
     *
     * v2026-07-29 修正：原仅判断 dueDate < now，会导致"设了提醒但未设截止"的待办永不过期。
     * 现改为 HomeViewModel.isOverdueTodo 统一判断，与 overdueCount 计数保持一致。
     */
    OVERDUE,

    /** 重复提醒（repeatType != 0，即设置了重复提醒的待办） */
    REPEAT_REMINDER
}
