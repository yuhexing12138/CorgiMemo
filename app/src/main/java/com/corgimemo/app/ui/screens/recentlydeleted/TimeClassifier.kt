// 纯函数：时间分类与格式化工具
package com.corgimemo.app.ui.screens.recentlydeleted

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * 纯函数：时间分类与格式化
 *
 * 不持有状态、不依赖 Android 框架，可独立单测。
 * 提供两个核心方法：
 * - [classifyByTime]: 把时间戳归类到 [DeletedTodoGroupKind]
 * - [formatRelativeTime]: 格式化为可读字符串
 */
object TimeClassifier {

    /**
     * 将 [deletedAt] 时间戳按其与 [now] 的相对关系归类到一个分组
     *
     * 分类规则（基于自然日，非 24 小时滚动）：
     * - 同一天 → TODAY
     * - 前一天 → YESTERDAY
     * - 同周（2-6 天前）→ THIS_WEEK
     * - 7 天前及更早 → EARLIER
     *
     * @param deletedAt 待分类的时间戳（毫秒）
     * @param now 参考"现在"时间戳（毫秒）
     * @return 分组枚举
     */
    fun classifyByTime(deletedAt: Long, now: Long): DeletedTodoGroupKind {
        val days = daysBetween(deletedAt, now)
        return when {
            days == 0L -> DeletedTodoGroupKind.TODAY
            days == 1L -> DeletedTodoGroupKind.YESTERDAY
            days in 2L..6L -> DeletedTodoGroupKind.THIS_WEEK
            else -> DeletedTodoGroupKind.EARLIER
        }
    }

    /**
     * 格式化为相对时间显示
     *
     * - 1 分钟内 → "刚刚"
     * - 60 分钟内且当天 → "N 分钟前"
     * - 当天其他时间 → "今天 HH:mm"
     * - 昨天 → "昨天 HH:mm"
     * - 7 天前及更早 → "YYYY-MM-DD"
     *
     * @param deletedAt 时间戳（毫秒）
     * @param now 参考"现在"时间戳（毫秒）
     * @return 相对时间字符串
     */
    fun formatRelativeTime(deletedAt: Long, now: Long): String {
        val diffMs = now - deletedAt
        val diffMin = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        val diffDays = daysBetween(deletedAt, now)

        return when {
            diffMin < 1L -> "刚刚"
            diffMin < 60L && diffDays == 0L -> "$diffMin 分钟前"
            diffDays == 0L -> "今天 ${formatTime(deletedAt)}"
            diffDays == 1L -> "昨天 ${formatTime(deletedAt)}"
            else -> formatDate(deletedAt)
        }
    }

    /**
     * 计算两个时间戳相隔的整天数（基于自然日，00:00 为分界）
     *
     * 关键点：先把两个时间都归一化到当日 00:00:00，再用自然日（DAY_OF_YEAR）作差，
     * 这样跨年且不足 24 小时时也能正确得到 1 天。
     *
     * @return 非负整数（始终为 abs 值）
     */
    private fun daysBetween(deletedAt: Long, now: Long): Long {
        val cal1 = Calendar.getInstance().apply {
            timeInMillis = deletedAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val cal2 = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val y1 = cal1.get(Calendar.YEAR)
        val d1 = cal1.get(Calendar.DAY_OF_YEAR)
        val y2 = cal2.get(Calendar.YEAR)
        val d2 = cal2.get(Calendar.DAY_OF_YEAR)
        if (y1 == y2) return abs(d2 - d1).toLong()
        // 跨年：使用归一化后的毫秒差计算天数（一定为正整倍数）
        return TimeUnit.MILLISECONDS.toDays(abs(cal2.timeInMillis - cal1.timeInMillis))
    }

    /**
     * 格式化为 "HH:mm"（24 小时制，零填充）
     *
     * @param timestamp 时间戳（毫秒）
     * @return 24 小时制时间字符串
     */
    private fun formatTime(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /**
     * 格式化为 "YYYY-MM-DD"
     *
     * @param timestamp 时间戳（毫秒）
     * @return ISO 风格日期字符串
     */
    private fun formatDate(timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }
}
