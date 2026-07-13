package com.corgimemo.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.notification.AlarmScheduler
import com.corgimemo.app.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 通知操作按钮广播接收器
 * 处理通知中的"完成"、"稍后"等操作
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REMINDER = "com.corgimemo.app.ACTION_REMINDER"
        const val ACTION_COMPLETE = "com.corgimemo.app.ACTION_COMPLETE"
        const val ACTION_SNOOZE_10M = "com.corgimemo.app.ACTION_SNOOZE_10M"
        const val ACTION_SNOOZE_1H = "com.corgimemo.app.ACTION_SNOOZE_1H"
        const val ACTION_SNOOZE_TOMORROW = "com.corgimemo.app.ACTION_SNOOZE_TOMORROW"
        const val ACTION_SNOOZE_WEEKEND = "com.corgimemo.app.ACTION_SNOOZE_WEEKEND"

        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        const val SNOOZE_10_MINUTES = 10 * 60 * 1000L
        const val SNOOZE_1_HOUR = 60 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        if (todoId == -1L) return

        val database = CorgiMemoDatabase.getDatabase(context)
        val todoDao = database.todoDao()
        val categoryDao = database.categoryDao()

        when (intent.action) {
            ACTION_REMINDER -> {
                handleReminderTrigger(context, todoDao, categoryDao, todoId)
            }
            ACTION_COMPLETE -> {
                handleCompleteAction(context, todoDao, todoId, notificationId)
            }
            ACTION_SNOOZE_10M -> {
                handleSnoozeAction(context, todoDao, categoryDao, todoId, notificationId, SNOOZE_10_MINUTES)
            }
            ACTION_SNOOZE_1H -> {
                handleSnoozeAction(context, todoDao, categoryDao, todoId, notificationId, SNOOZE_1_HOUR)
            }
            ACTION_SNOOZE_TOMORROW -> {
                handleSnoozeTomorrow(context, todoDao, categoryDao, todoId, notificationId)
            }
            ACTION_SNOOZE_WEEKEND -> {
                handleSnoozeWeekend(context, todoDao, categoryDao, todoId, notificationId)
            }
        }
    }

    /**
     * 处理"完成"操作
     */
    private fun handleCompleteAction(
        context: Context,
        todoDao: com.corgimemo.app.data.local.db.TodoDao,
        todoId: Long,
        notificationId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.getTodoById(todoId)
                todo?.let {
                    val updatedTodo = createCompletedTodo(it)
                    todoDao.update(updatedTodo)
                }

                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理提醒触发
     */
    private fun handleReminderTrigger(
        context: Context,
        todoDao: com.corgimemo.app.data.local.db.TodoDao,
        categoryDao: com.corgimemo.app.data.local.db.CategoryDao,
        todoId: Long
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.getTodoById(todoId) ?: return@launch

                if (todo.status == 1) return@launch

                val category = categoryDao.getCategoryById(todo.categoryId)

                val pendingTodos = todoDao.getTodosByStatus(0).first()
                val upcomingTodos = pendingTodos.filter { it.reminderTime != null }

                NotificationHelper.notifyTodo(
                    context = context,
                    todo = todo,
                    categoryName = category?.name,
                    allTodos = upcomingTodos
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理"稍后提醒"操作
     */
    private fun handleSnoozeAction(
        context: Context,
        todoDao: com.corgimemo.app.data.local.db.TodoDao,
        categoryDao: com.corgimemo.app.data.local.db.CategoryDao,
        todoId: Long,
        notificationId: Int,
        delayMillis: Long
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.getTodoById(todoId)
                todo?.let {
                    val updatedTodo = createSnoozedTodo(it, delayMillis)
                    todoDao.update(updatedTodo)
                    AlarmScheduler.rescheduleReminder(context, updatedTodo)
                }

                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理"推迟到明天"操作
     */
    private fun handleSnoozeTomorrow(
        context: Context,
        todoDao: com.corgimemo.app.data.local.db.TodoDao,
        categoryDao: com.corgimemo.app.data.local.db.CategoryDao,
        todoId: Long,
        notificationId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.getTodoById(todoId)
                todo?.let {
                    val currentTime = it.reminderTime ?: System.currentTimeMillis()
                    val tomorrowTime = AlarmScheduler.calculateTomorrowSameTime(currentTime)
                    val updatedTodo = it.copy(
                        reminderTime = tomorrowTime,
                        updatedAt = System.currentTimeMillis()
                    )
                    todoDao.update(updatedTodo)
                    AlarmScheduler.rescheduleReminder(context, updatedTodo)
                }

                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 处理"改到周末"操作
     */
    private fun handleSnoozeWeekend(
        context: Context,
        todoDao: com.corgimemo.app.data.local.db.TodoDao,
        categoryDao: com.corgimemo.app.data.local.db.CategoryDao,
        todoId: Long,
        notificationId: Int
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val todo = todoDao.getTodoById(todoId)
                todo?.let {
                    val weekendTime = AlarmScheduler.calculateNextSaturday()
                    val updatedTodo = it.copy(
                        reminderTime = weekendTime,
                        updatedAt = System.currentTimeMillis()
                    )
                    todoDao.update(updatedTodo)
                    AlarmScheduler.rescheduleReminder(context, updatedTodo)
                }

                cancelNotification(context, notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 创建已完成状态的待办对象
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
     */
    private fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.cancel(notificationId)
    }
}
