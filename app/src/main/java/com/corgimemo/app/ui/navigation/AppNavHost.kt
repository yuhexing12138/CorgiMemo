package com.corgimemo.app.ui.navigation

import androidx.compose.runtime.Composable
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
import com.corgimemo.app.ui.screens.inspire.InspireScreenPlaceholder

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    onExportClick: (BackupManager.ExportFormat) -> Unit = {},
    onImportClick: () -> Unit = {}
) {
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

        composable("smart_category_settings") {
            SmartCategorySettingsScreen(navController = navController)
        }

        composable(Screen.OperationHistory.route) {
            OperationHistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // 新增：底部导航栏页面路由
        composable(Screen.Inspire.route) {
            InspireScreenPlaceholder()
        }

        composable(Screen.Date.route) {
            DateScreenPlaceholder()
        }
    }
}
