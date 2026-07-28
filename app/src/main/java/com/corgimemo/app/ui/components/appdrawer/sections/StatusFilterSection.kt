package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.viewmodel.FilterItem
import com.corgimemo.app.viewmodel.StatusFilter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 状态过滤分区（v2026-07-28 v2 跨维度改造）
 *
 * 显示在侧滑栏 TODO Tab 下"状态管理"激活时。提供 6 个状态过滤项：
 * **全部状态 / 置顶 / 待完成 / 已完成 / 已过期 / 重复提醒**。
 *
 * **v2 跨维度改造**（v2026-07-28）：
 * - 改为多选交互（`selectedStatusItems: Set<FilterItem.Status>`）
 * - 配合全局 [com.corgimemo.app.viewmodel.HomeViewModel.filterMode] 实现 OR/AND/NOT 跨维度组合
 * - "全部状态"项点击 → 清空所有过滤（[onClearAllFilters]）
 * - 其他 5 个状态项（PINNED / PENDING / COMPLETED / OVERDUE / REPEAT_REMINDER）支持多选
 *
 * **🆕 v2026-07-28 搜索框 + Plan A/D 改造**：
 * - 加搜索框（本地状态，按状态名模糊过滤显示）
 * - Plan A：onReorder 调用时机从 onMove 改为 onDragStopped
 * - Plan D：graphicsLayer 参数改用 animateFloatAsState 平滑过渡
 * - 接入 [rememberReorderableDiagnostics] 拖拽埋点
 *
 * **"全部状态"作为固定首项不参与拖拽**（与之前保持一致）：
 * - ALL 用普通 `item()` 渲染（非 `ReorderableItem` 包装）
 * - 拖拽范围仅限其他 5 个状态项
 * - onMove 中拒绝 `from.index == 0` 和 `to.index == 0`
 *
 * @param statusOrder 状态项渲染顺序（来自 HomeViewModel.statusOrder，可拖拽排序）
 * @param selectedStatusItems 当前选中的状态过滤项集合（不包含 ALL）
 * @param totalCount "全部状态"项显示的总数
 * @param pinnedCount 置顶待办数
 * @param pendingCount 待完成待办数
 * @param completedCount 已完成待办数
 * @param overdueCount 已过期待办数
 * @param repeatReminderCount 设置了重复提醒的待办数
 * @param onStatusToggle 状态项点击回调（参数为要切换的 [FilterItem.Status]）
 * @param onClearAllFilters 点击"全部状态"时回调
 * @param onReorder 状态项拖拽完成回调
 * @param modifier 外部 Modifier
 */
@Composable
internal fun StatusFilterSection(
    statusOrder: List<StatusFilter>,
    selectedStatusItems: Set<FilterItem.Status>,
    totalCount: Int,
    pinnedCount: Int,
    pendingCount: Int,
    completedCount: Int,
    overdueCount: Int,
    repeatReminderCount: Int,
    onStatusToggle: (FilterItem.Status) -> Unit,
    onClearAllFilters: () -> Unit,
    onReorder: (List<StatusFilter>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-28 P8.7：拆分固定首项与可拖列表
    val draggableFilters = remember(statusOrder) {
        statusOrder.filter { it != StatusFilter.ALL }
    }

    // 🆕 v2026-07-28 搜索框本地状态（按 statusDisplayName 模糊过滤显示）
    var searchQuery by remember { mutableStateOf("") }
    val filteredDraggable = remember(draggableFilters, searchQuery) {
        if (searchQuery.isBlank()) {
            draggableFilters
        } else {
            draggableFilters.filter { filter ->
                statusDisplayName(filter).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 🆕 v2026-07-28 拖拽埋点（诊断"残影/闪烁"问题，调试代码不入仓）
    val diag = rememberReorderableDiagnostics("Status")
    diag.onRecompose()

    // 🆕 v2026-07-28 Plan A+：拖拽中暂存新顺序，**不主动清空**
    var pendingReorder by remember { mutableStateOf<List<StatusFilter>?>(null) }
    val displayDraggable = pendingReorder ?: draggableFilters

    // 🆕 v2026-07-28 方案 C 监听器：ViewModel 数据与 pendingReorder 同步时自动清空
    LaunchedEffect(draggableFilters, pendingReorder) {
        if (pendingReorder != null) {
            val pendingIds = pendingReorder!!.map { it.name }
            val currentIds = draggableFilters.map { it.name }
            if (pendingIds == currentIds) {
                pendingReorder = null
            }
        }
    }

    // 🆕 v2026-07-28 P8 Phase 2 拖拽状态（仅作用于可拖列表）
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 拒绝拖动 ALL（index 0 是固定首项）
        if (from.index == 0 || to.index == 0) {
            return@rememberReorderableLazyListState
        }
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex !in displayDraggable.indices || toIndex !in displayDraggable.indices) {
            return@rememberReorderableLazyListState
        }
        // 🆕 Plan A：仅更新本地 pendingReorder，不触发外层 ViewModel
        pendingReorder = displayDraggable.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        diag.onMove(from = fromIndex, to = toIndex, listSize = displayDraggable.size, isDragging = true)
    }

    // 埋点 #6：LazyColumn 布局变化
    TrackLazyColumnLayout(listState, diag)

    // 埋点：items() 列表 key 顺序变化
    LaunchedEffect(displayDraggable) {
        diag.onListKeysChange(displayDraggable.map { it.name })
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
        // 🆕 v2026-07-28 搜索框（仅过滤可拖列表的显示，不跨维度）
        StatusSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onClear = { searchQuery = "" },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            // 1. "全部状态"（固定首项，**不可拖**）
            //    v2 跨维度：点击调用 onClearAllFilters() 清空所有过滤项
            item(key = StatusFilter.ALL.name) {
                CategoryItem(
                    icon = statusIcon(StatusFilter.ALL),
                    name = statusDisplayName(StatusFilter.ALL),
                    count = countMap[StatusFilter.ALL] ?: 0,
                    isSelected = selectedStatusItems.isEmpty(),
                    showMenu = false,
                    onClick = { onClearAllFilters() }
                )
            }

            // 2. 其他状态项（**可拖**）— 渲染 displayDraggable（拖拽中用 pendingReorder）
            //    v2 跨维度：多选交互 isSelected = FilterItem.Status(filter) in selectedStatusItems
            //    点击调用 onStatusToggle(FilterItem.Status(filter))
            items(
                items = displayDraggable,
                key = { it.name }
            ) { filter ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = filter.name
                ) { isDragging ->
                    val context = LocalContext.current
                    // 埋点：ReorderableItem 创建/销毁
                    DisposableEffect(filter.name) {
                        diag.onItemEnter(filter.name.hashCode().toLong())
                        onDispose {
                            diag.onItemExit(filter.name.hashCode().toLong())
                        }
                    }
                    // 埋点 #4：graphicsLayer 参数变化
                    LaunchedEffect(isDragging) {
                        diag.onGraphicsLayerChange(
                            isDragging = isDragging,
                            scaleX = if (isDragging) 1.05f else 1f,
                            scaleY = if (isDragging) 1.05f else 1f,
                            shadowElevation = if (isDragging) 8f else 0f,
                            zIndex = if (isDragging) 1f else 0f
                        )
                    }
                    // 🆕 v2026-07-28 Plan D：graphicsLayer 参数动画过渡
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 120),
                        label = "statusScale"
                    )
                    val shadow by animateFloatAsState(
                        targetValue = if (isDragging) 8f else 0f,
                        animationSpec = tween(durationMillis = 120),
                        label = "statusShadow"
                    )
                    // 埋点：拖拽中 scale/shadow 实际值
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                            snapshotFlow { scale to shadow }
                                .collect { (s, sh) ->
                                    diag.onScaleFrame(scale = s, shadow = sh, isDragging = isDragging)
                                }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = shadow
                            }
                            .longPressDraggableHandle(
                                onDragStarted = {
                                    // Plan A+：拖拽开始时清空 pendingReorder，确保新拖拽干净起步
                                    pendingReorder = null
                                    diag.onDragStarted(filter.name.hashCode().toLong())
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                },
                                onDragStopped = {
                                    // Plan A+：松手时调用 onReorder，**不**主动清空 pendingReorder
                                    pendingReorder?.let { finalList ->
                                        diag.onReorderSubmit(finalList.size)
                                        val newOrder = listOf(StatusFilter.ALL) + finalList
                                        onReorder(newOrder)
                                    }
                                    diag.onDragStopped(
                                        filter.name.hashCode().toLong(),
                                        listSize = displayDraggable.size + 1
                                    )
                                }
                            )
                    ) {
                        val item = FilterItem.Status(filter)
                        CategoryItem(
                            icon = statusIcon(filter),
                            name = statusDisplayName(filter),
                            count = countMap[filter] ?: 0,
                            isSelected = item in selectedStatusItems,
                            showMenu = false,
                            onClick = { onStatusToggle(item) }
                        )
                    }
                }
            }

            // 搜索无结果提示
            if (searchQuery.isNotBlank() && filteredDraggable.isEmpty() && draggableFilters.isNotEmpty()) {
                item(key = "no_match_status") {
                    Text(
                        text = "未找到匹配的状态",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * 状态搜索框（v2026-07-28 新增）
 */
@Composable
private fun StatusSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("搜索状态") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "清空")
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors()
    )
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
