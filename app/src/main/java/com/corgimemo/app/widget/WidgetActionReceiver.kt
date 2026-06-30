package com.corgimemo.app.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * 小部件操作广播接收器
 * 处理小部件上的点击操作（如完成待办）
 */
class WidgetActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_COMPLETE_TODO = "com.corgimemo.app.ACTION_COMPLETE_TODO"
        const val EXTRA_TODO_ID = "extra_todo_id"

        /**
         * 获取标记完成待办的 PendingIntent
         *
         * @param context 上下文
         * @param todoId 待办 ID
         * @return PendingIntent 对象
         */
        fun getCompletePendingIntent(context: Context, todoId: Long): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                action = ACTION_COMPLETE_TODO
                putExtra(EXTRA_TODO_ID, todoId)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_IMMUTABLE
                    } else {
                        0
                    }
            return PendingIntent.getBroadcast(
                context,
                todoId.toInt(),
                intent,
                flags
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1)
        if (todoId == -1L) return

        when (intent.action) {
            ACTION_COMPLETE_TODO -> handleCompleteTodo(context, todoId)
        }
    }

    /**
     * 处理标记完成待办操作
     *
     * @param context 上下文
     * @param todoId 待办 ID
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun handleCompleteTodo(context: Context, todoId: Long) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val database = CorgiMemoDatabase.getDatabase(context)
                val todoDao = database.todoDao()

                val todo = todoDao.getTodoById(todoId)
                if (todo != null) {
                    val updatedTodo = todo.copy(
                        status = 1,
                        completedAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    todoDao.update(updatedTodo)
                }

                WidgetUpdateReceiver.sendRefreshBroadcast(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
