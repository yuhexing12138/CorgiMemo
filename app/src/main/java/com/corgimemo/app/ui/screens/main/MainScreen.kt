package com.corgimemo.app.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.ui.components.AppDrawerContent
import com.corgimemo.app.ui.components.CategoryAction
import com.corgimemo.app.ui.components.EnhancedTopBar
import com.corgimemo.app.ui.components.LeftIconType
import com.corgimemo.app.ui.components.RightIconType
import com.corgimemo.app.ui.components.TodoMenuDropdown
import com.corgimemo.app.ui.components.InspirationMenuDropdown
import com.corgimemo.app.ui.components.FloatingCorgiButton
import com.corgimemo.app.ui.components.navigation.BubbleMenuOverlay
import com.corgimemo.app.ui.components.navigation.BubbleType
import com.corgimemo.app.ui.components.navigation.CorgiBottomNavigationBar
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.home.HomeBatchActionBar
import com.corgimemo.app.ui.screens.inspiration.components.InspirationBatchActionBar
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.home.shareTodoAsImage
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.InspirationViewModel
import com.corgimemo.app.ui.screens.inspiration.components.InspirationCalendarDialog
import com.corgimemo.app.ui.screens.home.components.TodoCalendarDialog
import com.corgimemo.app.ui.components.calendar.DatePickerRow
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.analytics.UserBehaviorAnalyzer
import com.corgimemo.app.analytics.UserBehaviorAnalyzerEntryPoint
import com.corgimemo.app.backup.exporter.ShareCoordinator
import com.corgimemo.app.ui.components.ShareModeDialog
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 主屏幕容器（统一版）
 *
 * 管理侧滑导航栏、顶部导航栏、底部导航栏、中央编辑按钮、气泡菜单和悬浮柯基按钮。
 * 三个核心页面（待办/灵感/日期）共享统一的导航框架。
 *
 * 布局架构：
 * ```
 * ModalNavigationDrawer
 * └── EnhancedTopBar + 页面内容 + FAB + 悬浮柯基 + 底部导航栏 + 中央编辑按钮 + 气泡菜单
 * ```
 *
 * Z轴层级（从底到顶）：
 * 1. 页面内容区域
 * 2. 悬浮柯基按钮
 * 3. 底部导航栏
 * 4. 中央编辑按钮
 * 5. 气泡菜单覆盖层
 *
 * @param navController 导航控制器
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(
    navController: NavController,
    backStackEntry: NavBackStackEntry? = null
) {
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }
    var isBubbleExpanded by remember { mutableStateOf(false) }
    var isFastCollapse by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    /**
     * 灵感页日历弹窗显示状态
     *
     * 由 MainScreen 统一管理，centerContent 点击时置 true，
     * 传递给 InspirationScreen 由其内部渲染 InspirationCalendarDialog。
     * 声明在 MainScreen 顶层以保证 topBar 与 content 区域均可访问。
     */
    var showInspirationCalendar by remember { mutableStateOf(false) }

    /**
     * 待办页日历弹窗显示状态
     *
     * 由 MainScreen 统一管理，centerContent 点击时置 true，
     * 传递给 TodoCalendarDialog 由其内部渲染。
     */
    var showTodoCalendar by remember { mutableStateOf(false) }

    /**
     * 监听其他页面返回时设置的 targetTab
     *
     * 例如：灵感编辑页退出时设置 savedStateHandle["targetTab"] = "INSPIRE"，
     * MainScreen 接收后切换到灵感 tab，确保从灵感编辑页退出后回到灵感页（而非待办页）。
     *
     * 使用 savedStateHandle.getStateFlow 监听值变化，消费后清除避免重复触发。
     */
    backStackEntry?.savedStateHandle?.let { handle ->
        val targetTab by handle.getStateFlow<String?>("targetTab", null)
            .collectAsStateWithLifecycle()
        LaunchedEffect(targetTab) {
            if (targetTab != null) {
                runCatching { TabItem.valueOf(targetTab!!) }.getOrNull()?.let {
                    selectedTab = it
                }
                // 消费后清除，避免重复触发
                handle["targetTab"] = null
            }
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    /** topBar 实际渲染高度（px），用于日历弹窗 topPadding 计算 */
    var topBarHeightPx by remember { mutableStateOf(0) }

    /**
     * 分享方式选择弹窗是否显示（多选模式）
     *
     * 当用户点击批量操作栏的"分享"按钮时，Coordinator 通过 onShowDialog 回调置为 true。
     * 用户选择合并/一条条 或 关闭弹窗时置为 false。
     */
    var showShareModeDialog by remember { mutableStateOf(false) }

    /**
     * 待分享的 todo 列表快照（多选模式，在弹窗打开时填充）
     *
     * 多选分享时由 selectedTodoIds 映射成的 TodoItem 列表；
     * 用于在弹窗的"合并/一条条"两个分支间共享同一份待分享数据。
     */
    var shareTodosSnapshot by remember { mutableStateOf<List<TodoItem>>(emptyList()) }

    val homeViewModel: HomeViewModel = hiltViewModel()
    /** 灵感页 ViewModel（用于日历弹窗获取灵感数据，与 InspirationScreen 共享同一实例） */
    val inspirationViewModel: InspirationViewModel = hiltViewModel()
    /** 灵感页标签相关状态（供侧边栏使用） */
    val inspirationTags by inspirationViewModel.savedTags.collectAsState()
    val selectedTags by inspirationViewModel.selectedTags.collectAsState()
    val tagFilterMode by inspirationViewModel.tagFilterMode.collectAsState()
    val tagCounts by inspirationViewModel.tagCounts.collectAsState()
    val totalInspirationCount by inspirationViewModel.totalInspirationCount.collectAsState()
    /**
     * 灵感页菜单与批量模式相关状态
     *
     * 与待办页对应状态（menuExpanded / isBatchMode / selectedTodoIds / hideDetails）同构，
     * 用于驱动灵感页的三点菜单弹窗（InspirationMenuDropdown）与批量操作栏（InspirationBatchActionBar）。
     */
    val inspirationMenuExpanded by inspirationViewModel.menuExpanded.collectAsState()
    val inspirationIsBatchMode by inspirationViewModel.isBatchMode.collectAsState()
    val inspirationSelectedIds by inspirationViewModel.selectedInspirationIds.collectAsState()
    val inspirationHideDetails by inspirationViewModel.hideDetails.collectAsState()
    /** 用户行为分析器（通过 Hilt 入口点获取 @Singleton 实例，用于记录页面访问） */
    val userBehaviorAnalyzer: UserBehaviorAnalyzer = remember {
        EntryPointAccessors.fromApplication(context, UserBehaviorAnalyzerEntryPoint::class.java).analyzer()
    }
    val corgiData by homeViewModel.corgiData.collectAsState()
    val categories by homeViewModel.categories.collectAsState()
    val todoCountByCategory by homeViewModel.todoCountByCategory.collectAsState()
    val selectedCategoryId by homeViewModel.selectedCategoryId.collectAsState()
    val currentMood by homeViewModel.currentMood.collectAsState()
    val currentPose by homeViewModel.currentPose.collectAsState()
    val hapticEnabled by homeViewModel.hapticEnabled.collectAsState()
    /** 是否有待办卡片的左滑操作区处于展开状态 */
    val swipeActionExpanded by homeViewModel.swipeActionExpanded.collectAsState()

    /**
     * 批量模式相关状态
     *
     * 用途：MainScreen 顶层监听 isBatchMode / selectedTodoIds / filteredTodos，
     * 用于：
     * 1. topBar 标题切换（"我的待办" ↔ "选中 X 项"）
     * 2. bottomBar 槽位互斥（底部导航栏 ↔ 批量操作栏）
     * 3. 批量模式时禁用侧滑菜单 / 隐藏右侧按钮
     */
    val isBatchMode by homeViewModel.isBatchMode.collectAsState()
    val selectedTodoIds by homeViewModel.selectedTodoIds.collectAsState()
    val filteredTodos by homeViewModel.filteredTodos.collectAsState()

    /**
     * 三点功能菜单状态（在 MainScreen 层订阅，供 EnhancedTopBar.dropdownContent 使用）
     *
     * 背景：菜单已从 HomeScreen 迁移到 EnhancedTopBar 内部渲染（解决 DropdownMenu
     * 锚点漂移问题），所以菜单所需的 ViewModel 状态需在 MainScreen 层收集。
     */
    val menuExpanded by homeViewModel.menuExpanded.collectAsState()
    val hideDetails by homeViewModel.hideDetails.collectAsState()
    val hideCompletedItems by homeViewModel.hideCompletedItems.collectAsState()

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    /** 灵感页批量删除确认弹窗显示状态（由 InspirationBatchActionBar 的删除按钮触发） */
    var showInspirationBatchDeleteDialog by remember { mutableStateOf(false) }
    var showRenameCategoryDialog by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }
    var showDeleteCategoryDialog by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }
    var showCategorySheet by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }

    val corgiPrefs = remember { CorgiPreferences.getInstance(context) }
    var corgiButtonPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var celebrationTrigger by remember { mutableLongStateOf(0L) }

    /**
     * 共享 SnackbarHost 状态
     *
     * 设计目标：让 SnackbarHost 通过 Material 3 Scaffold 的 snackbarHost 槽位渲染，
     * 自动管理 Snackbar 与 FAB / 底部导航栏的避让，避免与 FAB 重叠遮挡。
     *
     * 使用方式：
     * - 子页面（如 HomeScreen）通过参数接收该 state
     * - 调用 snackbarHostState.showSnackbar(...) 即可触发显示
     * - Scaffold 会自动调整位置避开 FAB / bottomBar
     */
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        corgiButtonPosition = corgiPrefs.getFloatingCorgiPosition()
    }

    val topBarTitle = when (selectedTab) {
        TabItem.TODO -> {
            when (selectedCategoryId) {
                null -> "📝 待办"
                0L -> "📦 未分类"
                else -> categories.find { it.id == selectedCategoryId }?.let { "📁 ${it.name}" } ?: "📝 待办"
            }
        }
        TabItem.INSPIRE -> "💡 灵感"
        TabItem.DATE -> "📅 日期"
        TabItem.PROFILE -> "👤 我的"
        TabItem.EDIT -> ""
    }

    /**
     * 顶部栏右侧图标类型
     *
     * - TODO / INSPIRE / DATE：使用三点菜单（MORE_MENU），提供功能菜单入口
     * - PROFILE：使用柯基爪子图标（CORGIE），跳转柯基详情
     * - EDIT：中央编辑按钮非真实 Tab，使用默认 CORGIE
     */
    val rightIconType = when (selectedTab) {
        TabItem.TODO -> RightIconType.MORE_MENU
        TabItem.INSPIRE -> RightIconType.MORE_MENU
        TabItem.DATE -> RightIconType.MORE_MENU
        TabItem.PROFILE -> RightIconType.CORGIE
        else -> RightIconType.CORGIE
    }

    /**
     * 三点菜单点击回调
     *
     * - TODO：展开 HomeScreen 的 TodoMenuDropdown（通过 ViewModel 状态触发）
     * - INSPIRE / DATE：暂未实现，弹 Toast 提示
     * - 其他：无回调（CORGIE 图标走 onCorgiClick）
     */
    val onMoreClick: (() -> Unit)? = when (selectedTab) {
        TabItem.TODO -> {
            { homeViewModel.setMenuExpanded(true) }
        }
        TabItem.INSPIRE -> {
            { inspirationViewModel.setMenuExpanded(true) }
        }
        TabItem.DATE -> {
            { Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show() }
        }
        else -> null
    }

    val topBarActionButtons: List<@Composable () -> Unit> = when (selectedTab) {
        TabItem.TODO -> listOf({
            IconButton(onClick = { navController.navigate(Screen.Stats.route) }) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "统计与排序",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        })
        TabItem.INSPIRE -> listOf({
            IconButton(onClick = { navController.navigate(Screen.InspirationStats.route) }) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "字数统计",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        })
        TabItem.DATE -> listOf({
            IconButton(onClick = { /* TODO: 排序选项 */ }) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "排序",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        })
        else -> emptyList()
    }

    BackHandler(enabled = isBubbleExpanded) {
        isBubbleExpanded = false
    }

    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch { drawerState.close() }
    }

    /**
     * 灵感页批量模式返回拦截：按返回键退出批量选择而非退出页面
     *
     * 仅在 inspirationIsBatchMode 为 true 时生效，避免与普通返回逻辑冲突。
     */
    BackHandler(enabled = inspirationIsBatchMode) {
        inspirationViewModel.exitBatchMode()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                AppDrawerContent(
                    currentTab = selectedTab,
                    corgiData = corgiData,
                    categories = categories,
                    todoCountByCategory = todoCountByCategory,
                    selectedCategoryId = selectedCategoryId,
                    inspirationTags = inspirationTags,
                    selectedTags = selectedTags,
                    tagFilterMode = tagFilterMode,
                    tagCounts = tagCounts,
                    totalInspirationCount = totalInspirationCount,
                    onCategoryClick = { categoryId ->
                        homeViewModel.filterByCategory(categoryId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onAddCategoryClick = { showAddCategoryDialog = true },
                    onCategoryAction = { action ->
                        when (action) {
                            is CategoryAction.ShowMenu -> showCategorySheet = action.category
                            is CategoryAction.Pin -> { /* TODO */ }
                            is CategoryAction.Rename -> showRenameCategoryDialog = action.category
                            is CategoryAction.Delete -> showDeleteCategoryDialog = action.category
                        }
                    },
                    onTagClick = { tag ->
                        inspirationViewModel.toggleTagSelection(tag)
                    },
                    onTagFilterModeChange = { mode ->
                        inspirationViewModel.setTagFilterMode(mode)
                    },
                    onClearTagSelection = {
                        inspirationViewModel.clearTagSelection()
                    },
                    onAddTagClick = { showAddTagDialog = true },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onHelpClick = {
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        // 外层 Box：包裹 Scaffold + 日历弹窗，让弹窗能覆盖全屏（包括底部导航栏）
        /**
         * 有效批量模式判断：根据当前 Tab 选择对应的批量模式状态
         *
         * 待办页使用 homeViewModel.isBatchMode，灵感页使用 inspirationViewModel.isBatchMode，
         * 其他页面无批量模式。统一为 effectiveBatchMode 后，topBar / bottomBar 各处判断无需再区分 Tab。
         * 声明在 Scaffold 之前，确保 topBar 与 bottomBar 两个 lambda 均可访问。
         */
        val effectiveBatchMode = when (selectedTab) {
            TabItem.TODO -> isBatchMode
            TabItem.INSPIRE -> inspirationIsBatchMode
            else -> false
        }
        val effectiveSelectedCount = when (selectedTab) {
            TabItem.TODO -> selectedTodoIds.size
            TabItem.INSPIRE -> inspirationSelectedIds.size
            else -> 0
        }
        Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (selectedTab != TabItem.EDIT) {
                    /**
                     * 批量模式时：title 切换为"选中 X 项"，隐藏菜单/柯基/统计按钮，
                     * 避免与批量操作 UI 冲突。
                     */
                    val batchModeTitle = if (effectiveBatchMode) "选中 $effectiveSelectedCount 项" else null
                    val effectiveTitle = batchModeTitle ?: topBarTitle
                    val effectiveActionButtons = if (effectiveBatchMode) emptyList() else topBarActionButtons
                    val effectiveMenuEnabled = !effectiveBatchMode

                    EnhancedTopBar(
                        title = effectiveTitle,
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            topBarHeightPx = coordinates.size.height
                        },
                        /**
                         * 灵感页中间内容：大号日期 + 月份 + 下拉箭头，点击触发日历弹窗。
                         * 仅在灵感页且非批量模式时显示，其他页面使用默认 title。
                         */
                        centerContent = if ((selectedTab == TabItem.INSPIRE || selectedTab == TabItem.TODO) && !effectiveBatchMode) {
                            {
                                if (selectedTab == TabItem.TODO) {
                                    DatePickerRow(
                                        isExpanded = showTodoCalendar,
                                        onClick = { showTodoCalendar = !showTodoCalendar }
                                    )
                                } else {
                                    DatePickerRow(
                                        isExpanded = showInspirationCalendar,
                                        onClick = { showInspirationCalendar = !showInspirationCalendar }
                                    )
                                }
                            }
                        } else null,
                        /**
                         * 批量模式时：左侧图标变为返回箭头（LeftIconType.BACK），点击触发
                         * homeViewModel.exitBatchMode() 退出多选；普通模式沿用 MENU + onMenuClick。
                         * 注意：EnhancedTopBar 的 onMenuClick 没有 enabled 参数，因此批量模式
                         * 不再依赖 effectiveMenuEnabled 抑制开抽屉——直接交给 onLeftIconClick 处理。
                         */
                        onMenuClick = {
                            coroutineScope.launch { drawerState.open() }
                        },
                        onCorgiClick = {
                            if (effectiveMenuEnabled) {
                                navController.navigate(Screen.CorgiDetail.route)
                            }
                        },
                        actionButtons = effectiveActionButtons,
                        /**
                         * 批量模式时强制使用柯基图标并禁用三点菜单回调，
                         * 避免批量操作期间误触发功能菜单。
                         */
                        rightIconType = if (effectiveBatchMode) RightIconType.CORGIE else rightIconType,
                        onMoreClick = if (effectiveBatchMode) null else onMoreClick,
                        /**
                         * 三点功能菜单内容（TODO / INSPIRE Tab 注入）
                         *
                         * 关键：菜单必须在 EnhancedTopBar 内部与 IconButton 共同 Box 渲染，
                         * 才能保证 Material 3 DropdownMenu 锚定到三点按钮正下方。
                         * 若在 HomeScreen 或外层 Box 渲染，菜单会锚定到外层 Box 左下角。
                         */
                        dropdownContent = if (selectedTab == TabItem.TODO && !effectiveBatchMode) {
                            {
                                TodoMenuDropdown(
                                    expanded = menuExpanded,
                                    onDismiss = { homeViewModel.setMenuExpanded(false) },
                                    hideDetails = hideDetails,
                                    onToggleHideDetails = { homeViewModel.toggleHideDetails() },
                                    hideCompletedItems = hideCompletedItems,
                                    onToggleHideCompletedItems = { homeViewModel.toggleHideCompletedItems() },
                                    onSortClick = { homeViewModel.setShowSortSheet(true) },
                                    onBatchSelectClick = {
                                        filteredTodos.firstOrNull()?.let { homeViewModel.enterBatchMode(it.id) }
                                    },
                                    onPlaceholderClick = {
                                        Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show()
                                    },
                                    onRecycleBinClick = { navController.navigate(Screen.RecycleBin.createRoute("todo")) }
                                )
                            }
                        } else if (selectedTab == TabItem.INSPIRE && !effectiveBatchMode) {
                            {
                                InspirationMenuDropdown(
                                    expanded = inspirationMenuExpanded,
                                    onDismiss = { inspirationViewModel.setMenuExpanded(false) },
                                    hideDetails = inspirationHideDetails,
                                    onToggleHideDetails = { inspirationViewModel.toggleHideDetails() },
                                    onBatchSelectClick = { inspirationViewModel.enterBatchMode() },
                                    onPlaceholderClick = { Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show() },
                                    onRecycleBinClick = { navController.navigate(Screen.RecycleBin.createRoute("inspiration")) }
                                )
                            }
                        } else null,
                        /**
                         * 批量模式时：左侧图标变为返回箭头，点击退出多选模式。
                         * 普通模式：保持默认 MENU 图标，点击开抽屉（onLeftIconClick=null 时回退到 onMenuClick）。
                         */
                        leftIconType = if (effectiveBatchMode) LeftIconType.BACK else LeftIconType.MENU,
                        onLeftIconClick = if (effectiveBatchMode) {
                            {
                                when (selectedTab) {
                                    TabItem.TODO -> homeViewModel.exitBatchMode()
                                    TabItem.INSPIRE -> inspirationViewModel.exitBatchMode()
                                    else -> {}
                                }
                            }
                        } else null
                    )
                }
            },
            bottomBar = {
                /**
                 * 底部槽位：批量模式时显示批量操作栏，普通模式时显示底部导航栏。
                 * 三路判断：TODO 批量 / INSPIRE 批量 / 普通，三者互斥避免遮挡。
                 */
                when {
                    effectiveBatchMode && selectedTab == TabItem.TODO -> {
                        /**
                         * 待办页批量操作栏（从 HomeScreen 提取）
                         *
                         * 只在 TODO tab 下显示，避免在灵感/日期/我的 tab 下误触发。
                         * 回调：
                         * - 全选/取消全选：viewModel 直接处理
                         * - 分享：遍历 selectedTodoIds 调用本地 shareTodoAsImage
                         * - 移动/删除/更多：触发 viewModel 中的对应 StateFlow，
                         *   HomeScreen 内部 subscribe 并渲染弹窗
                         */
                        HomeBatchActionBar(
                            isBatchMode = isBatchMode,
                            selectedTodoIds = selectedTodoIds,
                            filteredTodos = filteredTodos,
                            onSelectAll = { homeViewModel.selectAll() },
                            onClearSelection = { homeViewModel.clearSelection() },
                            onShare = {
                                /**
                                 * 多选模式分享：
                                 * 1. 用 selectedTodoIds 从 filteredTodos 找出完整 TodoItem 列表
                                 * 2. 调用 ShareCoordinator.shareTodos() 统一处理（单/多分支）
                                 * 3. 多个时由 Coordinator 回调 onShowDialog → 显示选择弹窗
                                 */
                                val todos = selectedTodoIds.mapNotNull { id ->
                                    filteredTodos.find { it.id == id }
                                }
                                if (todos.isNotEmpty()) {
                                    shareTodosSnapshot = todos
                                    coroutineScope.launch {
                                        ShareCoordinator.shareTodos(
                                            context = context,
                                            todos = todos,
                                            categories = categories,
                                            onShowDialog = { _ ->
                                                showShareModeDialog = true
                                            }
                                        )
                                    }
                                }
                            },
                            onMove = { homeViewModel.setShowBatchMoveDialog(true) },
                            onDelete = { homeViewModel.setShowBatchDeleteDialog(true) },
                            onMoreOptions = { homeViewModel.setShowMoreOptionsSheet(true) }
                        )
                    }
                    effectiveBatchMode && selectedTab == TabItem.INSPIRE -> {
                        /**
                         * 灵感页批量操作栏
                         *
                         * 仅在 INSPIRE tab 批量模式下显示。
                         * 回调：
                         * - 全选/取消全选：inspirationViewModel 直接处理
                         * - 删除：触发 showInspirationBatchDeleteDialog 弹窗确认
                         * - 置顶：调用 inspirationViewModel.batchPinInspirations()
                         */
                        InspirationBatchActionBar(
                            isBatchMode = inspirationIsBatchMode,
                            selectedInspirationIds = inspirationSelectedIds,
                            totalInspirationCount = totalInspirationCount,
                            onSelectAll = { inspirationViewModel.selectAllInspirations() },
                            onClearSelection = { inspirationViewModel.clearSelection() },
                            onDelete = { showInspirationBatchDeleteDialog = true },
                            onPin = { inspirationViewModel.batchPinInspirations() }
                        )
                    }
                    else -> {
                        /**
                         * 普通模式：底部导航栏（集成中央编辑按钮）
                         *
                         * 架构变更：CenterEditButton 已从独立 overlay 移入 CorgiBottomNavigationBar 内部
                         * 优势：无层级遮挡、按钮完整可见、统一管理安全区域
                         */
                        CorgiBottomNavigationBar(
                            selectedTab = selectedTab,
                            isExpanded = isBubbleExpanded,
                            onCenterButtonClick = {
                                val now = System.currentTimeMillis()
                                if (now - lastClickTime > 300) {
                                    lastClickTime = now
                                    isBubbleExpanded = !isBubbleExpanded
                                }
                            },
                            onTabSelected = { tab ->
                                if (isBubbleExpanded) {
                                    isFastCollapse = true
                                    isBubbleExpanded = false
                                }

                                // 记录页面访问（用于智能预加载策略优化）
                                val pageType = when (tab) {
                                    TabItem.TODO -> UserBehaviorAnalyzer.PageType.HOME
                                    TabItem.INSPIRE -> UserBehaviorAnalyzer.PageType.INSPIRATION
                                    TabItem.DATE -> UserBehaviorAnalyzer.PageType.SPECIAL_DATE
                                    else -> null
                                }
                                pageType?.let { type ->
                                    coroutineScope.launch { userBehaviorAnalyzer.recordPageVisit(type) }
                                }

                                // Tab 切换时关闭日历弹窗，避免切换到其他页面时弹窗残留
                                showInspirationCalendar = false
                                showTodoCalendar = false

                                selectedTab = tab
                            }
                        )
                    }
                }
            },
            /**
             * 共享 Snackbar 槽位
             *
             * 使用 Material 3 Scaffold 的 snackbarHost 槽位，让 Scaffold 自动管理
             * Snackbar 与 FAB / 底部导航栏的避让，避免手动定位时被遮挡。
             *
             * 子页面（HomeScreen）通过参数接收 snackbarHostState，调用 showSnackbar() 即可触发显示。
             */
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            /**
             * 动态计算导航栏总高度（含安全区域）
             * = Scaffold 底部内边距(含系统导航栏) + 中央按钮凸起量(36dp)
             * 传递给气泡菜单，确保气泡不遮挡导航栏
             */
            val dynamicNavBarHeight = paddingValues.calculateBottomPadding() + 36.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (selectedTab) {
                        TabItem.TODO -> HomeScreen(
                            navController = navController,
                            onFabClick = {
                                homeViewModel.onUserInteraction()
                                homeViewModel.setPoseForCreating()
                                navController.navigate("todo_edit")
                            },
                            viewModel = homeViewModel,
                            snackbarHostState = snackbarHostState
                        )
                        TabItem.INSPIRE -> InspirationScreen(
                            navController = navController,
                            onFabClick = { navController.navigate("inspiration_edit") }
                        )
                        TabItem.DATE -> SpecialDateScreen(
                            navController = navController,
                            onFabClick = { navController.navigate("date_edit") }
                        )
                        TabItem.PROFILE -> ProfileScreen(navController)
                        TabItem.EDIT -> { /* 中央编辑按钮不是真实Tab */ }
                    }
                }

                FloatingCorgiButton(
                    onClick = { navController.navigate(Screen.CorgiDetail.route) },
                    onPositionChanged = { x, y ->
                        coroutineScope.launch { corgiPrefs.saveFloatingCorgiPosition(x, y) }
                    },
                    onSwipeLeft = { navController.navigate("todo_edit") },
                    onSwipeRight = { navController.navigate(Screen.CorgiDetail.route) },
                    initialPosition = corgiButtonPosition,
                    triggerCelebration = celebrationTrigger,
                    currentMood = currentMood,
                    modifier = Modifier.padding(paddingValues)
                )

                /** 中央编辑按钮已移入 CorgiBottomNavigationBar 内部，无需在此处单独渲染 */

                if (isBubbleExpanded) {
                    BubbleMenuOverlay(
                        isExpanded = isBubbleExpanded,
                        isFastCollapse = isFastCollapse,
                        onDismiss = {
                            isBubbleExpanded = false
                            isFastCollapse = false
                        },
                        onBubbleClick = { bubbleType ->
                            isBubbleExpanded = false
                            isFastCollapse = false
                            when (bubbleType) {
                                BubbleType.CREATE_TODO -> navController.navigate("todo_edit")
                                BubbleType.RECORD_INSPIRE -> navController.navigate("inspiration_edit")
                                BubbleType.SPECIAL_DATE -> navController.navigate("date_edit")
                            }
                        },
                        navBarHeight = dynamicNavBarHeight,  // 动态值：Scaffold底部内边距 + 按钮凸起量
                        context = context,
                        hapticEnabled = hapticEnabled
                    )
                }
            }
        } // Scaffold content lambda 闭合

        // 灵感页日历弹窗（在 Scaffold 外部、外层 Box 内部渲染，覆盖全屏包括底部导航栏）
        if (showInspirationCalendar && selectedTab == TabItem.INSPIRE) {
            InspirationCalendarDialog(
                inspirationCountByDate = { year, month ->
                    inspirationViewModel.getCalendarInspirationCount(year, month)
                },
                getInspirationsByDate = { year, month, day ->
                    inspirationViewModel.getInspirationsByDate(year, month, day)
                },
                onInspirationClick = { inspiration ->
                    showInspirationCalendar = false
                    navController.navigate("inspiration_edit/${inspiration.id}")
                },
                onDismiss = { showInspirationCalendar = false },
                topPadding = with(density) { topBarHeightPx.toDp() }
            )
        }

        // 待办页日历弹窗（在 Scaffold 外部、外层 Box 内部渲染，覆盖全屏包括底部导航栏）
        if (showTodoCalendar && selectedTab == TabItem.TODO) {
            TodoCalendarDialog(
                todoCountByDate = { year, month ->
                    homeViewModel.getCalendarTodoCount(year, month)
                },
                getTodosByDate = { year, month, day ->
                    homeViewModel.getTodosByDate(year, month, day)
                },
                onTodoClick = { todo ->
                    showTodoCalendar = false
                    homeViewModel.requestScrollToTodo(todo.id)
                },
                onDismiss = { showTodoCalendar = false },
                topPadding = with(density) { topBarHeightPx.toDp() }
            )
        }
        } // 外层 Box 闭合
    }

    if (showAddCategoryDialog) {
        com.corgimemo.app.ui.components.AddCategoryDialog(
            onConfirm = { name ->
                homeViewModel.createCategory(name)
                showAddCategoryDialog = false
            },
            onDismiss = { showAddCategoryDialog = false }
        )
    }

    if (showAddTagDialog) {
        com.corgimemo.app.ui.components.AddCategoryDialog(
            onConfirm = { name ->
                inspirationViewModel.addUserTag(name)
                showAddTagDialog = false
            },
            onDismiss = { showAddTagDialog = false },
            title = "新建标签",
            label = "标签名称"
        )
    }

    showRenameCategoryDialog?.let { category ->
        com.corgimemo.app.ui.components.RenameCategoryDialog(
            currentName = category.name,
            onConfirm = { newName ->
                homeViewModel.renameCategory(category.id, newName)
                showRenameCategoryDialog = null
            },
            onDismiss = { showRenameCategoryDialog = null }
        )
    }

    showDeleteCategoryDialog?.let { category ->
        com.corgimemo.app.ui.components.DeleteCategoryConfirmDialog(
            categoryName = category.name,
            onConfirm = {
                homeViewModel.deleteCategory(category.id)
                showDeleteCategoryDialog = null
            },
            onDismiss = { showDeleteCategoryDialog = null }
        )
    }

    showCategorySheet?.let { category ->
        com.corgimemo.app.ui.components.CategoryOperationSheet(
            category = category,
            onPin = { /* TODO */ },
            onRename = {
                showRenameCategoryDialog = category
                showCategorySheet = null
            },
            onDelete = {
                showDeleteCategoryDialog = category
                showCategorySheet = null
            },
            onDismiss = { showCategorySheet = null }
        )
    }

    /**
     * 分享方式选择弹窗（多选模式）
     *
     * 当用户点击批量操作栏的"分享"按钮时，onShare 内 Coordinator 通过回调设置
     * shareTodosSnapshot + showShareModeDialog=true 触发显示。
     * 弹窗中提供"合并分享/一条条分享/取消"三种操作。
     */
    if (showShareModeDialog) {
        val enableMerge = shareTodosSnapshot.size <= 10
        ShareModeDialog(
            count = shareTodosSnapshot.size,
            enableMerge = enableMerge,
            onDismiss = { showShareModeDialog = false },
            onMerge = {
                showShareModeDialog = false
                coroutineScope.launch {
                    ShareCoordinator.shareMerged(
                        context = context,
                        todos = shareTodosSnapshot,
                        categories = categories,
                        onShowSnackBar = { msg ->
                            android.widget.Toast.makeText(
                                context,
                                msg,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            },
            onOneByOne = {
                showShareModeDialog = false
                coroutineScope.launch {
                    ShareCoordinator.shareOneByOne(
                        context = context,
                        todos = shareTodosSnapshot,
                        categories = categories
                    )
                }
            }
        )
    }

    /**
     * 灵感页批量删除确认弹窗
     *
     * 由 InspirationBatchActionBar 的 onDelete 回调触发（showInspirationBatchDeleteDialog = true）。
     * 确认后调用 inspirationViewModel.batchDeleteInspirations() 执行删除并退出批量模式。
     */
    if (showInspirationBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showInspirationBatchDeleteDialog = false },
            title = { Text("删除选中项") },
            text = {
                Text("确定要删除已选择的 ${inspirationSelectedIds.size} 条灵感吗？\n此操作不可撤销。")
            },
            confirmButton = {
                TextButton(onClick = {
                    inspirationViewModel.batchDeleteInspirations()
                    showInspirationBatchDeleteDialog = false
                }) {
                    Text("删除", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showInspirationBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
