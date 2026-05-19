package com.corgimemo.app.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 2x2 今日待办预览小部件
 * 显示 3-5 条今日待办，支持点击跳转和快捷完成
 */
class TodayPreviewWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        /**
         * 更新所有此小部件的实例
         */
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodayPreviewWidget::class.java)
            glanceIds.forEach { glanceId ->
                TodayPreviewWidget().update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val todos = try {
            WidgetDataManager.getTodayTodos(context, 5)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        provideContent {
            GlanceTheme(colors = GlanceTheme.colors) {
                TodayPreviewContent(context, todos)
            }
        }
    }

    @Composable
    private fun TodayPreviewContent(
        context: Context,
        todos: List<WidgetDataManager.WidgetTodoItem>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFFAFAFA)))
                .padding(12.dp)
        ) {
            HeaderRow(context)

            Spacer(modifier = GlanceModifier.height(8.dp))

            if (todos.isEmpty()) {
                EmptyState()
            } else {
                TodoList(context, todos, 5)
            }
        }
    }

    @Composable
    private fun HeaderRow(context: Context) {
        val homeIntent = WidgetNavigationHelper.getHomeIntent(context)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .clickable(actionStartActivity(homeIntent)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📝",
                style = TextStyle(fontSize = 18.sp)
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "柯基待办",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(0xFF333333))
                )
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(Color(0xFFFF9800))
                )
            )
        }
    }

    @Composable
    private fun EmptyState() {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center,
            content = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🐕",
                        style = TextStyle(fontSize = 32.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Text(
                        text = "今日没有待办",
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = ColorProvider(Color(0xFF666666))
                        )
                    )
                    Text(
                        text = "享受轻松时光吧！",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(Color(0xFF999999))
                        )
                    )
                }
            }
        )
    }

    @Composable
    private fun TodoList(
        context: Context,
        todos: List<WidgetDataManager.WidgetTodoItem>,
        maxItems: Int
    ) {
        val displayTodos = if (todos.size > maxItems) {
            todos.take(maxItems)
        } else {
            todos
        }

        Column(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            displayTodos.forEachIndexed { index, todo ->
                TodoItemRow(context, todo)
                if (index < displayTodos.size - 1) {
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun TodoItemRow(
        context: Context,
        todo: WidgetDataManager.WidgetTodoItem
    ) {
        val editIntent = WidgetNavigationHelper.getEditIntent(context, todo.id)
        
        val completeIntent = Intent(WidgetActionReceiver.ACTION_COMPLETE_TODO).apply {
            setPackage(context.packageName)
            putExtra(WidgetActionReceiver.EXTRA_TODO_ID, todo.id)
        }
        
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(0xFFFFFFFF)))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(24.dp)
                    .background(todo.priorityColor),
                content = {}
            )

            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(editIntent))
            ) {
                Text(
                    text = todo.title,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color(0xFF333333))
                    ),
                    maxLines = 1
                )
                if (todo.dueTimeText.isNotEmpty()) {
                    Text(
                        text = todo.dueTimeText,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(Color(0xFF999999))
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(8.dp))

            CompleteButton(completeIntent)
        }
    }

    @Composable
    private fun CompleteButton(completeIntent: Intent) {
        Box(
            modifier = GlanceModifier
                .width(28.dp)
                .height(28.dp)
                .background(ColorProvider(Color(0xFFE8F5E9)))
                .clickable(actionSendBroadcast(completeIntent)),
            contentAlignment = Alignment.Center,
            content = {
                Text(
                    text = "✓",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF4CAF50))
                    )
                )
            }
        )
    }
}
