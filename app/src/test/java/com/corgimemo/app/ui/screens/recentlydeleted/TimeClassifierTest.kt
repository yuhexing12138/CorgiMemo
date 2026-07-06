// TimeClassifier 单元测试
package com.corgimemo.app.ui.screens.recentlydeleted

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * TimeClassifier 纯函数测试
 *
 * 覆盖 classifyByTime 和 formatRelativeTime 的所有分支和边界条件。
 */
class TimeClassifierTest {

    /**
     * 构造指定年/月/日/时/分的时间戳
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param day 日
     * @param hour 小时（0-23）
     * @param minute 分钟（0-59）
     * @return 毫秒级时间戳
     */
    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }
        return cal.timeInMillis
    }

    /**
     * 构造"现在"时间戳
     *
     * @param year 年份
     * @param month 月份（1-12）
     * @param day 日
     * @param hour 小时（0-23）
     * @return 毫秒级时间戳
     */
    private fun now(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        ts(year, month, day, hour, 0)

    /**
     * 同一日（10:00 vs 15:00）应归类为 TODAY
     */
    @Test
    fun `classifyByTime 同一日归类为 TODAY`() {
        val n = now(2026, 7, 6, 10)
        val t = ts(2026, 7, 6, 15)
        assertEquals(DeletedTodoGroupKind.TODAY, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 昨天应归类为 YESTERDAY
     */
    @Test
    fun `classifyByTime 昨天归类为 YESTERDAY`() {
        val n = now(2026, 7, 6, 10)
        val t = ts(2026, 7, 5, 15)
        assertEquals(DeletedTodoGroupKind.YESTERDAY, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 3 天前应归类为 THIS_WEEK
     */
    @Test
    fun `classifyByTime 3 天前归类为 THIS_WEEK`() {
        val n = now(2026, 7, 6, 10)
        val t = ts(2026, 7, 3, 15)
        assertEquals(DeletedTodoGroupKind.THIS_WEEK, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 6 天前（本周边界）应归类为 THIS_WEEK
     */
    @Test
    fun `classifyByTime 6 天前归类为 THIS_WEEK`() {
        val n = now(2026, 7, 6, 10)
        val t = ts(2026, 7, 1, 0)
        assertEquals(DeletedTodoGroupKind.THIS_WEEK, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 7 天前应归类为 EARLIER
     */
    @Test
    fun `classifyByTime 7 天前归类为 EARLIER`() {
        val n = now(2026, 7, 6, 10)
        val t = ts(2026, 6, 29, 15)
        assertEquals(DeletedTodoGroupKind.EARLIER, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 跨月边界（7月1日 vs 6月30日）应归类为 YESTERDAY
     */
    @Test
    fun `classifyByTime 跨月边界 月初归类正确`() {
        val n = now(2026, 7, 1, 10)
        val t = ts(2026, 6, 30, 15)
        assertEquals(DeletedTodoGroupKind.YESTERDAY, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 跨年边界（2026-01-01 vs 2025-12-31）应归类为 YESTERDAY
     */
    @Test
    fun `classifyByTime 跨年边界 1月1日归类正确`() {
        val n = now(2026, 1, 1, 10)
        val t = ts(2025, 12, 31, 15)
        assertEquals(DeletedTodoGroupKind.YESTERDAY, TimeClassifier.classifyByTime(t, n))
    }

    /**
     * 1 分钟内（30 秒）应返回"刚刚"
     */
    @Test
    fun `formatRelativeTime 1 分钟内返回 刚刚`() {
        val n = ts(2026, 7, 6, 10, 30)
        val t = n - TimeUnit.SECONDS.toMillis(30)
        assertEquals("刚刚", TimeClassifier.formatRelativeTime(t, n))
    }

    /**
     * 5 分钟前应返回"5 分钟前"
     */
    @Test
    fun `formatRelativeTime 5 分钟前`() {
        val n = ts(2026, 7, 6, 10, 30)
        val t = n - TimeUnit.MINUTES.toMillis(5)
        assertEquals("5 分钟前", TimeClassifier.formatRelativeTime(t, n))
    }

    /**
     * 当天超过 1 小时应返回"今天 HH:mm"
     */
    @Test
    fun `formatRelativeTime 当天超过 1 小时返回 今天 HHmm`() {
        val n = ts(2026, 7, 6, 15, 30)
        val t = ts(2026, 7, 6, 14, 30)
        assertEquals("今天 14:30", TimeClassifier.formatRelativeTime(t, n))
    }

    /**
     * 昨天任意时间应返回"昨天 HH:mm"
     */
    @Test
    fun `formatRelativeTime 昨天任意时间`() {
        val n = ts(2026, 7, 6, 15, 30)
        val t = ts(2026, 7, 5, 18, 0)
        assertEquals("昨天 18:00", TimeClassifier.formatRelativeTime(t, n))
    }

    /**
     * 7 天前应返回"YYYY-MM-DD"
     */
    @Test
    fun `formatRelativeTime 7 天前返回 YYYY-MM-DD`() {
        val n = ts(2026, 7, 6, 15, 30)
        val t = ts(2026, 6, 29, 10, 0)
        assertEquals("2026-06-29", TimeClassifier.formatRelativeTime(t, n))
    }
}
