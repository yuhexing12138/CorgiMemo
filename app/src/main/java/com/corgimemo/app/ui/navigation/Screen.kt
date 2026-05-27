package com.corgimemo.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object TodoEdit : Screen("todo_edit")
    object TodoEditWithId : Screen("todo_edit/{todoId}")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object BackupHistory : Screen("backup_history")
    object Stats : Screen("stats")
    object Achievement : Screen("achievement")
    object CorgiDetail : Screen("corgi_detail")
    object OperationHistory : Screen("operation_history")

    // 新增：底部导航栏页面
    object Inspire : Screen("inspire")           // 灵感记录
    object Date : Screen("date")                 // 特殊日期

    // 灵感编辑页面
    object InspirationEdit : Screen("inspiration_edit")                    // 新建灵感
    object InspirationEditWithId : Screen("inspiration_edit/{inspirationId}")  // 编辑灵感

    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
