package com.corgimemo.app.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.ui.components.AppDrawerContent
import com.corgimemo.app.ui.components.CategoryAction
import com.corgimemo.app.ui.components.EnhancedTopBar
import com.corgimemo.app.ui.components.RightIconType
import com.corgimemo.app.ui.components.TodoMenuDropdown
import com.corgimemo.app.ui.components.FloatingCorgiButton
import com.corgimemo.app.ui.components.navigation.BubbleMenuOverlay
import com.corgimemo.app.ui.components.navigation.BubbleType
import com.corgimemo.app.ui.components.navigation.CorgiBottomNavigationBar
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.home.HomeBatchActionBar
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.home.shareTodoAsImage
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.analytics.UserBehaviorAnalyzer
import com.corgimemo.app.analytics.UserBehaviorAnalyzerEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

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
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(TabItem.TODO) }
    var isBubbleExpanded by remember { mutableStateOf(false) }
    var isFastCollapse by remember { mutableStateOf(false) }
    var lastClickTime by remember { mutableLongStateOf(0L) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val homeViewModel: HomeViewModel = hiltViewModel()
    /** 用户行为分析器（通过 Hilt 入口点获取 @Singleton 实例，用于记录页面访问） */
    val userBehaviorAnalyzer: UserBehaviorAnalyzer = remember {
        EntryPointAccessors.fromApplication(context, UserBehaviorAnalyzerEntryPoint::class.java).analyzer()
    }
    val corgiData by homeViewModel.corgiData.collectAsState()
    val categories by homeViewModel.categories.collectAsState()
    val todoCountByCategory by homeViewModel.todoCountByCategory.collectAsState()
    val recentlyDeletedCount by homeViewModel.recentlyDeletedCount.collectAsState()
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
    var showRenameCategoryDialog by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }
    var showDeleteCategoryDialog by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }
    var showCategorySheet by remember { mutableStateOf<com.corgimemo.app.data.model.Category?>(null) }

    val corgiPrefs = remember { CorgiPreferences.getInstance(context) }
    var corgiButtonPosition by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var celebrationTrigger by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        corgiButtonPosition = corgiPrefs.getFloatingCorgiPosition()
    }

    val topBarTitle = when (selectedTab) {
        TabItem.TODO -> {
            when (selectedCategoryId) {
                null -> "📝 我的待办"
                0L -> "📦 未分类"
                else -> categories.find { it.id == selectedCategoryId }?.let { "📁 ${it.name}" } ?: "📝 我的待办"
            }
        }
        TabItem.INSPIRE -> "💡 我的灵感"
        TabItem.DATE -> "📅 特殊日期"
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
            { Toast.makeText(context, "功能开发中...", Toast.LENGTH_SHORT).show() }
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
            IconButton(onClick = { /* TODO: 标签管理 */ }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Label,
                    contentDescription = "标签管理",
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(280.dp)) {
                AppDrawerContent(
                    currentTab = selectedTab,
                    corgiData = corgiData,
                    categories = categories,
                    todoCountByCategory = todoCountByCategory,
                    recentlyDeletedCount = recentlyDeletedCount,
                    selectedCategoryId = selectedCategoryId,
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
                    onRecentlyDeletedClick = {
                        coroutineScope.launch { drawerState.close() }
                    },
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
        Scaffold(
            topBar = {
                if (selectedTab != TabItem.EDIT) {
                    /**
                     * 批量模式时：title 切换为"选中 X 项"，隐藏菜单/柯基/统计按钮，
                     * 避免与批量操作 UI 冲突。
                     */
                    val batchModeTitle = if (isBatchMode) "选中 ${selectedTodoIds.size} 项" else null
                    val effectiveTitle = batchModeTitle ?: topBarTitle
                    val effectiveActionButtons = if (isBatchMode) emptyList() else topBarActionButtons
                    val effectiveMenuEnabled = !isBatchMode

                    EnhancedTopBar(
                        title = effectiveTitle,
                        /**
                         * 批量模式时禁用菜单按钮（点击不开抽屉），避免误操作。
                         * 注意：EnhancedTopBar 的 onMenuClick 没有 enabled 参数，
                         * 因此通过传入"空回调"实现禁用。
                         */
                        onMenuClick = {
                            if (effectiveMenuEnabled) {
                                coroutineScope.launch { drawerState.open() }
                            }
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
                        rightIconType = if (isBatchMode) RightIconType.CORGIE else rightIconType,
                        onMoreClick = if (isBatchMode) null else onMoreClick,
                        /**
                         * 三点功能菜单内容（仅 TODO Tab 注入）
                         *
                         * 关键：菜单必须在 EnhancedTopBar 内部与 IconButton 共同 Box 渲染，
                         * 才能保证 Material 3 DropdownMenu 锚定到三点按钮正下方。
                         * 若在 HomeScreen 或外层 Box 渲染，菜单会锚定到外层 Box 左下角。
                         */
                        dropdownContent = if (selectedTab == TabItem.TODO && !isBatchMode) {
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
                                    }
                                )
                            }
                        } else null
                    )
                }
            },
            bottomBar = {
                /**
                 * 底部槽位：批量模式时显示 HomeBatchActionBar（"全选+4图标"），
                 * 普通模式时显示 CorgiBottomNavigationBar（底部导航栏）。
                 * 两者互斥，避免遮挡问题。
                 */
                if (isBatchMode && selectedTab == TabItem.TODO) {
                    /**
                     * 批量操作栏（从 HomeScreen 提取）
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
                             * 遍历选中项，对每个待办生成图片并弹出系统分享面板。
                             * 复用 HomeScreen 中提取的 shareTodoAsImage 函数。
                             */
                            selectedTodoIds.forEach { id ->
                                val todo = filteredTodos.find { it.id == id }
                                if (todo != null) {
                                    shareTodoAsImage(context, todo, categories)
                                }
                            }
                        },
                        onMove = { homeViewModel.setShowBatchMoveDialog(true) },
                        onDelete = { homeViewModel.setShowBatchDeleteDialog(true) },
                        onMoreOptions = { homeViewModel.setShowMoreOptionsSheet(true) }
                    )
                } else {
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

                            selectedTab = tab
                        }
                    )
                }
            }
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
                            viewModel = homeViewModel
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
        }
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
}
