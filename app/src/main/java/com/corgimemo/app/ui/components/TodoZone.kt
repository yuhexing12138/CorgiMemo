package com.corgimemo.app.ui.components

import com.corgimemo.app.data.model.TodoItem

/**
 * 待办区域枚举
 *
 * 作为 (isPinned, status) 笛卡尔积的语义化封装，
 * 把"置顶边界"从 divider 抽象出来，无论 UI 是否有 divider，都能精准判定跨区。
 *
 * - [PINNED_PENDING]: isPinned=true,  status=0（置顶待完成）
 * - [PENDING]:        isPinned=false, status=0（普通待完成）
 * - [PINNED_COMPLETED]: isPinned=true,  status=1（置顶已完成）
 * - [COMPLETED]:      isPinned=false, status=1（普通已完成）
 */
enum class TodoZone {
    PINNED_PENDING,
    PENDING,
    PINNED_COMPLETED,
    COMPLETED
}

/**
 * 由 TodoItem 的 isPinned + status 派生 zone
 *
 * 单一数据源：zone 不持久化到数据库，每次调用即时计算
 */
fun TodoItem.zone(): TodoZone = when {
    isPinned && status == 0 -> TodoZone.PINNED_PENDING
    !isPinned && status == 0 -> TodoZone.PENDING
    isPinned && status == 1 -> TodoZone.PINNED_COMPLETED
    else -> TodoZone.COMPLETED
}
