// 最近删除项的时间分组枚举
package com.corgimemo.app.ui.screens.recentlydeleted

/**
 * 最近删除项的时间分组枚举
 *
 * - [TODAY]: 今天（0:00 - 23:59:59）
 * - [YESTERDAY]: 昨天
 * - [THIS_WEEK]: 本周内（过去 2-6 天）
 * - [EARLIER]: 更早（≥ 7 天前）
 *
 * 由 [TimeClassifier.classifyByTime] 派生，不直接持久化。
 */
enum class DeletedTodoGroupKind { TODAY, YESTERDAY, THIS_WEEK, EARLIER }
