package com.corgimemo.app.ui.screens.date

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.R
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.components.CorgiPullRefreshIndicator
import com.corgimemo.app.ui.components.PullRefreshState
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.SwipeActionType
import com.corgimemo.app.ui.components.SwipeButtonConfig
import com.corgimemo.app.ui.components.SwipeableTodoBox
import com.corgimemo.app.ui.components.UnifiedEmptyState
import com.corgimemo.app.ui.components.rememberPullRefreshStateHolder
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.corgimemo.app.ui.screens.date.components.DateSectionHeader
import com.corgimemo.app.ui.screens.date.components.PinnedDateCard
import com.corgimemo.app.ui.screens.date.components.SpecialDateCard
import com.corgimemo.app.viewmodel.DateGroup
import com.corgimemo.app.viewmodel.SpecialDateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 特殊日期列表页面（重构版）
 *
 * 关键设计：
 * 1. 单一 ticker：顶层 LaunchedEffect 驱动 nowMs 状态，每秒更新一次，
 *    避免每张卡片各自启动 ticker 导致 N 个协程。
 * 2. 三段式布局：搜索框 → 3 个折叠分组头（倒计时/正计时 默认展开；已归档 折叠）
 *    → 卡片列表（每张卡片用 SwipeableTodoBox 包裹提供左滑三按钮）
 * 3. 左滑操作：置顶/归档/删除三按钮
 * 4. 归档后 Snackbar 3 秒撤回：点"撤回"恢复数据；超时仅清空缓存（不撤回）
 *
 * 2026-07-13 重构：第三段分组语义由"已过期"改为"已归档"，
 * 分组规则（SpecialDateViewModel.groupByDisplayDates）改为按 isArchived + 日期与今天大小划分。
 *
 * 注意：本页面**不包含** Scaffold / padding(paddingValues) / SnackbarHost。
 * - Scaffold 由 MainScreen 顶层统一管理（与 HomeScreen / InspirationScreen 保持一致）
 * - snackbarHostState 由 MainScreen 创建并通过参数传入，避免双重渲染
 * - 这样可保证搜索框与待办页 / 灵感页在同一相对位置（不被多推下 status bar 高度）
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param snackbarHostState Snackbar 宿主（由 MainScreen 顶层 Scaffold 创建并传入）
 * @param viewModel 日期视图模型（通过 Hilt 自动注入）
 */
@Composable
fun SpecialDateScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    viewModel: SpecialDateViewModel = hiltViewModel()
) {
    val groupedDates by viewModel.groupedDates.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()
    val expandedDateId by viewModel.expandedDateId.collectAsState()
    val pinnedDateId by viewModel.pinnedDateId.collectAsState()
    val pinnedDate by viewModel.pinnedDate.collectAsState()  // 2026-07-14 新增
    val pendingArchive by viewModel.pendingArchive.collectAsState()
    // 2026-07-14 新增：三点按钮弹窗功能相关状态
    val hideDetails by viewModel.hideDetails.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val selectedDateIds by viewModel.selectedDateIds.collectAsState()
    val pendingDeletedDate by viewModel.pendingDeletedDate.collectAsState()

    // 2026-07-14 新增：批量模式下按返回键退出批量模式
    BackHandler(enabled = isBatchMode) {
        viewModel.exitBatchMode()
    }

    /** 协程作用域：用于点击日期卡片时显示"编辑功能开发中" Snackbar */
    val coroutineScope = rememberCoroutineScope()

    // 单一 ticker：整页仅 1 个协程驱动 nowMs，每秒更新一次
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    // 接收 SpecialDateCardStyleScreen 的保存成功信号（通过 SavedStateHandle）
    // 信号源：SpecialDateCardStyleScreen 在 saveState == Success 时
    //        调用 previousBackStackEntry.savedStateHandle.set("date_saved", true) 并 popBackStack
    // 信号消费：此处 LaunchedEffect 监听到 true 时弹"保存成功" Snackbar,并 remove 标记
    //          防止旋转屏幕 / recompose 重复触发
    val dateSaved = navController.currentBackStackEntry?.savedStateHandle?.get<Boolean>("date_saved")
    LaunchedEffect(dateSaved) {
        if (dateSaved == true) {
            snackbarHostState?.showSnackbar("保存成功")
            navController.currentBackStackEntry?.savedStateHandle?.remove<Boolean>("date_saved")
        }
    }

    // Snackbar：归档后 3 秒内可点"撤回"恢复
    // snackbarHostState 由 MainScreen 顶层 Scaffold 创建并通过参数传入
    // 深链场景下可能为 null，此时跳过 showSnackbar 调用（撤回功能静默失效）
    // 注意：仅用 pendingArchive 作为 key，因为 nullable 状态不适合作为 LaunchedEffect key
    // （否则会引发 Kotlin 编译警告"elvis always returns left operand"）
    LaunchedEffect(pendingArchive) {
        val host = snackbarHostState ?: return@LaunchedEffect
        val snapshot = pendingArchive ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = "已归档『${snapshot.title}』",
            actionLabel = "撤回",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        when (result) {
            // 用户点"撤回"：恢复数据
            SnackbarResult.ActionPerformed -> viewModel.undoArchive()
            // 3s 后超时 / 用户手动关闭：仅清空 pendingArchive（数据已正确归档）
            SnackbarResult.Dismissed -> viewModel.clearPendingArchive()
        }
    }

    // 2026-07-14 新增：删除撤回 Snackbar（3 秒内可点"撤回"恢复）
    // 与归档撤回逻辑结构一致：snapshot 为 null 时直接返回，避免重复弹 Snackbar
    LaunchedEffect(pendingDeletedDate) {
        val host = snackbarHostState ?: return@LaunchedEffect
        val snapshot = pendingDeletedDate ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = "已删除『${snapshot.title}』",
            actionLabel = "撤回",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        when (result) {
            // 用户点"撤回"：恢复数据
            SnackbarResult.ActionPerformed -> viewModel.undoDelete()
            // 3s 后超时 / 用户手动关闭：仅清空 pendingDeletedDate（数据已正确删除）
            SnackbarResult.Dismissed -> viewModel.clearPendingDeletedDate()
        }
    }

    // 各分组的展开状态：COUNTDOWN + COUNTUP 默认展开，EXPIRED 默认折叠
    val expandState = remember {
        mutableStateMapOf(
            DateGroup.COUNTDOWN to true,
            DateGroup.COUNTUP to true,
            DateGroup.EXPIRED to false
        )
    }

    // 顶层 Box：与 HomeScreen / InspirationScreen 结构一致
    // - MainScreen 顶层已用 .padding(paddingValues) 避开 status bar / nav bar
    // - 本页面 Box 不再嵌套 Scaffold / padding(paddingValues)，避免双重 padding
    // - 确保搜索框与待办页 / 灵感页在同一相对位置
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. 搜索框
            SearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onClear = { viewModel.updateSearchQuery("") },
                placeholder = "搜索日期...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin))
            )

            // 移除 Spacer(8.dp),与待办页 SectionHeader 距搜索框距离完全一致(0dp)
            // (待办页 ZonedReorderableLazyColumn 的 itemSpacing = 0.dp)

            // 2. 内容区显示逻辑
            if (!isDataInitialized) {
                // 数据未初始化：显示页面专属骨架屏，避免从空列表闪烁到有数据
                SpecialDateSkeleton()
            } else if (groupedDates.isEmpty() && pinnedDate == null) {
                // 2026-07-14 修改：仅当没有置顶卡也没有任何分组卡时才显示空态
                UnifiedEmptyState(
                    icon = "📅",
                    title = "还没有特殊日期~",
                    subtitle = "记录重要的日子，不错过每个纪念！",
                    ctaText = "📅 添加日期",
                    onCtaClick = onFabClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 2026-07-14 新增：下拉刷新（参考 InspirationScreen / HomeScreen）
                // - isRefreshing 状态来自 viewModel
                // - onRefresh 回调触发 viewModel.onRefresh()
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                DateSectionsList(
                    pinnedDate = pinnedDate,  // 2026-07-14 新增
                    groupedDates = groupedDates,
                    nowMs = nowMs,
                    expandedDateId = expandedDateId,
                    pinnedDateId = pinnedDateId,
                    expandState = expandState,
                    onSetExpanded = viewModel::setExpandedDateId,
                    onPin = viewModel::pinDate,
                    onUnpin = viewModel::unpinDate,
                    onArchive = viewModel::archiveDate,
                    onUnarchive = viewModel::unarchiveDate,
                    onDelete = viewModel::deleteDate,
                    onCardClick = { date ->
                        navController.navigate(Screen.SpecialDateDetailWithId.createRoute(date.id))
                    },
                    // 2026-07-14 新增：三点按钮弹窗功能参数
                    isSimpleMode = hideDetails,
                    isBatchMode = isBatchMode,
                    selectedDateIds = selectedDateIds,
                    onToggleSelection = { id -> viewModel.toggleSelection(id) },
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.onRefresh() }
                )
            }
        }

        // 3. FAB
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 16.dp)
                .zIndex(10f)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "添加日期",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 三段式日期列表（倒计时/正计时/已归档）
 *
 * 列表结构：
 * - 每一组：DateSectionHeader + N × SwipeableTodoBox(SpecialDateCard)
 * - 卡片间距 8dp，水平内边距 20dp
 */
@Composable
private fun DateSectionsList(
    pinnedDate: com.corgimemo.app.viewmodel.DisplayDate?,  // 2026-07-14 新增
    groupedDates: Map<DateGroup, List<com.corgimemo.app.viewmodel.DisplayDate>>,
    nowMs: Long,
    expandedDateId: Long?,
    pinnedDateId: Long?,
    expandState: androidx.compose.runtime.snapshots.SnapshotStateMap<DateGroup, Boolean>,
    onSetExpanded: (Long?) -> Unit,
    onPin: (Long) -> Unit,
    onUnpin: (Long) -> Unit,
    onArchive: (Long) -> Unit,
    onUnarchive: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onCardClick: (com.corgimemo.app.viewmodel.DisplayDate) -> Unit,
    // 2026-07-14 新增：三点按钮弹窗功能参数
    /** 简洁模式：隐藏时间信息行（对应菜单"隐藏详情"） */
    isSimpleMode: Boolean = false,
    /** 批量选择模式：卡片显示左侧圆形选择框 */
    isBatchMode: Boolean = false,
    /** 批量模式下已选中的日期 id 集合 */
    selectedDateIds: Set<Long> = emptySet(),
    /** 批量模式下点击卡片切换选中状态的回调 */
    onToggleSelection: (Long) -> Unit = {},
    // 2026-07-14 新增：下拉刷新（参考 InspirationScreen / HomeScreen）
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    // 复用 Reorderable 库结构使 DateSectionHeader 与待办页 PinnedSectionHeader
    // 渲染结构完全一致(都用 ReorderableItem 包裹),保证布局对齐。
    // SectionHeader 不参与拖拽排序,onMove 留空,enabled=false 锁定。
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState) { _, _ -> }

    // 2026-07-14 新增：下拉刷新状态（100dp 最大高度，60dp 刷新阈值）
    val pullRefreshState = rememberPullRefreshStateHolder(
        maxPullHeight = 100.dp,
        refreshThreshold = 60.dp,
        onRefresh = onRefresh
    )

    // 刷新完成时回弹 pullOffset
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) pullRefreshState.onRefreshComplete()
    }

    // 兜底超时回弹：监测 PULLING/RELEASING 状态持续 200ms 无新事件
    // 与 InspirationScreen 完全一致的兜底逻辑
    LaunchedEffect(pullRefreshState.state, pullRefreshState.pullOffset) {
        if (pullRefreshState.state == PullRefreshState.PULLING ||
            pullRefreshState.state == PullRefreshState.RELEASING) {
            kotlinx.coroutines.delay(200)
            if (pullRefreshState.state == PullRefreshState.PULLING ||
                pullRefreshState.state == PullRefreshState.RELEASING) {
                pullRefreshState.onRelease(forceResetFromReleasing = true)
            }
        }
    }

    // 外层 Box：承载 nestedScrollConnection 与柯基指示器
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
            // 兜底监听"松手"事件：解决列表底部快速下滑 / 慢速下拉
            // 放手时 onPreFling 不会被触发的卡住问题
            .pointerInput(pullRefreshState) {
                awaitEachGesture {
                    // 关键：使用 Initial pass 监听 down 事件
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    // 关键：使用 Final pass 等待 up 事件
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    // 手指完全抬起 → 触发松手处理
                    pullRefreshState.onRelease()
                }
            }
    ) {
        // 空白区 + 居中奔跑柯基（铺满宽度，高度=pullOffset）
        CorgiPullRefreshIndicator(
            pullOffset = pullRefreshState.pullOffset,
            state = pullRefreshState.state,
            maxPullHeightPx = pullRefreshState.maxPullHeightPx,
            refreshThresholdPx = pullRefreshState.refreshThresholdPx,
            modifier = Modifier.fillMaxWidth()
        )

        // 内层 Box：列表整体下移 pullOffset
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = pullRefreshState.pullOffset }
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    // 与待办页 ZonedReorderableLazyColumn 保持完全一致(都加 8dp 水平 padding)
                    // - 让 SectionHeader 距屏幕左侧 = 8dp = 24px(与 PinnedSectionHeader 一致)
                    // - 卡片距屏幕左侧 = 8dp + SwipeableTodoBox modifier.padding(1.dp) = 9dp(与待办页一致)
                    .padding(horizontal = 8.dp),
                // 不再加水平 padding(原 20dp),让 SectionHeader 与待办页 PinnedSectionHeader
                // 距离屏幕左侧完全一致(都是 16dp,来自 CollapsibleSectionHeader 内部 padding)
                // 卡片(SwipeableTodoBox)的位置通过传 modifier = Modifier.padding(horizontal=20.dp) 保持
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
        // 2026-07-14 新增：置顶卡（仅在 pinnedDate != null 时渲染，位于所有分组之上）
        pinnedDate?.let { pinned ->
            item(key = "pinned_${pinned.id}") {
                // 2026-07-14 新增：已归档+置顶的卡使用"取消归档"按钮（与 EXPIRED 分组卡片一致）
                val isPinnedArchived = pinned.isArchived
                SwipeableTodoBox(
                    isExpanded = expandedDateId == pinned.id,
                    isPinned = true,
                    // 2026-07-14 新增：批量模式下禁用置顶卡的左滑操作
                    isEnabled = !isBatchMode,
                    onExpandChange = { expanded ->
                        onSetExpanded(if (expanded) pinned.id else null)
                    },
                    onPinClick = { onUnpin(pinned.id) },
                    // 2026-07-14 修改：已归档+置顶的卡点击"取消归档"按钮调用 onUnarchive
                    //            未归档+置顶的卡点击"归档"按钮调用 onArchive
                    onArchiveClick = {
                        if (isPinnedArchived) onUnarchive(pinned.id) else onArchive(pinned.id)
                    },
                    onDeleteClick = { onDelete(pinned.id) },
                    modifier = Modifier.padding(1.dp),
                    customButtons = listOf(
                        SwipeButtonConfig(
                            label = "取消置顶",
                            backgroundColorRes = R.color.ui_primary,
                            icon = Icons.Outlined.PushPin,
                            zIndex = 3f,
                            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                            actionType = SwipeActionType.PIN
                        ),
                        // 2026-07-14 修改：已归档+置顶的卡显示"取消归档"按钮（蓝色 + Unarchive 图标）
                        //            未归档+置顶的卡显示"归档"按钮（蓝色 + Archive 图标）
                        SwipeButtonConfig(
                            label = if (isPinnedArchived) "取消归档" else "归档",
                            backgroundColorRes = R.color.ui_archive,
                            icon = if (isPinnedArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                            zIndex = 2f,
                            shape = RoundedCornerShape(0.dp),
                            actionType = if (isPinnedArchived) SwipeActionType.UNARCHIVE else SwipeActionType.ARCHIVE
                        ),
                        SwipeButtonConfig(
                            label = "删除",
                            backgroundColorRes = R.color.ui_swipe_delete,
                            icon = Icons.Outlined.Delete,
                            zIndex = 1f,
                            shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                            actionType = SwipeActionType.DELETE
                        )
                    )
                ) { isClickBlocked ->
                    PinnedDateCard(
                        date = pinned,
                        nowMs = nowMs,
                        isClickBlocked = isClickBlocked,
                        onClick = { onCardClick(pinned) }
                    )
                }
            }
        }

        DateGroup.values().forEach { group ->
            val dates = groupedDates[group].orEmpty()
            if (dates.isEmpty()) return@forEach
            val isExpanded = expandState[group] ?: false
            // 分组头 - 用 ReorderableItem 包裹以与待办页 PinnedSectionHeader 完全对齐
            item(key = "header_${group.name}") {
                // 完全模拟待办页 PinnedSectionHeader 渲染结构:
                //   ReorderableItem(enabled=true) > Box(longPressDraggableHandle enabled=false) > 内容
                // enabled=true 让 item 加入 reorderableKeys(可作为 onMove 的 to 目标,与其他项跨界时识别边界),
                // longPressDraggableHandle(enabled=false) 保证 SectionHeader 本身不可被拖拽。
                ReorderableItem(
                    state = reorderableState,
                    key = "date_header_${group.name}",
                    enabled = true
                ) {
                    // longPressDraggableHandle 是 ReorderableCollectionItemScope 接口的
                    // Modifier 扩展,正确调用方式为 Modifier.longPressDraggableHandle(...)
                    Box(
                        modifier = Modifier.longPressDraggableHandle(enabled = false)
                    ) {
                        DateSectionHeader(
                            group = group,
                            count = dates.size,
                            isExpanded = isExpanded,
                            onToggle = {
                                expandState[group] = !isExpanded
                            }
                        )
                    }
                }
            }
            // 卡片列表（仅在分组展开时渲染）
            if (isExpanded) {
                items(dates, key = { "date_${it.id}" }) { date ->
                    SwipeableTodoBox(
                        isExpanded = expandedDateId == date.id,
                        isPinned = date.isPinned,
                        // 2026-07-14 新增：批量模式下禁用普通卡的左滑操作
                        isEnabled = !isBatchMode,
                        onExpandChange = { expanded ->
                            onSetExpanded(if (expanded) date.id else null)
                        },
                        onPinClick = {
                            if (date.isPinned) onUnpin(date.id) else onPin(date.id)
                        },
                        // 2026-07-13：根据 date.isArchived 分发到归档/取消归档
                        // - 未归档卡片：onArchive → archiveDate，触发"已归档" Snackbar
                        // - 已归档卡片：onUnarchive → unarchiveDate，静默取消归档，不弹 Snackbar
                        onArchiveClick = {
                            if (date.isArchived) onUnarchive(date.id) else onArchive(date.id)
                        },
                        onDeleteClick = { onDelete(date.id) },
                        // 卡片水平 1dp 缩进(与待办页 SwipeableTodoBox 一致: Modifier.padding(1.dp))
                        // 配合 LazyColumn.padding(horizontal = 8.dp):
                        //   - 卡片距屏幕左侧 = 8dp + 1dp = 9dp (与待办页完全一致)
                        //   - SectionHeader 距屏幕左侧 = 8dp = 24px (与 PinnedSectionHeader 一致)
                        modifier = Modifier.padding(1.dp),
                        customButtons = listOf(
                            // 置顶按钮（最左）
                            SwipeButtonConfig(
                                label = if (date.isPinned) "取消置顶" else "置顶",
                                backgroundColorRes = R.color.ui_primary,
                                // 激活态与未激活态用同一图标，靠主色 + label 文案区分
                                icon = Icons.Outlined.PushPin,
                                zIndex = 3f,
                                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                                actionType = SwipeActionType.PIN
                            ),
                            // 归档按钮（中间）
                            // 2026-07-13 优化：根据卡片是否已归档切换文案、图标与行为：
                            //   - 未归档 → 标签"归档"，图标 Archive，点击调用 onArchive(归档 + Snackbar 撤回)
                            //   - 已归档 → 标签"取消归档"，图标 Unarchive，点击调用 onUnarchive(静默取消归档)
                            SwipeButtonConfig(
                                label = if (date.isArchived) "取消归档" else "归档",
                                backgroundColorRes = R.color.ui_archive,
                                icon = if (date.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                zIndex = 2f,
                                shape = RoundedCornerShape(0.dp),
                                actionType = SwipeActionType.ARCHIVE
                            ),
                            // 删除按钮（最右）
                            SwipeButtonConfig(
                                label = "删除",
                                backgroundColorRes = R.color.ui_swipe_delete,
                                icon = Icons.Outlined.Delete,
                                zIndex = 1f,
                                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                                actionType = SwipeActionType.DELETE
                            )
                        )
                    ) { isClickBlocked ->
                        SpecialDateCard(
                            date = date,
                            nowMs = nowMs,
                            isClickBlocked = isClickBlocked,
                            onClick = { onCardClick(date) },
                            // 2026-07-14 新增：三点按钮弹窗功能参数
                            isSimpleMode = isSimpleMode,
                            isBatchMode = isBatchMode,
                            isSelected = selectedDateIds.contains(date.id),
                            onSelectClick = { onToggleSelection(date.id) }
                        )
                    }
                }
            }
        }

        // 底部留白：确保列表最后一项可滚动到 FAB 上方（与待办页/灵感页一致）
        // FAB 高度 56dp + 底部 padding 16dp = 72dp，80dp 留有少量余量
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
        }  // 闭合 LazyColumn lambda
        }  // 闭合内层 Box(graphicsLayer)
    }  // 闭合外层 Box(nestedScroll)
}  // 闭合 DateSectionsList 函数
