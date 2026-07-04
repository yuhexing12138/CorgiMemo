package com.corgimemo.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * ReminderTimeFormatter 单元测试
 *
 * 覆盖 13 种场景：今天/昨天/明天/同年跨日/跨年 过去-未来 各组合
 * 验证 月日不补 0、日期时间有空格、跨年加 yyyy年 前缀
 */
class ReminderTimeFormatterTest {

    /** 辅助：用 (year, month1based, day, hour, minute) 构造毫秒时间戳 */
    private fun ts(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test fun case1_todayFuture() {
        val reminder = ts(2026, 6, 23, 23, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天23:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case2_todayOverdue() {
        val reminder = ts(2026, 6, 23, 21, 31)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天21:31 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case3_yesterdayOverdue() {
        val reminder = ts(2026, 6, 22, 21, 33)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("昨天21:33 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case4_sameYearMonthsAgoOverdue() {
        val reminder = ts(2026, 6, 21, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("6月21日 21:34 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case5_tomorrowFuture() {
        val reminder = ts(2026, 6, 24, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("明天21:34", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case6_sameYearFuture() {
        val reminder = ts(2026, 6, 25, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("6月25日 21:34", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case7_crossYearFuture() {
        // reminder 设为 1月2日，避免成为 now(12-31) 的明天，从而触发跨年分支
        val reminder = ts(2027, 1, 2, 9, 0)
        val now = ts(2026, 12, 31, 18, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2027年1月2日 09:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case8_sameMinute() {
        val reminder = ts(2026, 6, 23, 21, 33)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天21:33", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case9_lastYearOverdue() {
        val reminder = ts(2025, 6, 25, 10, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2025年6月25日 10:00 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case10_sameYearDifferentMonthOverdue() {
        val reminder = ts(2026, 1, 1, 10, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("1月1日 10:00 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case11_lastYearFuture() {
        // now 改为 2024 年，让 reminder(2025) 真正成为去年未来，触发跨年分支
        val reminder = ts(2025, 12, 31, 10, 0)
        val now = ts(2024, 6, 1, 12, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2025年12月31日 10:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case12_noZeroPadding() {
        // now 改为 2月，让 reminder(3月) 是未来，纯测试月日不补0格式，无需过期后缀
        val reminder = ts(2026, 3, 5, 8, 5)
        val now = ts(2026, 2, 1, 12, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("3月5日 08:05", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case13_crossYearBoundary() {
        // reminder 改为 12月30日，避免成为 now(2027-01-01) 的昨天，从而触发跨年分支
        val reminder = ts(2026, 12, 30, 23, 59)
        val now = ts(2027, 1, 1, 0, 1)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2026年12月30日 23:59 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }
}
