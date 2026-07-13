package com.corgimemo.app.data.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DateCardColor 4 个 helper 函数单元测试
 *
 * 覆盖:
 * - topBarColor:14 个 color 全部返回非空 Color
 * - backgroundColor:DEFAULT 橙撕=White,DEFAULT 日历=#FFF8F0,非 DEFAULT=调色板
 * - bigNumberColor:DEFAULT=橙,5 个深色(Navy/Black/Brown/Purple/Red)=White,其他=调色板
 * - targetRingColor:DEFAULT=红 #FFFF8A80,非 DEFAULT=调色板
 */
class DateCardColorMappingTest {

    // ==================== topBarColor ====================
    @Test
    fun `topBarColor Default returns UiColors Primary #FF9A5C`() {
        assertEquals(0xFFFF9A5C, topBarColor(DateCardColor.Default).value.toLong())
    }

    @Test
    fun `topBarColor Blue returns #3F5BFF`() {
        assertEquals(0xFF3F5BFF, topBarColor(DateCardColor.Blue).value.toLong())
    }

    @Test
    fun `topBarColor SkyBlue returns #1E9CFF`() {
        assertEquals(0xFF1E9CFF, topBarColor(DateCardColor.SkyBlue).value.toLong())
    }

    @Test
    fun `topBarColor Teal returns #26C7B7`() {
        assertEquals(0xFF26C7B7, topBarColor(DateCardColor.Teal).value.toLong())
    }

    @Test
    fun `topBarColor Green returns #4CAF50`() {
        assertEquals(0xFF4CAF50, topBarColor(DateCardColor.Green).value.toLong())
    }

    @Test
    fun `topBarColor Lime returns #8BC34A`() {
        assertEquals(0xFF8BC34A, topBarColor(DateCardColor.Lime).value.toLong())
    }

    @Test
    fun `topBarColor Orange returns #FF9A5C`() {
        assertEquals(0xFFFF9A5C, topBarColor(DateCardColor.Orange).value.toLong())
    }

    @Test
    fun `topBarColor Red returns #FF5252`() {
        assertEquals(0xFFFF5252, topBarColor(DateCardColor.Red).value.toLong())
    }

    @Test
    fun `topBarColor Pink returns #EC407A`() {
        assertEquals(0xFFEC407A, topBarColor(DateCardColor.Pink).value.toLong())
    }

    @Test
    fun `topBarColor Purple returns #7E57C2`() {
        assertEquals(0xFF7E57C2, topBarColor(DateCardColor.Purple).value.toLong())
    }

    @Test
    fun `topBarColor Navy returns #1A237E`() {
        assertEquals(0xFF1A237E, topBarColor(DateCardColor.Navy).value.toLong())
    }

    @Test
    fun `topBarColor Brown returns #6D4C41`() {
        assertEquals(0xFF6D4C41, topBarColor(DateCardColor.Brown).value.toLong())
    }

    @Test
    fun `topBarColor Black returns #212121`() {
        assertEquals(0xFF212121, topBarColor(DateCardColor.Black).value.toLong())
    }

    @Test
    fun `topBarColor Rainbow returns UiColors Primary fallback`() {
        // 彩虹实际不调用此函数(顶层用 Brush.sweepGradient 覆盖),fallback 到 Primary
        assertEquals(0xFFFF9A5C, topBarColor(DateCardColor.Rainbow).value.toLong())
    }

    // ==================== backgroundColor ====================
    @Test
    fun `backgroundColor Default OrangeTearOff returns White`() {
        assertEquals(Color.White, backgroundColor(DateCardColor.Default, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `backgroundColor Default CalendarTearOff returns #FFF8F0`() {
        assertEquals(0xFFFFF8F0, backgroundColor(DateCardColor.Default, DateCardStyle.CalendarTearOff).value.toLong())
    }

    @Test
    fun `backgroundColor Blue OrangeTearOff returns Blue`() {
        assertEquals(0xFF3F5BFF, backgroundColor(DateCardColor.Blue, DateCardStyle.OrangeTearOff).value.toLong())
    }

    @Test
    fun `backgroundColor Teal CalendarTearOff returns Teal`() {
        assertEquals(0xFF26C7B7, backgroundColor(DateCardColor.Teal, DateCardStyle.CalendarTearOff).value.toLong())
    }

    // ==================== bigNumberColor ====================
    @Test
    fun `bigNumberColor Default returns UiColors Primary #FF9A5C`() {
        assertEquals(0xFFFF9A5C, bigNumberColor(DateCardColor.Default, DateCardStyle.OrangeTearOff).value.toLong())
    }

    @Test
    fun `bigNumberColor Navy OrangeTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Navy, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `bigNumberColor Black OrangeTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Black, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `bigNumberColor Brown OrangeTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Brown, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `bigNumberColor Purple OrangeTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Purple, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `bigNumberColor Red OrangeTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Red, DateCardStyle.OrangeTearOff))
    }

    @Test
    fun `bigNumberColor Navy CalendarTearOff returns White for contrast`() {
        assertEquals(Color.White, bigNumberColor(DateCardColor.Navy, DateCardStyle.CalendarTearOff))
    }

    @Test
    fun `bigNumberColor Blue OrangeTearOff returns Blue (light color uses palette)`() {
        assertEquals(0xFF3F5BFF, bigNumberColor(DateCardColor.Blue, DateCardStyle.OrangeTearOff).value.toLong())
    }

    @Test
    fun `bigNumberColor Teal CalendarTearOff returns Teal (light color uses palette)`() {
        assertEquals(0xFF26C7B7, bigNumberColor(DateCardColor.Teal, DateCardStyle.CalendarTearOff).value.toLong())
    }

    // ==================== targetRingColor ====================
    @Test
    fun `targetRingColor Default returns #FFFF8A80 (existing red)`() {
        assertEquals(0xFFFF8A80, targetRingColor(DateCardColor.Default).value.toLong())
    }

    @Test
    fun `targetRingColor Blue returns Blue`() {
        assertEquals(0xFF3F5BFF, targetRingColor(DateCardColor.Blue).value.toLong())
    }

    @Test
    fun `targetRingColor Teal returns Teal`() {
        assertEquals(0xFF26C7B7, targetRingColor(DateCardColor.Teal).value.toLong())
    }

    @Test
    fun `targetRingColor Red returns Red`() {
        assertEquals(0xFFFF5252, targetRingColor(DateCardColor.Red).value.toLong())
    }
}
