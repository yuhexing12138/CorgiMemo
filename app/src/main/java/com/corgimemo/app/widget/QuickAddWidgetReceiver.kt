package com.corgimemo.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 1x1 快速添加小部件的 Receiver
 * 接收系统广播并将事件转发给 QuickAddWidget
 */
class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
