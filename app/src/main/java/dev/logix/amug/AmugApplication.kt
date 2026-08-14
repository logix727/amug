package dev.logix.amug

import android.app.Application

class AmugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveMugConnection.mugId = null
    }
}
