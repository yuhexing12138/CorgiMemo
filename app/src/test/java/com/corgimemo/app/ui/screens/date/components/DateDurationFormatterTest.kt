package com.corgimemo.app.ui.screens.date.components

import org.junit.Test
import kotlin.test.assertEquals

/**
 * formatDuration 工具函数单元测试
 *
 * 规则：从最大有效单位逐级显示到秒，不补位
 * - 0            → "0秒"
 * - 59秒         → "59秒"
 * - 60秒(1分)    → "1分 0秒"
 * - 3600秒(1时)  → "1时 0分 0秒"
 * - 86400秒(1天) → "1天 0时 0分 0秒"
 * - 一年一天一秒 → "1年 1天 0时 0分 1秒"
 * - 负数(已过期) → "0秒"（coerceAtLeast 兜底）
 */
class DateDurationFormatterTest {

    @Test fun `0 毫秒 -> 0秒`() {
        assertEquals("0秒", formatDuration(0L))
    }

    @Test fun `59 秒 -> 59秒`() {
        assertEquals("59秒", formatDuration(59_000L))
    }

    @Test fun `60 秒 -> 1分 0秒`() {
        assertEquals("1分 0秒", formatDuration(60_000L))
    }

    @Test fun `3600 秒 -> 1时 0分 0秒`() {
        assertEquals("1时 0分 0秒", formatDuration(3_600_000L))
    }

    @Test fun `86400 秒 -> 1天 0时 0分 0秒`() {
        assertEquals("1天 0时 0分 0秒", formatDuration(86_400_000L))
    }

    @Test fun `一年一天一秒 -> 1年 1天 0时 0分 1秒`() {
        // 365*86400 + 86400 + 1 = 31_536_001 秒
        val oneYearOneDayOneSec = (365L * 86_400L + 86_400L + 1L) * 1_000L
        assertEquals("1年 1天 0时 0分 1秒", formatDuration(oneYearOneDayOneSec))
    }

    @Test fun `负数 -> 0秒 兜底`() {
        assertEquals("0秒", formatDuration(-1000L))
    }

    @Test fun `5 天 3 时 -> 5天 3时 0分 0秒`() {
        val fiveDayThreeHour = (5L * 86_400L + 3L * 3_600L) * 1_000L
        assertEquals("5天 3时 0分 0秒", formatDuration(fiveDayThreeHour))
    }

    @Test fun `4 分 5 秒 -> 4分 5秒`() {
        val fourMinFiveSec = (4L * 60L + 5L) * 1_000L
        assertEquals("4分 5秒", formatDuration(fourMinFiveSec))
    }
}
