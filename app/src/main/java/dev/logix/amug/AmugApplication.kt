package dev.logix.amug

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.runBlocking

class AmugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LiveMugConnection.mugId = null
        runBlocking { MugWidget().updateAll(this@AmugApplication) }
    }
}
