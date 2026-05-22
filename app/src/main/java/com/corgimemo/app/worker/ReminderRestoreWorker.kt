package com.corgimemo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.di.IoDispatcher
import com.corgimemo.app.notification.AlarmScheduler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 提醒闹钟恢复 Worker
 *
 * 应用启动时执行，恢复所有未过期的提醒闹钟
 */
class ReminderRestoreWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun todoRepository(): TodoRepository
        @IoDispatcher fun ioDispatcher(): CoroutineDispatcher
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WorkerEntryPoint::class.java
        )

        val todoRepository = entryPoint.todoRepository()
        val ioDispatcher = entryPoint.ioDispatcher()

        return withContext(ioDispatcher) {
            try {
                val pendingReminders = todoRepository.getTodosWithPendingReminders()
                AlarmScheduler.restoreAllReminders(context, pendingReminders)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
