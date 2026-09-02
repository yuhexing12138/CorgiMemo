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
 * 1. 「插入图片后撤销 → 撤销键变灰、无法继续撤销文字」——[takePendingFocus] 与
 *    [ReplaceBlocksCommand] 暂存并原样还原原始块对象（保留块内 history）的修复；
 * 2. 「撤销图片后光标跑到『一』左边（offset 0）」——[pendingFocus] 取走即清空、
 *    焦点偏移原子落地的修复。
 *
 * 测试通过公开/内部 API 复刻真实交互：逐字输入产生带历史的块、移动光标、插入图片、撤销。
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
}
