package com.corgimemo.app.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.receiver.ReminderActionReceiver
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * 通知渠道 ID 常量
 */
object NotificationChannels {
    /** 重要提醒（高优先级待办，priority=3） */
    const val TODO_IMPORTANT = "todo_important"

    /** 一般提醒（中优先级待办，priority=2） */
    const val TODO_NORMAL = "todo_normal"

    /** 次要提醒（低优先级待办，priority=1） */
    const val TODO_LOW = "todo_low"

    /** 备份状态通知 */
    const val BACKUP_STATUS = "backup_status"
}

/**
 * 通知分组 ID 常量
 */
object NotificationGroups {
    /** 待办提醒分组 */
    const val TODO_REMINDERS = "group_todo_reminders"
}

/**
 * 通知 ID 常量
 */
object NotificationIds {
    /** 聚合通知（总结通知）的固定 ID */
    const val SUMMARY_ID = 9999
}

/**
 * 通知管理器
 * 负责创建通知渠道、构建通知、处理通知聚合逻辑
 */
object NotificationHelper {

    /** 通知追踪 SharedPreferences 名称 */
    private const val PREF_NOTIFICATION_TRACKER = "notification_tracker"

    /** 小时开始时间戳的 Key */
    private const val KEY_HOUR_START = "hour_start"

    /** 通知计数的 Key */
    private const val KEY_COUNT = "count"

    /** 待办 ID 集合的 Key */
    private const val KEY_TODO_IDS = "todo_ids"

    /** 通知聚合阈值（同一小时内达到此数量时聚合） */
    private const val GROUP_THRESHOLD = 3

    /**
     * 创建所有通知渠道
     * 在 Application.onCreate 中调用
     *
     * @param context 上下文
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                createImportantChannel(context),
                createNormalChannel(context),
                createLowChannel(context),
                createBackupStatusChannel(context)
            )
            val notificationManager = context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager
            notificationManager.createNotificationChannels(channels)
        }
    }

    /**
     * 创建重要提醒渠道
     * - IMPORTANCE_HIGH：弹出横幅通知
     * - 长震动模式
     * - 自定义提示音
     * - 显示角标
     *
     * @param context 上下文
     * @return NotificationChannel 对象
     */
    private fun createImportantChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            NotificationChannels.TODO_IMPORTANT,
            "重要提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "高优先级待办的提醒，会弹出横幅通知"
            enableLights(true)
            enableVibration(true)
            setShowBadge(true)

            vibrationPattern = longArrayOf(0, 500, 200, 500)

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(defaultSoundUri, audioAttributes)
        }
        return channel
    }

    /**
     * 创建一般提醒渠道
     * - IMPORTANCE_DEFAULT：在通知栏显示
     * - 短震动模式
     * - 系统默认音
     *
     * @param context 上下文
     * @return NotificationChannel 对象
     */
    private fun createNormalChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            NotificationChannels.TODO_NORMAL,
            "一般提醒",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "中优先级待办的提醒"
            enableLights(true)
            enableVibration(true)

            vibrationPattern = longArrayOf(0, 200)

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            setSound(defaultSoundUri, audioAttributes)
        }
        return channel
    }

    /**
     * 创建次要提醒渠道
     * - IMPORTANCE_LOW：仅在通知栏显示，不弹出
     * - 无震动
     * - 无声音
     *
     * @param context 上下文
     * @return NotificationChannel 对象
     */
    private fun createLowChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            NotificationChannels.TODO_LOW,
            "次要提醒",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "低优先级待办的提醒，安静不打扰"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        return channel
    }

    /**
     * 创建备份状态通知渠道
     * - IMPORTANCE_LOW：仅在通知栏显示
     * - 无声音
     * - 无震动
     *
     * @param context 上下文
     * @return NotificationChannel 对象
     */
    private fun createBackupStatusChannel(context: Context): NotificationChannel {
        val channel = NotificationChannel(
            NotificationChannels.BACKUP_STATUS,
            "备份状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "自动备份完成或失败时的通知"
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        return channel
    }

    /**
     * 根据待办优先级获取对应的通知渠道 ID
     *
     * @param priority 待办优先级（1-3）
     * @return 通知渠道 ID
     */
    fun getChannelIdForPriority(priority: Int): String {
        return when (priority) {
            3 -> NotificationChannels.TODO_IMPORTANT
            2 -> NotificationChannels.TODO_NORMAL
            else -> NotificationChannels.TODO_LOW
        }
    }

    /**
     * 根据优先级获取优先级名称
     *
     * @param priority 待办优先级（1-3）
     * @return 优先级名称
     */
    fun getPriorityName(priority: Int): String {
        return when (priority) {
            3 -> "高优先级"
            2 -> "中优先级"
            1 -> "低优先级"
            else -> "普通"
        }
    }

    /**
     * 获取应用图标资源 ID
     *
     * @param context 上下文
     * @return 图标资源 ID
     */
    private fun getAppIconResId(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.icon, typedValue, true)
        return if (typedValue.resourceId != 0) typedValue.resourceId else android.R.drawable.ic_dialog_info
    }

    /**
     * 构建单条待办通知
     *
     * @param context 上下文
     * @param todo 待办项
     * @param categoryName 分类名称（可选）
     * @return NotificationCompat.Builder
     */
    fun buildTodoNotification(
        context: Context,
        todo: TodoItem,
        categoryName: String? = null,
        corgiName: String = "柯基"
    ): NotificationCompat.Builder {
        val channelId = getChannelIdForPriority(todo.priority)
        val notificationId = todo.id.toInt()

        val intent = try {
            Intent(context, Class.forName("com.corgimemo.app.ui.MainActivity"))
        } catch (e: ClassNotFoundException) {
            null
        }
        val pendingIntent = intent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val contentText = buildContentText(todo, categoryName)

        /** 使用「[柯基名]提醒你：该做[任务名]啦！」格式作为通知标题 */
        val notificationTitle = "${corgiName}提醒你：该做${todo.title}啦！"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getAppIconResId(context))
            .setContentTitle(notificationTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(contentText)
            )
            .setPriority(getNotificationPriority(todo.priority))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup(NotificationGroups.TODO_REMINDERS)

        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        addActionButtons(context, builder, todo, notificationId)

        return builder
    }

    /**
     * 构建通知内容文本
     *
     * @param todo 待办项
     * @param categoryName 分类名称
     * @return 内容文本
     */
    private fun buildContentText(todo: TodoItem, categoryName: String?): String {
        val parts = mutableListOf<String>()

        if (categoryName != null) {
            parts.add("[$categoryName]")
        }

        parts.add(getPriorityName(todo.priority))

        if (todo.content != null && todo.content!!.isNotEmpty()) {
            parts.add(todo.content!!)
        }

        return parts.joinToString(" ")
    }

    /**
     * 将待办优先级映射为通知优先级
     *
     * @param priority 待办优先级
     * @return NotificationCompat 优先级常量
     */
    private fun getNotificationPriority(priority: Int): Int {
        return when (priority) {
            3 -> NotificationCompat.PRIORITY_HIGH
            2 -> NotificationCompat.PRIORITY_DEFAULT
            1 -> NotificationCompat.PRIORITY_LOW
            else -> NotificationCompat.PRIORITY_DEFAULT
        }
    }

    /**
     * 为通知添加操作按钮
     * 包括：完成、稍后10分钟、稍后1小时、推迟到明天、改到周末
     *
     * @param context 上下文
     * @param builder NotificationCompat.Builder
     * @param todo 待办项
     * @param notificationId 通知 ID
     */
    private fun addActionButtons(
        context: Context,
        builder: NotificationCompat.Builder,
        todo: TodoItem,
        notificationId: Int
    ) {
        val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_COMPLETE
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id * 10).toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_myplaces,
                "完成",
                completePendingIntent
            ).build()
        )

        val snooze10mIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_10M
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze10mPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id * 10 + 1).toInt(),
            snooze10mIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_recent_history,
                "稍后10分钟",
                snooze10mPendingIntent
            ).build()
        )

        val snooze1hIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_1H
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id * 10 + 2).toInt(),
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_rotate,
                "稍后1小时",
                snooze1hPendingIntent
            ).build()
        )

        val snoozeTomorrowIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_TOMORROW
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozeTomorrowPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id * 10 + 3).toInt(),
            snoozeTomorrowIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_today,
                "推迟到明天",
                snoozeTomorrowPendingIntent
            ).build()
        )

        val snoozeWeekendIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_WEEKEND
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snoozeWeekendPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id * 10 + 4).toInt(),
            snoozeWeekendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_week,
                "改到周末",
                snoozeWeekendPendingIntent
            ).build()
        )
    }

    /**
     * 构建聚合通知（总结通知）
     * 当同一小时内有 3 条以上提醒时使用
     *
     * @param context 上下文
     * @param todos 待办列表
     * @param hourStart 小时开始时间戳
     * @return NotificationCompat.Builder
     */
    fun buildSummaryNotification(
        context: Context,
        todos: List<TodoItem>,
        hourStart: Long
    ): NotificationCompat.Builder {
        val pendingTodos = todos.filter { it.status == 0 }
        val count = pendingTodos.size

        val intent = try {
            Intent(context, Class.forName("com.corgimemo.app.ui.MainActivity"))
        } catch (e: ClassNotFoundException) {
            null
        }
        val pendingIntent = intent?.let {
            it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("你有 $count 个待办即将到期")

        pendingTodos.take(5).forEach { todo ->
            inboxStyle.addLine("• ${todo.title}")
        }

        if (pendingTodos.size > 5) {
            inboxStyle.setSummaryText("还有 ${pendingTodos.size - 5} 个待办...")
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.TODO_IMPORTANT)
            .setSmallIcon(getAppIconResId(context))
            .setContentTitle("你有 $count 个待办即将到期")
            .setContentText("点击查看详情")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setGroup(NotificationGroups.TODO_REMINDERS)
            .setGroupSummary(true)

        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        return builder
    }

    /**
     * 发送待办通知
     * 处理通知聚合逻辑
     *
     * @param context 上下文
     * @param todo 待办项
     * @param categoryName 分类名称（可选）
     * @param allTodos 所有待办列表（用于聚合通知时获取详细信息）
     */
    fun notifyTodo(
        context: Context,
        todo: TodoItem,
        categoryName: String? = null,
        allTodos: List<TodoItem>? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        /** 从数据库获取柯基名称，用于通知标题格式化 */
        val corgiName = getCorgiName(context)

        val notificationManager = NotificationManagerCompat.from(context)
        val notificationId = todo.id.toInt()

        val (shouldGroup, groupedTodoIds) = shouldGroupNotifications(context, todo.id)

        if (shouldGroup) {
            val todosForSummary = allTodos?.filter { it.id in groupedTodoIds } ?: listOf(todo)
            val hourStart = getCurrentHourStart()

            notificationManager.notify(
                NotificationIds.SUMMARY_ID,
                buildSummaryNotification(context, todosForSummary, hourStart).build()
            )
        } else {
            notificationManager.notify(
                notificationId,
                buildTodoNotification(context, todo, categoryName, corgiName).build()
            )
        }
    }

    /**
     * 检查是否需要聚合通知
     *
     * @param context 上下文
     * @param todoId 待办 ID
     * @return Pair<是否需要聚合, 聚合的待办 ID 列表>
     */
    private fun shouldGroupNotifications(
        context: Context,
        todoId: Long
    ): Pair<Boolean, List<Long>> {
        val prefs = context.getSharedPreferences(
            PREF_NOTIFICATION_TRACKER,
            Context.MODE_PRIVATE
        )

        val currentHourStart = getCurrentHourStart()
        val savedHourStart = prefs.getLong(KEY_HOUR_START, 0)
        val count = prefs.getInt(KEY_COUNT, 0)
        val todoIds = prefs.getStringSet(KEY_TODO_IDS, emptySet()) ?: emptySet()

        if (currentHourStart != savedHourStart) {
            prefs.edit()
                .putLong(KEY_HOUR_START, currentHourStart)
                .putInt(KEY_COUNT, 1)
                .putStringSet(KEY_TODO_IDS, setOf(todoId.toString()))
                .apply()
            return Pair(false, listOf(todoId))
        }

        val newCount = count + 1
        val newTodoIds = todoIds + todoId.toString()

        prefs.edit()
            .putInt(KEY_COUNT, newCount)
            .putStringSet(KEY_TODO_IDS, newTodoIds)
            .apply()

        return if (newCount >= GROUP_THRESHOLD) {
            Pair(true, newTodoIds.map { it.toLong() })
        } else {
            Pair(false, listOf(todoId))
        }
    }

    /**
     * 获取当前小时开始的时间戳
     *
     * @return 小时开始的时间戳（毫秒）
     */
    private fun getCurrentHourStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * 取消通知
     *
     * @param context 上下文
     * @param notificationId 通知 ID
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * 取消所有待办提醒通知
     *
     * @param context 上下文
     */
    fun cancelAllTodoNotifications(context: Context) {
        val notificationManager = context.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        notificationManager.cancelAll()
    }

    /**
     * 清除通知追踪数据（用于测试或重置）
     *
     * @param context 上下文
     */
    fun clearNotificationTracker(context: Context) {
        val prefs = context.getSharedPreferences(
            PREF_NOTIFICATION_TRACKER,
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().apply()
    }

    /**
     * 显示备份完成通知
     *
     * @param context 上下文
     * @param success 是否成功
     * @param todoCount 备份的待办数量
     * @param errorMessage 错误信息（失败时）
     */
    fun showBackupNotification(
        context: Context,
        success: Boolean,
        todoCount: Int,
        errorMessage: String?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notificationManager = NotificationManagerCompat.from(context)
        val notificationId = 10000

        val intent = try {
            Intent(context, Class.forName("com.corgimemo.app.ui.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "backup_history")
            }
        } catch (e: ClassNotFoundException) {
            null
        }

        val pendingIntent = intent?.let {
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val title = if (success) "自动备份完成" else "自动备份失败"
        val content = if (success) {
            if (todoCount > 0) "共备份 $todoCount 条待办" else "备份完成"
        } else {
            errorMessage ?: "请检查存储空间"
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.BACKUP_STATUS)
            .setSmallIcon(getAppIconResId(context))
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)

        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        notificationManager.notify(notificationId, builder.build())
    }

    /**
     * 获取柯基名称
     * 从 CorgiData 数据库中查询柯基的名字，
     * 用于通知标题格式化（如"小柯提醒你：该做xxx啦！"）
     *
     * @param context 应用上下文
     * @return 柯基名称，查询失败时返回默认值 "柯基"
     */
    private fun getCorgiName(context: Context): String {
        return try {
            val database = CorgiMemoDatabase.getDatabase(context)
            val corgiDao = database.corgiDao()
            /** 使用 runBlocking 调用 suspend 函数获取柯基数据 */
            val corgiData = runBlocking { corgiDao.getCorgiData() }
            corgiData?.name ?: "柯基"
        } catch (e: Exception) {
            "柯基"
        }
    }
}
