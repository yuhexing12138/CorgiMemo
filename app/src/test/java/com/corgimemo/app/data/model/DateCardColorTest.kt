package com.corgimemo.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * DateCardColor sealed class 单元测试
 *
 * 覆盖:
 * - 14 个 object 的 serialName 字面量
 * - DEFAULT == Default
 * - fromSerialName 合法/非法/null 全部场景
 * - presets 列表 12 个且 serialName 不重复
 */
class DateCardColorTest {

    @Test
    fun `Default serialName is DEFAULT`() {
        assertEquals("DEFAULT", DateCardColor.Default.serialName)
    }

    @Test
    fun `Rainbow serialName is RAINBOW`() {
        assertEquals("RAINBOW", DateCardColor.Rainbow.serialName)
    }

    @Test
    fun `Blue serialName is BLUE`() {
        assertEquals("BLUE", DateCardColor.Blue.serialName)
    }

    @Test
    fun `SkyBlue serialName is SKY_BLUE`() {
        assertEquals("SKY_BLUE", DateCardColor.SkyBlue.serialName)
    }

    @Test
    fun `Teal serialName is TEAL`() {
        assertEquals("TEAL", DateCardColor.Teal.serialName)
    }

    @Test
    fun `Green serialName is GREEN`() {
        assertEquals("GREEN", DateCardColor.Green.serialName)
    }

    @Test
    fun `Lime serialName is LIME`() {
        assertEquals("LIME", DateCardColor.Lime.serialName)
    }

    @Test
    fun `Orange serialName is ORANGE`() {
        assertEquals("ORANGE", DateCardColor.Orange.serialName)
    }

    @Test
    fun `Red serialName is RED`() {
        assertEquals("RED", DateCardColor.Red.serialName)
    }

    @Test
    fun `Pink serialName is PINK`() {
        assertEquals("PINK", DateCardColor.Pink.serialName)
    }

    @Test
    fun `Purple serialName is PURPLE`() {
        assertEquals("PURPLE", DateCardColor.Purple.serialName)
    }

    @Test
    fun `Navy serialName is NAVY`() {
        assertEquals("NAVY", DateCardColor.Navy.serialName)
    }

    @Test
    fun `Brown serialName is BROWN`() {
        assertEquals("BROWN", DateCardColor.Brown.serialName)
    }

    @Test
    fun `Black serialName is BLACK`() {
        assertEquals("BLACK", DateCardColor.Black.serialName)
    }

    @Test
    fun `DEFAULT is Default`() {
        assertSame(DateCardColor.Default, DateCardColor.DEFAULT)
    }

    @Test
    fun `fromSerialName returns Default for DEFAULT`() {
        assertSame(DateCardColor.Default, DateCardColor.fromSerialName("DEFAULT"))
    }

    @Test
    fun `fromSerialName returns Blue for BLUE`() {
        assertSame(DateCardColor.Blue, DateCardColor.fromSerialName("BLUE"))
    }

    @Test
    fun `fromSerialName returns SkyBlue for SKY_BLUE`() {
        assertSame(DateCardColor.SkyBlue, DateCardColor.fromSerialName("SKY_BLUE"))
    }

    @Test
    fun `fromSerialName returns Rainbow for RAINBOW`() {
        assertSame(DateCardColor.Rainbow, DateCardColor.fromSerialName("RAINBOW"))
    }

    @Test
    fun `fromSerialName returns DEFAULT for null`() {
        assertSame(DateCardColor.DEFAULT, DateCardColor.fromSerialName(null))
    }

    @Test
    fun `fromSerialName returns DEFAULT for empty string`() {
        assertSame(DateCardColor.DEFAULT, DateCardColor.fromSerialName(""))
    }

    @Test
    fun `fromSerialName returns DEFAULT for invalid string`() {
        assertSame(DateCardColor.DEFAULT, DateCardColor.fromSerialName("INVALID_COLOR"))
    }

    @Test
    fun `presets contains exactly 12 colors`() {
        assertEquals(12, DateCardColor.presets.size)
    }

    @Test
    fun `presets has unique serialName`() {
        val serialNames = DateCardColor.presets.map { it.serialName }
        assertEquals(serialNames.size, serialNames.toSet().size)
    }

    @Test
    fun `presets does not contain Default`() {
        assert(!DateCardColor.presets.contains(DateCardColor.Default))
    }

    @Test
    fun `presets does not contain Rainbow`() {
        assert(!DateCardColor.presets.contains(DateCardColor.Rainbow))
    }

    @Test
    fun `presets contains all 12 single colors`() {
        val expected = listOf(
            DateCardColor.Blue, DateCardColor.SkyBlue, DateCardColor.Teal,
            DateCardColor.Green, DateCardColor.Lime, DateCardColor.Orange,
            DateCardColor.Red, DateCardColor.Pink, DateCardColor.Purple,
            DateCardColor.Navy, DateCardColor.Brown, DateCardColor.Black
        )
        assertEquals(expected.toSet(), DateCardColor.presets.toSet())
    }

    @Test
    fun `Default and Rainbow are not same instance`() {
        assertNotEquals(DateCardColor.Default, DateCardColor.Rainbow)
    }
}
