package com.corgimemo.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object TodoEdit : Screen("todo_edit")
    object TodoEditWithId : Screen("todo_edit/{todoId}")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object BackupHistory : Screen("backup_history")

    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
