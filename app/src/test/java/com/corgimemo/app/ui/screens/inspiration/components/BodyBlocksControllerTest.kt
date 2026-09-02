package com.corgimemo.app.ui.screens.inspiration.components

import com.corgimemo.app.ui.screens.inspiration.components.BodyBlock.Image
import com.corgimemo.app.ui.screens.inspiration.components.BodyBlock.Text
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BodyBlocksController 撤销栈回归测试（方案A：自建 Command 栈 + 库内 history 两套历史隔离）。
 *
 * 覆盖两个已修复的真实用户场景：
 * 1. 「插入图片后撤销 → 撤销键变灰、无法继续撤销文字」——[ReplaceBlocksCommand]
 *    暂存并原样还原原始块对象（保留块内 history）的修复；
 * 2. 「撤销图片后光标跑到『一』左边」——根因是 [currentFocusSpec] 把光标存成了
 *    **有效坐标**（剥 ZWSP 后长度），而所有落点还原入口都把 offset 当**原始坐标**
 *    直接写进 selection；对带前导 ZWSP 的真实打字块（\u200B一二），effective 1 ≠ raw 1，
 *    于是还原后落点变成「ZWSP 与『一』之间 = 『一』左边」。修复：统一 [FocusSpec.offset]
 *    为 raw 坐标（[currentFocusSpec] 直接存 raw 光标）。
 *
 * 特别注意：本类特意保留一条「**直接打字**产生带前导 ZWSP 的块」的用例——否则用
 * setText 重建的块无 ZWSP、effective==raw，旧 bug 代码反而能蒙混过关，测不出真 bug。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BodyBlocksControllerTest {

    /** 每个用例独立的新控制器（构造时自带一个空 Text 块） */
    private lateinit var controller: BodyBlocksController

    @Before
    fun setUp() {
        controller = BodyBlocksController(registerTriggers = {})
    }

    /** 把一段文字逐字输入到指定 Text 块，产生可撤销的打字历史（不含 ZWSP，贴近真实「一二」块） */
    private fun typeInto(block: Text, text: String) {
        // setText("") 清空块并清掉旧 history，之后逐字 addTextAfterSelection 记录打字历史
        block.state.setText("")
        text.forEach { block.state.addTextAfterSelection(it.toString()) }
    }

    /** 剥 ZWSP 取有效文本（与控制器内 effectiveText 对齐） */
    private fun effective(text: String): String = text.filterNot { it == '\u200B' || it == '\uFFFD' }

    // ==================== 取走即清空（take-and-clear）语义 ====================

    /**
     * 场景：focusSpec 写入 pendingFocus 后，takePendingFocus 应原子取走并清空。
     *
     * 预期：第一次取回正确 FocusSpec 且 pendingFocus 置空；第二次返回 null。
     */
    @Test
    fun `takePendingFocus 取走即清空，二次调用返回 null`() {
        val a = controller.blocks.first() as Text
        controller.focusSpec(FocusSpec(a.id, 5))

        val taken = controller.takePendingFocus()
        assertEquals(FocusSpec(a.id, 5), taken)
        assertNull(controller.pendingFocus)

        // 第二次取走应为空，证明已被清空
        assertNull(controller.takePendingFocus())
    }

    // ==================== Bug 1：撤销图片后可继续撤销文字 ====================

    /**
     * 场景：输入「一二」→ 光标移到一/二之间 → 插入图片 → 撤销一次。
     *
     * 预期（修复后）：
     * - 块列表回到单块，且该块是**原始块对象 A 本身**（stash 原样还原，history 不丢）；
     * - 文字内容仍为「一二」；
     * - [BodyBlocksController.canUndo] 仍为 true（撤销键不再变灰）；
     * - 还原块自带的库内 history.canUndo 仍为 true。
     */
    @Test
    fun `插入图片后撤销，可继续撤销且还原原始块`() {
        val a = controller.blocks.first() as Text
        typeInto(a, "一二")
        // 光标移到「一」和「二」之间（有效偏移 1）
        a.state.selection = androidx.compose.ui.text.TextRange(1)
        controller.focusSpec(FocusSpec(a.id, 1))

        // 插入图片 → [Text(一), Image, Text(二)]
        controller.insertImageAtFocused("/fake/image.png")
        assertEquals(3, controller.blocks.size)
        assertTrue(controller.blocks[1] is Image)

        // 撤销图片命令
        controller.undo()

        // 回到单块，且是原始块对象 A（stash 原样还原）
        assertEquals(1, controller.blocks.size)
        assertSame(a, controller.blocks.first())
        assertEquals("一二", effective(a.state.annotatedString.text))

        // Bug 1 核心断言：撤销键不灰、可继续撤销
        assertTrue("撤销图片后 canUndo 应为 true（修复前会变灰）", controller.canUndo)
        assertTrue("还原出的原始块应保留打字历史", a.state.history.canUndo)
    }

    /**
     * 场景：同上插入并撤销图片后，一路撤销直至文字清空。
     *
     * 预期：至少执行 2 次 undo（图片命令 + 至少一次文字撤销），证明撤销键没有在
     * 撤销图片后提前变灰；最终有效文本为空。
     */
    @Test
    fun `撤销图片后可一路撤销至文字清空`() {
        val a = controller.blocks.first() as Text
        typeInto(a, "一二")
        a.state.selection = androidx.compose.ui.text.TextRange(1)
        controller.focusSpec(FocusSpec(a.id, 1))

        controller.insertImageAtFocused("/fake/image.png")
        controller.undo() // 撤销图片命令

        var undoCount = 0
        while (controller.canUndo) {
            controller.undo()
            undoCount++
        }

        // 至少图片撤销 + 一次文字撤销，证明撤销键未提前变灰
        assertTrue("撤销图片后应能继续撤销文字（至少 2 次）", undoCount >= 2)
        assertEquals("", effective(a.state.annotatedString.text))
    }

    // ==================== Bug 2：撤销图片后焦点在「一」「二」之间 ====================

    /**
     * 场景：输入「一二」→ 光标移到一/二之间 → 插入图片 → 撤销一次。
     *
     * 预期（修复后）：[BodyBlocksController.pendingFocus] 的偏移为 1（一/二之间），
     * 而非 0（『一』左边）。
     */
    @Test
    fun `插入图片后撤销，焦点落在二字之间而非左边`() {
        val a = controller.blocks.first() as Text
        typeInto(a, "一二")
        a.state.selection = androidx.compose.ui.text.TextRange(1)
        controller.focusSpec(FocusSpec(a.id, 1))

        controller.insertImageAtFocused("/fake/image.png")
        controller.undo()

        assertNotNull(controller.pendingFocus)
        assertEquals("撤销后焦点应落在一/二之间（偏移 1），而非『一』左边（偏移 0）",
            1, controller.pendingFocus?.offset)
        assertEquals(a.id, controller.pendingFocus?.blockId)
    }

    // ==================== Bug 2（真场景）：带前导 ZWSP 的打字块 ====================

    /**
     * 复刻真实打字场景：不调 setText（那会清掉 ZWSP），而是直接在控制器自带、
     * 预置了 ZWSP 的初始空块上逐字输入，得到与用户真实一致的「\u200B一二」块。
     *
     * 场景：type 一二 → 光标移到「一」「二」之间（raw 偏移 = 2，ZWSP 占 1 位）
     *       → 插图片 → 撤销一次。
     *
     * 预期（修复后）：[BodyBlocksController.pendingFocus] 的 offset == 2（raw，一/二之间），
     * 而非 1（旧「有效坐标」误算，落到 ZWSP 与『一』之间 = 『一』左边）。
     *
     * 这条用例用带 ZWSP 的块，才是触发并验证 Bug 2 真根因（effective≠raw）的用例；
     * 上方无 ZWSP 的用例即便在修复前也会通过，不足以证明修复有效。
     */
    @Test
    fun `带ZWSP的真实打字块，撤销图片后焦点落一/二之间 raw偏移`() {
        val a = controller.blocks.first() as Text
        // 直接在预置 ZWSP 的初始块上打字 → \u200B一二（保留前导 ZWSP，贴近真实）
        "一二".forEach { a.state.addTextAfterSelection(it.toString()) }
        assertTrue("打字块应保留前导 ZWSP（\u200B一二），否则无法复现该 bug",
            a.state.annotatedString.text.startsWith('\u200B'))
        // 光标移到「一」「二」之间：\u200B 一 二 → raw 偏移 2
        a.state.selection = androidx.compose.ui.text.TextRange(2)
        controller.focusSpec(FocusSpec(a.id, 2))

        controller.insertImageAtFocused("/fake/image.png")
        controller.undo()

        assertNotNull(controller.pendingFocus)
        assertEquals(a.id, controller.pendingFocus?.blockId)
        assertEquals(
            "撤销后焦点应落在「一」「二」之间（raw 偏移 2，含 ZWSP 1 位），而非『一』左边（偏移 1）",
            2, controller.pendingFocus?.offset
        )
        // 还原块的 selection 也应是 raw 2（一/二之间），而非 raw 1（一左边）
        assertEquals(2, a.state.selection.start)
    }
}
