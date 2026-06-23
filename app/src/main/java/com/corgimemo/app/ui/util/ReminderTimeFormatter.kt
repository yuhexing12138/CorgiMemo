package com.corgimemo.app.ui.util

import java.util.Calendar

/**
 * 提醒时间显示数据
 *
 * @property text 已格式化好可直接渲染的文字，例如 "今天21:31 已过期"、"明天21:34"、"6月25日 21:34"、"2025年6月25日 10:00 已过期"
 * @property isOverdue 是否已过期（决定 UI 颜色：红 vs 主题色）
 */
data class ReminderDisplay(
    val text: String,
    val isOverdue: Boolean
)

/**
 * 把提醒时间戳格式化为友好的显示文字
 *
 * 规则：
 * - 与当前时间同一天：今天HH:MM
 * - 早一天：昨天HH:MM
 * - 晚一天：明天HH:MM
 * - 同年但跨日：M月D日 HH:MM（月日不补 0，日期与时间中间有 1 个空格）
 * - 跨年：yyyy年M月D日 HH:MM（月日不补 0，日期与时间中间有 1 个空格）
 * - 若 reminderTime < now，追加 " 已过期" 后缀并标记 isOverdue=true
 *
 * @param reminderTime 用户设置的提醒时间（毫秒）
 * @param now 当前时间（毫秒），默认 System.currentTimeMillis()，便于测试
 */
fun formatReminderDisplay(
    reminderTime: Long,
    now: Long = System.currentTimeMillis()
): ReminderDisplay {
    // 判定是否已过期
    val isOverdue = reminderTime < now

    // 分别构造目标时间和当前时间的 Calendar 实例
    val target = Calendar.getInstance().apply { timeInMillis = reminderTime }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

    // 时分保持两位（如 21:04，非 21:4）
    val hh = "%02d".format(target.get(Calendar.HOUR_OF_DAY))
    val mm = "%02d".format(target.get(Calendar.MINUTE))
    val timePart = "$hh:$mm"

    // 月日不补 0（如 6月3日，非 06月03日）
    val month = target.get(Calendar.MONTH) + 1
    val day = target.get(Calendar.DAY_OF_MONTH)

    // 根据与当前日期的相对关系选择前缀
    val prefix = when {
        isSameDay(target, today)   -> "今天"
        isSameDay(target, yesterday) -> "昨天"
        isSameDay(target, tomorrow)  -> "明天"
        isSameYear(target, today) -> {
            // 同年但跨日：M月D日
            "${month}月${day}日"
        }
        else -> {
            // 跨年：yyyy年M月D日
            "${target.get(Calendar.YEAR)}年${month}月${day}日"
        }
    }

    // 今天/昨天/明天 与时间无空格；M月D日 / yyyy年M月D日 与时间中间有 1 个空格
    val separator = if (prefix.startsWith("今天") || prefix.startsWith("昨天") || prefix.startsWith("明天")) "" else " "
    val text = if (isOverdue) "$prefix$separator$timePart 已过期" else "$prefix$separator$timePart"
    return ReminderDisplay(text, isOverdue)
}

/** 判定两个 Calendar 是否在同一天（同年同 DAY_OF_YEAR） */
private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

/** 判定两个 Calendar 是否在同一年 */
private fun isSameYear(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
}
