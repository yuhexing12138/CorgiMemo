package com.corgimemo.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.corgimemo.app.ui.components.safeAreaForBottomBar /** 安全区域内边距：底栏导航栏*/
import com.corgimemo.app.ui.components.SwipeableTodoBox
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.Scaffold  // 已移除：避免与 MainScreen 的外层 Scaffold 嵌套
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.animation.BehaviorType
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.animation.HapticFeedbackManager
import com.corgimemo.app.animation.InteractionType
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.backup.exporter.ImageExporter
import com.corgimemo.app.backup.exporter.ShareIntentHelper
import com.corgimemo.app.ui.components.AchievementUnlockDialog
import com.corgimemo.app.ui.components.CorgiNamerDialog
import com.corgimemo.app.ui.components.UnifiedEmptyState
import com.corgimemo.app.ui.components.FirstTimeGuideOverlay
import com.corgimemo.app.ui.components.SolarTermCard
import com.corgimemo.app.ui.components.TodoListItem
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.CorgiPullToRefreshIndicator
import com.corgimemo.app.ui.components.SortBottomSheet
import com.corgimemo.app.ui.components.ReorderableLazyColumn
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.corgimemo.app.viewmodel.CelebrationLevel
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val filteredTodos by viewModel.filteredTodos.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val corgiData by viewModel.corgiData.collectAsState()
    val showNamerDialog by viewModel.showNamerDialog.collectAsState()
    val _currentPose by viewModel.currentPose.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val _currentOutfit by viewModel.currentOutfit.collectAsState()
    val currentHoliday by viewModel.currentHoliday.collectAsState()
    val currentSolarTerm by viewModel.currentSolarTerm.collectAsState()
    val showSolarTermCard by viewModel.showSolarTermCard.collectAsState()
    val _greeting by viewModel.greeting.collectAsState()

    // 实际显示的装扮：节日装扮优先级高于用户选择的装扮
    val _effectiveOutfit = currentHoliday?.outfitId ?: _currentOutfit

    val celebrationState by viewModel.celebrationState.collectAsState()
    val showLevelUp by viewModel.showLevelUp.collectAsState()
    val showAchievementUnlock by viewModel.showAchievementUnlock.collectAsState()
    val showConsecutiveBonus by viewModel.showConsecutiveBonus.collectAsState()
    val currentBehavior by viewModel.currentBehavior.collectAsState()
    val showMissedYouDialog by viewModel.showMissedYouDialog.collectAsState()
    val missedYouDays by viewModel.missedYouDays.collectAsState()
    val showOutfitSheet by viewModel.showOutfitSheet.collectAsState()
    val moodChangeMessage by viewModel.moodChangeMessage.collectAsState()
    val todoActionMessage by viewModel.todoActionMessage.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val pendingDeletedTodo by viewModel.pendingDeletedTodo.collectAsState()
    val pendingBatchDeletes by viewModel.pendingBatchDeletes.collectAsState()
    val pendingCompleteTodo by viewModel.pendingCompleteTodo.collectAsState()
    /** 拖拽排序多级撤销历史栈 */
    val reorderUndoStack by viewModel.reorderUndoStack.collectAsState()

    // 子任务进度映射
    val subTaskProgressMap by viewModel.subTaskProgressMap.collectAsState()

    // 子任务列表映射
    val subTasksMap by viewModel.subTasksMap.collectAsState()

    // 展开状态
    val expandedTodos by viewModel.expandedTodos.collectAsState()

    // 批量选择模式状态
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val selectedTodoIds by viewModel.selectedTodoIds.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()

    /** 排序方式状态 */
    val sortType by viewModel.sortType.collectAsState()

    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchMoveDialog by remember { mutableStateOf(false) }

    /**
     * 单个待办删除二次确认状态（长按菜单删除走此流程）
     *
     * 区别于批量删除的 AlertDialog（showBatchDeleteDialog），这是单个待办
     * 通过长按菜单触发"删除"后的二次确认，符合 spec 中"删除需二次确认"的要求。
     *
     * 状态：null = 不显示；非 null = 显示弹窗并传入要删除的 todoId
     */
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    /** 左滑互斥展开状态：同时只允许一张卡片展开操作层 */
    var swipeExpandedTodoId by remember { mutableStateOf<Long?>(null) }

    /** 快速添加待办 BottomSheet 状态 */
    var showQuickAddSheet by remember { mutableStateOf(false) }

    /** 排序弹窗 BottomSheet 状态 */
    var showSortSheet by remember { mutableStateOf(false) }
    val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** 首次引导状态 */
    var showFirstTimeGuide by remember { mutableStateOf(false) }

    /** A/B 测试组别 */
    var abGroup by remember { mutableStateOf("A") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 新成就解锁弹窗状态
    var currentUnlockedAchievement by remember {
        mutableStateOf<com.corgimemo.app.data.model.Achievement?>(null)
    }

    // 监听新的成就解锁事件
    LaunchedEffect(hapticEnabled) {
        viewModel.achievementUnlockEvents.collect { achievement ->
            // 触发震动反馈：成就解锁长震动
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.ACHIEVEMENT_UNLOCK,
                enabled = hapticEnabled
            )
            // 触发柯基庆祝动画
            viewModel.setPoseForCelebrating()
            // 显示成就解锁弹窗
            currentUnlockedAchievement = achievement
        }
    }

    moodChangeMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearMoodChangeMessage()
        }
    }

    todoActionMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearTodoActionMessage()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshGreetingIfNeeded()

        /** 初始化 A/B 测试分组 */
        val corgiPrefs = com.corgimemo.app.data.local.datastore.CorgiPreferences.getInstance(context)
        abGroup = corgiPrefs.getOrAssignAbGroup()

        /** 检查是否需要显示首次引导 */
        val isFirstGuideShown = corgiPrefs.getFirstGuideShown()
        if (!isFirstGuideShown && filteredTodos.isEmpty()) {
            showFirstTimeGuide = true
        }
    }

    val outfitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val quickAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** 监听单个待办删除事件，显示 Snackbar（带标题）*/
    LaunchedEffect(pendingDeletedTodo) {
        pendingDeletedTodo?.let { todo ->
            val result = snackbarHostState.showSnackbar(
                message = "☑️ '${todo.title}' 已删除",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoDelete()
            }
        }
    }

    /** 监听批量删除事件，显示 Snackbar */
    LaunchedEffect(pendingBatchDeletes) {
        pendingBatchDeletes?.let { todos ->
            if (todos.isNotEmpty()) {
                val result = snackbarHostState.showSnackbar(
                    message = "☑️ 已删除 ${todos.size} 个待办",
                    actionLabel = "全部撤销",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    viewModel.undoBatchDelete()
                }
            }
        }
    }

    /** 监听待办完成事件，显示 Snackbar（支持撤销）*/
    LaunchedEffect(pendingCompleteTodo) {
        pendingCompleteTodo?.let { (todo, _) ->
            val result = snackbarHostState.showSnackbar(
                message = "✅ '${todo.title}' 已完成",
                actionLabel = "撤销",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoComplete()
            }
        }
    }

    /** 监听拖拽排序事件，显示 Snackbar（支持多级撤销）*/
    LaunchedEffect(reorderUndoStack.size) {
        if (reorderUndoStack.isNotEmpty()) {
            val result = snackbarHostState.showSnackbar(
                message = "已调整排序顺序 (${reorderUndoStack.size} 层可撤销)",
                actionLabel = "撤销",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoReorder()
            }
        }
    }

    // 批量模式下拦截返回键
    if (isBatchMode) {
        BackHandler {
            viewModel.exitBatchMode()
        }
    }

    // 使用 Box 作为根容器，确保所有子元素正确堆叠
    Box(modifier = Modifier.fillMaxSize()) {
        // 浮动操作按钮（FAB）—— 与灵感/日期页统一
        // ⚠️ 必须使用 zIndex 确保 FAB 在 Column 内容区之上，
        //    否则 SwipeRefresh/ReorderableLazyColumn(fillMaxSize) 会拦截 FAB 的触摸事件
        if (!isBatchMode) {
            FloatingActionButton(
                onClick = {
                    viewModel.onUserInteraction()
                    viewModel.setPoseForCreating()
                    onFabClick()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    /** 小间距即可：父容器 Column 已通过 paddingValues 避开导航栏区域 */
                    .padding(end = 20.dp, bottom = 16.dp)
                    /** 提升 z-index 到最上层，避免被 fillMaxSize() 的内容列遮挡 */
                    .zIndex(10f)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "添加待办",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

            // 主内容区域使用 Column 垂直排列
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部栏区域
                if (isBatchMode) {
                    TopAppBar(
                        title = {
                            Text(
                                text = "已选择 ${selectedTodoIds.size} 项",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { viewModel.exitBatchMode() }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "关闭批量模式",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    if (selectedTodoIds.size == filteredTodos.size) {
                                        viewModel.clearSelection()
                                    } else {
                                        viewModel.selectAll()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SelectAll,
                                    contentDescription = "全选",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }

                // 主内容区域：使用 weight(1f) 填满剩余空间
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (showSolarTermCard && currentSolarTerm != null) {
                        AnimatedVisibility(
                            visible = showSolarTermCard,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 })
                        ) {
                            SolarTermCard(
                                solarTerm = currentSolarTerm!!,
                                onShare = {
                                    Toast.makeText(context, "分享功能开发中...", Toast.LENGTH_SHORT).show()
                                },
                                onDismiss = {
                                    viewModel.dismissSolarTermCard()
                                }
                            )
                        }
                    }

                    val searchQuery by viewModel.searchQuery.collectAsState()

                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { newQuery ->
                            viewModel.updateSearchQuery(newQuery)
                        },
                        onClear = {
                            viewModel.clearSearch()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        trailingIcon = {
                            /** 排序按钮（与清空按钮互斥显示） */
                            IconButton(
                                onClick = { showSortSheet = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Text(
                                    text = "📊",
                                    fontSize = 20.sp,
                                    color = Color(0xFFFF9A5C) // 暖橙色
                                )
                            }
                        }
                    )

                    // 柯基陪伴区已分离为悬浮按钮，此处不再显示

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterButton(
                            text = "全部",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.ALL,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.ALL)
                            }
                        )
                        FilterButton(
                            text = "待办",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.PENDING,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.PENDING)
                            }
                        )
                        FilterButton(
                            text = "已完成",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.COMPLETED,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.COMPLETED)
                            }
                        )
                    }

                    /**
                     * 内容区域显示逻辑：
                     * 1. 数据未初始化 → 显示加载指示器（避免闪烁）
                     * 2. 数据已初始化 + 列表为空 → 显示空状态
                     * 3. 数据已初始化 + 列表有内容 → 显示列表
                     */
                    if (!isDataInitialized) {
                        // 数据未初始化：显示页面专属骨架屏，避免从空列表闪烁到有数据
                        TodoSkeleton(itemCount = 4)
                    } else if (filteredTodos.isEmpty()) {
                        UnifiedEmptyState(
                            icon = "📝",
                            title = "还没有待办~",
                            subtitle = "点击下方按钮添加第一个待办吧！",
                            ctaText = "📝 添加待办",
                            onCtaClick = {
                                viewModel.onUserInteraction()
                                viewModel.setPoseForCreating()
                                onFabClick()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val isRefreshing by viewModel.isRefreshing.collectAsState()
                        val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isRefreshing)

                        SwipeRefresh(
                            state = swipeRefreshState,
                            onRefresh = { viewModel.onRefresh() },
                            indicator = { _, _ ->
                                CorgiPullToRefreshIndicator(
                                    isRefreshing = isRefreshing,
                                    pullProgress = if (isRefreshing) 1f else 0f
                                )
                            }
                        ) {
                            /**
                             * 使用增强版 ReorderableLazyColumn 替代原 LazyColumn + animatedItems
                             *
                             * **集成特性**:
                             * - ✅ 内置入场动画（fade+slideIn，与原 AnimatedLazyColumn 效果一致）
                             * - ✅ 长按触发拖拽排序
                             * - ✅ 精确索引算法（LazyListLayoutInfo 替代硬编码估算）
                             * - ✅ 拖拽释放弹性缩放动画（0.95→1.05→1.0）
                             * - ✅ 触觉反馈（节流 200ms，混合模式）
                             * - ✅ 帧率监控（TrackedAnimateFloatAsState 超阈值输出 Logcat）
                             * - ✅ 自动连接 viewModel.reorderTodos() 持久化新顺序
                             * - ✅ 多级 Undo 支持（ArrayDeque 栈，最多 10 层）
                             */
                            /** 使用项目自有的 HapticFeedbackManager 替代 Compose LocalHapticFeedback（避免版本兼容问题） */

                            ReorderableLazyColumn(
                                items = filteredTodos,
                                onReorder = { fromIndex, toIndex ->
                                    /** 用户拖拽完成后，调用 ViewModel 执行数据库更新 */
                                    viewModel.reorderTodos(fromIndex, toIndex)
                                },
                                key = { _, todo -> todo.id },  // 使用待办 ID 作为稳定键
                                onHapticFeedback = {
                                    /** 拖拽过程中使用项目自有的 HapticFeedbackManager 触发轻微震动 */
                                    com.corgimemo.app.animation.HapticFeedbackManager.performHapticFeedback(
                                        context = context,
                                        type = com.corgimemo.app.animation.InteractionType.LONG_CLICK,
                                        enabled = hapticEnabled
                                    )
                                }
                            ) { index, todo, isDragging ->
                                val category = categories.find { it.id == todo.categoryId }
                                val categoryIcon = category?.let { c ->
                                    when(c.type) {
                                        com.corgimemo.app.data.model.CategoryType.STUDY -> "📚"
                                        com.corgimemo.app.data.model.CategoryType.WORK -> "💼"
                                        com.corgimemo.app.data.model.CategoryType.LIFE -> "🏠"
                                        com.corgimemo.app.data.model.CategoryType.SPORT -> "🏃"
                                        else -> "📋"
                                    }
                                }
                                SwipeableTodoBox(
                                    isEnabled = !isBatchMode,
                                    isExpanded = swipeExpandedTodoId == todo.id,
                                    onExpandChange = { expanded ->
                                        swipeExpandedTodoId = if (expanded) todo.id else null
                                        // 同步展开状态到 ViewModel，
                                        // 让 MainScreen 控制 ModalNavigationDrawer 手势
                                        viewModel.setSwipeActionExpanded(expanded)
                                    },
                                    onShareClick = {
                                        shareTodoAsImage(context, todo, categories)
                                    },
                                    onPinClick = {
                                        viewModel.togglePin(todo.id)
                                    },
                                    onDeleteClick = {
                                        pendingDeleteId = todo.id
                                    }
                                ) {
                                    TodoListItem(
                                        todo = todo,
                                        subTaskProgress = subTaskProgressMap[todo.id],
                                        subTasks = subTasksMap[todo.id] ?: emptyList(),
                                        isExpanded = expandedTodos.contains(todo.id),
                                        isBatchMode = isBatchMode,
                                        isSelected = selectedTodoIds.contains(todo.id),
                                        categoryName = category?.name,
                                        categoryIcon = categoryIcon,
                                        onToggleComplete = { id, isChecked ->
                                            viewModel.onUserInteraction()
                                            viewModel.toggleTodoStatus(id, isChecked)
                                        },
                                        onDelete = {
                                            /**
                                             * 长按菜单触发的删除：弹出二次确认弹窗
                                             */
                                            viewModel.onUserInteraction()
                                            pendingDeleteId = it
                                        },
                                        onClick = {
                                            viewModel.onUserInteraction()
                                            navController.navigate(Screen.TodoEditWithId.withArgs(todo.id.toString()))
                                        },
                                        onLongClick = {
                                            viewModel.enterBatchMode(todo.id)
                                        },
                                        onSelectClick = {
                                            viewModel.toggleSelection(todo.id)
                                        },
                                        onShareAsImage = {
                                            shareTodoAsImage(context, todo, categories)
                                        },
                                        onToggleExpand = {
                                            viewModel.toggleExpand(todo.id)
                                        },
                                        onToggleSubTask = { subTaskId ->
                                            viewModel.onUserInteraction()
                                            viewModel.toggleSubTaskCompletion(subTaskId)
                                        },
                                        relationHint = null,
                                        /** 传递搜索关键词用于结果高亮显示 */
                                        searchQuery = searchQuery
                                    )
                                }
                            }
                        }
                        }
                    }
                }

            }

            }

        // 弹窗和覆盖层（作为外层 Box 的直接子元素）
        if (showNamerDialog) {
            CorgiNamerDialog(
                onConfirm = { name -> viewModel.saveCorgiName(name) },
                onDismiss = { viewModel.dismissNamerDialog() }
            )
        }

        showLevelUp?.let { level ->
            LevelUpDialog(
                level = level,
                onDismiss = { viewModel.dismissLevelUp() }
            )
        }

        showAchievementUnlock?.let { achievement ->
            AchievementUnlockDialog(
                achievement = achievement,
                onDismiss = { viewModel.dismissAchievementUnlock() }
            )
        }

        if (showConsecutiveBonus) {
            ConsecutiveBonusDialog(
                onDismiss = { viewModel.dismissConsecutiveBonus() }
            )
        }

        if (showMissedYouDialog) {
            MissedYouDialog(
                daysAway = missedYouDays,
                onDismiss = { viewModel.dismissMissedYouDialog() }
            )
        }

        // 新成就解锁弹窗（使用新系统）
        currentUnlockedAchievement?.let { achievement ->
            AchievementUnlockDialog(
                achievement = achievement,
                isSoundEnabled = soundEnabled,
                onDismiss = {
                    currentUnlockedAchievement = null
                    // 弹窗关闭后恢复柯基默认姿态
                    viewModel.restorePoseWithDelay(200)
                }
            )
        }

        // 对齐组件容器（包裹需要使用 align 的组件）
        Box(modifier = Modifier.fillMaxSize()) {
            // 庆祝覆盖层
            AnimatedVisibility(
                visible = celebrationState.isShowing,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 },
                modifier = Modifier.align(Alignment.Center)
            ) {
                CelebrationOverlay(
                    level = celebrationState.level,
                    message = celebrationState.message
                )
            }

            // SnackbarHost 悬浮显示
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            /** 批量操作栏（底部）- 添加系统导航栏边距，避免三键导航模式下被遮挡 */
            AnimatedVisibility(
                visible = isBatchMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    /** 使用 safeAreaForBottomBar 动态适配不同导航模式（手势导航/三键导航） */
                    modifier = Modifier.safeAreaForBottomBar()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val hasSelection = selectedTodoIds.isNotEmpty()

                        // 全部完成按钮
                        Button(
                            onClick = {
                                viewModel.batchComplete()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("已完成 ${selectedTodoIds.size} 个待办")
                                }
                            },
                            enabled = hasSelection,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "✅ 全部完成")
                        }

                        // 移动按钮
                        Button(
                            onClick = { showBatchMoveDialog = true },
                            enabled = hasSelection,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "📂 移动")
                        }

                        // 删除按钮
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = hasSelection,
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = UiColors.Error,
                                contentColor = Color.White
                            )
                        ) {
                            Text(text = "🗑️ 删除")
                        }

                        // 取消按钮
                        TextButton(
                            onClick = { viewModel.exitBatchMode() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "✕ 取消")
                        }
                    }
                }
            }

            // 覆盖层效果
            AnimatedVisibility(
                visible = celebrationState.isShowing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GlowOverlay(level = celebrationState.level)
            }
        }

        // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中项") },
            text = {
                Text(
                    "确定要删除已选择的 ${selectedTodoIds.size} 个待办吗？\n此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBatchDeleteDialog = false
                        val count = selectedTodoIds.size
                        viewModel.batchDelete()
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已删除 $count 个待办")
                        }
                    }
                ) {
                    Text("删除", color = UiColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 单个待办删除二次确认弹窗
    // 区别于批量删除弹窗（showBatchDeleteDialog），本弹窗仅针对单个待办
    pendingDeleteId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除待办") },
            text = {
                Text(
                    "确定要删除这个待办吗？\n此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteId = null
                        viewModel.deleteTodo(targetId)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("已删除")
                        }
                    }
                ) {
                    Text("删除", color = UiColors.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 批量移动分类选择对话框
    if (showBatchMoveDialog) {
        val categoryList = categories
        AlertDialog(
            onDismissRequest = { showBatchMoveDialog = false },
            title = { Text("移动到分类") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    categoryList.forEach { category ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBatchMoveDialog = false
                                    viewModel.batchMove(category.id)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("已移动到「${category.name}」")
                                    }
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                text = category.name,
                                fontSize = 16.sp
                            )
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBatchMoveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    LaunchedEffect(showOutfitSheet) {
        if (showOutfitSheet) {
            outfitSheetState.show()
        } else if (outfitSheetState.isVisible) {
            outfitSheetState.hide()
        }
    }

    if (showOutfitSheet) {
        OutfitQuickSwitchSheet(
            sheetState = outfitSheetState,
            currentOutfitId = _currentOutfit,
            unlockedOutfitsJson = corgiData?.unlockedOutfits ?: "[]",
            onSelect = { outfitId ->
                viewModel.quickSwitchOutfit(outfitId)
            },
            onDismiss = {
                viewModel.hideOutfitSheet()
            }
        )
    }

    /** 首次使用引导覆盖层 */
    if (showFirstTimeGuide) {
        FirstTimeGuideOverlay(
            onGuideCompleted = {
                showFirstTimeGuide = false
                /** 标记首次引导已完成并记录时间戳 */
                coroutineScope.launch {
                    val corgiPrefs = com.corgimemo.app.data.local.datastore.CorgiPreferences.getInstance(context)
                    corgiPrefs.setFirstGuideShown()
                    corgiPrefs.saveGuideCompletedAt(System.currentTimeMillis())
                }
                /** 触发庆祝动画和成就解锁 */
                viewModel.completeFirstGuide(context)
            },
            onFabClicked = {
                showFirstTimeGuide = false
                /** 标记首次引导已完成并导航到待办编辑页 */
                coroutineScope.launch {
                    val corgiPrefs = com.corgimemo.app.data.local.datastore.CorgiPreferences.getInstance(context)
                    corgiPrefs.setFirstGuideShown()
                }
                viewModel.onUserInteraction()
                viewModel.setPoseForCreating()
                navController.navigate(Screen.TodoEdit.route)
            },
            onTemplateSelected = { template ->
                showFirstTimeGuide = false
                /** 标记首次引导已完成并创建模板待办 */
                coroutineScope.launch {
                    val corgiPrefs = com.corgimemo.app.data.local.datastore.CorgiPreferences.getInstance(context)
                    corgiPrefs.setFirstGuideShown()
                    corgiPrefs.saveGuideCompletedAt(System.currentTimeMillis())
                }
                viewModel.createTodosFromTemplate(template)
            },
            abGroup = abGroup
        )
    }

    /** 快速添加待办 BottomSheet（悬浮柯基按钮左滑触发） */
    if (showQuickAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickAddSheet = false },
            sheetState = quickAddSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QuickAddTodoContent(
                categories = categories,
                onAddTodo = { title, categoryId, priority ->
                    viewModel.quickAddTodo(title, categoryId, priority)
                    showQuickAddSheet = false
                },
                onDismiss = { showQuickAddSheet = false }
            )
        }
    }

    /** 排序弹窗 */
    if (showSortSheet) {
        SortBottomSheet(
            sheetState = sortSheetState,
            currentSortOrder = sortType,
            onDismiss = { showSortSheet = false },
            onSortOrderSelected = { order ->
                viewModel.updateSortOrder(order)
            }
        )
    }
}

/**
 * 柯基互动卡片
 * 整合柯基展示、等级信息、经验进度、情绪状态、问候语
 *
 * @param corgiData 柯基数据
 * @param currentPose 当前姿态
 * @param currentMood 当前情绪
 * @param currentOutfit 当前装扮 ID
 * @param greeting 问候语（由 ViewModel 计算，已包含节日问候逻辑）
 * @param onLongClick 柯基长按回调（快速换装）
 * @param onInteraction 柯基被触摸时的回调（单击/双击/长按）
 * @param soundEnabled 音效开关
 * @param hapticEnabled 触觉反馈开关
 * @param modifier 修饰符
 */
@Composable
fun CorgiInteractionCard(
    corgiData: CorgiData,
    currentPose: com.corgimemo.app.animation.CorgiPose,
    currentMood: CorgiMood,
    currentOutfit: String?,
    greeting: String,
    onLongClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    soundEnabled: Boolean = true,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val levelStage = LevelManager.getLevelStage(corgiData.level)
    val (_, progress) = LevelManager.getCurrentLevelAndProgress(corgiData.experience)
    val progressText = LevelManager.getProgressText(corgiData.experience)

    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            CorgiDisplayArea(
                corgiData = corgiData,
                currentPose = currentPose,
                currentMood = currentMood,
                currentOutfit = currentOutfit,
                onLongClick = onLongClick,
                onInteraction = onInteraction,
                soundEnabled = soundEnabled,
                hapticEnabled = hapticEnabled
            )

            CorgiInfoArea(
                corgiData = corgiData,
                levelStage = levelStage,
                currentMood = currentMood,
                progress = progress,
                progressText = progressText,
                greeting = greeting
            )
        }
    }
}

/**
 * 柯基展示区域
 * 包含柯基动画和背景
 *
 * @param corgiData 柯基数据
 * @param currentPose 当前姿态
 * @param currentMood 当前情绪
 * @param currentOutfit 当前装扮 ID
 * @param onLongClick 长按回调（快速换装入口）
 * @param onInteraction 柯基被触摸时的回调（单击/双击/长按）
 * @param soundEnabled 音效开关
 * @param hapticEnabled 触觉反馈开关
 */
@Composable
fun CorgiDisplayArea(
    corgiData: CorgiData,
    currentPose: com.corgimemo.app.animation.CorgiPose,
    currentMood: CorgiMood,
    currentOutfit: String?,
    onLongClick: () -> Unit = {},
    onInteraction: () -> Unit = {},
    soundEnabled: Boolean = true,
    hapticEnabled: Boolean = true
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9A5C),
            Color(0xFFFFB366)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(gradient),
        contentAlignment = Alignment.Center
    ) {
        val sizeScale = when {
            corgiData.level >= 10 -> 1.0f
            corgiData.level >= 7 -> 1.0f
            corgiData.level >= 4 -> 0.9f
            else -> 0.8f
        }
        val baseSize = 140.dp
        val corgiSize = (baseSize.value * sizeScale).dp

        if (corgiData.level >= 10) {
            Box(
                modifier = Modifier
                    .size(corgiSize + 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            contentAlignment = Alignment.Center
        ) {
            OutfitDisplay(
                outfitId = currentOutfit,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            InteractiveCorgi(
                pose = currentPose,
                mood = currentMood,
                corgiName = corgiData.name,
                level = corgiData.level,
                outfitId = currentOutfit,
                onInteraction = { onInteraction() },
                onLongPress = { onLongClick() },
                soundEnabled = soundEnabled,
                hapticEnabled = hapticEnabled,
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
            )
        }

        com.corgimemo.app.ui.components.MoodIndicator(
            moodValue = corgiData.moodValue,
            size = 64,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )
    }
}

/**
 * 装扮显示组件
 * 在柯基顶部显示装扮图标
 *
 * @param outfitId 装扮 ID
 * @param modifier 修饰符
 */
@Composable
fun OutfitDisplay(
    outfitId: String?,
    modifier: Modifier = Modifier
) {
    if (outfitId == null || outfitId == com.corgimemo.app.animation.OutfitId.DEFAULT) return

    val icon = when (outfitId) {
        com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
        com.corgimemo.app.animation.OutfitId.TIE -> "👔"
        com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
        com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
        com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
        // 节日装扮
        com.corgimemo.app.animation.HolidayOutfitId.NEW_YEAR_HAT -> "🎉"
        com.corgimemo.app.animation.HolidayOutfitId.RED_SCARF -> "🧣"
        com.corgimemo.app.animation.HolidayOutfitId.LANTERN -> "🏮"
        com.corgimemo.app.animation.HolidayOutfitId.LABOR_HAT -> "⛑️"
        com.corgimemo.app.animation.HolidayOutfitId.DRAGON_HAT -> "🐲"
        com.corgimemo.app.animation.HolidayOutfitId.FLAG -> "🇨🇳"
        com.corgimemo.app.animation.HolidayOutfitId.MOON_DECOR -> "🌕"
        com.corgimemo.app.animation.HolidayOutfitId.SCARF -> "🧶"
        com.corgimemo.app.animation.HolidayOutfitId.CHRISTMAS_HAT -> "🎅"
        else -> null
    }

    icon?.let {
        Text(
            text = it,
            fontSize = 36.sp,
            modifier = modifier.padding(bottom = 60.dp)
        )
    }
}

/**
 * 柯基信息区域
 * 显示等级、阶段、情绪、经验进度、问候语
 *
 * @param corgiData 柯基数据
 * @param levelStage 等级阶段
 * @param currentMood 当前情绪
 * @param progress 经验进度 (0.0-1.0)
 * @param progressText 进度文本 (如 "25/100")
 * @param greeting 问候语
 */
@Composable
fun CorgiInfoArea(
    corgiData: CorgiData,
    levelStage: com.corgimemo.app.animation.LevelStage,
    currentMood: CorgiMood,
    progress: Float,
    progressText: String,
    greeting: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = corgiData.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = levelStage.displayName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Lv.${corgiData.level}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                MoodBadge(mood = currentMood)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "经验值",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Text(
            text = greeting,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
    }
}

/**
 * 情绪徽章
 * 显示当前情绪状态
 *
 * @param mood 情绪状态
 */
@Composable
fun MoodBadge(mood: CorgiMood) {
    val emoji = when (mood) {
        CorgiMood.EXCITED -> "🎉"
        CorgiMood.HAPPY -> "😊"
        CorgiMood.NORMAL -> "🐾"
        CorgiMood.EXPECTING -> "🤔"
        CorgiMood.WORRIED -> "😟"
        CorgiMood.SLEEPY -> "💤"
        CorgiMood.SAD -> "🥺"
    }
    val description = when (mood) {
        CorgiMood.EXCITED -> "兴奋"
        CorgiMood.HAPPY -> "开心"
        CorgiMood.NORMAL -> "普通"
        CorgiMood.EXPECTING -> "期待"
        CorgiMood.WORRIED -> "担心"
        CorgiMood.SLEEPY -> "困倦"
        CorgiMood.SAD -> "失落"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = emoji, fontSize = 14.sp)
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 柯基等级卡片
 * 显示当前等级、等级阶段和经验值进度
 *
 * @param level 当前等级
 * @param experience 总经验值
 * @param modifier 修饰符
 */
@Composable
fun CorgiLevelCard(
    level: Int,
    experience: Int,
    modifier: Modifier = Modifier
) {
    val levelStage = LevelManager.getLevelStage(level)
    val (_, progress) = LevelManager.getCurrentLevelAndProgress(experience)
    val progressText = LevelManager.getProgressText(experience)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lv.$level",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = levelStage.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * 升级弹窗
 *
 * @param level 升级到的等级
 * @param onDismiss 关闭回调
 */
@Composable
fun LevelUpDialog(
    level: Int,
    onDismiss: () -> Unit
) {
    val levelStage = LevelManager.getLevelStage(level)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 48.sp
                )
                Text(
                    text = "升级啦！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Lv.$level",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "成为了 ${levelStage.displayName}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = levelStage.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "太棒了！")
                }
            }
        }
    }
}

/**
 * 成就解锁弹窗
 *
 * @param achievement 解锁的成就
 * @param onDismiss 关闭回调
 */
@Composable
fun AchievementUnlockDialog(
    achievement: com.corgimemo.app.animation.Achievement,
    onDismiss: () -> Unit
) {
    val icon = com.corgimemo.app.animation.AchievementManager.getAchievementIcon(achievement.id)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    fontSize = 64.sp
                )
                Text(
                    text = "成就解锁！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = achievement.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = achievement.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                achievement.outfitId?.let { outfitId ->
                    val outfit = com.corgimemo.app.animation.OutfitManager.getOutfitById(outfitId)
                    outfit?.let {
                        val outfitIcon = when (it.id) {
                            com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
                            com.corgimemo.app.animation.OutfitId.TIE -> "👔"
                            com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
                            com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
                            com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
                            else -> "🎁"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                text = outfitIcon,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "解锁装扮：${it.name}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "开心！")
                }
            }
        }
    }
}

/**
 * 连续 7 天奖励弹窗
 *
 * @param onDismiss 关闭回调
 */
@Composable
fun ConsecutiveBonusDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔥",
                    fontSize = 48.sp
                )
                Text(
                    text = "连续 7 天！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "获得额外 +50 经验值",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "继续加油！")
                }
            }
        }
    }
}

/**
 * 庆祝动画覆盖层
 * 根据任务优先级显示不同的庆祝效果
 *
 * @param level 庆祝级别（低、中、高、超级）
 * @param message 鼓励语
 */
@Composable
fun CelebrationOverlay(level: CelebrationLevel, message: String) {
    val (bgAlpha, emoji, fontSize) = when (level) {
        CelebrationLevel.LOW -> Triple(0.25f, "😊", 28.sp)
        CelebrationLevel.MEDIUM -> Triple(0.35f, "⭐", 30.sp)
        CelebrationLevel.HIGH -> Triple(0.5f, "🎉", 32.sp)
        CelebrationLevel.SUPER -> Triple(0.6f, "🏆", 36.sp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "$emoji $message",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (level == CelebrationLevel.SUPER) {
                Text(
                    text = "超级棒！经验值 +10",
                    fontSize = 20.sp,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "柯基为你感到骄傲！",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (level == CelebrationLevel.HIGH) {
                Text(
                    text = "经验值 +10",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (level == CelebrationLevel.MEDIUM) {
                Text(
                    text = "继续加油！",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * 过滤器按钮
 *
 * @param text 按钮文本
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 边缘光晕效果组件
 * 根据庆祝级别显示不同的边缘渐变光效
 *
 * @param level 庆祝级别（LOW/MEDIUM/HIGH/SUPER）
 */
@Composable
fun GlowOverlay(level: CelebrationLevel) {
    // 低优先级任务不显示光晕
    if (level == CelebrationLevel.LOW) return

    // 根据级别获取配置参数：(光晕宽度, 透明度)
    val (glowWidth, alpha) = when (level) {
        CelebrationLevel.LOW -> Pair(0.dp, 0f)
        CelebrationLevel.MEDIUM -> Pair(60.dp, 0.20f)
        CelebrationLevel.HIGH -> Pair(80.dp, 0.35f)
        CelebrationLevel.SUPER -> Pair(100.dp, 0.5f)
    }

    // 中优先级使用暖橙色，高优先级使用彩虹渐变，超级级别使用金色光晕
    when (level) {
        CelebrationLevel.MEDIUM -> {
            // 中优先级：暖橙色光晕
            MediumGlowOverlay(width = glowWidth, alpha = alpha)
        }
        CelebrationLevel.HIGH -> {
            // 高优先级：彩虹渐变光晕
            HighGlowOverlay(width = glowWidth, alpha = alpha)
        }
        CelebrationLevel.SUPER -> {
            // 超级级别：金色光晕（结合彩虹和金色）
            SuperGlowOverlay(width = glowWidth, alpha = alpha)
        }
        else -> {}
    }
}

/**
 * 中优先级光晕效果
 * 暖橙色边缘渐变光效
 *
 * @param width 光晕宽度
 * @param alpha 透明度
 */
@Composable
private fun MediumGlowOverlay(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    val glowColor = Color(0xFFFF9A5C)  // 暖橙色

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = alpha * 0.7f),
                        Color.Transparent,
                        Color.Transparent,
                        glowColor.copy(alpha = alpha * 0.7f)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 左侧边缘光晕
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )

        // 右侧边缘光晕
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = alpha)
                        )
                    )
                )
        )

        // 顶部边缘光晕
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部边缘光晕
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = alpha)
                        )
                    )
                )
        )
    }
}

/**
 * 高优先级光晕效果
 * 彩虹渐变边缘光效（红→橙→黄→绿→蓝→紫）
 *
 * @param width 光晕宽度
 * @param alpha 透明度
 */
@Composable
private fun HighGlowOverlay(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    // 顶部到底部的彩虹渐变（垂直方向）
    val verticalRainbow = listOf(
        Color(0xFFFF6B6B).copy(alpha = alpha * 0.5f),  // 顶部红色
        Color.Transparent,
        Color.Transparent,
        Color(0xFF9C27B0).copy(alpha = alpha * 0.5f),  // 底部紫色
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = verticalRainbow,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 左侧边缘光晕：使用彩虹渐变（从上到下）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF6B6B).copy(alpha = alpha),  // 红色（外侧）
                            Color.Transparent
                        )
                    )
                )
        )

        // 右侧边缘光晕：使用彩虹渐变
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF9C27B0).copy(alpha = alpha),  // 紫色（外侧）
                        )
                    )
                )
        )

        // 顶部边缘光晕：红色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6B6B).copy(alpha = alpha),  // 红色（顶部）
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部边缘光晕：紫色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF9C27B0).copy(alpha = alpha),  // 紫色（底部）
                        )
                    )
                )
        )
    }
}

/**
 * 超级级别光晕效果
 * 金色渐变边缘光效，结合彩虹和金色元素
 *
 * @param width 光晕宽度
 * @param alpha 透明度
 */
@Composable
private fun SuperGlowOverlay(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    // 顶部到底部的金色渐变（垂直方向）
    val verticalGold = listOf(
        Color(0xFFFFD700).copy(alpha = alpha * 0.7f),  // 顶部金色
        Color.Transparent,
        Color.Transparent,
        Color(0xFFFFA500).copy(alpha = alpha * 0.7f),  // 底部橙色
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = verticalGold,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 左侧边缘光晕：金色
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = alpha),  // 金色（外侧）
                            Color.Transparent
                        )
                    )
                )
        )

        // 右侧边缘光晕：金色
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFA500).copy(alpha = alpha),  // 橙色（外侧）
                        )
                    )
                )
        )

        // 顶部边缘光晕：金色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = alpha),  // 金色（顶部）
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部边缘光晕：橙色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFFFA500).copy(alpha = alpha),  // 橙色（底部）
                        )
                    )
                )
        )

        // 四个角落的亮点装饰
        // 左上角
        Box(
            modifier = Modifier
                .size(width * 1.5f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = alpha * 1.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 右上角
        Box(
            modifier = Modifier
                .size(width * 1.5f)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFD700).copy(alpha = alpha * 1.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 左下角
        Box(
            modifier = Modifier
                .size(width * 1.5f)
                .align(Alignment.BottomStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFA500).copy(alpha = alpha * 1.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 右下角
        Box(
            modifier = Modifier
                .size(width * 1.5f)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFA500).copy(alpha = alpha * 1.2f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

/**
 * 被忽略想念弹窗
 * 3天未打开APP时显示欢迎回来的提示
 *
 * @param daysAway 离开的天数
 * @param onDismiss 关闭回调
 */
@Composable
fun MissedYouDialog(
    daysAway: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🥺",
                    fontSize = 48.sp
                )
                Text(
                    text = "柯基想你了！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "终于回来啦",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "已经 $daysAway 天没见到你了...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "柯基一直都在等你哦~",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "我也想你！")
                }
            }
        }
    }
}

/**
 * 快速换装底部弹窗
 * 长按首页柯基时显示，提供已解锁装扮的快速切换
 *
 * @param sheetState 底部弹窗状态
 * @param currentOutfitId 当前装扮 ID
 * @param unlockedOutfitsJson 已解锁装扮 JSON
 * @param onSelect 选择装扮回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitQuickSwitchSheet(
    sheetState: SheetState,
    currentOutfitId: String?,
    unlockedOutfitsJson: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "快速换装 🎨",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "长按柯基试试这个功能~",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val unlockedOutfits = com.corgimemo.app.animation.OutfitManager.getOutfitsWithStatus(unlockedOutfitsJson)
                .filter { it.second }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                items(unlockedOutfits) { (outfit, _) ->
                    val isSelected = currentOutfitId == outfit.id ||
                            (outfit.isDefault && currentOutfitId == null)
                    QuickOutfitCard(
                        outfit = outfit,
                        isSelected = isSelected,
                        onClick = { onSelect(outfit.id) }
                    )
                }
            }
        }
    }
}

/**
 * 快速换装卡片
 * 与 ProfileScreen 的 OutfitCard 类似，但简化为只显示已解锁装扮
 *
 * @param outfit 装扮
 * @param isSelected 是否当前选中
 * @param onClick 点击回调
 */
@Composable
fun QuickOutfitCard(
    outfit: com.corgimemo.app.animation.Outfit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (outfit.id) {
                    com.corgimemo.app.animation.OutfitId.DEFAULT -> "🐕"
                    com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
                    com.corgimemo.app.animation.OutfitId.TIE -> "👔"
                    com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
                    com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
                    com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
                    // 节日装扮
                    com.corgimemo.app.animation.HolidayOutfitId.NEW_YEAR_HAT -> "🎉"
                    com.corgimemo.app.animation.HolidayOutfitId.RED_SCARF -> "🧣"
                    com.corgimemo.app.animation.HolidayOutfitId.LANTERN -> "🏮"
                    com.corgimemo.app.animation.HolidayOutfitId.LABOR_HAT -> "⛑️"
                    com.corgimemo.app.animation.HolidayOutfitId.DRAGON_HAT -> "🐲"
                    com.corgimemo.app.animation.HolidayOutfitId.FLAG -> "🇨🇳"
                    com.corgimemo.app.animation.HolidayOutfitId.MOON_DECOR -> "🌕"
                    com.corgimemo.app.animation.HolidayOutfitId.SCARF -> "🧶"
                    com.corgimemo.app.animation.HolidayOutfitId.CHRISTMAS_HAT -> "🎅"
                    else -> "🐕"
                },
                fontSize = 32.sp
            )
            Text(
                text = outfit.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSelected) {
                Text(
                    text = "✓ 当前",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}


/**
 * 快速添加待办内容组件
 * 用于悬浮柯基按钮左滑触发的 BottomSheet
 *
 * @param categories 分类列表
 * @param onAddTodo 添加待办回调（title, categoryId, priority）
 * @param onDismiss 关闭回调
 */
@Composable
private fun QuickAddTodoContent(
    categories: List<com.corgimemo.app.data.model.Category>,
    onAddTodo: (String, Long, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableLongStateOf(categories.firstOrNull()?.id ?: 1L) }
    var selectedPriority by remember { mutableStateOf(1) }  // 0=高 1=中 2=低

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "快速添加待办",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 标题输入框
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("待办标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiColors.Primary,
                focusedLabelColor = UiColors.Primary,
                cursorColor = UiColors.Primary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 分类选择
        Text(
            text = "分类",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(4).forEach { category ->
                FilterChip(
                    selected = selectedCategoryId == category.id,
                    onClick = { selectedCategoryId = category.id },
                    label = { Text(category.name, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UiColors.Primary.copy(alpha = 0.15f),
                        selectedLabelColor = UiColors.Primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 优先级选择
        Text(
            text = "优先级",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("高" to 0, "中" to 1, "低" to 2).forEach { (label, priority) ->
                val dotColor = when (priority) {
                    0 -> Color(0xFFEF4444)  // 红色
                    1 -> Color(0xFFF59E0B)  // 黄色
                    else -> Color(0xFF10B981)  // 绿色
                }
                FilterChip(
                    selected = selectedPriority == priority,
                    onClick = { selectedPriority = priority },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(label, fontSize = 13.sp)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UiColors.Primary.copy(alpha = 0.15f),
                        selectedLabelColor = UiColors.Primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 操作按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAddTodo(title.trim(), selectedCategoryId, selectedPriority)
                    }
                },
                enabled = title.isNotBlank(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("添加", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 分享待办为图片
 *
 * @param context 上下文
 * @param todo 待办项
 * @param categories 分类列表
 */
fun shareTodoAsImage(
    context: android.content.Context,
    todo: TodoItem,
    categories: List<Category>
) {
    val coroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
    coroutineScope.launch {
        try {
            val category = categories.find { it.id == todo.categoryId }

            val bitmap = ImageExporter.createTodoShareCard(
                context = context,
                todo = todo,
                category = category
            )

            val imageFile = ImageExporter.saveBitmapToCache(context, bitmap)

            val shareIntent = ShareIntentHelper.createShareImageIntent(
                context = context,
                imageFile = imageFile,
                text = "我在 CorgiMemo 创建了待办：${todo.title}"
            )

            shareIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                context.startActivity(shareIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context,
                    "分享失败：${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
