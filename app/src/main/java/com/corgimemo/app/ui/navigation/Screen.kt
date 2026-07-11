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
    /** V2.6: 编辑历史时间线（支持 todoId 参数） */
    object EditHistory : Screen("edit_history?todoId={todoId}") {
        /** 带参数的导航路径 */
        fun createRoute(todoId: Long) = "edit_history?todoId=$todoId"
    }

    // 新增：底部导航栏页面
    object Inspire : Screen("inspire")           // 灵感记录
    object Date : Screen("date")                 // 特殊日期

    // 灵感编辑页面
    object InspirationEdit : Screen("inspiration_edit")                    // 新建灵感
    object InspirationEditWithId : Screen("inspiration_edit/{inspirationId}")  // 编辑灵感

    // 灵感展示页面（只读预览，支持左右滑动切换）
    object InspirationViewWithId : Screen("inspiration_view/{inspirationId}") {
        /** 带参数的导航路径 */
        fun createRoute(inspirationId: Long) = "inspiration_view/$inspirationId"
    }

    // 灵感字数统计页面
    object InspirationStats : Screen("inspiration_stats")

    // 特殊日期编辑页面
    object SpecialDateEdit : Screen("date_edit")                          // 新建日期
    object SpecialDateEditWithId : Screen("date_edit/{specialDateId}")     // 编辑日期

    // 图片全屏预览页面
    object ImagePreview : Screen("image_preview")                          // 图片预览（参数通过 NavBackStackEntry 传递）

    /** 智能分类设置页面 */
    object SmartCategorySettings : Screen("smart_category_settings")

    /** V2.7: 最近删除页面 */
    object RecentlyDeleted : Screen("recently_deleted")

    fun withArgs(vararg args: String): String {
        var result = route
        args.forEach { arg ->
            val start = result.indexOf('{')
            val end = result.indexOf('}')
            if (start != -1 && end > start) {
                result = result.replaceRange(start..end, arg)
            } else {
                result = "$result/$arg"
            }
        }
        return result
    }
}
