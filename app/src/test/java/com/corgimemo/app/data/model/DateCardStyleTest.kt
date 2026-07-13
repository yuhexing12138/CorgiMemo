package com.corgimemo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * DateCardStyle sealed class 单元测试
 *
 * 覆盖:
 * - 合法 serialName 解析
 * - null/非法值 fallback 到 DEFAULT
 * - DEFAULT == OrangeTearOff
 * - all 列表包含 2 个元素且 serialName 不重复
 */
class DateCardStyleTest {

    @Test
    fun `OrangeTearOff serialName is ORANGE_TEAR_OFF`() {
        assertEquals("ORANGE_TEAR_OFF", DateCardStyle.OrangeTearOff.serialName)
    }

    @Test
    fun `CalendarTearOff serialName is CALENDAR_TEAR_OFF`() {
        assertEquals("CALENDAR_TEAR_OFF", DateCardStyle.CalendarTearOff.serialName)
    }

    @Test
    fun `DEFAULT is OrangeTearOff`() {
        assertSame(DateCardStyle.OrangeTearOff, DateCardStyle.DEFAULT)
    }

    @Test
    fun `fromSerialName returns OrangeTearOff for ORANGE_TEAR_OFF`() {
        assertSame(DateCardStyle.OrangeTearOff, DateCardStyle.fromSerialName("ORANGE_TEAR_OFF"))
    }

    @Test
    fun `fromSerialName returns CalendarTearOff for CALENDAR_TEAR_OFF`() {
        assertSame(DateCardStyle.CalendarTearOff, DateCardStyle.fromSerialName("CALENDAR_TEAR_OFF"))
    }

    @Test
    fun `fromSerialName returns DEFAULT for null`() {
        assertSame(DateCardStyle.DEFAULT, DateCardStyle.fromSerialName(null))
    }

    @Test
    fun `fromSerialName returns DEFAULT for invalid string`() {
        assertSame(DateCardStyle.DEFAULT, DateCardStyle.fromSerialName("INVALID_STYLE"))
    }

    @Test
    fun `fromSerialName returns DEFAULT for empty string`() {
        assertSame(DateCardStyle.DEFAULT, DateCardStyle.fromSerialName(""))
    }

    @Test
    fun `all contains exactly 2 styles`() {
        assertEquals(2, DateCardStyle.all.size)
    }

    @Test
    fun `all has unique serialName`() {
        val serialNames = DateCardStyle.all.map { it.serialName }
        assertEquals(serialNames.size, serialNames.toSet().size)
    }

    @Test
    fun `all contains OrangeTearOff and CalendarTearOff`() {
        assert(DateCardStyle.all.contains(DateCardStyle.OrangeTearOff))
        assert(DateCardStyle.all.contains(DateCardStyle.CalendarTearOff))
    }

    @Test
    fun `two styles are not same instance`() {
        assertNotEquals(DateCardStyle.OrangeTearOff, DateCardStyle.CalendarTearOff)
    }
}
