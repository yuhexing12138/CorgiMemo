package com.corgimemo.app.data.repository

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
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.receiver.ReminderActionReceiver

class GeofenceRepository(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "CorgiMemo_Geofence"
        const val NOTIFICATION_ID = 1001
    }

    fun checkLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "位置提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "到达或离开指定位置时的提醒"
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showGeofenceNotification(todo: TodoItem) {
        val intent = Intent(context, Class.forName("com.corgimemo.app.ui.MainActivity"))
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (todo.geofenceType == 0) "到达位置提醒" else "离开位置提醒"
        val address = todo.geofenceAddress ?: "未知位置"

        val typedValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.icon, typedValue, true)
        val iconResId = typedValue.resourceId

        // 使用 todo.id 作为通知 ID，确保多个待办的通知不会互相覆盖
        val notificationId = todo.id.toInt()

        // 创建"完成"操作按钮的 PendingIntent
        val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_COMPLETE
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val completePendingIntent = PendingIntent.getBroadcast(
            context,
            todo.id.toInt(),
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建"稍后10分钟"操作按钮的 PendingIntent
        val snooze10mIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_10M
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze10mPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id.toInt() + 1),
            snooze10mIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建"稍后1小时"操作按钮的 PendingIntent
        val snooze1hIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE_1H
            putExtra(ReminderActionReceiver.EXTRA_TODO_ID, todo.id)
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val snooze1hPendingIntent = PendingIntent.getBroadcast(
            context,
            (todo.id.toInt() + 2),
            snooze1hIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建通知
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (iconResId != 0) iconResId else android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("${todo.title} - ${address}")
            // 使用 BigTextStyle 展开显示完整内容
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${todo.title}\n${address}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            // Android 12+ 适配：使用 CATEGORY_REMINDER
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // 添加"完成"操作按钮
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_myplaces,
                    "完成",
                    completePendingIntent
                )
                    .build()
            )
            // 添加"稍后10分钟"操作按钮
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_recent_history,
                    "稍后10分钟",
                    snooze10mPendingIntent
                )
                    .build()
            )
            // 添加"稍后1小时"操作按钮
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_rotate,
                    "稍后1小时",
                    snooze1hPendingIntent
                )
                    .build()
            )

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(notificationId, builder.build())
            }
        }
    }

    fun calculateDistance(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Float {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    fun isInsideGeofence(
        currentLat: Double,
        currentLng: Double,
        geofenceLat: Double,
        geofenceLng: Double,
        radius: Float
    ): Boolean {
        val distance = calculateDistance(currentLat, currentLng, geofenceLat, geofenceLng)
        return distance <= radius
    }
}
