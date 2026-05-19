package com.corgimemo.app.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 2x2 今日待办预览小部件的 Receiver
 * 接收系统广播并将事件转发给 TodayPreviewWidget
 */
class TodayPreviewWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayPreviewWidget()
}
