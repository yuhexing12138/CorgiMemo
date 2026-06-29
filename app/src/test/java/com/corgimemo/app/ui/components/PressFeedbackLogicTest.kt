package com.corgimemo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PressFeedbackLogicTest {

    @Test
    fun `resolveNextState IDLE 状态保持 IDLE`() {
        val result = PressFeedbackLogic.resolveNextState(
            currentState = PressState.IDLE,
            dragDistance = 0f,
            touchSlop = 8f,
            elapsedSinceDownMs = 0L
        )
        assertEquals(PressState.IDLE, result)
    }

    @Test
    fun `resolveNextState 16ms 内移动超过 touchSlop 切换为 SCROLL_CONTACT`() {
        val result = PressFeedbackLogic.resolveNextState(
            currentState = PressState.SCROLL_CONTACT,
            dragDistance = 10f,
            touchSlop = 8f,
            elapsedSinceDownMs = 10L
        )
        assertEquals(PressState.SCROLL_CONTACT, result)
    }

    @Test
    fun `resolveNextState 16ms 后未移动 切换为 PRESS`() {
        val result = PressFeedbackLogic.resolveNextState(
            currentState = PressState.SCROLL_CONTACT,
            dragDistance = 0f,
            touchSlop = 8f,
            elapsedSinceDownMs = 20L
        )
        assertEquals(PressState.PRESS, result)
    }

    @Test
    fun `resolveNextState 16ms 内未移动 保持 SCROLL_CONTACT 等待 16ms 后再判断`() {
        // 在 16ms 内即使 dragDistance 较小，状态保持原状
        val result = PressFeedbackLogic.resolveNextState(
            currentState = PressState.SCROLL_CONTACT,
            dragDistance = 0f,
            touchSlop = 8f,
            elapsedSinceDownMs = 10L
        )
        assertEquals(PressState.SCROLL_CONTACT, result)
    }

    @Test
    fun `resolveNextState 16ms 后移动超过 touchSlop 保持 SCROLL_CONTACT`() {
        val result = PressFeedbackLogic.resolveNextState(
            currentState = PressState.SCROLL_CONTACT,
            dragDistance = 10f,
            touchSlop = 8f,
            elapsedSinceDownMs = 20L
        )
        assertEquals(PressState.SCROLL_CONTACT, result)
    }

    @Test
    fun `targetScaleForState IDLE 返回 1f`() {
        val result = PressFeedbackLogic.targetScaleForState(PressState.IDLE, 0.92f)
        assertEquals(1f, result, 0.001f)
    }

    @Test
    fun `targetScaleForState SCROLL_CONTACT 返回 scaleDown`() {
        val result = PressFeedbackLogic.targetScaleForState(PressState.SCROLL_CONTACT, 0.92f)
        assertEquals(0.92f, result, 0.001f)
    }

    @Test
    fun `targetScaleForState PRESS 返回 1f`() {
        val result = PressFeedbackLogic.targetScaleForState(PressState.PRESS, 0.92f)
        assertEquals(1f, result, 0.001f)
    }
}
