package com.corgimemo.app

import android.app.Application
import com.corgimemo.app.notification.NotificationHelper
import com.corgimemo.app.worker.ArchiveCleanupScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CorgiMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ArchiveCleanupScheduler.scheduleIfNeeded(this)
    }
}