package com.corgimemo.app

import android.app.Application
import com.corgimemo.app.worker.ArchiveCleanupScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CorgiMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ArchiveCleanupScheduler.scheduleIfNeeded(this)
    }
}