package com.corgimemo.app.data.model

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
