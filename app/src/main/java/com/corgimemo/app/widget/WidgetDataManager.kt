package com.corgimemo.app.widget

import android.content.Context
import androidx.glance.unit.ColorProvider
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 小部件数据管理类
 * 负责从小部件获取和格式化待办数据
 */
object WidgetDataManager {

    /**
     * 优先级颜色配置
     * 与应用内优先级颜色保持一致
     */
    object PriorityColors {
        val High = ColorProvider(0xFFF44336.toInt())
        val Medium = ColorProvider(0xFFFF9800.toInt())
        val Low = ColorProvider(0xFF2196F3.toInt())
    }

    /**
     * 小部件待办数据模型
     * 专门用于小部件显示的简化版待办数据
     */
    data class WidgetTodoItem(
        val id: Long,
        val title: String,
        val dueTime: Long?,
        val dueTimeText: String,
        val priority: Int,
        val priorityColor: ColorProvider,
        val categoryName: String?
    )

    /**
     * 获取今日待办列表
     * 只返回未完成的待办，按优先级和截止时间排序
     *
     * @param context 上下文
     * @param maxCount 最大返回数量（-1 表示不限制）
     * @return 格式化后的待办列表
     */
    suspend fun getTodayTodos(context: Context, maxCount: Int = -1): List<WidgetTodoItem> = withContext(Dispatchers.IO) {
        try {
            val database = CorgiMemoDatabase.getDatabase(context)
            val todoDao = database.todoDao()
            val categoryDao = database.categoryDao()

            val todayStart = getTodayStart()
            val todayEnd = getTodayEnd()

            val allPendingTodos = todoDao.getTodosByStatus(0).first()

            val todayTodos = allPendingTodos.filter { todo ->
                val isDueToday = todo.dueDate?.let { dueDate ->
                    dueDate in todayStart..todayEnd
                } ?: false
                
                val isReminderToday = todo.reminderTime?.let { reminderTime ->
                    reminderTime in todayStart..todayEnd
                } ?: false
                
                isDueToday || isReminderToday
            }.sortedWith(
                compareByDescending<TodoItem> { it.priority }
                    .thenBy { it.dueDate ?: it.reminderTime ?: Long.MAX_VALUE }
            )

            val categoryMap = mutableMapOf<Long, String>()
            val categories = categoryDao.getAllCategories().first()
            categories.forEach { category ->
                categoryMap[category.id] = category.name
            }

            val widgetItems = todayTodos.map { todo ->
                WidgetTodoItem(
                    id = todo.id,
                    title = todo.title,
                    dueTime = todo.dueDate ?: todo.reminderTime,
                    dueTimeText = formatDueTime(todo.dueDate ?: todo.reminderTime),
                    priority = todo.priority,
                    priorityColor = getPriorityColor(todo.priority),
                    categoryName = todo.categoryId?.let { categoryMap[it] }
                )
            }

            if (maxCount > 0 && widgetItems.size > maxCount) {
                widgetItems.take(maxCount)
            } else {
                widgetItems
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 格式化截止时间显示文本
     *
     * @param dueTime 截止时间戳（毫秒）
     * @return 格式化后的文本
     */
    fun formatDueTime(dueTime: Long?): String {
        if (dueTime == null) {
            return ""
        }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = dueTime

        val today = Calendar.getInstance()
        val tomorrow = Calendar.getInstance()
        tomorrow.add(Calendar.DAY_OF_YEAR, 1)

        val isToday = isSameDay(calendar, today)
        val isTomorrow = isSameDay(calendar, tomorrow)

        return when {
            isToday -> {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                timeFormat.format(dueTime)
            }
            isTomorrow -> {
                "明天"
            }
            else -> {
                val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
                dateFormat.format(dueTime)
            }
        }
    }

    /**
     * 根据优先级获取对应的颜色
     *
     * @param priority 优先级（1=低, 2=中, 3=高）
     * @return ColorProvider 对象
     */
    fun getPriorityColor(priority: Int): ColorProvider {
        return when (priority) {
            3 -> PriorityColors.High
            2 -> PriorityColors.Medium
            1 -> PriorityColors.Low
            else -> PriorityColors.Low
        }
    }

    /**
     * 根据分类 ID 获取分类默认提醒提前量（分钟）
     *
     * @param categoryId 分类 ID
     * @param context 上下文
     * @return 提前分钟数
     */
    suspend fun getDefaultAdvanceMinutes(categoryId: Long?, context: Context): Int {
        if (categoryId == null) {
            return 30
        }

        val database = CorgiMemoDatabase.getDatabase(context)
        val categoryDao = database.categoryDao()
        val category = categoryDao.getCategoryById(categoryId)

        return when (category?.type) {
            CategoryType.WORK -> 30
            CategoryType.LIFE -> 60
            CategoryType.STUDY -> 120
            else -> 30
        }
    }

    /**
     * 获取今天开始的时间戳（00:00:00）
     */
    private fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 获取今天结束的时间戳（23:59:59）
     */
    private fun getTodayEnd(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /**
     * 判断两个 Calendar 是否为同一天
     */
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}
