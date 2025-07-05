package com.yevhenii.audiobooksyncer

import android.app.Application
import android.content.Intent

class TheApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        Intent(applicationContext, TheForegroundService::class.java).also {
            startService(it)
        }
    }
}
