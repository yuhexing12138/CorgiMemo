package com.corgimemo.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.di.IoDispatcher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * 自动归档清理 Worker
 *
 * 每日凌晨执行，清理超过 30 天的已完成待办
 */
class AutoArchiveCleanupWorker(
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
                val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
                val deletedCount = todoRepository.cleanupOldCompletedTodos(thirtyDaysAgo)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }
}
