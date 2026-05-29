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

    // 特殊日期编辑页面
    object SpecialDateEdit : Screen("date_edit")                          // 新建日期
    object SpecialDateEditWithId : Screen("date_edit/{specialDateId}")     // 编辑日期

    // 图片全屏预览页面
    object ImagePreview : Screen("image_preview")                          // 图片预览（参数通过 NavBackStackEntry 传递）

    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
