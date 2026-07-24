package com.corgimemo.app.data.repository

import android.content.Context
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.notification.AlarmScheduler
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 重复类型常量
 */
object RepeatType {
    /** 不重复 */
    const val NONE = 0
    /** 每天重复 */
    const val DAILY = 1
    /** 每周重复 */
    const val WEEKLY = 2
    /** 每月重复 */
    const val MONTHLY = 3
    /** 工作日（周一到周五） */
    const val WEEKDAYS = 4
    /** 每年同月同日 */
    const val YEARLY = 5
    /** 周末（周六、周日）— UI 当前未提供此选项，保留常量以备扩展 */
    const val WEEKENDS = 6
}

/**
 * 重复任务管理器
 * 处理重复任务的创建、调度和完成逻辑
 */
object RepeatTaskManager {

    /**
     * 获取重复类型的显示名称
     *
     * @param repeatType 重复类型
     * @return 显示名称
     */
    fun getRepeatTypeName(repeatType: Int): String {
        return when (repeatType) {
            RepeatType.DAILY -> "每天"
            RepeatType.WEEKLY -> "每周"
            RepeatType.MONTHLY -> "每月"
            RepeatType.WEEKDAYS -> "周一至周五"
            RepeatType.YEARLY -> "每年"
            RepeatType.WEEKENDS -> "周六至周日"
            else -> "不重复"
        }
    }

    /**
     * 获取所有可用的重复类型列表
     *
     * @return 重复类型列表（类型ID, 显示名称）
     */
    fun getRepeatTypeOptions(): List<Pair<Int, String>> {
        return listOf(
            RepeatType.NONE to "不重复",
            RepeatType.DAILY to "每天",
            RepeatType.WEEKLY to "每周",
            RepeatType.MONTHLY to "每月",
            RepeatType.WEEKDAYS to "工作日",
            RepeatType.YEARLY to "每年",
            RepeatType.WEEKENDS to "周末"
        )
    }

    /**
     * 计算下一个重复日期
     *
     * @param currentTime 当前日期时间戳
     * @param repeatType 重复类型
     * @return 下一个日期的时间戳，如果不重复返回 null
     */
    fun calculateNextRepeatTime(currentTime: Long, repeatType: Int): Long? {
        if (repeatType == RepeatType.NONE) return null

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTime

        when (repeatType) {
            RepeatType.DAILY -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
            RepeatType.WEEKLY -> {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
            RepeatType.MONTHLY -> {
                calendar.add(Calendar.MONTH, 1)
            }
            RepeatType.YEARLY -> {
                // 每年同月同日同时分重复（如生日、纪念日）
                // 使用 Calendar.YEAR + 1，可自动处理闰年 2/29 → 2/28 的回退（Kotlin/Java Calendar 行为）
                calendar.add(Calendar.YEAR, 1)
            }
            RepeatType.WEEKDAYS -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                while (isWeekend(calendar)) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            RepeatType.WEEKENDS -> {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                while (!isWeekend(calendar)) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            else -> return null
        }

        return calendar.timeInMillis
    }

    /**
     * 判断是否是周末
     */
    private fun isWeekend(calendar: Calendar): Boolean {
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    /**
     * 处理重复任务完成
     * 当重复任务完成时，创建下一个周期的新任务
     *
     * @param context 上下文
     * @param completedTodo 已完成的重复任务
     * @return 创建的新任务，如果不重复返回 null
     */
    suspend fun handleRepeatTaskCompletion(
        context: Context,
        completedTodo: TodoItem
    ): TodoItem? {
        if (completedTodo.repeatType == RepeatType.NONE) return null

        val database = CorgiMemoDatabase.getDatabase(context)
        val currentTime = System.currentTimeMillis()

        val nextStartDate = completedTodo.startDate?.let {
            calculateNextRepeatTime(it, completedTodo.repeatType)
        }

        val nextReminderTime = completedTodo.reminderTime?.let {
            calculateNextRepeatTime(it, completedTodo.repeatType)
        }

        if (nextStartDate == null && nextReminderTime == null) {
            return null
        }

        val newTodo = TodoItem(
            title = completedTodo.title,
            content = completedTodo.content,
            categoryId = completedTodo.categoryId,
            priority = completedTodo.priority,
            status = 0,
            startDate = nextStartDate,
            estimatedDurationMinutes = completedTodo.estimatedDurationMinutes,
            reminderTime = nextReminderTime,
            repeatType = completedTodo.repeatType,
            createdAt = currentTime,
            updatedAt = currentTime,
            completedAt = null,
            geofenceLat = completedTodo.geofenceLat,
            geofenceLng = completedTodo.geofenceLng,
            geofenceRadius = completedTodo.geofenceRadius,
            geofenceType = completedTodo.geofenceType,
            geofenceEnabled = completedTodo.geofenceEnabled,
            geofenceAddress = completedTodo.geofenceAddress,
            hasSubTasks = completedTodo.hasSubTasks
        )

        val newTodoId = database.todoDao().insert(newTodo)
        val insertedTodo = database.todoDao().getTodoById(newTodoId)

        insertedTodo?.let { todo ->
            if (todo.reminderTime != null) {
                AlarmScheduler.scheduleReminder(context, todo)
            }
        }

        return insertedTodo
    }

    /**
     * 取消重复任务的提醒
     *
     * @param context 上下文
     * @param todoId 待办 ID
     */
    fun cancelRepeatReminder(context: Context, todoId: Long) {
        AlarmScheduler.cancelReminder(context, todoId)
    }

    /**
     * 检查任务是否是重复任务
     *
     * @param repeatType 重复类型
     * @return 是否是重复任务
     */
    fun isRepeatTask(repeatType: Int): Boolean {
        return repeatType != RepeatType.NONE
    }

    /**
     * 格式化重复描述（用于显示）
     *
     * @param repeatType 重复类型
     * @param startDate 开始日期
     * @return 格式化的描述
     */
    fun formatRepeatDescription(repeatType: Int, startDate: Long?): String {
        if (repeatType == RepeatType.NONE) return "不重复"

        val baseName = getRepeatTypeName(repeatType)

        if (startDate == null) return baseName

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startDate

        return when (repeatType) {
            RepeatType.WEEKLY -> {
                val dayOfWeek = getDayOfWeekName(calendar.get(Calendar.DAY_OF_WEEK))
                "$baseName（每$dayOfWeek）"
            }
            RepeatType.MONTHLY -> {
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                "$baseName（每月${dayOfMonth}日）"
            }
            RepeatType.YEARLY -> {
                // 每年同月同日重复（如生日），显示「每年（月X日）」
                val month = calendar.get(Calendar.MONTH) + 1
                val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
                "$baseName（${month}月${dayOfMonth}日）"
            }
            else -> baseName
        }
    }

    /**
     * 获取星期几的中文名称
     */
    private fun getDayOfWeekName(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }

    /**
     * 获取重复任务的下一个触发时间描述
     */
    fun getNextOccurrenceDescription(repeatType: Int, startDate: Long?): String? {
        if (repeatType == RepeatType.NONE || startDate == null) return null

        val nextTime = calculateNextRepeatTime(startDate, repeatType) ?: return null

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nextTime

        val now = Calendar.getInstance()
        val diffDays = TimeUnit.MILLISECONDS.toDays(
            nextTime - now.timeInMillis
        )

        return when {
            diffDays == 0L -> "今天"
            diffDays == 1L -> "明天"
            diffDays < 7L -> "${diffDays}天后"
            else -> {
                val month = calendar.get(Calendar.MONTH) + 1
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                "${month}月${day}日"
            }
        }
    }
}
