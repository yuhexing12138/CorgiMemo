package com.corgimemo.app.animation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * 节日通知管理器
 * 负责创建节日提醒通知
 */
object HolidayNotificationManager {

    const val CHANNEL_ID = "CorgiMemo_Holiday"
    const val NOTIFICATION_ID_BASE = 2000

    /**
     * 创建节日通知渠道
     * Android 8.0+ 需要先创建通知渠道才能发送通知
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "节日提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "节日当天推送问候和提醒"
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 发送节日通知
     *
     * @param context 上下文
     * @param holiday 节日对象
     * @param corgiName 柯基名字（可选）
     * @param notificationId 通知 ID（默认使用节日名称的 hashCode）
     */
    fun sendHolidayNotification(
        context: Context,
        holiday: Holiday,
        corgiName: String? = null,
        notificationId: Int = NOTIFICATION_ID_BASE + holiday.name.hashCode()
    ) {
        // 检查通知权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        createNotificationChannel(context)

        // 创建点击通知的 PendingIntent（跳转到主页面）
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

        // 获取应用图标
        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.icon, typedValue, true)
        val iconResId = typedValue.resourceId

        // 获取节日问候语
        val greeting = holiday.getRandomGreeting(corgiName)

        // 构建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (iconResId != 0) iconResId else android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 ${holiday.displayName}快乐！")
            .setContentText(greeting)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(greeting)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)

        pendingIntent?.let {
            builder.setContentIntent(it)
        }

        with(NotificationManagerCompat.from(context)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, builder.build())
            }
        }
    }

    /**
     * 检查当前是否有节日，如果有则发送节日通知
     *
     * @param context 上下文
     * @param corgiName 柯基名字（可选）
     * @param currentTime 当前时间戳
     * @return 发送的通知的节日对象，如果不是节日返回 null
     */
    fun checkAndSendHolidayNotification(
        context: Context,
        corgiName: String? = null,
        currentTime: Long = System.currentTimeMillis()
    ): Holiday? {
        val holiday = HolidayManager.getCurrentHoliday(currentTime)
        if (holiday != null) {
            sendHolidayNotification(context, holiday, corgiName)
        }
        return holiday
    }

    /**
     * 获取节日名称的 emoji
     *
     * @param holiday 节日对象
     * @return 对应的 emoji
     */
    fun getHolidayEmoji(holiday: Holiday): String {
        return when (holiday.id) {
            HolidayId.NEW_YEAR -> "🎊"
            HolidayId.SPRING_FESTIVAL -> "🧧"
            HolidayId.LANTERN -> "🏮"
            HolidayId.LABOR -> "👷"
            HolidayId.DRAGON_BOAT -> "🐲"
            HolidayId.NATIONAL -> "🇨🇳"
            HolidayId.MID_AUTUMN -> "🌕"
            HolidayId.WINTER_SOLSTICE -> "❄️"
            HolidayId.CHRISTMAS -> "🎄"
            else -> "🎉"
        }
    }
}
