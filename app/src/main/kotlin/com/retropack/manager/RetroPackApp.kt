package com.retropack.manager

import android.app.Application
import com.retropack.manager.util.AppLogger

class RetroPackApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        AppLogger.i("RetroPackApp", "Application process initialized successfully.")
    }
}
