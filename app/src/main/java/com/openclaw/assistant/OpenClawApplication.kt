package com.openclaw.assistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OpenClawApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("OpenClaw", "Application started – v${BuildConfig.VERSION_NAME}")
    }
}
