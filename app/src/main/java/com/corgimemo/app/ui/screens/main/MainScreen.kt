package com.corgimemo.app.ui.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Label
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
import com.corgimemo.app.ui.components.FloatingCorgiButton
import com.corgimemo.app.ui.components.navigation.BubbleMenuOverlay
import com.corgimemo.app.ui.components.navigation.BubbleType
import com.corgimemo.app.ui.components.navigation.CorgiBottomNavigationBar
import com.corgimemo.app.ui.components.navigation.CenterEditButton
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.viewmodel.HomeViewModel
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
    val corgiData by homeViewModel.corgiData.collectAsState()
    val categories by homeViewModel.categories.collectAsState()
    val todoCountByCategory by homeViewModel.todoCountByCategory.collectAsState()
    val recentlyDeletedCount by homeViewModel.recentlyDeletedCount.collectAsState()
    val selectedCategoryId by homeViewModel.selectedCategoryId.collectAsState()
    val currentMood by homeViewModel.currentMood.collectAsState()
    val currentPose by homeViewModel.currentPose.collectAsState()

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
                    imageVector = Icons.Default.Label,
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
                    EnhancedTopBar(
                        title = topBarTitle,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        onCorgiClick = { navController.navigate(Screen.CorgiDetail.route) },
                        actionButtons = topBarActionButtons
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(bottom = 72.dp)
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
                            onFabClick = { navController.navigate("inspiration_edit") },
                            corgiData = corgiData,
                            currentPose = currentPose,
                            currentMood = currentMood
                        )
                        TabItem.DATE -> SpecialDateScreen(
                            navController = navController,
                            onFabClick = { navController.navigate("date_edit") },
                            corgiData = corgiData,
                            currentPose = currentPose,
                            currentMood = currentMood
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

                CorgiBottomNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        if (isBubbleExpanded) {
                            isFastCollapse = true
                            isBubbleExpanded = false
                        }
                        selectedTab = tab
                    },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = (-28).dp),
                    contentAlignment = Alignment.Center
                ) {
                    CenterEditButton(
                        isExpanded = isBubbleExpanded,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (now - lastClickTime > 300) {
                                lastClickTime = now
                                isBubbleExpanded = !isBubbleExpanded
                            }
                        }
                    )
                }

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
                        }
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
