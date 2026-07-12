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

    // 灵感图表横屏全屏页面（chartType: line / bar）
    object ChartFullscreen : Screen("chart_fullscreen/{chartType}") {
        /** 带参数的导航路径 */
        fun createRoute(chartType: String) = "chart_fullscreen/$chartType"
    }

    // 特殊日期快速创建页面（重构版：4 行核心功能 + 下一步）
    object SpecialDateQuickCreate : Screen("date_create")                  // 日期新建快速创建页

    // 图片全屏预览页面
    object ImagePreview : Screen("image_preview")                          // 图片预览（参数通过 NavBackStackEntry 传递）

    /** 智能分类设置页面 */
    object SmartCategorySettings : Screen("smart_category_settings")

    /** 回收站页面（支持 source 参数指定默认 Tab） */
    object RecycleBin : Screen("recycle_bin?source={source}") {
        fun createRoute(source: String) = "recycle_bin?source=$source"
    }

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
