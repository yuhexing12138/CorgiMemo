package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.viewmodel.StatusFilter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
 * **v2026-07-27 P8 Phase 2 改造**：
 * - 渲染顺序由 [statusOrder] 参数控制（从 ESP 加载 / 用户拖拽后由 ViewModel 持久化）
 * - 6 个状态项**全部**支持长按拖拽（与 P1 GROUP Tab 风格一致）
 * - 拖拽视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 *
 * **架构角色**：
 * - 本函数是 sections 包的内部实现（`internal` 可见性）
 * - 由 [AppDrawerContentImpl] 在 DrawerSection.STATUS 分支调用
 * - 调用方应使用 `com.corgimemo.app.ui.components.AppDrawerContent`（薄壳层）
 *
 * @param statusOrder 状态项渲染顺序（来自 HomeViewModel.statusOrder，可拖拽排序）
 * @param currentFilter 当前选中的状态过滤项
 * @param totalCount "全部状态"项显示的总数（来自 HomeViewModel.totalTodoCount）
 * @param pinnedCount 置顶待办数
 * @param pendingCount 待完成待办数
 * @param completedCount 已完成待办数
 * @param overdueCount 已过期待办数
 * @param repeatReminderCount 设置了重复提醒的待办数
 * @param onFilterClick 状态项点击回调（参数为新选中的 StatusFilter）
 * @param onReorder 状态项拖拽完成回调（参数为排序后的新 StatusFilter 列表，v2026-07-27 P8 Phase 2 新增）
 * @param modifier 外部 Modifier
 */
@Composable
internal fun StatusFilterSection(
    statusOrder: List<StatusFilter>,
    currentFilter: StatusFilter,
    totalCount: Int,
    pinnedCount: Int,
    pendingCount: Int,
    completedCount: Int,
    overdueCount: Int,
    repeatReminderCount: Int,
    onFilterClick: (StatusFilter) -> Unit,
    onReorder: (List<StatusFilter>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-27 P8 Phase 2 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 复制列表，重排，通知外层
        val newOrder = statusOrder.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onReorder(newOrder)
    }

    // 把 6 个 count 装成 Map，便于按 StatusFilter 查表
    val countMap: Map<StatusFilter, Int> = mapOf(
        StatusFilter.ALL to totalCount,
        StatusFilter.PINNED to pinnedCount,
        StatusFilter.PENDING to pendingCount,
        StatusFilter.COMPLETED to completedCount,
        StatusFilter.OVERDUE to overdueCount,
        StatusFilter.REPEAT_REMINDER to repeatReminderCount
    )

    Column(modifier = modifier) {
        // 6 个状态项，按 statusOrder 顺序渲染，全部支持长按拖拽
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // items() 按 statusOrder 渲染，注意：key 用 StatusFilter.name（稳定枚举名）
            items(
                items = statusOrder,
                key = { it.name }
            ) { filter ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = filter.name
                ) { isDragging ->
                    val context = LocalContext.current
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = if (isDragging) 1.05f else 1f
                                scaleY = if (isDragging) 1.05f else 1f
                                shadowElevation = if (isDragging) 8f else 0f
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                },
                                onDragStopped = {}
                            )
                    ) {
                        CategoryItem(
                            icon = statusIcon(filter),
                            name = statusDisplayName(filter),
                            count = countMap[filter] ?: 0,
                            isSelected = currentFilter == filter,
                            showMenu = false,
                            onClick = { onFilterClick(filter) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * StatusFilter → emoji 图标映射
 */
private fun statusIcon(filter: StatusFilter): String = when (filter) {
    StatusFilter.ALL -> STATUS_ICON_ALL
    StatusFilter.PINNED -> STATUS_ICON_PINNED
    StatusFilter.PENDING -> STATUS_ICON_PENDING
    StatusFilter.COMPLETED -> STATUS_ICON_COMPLETED
    StatusFilter.OVERDUE -> STATUS_ICON_OVERDUE
    StatusFilter.REPEAT_REMINDER -> STATUS_ICON_REPEAT
}

/**
 * StatusFilter → 中文显示名称映射
 */
private fun statusDisplayName(filter: StatusFilter): String = when (filter) {
    StatusFilter.ALL -> "全部状态"
    StatusFilter.PINNED -> "置顶"
    StatusFilter.PENDING -> "待完成"
    StatusFilter.COMPLETED -> "已完成"
    StatusFilter.OVERDUE -> "已过期"
    StatusFilter.REPEAT_REMINDER -> "重复提醒"
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
