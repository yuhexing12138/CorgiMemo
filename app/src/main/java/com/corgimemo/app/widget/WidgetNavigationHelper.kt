package com.corgimemo.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.corgimemo.app.ui.MainActivity

/**
 * 小部件导航辅助类
 * 用于创建带有导航参数的 PendingIntent
 */
object WidgetNavigationHelper {

    const val EXTRA_WIDGET_NAVIGATE = "navigate_to"
    const val NAVIGATE_HOME = "home"
    const val NAVIGATE_CREATE = "create_todo"
    const val NAVIGATE_EDIT = "edit_todo"
    const val EXTRA_TODO_ID = "extra_todo_id"

    /**
     * 获取跳转到首页的 Intent
     */
    fun getHomeIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_NAVIGATE, NAVIGATE_HOME)
        }
    }

    /**
     * 获取跳转到创建待办页面的 Intent
     */
    fun getCreateIntent(context: Context): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_WIDGET_NAVIGATE, NAVIGATE_CREATE)
        }
    }

    /**
     * 获取跳转到编辑待办页面的 Intent
     */
    fun getEditIntent(context: Context, todoId: Long): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_WIDGET_NAVIGATE, NAVIGATE_EDIT)
            putExtra(EXTRA_TODO_ID, todoId)
        }
    }

    /**
     * 获取跳转到首页的 PendingIntent
     */
    fun getHomePendingIntent(context: Context): PendingIntent {
        val intent = getHomeIntent(context)
        return createPendingIntent(context, 1000, intent)
    }

    /**
     * 获取跳转到创建待办页面的 PendingIntent
     */
    fun getCreatePendingIntent(context: Context): PendingIntent {
        val intent = getCreateIntent(context)
        return createPendingIntent(context, 1001, intent)
    }

    /**
     * 获取跳转到编辑待办页面的 PendingIntent
     */
    fun getEditPendingIntent(context: Context, todoId: Long): PendingIntent {
        val intent = getEditIntent(context, todoId)
        return createPendingIntent(context, (1002 + todoId).toInt(), intent)
    }

    private fun createPendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }
}
