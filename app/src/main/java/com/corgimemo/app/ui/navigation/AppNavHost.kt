package com.corgimemo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.ui.screens.achievement.AchievementScreen
import com.corgimemo.app.ui.screens.backup.BackupHistoryScreen
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.main.MainScreen
import com.corgimemo.app.ui.screens.onboarding.OnboardingScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.ui.screens.settings.OperationHistoryScreen
import com.corgimemo.app.ui.screens.settings.SettingsScreen
import com.corgimemo.app.ui.screens.settings.SmartCategorySettingsScreen
import com.corgimemo.app.ui.screens.stats.StatsScreen
import com.corgimemo.app.ui.screens.todo.TodoEditScreen
import com.corgimemo.app.ui.screens.corgi.CorgiDetailScreen
import com.corgimemo.app.ui.screens.date.DateScreenPlaceholder
import com.corgimemo.app.ui.screens.date.SpecialDateScreen
import com.corgimemo.app.ui.screens.date.SpecialDateEditScreen
import com.corgimemo.app.ui.screens.inspire.InspireScreenPlaceholder
import com.corgimemo.app.ui.screens.inspiration.InspirationScreen
import com.corgimemo.app.ui.screens.inspiration.InspirationEditScreen
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

        composable(Screen.Home.route) {
            MainScreen(navController = navController)
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

        composable(Screen.SmartCategorySettings.route) {
            SmartCategorySettingsScreen(navController = navController)
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

        // 特殊日期编辑页面路由
        composable(Screen.SpecialDateEdit.route) {
            SpecialDateEditScreen(navController = navController)
        }

        composable(Screen.SpecialDateEditWithId.route) { backStackEntry ->
            val specialDateId = backStackEntry.arguments?.getString("specialDateId")?.toLongOrNull()
            SpecialDateEditScreen(
                navController = navController,
                specialDateId = specialDateId
            )
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
