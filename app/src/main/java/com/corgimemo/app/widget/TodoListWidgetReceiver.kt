package com.corgimemo.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 4x2 完整待办列表小部件的 Receiver
 * 接收系统广播并将事件转发给 TodoListWidget
 */
class TodoListWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodoListWidget()
}
