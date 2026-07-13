package com.corgimemo.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.notification.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启广播接收器
 * 监听系统开机完成事件（BOOT_COMPLETED），
 * 重新注册所有未完成且未过期的待办提醒闹钟。
 *
 * 使用场景：
 * - 用户重启手机后，之前设置的提醒闹钟会被系统清除
 * - BootReceiver 在开机后自动恢复这些闹钟
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            /** 在 IO 线程中执行数据库查询和闹钟恢复操作 */
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    restorePendingReminders(context)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 恢复所有待处理的提醒闹钟
     * 查询数据库中满足以下条件的待办：
     * 1. status == 0（未完成）
     * 2. reminderTime 不为空
     * 3. reminderTime > 当前时间（未过期）
     *
     * @param context 应用上下文
     */
    private suspend fun restorePendingReminders(context: Context) {
        val database = CorgiMemoDatabase.getDatabase(context)
        val todoDao = database.todoDao()

        /** 获取所有未完成的待办（getTodosByStatus 返回 Flow，需先收集） */
        val pendingTodos = todoDao.getTodosByStatus(0).first()

        /** 过滤出有提醒时间且未过期的待办，逐一恢复闹钟 */
        val validReminders = pendingTodos.filter { todo ->
            todo.reminderTime != null && todo.reminderTime > System.currentTimeMillis()
        }

        if (validReminders.isNotEmpty()) {
            AlarmScheduler.restoreAllReminders(context, validReminders)
        }
    }
}
