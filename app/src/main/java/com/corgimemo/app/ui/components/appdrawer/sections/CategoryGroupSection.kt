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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.ui.components.appdrawer.model.CategoryAction
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 待办分组管理分区（侧边栏）
 *
 * 布局：
 * 1. "全部待办"项（selectedCategoryId == null）
 * 2. "未分类"项（selectedCategoryId == 0L）
 * 3. 自定义分类列表（来自 [categories]，按 sortOrder 排序，**v2026-07-27 起支持长按拖拽**）
 *
 * **v2026-07-27 调整**：删除内部"分组管理"标题 + 橙线，避免与上方
 * [DrawerSectionTab] Tab 切换器的"分组管理"文字重复。
 * 顶部 8dp 间距用 LazyColumn.padding(top) 替代原来的 Spacer。
 *
 * **v2026-07-27 P8 Phase 1 改造**：自定义分类列表接入 Reorderable 库
 * - 长按整行触发拖拽（与首页 todo 卡片拖拽一致）
 * - 拖拽中视觉：scale 1.05 + shadowElevation 8dp + zIndex 1f
 * - 触觉反馈：HapticFeedbackManager.TEXT_MOVE
 * - 顶部 2 个 fixed items（全部待办/未分类）**不可拖拽**（无 sortOrder）
 *
 * **v2026-07-28 Plan A 改造**：onReorder 调用时机从 onMove 改为 onDragStopped
 * - 原：每次 onMove 都回调 onReorder → 整条 StateFlow 链路更新 → 单次拖拽重组 200-400 次
 * - 现：拖拽中仅更新本地 pendingReorder，拖拽结束才回调 onReorder
 * - 预期：单次拖拽重组 < 10 次
 *
 * **v2026-07-28 Plan D 改造**：graphicsLayer 参数改用 animateFloatAsState
 * - 原：scale/shadow 突变（isDragging 切换瞬间从 1.0/0 跳到 1.05/8）
 * - 现：scale/shadow 用 120ms tween 平滑过渡
 * - 目的：消除 scale/shadow 突变导致的残影/闪烁
 *
 * 点击自定义分类右侧三点菜单 → 触发 [CategoryAction.ShowMenu]（MainScreen 显示 BottomSheet）
 *
 * **可见性说明**：原 `private` 改为 `internal`，被 AppDrawerContentImpl 调用。
 *
 * @param categories 自定义分类列表（已按 sortOrder 排序）
 * @param todoCountByCategory 各分类 ID → 待办数量映射（key=-1 表示全部，key=0 表示未分类）
 * @param selectedCategoryId 当前选中的分类 ID（null=全部, 0L=未分类, 其他=自定义分类 ID）
 * @param onCategoryClick 点击分类行回调（参数为分类 ID）
 * @param onCategoryAction 分组操作回调（ShowMenu / Pin / Rename / Delete）
 * @param onReorder 拖拽结束回调（v2026-07-28 Plan A：仅在拖拽结束时调用，参数为最终的新分类列表）
 *   原 v2026-07-27 接 Reorderable onMove 通知，委托外层 ViewModel 持久化
 * @param modifier 外部 Modifier
 */
@Composable
internal fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    onReorder: (List<Category>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 🆕 v2026-07-28 拖拽埋点（诊断"残影/闪烁"问题，调试代码不入仓）
    //   7 类埋点：onRecompose / onMove / onDragStarted / onDragStopped /
    //             onGraphicsLayerChange / onLayoutChange / onReorderSubmit
    val diag = rememberReorderableDiagnostics("Category")
    // 重组埋点：函数体每次重组都自增
    diag.onRecompose()

    // 🆕 v2026-07-28 Plan A：拖拽中暂存新顺序，拖拽结束才提交外层 ViewModel
    //   原因：避免每次 onMove 都触发 ViewModel → Repository → DAO → StateFlow 整条链路更新，
    //         导致整列表重组（实测单次拖拽重组 200-400 次）。
    //   行为：拖拽过程中仅更新本地 pendingReorder，onDragStopped 才回调 onReorder。
    var pendingReorder by remember { mutableStateOf<List<Category>?>(null) }
    // 拖拽中显示 pendingReorder（如果有），否则显示原始 categories
    val displayCategories = pendingReorder ?: categories

    // 🆕 v2026-07-27 P8 Phase 1 拖拽状态
    // 固定项偏移：LazyColumn 前 2 项（"全部待办"/"未分类"）不可拖拽
    val listState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState = listState) { from, to ->
        // 埋点 #2：位置交换（仅记录，不回调外层）
        diag.onMove(from = from.index, to = to.index, listSize = displayCategories.size + 2, isDragging = true)
        // 全局索引 → categories 子列表索引（减去固定项偏移 2）
        val fromIndex = from.index - 2
        val toIndex = to.index - 2
        val currentList = pendingReorder ?: categories
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) {
            return@rememberReorderableLazyListState
        }
        // Plan A：仅更新本地 pendingReorder，不触发外层 ViewModel
        pendingReorder = currentList.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        // 埋点：onMove 后的 pendingReorder 快照（验证 list 变化 → isDragging 因果链）
        diag.onMoveSnapshot(from = fromIndex, to = toIndex, snapshot = pendingReorder!!)
    }

    // 埋点 #6：LazyColumn 布局变化
    TrackLazyColumnLayout(listState, diag)

    // 埋点：items() 列表 key 顺序变化
    LaunchedEffect(displayCategories) {
        diag.onListKeysChange(displayCategories.map { it.id })
    }

    Column(modifier = modifier) {
        // 分类列表（顶部 8dp 间距替代原"标题 + 橙线 + Spacer 8dp"）
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            // 1. "全部待办" 项（特殊 ID: -1L，不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryId == null,
                    showMenu = false,
                    onClick = { onCategoryClick(null) }
                )
            }

            // 2. "未分类" 项（特殊 ID: 0L，不可拖拽）
            item {
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分类",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = selectedCategoryId == 0L,
                    showMenu = false,
                    onClick = { onCategoryClick(0L) }
                )
            }

            // 3. 自定义分类列表（v2026-07-27 起支持长按拖拽）
            //    注意：key 用 category.id（稳定主键），不是 index（拖拽中 index 会变）
            //    v2026-07-28 Plan A：items() 使用 displayCategories，拖拽中显示 pendingReorder
            items(
                items = displayCategories,
                key = { it.id }
            ) { category ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = category.id
                ) { isDragging ->
                    val context = LocalContext.current
                    // 埋点：ReorderableItem 创建/销毁（验证 isDragging 反复切换根因）
                    DisposableEffect(category.id) {
                        diag.onItemEnter(category.id)
                        onDispose {
                            diag.onItemExit(category.id)
                        }
                    }
                    // 埋点 #4：graphicsLayer 参数变化（isDragging 切换时触发）
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
                    //   即使 isDragging 切换，scale/shadow 也是平滑过渡，不会瞬间跳变
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
                    // 埋点：拖拽中 scale/shadow 实际值（验证动画过渡是否生效）
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
                                    // Plan A：拖拽开始时清空 pendingReorder，确保新拖拽干净起步
                                    pendingReorder = null
                                    // 埋点 #1：拖拽开始
                                    diag.onDragStarted(category.id)
                                    HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = InteractionType.TEXT_MOVE,
                                        enabled = true
                                    )
                                },
                                onDragStopped = {
                                    // Plan A：拖拽结束才提交到外层 ViewModel
                                    pendingReorder?.let { finalList ->
                                        // 埋点 #7：实际触发 ViewModel 更新的次数（Plan A 验证）
                                        diag.onReorderSubmit(finalList.size)
                                        onReorder(finalList)
                                    }
                                    pendingReorder = null
                                    // 埋点 #3：拖拽结束
                                    diag.onDragStopped(category.id, listSize = displayCategories.size + 2)
                                }
                            )
                    ) {
                        val icon = categoryIcons[category.type] ?: "📂"
                        CategoryItem(
                            icon = icon,
                            name = category.name,
                            count = todoCountByCategory[category.id] ?: 0,
                            isSelected = selectedCategoryId == category.id,
                            showMenu = !category.isDefault,
                            onClick = { onCategoryClick(category.id) },
                            onMenuClick = {
                                onCategoryAction(
                                    CategoryAction.ShowMenu(category)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
