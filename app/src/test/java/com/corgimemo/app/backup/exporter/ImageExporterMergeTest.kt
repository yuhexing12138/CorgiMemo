package com.corgimemo.app.backup.exporter

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ImageExporter.mergeBitmaps 失败测试（TDD - 仅写测试，不实现）
 *
 * 覆盖以下场景：
 * 1. 2 张图网格拼接：输出宽度大于单张宽度，高度等于较高子图
 * 2. 6 张图网格拼接：2 列 3 行
 * 3. 10 张图垂直拼接：1 列 10 行（高度叠加）
 * 4. 11 张图应抛出 TooManyBitmapsException（超过 10 张上限）
 * 5. 0 张图应抛出 IllegalArgumentException
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ImageExporterMergeTest {

    /**
     * 创建指定尺寸和纯色的 ARGB_8888 测试 Bitmap
     *
     * @param width 宽度（像素）
     * @param height 高度（像素）
     * @param color 填充色，默认白色
     * @return 测试用 Bitmap
     */
    private fun makeBitmap(width: Int, height: Int, color: Int = Color.WHITE): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    /**
     * 场景 1：2 张不同高度的图按网格拼接
     *
     * 期望：
     * - 输出宽度 = 2 * 720 = 1440（横向拼接 2 张）
     * - 输出高度 = max(1000, 800) = 1000（取较高子图）
     */
    @Test
    fun `mergeBitmaps 2 张网格布局输出宽度 720dp 高度等于较高子图`() = kotlinx.coroutines.runBlocking {
        val b1 = makeBitmap(720, 1000, Color.RED)
        val b2 = makeBitmap(720, 800, Color.BLUE)

        val merged = ImageExporter.mergeBitmaps(listOf(b1, b2))

        assertTrue("输出宽度应 > 720（拼接 2 张）", merged.width > 720)
        assertEquals("网格行高取较高子图", 1000, merged.height)
    }

    /**
     * 场景 2：6 张图按 2 列 3 行网格拼接
     *
     * 期望：
     * - 输出宽度 ≈ 720 * 2 = 1440
     * - 输出高度 ≈ 1000 * 3 = 3000
     */
    @Test
    fun `mergeBitmaps 6 张网格布局输出 2 列 3 行`() = kotlinx.coroutines.runBlocking {
        val bitmaps = (1..6).map { makeBitmap(720, 1000, Color.WHITE) }

        val merged = ImageExporter.mergeBitmaps(bitmaps)

        assertTrue("输出宽度应 ≈ 720*2", merged.width in 1400..1500)
        assertTrue("输出高度应 ≈ 1000*3", merged.height in 2900..3100)
    }

    /**
     * 场景 3：10 张图按 1 列 10 行垂直拼接
     *
     * 期望：
     * - 输出宽度固定为 720
     * - 输出高度 ≈ 500 * 10 = 5000
     */
    @Test
    fun `mergeBitmaps 10 张垂直布局输出 1 列 10 行`() = kotlinx.coroutines.runBlocking {
        val bitmaps = (1..10).map { makeBitmap(720, 500, Color.WHITE) }

        val merged = ImageExporter.mergeBitmaps(bitmaps)

        assertEquals("垂直布局宽度固定 720", 720, merged.width)
        assertTrue("垂直布局高度应 ≈ 500*10", merged.height in 4900..5100)
    }

    /**
     * 场景 4：超过 10 张上限应抛出 TooManyBitmapsException，且异常携带实际数量
     */
    @Test
    fun `mergeBitmaps 11 张抛出 TooManyBitmapsException`() = kotlinx.coroutines.runBlocking {
        val bitmaps = (1..11).map { makeBitmap(720, 500, Color.WHITE) }

        try {
            ImageExporter.mergeBitmaps(bitmaps)
            fail("期望抛出 TooManyBitmapsException")
        } catch (e: TooManyBitmapsException) {
            assertEquals(11, e.count)
        }
    }

    /**
     * 场景 5：空列表应抛出 IllegalArgumentException
     */
    @Test
    fun `mergeBitmaps 0 张抛出 IllegalArgumentException`() = kotlinx.coroutines.runBlocking {
        try {
            ImageExporter.mergeBitmaps(emptyList())
            fail("期望抛出 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // 期望行为，无需额外断言
        }
    }
}
