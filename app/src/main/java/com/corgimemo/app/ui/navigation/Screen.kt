package com.corgimemo.app.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Onboarding : Screen("onboarding")
    object TodoEdit : Screen("todo_edit")
    object TodoEditWithId : Screen("todo_edit/{todoId}")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    /**
     * 外观设置页（深色模式 + 主题色）
     *
     * 从 SettingsScreen 拆出独立页，由"我的"页 ThemeQuickSwitch 入口直达。
     * 6 色主题配色方案 key：orange / pink / green / blue / purple / brown
     * （详见 `app/ui/screens/profile/components/ThemeQuickSwitch.kt` 的 `ThemePresets`）
     */
    object Appearance : Screen("appearance")
    object BackupHistory : Screen("backup_history")
    object Stats : Screen("stats")
    object Achievement : Screen("achievement")
    object CorgiDetail : Screen("corgi_detail")
    /** 装扮详情页（从"我的"页外移，承载柯基动画+预览模式+横滑列表） */
    object Outfit : Screen("outfit")
    /** 个人详情页（头像上传 / 名称 / 性别编辑） */
    object ProfileDetail : Screen("profile_detail")
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

    // 日期数据统计页面
    object DateStats : Screen("date_stats")

    // 灵感图表横屏全屏页面（chartType: line / bar）
    object ChartFullscreen : Screen("chart_fullscreen/{chartType}") {
        /** 带参数的导航路径 */
        fun createRoute(chartType: String) = "chart_fullscreen/$chartType"
    }

    // 特殊日期快速创建页面（重构版：4 行核心功能 + 下一步）
    object SpecialDateQuickCreate : Screen("date_create")                  // 日期新建快速创建页

    // 日期详情页
    object SpecialDateDetailWithId : Screen("date_detail/{dateId}") {
        /** 带参数的导航路径 */
        fun createRoute(dateId: Long) = "date_detail/$dateId"
    }

    // 日期编辑页（复用 QuickCreate，编辑模式）
    object SpecialDateEditWithId : Screen("date_edit/{dateId}") {
        /** 带参数的导航路径 */
        fun createRoute(dateId: Long) = "date_edit/$dateId"
    }

    // 特殊日期卡片样式选择页（新建日期流程第二步：选样式后保存落库）
    object SpecialDateCardStyle :
        Screen("date_card_style?title={title}&date={date}&category={category}&pin={pin}") {
        /**
         * 构建路由字符串
         *
         * - title 走 URL 编码（中文名 / 特殊字符安全）
         * - category 走 URL 编码（自定义分类可能含特殊字符）
         * - date / pin 为基础类型直接拼接
         *
         * @param title    来自 QuickCreate 的名称（空值兜底为"未命名"）
         * @param date     来自 QuickCreate 的目标日期时间戳（毫秒）
         * @param category 来自 QuickCreate 的分类（预设枚举名或自定义字符串，空值兜底为"OTHER"）
         * @param pin      来自 QuickCreate 的置顶开关
         */
        fun createRoute(
            title: String,
            date: Long,
            category: String,
            pin: Boolean
        ): String {
            val encodedTitle = android.net.Uri.encode(title.ifBlank { "未命名" })
            val encodedCategory = android.net.Uri.encode(category.ifBlank { "OTHER" })
            return "date_card_style?title=$encodedTitle&date=$date&category=$encodedCategory&pin=$pin"
        }
    }

    // 图片全屏预览页面
    object ImagePreview : Screen("image_preview")                          // 图片预览（参数通过 NavBackStackEntry 传递）

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
