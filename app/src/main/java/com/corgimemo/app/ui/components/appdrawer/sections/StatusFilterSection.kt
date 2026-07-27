package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.viewmodel.StatusFilter

/**
 * 状态过滤分区（v2026-07-27 新增）
 *
 * 显示在侧滑栏 TODO Tab 下"状态管理"激活时。提供 6 个状态过滤项：
 * **全部状态 / 置顶 / 待完成 / 已完成 / 已过期 / 重复提醒**。
 *
 * **复用策略**：
 * - 6 个状态项全部复用 [CategoryItem]（同包 `internal` 可见性）
 *
 * **v2026-07-27 调整**：删除内部"状态过滤"标题 + 橙线，避免与上方
 * [DrawerSectionTab] Tab 切换器的"状态管理"文字重复。
 * 顶部 8dp 间距用 LazyColumn.padding(top) 替代原来的 Spacer。
 *
 * **过滤行为**：
 * 状态过滤 + 分组过滤（来自 CategoryGroupSection）是 **AND 组合关系**，
 * 可同时生效。如"已过期 + 未分类"= 所有过期未分类待办。
 *
 * **架构角色**：
 * - 本函数是 sections 包的内部实现（`internal` 可见性）
 * - 由 [AppDrawerContentImpl] 在 DrawerSection.STATUS 分支调用
 * - 调用方应使用 `com.corgimemo.app.ui.components.AppDrawerContent`（薄壳层）
 *
 * @param currentFilter 当前选中的状态过滤项
 * @param totalCount "全部状态"项显示的总数（来自 HomeViewModel.totalTodoCount）
 * @param pinnedCount 置顶待办数
 * @param pendingCount 待完成待办数
 * @param completedCount 已完成待办数
 * @param overdueCount 已过期待办数
 * @param repeatReminderCount 设置了重复提醒的待办数
 * @param onFilterClick 状态项点击回调（参数为新选中的 StatusFilter）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun StatusFilterSection(
    currentFilter: StatusFilter,
    totalCount: Int,
    pinnedCount: Int,
    pendingCount: Int,
    completedCount: Int,
    overdueCount: Int,
    repeatReminderCount: Int,
    onFilterClick: (StatusFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // 6 个状态项（复用 CategoryItem，统一视觉；顶部 8dp 间距替代原"标题 + 橙线 + Spacer"）
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // 1) 全部状态（默认选中）
            item {
                CategoryItem(
                    icon = STATUS_ICON_ALL,
                    name = "全部状态",
                    count = totalCount,
                    isSelected = currentFilter == StatusFilter.ALL,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.ALL) }
                )
            }
            // 2) 置顶
            item {
                CategoryItem(
                    icon = STATUS_ICON_PINNED,
                    name = "置顶",
                    count = pinnedCount,
                    isSelected = currentFilter == StatusFilter.PINNED,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.PINNED) }
                )
            }
            // 3) 待完成
            item {
                CategoryItem(
                    icon = STATUS_ICON_PENDING,
                    name = "待完成",
                    count = pendingCount,
                    isSelected = currentFilter == StatusFilter.PENDING,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.PENDING) }
                )
            }
            // 4) 已完成
            item {
                CategoryItem(
                    icon = STATUS_ICON_COMPLETED,
                    name = "已完成",
                    count = completedCount,
                    isSelected = currentFilter == StatusFilter.COMPLETED,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.COMPLETED) }
                )
            }
            // 5) 已过期
            item {
                CategoryItem(
                    icon = STATUS_ICON_OVERDUE,
                    name = "已过期",
                    count = overdueCount,
                    isSelected = currentFilter == StatusFilter.OVERDUE,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.OVERDUE) }
                )
            }
            // 6) 重复提醒
            item {
                CategoryItem(
                    icon = STATUS_ICON_REPEAT,
                    name = "重复提醒",
                    count = repeatReminderCount,
                    isSelected = currentFilter == StatusFilter.REPEAT_REMINDER,
                    showMenu = false,
                    onClick = { onFilterClick(StatusFilter.REPEAT_REMINDER) }
                )
            }
        }
    }
}

// ==================== 共享图标常量（internal） ====================
// v2026-07-27 新增：6 个状态项的前置图标。
// 当前用 emoji 保持视觉轻量，后续可改为 Material Icons filled。

/** "全部状态" 图标 */
internal const val STATUS_ICON_ALL = "📋"

/** "置顶" 图标 */
internal const val STATUS_ICON_PINNED = "📌"

/** "待完成" 图标 */
internal const val STATUS_ICON_PENDING = "⏳"

/** "已完成" 图标 */
internal const val STATUS_ICON_COMPLETED = "✅"

/** "已过期" 图标 */
internal const val STATUS_ICON_OVERDUE = "⏰"

/** "重复提醒" 图标 */
internal const val STATUS_ICON_REPEAT = "🔁"
