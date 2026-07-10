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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import com.corgimemo.app.ui.components.rememberPullRefreshStateHolder
import com.corgimemo.app.ui.screens.inspiration.components.InspirationDateTimePickerDialog
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
 *
 * @param navController 导航控制器，用于页面跳转
 * @param onFabClick FAB按钮点击回调（由 MainScreen 传入）
 * @param viewModel 灵感视图模型（通过 Hilt 自动注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspirationScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: InspirationViewModel = hiltViewModel()
) {
    val displayItems by viewModel.filteredDisplayInspirations.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val selectedInspirationIds by viewModel.selectedInspirationIds.collectAsState()
    val hideDetails by viewModel.hideDetails.collectAsState()

    // 弹窗状态
    var showLongPressSheet by remember { mutableStateOf(false) }
    var longPressedInspiration by remember { mutableStateOf<Inspiration?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // 批量模式下拦截返回键
    if (isBatchMode) {
        BackHandler {
            viewModel.exitBatchMode()
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
                InspirationSkeleton(groupCount = 1, itemsPerGroup = 2)
            } else if (displayItems.isEmpty()) {
                UnifiedEmptyState(
                    icon = "💡",
                    title = "还没有灵感记录~",
                    subtitle = "点击右下角按钮记录你的第一个灵感吧！",
                    ctaText = "💡 记录灵感",
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = pullRefreshState.pullOffset }
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(
                                items = displayItems,
                                key = { "inspiration_${it.inspiration.id}" }
                            ) { item ->
                                val inspiration = item.inspiration
                                val tags = viewModel.decodeTags(inspiration.tags)
                                val imagePaths = viewModel.decodePaths(inspiration.imagePaths)
                                val formattedTime = viewModel.formatTime(inspiration.createdAt)

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
                                    }
                                )
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
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

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
                // 关闭长按面板，打开日期时间选择器（保留 longPressedInspiration 供 ReminderPickerBottomSheet 使用）
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
            }
        )
    }
}
