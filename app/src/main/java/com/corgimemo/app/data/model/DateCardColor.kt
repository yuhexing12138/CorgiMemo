package com.corgimemo.app.data.model

import androidx.compose.ui.graphics.Color

/**
 * 日期卡片颜色(主页/QuickCreate 后的颜色选择)
 *
 * 采用 sealed class + serialName 模式,与 DateCardStyle 完全对称:
 * - 类型安全:`when` 编译期穷尽匹配
 * - 持久化:数据库存 serialName 字符串
 * - 解析容错:fromSerialName 失败时 fallback 到 DEFAULT
 *
 * 三种状态:
 * - [Default]: 占位"无颜色"选项(斜线圆),选中后卡片不修改任何颜色
 * - 12 个 [presets] 单色:Blue / SkyBlue / Teal / Green / Lime / Orange / Red / Pink / Purple / Navy / Brown / Black
 * - [Rainbow]: 占位"彩虹"选项(渐变圆),当前选中弹 Snackbar "彩虹色功能开发中"
 *
 * 新增颜色步骤(预留扩展):
 * 1. 在本 sealed class 中添加 `object NewColor : DateCardColor("NEW_COLOR")`
 * 2. 在 [presets] 列表中追加
 * 3. 在 4 个 helper 函数(topBarColor / backgroundColor / bigNumberColor / targetRingColor)中补全 case
 * 4. 在 DateCardColorPicker 的颜色列表中追加
 */
sealed class DateCardColor(val serialName: String) {
    /** 占位"无颜色"选项:选中后卡片不修改任何颜色,使用样式原色 */
    object Default : DateCardColor("DEFAULT")

    // ========== 12 个单色(presets) ==========
    object Blue    : DateCardColor("BLUE")        // 蓝
    object SkyBlue : DateCardColor("SKY_BLUE")    // 天蓝
    object Teal    : DateCardColor("TEAL")        // 青绿
    object Green   : DateCardColor("GREEN")       // 绿
    object Lime    : DateCardColor("LIME")        // 黄绿
    object Orange  : DateCardColor("ORANGE")      // 橙
    object Red     : DateCardColor("RED")         // 红
    object Pink    : DateCardColor("PINK")        // 粉
    object Purple  : DateCardColor("PURPLE")      // 紫
    object Navy    : DateCardColor("NAVY")        // 深蓝
    object Brown   : DateCardColor("BROWN")       // 棕
    object Black   : DateCardColor("BLACK")       // 黑

    /** 占位"彩虹"选项:当前选中弹 Snackbar,功能后续实现 */
    object Rainbow : DateCardColor("RAINBOW")

    companion object {
        val DEFAULT: DateCardColor = Default

        /** 真实可选的 12 种单色(不含 Default 与 Rainbow) */
        val presets: List<DateCardColor> = listOf(
            Blue, SkyBlue, Teal, Green, Lime, Orange,
            Red, Pink, Purple, Navy, Brown, Black
        )

        /**
         * 从 serialName 解析为 DateCardColor 实例
         * @param name serialName 字符串(null/非法值 fallback 到 DEFAULT)
         */
        fun fromSerialName(name: String?): DateCardColor = when (name) {
            Default.serialName  -> Default
            Blue.serialName     -> Blue
            SkyBlue.serialName  -> SkyBlue
            Teal.serialName     -> Teal
            Green.serialName    -> Green
            Lime.serialName     -> Lime
            Orange.serialName   -> Orange
            Red.serialName      -> Red
            Pink.serialName     -> Pink
            Purple.serialName   -> Purple
            Navy.serialName     -> Navy
            Brown.serialName    -> Brown
            Black.serialName    -> Black
            Rainbow.serialName  -> Rainbow
            else                -> DEFAULT
        }
    }
}

// ==================== helper 函数 ====================

/**
 * 顶部条颜色(橙撕 60dp 撕页条)
 * - DEFAULT → UiColors.Primary (#FF9A5C)
 * - 非 DEFAULT → 调色板对应色
 * - RAINBOW → fallback 到 Primary(实际不调用,顶层用 sweepGradient 覆盖)
 */
fun topBarColor(color: DateCardColor): Color = when (color) {
    DateCardColor.Default -> Color(0xFFFF9A5C)
    DateCardColor.Blue    -> Color(0xFF3F5BFF)
    DateCardColor.SkyBlue -> Color(0xFF1E9CFF)
    DateCardColor.Teal    -> Color(0xFF26C7B7)
    DateCardColor.Green   -> Color(0xFF4CAF50)
    DateCardColor.Lime    -> Color(0xFF8BC34A)
    DateCardColor.Orange  -> Color(0xFFFF9A5C)
    DateCardColor.Red     -> Color(0xFFFF5252)
    DateCardColor.Pink    -> Color(0xFFEC407A)
    DateCardColor.Purple  -> Color(0xFF7E57C2)
    DateCardColor.Navy    -> Color(0xFF1A237E)
    DateCardColor.Brown   -> Color(0xFF6D4C41)
    DateCardColor.Black   -> Color(0xFF212121)
    DateCardColor.Rainbow -> Color(0xFFFF9A5C)
}

/**
 * 卡片背景色(两种样式共用)
 * - DEFAULT:
 *   - 橙撕 → 白色
 *   - 日历 → 米色 #FFF8F0
 * - 非 DEFAULT → 调色板对应色(由 [topBarColor] 提供)
 */
fun backgroundColor(color: DateCardColor, style: DateCardStyle): Color = when (color) {
    DateCardColor.Default -> when (style) {
        DateCardStyle.OrangeTearOff   -> Color.White
        DateCardStyle.CalendarTearOff -> Color(0xFFFFF8F0)
    }
    else -> topBarColor(color)
}

/**
 * 大数字 64sp Bold 文字色
 * - DEFAULT → UiColors.Primary (#FF9A5C)
 * - 非 DEFAULT → 固定深灰 #333333
 *
 * 设计意图:大数字在白底卡片上**必须绝对清晰可见**。
 * 之前的设计是"5 个深色 cardColor(Navy/Black/Brown/Purple/Red)用 Color.White",
 * 但 [backgroundColor] helper 已改为永远输出 DEFAULT 分支(白底),
 * 导致白字+白底=完全不可见的 bug。
 * 改为固定深灰 #333333 后,所有 cardColor 下大数字都清晰可读。
 * 撕页条(顶部装饰)仍通过 [topBarColor] 跟随 cardColor 表达用户颜色选择。
 */
fun bigNumberColor(color: DateCardColor, style: DateCardStyle): Color = when (color) {
    DateCardColor.Default -> Color(0xFFFF9A5C)
    else -> Color(0xFF333333)  // 固定深灰,在白底卡片上绝对清晰可见
}

/**
 * MiniCalendar 目标日 1.5dp 描边圈颜色
 * - DEFAULT → 现有红色 #FFFF8A80
 * - 非 DEFAULT → 调色板对应色
 */
fun targetRingColor(color: DateCardColor): Color = when (color) {
    DateCardColor.Default -> Color(0xFFFF8A80)
    else -> topBarColor(color)
}
