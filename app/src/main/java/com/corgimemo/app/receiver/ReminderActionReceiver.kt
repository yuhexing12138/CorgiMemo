package com.corgimemo.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 通知操作按钮广播接收器
 * 处理通知中的"完成"、"稍后"等操作
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        // Action 常量
        const val ACTION_COMPLETE = "com.corgimemo.app.ACTION_COMPLETE"
        const val ACTION_SNOOZE_10M = "com.corgimemo.app.ACTION_SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "com.corgimemo.app.ACTION_SNOOZE_1H"

        // Extra 常量
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        // Snooze 时间（毫秒）
        const val SNOOZE_10_MINUTES = 10 * 60 * 1000L
        const val SNOOZE_1_HOUR = 60 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (todoId == -1L) return

        // 获取 Repository
        val database = CorgiMemoDatabase.getDatabase(context)
        val todoRepository = TodoRepository(
            todoDao = database.todoDao(),
            ioDispatcher = Dispatchers.IO
        )

        when (intent.action) {
            ACTION_COMPLETE -> {
                // 标记待办完成
                handleCompleteAction(context, todoRepository, todoId, notificationId)
            }
            ACTION_SNOOZE_10M -> {
                // 稍后10分钟
                handleSnoozeAction(context, todoRepository, todoId, notificationId, SNOOZE_10_MINUTES)
            }
            ACTION_SNOOZE_1H -> {
                // 稍后1小时
                handleSnoozeAction(context, todoRepository, todoId, notificationId, SNOOZE_1_HOUR)
            }
        }
    }

    /**
     * 处理"完成"操作
     *
     * @param context 上下文
     * @param todoRepository 待办 Repository
     * @param todoId 待办 ID
     * @param notificationId 通知 ID
     */
    private fun handleCompleteAction(
        context: Context,
        todoRepository: TodoRepository,
        todoId: Long,
        notificationId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 从数据库获取待办
                val todo = todoRepository.getTodoById(todoId)
                todo?.let {
                    // 2. 更新为已完成状态
                    val updatedTodo = createCompletedTodo(it)
                    todoRepository.updateTodo(updatedTodo)
                }

                // 3. 关闭通知
                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理"稍后提醒"操作
     *
     * @param context 上下文
     * @param todoRepository 待办 Repository
     * @param todoId 待办 ID
     * @param notificationId 通知 ID
     * @param delayMillis 延迟时间（毫秒）
     */
    private fun handleSnoozeAction(
        context: Context,
        todoRepository: TodoRepository,
        todoId: Long,
        notificationId: Int,
        delayMillis: Long
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 从数据库获取待办
                val todo = todoRepository.getTodoById(todoId)
                todo?.let {
                    // 2. 更新 reminderTime 为当前时间 + 延迟时间
                    val updatedTodo = createSnoozedTodo(it, delayMillis)
                    todoRepository.updateTodo(updatedTodo)

                    // 3. 重新设置闹钟（如果有 AlarmScheduler 实现）
                    // TODO: AlarmScheduler.scheduleReminder(updatedTodo)
                }

                // 4. 关闭当前通知
                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 创建已完成状态的待办对象
     *
     * @param todo 原始待办对象
     * @return 更新后的待办对象
     */
    private fun createCompletedTodo(todo: TodoItem): TodoItem {
        return todo.copy(
            status = 1,
            completedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 创建稍后提醒的待办对象
     *
     * @param todo 原始待办对象
     * @param delayMillis 延迟时间（毫秒）
     * @return 更新后的待办对象
     */
    private fun createSnoozedTodo(todo: TodoItem, delayMillis: Long): TodoItem {
        val newReminderTime = System.currentTimeMillis() + delayMillis
        return todo.copy(
            reminderTime = newReminderTime,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 关闭通知
     *
     * @param context 上下文
     * @param notificationId 通知 ID
     */
    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
