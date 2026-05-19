package com.corgimemo.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 小部件刷新广播接收器
 * 用于在应用内待办数据变化时立即刷新所有小部件
 */
class WidgetUpdateReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REFRESH = "com.corgimemo.app.ACTION_REFRESH_WIDGET"

        /**
         * 发送刷新广播
         * 在应用内待办数据变化时调用此方法
         */
        fun sendRefreshBroadcast(context: Context) {
            val intent = Intent(ACTION_REFRESH).apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            CoroutineScope(Dispatchers.IO).launch {
                refreshAllWidgets(context)
            }
        }
    }

    private suspend fun refreshAllWidgets(context: Context) {
        try {
            QuickAddWidget.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            TodayPreviewWidget.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            TodoListWidget.updateAll(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        triggerSystemUpdate(context)
    }

    /**
     * 触发系统层面的小部件更新
     * 使用传统的 AppWidgetManager 方式通知系统刷新小部件
     */
    private fun triggerSystemUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val widgetClasses = listOf(
            QuickAddWidgetReceiver::class.java,
            TodayPreviewWidgetReceiver::class.java,
            TodoListWidgetReceiver::class.java
        )

        widgetClasses.forEach { widgetClass ->
            try {
                val componentName = ComponentName(context, widgetClass)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, widgetClass).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
