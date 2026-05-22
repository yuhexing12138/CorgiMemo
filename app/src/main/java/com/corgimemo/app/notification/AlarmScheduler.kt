package com.corgimemo.app.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.receiver.ReminderActionReceiver

/**
 * 提醒调度器
 * 使用 AlarmManager 设置和取消待办提醒闹钟
 */
object AlarmScheduler {

    /** 提醒广播 Action */
    private const val ACTION_REMINDER = "com.corgimemo.app.ACTION_REMINDER"

    /**
     * 设置提醒闹钟
     * 直接使用 reminderTime 作为触发时间
     *
     * @param context 上下文
     * @param todo 待办项
     */
    fun scheduleReminder(
        context: Context,
        todo: TodoItem
    ) {
        val triggerTime = todo.reminderTime ?: return

        if (triggerTime <= System.currentTimeMillis()) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createReminderPendingIntent(context, todo)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasExactAlarmPermission(context)) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消提醒闹钟
     *
     * @param context 上下文
     * @param todoId 待办 ID
     */
    fun cancelReminder(context: Context, todoId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createReminderPendingIntent(context, todoId)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 重新设置提醒
     * 用于推迟提醒后重新设置闹钟
     *
     * @param context 上下文
     * @param todo 更新后的待办项
     */
    fun rescheduleReminder(
        context: Context,
        todo: TodoItem
    ) {
        cancelReminder(context, todo.id)
        scheduleReminder(context, todo)
    }

    /**
     * 创建提醒广播的 PendingIntent
     *
     * @param context 上下文
     * @param todo 待办项
     * @return PendingIntent
     */
    private fun createReminderPendingIntent(context: Context, todo: TodoItem): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, todo.id.toInt())
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            todo.id.toInt(),
            intent,
            flags
        )
    }

    /**
     * 创建提醒广播的 PendingIntent（仅使用 todoId）
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return PendingIntent
     */
    private fun createReminderPendingIntent(context: Context, todoId: Long): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todoId)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, todoId.toInt())
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            intent,
            flags
        )
    }

    /**
     * 计算推迟到明天的时间
     * 保持相同的时分秒
     *
     * @param currentTime 当前时间戳
     * @return 明天同一时间的时间戳
     */
    fun calculateTomorrowSameTime(currentTime: Long): Long {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = currentTime
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    /**
     * 计算本周六 9:00 的时间
     * 如果今天是周六或周日，则推迟到下周六
     *
     * @return 目标时间戳
     */
    fun calculateNextSaturday(): Long {
        val cal = java.util.Calendar.getInstance()
        val today = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val daysToAdd = when (today) {
            java.util.Calendar.SATURDAY -> 7
            java.util.Calendar.SUNDAY -> 6
            else -> java.util.Calendar.SATURDAY - today
        }

        cal.add(java.util.Calendar.DAY_OF_WEEK, daysToAdd)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        return cal.timeInMillis
    }

    /**
     * 检查是否有精确闹钟权限
     * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限
     *
     * @param context 上下文
     * @return 是否有精确闹钟权限
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * 检查提醒是否已设置
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return 是否已设置
     */
    fun isReminderScheduled(context: Context, todoId: Long): Boolean {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todoId)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }

        return PendingIntent.getBroadcast(
            context,
            todoId.toInt(),
            intent,
            flags
        ) != null
    }

    /**
     * 恢复所有未过期的提醒闹钟
     * 应用启动时调用，重新设置所有待办的提醒
     *
     * @param context 上下文
     * @param todos 待办列表
     */
    fun restoreAllReminders(context: Context, todos: List<TodoItem>) {
        val currentTime = System.currentTimeMillis()
        todos.filter {
            it.status == 0 &&
            it.reminderTime != null &&
            it.reminderTime > currentTime
        }.forEach {
            scheduleReminder(context, it)
        }
    }
}
