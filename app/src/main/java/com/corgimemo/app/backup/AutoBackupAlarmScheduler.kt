package com.corgimemo.app.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.corgimemo.app.receiver.AutoBackupReceiver
import java.util.Calendar

/**
 * 自动备份闹钟调度器
 * 使用 AlarmManager 实现精确时间调度：
 * - 每周频率：每周日凌晨 3:00
 * - 每月频率：每月 1 日凌晨 3:00
 */
object AutoBackupAlarmScheduler {

    private const val REQUEST_CODE_WEEKLY = 1001
    private const val REQUEST_CODE_MONTHLY = 1002

    /**
     * 调度自动备份
     *
     * @param context 上下文
     * @param frequency 备份频率
     */
    fun schedule(context: Context, frequency: BackupFrequency) {
        cancel(context)

        val triggerTime = calculateNextTriggerTime(frequency)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = createPendingIntent(context, frequency)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 使用精确闹钟
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                // 如果没有精确闹钟权限，使用不精确闹钟
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    getIntervalMillis(frequency),
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * 取消所有自动备份闹钟
     *
     * @param context 上下文
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val weeklyIntent = createPendingIntent(context, BackupFrequency.WEEKLY)
        val monthlyIntent = createPendingIntent(context, BackupFrequency.MONTHLY)

        alarmManager.cancel(weeklyIntent)
        alarmManager.cancel(monthlyIntent)
    }

    /**
     * 计算下次触发时间
     *
     * @param frequency 备份频率
     * @return 下次触发时间（毫秒）
     */
    private fun calculateNextTriggerTime(frequency: BackupFrequency): Long {
        val now = Calendar.getInstance()

        return when (frequency) {
            BackupFrequency.WEEKLY -> calculateNextSunday3AM(now)
            BackupFrequency.MONTHLY -> calculateNextMonthFirstDay3AM(now)
        }
    }

    /**
     * 计算下周日凌晨 3:00
     *
     * @param now 当前时间
     * @return 目标时间（毫秒）
     */
    private fun calculateNextSunday3AM(now: Calendar): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
        }

        val today = cal.get(Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        // 计算到周日的天数
        var daysToSunday = when {
            today < Calendar.SUNDAY -> Calendar.SUNDAY - today
            today == Calendar.SUNDAY && currentHour < 3 -> 0
            else -> 7  // 今天是周日但已过3点，等下周日
        }

        cal.add(Calendar.DAY_OF_WEEK, daysToSunday)
        cal.set(Calendar.HOUR_OF_DAY, 3)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal.timeInMillis
    }

    /**
     * 计算下月 1 日凌晨 3:00
     *
     * @param now 当前时间
     * @return 目标时间（毫秒）
     */
    private fun calculateNextMonthFirstDay3AM(now: Calendar): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
        }

        val today = cal.get(Calendar.DAY_OF_MONTH)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)

        // 如果今天是 1 号且还没到 3 点，使用今天
        if (today == 1 && currentHour < 3) {
            cal.set(Calendar.HOUR_OF_DAY, 3)
        } else {
            // 否则跳到下月 1 号
            cal.add(Calendar.MONTH, 1)
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, 3)
        }

        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        return cal.timeInMillis
    }

    /**
     * 获取间隔时间（毫秒）
     *
     * @param frequency 备份频率
     * @return 间隔时间（毫秒）
     */
    private fun getIntervalMillis(frequency: BackupFrequency): Long {
        return when (frequency) {
            BackupFrequency.WEEKLY -> AlarmManager.INTERVAL_DAY * 7
            BackupFrequency.MONTHLY -> AlarmManager.INTERVAL_DAY * 30
        }
    }

    /**
     * 创建 PendingIntent
     *
     * @param context 上下文
     * @param frequency 备份频率
     * @return PendingIntent
     */
    private fun createPendingIntent(context: Context, frequency: BackupFrequency): PendingIntent {
        val intent = Intent(context, AutoBackupReceiver::class.java).apply {
            action = AutoBackupReceiver.ACTION_AUTO_BACKUP
            putExtra(AutoBackupReceiver.EXTRA_FREQUENCY, frequency.value)
        }

        val requestCode = when (frequency) {
            BackupFrequency.WEEKLY -> REQUEST_CODE_WEEKLY
            BackupFrequency.MONTHLY -> REQUEST_CODE_MONTHLY
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags
        )
    }
}
