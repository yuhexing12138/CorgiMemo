package com.corgimemo.app.backup

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.corgimemo.app.data.local.datastore.CorgiPreferences

/**
 * 备份调度器
 * 统一管理自动备份的调度
 * 使用 AlarmManager 实现精确时间调度
 */
object BackupScheduler {

    /**
     * 根据配置调度自动备份
     *
     * @param context 上下文
     */
    suspend fun scheduleFromConfig(context: Context) {
        val preferences = CorgiPreferences.getInstance(context)
        val enabled = preferences.getAutoBackupEnabled()

        if (!enabled) {
            cancel(context)
            return
        }

        val frequency = preferences.getAutoBackupFrequency()
        val backupFrequency = BackupFrequency.fromValue(frequency)
        schedule(context, backupFrequency)
    }

    /**
     * 调度自动备份
     * 使用 AlarmManager 实现精确时间：
     * - 每周：周日凌晨 3:00
     * - 每月：1 日凌晨 3:00
     *
     * @param context 上下文
     * @param frequency 备份频率
     */
    fun schedule(context: Context, frequency: BackupFrequency) {
        AutoBackupAlarmScheduler.schedule(context, frequency)
    }

    /**
     * 取消所有自动备份调度
     *
     * @param context 上下文
     */
    fun cancel(context: Context) {
        AutoBackupAlarmScheduler.cancel(context)
    }

    /**
     * 立即执行一次备份
     * 用于手动触发备份
     *
     * @param context 上下文
     */
    fun triggerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    /**
     * 检查是否有自动备份任务在运行
     *
     * @param context 上下文
     * @return 是否有任务在运行
     */
    suspend fun isScheduled(context: Context): Boolean {
        val preferences = CorgiPreferences.getInstance(context)
        return preferences.getAutoBackupEnabled()
    }

    /**
     * 获取下次备份时间描述
     *
     * @param context 上下文
     * @return 下次备份时间的描述，如果未启用则返回 null
     */
    suspend fun getNextBackupDescription(context: Context): String? {
        val preferences = CorgiPreferences.getInstance(context)
        val enabled = preferences.getAutoBackupEnabled()

        if (!enabled) return null

        val frequency = preferences.getAutoBackupFrequency()
        val backupFrequency = BackupFrequency.fromValue(frequency)

        return when (backupFrequency) {
            BackupFrequency.WEEKLY -> "下周日凌晨 3:00"
            BackupFrequency.MONTHLY -> "下月 1 日凌晨 3:00"
        }
    }
}
