package com.corgimemo.app.widget

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 1x1 快速添加小部件
 * 点击后跳转到创建待办页面
 */
class QuickAddWidget : GlanceAppWidget() {

    companion object {
        /**
         * 更新所有此小部件的实例
         */
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(QuickAddWidget::class.java)
            glanceIds.forEach { glanceId ->
                QuickAddWidget().update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme(colors = GlanceTheme.colors) {
                QuickAddContent(context)
            }
        }
    }

    @Composable
    private fun QuickAddContent(context: Context) {
        val createIntent = WidgetNavigationHelper.getCreateIntent(context)
        
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFFF9800)))
                .clickable(actionStartActivity(createIntent))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🐕",
                    style = TextStyle(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "+",
                    style = TextStyle(
                        fontSize = 28.sp,
                        color = ColorProvider(Color.White),
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "新建待办",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color.White)
                    )
                )
            }
        }
    }
}
