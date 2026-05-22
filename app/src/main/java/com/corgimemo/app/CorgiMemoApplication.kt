package com.corgimemo.app

import android.app.Application
import com.corgimemo.app.notification.NotificationHelper
import com.corgimemo.app.widget.WidgetUpdateWorker
import com.corgimemo.app.worker.ArchiveCleanupScheduler
import com.corgimemo.app.worker.ReminderRestoreScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CorgiMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ArchiveCleanupScheduler.scheduleIfNeeded(this)
        WidgetUpdateWorker.scheduleWidgetUpdates(this)
        ReminderRestoreScheduler.restoreNow(this)
    }
}