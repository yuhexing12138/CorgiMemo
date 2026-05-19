package com.corgimemo.app.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * 小部件定期刷新 Worker
 * 使用 WorkManager 每 30 分钟刷新一次所有小部件
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val WORK_NAME = "WidgetUpdateWork"
        private const val REFRESH_INTERVAL_MINUTES = 30L

        /**
         * 调度定期刷新任务
         */
        fun scheduleWidgetUpdates(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
                repeatInterval = REFRESH_INTERVAL_MINUTES,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /**
         * 取消定期刷新任务
         */
        fun cancelWidgetUpdates(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            refreshAllWidgets(applicationContext)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun refreshAllWidgets(context: Context) {
        QuickAddWidget.updateAll(context)
        TodayPreviewWidget.updateAll(context)
        TodoListWidget.updateAll(context)
    }
}
