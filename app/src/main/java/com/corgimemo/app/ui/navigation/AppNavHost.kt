package com.corgimemo.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.corgimemo.app.backup.BackupManager
import com.corgimemo.app.ui.screens.home.HomeScreen
import com.corgimemo.app.ui.screens.onboarding.OnboardingScreen
import com.corgimemo.app.ui.screens.profile.ProfileScreen
import com.corgimemo.app.ui.screens.settings.SettingsScreen
import com.corgimemo.app.ui.screens.todo.TodoEditScreen

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
            HomeScreen(navController = navController)
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
    }
}
