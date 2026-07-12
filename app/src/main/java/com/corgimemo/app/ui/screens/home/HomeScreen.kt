package com.corgimemo.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.sp
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
import com.corgimemo.app.ui.components.ZonedReorderableLazyColumn
import com.corgimemo.app.ui.components.zone
import com.corgimemo.app.ui.components.TodoListItem
import com.corgimemo.app.ui.components.SearchBar
import com.corgimemo.app.ui.components.SectionHeaderColors
import com.corgimemo.app.ui.components.CorgiPullRefreshIndicator
import com.corgimemo.app.ui.components.PullRefreshState
import com.corgimemo.app.ui.components.rememberPullRefreshStateHolder
import com.corgimemo.app.ui.components.SortBottomSheet
import com.corgimemo.app.ui.components.MoreOptionsSheet
import com.corgimemo.app.ui.components.CollapsibleSectionHeader
import com.corgimemo.app.ui.components.PendingSectionHeader
import com.corgimemo.app.ui.components.PinnedSectionHeader
import com.corgimemo.app.ui.components.PriorityPickerSheet
import com.corgimemo.app.ui.components.ReminderPickerBottomSheet
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.corgimemo.app.viewmodel.CelebrationLevel
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.ui.theme.UiColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onFabClick: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    /**
     * 共享的 Snackbar 状态
     *
     * 来源：MainScreen 顶层 Scaffold 创建并通过参数传入。
     * 原因：使用 Material 3 Scaffold 的 snackbarHost 槽位后，Scaffold 会自动
     * 管理 Snackbar 与 FAB / 底部导航栏的避让，避免被遮挡。
     *
     * 子页面调用 snackbarHostState.showSnackbar(...) 即可触发显示。
     */
    snackbarHostState: SnackbarHostState
) {
    val filteredTodos by viewModel.filteredTodos.collectAsState()
    val isDataInitialized by viewModel.isDataInitialized.collectAsState()
    val showCompleted by viewModel.showCompleted.collectAsState()
    val completedCount by viewModel.completedCount.collectAsState()
    val showPinned by viewModel.showPinned.collectAsState()
    val pinnedCount by viewModel.pinnedCount.collectAsState()
    val showPending by viewModel.showPending.collectAsState()
    val pendingCount by viewModel.pendingCount.collectAsState()
    val pinnedPendingTodos by viewModel.pinnedPendingTodos.collectAsState()
    val pendingTodos by viewModel.pendingTodos.collectAsState()
    val pinnedCompletedTodos by viewModel.pinnedCompletedTodos.collectAsState()
    val completedTodos by viewModel.completedTodos.collectAsState()
    /** 合并所有待完成（置顶 + 非置顶），用于空状态判断与过滤 */
    val pendingTodosAll = pinnedPendingTodos + pendingTodos
    /** 合并所有已完成（置顶 + 非置顶），用于过滤 */
    val visibleCompletedTodosAll = pinnedCompletedTodos + completedTodos
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
    val pendingBatchCompleteCount by viewModel.pendingBatchCompleteCount.collectAsState()
    // 方案 B：批量复制失败 Snackbar 标志
    val pendingBatchDuplicateFailure by viewModel.pendingBatchDuplicateFailure.collectAsState()

    // 子任务进度映射
    val subTaskProgressMap by viewModel.subTaskProgressMap.collectAsState()

    // 子任务列表映射
    val subTasksMap by viewModel.subTasksMap.collectAsState()

    // 待办列表 LazyColumn 状态（用于滚动位置记忆与下拉刷新联动）
    val lazyListState = rememberLazyListState()

    // 展开状态
    val expandedTodos by viewModel.expandedTodos.collectAsState()

    // 批量选择模式状态
    val isBatchMode by viewModel.isBatchMode.collectAsState()
    val selectedTodoIds by viewModel.selectedTodoIds.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val selectedCategoryId by viewModel.selectedCategoryId.collectAsState()

    /** 排序方式状态 */
    val sortType by viewModel.sortType.collectAsState()

    /** 待办卡片简化显示（隐藏详情） */
    val hideDetails by viewModel.hideDetails.collectAsState()
    /** 隐藏所有已完成项 */
    val hideCompletedItems by viewModel.hideCompletedItems.collectAsState()

    /**
     * 批量操作弹窗显示状态（已提升到 ViewModel）
     *
     * 背景：批量操作栏已提取到 MainScreen 的 bottomBar 槽位（详见 HomeBatchActionBar），
     * 但弹窗渲染仍需在 HomeScreen 内（因弹窗依赖 filteredTodos / categories 等）。
     * 因此把这 3 个 boolean 状态从 HomeScreen 本地 state 提升为 ViewModel 级 StateFlow：
     * - MainScreen 调用 viewModel.setShowBatchXxx(true) 触发显示
     * - HomeScreen 通过 collectAsState() 订阅并渲染对应弹窗
     */
    val showBatchDeleteDialog by viewModel.showBatchDeleteDialog.collectAsState()
    val showBatchMoveDialog by viewModel.showBatchMoveDialog.collectAsState()
    val showMoreOptionsSheet by viewModel.showMoreOptionsSheet.collectAsState()

    /** PriorityPicker 弹窗显示状态（MoreOptions → 优先级） */
    var showPriorityPickerSheet by remember { mutableStateOf(false) }

    /** ReminderPicker 弹窗显示状态（MoreOptions → 提醒时间） */
    var showReminderPickerSheet by remember { mutableStateOf(false) }

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

    /** 拖拽激活状态：与子项/左滑协调手势 */
    var isDragActive by remember { mutableStateOf(false) }

    /** 快速添加待办 BottomSheet 状态 */
    var showQuickAddSheet by remember { mutableStateOf(false) }

    /**
     * 排序弹窗显示状态（已提升至 ViewModel，供 MainScreen.dropdownContent 触发）
     *
     * 排序入口现位于 EnhancedTopBar.dropdownContent 中（MainScreen 层），
     * 因此 showSortSheet 必须从 ViewModel 订阅。
     */
    val showSortSheet by viewModel.showSortSheet.collectAsState()
    val sortSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** MoreOptions 弹窗状态对象（多选页 ⋮ 按钮触发） */
    val moreOptionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** PriorityPicker 弹窗状态对象（MoreOptions → 优先级） */
    val priorityPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** ReminderPicker 弹窗状态对象（MoreOptions → 提醒时间） */
    val reminderPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    /** 首次引导状态 */
    var showFirstTimeGuide by remember { mutableStateOf(false) }

    /** A/B 测试组别 */
    var abGroup by remember { mutableStateOf("A") }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    /**
     * snackbarHostState 来源：
     * - 不再在 HomeScreen 内部创建，改为由 MainScreen 顶层 Scaffold 创建并通过参数传入
     * - 这样 SnackbarHost 通过 Scaffold 的 snackbarHost 槽位渲染，自动管理避让
     * - 调用方：snackbarHostState.showSnackbar(...)
     */

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

    /**
     * 监听批量完成事件，显示 Snackbar "已完成 N 项"
     *
     * 区别于单条完成（pendingCompleteTodo 带撤销）：
     * - 批量完成 Snackbar 不带撤销按钮（避免与批量模式冲突）
     * - 显示完成后立即清空状态，避免重复触发
     *
     * 关键设计：等待全屏弹窗关闭后再显示
     *
     * 用户反馈：当批量完成触发柯基升级时，只看到升级弹窗，看不到"已完成 N 项" Snackbar。
     *
     * 根因：
     * - LevelUpDialog / AchievementUnlockDialog 是全屏 Dialog，会完全遮挡屏幕底部的 SnackbarHost
     * - showSnackbar() 协程在 Dialog 显示期间正常执行完成（4s 后协程结束 + 状态清空）
     * - 用户关闭 Dialog 后 Snackbar 已过期，无法显示
     *
     * 修复策略：LaunchedEffect 的 key 包含 showLevelUp / showAchievementUnlock。
     * - 当弹窗未关闭时，return@LaunchedEffect 不显示 Snackbar
     * - 当用户关闭弹窗，key 变化，协程自动重启
     * - 弹窗已关闭，正常显示 Snackbar
     */
    LaunchedEffect(
        pendingBatchCompleteCount,
        showLevelUp,
        showAchievementUnlock
    ) {
        val count = pendingBatchCompleteCount ?: return@LaunchedEffect

        // 等待所有"抢占型"弹窗关闭（升级弹窗、成就弹窗）
        // 这些全屏 Dialog 会完全遮挡 SnackbarHost
        if (showLevelUp != null || showAchievementUnlock != null) {
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            message = "✅ 已完成 $count 项",
            duration = SnackbarDuration.Short
        )
        // 显示完成后立即清空，避免配置变化（如旋转）时重复触发
        viewModel.clearPendingBatchComplete()
    }

    /**
     * 方案 B：监听批量复制失败事件，显示 Snackbar "⚠️ 部分文件复制失败"
     *
     * 触发时机：HomeViewModel.batchDuplicate 文件复制过程中出现异常时
     * 设置 _pendingBatchDuplicateFailure = true
     *
     * 等待策略：与 pendingBatchCompleteCount 相同，等待 showLevelUp / showAchievementUnlock 关闭
     *
     * 国际化：使用 R.string.batch_duplicate_failure_hint（zh / en 双语均已配置）
     */
    LaunchedEffect(
        pendingBatchDuplicateFailure,
        showLevelUp,
        showAchievementUnlock
    ) {
        if (!pendingBatchDuplicateFailure) return@LaunchedEffect

        // 等待所有"抢占型"弹窗关闭（升级弹窗、成就弹窗）
        if (showLevelUp != null || showAchievementUnlock != null) {
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            message = context.getString(com.corgimemo.app.R.string.batch_duplicate_failure_hint),
            duration = SnackbarDuration.Short
        )
        // 显示完成后立即清空，避免配置变化（如旋转）时重复触发
        viewModel.clearPendingBatchDuplicateFailure()
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
                // 批量模式的"◀ 返回 + 选中 X 项"已由 MainScreen 顶层 TopAppBar 接管
                // （详见 MainScreen.kt 的 topBar 槽位：isBatchMode 时 title 切换为"选中 X 项"）
                // 此处不再渲染任何顶部栏，避免与 MainScreen 的 EnhancedTopBar 重复显示

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

                    /**
                     * 搜索框：固定显示在顶部，不随滚动隐藏
                     *
                     * 与 InspirationScreen / SpecialDateScreen 保持一致的布局结构，
                     * SearchBar 作为 Column 的首项，modifier 配置三页统一：
                     * - fillMaxWidth()
                     * - padding(horizontal = 20.dp)
                     * - padding(bottom = dimensionResource(ui_search_bar_bottom_margin))
                     */
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { newQuery ->
                            viewModel.updateSearchQuery(newQuery)
                        },
                        onClear = {
                            viewModel.clearSearch()
                        },
                        placeholder = "搜索待办...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = dimensionResource(com.corgimemo.app.R.dimen.ui_search_bar_bottom_margin))
                    )

                    // 柯基陪伴区已分离为悬浮按钮，此处不再显示

                    /**
                     * 内容区域显示逻辑：
                     * 1. 数据未初始化 → 显示加载指示器（避免闪烁）
                     * 2. 无任何待办 → 显示空状态
                     * 3. 所有待办已完成且折叠 → 只显示分隔按钮
                     * 4. 其他 → 显示列表
                     */
                    if (!isDataInitialized) {
                        // 数据未初始化：显示页面专属骨架屏，避免从空列表闪烁到有数据
                        TodoSkeleton(itemCount = 4)
                    } else if (pendingTodosAll.isEmpty() && completedCount == 0) {
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
                    } else if (pendingTodosAll.isEmpty() && completedCount > 0 && !showCompleted) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            CompletedSectionHeader(
                                count = completedCount,
                                isExpanded = showCompleted,
                                onClick = {
                                    viewModel.onUserInteraction()
                                    viewModel.toggleShowCompleted()
                                }
                            )
                        }
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
                                delay(200)
                                if (pullRefreshState.state == PullRefreshState.PULLING ||
                                    pullRefreshState.state == PullRefreshState.RELEASING) {
                                    pullRefreshState.onRelease(forceResetFromReleasing = true)
                                }
                            }
                        }

                        /**
                         * 应用分类和搜索过滤
                         */
                        fun applyFilters(list: List<TodoItem>): List<TodoItem> {
                            var result = list
                            val catId = selectedCategoryId
                            if (catId != null && catId > 0) {
                                result = result.filter { it.categoryId == catId }
                            } else if (catId != null && catId == 0L) {
                                val validCategoryIds = categories.map { it.id }.toSet()
                                result = result.filter { it.categoryId !in validCategoryIds }
                            }
                            if (searchQuery.isNotBlank()) {
                                result = result.filter { todo ->
                                    todo.title.contains(searchQuery, ignoreCase = true) ||
                                    (todo.content?.contains(searchQuery, ignoreCase = true) ?: false) ||
                                    (todo.contentFormat?.let { format ->
                                        com.corgimemo.app.util.MarkdownParser.stripMarkdown(format)
                                            .contains(searchQuery, ignoreCase = true)
                                    } ?: false)
                                }
                            }
                            return result
                        }

                        val filteredPending = applyFilters(pendingTodosAll)
                        val filteredCompleted = applyFilters(visibleCompletedTodosAll)

                        val displayItems = remember(
                            filteredPending, filteredCompleted,
                            showPinned, showPending, showCompleted,
                            pinnedCount, pendingCount, completedCount,
                            hideCompletedItems
                        ) {
                            buildList {
                                // 置顶区（仅当有置顶待办时显示）
                                if (pinnedCount >= 1) {
                                    add(DisplayItem.PinnedDivider(
                                        count = pinnedCount,
                                        isExpanded = showPinned
                                    ))
                                    if (showPinned) {
                                        filteredPending.filter { it.isPinned }
                                            .forEach { add(DisplayItem.Todo(it)) }
                                    }
                                }
                                // 待完成区（始终显示，代表非置顶待完成）
                                add(DisplayItem.PendingDivider(
                                    count = pendingCount,
                                    isExpanded = showPending
                                ))
                                if (showPending) {
                                    filteredPending.filter { !it.isPinned }
                                        .forEach { add(DisplayItem.Todo(it)) }
                                }
                                // 已完成区（原有逻辑不变）
                                if (!hideCompletedItems && completedCount > 0) {
                                    add(DisplayItem.CompletedDivider(
                                        count = completedCount,
                                        isExpanded = showCompleted
                                    ))
                                    if (showCompleted) {
                                        filteredCompleted.forEach { add(DisplayItem.Todo(it)) }
                                    }
                                }
                            }
                        }

                        // 监听日历弹窗点击待办后的滚动请求
                        val scrollToTodoId by viewModel.scrollToTodoId.collectAsState()
                        LaunchedEffect(scrollToTodoId) {
                            val targetId = scrollToTodoId ?: return@LaunchedEffect
                            // 在 displayItems 中查找目标待办
                            val targetIndex = displayItems.indexOfFirst { item ->
                                item is DisplayItem.Todo && item.item.id == targetId
                            }
                            if (targetIndex >= 0) {
                                // 目标在可见列表中，直接滚动
                                lazyListState.animateScrollToItem(targetIndex)
                                viewModel.clearScrollToTodo()
                            } else {
                                // 目标可能在折叠区域，需要先展开再查找
                                val todo = viewModel.filteredTodos.value.find { it.id == targetId }
                                if (todo != null) {
                                    when {
                                        // 已完成区折叠 → 展开已完成区
                                        todo.status == 1 && !showCompleted -> {
                                            viewModel.toggleShowCompleted()
                                        }
                                        // 置顶区折叠 → 展开置顶区
                                        todo.isPinned && !showPinned -> {
                                            viewModel.toggleShowPinned()
                                        }
                                        // 待完成区折叠 → 展开待完成区
                                        !todo.isPinned && !showPending -> {
                                            viewModel.toggleShowPending()
                                        }
                                        else -> {
                                            // 不在任何折叠区，滚动失败
                                            viewModel.clearScrollToTodo()
                                            return@LaunchedEffect
                                        }
                                    }
                                    // 等待 displayItems 重组后包含目标待办（3秒超时保护，避免无限挂起）
                                    val found = try {
                                        withTimeout(3000L) {
                                            snapshotFlow { displayItems }
                                                .first { items ->
                                                    items.any { it is DisplayItem.Todo && it.item.id == targetId }
                                                }
                                            true
                                        }
                                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                                        false
                                    }
                                    if (!found) {
                                        viewModel.clearScrollToTodo()
                                        return@LaunchedEffect
                                    }
                                    // 重组完成后滚动到目标位置
                                    val newIndex = displayItems.indexOfFirst { item ->
                                        item is DisplayItem.Todo && item.item.id == targetId
                                    }
                                    if (newIndex >= 0) {
                                        lazyListState.animateScrollToItem(newIndex)
                                    }
                                }
                                viewModel.clearScrollToTodo()
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

                            // 内层 Box：列表 + 搜索框整体下移 pullOffset
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { translationY = pullRefreshState.pullOffset }
                            ) {
                            ZonedReorderableLazyColumn(
                                items = displayItems,
                                listState = lazyListState,
                                isDragEnabled = !isBatchMode && swipeExpandedTodoId == null
                                    && searchQuery.isBlank() && selectedCategoryId == null,
                                key = { item ->
                                    when (item) {
                                        is DisplayItem.Todo -> item.item.id
                                        is DisplayItem.PinnedDivider -> "pinned_divider_${item.count}"
                                        is DisplayItem.PendingDivider -> "pending_divider_${item.count}"
                                        is DisplayItem.CompletedDivider -> "completed_divider"
                                    }
                                },
                                onReorder = { dragResult, draggedTodoItem, targetZoneRelativeIndex ->
                                    // 纯转发：组件已基于内部 displayItems 计算好被拖项和相对索引
                                    viewModel.reorderOnDragResult(
                                        draggedItemId = draggedTodoItem.item.id,
                                        draggedTodo = draggedTodoItem.item,
                                        dragResult = dragResult,
                                        targetZoneRelativeIndex = targetZoneRelativeIndex
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                itemSpacing = 8.dp
                            ) { index, displayItem, isDragging, dragActive ->
                                LaunchedEffect(dragActive) {
                                    isDragActive = dragActive
                                }

                                when (displayItem) {
                                    is DisplayItem.PinnedDivider -> {
                                        PinnedSectionHeader(
                                            count = displayItem.count,
                                            isExpanded = displayItem.isExpanded,
                                            onClick = {
                                                viewModel.onUserInteraction()
                                                viewModel.toggleShowPinned()
                                            }
                                        )
                                    }
                                    is DisplayItem.PendingDivider -> {
                                        PendingSectionHeader(
                                            count = displayItem.count,
                                            isExpanded = displayItem.isExpanded,
                                            onClick = {
                                                viewModel.onUserInteraction()
                                                viewModel.toggleShowPending()
                                            }
                                        )
                                    }
                                    is DisplayItem.CompletedDivider -> {
                                        CompletedSectionHeader(
                                            count = displayItem.count,
                                            isExpanded = displayItem.isExpanded,
                                            onClick = {
                                                viewModel.onUserInteraction()
                                                viewModel.toggleShowCompleted()
                                            }
                                        )
                                    }
                                    is DisplayItem.Todo -> {
                                        val todo = displayItem.item
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
                                            modifier = Modifier.padding(1.dp),
                                            isEnabled = !isBatchMode && !dragActive,
                                            isExpanded = swipeExpandedTodoId == todo.id,
                                            isPinned = todo.isPinned,
                                            onExpandChange = { expanded ->
                                                swipeExpandedTodoId = if (expanded) todo.id else null
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
                                        ) { isClickBlocked ->
                                            TodoListItem(
                                                todo = todo,
                                                subTaskProgress = subTaskProgressMap[todo.id],
                                                subTasks = subTasksMap[todo.id] ?: emptyList(),
                                                isExpanded = expandedTodos.contains(todo.id),
                                                isBatchMode = isBatchMode,
                                                isSelected = selectedTodoIds.contains(todo.id),
                                                isSimpleMode = hideDetails,
                                                categoryName = category?.name,
                                                categoryIcon = categoryIcon,
                                                onToggleComplete = { id, isChecked ->
                                                    viewModel.onUserInteraction()
                                                    viewModel.toggleTodoStatus(id, isChecked)
                                                },
                                                onDelete = {
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
                                                searchQuery = searchQuery,
                                                hapticEnabled = hapticEnabled,
                                                isDragging = isDragging,
                                                isDragActive = dragActive,
                                                isClickBlocked = isClickBlocked
                                            )
                                        }
                                    }
                                }
                            }
                            } // 闭合内层 Box（列表 + 搜索框整体下移 pullOffset）
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

            /**
             * SnackbarHost 已提升到 MainScreen 顶层 Scaffold 的 snackbarHost 槽位，
             * 由 Scaffold 自动管理与 FAB / 底部导航栏的避让。
             * 此处不再渲染 SnackbarHost，避免双重渲染与位置冲突。
             *
             * 调用方式：snackbarHostState.showSnackbar(...) 即可触发显示。
             */

            /**
             * 批量操作栏已提取为 HomeBatchActionBar，在 MainScreen 的 bottomBar 槽位渲染。
             * 原因：原位置（HomeScreen 内部 Box.align(BottomCenter)）会被 MainScreen 的
             * CorgiBottomNavigationBar 遮挡，导致"全选+4图标"完全不可见。
             * 提取后批量操作栏直接占据 Scaffold 的 bottomBar 槽位，与 CorgiBottomNavigationBar
             * 互斥显示（详见 MainScreen.kt）。
             */

            // 覆盖层效果
            AnimatedVisibility(
                visible = celebrationState.isShowing,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                GlowOverlay(level = celebrationState.level)
            }
        }
    }

    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowBatchDeleteDialog(false) },
            title = { Text("删除选中项") },
            text = {
                Text(
                    "确定要删除已选择的 ${selectedTodoIds.size} 个待办吗？\n此操作不可撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setShowBatchDeleteDialog(false)
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
                TextButton(onClick = { viewModel.setShowBatchDeleteDialog(false) }) {
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
            onDismissRequest = { viewModel.setShowBatchMoveDialog(false) },
            title = { Text("移动到分类") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    categoryList.forEach { category ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setShowBatchMoveDialog(false)
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
                TextButton(onClick = { viewModel.setShowBatchMoveDialog(false) }) {
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
            onDismiss = { viewModel.setShowSortSheet(false) },
            onSortOrderSelected = { order ->
                viewModel.onSortTypeChanged(order)
            },
            onRestoreDefaultOrder = {
                viewModel.restoreDefaultOrder()
            }
        )
    }

    /**
     * 三点功能菜单下拉弹窗（已迁移至 MainScreen → EnhancedTopBar.dropdownContent 渲染）
     *
     * 关键变更：原 HomeScreen 内渲染 TodoMenuDropdown 时，Material 3 DropdownMenu
     * 锚定到外层 Box（覆盖整个列表区域），导致菜单显示在屏幕左下角而非右上角三点按钮正下方。
     * 修复方案：菜单在 EnhancedTopBar 内部与 IconButton 共同 Box 渲染，保证锚点对齐。
     *
     * 保留 showSortSheet 状态在此处管理（用于排序弹窗）。
     */

    /**
     * 多选页 ⋮ 按钮触发的"更多选项"弹窗
     *
     * 包含 6 个批量操作：完成 / 置顶 / 优先级 / 提醒时间 / 创建副本 / 转换为灵感
     * - "完成"、"置顶"、"创建副本" 直接触发 ViewModel 对应批量方法，操作完成后退出多选
     * - "优先级" 触发 PriorityPickerSheet
     * - "提醒时间" 触发 ReminderPickerBottomSheet
     * - "转换为灵感" 暂不实现，弹 Toast 提示
     *
     * 选中数量为 0 时禁用此弹窗（由 IconButton 的 enabled 控制），但弹窗打开后如
     * 选中项变化，组件重组不会自动关闭——用户可主动点击完成/置顶等按钮继续。
     */
    if (showMoreOptionsSheet) {
        MoreOptionsSheet(
            sheetState = moreOptionsSheetState,
            onDismiss = { viewModel.setShowMoreOptionsSheet(false) },
            onComplete = {
                /**
                 * 批量完成选中的待办。
                 * 已完成项会被 [batchComplete] 内部跳过，操作完成后调用
                 * exitBatchMode() 退出多选。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                val count = selectedTodoIds.size
                viewModel.batchComplete()
                viewModel.exitBatchMode()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("已完成 $count 个待办")
                }
            },
            onPin = {
                /**
                 * 批量置顶选中的待办。
                 * 已置顶项保持置顶状态，操作完成后退出多选。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                val count = selectedTodoIds.size
                viewModel.batchPin()
                viewModel.exitBatchMode()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("已置顶 $count 个待办")
                }
            },
            onPriority = {
                /**
                 * 关闭 MoreOptions，弹出 PriorityPicker。
                 * 保持多选模式（selectedTodoIds 不变），用户选完优先级后由弹窗 onConfirm 触发批量更新。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                showPriorityPickerSheet = true
            },
            onReminder = {
                /**
                 * 关闭 MoreOptions，弹出 ReminderPicker。
                 * 保持多选模式，用户选完提醒时间后由弹窗 onConfirm 触发批量更新。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                showReminderPickerSheet = true
            },
            onDuplicate = {
                /**
                 * 批量复制选中的待办。
                 * [batchDuplicate] 在 ViewModel 中已实现：
                 * 1. 数据库复制（主表 + SubTask）
                 * 2. 后台深复制文件（fileCopyManager.copyAllAttachments，顺序 for 循环）
                 * 3. 失败时设置 _pendingBatchDuplicateFailure
                 *    → HomeScreen 监听后显示 Snackbar "⚠️ 部分文件复制失败"
                 *
                 * **方案 B 简化**：移除进度条 UI 反馈与"已创建 N 个副本" Snackbar，
                 * 仅保留失败 Snackbar（仅在文件复制出现异常时弹）。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                viewModel.batchDuplicate()
                viewModel.exitBatchMode()
            },
            onConvertToInspiration = {
                /**
                 * "转换为灵感"功能暂不实现，弹 Toast 提示并关闭弹窗，保持多选模式。
                 */
                viewModel.setShowMoreOptionsSheet(false)
                Toast.makeText(context, "转换为灵感功能开发中...", Toast.LENGTH_SHORT).show()
            }
        )
    }

    /**
     * 多选页"优先级"子菜单弹窗
     *
     * - 4 选 1 单选（无/低/中/高）
     * - 初始选中值：取首个选中项的 priority，让用户看到当前选中项的优先级
     * - 点击任一选项 → 触发 [batchUpdatePriority] 批量更新并退出多选
     * - 点击空白区域或下滑 → 仅关闭弹窗，不更新
     */
    if (showPriorityPickerSheet) {
        /**
         * 首个选中的待办：用于读取初始优先级。
         * 注意：批量场景下选中项的优先级可能不一致，但弹窗只能显示一个初值，
         * 因此取首个选中项作为代表，行为与设计文档 Task 7 步骤 1 一致。
         */
        val firstSelected = filteredTodos
            .firstOrNull { selectedTodoIds.contains(it.id) }

        PriorityPickerSheet(
            sheetState = priorityPickerSheetState,
            initialPriority = firstSelected?.priority ?: 0,
            onDismiss = { showPriorityPickerSheet = false },
            onConfirm = { priority ->
                /**
                 * 批量设置选中的待办优先级。
                 * 完成后退出多选模式。
                 */
                showPriorityPickerSheet = false
                val count = selectedTodoIds.size
                viewModel.batchUpdatePriority(priority)
                viewModel.exitBatchMode()
                val name = when (priority) {
                    1 -> "低"
                    2 -> "中"
                    3 -> "高"
                    else -> "无"
                }
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("已设置 $count 个待办为「$name」优先级")
                }
            }
        )
    }

    /**
     * 多选页"提醒时间"子菜单弹窗
     *
     * 复用现有 [ReminderPickerBottomSheet]，参数语义：
     * - dateMillis/hour/minute → 组合为最终的 reminderTime 时间戳
     * - repeatType → 写入 TodoItem.repeatType
     * - calendarEnabled → 仅 UI 状态，不持久化到 TodoItem（设计文档 Task 2 约定）
     *
     * 注意：[ReminderPickerBottomSheet] 本身不包含 ModalBottomSheet 容器，
     * 需手动用 ModalBottomSheet 包裹以提供标准弹窗体验（圆角、滑入动画、点击外部关闭）。
     *
     * 初始值策略：取首个选中项的 reminderTime / repeatType，让用户看到当前选中项的
     * 提醒配置。如首个选中项未设置提醒，则默认今天 13:35，与 ReminderPickerBottomSheet
     * 默认值保持一致。
     */
    if (showReminderPickerSheet) {
        /**
         * 首个选中的待办：用于读取初始提醒时间。
         * 与 PriorityPickerSheet 同样的策略：批量场景下选中项的提醒可能不一致，
         * 取首个选中项作为代表。
         */
        val firstSelected = filteredTodos
            .firstOrNull { selectedTodoIds.contains(it.id) }

        /**
         * 把首个选中项的 reminderTime 拆分为初始日期 + 初始时分。
         * - 未设置提醒（reminderTime == null）→ 默认今天 13:35
         * - 已设置提醒 → 从时间戳解析
         */
        val initCal = remember(firstSelected?.reminderTime) {
            java.util.Calendar.getInstance().apply {
                if (firstSelected?.reminderTime != null) {
                    timeInMillis = firstSelected.reminderTime
                }
            }
        }
        val initHour = initCal.get(java.util.Calendar.HOUR_OF_DAY)
        val initMinute = initCal.get(java.util.Calendar.MINUTE)

        ModalBottomSheet(
            onDismissRequest = { showReminderPickerSheet = false },
            sheetState = reminderPickerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ReminderPickerBottomSheet(
                /**
                 * 初始日期：首个选中项的 reminderTime，未设置时 ReminderPicker 会取今天
                 */
                initialDateMillis = firstSelected?.reminderTime,
                initialHour = initHour,
                initialMinute = initMinute,
                initialRepeatType = firstSelected?.repeatType ?: 0,
                onDismiss = { showReminderPickerSheet = false },
                onConfirm = { dateMillis, hour, minute, repeatType, _, dueDateMillis ->
                    /**
                     * 把日期 + 时分组合为完整时间戳。
                     * 若未选择日期（dateMillis 为 null），使用当前时刻。
                     */
                    val calendar = java.util.Calendar.getInstance()
                    if (dateMillis != null) {
                        calendar.timeInMillis = dateMillis
                    }
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
                    calendar.set(java.util.Calendar.MINUTE, minute)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    val reminderTime = calendar.timeInMillis

                    showReminderPickerSheet = false
                    val count = selectedTodoIds.size
                    viewModel.batchUpdateReminder(reminderTime, repeatType)
                    // 批量更新截止日期
                    viewModel.batchUpdateDueDate(dueDateMillis)
                    viewModel.exitBatchMode()
                    coroutineScope.launch {
                        val msg = if (dueDateMillis != null) {
                            "已为 $count 个待办设置提醒和截止日期"
                        } else {
                            "已为 $count 个待办设置提醒"
                        }
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            )
        }
    }
}

/**
 * 首页批量模式底部操作栏（提取版）
 *
 * 设计初衷：
 * 原实现位于 HomeScreen 内部 Box.align(BottomCenter)，会被 MainScreen 的
 * CorgiBottomNavigationBar 在 z 轴上完全遮挡，导致"全选+4图标"在用户设备上
 * 不可见。本组件提取到 MainScreen 的 bottomBar 槽位中，与 CorgiBottomNavigationBar
 * 互斥显示（详见 MainScreen.kt）。
 *
 * 布局：
 * ```
 * ┌─────────────────────────────────────┐
 * │  全选/取消全选    🖼   ➡️   🗑   ⋮   │
 * └─────────────────────────────────────┘
 * ```
 *
 * @param isBatchMode 是否处于批量模式（控制整个栏的显隐动画）
 * @param selectedTodoIds 当前选中的待办 ID 集合
 * @param filteredTodos 当前过滤后的待办列表（用于判断"全选"状态）
 * @param onSelectAll 全选回调
 * @param onClearSelection 取消全选回调
 * @param onShare 分享按钮回调
 * @param onMove 移动按钮回调（触发批量移动分类弹窗）
 * @param onDelete 删除按钮回调（触发批量删除确认弹窗）
 * @param onMoreOptions 更多选项按钮回调（触发 MoreOptions 弹窗）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBatchActionBar(
    isBatchMode: Boolean,
    selectedTodoIds: Set<Long>,
    filteredTodos: List<TodoItem>,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onShare: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onMoreOptions: () -> Unit
) {
    /**
     * 整栏显隐动画：
     * - 显示：从底部滑入（slideInVertically）
     * - 隐藏：向底部滑出（slideOutVertically）
     */
    AnimatedVisibility(
        visible = isBatchMode,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it })
    ) {
        Surface(
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            /**
             * 系统导航栏安全区域：避免三键导航模式下按钮被遮挡。
             * 即使作为 Scaffold bottomBar 的子组件，仍保留此 padding 以兼容
             * 未来可能的独立放置场景。
             */
            modifier = Modifier.safeAreaForBottomBar()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hasSelection = selectedTodoIds.isNotEmpty()
                val isAllSelected = filteredTodos.isNotEmpty() &&
                    selectedTodoIds.size == filteredTodos.size

                /** 左下：全选 / 取消全选 按钮 */
                TextButton(
                    onClick = {
                        if (isAllSelected) onClearSelection() else onSelectAll()
                    },
                    enabled = filteredTodos.isNotEmpty()
                ) {
                    Text(
                        text = if (isAllSelected) "取消全选" else "全选",
                        color = UiColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                /** 右下：4 个图标按钮（分享/移动/删除/更多） */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 1. 分享 */
                    IconButton(
                        onClick = onShare,
                        enabled = hasSelection,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "分享",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 2. 移动到分组 */
                    IconButton(
                        onClick = onMove,
                        enabled = hasSelection,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                            contentDescription = "移动到分组",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 3. 删除待办 */
                    IconButton(
                        onClick = onDelete,
                        enabled = hasSelection,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "删除待办",
                            tint = if (hasSelection) {
                                UiColors.Error
                            } else {
                                UiColors.Error.copy(alpha = 0.38f)
                            }
                        )
                    }

                    /** 4. 更多选项（⋮） */
                    IconButton(
                        onClick = onMoreOptions,
                        enabled = hasSelection,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "更多选项",
                            tint = if (hasSelection) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                }
            }
        }
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
 * 列表显示项：普通待办或"已完成"分隔按钮
 *
 * 提升为 top-level public，供 [com.corgimemo.app.ui.components.ZonedReorderableLazyColumn]
 * 跨文件引用（Task 5）。
 */
sealed interface DisplayItem {
    data class Todo(val item: TodoItem) : DisplayItem
    data class PinnedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class PendingDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
    data class CompletedDivider(val count: Int, val isExpanded: Boolean) : DisplayItem
}

/**
 * "已完成"区域分隔按钮
 *
 * 颜色已统一使用 [SectionHeaderColors.Completed](绿色 #7EC8A0),
 * 与新"置顶"/"待完成"按钮形成完整的设计语言。
 */
@Composable
private fun CompletedSectionHeader(
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) = CollapsibleSectionHeader(
    label = "已完成",
    count = count,
    isExpanded = isExpanded,
    color = SectionHeaderColors.Completed,  // 绿色 #7EC8A0
    onClick = onClick,
)

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
