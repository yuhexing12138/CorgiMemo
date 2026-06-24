package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * SwipeableTodoBox 飞书式左滑交互 Compose UI 测试
 *
 * 覆盖 6 个关键路径：
 * 1. 未滑动时按钮不可见
 * 2. 拖动 ≥ 72dp 完全展开
 * 3. 拖动 < 72dp 回弹关闭
 * 4. 点击置顶触发 onPinClick
 * 5. 点击分享触发 onShareClick
 * 6. 点击删除触发 onDeleteClick
 */
class SwipeableTodoBoxTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * 辅助 Composable：构造一个最小可测试的 SwipeableTodoBox 容器
     *
     * - 使用 MaterialTheme + Surface 包裹提供合理主题与背景
     * - 使用 Modifier.testTag("swipeBox") 标记 SwipeableTodoBox 本身以便模拟滑动
     * - 使用 Modifier.testTag("cardContent") 标记内部 content 区域
     *
     * @param isEnabled 是否启用左滑
     * @param isExpanded 是否处于展开状态
     * @param onPinClick 置顶点击回调
     * @param onShareClick 分享点击回调
     * @param onDeleteClick 删除点击回调
     * @param onExpandChange 展开状态变化回调
     */
    @androidx.compose.runtime.Composable
    private fun TestContainer(
        isEnabled: Boolean = true,
        isExpanded: Boolean = false,
        onPinClick: () -> Unit = {},
        onShareClick: () -> Unit = {},
        onDeleteClick: () -> Unit = {},
        onExpandChange: (Boolean) -> Unit = {}
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                SwipeableTodoBox(
                    modifier = Modifier.testTag("swipeBox"),
                    isEnabled = isEnabled,
                    isExpanded = isExpanded,
                    onPinClick = onPinClick,
                    onShareClick = onShareClick,
                    onDeleteClick = onDeleteClick,
                    onExpandChange = onExpandChange
                ) {
                    Box(modifier = Modifier.height(80.dp).testTag("cardContent")) {
                        Text("测试卡片", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }

    /**
     * 验收 AC-1：未滑动时 3 按钮完全不可见
     *
     * 初始状态 cardOffsetX=0，按钮 alpha=0 → 三个操作按钮均不可见
     */
    @Test
    fun buttons_initiallyHidden() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 初始时 3 个按钮 alpha=0，应该不可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("分享").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("删除").assertIsNotDisplayed()
    }

    /**
     * 验收 AC-4：拖动 ≥ 72dp 松手后完全展开
     *
     * swipeLeft() 默认从中心拖到屏幕左边缘，距离必然 > 72dp 阈值，
     * 松手后 cardOffsetX 弹簧动画到 -actionsWidthPx，按钮完全显示
     */
    @Test
    fun swipe_fullyExpanded_allButtonsVisible() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 左滑（默认从中心拖到左边），距离足够超过 72dp 屏宽
        composeTestRule.onNodeWithTag("swipeBox")
            .performTouchInput { swipeLeft() }

        // 等待 600ms 让动画完成（spring 弹性 + 按钮渐入 + 缓冲）
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 完全展开后，3 个按钮都应该可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("分享").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("删除").assertIsDisplayed()
    }

    /**
     * 验收 AC-5：拖动 < 72dp 松手后回弹关闭
     *
     * 使用 performTouchInput 控制拖动距离到 200px（小于完全展开所需的距离），
     * 期望松手后按钮回弹到不可见状态
     */
    @Test
    fun swipePartialReboundToClosed() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 较小距离的左滑（200px），不足以触发完全展开阈值
        composeTestRule.onNodeWithTag("swipeBox")
            .performTouchInput {
                swipeLeft(distanceX = 200f)
            }

        // 等待 600ms 让回弹动画完成
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 拖动幅度不足，按钮应回弹到不可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsNotDisplayed()
    }

    /**
     * 验收 AC-7：点击置顶按钮触发 onPinClick 回调
     *
     * 流程：先完全展开 swipe → 点击置顶按钮 → 验证回调被调用
     */
    @Test
    fun clickPin_triggersCallback() {
        var pinClicked = false

        composeTestRule.setContent {
            TestContainer(onPinClick = { pinClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击置顶按钮
        composeTestRule.onNodeWithContentDescription("置顶").performClick()

        assertTrue("onPinClick 未被调用", pinClicked)
    }

    /**
     * 验收 AC-8：点击分享按钮触发 onShareClick 回调
     *
     * 流程：先完全展开 swipe → 点击分享按钮 → 验证回调被调用
     */
    @Test
    fun clickShare_triggersCallback() {
        var shareClicked = false

        composeTestRule.setContent {
            TestContainer(onShareClick = { shareClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击分享按钮
        composeTestRule.onNodeWithContentDescription("分享").performClick()

        assertTrue("onShareClick 未被调用", shareClicked)
    }

    /**
     * 验收 AC-9：点击删除按钮触发 onDeleteClick 回调
     *
     * 流程：先完全展开 swipe → 点击删除按钮 → 验证回调被调用
     */
    @Test
    fun clickDelete_triggersCallback() {
        var deleteClicked = false

        composeTestRule.setContent {
            TestContainer(onDeleteClick = { deleteClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击删除按钮
        composeTestRule.onNodeWithContentDescription("删除").performClick()

        assertTrue("onDeleteClick 未被调用", deleteClicked)
    }
}
