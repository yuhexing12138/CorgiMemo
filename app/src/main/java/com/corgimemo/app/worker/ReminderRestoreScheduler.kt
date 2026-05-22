package com.corgimemo.app.worker

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * 提醒闹钟恢复调度器
 *
 * 负责在应用启动时恢复所有未过期的提醒闹钟
 */
object ReminderRestoreScheduler {

    private const val WORK_NAME = "ReminderRestore"

    /**
     * 立即执行提醒闹钟恢复
     *
     * @param context 上下文
     */
    fun restoreNow(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<ReminderRestoreWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(oneTimeRequest)
    }

    /**
     * 取消所有提醒恢复任务
     *
     * @param context 上下文
     */
    fun cancelRestore(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
    }
}
