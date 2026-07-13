package com.corgimemo.app.data.model

/**
 * 日期卡片样式(主页/QuickCreate 后的样式选择)
 *
 * 采用 sealed class + serialName 模式:
 * - 类型安全:`when` 编译期穷尽匹配
 * - 持久化:数据库存 serialName 字符串
 * - 解析容错:fromSerialName 失败时 fallback 到 DEFAULT
 *
 * 新增样式步骤(预留扩展):
 * 1. 在本 sealed class 中添加 `object NewStyle : DateCardStyle("NEW_STYLE")`
 * 2. 在 components/cardstyle/ 目录下新增 NewStyleCard.kt
 * 3. 在 DateCardStyleRenderer.kt 的 when 分支中补全 case(编译器会报错提示)
 * 4. 在 DateCardStyleSelector.kt 的 styles 列表中追加
 */
sealed class DateCardStyle(val serialName: String) {
    /** 橙色撕页样式:顶部橙色块 + 倒计文字 + 大数字 + 标题(参考图 2) */
    object OrangeTearOff : DateCardStyle("ORANGE_TEAR_OFF")

    /** 米色日历撕页样式:顶部月历 + 大数字 + 标题(参考图 3) */
    object CalendarTearOff : DateCardStyle("CALENDAR_TEAR_OFF")

    companion object {
        val DEFAULT: DateCardStyle = OrangeTearOff

        /**
         * 从 serialName 解析为 DateCardStyle 实例
         * @param name serialName 字符串(null/非法值 fallback 到 DEFAULT)
         */
        fun fromSerialName(name: String?): DateCardStyle {
            return when (name) {
                OrangeTearOff.serialName -> OrangeTearOff
                CalendarTearOff.serialName -> CalendarTearOff
                else -> DEFAULT
            }
        }

        /** 全部样式列表(用于选择器与遍历) */
        val all: List<DateCardStyle> = listOf(OrangeTearOff, CalendarTearOff)
    }
}
