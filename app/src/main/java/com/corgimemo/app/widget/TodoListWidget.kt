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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
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
 * 4x2 完整待办列表小部件
 * 显示所有今日待办，支持滚动查看
 */
class TodoListWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    companion object {
        /**
         * 更新所有此小部件的实例
         */
        suspend fun updateAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(TodoListWidget::class.java)
            glanceIds.forEach { glanceId ->
                TodoListWidget().update(context, glanceId)
            }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val todos = try {
            WidgetDataManager.getTodayTodos(context, -1)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }

        provideContent {
            GlanceTheme(colors = GlanceTheme.colors) {
                TodoListContent(context, todos)
            }
        }
    }

    @Composable
    private fun TodoListContent(
        context: Context,
        todos: List<WidgetDataManager.WidgetTodoItem>
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(Color(0xFFFAFAFA)))
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(12.dp, 12.dp, 12.dp, 8.dp),
                content = {
                    HeaderRow(context)
                }
            )

            if (todos.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                    content = {
                        EmptyState()
                    }
                )
            } else {
                LazyColumn(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .padding(12.dp, 0.dp, 12.dp, 12.dp)
                ) {
                    items(todos) { todo ->
                        TodoItemRow(context, todo)
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
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
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .width(6.dp)
                    .height(28.dp)
                    .background(todo.priorityColor),
                content = {}
            )

            Spacer(modifier = GlanceModifier.width(10.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(actionStartActivity(editIntent))
            ) {
                Text(
                    text = todo.title,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = ColorProvider(Color(0xFF333333))
                    ),
                    maxLines = 1
                )
                val subtitle = buildString {
                    if (todo.dueTimeText.isNotEmpty()) {
                        append(todo.dueTimeText)
                    }
                    if (todo.categoryName != null) {
                        if (isNotEmpty()) append(" · ")
                        append(todo.categoryName)
                    }
                }
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = ColorProvider(Color(0xFF999999))
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            CompleteButton(completeIntent)
        }
    }

    @Composable
    private fun CompleteButton(completeIntent: Intent) {
        Box(
            modifier = GlanceModifier
                .width(32.dp)
                .height(32.dp)
                .background(ColorProvider(Color(0xFFE8F5E9)))
                .clickable(actionSendBroadcast(completeIntent)),
            contentAlignment = Alignment.Center,
            content = {
                Text(
                    text = "✓",
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(Color(0xFF4CAF50))
                    )
                )
            }
        )
    }
}
