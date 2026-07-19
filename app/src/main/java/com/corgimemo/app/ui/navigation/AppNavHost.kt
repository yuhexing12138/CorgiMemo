package com.corgimemo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.ui.screens.achievement.AchievementScreen
import com.corgimemo.app.ui.screens.backup.BackupHistoryScreen
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.main.MainScreen
import com.corgimemo.app.ui.screens.onboarding.OnboardingScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.ui.screens.settings.OperationHistoryScreen
import com.corgimemo.app.ui.screens.settings.SettingsScreen
import com.corgimemo.app.ui.screens.stats.StatsScreen
import com.corgimemo.app.ui.screens.todo.TodoEditScreen
import com.corgimemo.app.ui.screens.corgi.CorgiDetailScreen
import com.corgimemo.app.ui.screens.date.DateScreenPlaceholder
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.date.SpecialDateQuickCreateScreen
import com.corgimemo.app.ui.screens.date.SpecialDateCardStyleScreen
import com.corgimemo.app.ui.screens.date.SpecialDateDetailScreen
import com.corgimemo.app.ui.screens.inspire.InspireScreenPlaceholder
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.inspiration.InspirationEditScreen
import com.corgimemo.app.ui.screens.inspiration.stats.ChartFullscreenScreen
import com.corgimemo.app.ui.screens.inspiration.stats.InspirationStatsScreen
import com.corgimemo.app.ui.screens.date.stats.DateStatsScreen
import com.corgimemo.app.ui.screens.common.ImagePreviewScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    onExportClick: (BackupManager.ExportFormat) -> Unit = {},
    onImportClick: () -> Unit = {}
) {
    /** 获取协程作用域和上下文（用于编辑历史回调） */
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(navController = navController)
        }

        composable(Screen.Home.route) { backStackEntry ->
            MainScreen(navController = navController, backStackEntry = backStackEntry)
        }

        composable(Screen.TodoEdit.route) {
            TodoEditScreen(navController = navController)
        }

        composable(Screen.TodoEditWithId.route) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getString("todoId")?.toLongOrNull()
            TodoEditScreen(navController = navController, todoId = todoId)
        }

        composable(Screen.Profile.route) {
            ProfileScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                onExportClick = onExportClick,
                onImportClick = onImportClick
            )
        }

        composable(Screen.BackupHistory.route) {
            BackupHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Stats.route) {
            StatsScreen(navController = navController)
        }

        composable(Screen.Achievement.route) {
            AchievementScreen(navController = navController)
        }

        composable(Screen.CorgiDetail.route) {
            CorgiDetailScreen(navController = navController)
        }

        /** 装扮详情页（从"我的"页外移，承载柯基动画+预览模式+横滑列表） */
        composable(Screen.Outfit.route) {
            com.corgimemo.app.ui.screens.outfit.OutfitScreen(navController = navController)
        }

        composable(Screen.OperationHistory.route) {
            OperationHistoryScreen(
                onBack = { navController.popBackStack() },
                /**
                 * V2.7: 设置页入口 → 编辑历史（读取最后编辑的 Todo ID）
                 *
                 * 从 CorgiPreferences 读取最近编辑的 Todo ID，
                 * 传递给 EditHistory 页面以加载对应的编辑历史。
                 */
                onEditHistory = {
                    val prefs = com.corgimemo.app.data.local.datastore.CorgiPreferences.getInstance(context)
                    coroutineScope.launch {
                        val lastTodoId = prefs.getLastEditedTodoId()
                        if (lastTodoId > 0) {
                            navController.navigate(Screen.EditHistory.createRoute(lastTodoId))
                        } else {
                            /** 无编辑历史时仍导航到页面，显示空状态 */
                            navController.navigate(Screen.EditHistory.route)
                        }
                    }
                }
            )
        }

        /** 编辑历史时间线页面（V2.6: 支持todoId参数+点击恢复） */
        composable(
            route = Screen.EditHistory.route,
            arguments = listOf(
                androidx.navigation.navArgument("todoId") {
                    type = androidx.navigation.NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val todoId = backStackEntry.arguments?.getLong("todoId", -1L) ?: -1L

            /**
             * V2.7: 点击恢复回调 — 使用 SavedStateHandle Result API
             * 将目标 AnnotatedString 的原始序列化 JSON 写入前一个页面的 savedStateHandle，
             * 然后返回上一页，由编辑器页面反序列化为完整格式（含 SpanStyle）。
             */
            val onRestoreText: (String) -> Unit = { annotatedJson ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("restore_text", annotatedJson)
                navController.popBackStack()
            }

            com.corgimemo.app.ui.screens.settings.EditHistoryScreen(
                onBack = { navController.popBackStack() },
                todoId = todoId,
                onRestoreText = onRestoreText
            )
        }

        // 新增：底部导航栏页面路由
        composable(Screen.Inspire.route) {
            InspirationScreen(navController = navController)
        }

        composable(Screen.Date.route) {
            SpecialDateScreen(navController = navController)
        }

        // 特殊日期快速创建页面路由（重构版：4 行核心 + 下一步）
        composable(route = Screen.SpecialDateQuickCreate.route) {
            SpecialDateQuickCreateScreen(navController = navController)
        }

        // 特殊日期卡片样式选择页路由（新建日期流程第二步：选样式 + 保存落库）
        // 参数：title(String) / date(Long) / category(String) / pin(Boolean)
        composable(
            route = Screen.SpecialDateCardStyle.route,
            arguments = listOf(
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("date") { type = NavType.LongType; defaultValue = 0L },
                navArgument("category") { type = NavType.StringType; defaultValue = "OTHER" },
                navArgument("pin") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            // 从 NavBackStackEntry.arguments 中读取 4 个路由参数
            val title = entry.arguments?.getString("title").orEmpty()
            val date = entry.arguments?.getLong("date") ?: 0L
            val category = entry.arguments?.getString("category") ?: "OTHER"
            val pin = entry.arguments?.getBoolean("pin") ?: false
            SpecialDateCardStyleScreen(
                navController = navController,
                title = title,
                dateMillis = date,
                category = category,
                isPinned = pin
            )
        }

        // 日期详情页路由
        composable(
            route = Screen.SpecialDateDetailWithId.route,
            arguments = listOf(
                navArgument("dateId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val dateId = backStackEntry.arguments?.getLong("dateId") ?: 0L
            SpecialDateDetailScreen(navController = navController, dateId = dateId)
        }

        // 日期编辑页路由（复用 QuickCreate，通过 dateId 参数区分编辑模式）
        composable(
            route = Screen.SpecialDateEditWithId.route,
            arguments = listOf(
                navArgument("dateId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val dateId = backStackEntry.arguments?.getLong("dateId") ?: 0L
            SpecialDateQuickCreateScreen(navController = navController, dateId = dateId)
        }

        // 灵感编辑页面路由
        composable(Screen.InspirationEdit.route) {
            InspirationEditScreen(navController = navController)
        }

        composable(Screen.InspirationEditWithId.route) { backStackEntry ->
            val inspirationId = backStackEntry.arguments?.getString("inspirationId")?.toLongOrNull()
            InspirationEditScreen(
                navController = navController,
                inspirationId = inspirationId
            )
        }

        // 灵感展示页面路由（只读预览，支持左右滑动切换）
        composable(
            route = Screen.InspirationViewWithId.route,
            arguments = listOf(
                androidx.navigation.navArgument("inspirationId") {
                    type = androidx.navigation.NavType.LongType
                }
            )
        ) { backStackEntry ->
            val inspirationId = backStackEntry.arguments?.getLong("inspirationId") ?: -1L
            com.corgimemo.app.ui.screens.inspiration.InspirationViewScreen(
                inspirationId = inspirationId,
                navController = navController
            )
        }

        // 灵感字数统计页面路由
        composable(Screen.InspirationStats.route) {
            InspirationStatsScreen(navController = navController)
        }

        // 日期数据统计页面路由
        composable(Screen.DateStats.route) {
            DateStatsScreen(navController = navController)
        }

        // 灵感图表横屏全屏页面路由（chartType: line / bar）
        composable(Screen.ChartFullscreen.route) {
            ChartFullscreenScreen(navController = navController)
        }

        // 回收站页面路由
        composable(
            route = Screen.RecycleBin.route,
            arguments = listOf(navArgument("source") {
                type = NavType.StringType
                defaultValue = "todo"
            })
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source") ?: "todo"
            com.corgimemo.app.ui.screens.recyclebin.RecycleBinScreen(
                navController = navController,
                source = source
            )
        }

        // 图片全屏预览页面路由
        composable(Screen.ImagePreview.route) { backStackEntry ->
            /**
             * 从 SavedStateHandle 获取图片路径列表和初始索引
             * 由调用方在导航前设置参数：
             * navController.currentBackStackEntry?.savedStateHandle?.set("imagePaths", paths)
             * navController.currentBackStackEntry?.savedStateHandle?.set("initialIndex", index)
             */
            val imagePaths = backStackEntry.savedStateHandle.get<List<String>>("imagePaths") ?: emptyList()
            val initialIndex = backStackEntry.savedStateHandle.get<Int>("initialIndex") ?: 0

            ImagePreviewScreen(
                imagePaths = imagePaths,
                initialIndex = initialIndex,
                onDeleteClick = { index ->
                    /** 删除图片回调（可选，编辑模式时传入）*/
                    // TODO: 实现删除逻辑并刷新列表
                },
                onDismiss = {
                    /** 关闭预览页面 */
                    navController.popBackStack()
                }
            )
        }
    }
}
