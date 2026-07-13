package com.corgimemo.app.ui.screens.date.components.cardstyle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DateCardTimeUtils 单元测试
 */
class DateCardTimeUtilsTest {

    private val now = 1721260800000L  // 2024-07-18 00:00:00 UTC
    private val future1Day = now + 86_400_000L  // +1 天
    private val future5Days = now + 5 * 86_400_000L  // +5 天
    private val past1Day = now - 86_400_000L  // -1 天
    private val past5Days = now - 5 * 86_400_000L  // -5 天
    private val targetFri20260717 = 1792233600000L  // 2026-07-17(本地时间视系统而定,只测格式)

    // daysUntil --------------------------------------------------------

    @Test
    fun `daysUntil returns 0 when target equals now`() {
        assertEquals(0L, daysUntil(now, now))
    }

    @Test
    fun `daysUntil returns positive for future target`() {
        assertEquals(1L, daysUntil(future1Day, now))
    }

    @Test
    fun `daysUntil returns positive for past target(abs)`() {
        assertEquals(1L, daysUntil(past1Day, now))
    }

    @Test
    fun `daysUntil returns 5 for 5-day future`() {
        assertEquals(5L, daysUntil(future5Days, now))
    }

    // isFuture ----------------------------------------------------------

    @Test
    fun `isFuture returns true for future target`() {
        assertTrue(isFuture(future1Day, now))
    }

    @Test
    fun `isFuture returns false for past target`() {
        assertFalse(isFuture(past1Day, now))
    }

    @Test
    fun `isFuture returns false for same timestamp`() {
        assertFalse(isFuture(now, now))
    }

    // formatDistanceTextWithWeekday -----------------------------------

    @Test
    fun `formatDistanceTextWithWeekday future format contains 还有`() {
        val text = formatDistanceTextWithWeekday(future5Days, now)
        assertTrue("应包含 '还有': $text", text.contains("还有"))
    }

    @Test
    fun `formatDistanceTextWithWeekday past format contains 已过`() {
        val text = formatDistanceTextWithWeekday(past5Days, now)
        assertTrue("应包含 '已过': $text", text.contains("已过"))
    }

    @Test
    fun `formatDistanceTextWithWeekday contains date and weekday`() {
        val text = formatDistanceTextWithWeekday(targetFri20260717, now)
        assertTrue("应包含日期: $text", text.matches(Regex(""".*\d{4}/\d{2}/\d{2}.*""")))
        assertTrue("应包含 '周': $text", text.contains("周"))
    }

    // formatDistanceTextShort -----------------------------------------

    @Test
    fun `formatDistanceTextShort future format contains 还有`() {
        val text = formatDistanceTextShort(future5Days, now)
        assertTrue("应包含 '还有': $text", text.contains("还有"))
    }

    @Test
    fun `formatDistanceTextShort past format contains 已过`() {
        val text = formatDistanceTextShort(past5Days, now)
        assertTrue("应包含 '已过': $text", text.contains("已过"))
    }

    @Test
    fun `formatDistanceTextShort does not contain weekday`() {
        val text = formatDistanceTextShort(targetFri20260717, now)
        assertFalse("不应包含 '周': $text", text.contains("周"))
    }

    // formatDateWithWeekday -------------------------------------------

    @Test
    fun `formatDateWithWeekday matches yyyy slash MM slash dd pattern`() {
        val text = formatDateWithWeekday(targetFri20260717)
        assertTrue("应匹配日期格式: $text", text.matches(Regex(""".*\d{4}/\d{2}/\d{2}.*""")))
    }

    @Test
    fun `formatDateWithWeekday contains 周X suffix`() {
        val text = formatDateWithWeekday(targetFri20260717)
        assertTrue("应包含 '周X' 后缀: $text", text.matches(Regex(""".*周[一二三四五六日]$""")))
    }

    // formatShortDate --------------------------------------------------

    @Test
    fun `formatShortDate matches yyyy slash MM slash dd pattern`() {
        val text = formatShortDate(targetFri20260717)
        assertTrue("应匹配日期格式: $text", text.matches(Regex(""".*\d{4}/\d{2}/\d{2}""")))
    }

    @Test
    fun `formatShortDate does not contain weekday`() {
        val text = formatShortDate(targetFri20260717)
        assertFalse("不应包含 '周': $text", text.contains("周"))
    }
}
