package dev.logix.amug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class MugTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, Intent(context, MugConnectionService::class.java).setAction(MugConnectionService.ACTION_TIMER_EXPIRED))
        } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
            TimerRecovery.notifyInterrupted(context, "AMUG timer expired", "Android blocked background mug reconnection. Open AMUG to stop hold; the mug's hardware failsafe remains active.")
        } catch (_: SecurityException) {
            TimerRecovery.notifyInterrupted(context, "AMUG timer needs attention", "Bluetooth permission is unavailable. Open AMUG to check temperature hold.")
        }
    }
}
