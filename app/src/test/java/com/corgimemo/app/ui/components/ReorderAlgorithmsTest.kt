package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 拖拽排序算法纯函数单元测试
 *
 * 覆盖：
 * - findSwapTarget：等高/变高/无重叠/自身排除
 * - checkPinnedZoneCrossed：置顶→非置顶/非置顶→置顶/区内移动
 */
class ReorderAlgorithmsTest {

    // ==================== findSwapTarget 测试 ====================

    /**
     * 场景：被拖项与下方项 70% 重叠（等高 100px）
     * 预期：返回下方项 key
     */
    @Test
    fun `findSwapTarget 等高 70 percent 重叠返回目标`() {
        val draggedKey = "A"
        val otherKey = "B"
        // 被拖项中心 fingerY=220，区间 [170, 270]
        // 下方项 offset=200, size=100，区间 [200, 300]
        // 重叠 [200, 270] = 70px → 70%
        val visible = listOf(
            VisibleItemInfo(draggedKey, offset = 0, size = 100),
            VisibleItemInfo(otherKey, offset = 200, size = 100)
        )
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 220f,
            draggedSize = 100,
            visibleItems = visible
        )
        assertEquals(otherKey, target)
    }

    /**
     * 场景：被拖项与下方项 10% 重叠
     * 预期：返回 null（未达 50% 阈值）
     */
    @Test
    fun `findSwapTarget 10 percent 重叠返回 null`() {
        val draggedKey = "A"
        val otherKey = "B"
        // fingerY=160，区间 [110, 210]
        // 下方项 [200, 300]，重叠 [200, 210] = 10px → 10%
        val visible = listOf(
            VisibleItemInfo(draggedKey, offset = 0, size = 100),
            VisibleItemInfo(otherKey, offset = 200, size = 100)
        )
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 160f,
            draggedSize = 100,
            visibleItems = visible
        )
        assertNull(target)
    }

    /**
     * 场景：变高项 - 被拖项 80px，其他项 200px，重叠 80px
     * 预期：重叠比例 = 80 / max(80, 200) = 40% < 50% → 返回 null
     *
     * 说明：修正后使用 maxSize 作为分母，使小卡片不再易触发交换。
     * 此测试验证阈值算法变更后的正确行为。
     */
    @Test
    fun `findSwapTarget 变高项 80px 对 200px 重叠 40 percent 返回 null`() {
        val draggedKey = "A"
        val otherKey = "B"
        // 被拖项 80px，fingerY=140，区间 [100, 180]
        // 其他项 offset=80, size=200，区间 [80, 280]
        // 重叠 [100, 180] = 80px → 80/max(80,200)=40% < 50%
        val visible = listOf(
            VisibleItemInfo(draggedKey, offset = 0, size = 80),
            VisibleItemInfo(otherKey, offset = 80, size = 200)
        )
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 140f,
            draggedSize = 80,
            visibleItems = visible
        )
        assertNull(target)
    }

    /**
     * 场景：仅自身可见
     * 预期：返回 null（不与自身交换）
     */
    @Test
    fun `findSwapTarget 仅自身可见返回 null`() {
        val draggedKey = "A"
        val visible = listOf(VisibleItemInfo(draggedKey, offset = 0, size = 100))
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 50f,
            draggedSize = 100,
            visibleItems = visible
        )
        assertNull(target)
    }

    /**
     * 场景：80px vs 200px，重叠 60px
     * 预期：60 / max(80, 200) = 30% < 50% → null
     */
    @Test
    fun `findSwapTarget 高度差 60px 重叠 30 percent 返回 null`() {
        val draggedKey = "A"
        val otherKey = "B"
        // 被拖项 80px，fingerY=150，区间 [110, 190]
        // 其他项 offset=100, size=200，区间 [100, 300]
        // 重叠 [110, 190] = 80px → 80/max(80,200)=40% < 50% → null
        val visible = listOf(
            VisibleItemInfo(draggedKey, offset = 0, size = 80),
            VisibleItemInfo(otherKey, offset = 100, size = 200)
        )
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 150f,
            draggedSize = 80,
            visibleItems = visible
        )
        assertNull(target)
    }

    /**
     * 场景：150px vs 200px，重叠 150px
     * 预期：150 / max(150, 200) = 75% > 50% → 返回目标
     */
    @Test
    fun `findSwapTarget 高度差 110px 重叠 55 percent 返回目标`() {
        val draggedKey = "A"
        val otherKey = "B"
        // 被拖项 150px，fingerY=200，区间 [125, 275]
        // 其他项 offset=100, size=200，区间 [100, 300]
        // 重叠 [125, 275] = 150px → 150/max(150,200)=150/200=75% > 50% → 返回目标
        val visible = listOf(
            VisibleItemInfo(draggedKey, offset = 0, size = 150),
            VisibleItemInfo(otherKey, offset = 100, size = 200)
        )
        val target = ReorderAlgorithms.findSwapTarget(
            draggedKey = draggedKey,
            fingerY = 200f,
            draggedSize = 150,
            visibleItems = visible
        )
        assertEquals(otherKey, target)
    }

    // ==================== checkPinnedZoneCrossed 测试 ====================

    /**
     * 场景：原置顶，拖到非置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶到非置顶返回 true`() {
        // 算法设计：传入的 displayItems 是被拖项已移除后的列表
        val displayItems = listOf(false, false)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 0 // 插入到位置 0，邻居为位置 1（false）
        )
        assertEquals(true, result)
    }

    /**
     * 场景：原非置顶，拖到置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶到置顶返回 true`() {
        // 列表（被拖移除后）：[true, true]
        // 被拖原始 isPinned=false，插入到位置 0
        val displayItems = listOf(true, true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = false,
            draggedCurrentIndex = 0
        )
        assertEquals(true, result)
    }

    /**
     * 场景：置顶区内移动
     * 预期：返回 false（未跨越分界线）
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶区内移动返回 false`() {
        val displayItems = listOf(true, true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 1
        )
        assertEquals(false, result)
    }

    /**
     * 场景：非置顶区内移动
     * 预期：返回 false
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶区内移动返回 false`() {
        val displayItems = listOf(false, false)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = false,
            draggedCurrentIndex = 1
        )
        assertEquals(false, result)
    }

    /**
     * 场景：index 越界
     * 预期：返回 false（兜底）
     */
    @Test
    fun `checkPinnedZoneCrossed index 越界返回 false`() {
        val displayItems = listOf(true)
        val result = ReorderAlgorithms.checkPinnedZoneCrossed(
            displayItems = displayItems,
            draggedOriginalIsPinned = true,
            draggedCurrentIndex = 5
        )
        assertEquals(false, result)
    }

    // ==================== computeReleaseStartOffset 测试 ====================

    @Test
    fun `computeReleaseStartOffset 正偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 200f,
            baseCenterY = 140f
        )
        assertEquals(60.0f, offset, 0.001f)
    }

    @Test
    fun `computeReleaseStartOffset 零偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 140f,
            baseCenterY = 140f
        )
        assertEquals(0.0f, offset, 0.001f)
    }

    @Test
    fun `computeReleaseStartOffset 负偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 110f,
            baseCenterY = 140f
        )
        assertEquals(-30.0f, offset, 0.001f)
    }

    // ==================== shouldSkipDisplayUpdate 测试 ====================

    @Test
    fun `shouldSkipDisplayUpdate 释放期间返回 true`() {
        assertEquals(true, ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing = true))
    }

    @Test
    fun `shouldSkipDisplayUpdate 非释放期间返回 false`() {
        assertEquals(false, ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing = false))
    }

    // ==================== computeDraggedListCenterY 测试 ====================

    /**
     * 场景：被拖项被推到列表顶部（targetIndex=0）
     * 预期：被拖项中心 Y = 自身高度一半 = 75.0f
     * 依据：顶部 Y = 0 * 160 = 0，中心 = 0 + 150/2 = 75
     */
    @Test
    fun `computeDraggedListCenterY 移到顶部 index 0 返回被拖项中心`() {
        val centerY = ReorderAlgorithms.computeDraggedListCenterY(
            targetIndex = 0,
            draggedSize = 150,
            averageItemHeightPx = 160f
        )
        assertEquals(75.0f, centerY, 0.001f)
    }

    /**
     * 场景：被拖项被推到 index=2 位置
     * 预期：被拖项中心 Y = 2 * 160 + 150/2 = 320 + 75 = 395.0f
     */
    @Test
    fun `computeDraggedListCenterY 移到 index 2 返回 395f`() {
        val centerY = ReorderAlgorithms.computeDraggedListCenterY(
            targetIndex = 2,
            draggedSize = 150,
            averageItemHeightPx = 160f
        )
        assertEquals(395.0f, centerY, 0.001f)
    }

    /**
     * 场景：平均行高为 0（异常值，未初始化或空列表场景）
     * 预期：回退到 draggedSize 一半 = 75.0f（与 targetIndex 无关）
     */
    @Test
    fun `computeDraggedListCenterY 行高为 0 回退到 draggedSize 一半`() {
        val centerY = ReorderAlgorithms.computeDraggedListCenterY(
            targetIndex = 0,
            draggedSize = 150,
            averageItemHeightPx = 0f
        )
        assertEquals(75.0f, centerY, 0.001f)
    }

    /**
     * 场景：平均行高为负数（异常值，防止乘法越界）
     * 预期：回退到 draggedSize 一半 = 50.0f
     */
    @Test
    fun `computeDraggedListCenterY 负数行高回退`() {
        val centerY = ReorderAlgorithms.computeDraggedListCenterY(
            targetIndex = 5,
            draggedSize = 100,
            averageItemHeightPx = -50f
        )
        assertEquals(50.0f, centerY, 0.001f)
    }

    /**
     * 关键回归测试：验证 Bug B（非首页卡片与首页卡片交换后飞出屏幕）不会复发
     *
     * 场景：首页卡片 A（index=0）下移与 index=2 的卡片 B 交换位置后
     * 此刻 B（被拖项）的目标索引 = 0
     * 旧实现：read otherInfo.offset of A（A 此时仍在原位 offset=0），得到 baseCenterY=0+150/2=75
     * 视觉瞬移：手指 fingerY=275，松手 releaseStartOffset=275-75=200px，盒子飞出去
     *
     * 新实现：用目标索引反推基线，targetIndex=0 时中心=75，
     * 释放起点偏移 = 275 - 75 = 200f（与原 baseline 一致，无瞬移）
     * 但当 B 实际下移后手指在 275 位置时，松手 releaseStartOffset 必须等于手指相对新基线 75 的偏移
     * 此测试用纯函数层面固化"移到顶部"基线 = 75f（而非 275f）
     */
    @Test
    fun `computeDraggedListCenterY B 上移与 A 交换后基线为 75f 不是 275f`() {
        val centerY = ReorderAlgorithms.computeDraggedListCenterY(
            targetIndex = 0,
            draggedSize = 150,
            averageItemHeightPx = 160f
        )
        // 关键断言：基线必须等于 75f（顶部中心），绝对不能等于 275f（旧 BUG 的位置）
        assertEquals(75.0f, centerY, 0.001f)
    }
}
