package com.corgimemo.app.ui.components.appdrawer.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import kotlinx.coroutines.CancellationException
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import com.corgimemo.app.viewmodel.FilterItem
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 待办分组管理分区（侧边栏，v2026-07-28 v2 跨维度改造）
 *
 * **v2 跨维度改造**（v2026-07-28）：
 * - 改为多选交互（`selectedCategoryItems: Set<FilterItem.Category>`）
 * - 配合全局 [com.corgimemo.app.viewmodel.HomeViewModel.filterMode] 实现 OR/AND/NOT 跨维度组合
 * - "全部待办"项点击 → 清空所有过滤（[onClearAllFilters]）
 * - "未分组"项和自定义分组项支持多选
 *
 * **🆕 v2026-07-29 搜索框外部化**：
 * - 搜索框移至 [AppDrawerContentImpl] 顶部展开区，本组件不再渲染搜索框
 * - `searchQuery` 由外部传入，本组件仅负责按查询词过滤显示
 *
 * **布局**（沿用 P8 Phase 1 设计）：
 * 1. "全部待办"项（selectedCategoryItems 为空时高亮）
 * 2. "未分组"项（FilterItem.Category(0L) in selectedCategoryItems 时高亮）
 * 3. 自定义分类列表（按 sortOrder 排序，**v2026-07-27 起支持长按拖拽**）
 *
 * **v2026-07-27 调整**：删除内部"分组管理"标题 + 橙线，避免与上方
 * [DrawerSectionTab] Tab 切换器的"分组管理"文字重复。
 *
 * **v2026-07-28 Plan A 改造**：onReorder 调用时机从 onMove 改为 onDragStopped
 * - 原：每次 onMove 都回调 onReorder → 整条 StateFlow 链路更新 → 单次拖拽重组 200-400 次
 * - 现：拖拽中仅更新本地 pendingReorder，拖拽结束才回调 onReorder
 *
 * **v2026-07-28 Plan D 改造**：graphicsLayer 参数改用 animateFloatAsState
 * - 原：scale/shadow 突变（isDragging 切换瞬间从 1.0/0 跳到 1.05/8）
 * - 现：scale/shadow 用 120ms tween 平滑过渡
 *
 * **v2026-07-28 拖拽埋点**：接入 [rememberReorderableDiagnostics]（7 类埋点）
 *
 * @param categories 自定义分类列表（已按 sortOrder 排序）
 * @param todoCountByCategory 各分类 ID → 待办数量映射（key=-1 表示全部，key=0 表示未分组）
 * @param selectedCategoryItems 当前选中的分组项集合（不包含"全部"项）
 * @param searchQuery 搜索查询词（由外部 AppDrawerContentImpl 展开区传入，按分组名模糊过滤）
 * @param onCategoryToggle 分组项点击回调（参数为要切换的 [FilterItem.Category]）
 * @param onClearAllFilters 点击"全部待办"时回调
 * @param onCategoryAction 分组操作回调（ShowMenu / Pin / Rename / Delete）
 * @param onReorder 拖拽结束回调
 * @param modifier 外部 Modifier
 */
@Composable
internal fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryItems: Set<FilterItem.Category>,
    searchQuery: String = "",
    onCategoryToggle: (FilterItem.Category) -> Unit,
    onClearAllFilters: () -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    onReorder: (List<Category>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-28 拖拽埋点（诊断"残影/闪烁"问题，调试代码不入仓）
    val diag = rememberReorderableDiagnostics("Category")
    diag.onRecompose()

    // 🆕 v2026-07-28 Plan A+：拖拽中暂存新顺序，**不主动清空**
    var pendingReorder by remember { mutableStateOf<List<Category>?>(null) }
    val displayCategories = pendingReorder ?: categories

    // v2026-07-29 改造：右滑展开状态管理（互斥展开）
    // - 同一时间仅允许一个分组处于右滑展开状态
    // - 展开时禁用该分组的长按拖拽（避免手势冲突）
    // - 与首页待办卡片左滑行为一致（swipeExpandedTodoId 模式）
    var swipeExpandedCategoryId by remember { mutableStateOf<Long?>(null) }

    // 🆕 v2026-07-28 方案 C 监听器：ViewModel 数据已与 pendingReorder 同步时自动清空
    LaunchedEffect(categories, pendingReorder) {
        if (pendingReorder != null) {
            val pendingIds = pendingReorder!!.map { it.id }
            val currentIds = categories.map { it.id }
            if (pendingIds == currentIds) {
                pendingReorder = null
            }
        }
    }

    // 🆕 v2026-07-29 搜索框外部化：searchQuery 由外部 AppDrawerContentImpl 传入
    val filteredCategories = remember(displayCategories, searchQuery) {
        if (searchQuery.isBlank()) {
            displayCategories
        } else {
            displayCategories.filter { category ->
                category.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // 🆕 v2026-07-27 P8 Phase 1 拖拽状态
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 拒绝拖动 ALL 和 UNCATEGORIZED（前 2 个固定项）
        if (from.index < 2 || to.index < 2) {
            return@rememberReorderableLazyListState
        }
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        val currentList = pendingReorder ?: categories
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) {
            return@rememberReorderableLazyListState
        }
        // v2026-07-29 改造：禁止跨置顶区拖动
        // - 置顶分组（isPinned=true）锁定在置顶区内拖拽
        // - 非置顶分组（isPinned=false）锁定在非置顶区内拖拽
        // - 数据库已按 `isPinned DESC, sortOrder ASC, id ASC` 排序，
        //   置顶分组在列表前部，非置顶在后部，故只需判断两端 isPinned 是否一致
        val fromCategory = currentList[fromIndex]
        val toCategory = currentList[toIndex]
        if (fromCategory.isPinned != toCategory.isPinned) {
            // 跨置顶区拖动 → 抛 CancellationException 中止 moveItems
            //
            // **为何不用 return**：
            // Reorderable 库的 moveItems 在调用 onMove 后会设置 predictedDraggingItemOffset
            // （基于 targetItem 位置）。如果 onMove 仅 return，predictedDraggingItemOffset
            // 仍会被设置，导致被拖拽项"跳"到目标位置附近，而非跟随手指。
            //
            // 抛 CancellationException 后：
            // 1. moveItems 的 catch 块捕获异常并"do nothing"
            // 2. predictedDraggingItemOffset 不被设置（保持 null）
            // 3. 被拖拽项位置 = it.offset + draggingItemDraggedDelta（继续跟随手指）
            // 4. oldDraggingItemIndex 残留但不影响（predictedDraggingItemOffset 为 null 时用 it.offset）
            // 5. onDragStopped 时会重置 oldDraggingItemIndex 和 predictedDraggingItemOffset
            throw CancellationException("Cross pinned zone drag rejected")
        }
        // Plan A：仅更新本地 pendingReorder，不触发外层 ViewModel
        pendingReorder = currentList.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        diag.onMove(from = fromIndex, to = toIndex, listSize = displayCategories.size + 2, isDragging = true)
        diag.onMoveSnapshot(from = fromIndex, to = toIndex, snapshot = pendingReorder!!)
    }

    TrackLazyColumnLayout(listState, diag)

    LaunchedEffect(displayCategories) {
        diag.onListKeysChange(displayCategories.map { it.id })
    }

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
        ) {
            // 1. "全部待办" 项（特殊 ID: -1L，不可拖拽）
            //    v2 跨维度：点击调用 onClearAllFilters() 清空所有过滤
            //    v2026-07-29 改造：highlightBackground = true（固定项加深背景）
            item(key = "all_todos") {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryItems.isEmpty(),
                    showMenu = false,
                    highlightBackground = true,
                    onClick = { onClearAllFilters() }
                )
            }

            // 2. "未分组" 项（FilterItem.Category(0L)，不可拖拽）
            // v2026-07-29：原"未分类"改为"未分组"，与待办分组语义一致
            // v2026-07-29 改造：highlightBackground = true（固定项加深背景）
            item(key = "uncategorized") {
                val item = FilterItem.Category(0L)
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分组",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = item in selectedCategoryItems,
                    showMenu = false,
                    highlightBackground = true,
                    onClick = { onCategoryToggle(item) }
                )
            }

            // 3. 自定义分类列表（v2026-07-27 起支持长按拖拽）
            //    v2 跨维度：多选交互 isSelected = FilterItem.Category(category.id) in selectedCategoryItems
            //    v2026-07-28 搜索：渲染 filteredCategories（拖拽中仍用 displayCategories）
            //    v2026-07-29 改造：置顶分组（isPinned=true）加深背景，如微信置顶会话
            //    v2026-07-29 改造：用 SwipeableCategoryBox 替代三点菜单，右滑展开操作按钮
            items(
                items = filteredCategories,
                key = { it.id }
            ) { category ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = category.id
                ) { isDragging ->
                    val context = LocalContext.current
                    DisposableEffect(category.id) {
                        diag.onItemEnter(category.id)
                        onDispose {
                            diag.onItemExit(category.id)
                        }
                    }
                    LaunchedEffect(isDragging) {
                        diag.onGraphicsLayerChange(
                            isDragging = isDragging,
                            scaleX = if (isDragging) 1.05f else 1f,
                            scaleY = if (isDragging) 1.05f else 1f,
                            shadowElevation = if (isDragging) 8f else 0f,
                            zIndex = if (isDragging) 1f else 0f
                        )
                    }
                    val scale by animateFloatAsState(
                        targetValue = if (isDragging) 1.05f else 1f,
                        animationSpec = tween(durationMillis = 120),
                        label = "scale"
                    )
                    val shadow by animateFloatAsState(
                        targetValue = if (isDragging) 8f else 0f,
                        animationSpec = tween(durationMillis = 120),
                        label = "shadow"
                    )
                    LaunchedEffect(isDragging) {
                        if (isDragging) {
                            snapshotFlow { scale to shadow }
                                .collect { (s, sh) ->
                                    diag.onScaleFrame(scale = s, shadow = sh, isDragging = isDragging)
                                }
                        }
                    }

                    // v2026-07-29 改造：用 SwipeableCategoryBox 包裹分组项
                    // - 右滑展开"置顶/编辑/删除"三个操作按钮（仅图标）
                    // - 展开时禁用长按拖拽（避免手势冲突）
                    // - 互斥展开：同时只允许一个分组展开
                    // - 动画参数与首页待办卡片左滑一致（300ms 弹性回弹 + 20% 阈值吸附）
                    SwipeableCategoryBox(
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                shadowElevation = shadow
                            },
                        // 右滑手势始终启用（用于展开/收起）
                        isEnabled = true,
                        isExpanded = swipeExpandedCategoryId == category.id,
                        isPinned = category.isPinned,
                        onExpandChange = { expanded ->
                            swipeExpandedCategoryId = if (expanded) category.id else null
                        },
                        // v2026-07-29 互斥恢复机制：
                        // 首次右滑时调用，清除其他展开的分组（不设为当前ID，避免 isExpanded 变化导致 pointerInput 重启）
                        // 上一个展开的分组 isExpanded 变 false 后通过 LaunchedEffect 开始左滑归位
                        onExpandStart = {
                            if (swipeExpandedCategoryId != null && swipeExpandedCategoryId != category.id) {
                                swipeExpandedCategoryId = null
                            }
                        },
                        onPinClick = {
                            onCategoryAction(CategoryAction.Pin(category))
                        },
                        onEditClick = {
                            onCategoryAction(CategoryAction.Rename(category))
                        },
                        onDeleteClick = {
                            onCategoryAction(CategoryAction.Delete(category))
                        }
                    ) {
                        // content：长按拖拽 handle + CategoryItem
                        // 展开时禁用 longPressDraggableHandle，避免右滑手势与长按拖拽冲突
                        Box(
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    // v2026-07-29 改造：任何分组展开时禁用拖拽
                                    // 原因：右滑展开后若仍可长按拖拽，会产生手势冲突
                                    enabled = swipeExpandedCategoryId == null,
                                    onDragStarted = {
                                        pendingReorder = null
                                        diag.onDragStarted(category.id)
                                        HapticFeedbackManager.performHapticFeedback(
                                            context = context,
                                            type = InteractionType.TEXT_MOVE,
                                            enabled = true
                                        )
                                    },
                                    onDragStopped = {
                                        pendingReorder?.let { finalList ->
                                            diag.onReorderSubmit(finalList.size)
                                            onReorder(finalList)
                                        }
                                        diag.onDragStopped(category.id, listSize = displayCategories.size + 2)
                                    }
                                )
                        ) {
                            val icon = categoryIcons[category.type] ?: "📂"
                            val item = FilterItem.Category(category.id)
                            CategoryItem(
                                icon = icon,
                                name = category.name,
                                count = todoCountByCategory[category.id] ?: 0,
                                isSelected = item in selectedCategoryItems,
                                // v2026-07-29 改造：移除三点菜单按钮，改用右滑操作
                                showMenu = false,
                                // v2026-07-29 改造：置顶分组加深背景（如微信置顶会话）
                                highlightBackground = category.isPinned,
                                onClick = { onCategoryToggle(item) }
                            )
                        }
                    }
                }
            }

            // 搜索无结果提示
            if (searchQuery.isNotBlank() && filteredCategories.isEmpty() && displayCategories.isNotEmpty()) {
                item(key = "no_match_category") {
                    Text(
                        text = "未找到匹配的分组",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
