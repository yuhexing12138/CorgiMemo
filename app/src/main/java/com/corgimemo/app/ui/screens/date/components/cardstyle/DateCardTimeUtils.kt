package com.corgimemo.app.ui.screens.date.components.cardstyle

import java.time.Instant
import java.time.ZoneId

/**
 * 距离天数(取整,abs)
 *
 * @param target 目标日期时间戳(毫秒)
 * @param now 当前时间戳(毫秒,默认 System.currentTimeMillis())
 * @return 距离天数的绝对值(无论过去还是未来)
 */
fun daysUntil(target: Long, now: Long = System.currentTimeMillis()): Long {
    return kotlin.math.abs(target - now) / 86_400_000L
}

/**
 * 判断目标日期相对当前是过去还是未来
 *
 * @param target 目标日期时间戳(毫秒)
 * @param now 当前时间戳(毫秒,默认 System.currentTimeMillis())
 * @return true=目标日期在未来(还未到),false=目标日期在过去(已过)
 */
fun isFuture(target: Long, now: Long = System.currentTimeMillis()): Boolean {
    return target - now >= 0
}

/**
 * 距离文字(用于橙色撕页样式顶部)
 *
 * 格式:
 * - 未来:"距离 yyyy/MM/dd 周X 还有"
 * - 过去:"已过 yyyy/MM/dd 周X"
 *
 * 例: "距离 2026/07/17 周五 还有" / "已过 2026/07/17 周五"
 */
fun formatDistanceTextWithWeekday(target: Long, now: Long = System.currentTimeMillis()): String {
    val future = isFuture(target, now)
    val dateText = formatDateWithWeekday(target)
    return if (future) "距离 $dateText 还有" else "已过 $dateText"
}

/**
 * 距离文字(用于米色日历撕页样式底部)
 *
 * 格式:
 * - 未来:"距离 yyyy/MM/dd 还有"
 * - 过去:"已过 yyyy/MM/dd"
 *
 * 例: "距离 2026/07/17 还有" / "已过 2026/07/17"
 */
fun formatDistanceTextShort(target: Long, now: Long = System.currentTimeMillis()): String {
    val future = isFuture(target, now)
    val dateText = formatShortDate(target)
    return if (future) "距离 $dateText 还有" else "已过 $dateText"
}

/**
 * 完整日期+星期(用于橙色样式顶部)
 * 例: "2026/07/17 周五"
 */
fun formatDateWithWeekday(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val weekday = weekdays[date.dayOfWeek.value - 1]
    return "${date.year}/${String.format("%02d", date.monthValue)}/${String.format("%02d", date.dayOfMonth)} $weekday"
}

/**
 * 短日期(用于日历样式底部)
 * 例: "2026/07/17"
 */
fun formatShortDate(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.year}/${String.format("%02d", date.monthValue)}/${String.format("%02d", date.dayOfMonth)}"
}
