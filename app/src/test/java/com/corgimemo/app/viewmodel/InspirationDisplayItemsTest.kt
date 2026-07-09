package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.model.Inspiration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * InspirationViewModel.buildDisplayItems 单元测试
 *
 * 验证「相邻相同日期只显示一次」规则：
 * - 第 1 条始终 showDate=true
 * - 后续仅当与前一条日期（年月日）不同时 showDate=true
 *
 * 覆盖场景（对应设计文档 2.1 节）：
 * 1. 用户报告的 3 个真实场景
 * 2. 置顶↔普通同日边界
 * 3. 跨年月边界
 * 4. 连续 3 条同日
 * 5. 空列表
 * 6. 单条
 */
class InspirationDisplayItemsTest {

    // ========== 辅助构造函数 ==========

    /** 构造测试用灵感（仅需 id/createdAt/isPinned） */
    private fun inspiration(
        id: Long,
        createdAt: Long,
        isPinned: Boolean = false
    ): Inspiration = Inspiration(
        id = id,
        title = "灵感$id",
        createdAt = createdAt,
        updatedAt = createdAt,
        isPinned = isPinned
    )

    /** 2026.07.08 23:27:00 UTC+8 对应时间戳 */
    private val t_07_08_23_27 = 1752024420000L  // 灵感1 时间
    /** 2026.07.08 21:29:00 UTC+8 */
    private val t_07_08_21_29 = 1752017340000L  // 灵感2 时间
    /** 2026.07.09 21:27:00 UTC+8 */
    private val t_07_09_21_27 = 1752100820000L  // 灵感3 时间
    /** 2026.06.30 21:00:00 UTC+8 */
    private val t_06_30_21_00 = 1751288400000L
    /** 2026.07.01 09:00:00 UTC+8 */
    private val t_07_01_09_00 = 1751331600000L

    // ========== 用户报告的真实场景 ==========

    @Test
    fun `场景1_置顶灵感1_顺序_灵感1_灵感3_灵感2_日期_07_08_07_09_07_08`() {
        // DAO 排序：置顶(灵感1)在前 + 非置顶按 createdAt 倒序(灵感3 > 灵感2)
        val list = listOf(
            inspiration(id = 1, createdAt = t_07_08_23_27, isPinned = true),
            inspiration(id = 3, createdAt = t_07_09_21_27, isPinned = false),
            inspiration(id = 2, createdAt = t_07_08_21_29, isPinned = false)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(3, result.size)
        assertTrue(result[0].showDate)  // 灵感1: 07.08 首条
        assertTrue(result[1].showDate)  // 灵感3: 07.09 ≠ 07.08
        assertTrue(result[2].showDate)  // 灵感2: 07.08 ≠ 07.09
    }

    @Test
    fun `场景2_置顶灵感3_顺序_灵感3_灵感1_灵感2_日期_07_09_07_08_无`() {
        // 灵感1 与灵感3 都置顶，灵感2 非置顶
        val list = listOf(
            inspiration(id = 3, createdAt = t_07_09_21_27, isPinned = true),
            inspiration(id = 1, createdAt = t_07_08_23_27, isPinned = true),
            inspiration(id = 2, createdAt = t_07_08_21_29, isPinned = false)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(3, result.size)
        assertTrue(result[0].showDate)   // 灵感3: 07.09 首条
        assertTrue(result[1].showDate)   // 灵感1: 07.08 ≠ 07.09
        assertFalse(result[2].showDate)  // 灵感2: 07.08 == 07.08（与前一条相同）
    }

    @Test
    fun `场景3_置顶灵感2_三者都置顶_顺序_灵感3_灵感1_灵感2_日期_07_09_07_08_无`() {
        val list = listOf(
            inspiration(id = 3, createdAt = t_07_09_21_27, isPinned = true),
            inspiration(id = 1, createdAt = t_07_08_23_27, isPinned = true),
            inspiration(id = 2, createdAt = t_07_08_21_29, isPinned = true)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(3, result.size)
        assertTrue(result[0].showDate)   // 灵感3: 07.09 首条
        assertTrue(result[1].showDate)   // 灵感1: 07.08 ≠ 07.09
        assertFalse(result[2].showDate)  // 灵感2: 07.08 == 07.08
    }

    // ========== 边界场景 ==========

    @Test
    fun `置顶与非置顶同日_第二条不显示日期`() {
        val list = listOf(
            inspiration(id = 1, createdAt = t_07_09_21_27, isPinned = true),
            inspiration(id = 2, createdAt = t_07_09_21_27, isPinned = false)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(2, result.size)
        assertTrue(result[0].showDate)
        assertFalse(result[1].showDate)  // 同日
    }

    @Test
    fun `跨年月边界_两条都显示日期`() {
        val list = listOf(
            inspiration(id = 1, createdAt = t_06_30_21_00, isPinned = true),
            inspiration(id = 2, createdAt = t_07_01_09_00, isPinned = false)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(2, result.size)
        assertTrue(result[0].showDate)
        assertTrue(result[1].showDate)  // 07.01 ≠ 06.30
    }

    @Test
    fun `连续3条同日_仅首条显示`() {
        val list = listOf(
            inspiration(id = 1, createdAt = t_07_09_21_27),
            inspiration(id = 2, createdAt = t_07_09_21_27),
            inspiration(id = 3, createdAt = t_07_09_21_27)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(3, result.size)
        assertTrue(result[0].showDate)
        assertFalse(result[1].showDate)
        assertFalse(result[2].showDate)
    }

    @Test
    fun `空列表_返回空`() {
        val result = InspirationViewModel.buildDisplayItems(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `单条_显示日期`() {
        val list = listOf(inspiration(id = 1, createdAt = t_07_09_21_27))

        val result = InspirationViewModel.buildDisplayItems(list)

        assertEquals(1, result.size)
        assertTrue(result[0].showDate)
    }

    // ========== isPinned 透传校验 ==========

    @Test
    fun `isPinned_透传到显示项`() {
        val list = listOf(
            inspiration(id = 1, createdAt = t_07_09_21_27, isPinned = true),
            inspiration(id = 2, createdAt = t_07_09_21_27, isPinned = false)
        )

        val result = InspirationViewModel.buildDisplayItems(list)

        assertTrue(result[0].isPinned)
        assertFalse(result[1].isPinned)
    }

    // ========== isSameDay 直接校验 ==========

    @Test
    fun `isSameDay_同年同月同日_返回true`() {
        assertTrue(InspirationViewModel.isSameDay(t_07_08_23_27, t_07_08_21_29))
    }

    @Test
    fun `isSameDay_不同日_返回false`() {
        assertFalse(InspirationViewModel.isSameDay(t_07_08_23_27, t_07_09_21_27))
    }

    @Test
    fun `isSameDay_跨月_返回false`() {
        assertFalse(InspirationViewModel.isSameDay(t_06_30_21_00, t_07_01_09_00))
    }
}
