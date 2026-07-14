package com.corgimemo.app.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 全局 Snackbar 消息控制器
 *
 * 用途：在非 Composable 上下文（Activity、协程、ViewModel 等）中也能触发统一的 Snackbar 提示。
 *
 * 核心特性：
 * 1. **覆盖机制**：使用 [MutableStateFlow] 存储消息，新消息会自动替换旧消息，
 *    配合 Material 3 的 SnackbarHostState 实现"下一个提醒立即覆盖上一个"的效果。
 * 2. **支持 actionLabel**：带按钮的消息会等待用户操作，无按钮消息按自定义短 duration（2 秒）显示。
 * 3. **可消费**：订阅方处理完消息后调用 [consume] 标记为已处理，避免 recompose 时重复弹出。
 *
 * 使用方式：
 * 1. 任意位置调用 [showMessage] / [showMessageWithAction] 发送消息
 * 2. 在 Composable 树最外层订阅 [currentMessage] 并通过 [SnackbarHostState.showSnackbar] 显示
 * 3. 显示完成后调用 [consume] 清除
 *
 * 全应用唯一单例（object），生命周期与 Application 一致。
 */
object GlobalSnackbarController {

    /**
     * Snackbar 消息数据类
     *
     * @param id 自增 id，用于确保 [MutableStateFlow] 发送相同内容的消息时也能触发更新
     * @param text 提示文本
     * @param actionLabel 右侧按钮文案（null 时无按钮）
     */
    data class Message(
        val id: Long,
        val text: String,
        val actionLabel: String? = null
    )

    /**
     * 当前待显示的消息（已读消费后置为 null）
     *
     * 使用 MutableStateFlow：
     * - 多个调用方连续发消息时，自动保留最新一条
     * - 配合 SnackbarHostState 的覆盖机制（showSnackbar 内部 mutex 取消旧协程），
     *   实现"下一个提醒触发时，立即覆盖上一个"
     */
    private val _currentMessage = MutableStateFlow<Message?>(null)

    /**
     * 对外暴露只读消息流，供 Composable 订阅
     */
    val currentMessage: StateFlow<Message?> = _currentMessage.asStateFlow()

    /**
     * 自增 id 计数器，确保即使文本相同也能触发更新
     */
    private var messageId: Long = 0

    /**
     * 发送一条无按钮的 Snackbar 消息
     *
     * 任何线程、任何上下文都可以调用此方法。消息会异步推送到订阅端。
     *
     * @param message 提示文本
     */
    fun showMessage(message: String) {
        showMessageWithAction(message, actionLabel = null)
    }

    /**
     * 发送一条带右侧按钮的 Snackbar 消息
     *
     * @param message 提示文本
     * @param actionLabel 按钮文案（null 时无按钮；非空时显示右侧按钮并延长显示时长）
     */
    fun showMessageWithAction(message: String, actionLabel: String? = null) {
        messageId++
        _currentMessage.value = Message(
            id = messageId,
            text = message,
            actionLabel = actionLabel
        )
    }

    /**
     * 标记当前消息为已消费
     *
     * 订阅方在调用 `SnackbarHostState.showSnackbar(...)` 完成后调用此方法，
     * 避免后续 recompose 时再次弹出相同的消息。
     */
    fun consume() {
        _currentMessage.value = null
    }
}
