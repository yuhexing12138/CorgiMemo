package com.corgimemo.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 归档清理调度器
 *
 * 负责调度每日凌晨执行的归档清理任务
 */
object ArchiveCleanupScheduler {

    private const val WORK_NAME = "AutoArchiveCleanup"

    /**
     * 调度每日凌晨的清理任务
     *
     * @param context 上下文
     */
    fun scheduleDailyCleanup(context: Context) {
        val workRequest = PeriodicWorkRequestBuilder<AutoArchiveCleanupWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 1,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * 应用启动时检查并调度清理任务
     *
     * @param context 上下文
     */
    fun scheduleIfNeeded(context: Context) {
        scheduleDailyCleanup(context)
    }

    /**
     * 立即执行一次清理任务（用于测试）
     *
     * @param context 上下文
     */
    fun runCleanupNow(context: Context) {
        val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<AutoArchiveCleanupWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }

    /**
     * 取消所有清理任务
     *
     * @param context 上下文
     */
    fun cancelCleanup(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
