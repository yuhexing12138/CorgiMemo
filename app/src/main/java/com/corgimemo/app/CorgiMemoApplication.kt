package com.corgimemo.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CorgiMemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}