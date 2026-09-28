package com.corgimemo.app.ui.screens.inspiration

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.ui.components.CorgiPullRefreshIndicator
import com.corgimemo.app.ui.components.PullRefreshState
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.UnifiedEmptyState
// v2026-07-22 新增：关联列表 BottomSheet
import com.corgimemo.app.ui.components.RelationListBottomSheet
import com.corgimemo.app.ui.components.rememberPullRefreshStateHolder
import com.corgimemo.app.ui.screens.inspiration.components.InspirationDateTimePickerDialog
import com.corgimemo.app.ui.screens.inspiration.components.InspirationImageGallery
import com.corgimemo.app.ui.screens.inspiration.components.InspirationLongPressSheet
import com.corgimemo.app.ui.screens.inspiration.components.TagPickerSheet
import com.corgimemo.app.ui.screens.inspiration.components.TimelineInspirationItem
import com.corgimemo.app.viewmodel.InspirationViewModel
import androidx.compose.material3.rememberModalBottomSheetState

/**
 * 灵感记录列表页面（时间线版）
 *
 * 展示所有灵感记录的时间线列表，支持搜索、分组展示和快速添加功能。
 * 顶部导航栏和侧滑导航栏由 MainScreen 统一管理。
 *
 * 功能说明：
 * - 搜索栏：支持关键词实时搜索灵感
 * - 时间线布局：左侧日期列 + 右侧内容区
 * - 置顶区域：置顶灵感显示在最顶部
 * - 长按操作：置顶/标签/改日期/删除
 * - 日历弹窗：从顶部日期点击展开，查看每天的灵感
 * - FAB按钮：跳转到灵感编辑页
 * - 删除撤销：删除灵感时显示带撤销的 Snackbar 提示
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 * @param snackbarHostState 共享的 Snackbar 状态（由 MainScreen 顶层 Scaffold 创建并传入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: InspirationViewModel = hiltViewModel(),
    snackbarHostState: SnackbarHostState? = null
) {
    val displayItems by viewModel.filteredDisplayInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val selectedInspirationIds by viewModel.selectedInspirationIds.collectAsState()
    val hideDetails by viewModel.hideDetails.collectAsState()
    /** 各灵感的关联数量映射（v2026-07-21 新增，供首页卡片显示 🔗×N） */
    val relationCountMap by viewModel.relationCountMap.collectAsState()
    /** v2026-08-24 新增：各灵感的图片路径映射（供首页卡片 + 日期时间修改弹窗预览显示） */
    val imagePathsMap by viewModel.imagePathsMap.collectAsState()
    /** 灵感删除撤销状态（用于触发 Snackbar 提示） */
    val pendingDeletedInspiration by viewModel.pendingDeletedInspiration.collectAsState()
    val pendingBatchDeletedInspirations by viewModel.pendingBatchDeletedInspirations.collectAsState()

    // 弹窗状态
    var showLongPressSheet by remember { mutableStateOf(false) }
    var longPressedInspiration by remember { mutableStateOf<Inspiration?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    /**
     * 关联列表 BottomSheet 状态（v2026-07-22 新增）
     *
     * - showRelationListSheet: true 时显示 BottomSheet
     * - relationListSourceId: 当前展示的源 inspiration.id（null = 关闭中）
     *
     * 触发链路与 HomeScreen 一致：
     * 1. TimelineInspirationItem.onRelationCountClick → sourceId + show=true
     * 2. RelationListBottomSheet 弹出 → RelationListViewModel.loadRelations
     * 3. 用户点 × 解绑 → onUnlinked() → viewModel.refreshRelationCountsOnDemand()
     * 4. 关闭弹窗 → onDismiss() → 状态清空
     */
    var showRelationListSheet by remember { mutableStateOf(false) }
    var relationListSourceId by remember { mutableStateOf<Long?>(null) }

    // 图片全屏预览状态
    var showImageGallery by remember { mutableStateOf(false) }
    var galleryImagePaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryInitialIndex by remember { mutableStateOf(0) }

    // 批量模式下拦截返回键
    if (isBatchMode) {
        BackHandler {
            viewModel.exitBatchMode()
        }
    }

    /**
     * 监听单个灵感删除事件，显示 Snackbar（带撤销按钮）
     *
     * 关键：snackbarHostState 为 null 时（无主 Scaffold）直接 return，不显示提示
     * 行为：用户点撤销 → undoDeleteInspiration 移回灵感表；否则 5s 后 Snackbar 自动消失
     */
    LaunchedEffect(pendingDeletedInspiration) {
        pendingDeletedInspiration?.let {
            val host = snackbarHostState ?: return@let
            val result = host.showSnackbar(
                message = "已删除 1 条灵感",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDeleteInspiration()
            } else {
                viewModel.clearPendingDeletedInspiration()
            }
        }
    }

    /**
     * 监听批量删除灵感事件，显示 Snackbar（带全部撤销按钮）
     */
    LaunchedEffect(pendingBatchDeletedInspirations) {
        pendingBatchDeletedInspirations?.let { list ->
            if (list.isNotEmpty()) {
                val host = snackbarHostState ?: return@let
                val result = host.showSnackbar(
                    message = "已删除 ${list.size} 条灵感",
                    actionLabel = "全部撤销",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoBatchDeleteInspiration()
                } else {
                    viewModel.clearPendingBatchDeletedInspiration()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索框（保留现有）
            SearchBar(
                query = searchQuery,
                onQueryChange = { newQuery ->
                    viewModel.search(newQuery)
                },
                onClear = {
                    viewModel.clearSearch()
                },
                placeholder = "搜索灵感...",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = dimensionResource(com.corgimemo.app.R.dimen.ui_search_bar_bottom_margin))
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 内容区域
            if (!isDataInitialized) {
                InspirationSkeleton()
            } else if (displayItems.isEmpty()) {
                UnifiedEmptyState(
                    icon = "",
                    title = "还没有灵感记录~",
                    subtitle = "点击右下角按钮记录你的第一个灵感吧！",
                    ctaText = "记录灵感",
                    onCtaClick = onFabClick,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val isRefreshing by viewModel.isRefreshing.collectAsState()
                val pullRefreshState = rememberPullRefreshStateHolder(
                    maxPullHeight = 100.dp,
                    refreshThreshold = 60.dp,
                    onRefresh = { viewModel.onRefresh() }
                )

                // 刷新完成时回弹 pullOffset
                LaunchedEffect(isRefreshing) {
                    if (!isRefreshing) pullRefreshState.onRefreshComplete()
                }

                // 兜底超时回弹：监测 PULLING/RELEASING 状态持续 200ms 无新事件
                // 解决 pointerInput 兜底仍偶尔失效的场景（部分 Android 版本 / 设备上
                // up 事件传递不可靠）。当 state 在 PULLING 或 RELEASING 时启动延迟任务，
                // 期间 state 或 pullOffset 任何变化都会重启协程（key 变化），
                // 用户继续操作时不会误触发
                // 关键：必须同时处理 RELEASING 状态（卡死恢复路径）
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
                                // 父组件在 Initial pass 比子组件先收到事件，可靠性最高
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                // 关键：使用 Final pass 等待 up 事件
                                // Final pass 是兜底 pass，即使 LazyColumn 在 Main pass
                                // 消费了 up 事件，pointerInput 仍能在 Final pass 收到
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
                    // V5.4 修复：graphicsLayer 加 this.clip = false，补全「8 层 clip=false 链」第 1 层
                    // - 根因：graphicsLayer {} 一旦调用就会创建 RenderNode，RenderNode 默认 clip=true
                    //   之前只设了 translationY 未设 clip=false，导致 RenderNode 把顶卡左滑时超出
                    //   屏幕左边的部分硬裁剪掉（视觉左边缘 = 88dp - 180dp = -92dp）
                    //   而右滑最远 88+180=268dp 仍在屏幕内 360dp 内，所以右侧无裁剪
                    //   → 表现：左滑被裁、右滑不裁，与用户截图一致
                    // - 修复：translationY 仍保留（pullRefresh 行为不变），仅补 this.clip = false
                    // - 8 层链：L284 Box(本层) → LazyColumn → TimelineInspirationItem 外 Box
                    //         → 文本 Column → 图片行 outer Box → 图片行 inner Box
                    //         → SwipeableImageStack Stage Box → 卡片 Box
                    // - 视觉不变：Stage 起点 88dp / 顶卡静止位置 / 1·N 角标 / 展开按钮 / 文本列 / 收起按钮 全部不变
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.clip = false
                                translationY = pullRefreshState.pullOffset
                            }
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                // V6.0 修复：Modifier 顺序 + 关闭整个 LazyColumn RenderNode 裁剪
                                .graphicsLayer { this.clip = false },
                            // V6.0 修复：取消 LazyColumn contentPadding(start=18.dp)
                            // - 根因：V5.9 使用 contentPadding(start=18.dp, end=18.dp) 导致 item 的左边界 = 18dp（50px），
                            //   当顶卡左滑进入 0~50px（contentPadding 空白区）时，顶卡左边缘已超出 item 左边界，
                            //   即使 graphicsLayer clip=false，也可能被 LazyColumn viewport 内部机制或 View 层裁剪
                            // - 修复：start=0.dp，让 item 左边界 = 屏幕左边界（0px），
                            //   然后在 item 内部用 padding(start=18.dp) 把内容推到 18dp 位置，视觉完全一致，
                            //   但顶卡左滑进入 0~50px 时仍在 item 内部（合法范围），不被裁剪
                            // - end=18.dp 保留（右滑无裁剪问题）
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 0.dp,
                                end = 18.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(
                                items = displayItems,
                                key = { "inspiration_${it.inspiration.id}" }
                            ) { item ->
                                val inspiration = item.inspiration
                                val tags = viewModel.decodeTags(inspiration.tags)
                                // v2026-08-24 修复灵感图片不可见 bug：
                                // 改从 imagePathsMap 读，替代原 viewModel.decodePaths(inspiration.imagePaths)
                                // （原方式永远返回空，因为 saveInspiration() 已将 imagePaths 字段置空）
                                val imagePaths = imagePathsMap[inspiration.id] ?: emptyList()
                                val formattedTime = viewModel.formatTime(inspiration.createdAt)

                                // V6.0 修复：item 内部加 padding(start=18.dp)，还原视觉位置
                                // - LazyColumn contentPadding(start) 已取消，item 左边界 = 屏幕左边界 0px
                                // - 用内部 padding(start=18.dp) 把内容向右推到 18dp，与原视觉完全一致
                                // - 关键收益：顶卡左滑 finalLeft 在 0~50px 时，仍在 item 内部（合法范围）
                                //   配合 graphicsLayer clip=false，不会被任何层级裁剪
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 18.dp)  // V6.0 新增：替代 LazyColumn contentPadding(start=18.dp)
                                        .graphicsLayer { this.clip = false }
                                ) {
                                    TimelineInspirationItem(
                                        inspiration = inspiration,
                                        tags = tags,
                                        imagePaths = imagePaths,
                                        formattedTime = formattedTime,
                                        showDate = item.showDate,
                                        isPinnedItem = item.isPinned,
                                        hideDetails = hideDetails,
                                        isBatchMode = isBatchMode,
                                        isSelected = selectedInspirationIds.contains(inspiration.id),
                                        /** v2026-07-21 新增：传入关联数量，在标签右侧显示 🔗×N */
                                        relationCount = relationCountMap[inspiration.id] ?: 0,
                                        /** v2026-07-22 新增：点击 🔗×N 徽章弹出关联列表 BottomSheet */
                                        onRelationCountClick = {
                                            relationListSourceId = inspiration.id
                                            showRelationListSheet = true
                                        },
                                        onClick = {
                                            if (isBatchMode) {
                                                viewModel.toggleSelection(inspiration.id)
                                            } else {
                                                // v2.8 改为先进入展示页，再决定复制/编辑/分享
                                                navController.navigate(
                                                    com.corgimemo.app.ui.navigation.Screen.InspirationViewWithId
                                                        .createRoute(inspiration.id)
                                                )
                                            }
                                        },
                                        onLongClick = if (isBatchMode) {
                                            {}
                                        } else {
                                            {
                                                longPressedInspiration = inspiration
                                                showLongPressSheet = true
                                            }
                                        },
                                        onImageClick = { index ->
                                            // 点击图片：打开全屏图片预览（不进入编辑页）
                                            galleryImagePaths = imagePaths
                                            galleryInitialIndex = index
                                            showImageGallery = true
                                        }
                                    )
                                }
                            }

                            // 底部留白
                            item {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }

        // 加载指示器
        // 注意：原先使用 viewModel.isLoading + CircularProgressIndicator 居中展示。
        // 由于 Room Flow 的 collect 永不结束，导致 _isLoading 永远为 true，
        // 圆圈会一直旋转遮住列表内容。已移除该状态，改用 isDataInitialized
        // 控制骨架屏作为统一的加载占位。

        // FAB 按钮（批量模式下隐藏）
        if (!isBatchMode) {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "记录灵感",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 图片全屏预览（在 Box 内部渲染，确保 fillMaxSize 能正确占满屏幕）
        // 之前放在 Box 外部，但 MainScreen 将 InspirationScreen 放在 Column 中，
        // Box(fillMaxSize) 占满空间后 Column 无剩余高度，导致 Gallery 高度为 0 不可见。
        if (showImageGallery) {
            InspirationImageGallery(
                imagePaths = galleryImagePaths,
                initialIndex = galleryInitialIndex,
                onDismiss = {
                    showImageGallery = false
                    galleryImagePaths = emptyList()
                    galleryInitialIndex = 0
                }
            )
        }
    }

    // 长按操作面板
    if (showLongPressSheet && longPressedInspiration != null) {
        InspirationLongPressSheet(
            isPinned = longPressedInspiration!!.isPinned,
            onPinClick = {
                viewModel.togglePin(longPressedInspiration!!.id)
                showLongPressSheet = false
                longPressedInspiration = null
            },
            onTagClick = {
                // 关闭长按面板，打开标签管理弹窗（保留 longPressedInspiration 供 TagPickerSheet 使用）
                showLongPressSheet = false
                showTagPicker = true
            },
            onDateClick = {
                // 关闭长按面板，打开日期时间选择器
                showLongPressSheet = false
                showDateTimePicker = true
            },
            onDeleteClick = {
                showDeleteConfirm = true
                showLongPressSheet = false
            },
            onDismiss = {
                showLongPressSheet = false
                longPressedInspiration = null
            }
        )
    }

    // 删除确认对话框
    if (showDeleteConfirm && longPressedInspiration != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除这条灵感吗？删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteInspiration(longPressedInspiration!!.id)
                    showDeleteConfirm = false
                    longPressedInspiration = null
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 批量删除确认弹窗
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中项") },
            text = {
                Text("确定要删除已选择的 ${selectedInspirationIds.size} 条灵感吗？\n此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.batchDeleteInspirations()
                    showBatchDeleteDialog = false
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 标签管理弹窗
    if (showTagPicker && longPressedInspiration != null) {
        val tagSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val savedTags by viewModel.savedTags.collectAsState()
        TagPickerSheet(
            sheetState = tagSheetState,
            tags = viewModel.decodeTags(longPressedInspiration!!.tags),
            savedTags = savedTags,
            onTagsChange = { newTags ->
                viewModel.updateTags(longPressedInspiration!!.id, newTags)
            },
            onDismiss = {
                showTagPicker = false
                longPressedInspiration = null
            }
        )
    }

    // 日期时间修改弹窗
    // 使用 InspirationDateTimePickerDialog 专用弹窗，复用 InspirationCalendarDialog 的日历区和灵感区组件。
    // 弹窗内部使用 Dialog 窗口层级渲染，逃脱父容器约束，实现整屏遮罩覆盖。
    if (showDateTimePicker && longPressedInspiration != null) {
        val inspiration = longPressedInspiration!!
        // 弹窗出现时隐藏键盘（作用于原页面的输入框焦点）
        androidx.compose.ui.platform.LocalFocusManager.current.clearFocus()

        InspirationDateTimePickerDialog(
            inspiration = inspiration,
            onDismiss = {
                showDateTimePicker = false
                longPressedInspiration = null
            },
            onConfirm = { dateMillis, _, _ ->
                viewModel.updateInspirationDateTime(inspiration.id, dateMillis)
                showDateTimePicker = false
                longPressedInspiration = null
            },
            // v2026-08-24 新增：图片路径从 content_blocks 表读取，让日期时间修改弹窗也能显示图片
            imagePaths = imagePathsMap[inspiration.id] ?: emptyList()
        )
    }

    /**
     * 关联列表 BottomSheet（v2026-07-22 新增）
     *
     * 与 HomeScreen 的 RelationListBottomSheet 共用同一组件：
     * - sourceType = "inspiration"（灵感作为关联源）
     * - 跳详情时 inspiration → InspirationView，其他类型复用同样路由
     *
     * 注意：因 InspirationScreen 是 Box(fillMaxSize) 结构，BottomSheet
     * 通过 Portal 自动覆盖整个屏幕，无需额外包 Box。
     */
    relationListSourceId?.let { sourceId ->
        RelationListBottomSheet(
            visible = showRelationListSheet,
            sourceType = "inspiration",
            sourceId = sourceId,
            groupId = 0,
            onItemClick = { cardType, cardId ->
                showRelationListSheet = false
                relationListSourceId = null
                when (cardType) {
                    "todo" -> navController.navigate(
                        com.corgimemo.app.ui.navigation.Screen.TodoEditWithId.withArgs(cardId.toString())
                    )
                    "inspiration" -> navController.navigate(
                        com.corgimemo.app.ui.navigation.Screen.InspirationViewWithId.createRoute(cardId)
                    )
                    "date" -> navController.navigate(
                        com.corgimemo.app.ui.navigation.Screen.SpecialDateDetailWithId.createRoute(cardId)
                    )
                }
            },
            onUnlinked = {
                // 解除关联后重新查询灵感页 relationCountMap
                viewModel.refreshRelationCountsOnDemand()
            },
            onDismiss = {
                showRelationListSheet = false
                relationListSourceId = null
            }
        )
    }
}
