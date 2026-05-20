package com.corgimemo.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.corgimemo.app.backup.BackupWorker
import com.corgimemo.app.backup.BackupFrequency
import com.corgimemo.app.backup.AutoBackupAlarmScheduler
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 自动备份广播接收器
 * 接收 AlarmManager 触发的闹钟，启动 WorkManager 执行备份
 */
class AutoBackupReceiver : BroadcastReceiver() {

    companion object {
        /** 自动备份广播 Action */
        const val ACTION_AUTO_BACKUP = "com.corgimemo.app.ACTION_AUTO_BACKUP"

        /** 频率 Extra Key */
        const val EXTRA_FREQUENCY = "frequency"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_AUTO_BACKUP) {
            CoroutineScope(Dispatchers.IO).launch {
                val preferences = CorgiPreferences.getInstance(context)

                // 检查自动备份是否仍启用
                val enabled = preferences.getAutoBackupEnabled()
                if (enabled) {
                    // 启动 WorkManager 执行备份
                    val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
                    WorkManager.getInstance(context).enqueue(request)

                    // 获取当前频率，重新调度下次备份
                    val frequency = preferences.getAutoBackupFrequency()
                    val backupFrequency = BackupFrequency.fromValue(frequency)
                    AutoBackupAlarmScheduler.schedule(context, backupFrequency)
                }
            }
        }
    }
}
