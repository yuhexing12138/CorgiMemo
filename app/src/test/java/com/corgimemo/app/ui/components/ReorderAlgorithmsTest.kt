package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 拖拽排序算法纯函数单元测试
 *
 * 覆盖：
 * - checkPinnedZoneCrossed：跨区检测、divider 跳过、边界情况
 */
class ReorderAlgorithmsTest {

    /** 测试用数据项：isPinned 表示是否置顶，isDivider 表示是否分隔按钮 */
    private data class TestItem(val isPinned: Boolean, val isDivider: Boolean = false)

    /** 便捷封装：传入 List<TestItem> 调用算法 */
    private fun checkCrossed(
        items: List<TestItem>,
        draggedOriginalIsPinned: Boolean,
        draggedCurrentIndex: Int
    ): Boolean = ReorderAlgorithms.checkPinnedZoneCrossed(
        displayItems = items,
        isPinned = { it.isPinned },
        isDivider = { it.isDivider },
        draggedOriginalIsPinned = draggedOriginalIsPinned,
        draggedCurrentIndex = draggedCurrentIndex
    )

    // ========== 原有5个测试（签名适配，逻辑不变） ==========

    /**
     * 场景：原置顶，拖到非置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶到非置顶返回 true`() {
        val items = listOf(TestItem(false), TestItem(false))
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 0)
        assertEquals(true, result)
    }

    /**
     * 场景：原非置顶，拖到置顶区
     * 预期：返回 true
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶到置顶返回 true`() {
        val items = listOf(TestItem(true), TestItem(true))
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 0)
        assertEquals(true, result)
    }

    /**
     * 场景：置顶区内移动
     * 预期：返回 false（未跨越分界线）
     */
    @Test
    fun `checkPinnedZoneCrossed 置顶区内移动返回 false`() {
        val items = listOf(TestItem(true), TestItem(true))
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：非置顶区内移动
     * 预期：返回 false
     */
    @Test
    fun `checkPinnedZoneCrossed 非置顶区内移动返回 false`() {
        val items = listOf(TestItem(false), TestItem(false))
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：index 越界
     * 预期：返回 false（兜底）
     */
    @Test
    fun `checkPinnedZoneCrossed index 越界返回 false`() {
        val items = listOf(TestItem(true))
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 5)
        assertEquals(false, result)
    }

    // ========== 新增：divider 跳过测试（修复核心） ==========

    /**
     * 场景：置顶项拖到置顶区内（前方邻居是 PinnedDivider）
     *
     * displayItems: [PinnedDivider(false), P1(true), P2(true), P3(true)]
     * 拖 P4(true) 到 index=1，前方邻居是 PinnedDivider
     * 旧算法：邻居=false → crossed=true ❌
     * 新算法：跳过 divider 找到 P1(true) → crossed=false ✓
     */
    @Test
    fun `置顶项拖到置顶区内应跳过 PinnedDivider 不跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),  // PinnedDivider
            TestItem(isPinned = true),                       // P1
            TestItem(isPinned = true),                       // P2
            TestItem(isPinned = true)                        // P3
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：非置顶项拖到置顶区内（前方邻居是 PinnedDivider）
     *
     * displayItems: [PinnedDivider(false), P1(true), P2(true)]
     * 拖 N1(false) 到 index=1，前方邻居是 PinnedDivider
     * 旧算法：邻居=false → crossed=false ❌（未翻转）
     * 新算法：跳过 divider 找到 P1(true) → crossed=true ✓（翻转为置顶）
     */
    @Test
    fun `非置顶项拖到置顶区内应跳过 PinnedDivider 跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),  // PinnedDivider
            TestItem(isPinned = true),                       // P1
            TestItem(isPinned = true)                        // P2
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 1)
        assertEquals(true, result)
    }

    /**
     * 场景：置顶项拖到置顶区末尾（前方邻居是 PendingDivider）
     *
     * displayItems: [P1(true), P2(true), PendingDivider(false), N1(false)]
     * 拖 P3(true) 到 index=2，前方邻居 P2(true)
     * 新算法：邻居=true → crossed=false ✓
     */
    @Test
    fun `置顶项拖到置顶区末尾应取前方真实邻居`() {
        val items = listOf(
            TestItem(isPinned = true),                       // P1
            TestItem(isPinned = true),                       // P2
            TestItem(isPinned = false, isDivider = true),   // PendingDivider
            TestItem(isPinned = false)                        // N1
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 2)
        assertEquals(false, result)
    }

    /**
     * 场景：拖到列表最顶部（index=0，前方无项，向后找邻居）
     *
     * displayItems: [P1(true), P2(true)]
     * 拖 N1(false) 到 index=0，前方无项，向后找 P1(true)
     * 新算法：邻居=true → crossed=true ✓
     */
    @Test
    fun `拖到列表顶部应向后找邻居`() {
        val items = listOf(
            TestItem(isPinned = true),                       // P1
            TestItem(isPinned = true)                        // P2
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 0)
        assertEquals(true, result)
    }

    /**
     * 场景：列表只有待办无 divider（Case B 或纯待办场景）
     *
     * displayItems: [P1(true), P2(true), N1(false)]
     * 拖 P2(true) 到 index=1，前方邻居 P1(true)
     * 新算法：邻居=true → crossed=false ✓（与旧算法行为一致，不变量保证）
     */
    @Test
    fun `列表无 divider 时行为不变`() {
        val items = listOf(
            TestItem(isPinned = true),
            TestItem(isPinned = true),
            TestItem(isPinned = false)
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：整个列表都是 divider（理论上不可能，防御性）
     *
     * displayItems: [PinnedDivider, PendingDivider]
     * 新算法：找不到非 divider 邻居 → crossed=false
     */
    @Test
    fun `全 divider 列表应返回 false`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),
            TestItem(isPinned = false, isDivider = true)
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：draggedCurrentIndex 为负数
     * 预期：返回 false（兜底）
     */
    @Test
    fun `draggedCurrentIndex 负数应返回 false`() {
        val items = listOf(TestItem(isPinned = true))
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = -1)
        assertEquals(false, result)
    }

    /**
     * 场景：已置顶项（isPinned=true）在置顶区内拖拽，邻居同为 isPinned=true
     *
     * 这是 "首次拖入置顶区后再次拖拽" bug 场景的算法层不变式：
     * - draggedOriginalIsPinned=true（修复后由 displayItems 查询得到）
     * - 邻居 isPinned=true
     * - 期望 crossed=false（不触发 isPinned 翻转）
     *
     * 回归保护：未来若算法被重构，必须保持此不变式。
     */
    @Test
    fun `已置顶项与置顶区邻居同区不应跨区`() {
        val items = listOf(
            TestItem(isPinned = true),   // P1
            TestItem(isPinned = true)    // P2
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 1)
        assertEquals(false, result)
    }

    /**
     * 场景：非置顶项紧贴 PendingDivider 之后放置（divider 后第一项）
     *
     * 这是 "N6 拖到 PendingDivider 和 N5 之间" bug 场景的算法层不变式：
     * - displayItems: [PinnedDivider, P4, PendingDivider, N6(被拖到此处), N5]
     * - draggedOriginalIsPinned=false（N6 非置顶）
     * - 前邻居是 PendingDivider（边界）→ 应停止向前搜索
     * - 后邻居是 N5（isPinned=false）
     * - 期望 crossed=false（不触发 isPinned 翻转）
     *
     * 回归保护：未来若算法被重构，必须保持 "divider 是区域边界，不能跨越" 契约。
     */
    @Test
    fun `divider 后第一项不应跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),   // PinnedDivider
            TestItem(isPinned = true),                       // P4
            TestItem(isPinned = false, isDivider = true),   // PendingDivider
            TestItem(isPinned = false),                      // N6（被拖到此处）
            TestItem(isPinned = false)                        // N5
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = false, draggedCurrentIndex = 3)
        assertEquals(false, result)
    }

    /**
     * 场景：置顶项紧贴 PendingDivider 之前放置（divider 前第一项）
     *
     * 对称场景：置顶区末尾项不应因后邻居是 divider 而被误判跨区。
     * - displayItems: [PinnedDivider, P1(前邻居), P4(被拖到此处), PendingDivider, N5]
     * - draggedOriginalIsPinned=true（P4 置顶）
     * - 前邻居是 P1（isPinned=true）
     * - 后邻居是 PendingDivider（边界）→ 应停止向后搜索
     * - 期望 crossed=false
     */
    @Test
    fun `divider 前第一项不应跨区`() {
        val items = listOf(
            TestItem(isPinned = false, isDivider = true),   // PinnedDivider
            TestItem(isPinned = true),                       // P1（前邻居）
            TestItem(isPinned = true),                       // P4（被拖到此处）
            TestItem(isPinned = false, isDivider = true),   // PendingDivider
            TestItem(isPinned = false)                        // N5
        )
        val result = checkCrossed(items, draggedOriginalIsPinned = true, draggedCurrentIndex = 2)
        assertEquals(false, result)
    }
}
