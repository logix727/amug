package dev.logix.amug

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MugBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val preferences = MugRepository(context).globalPreferences.first()
                val deadline = preferences.sleepTimerDeadline ?: return@launch
                if (deadline <= System.currentTimeMillis()) {
                    TimerRecovery.notifyInterrupted(context, "AMUG timer interrupted by restart", "Open AMUG to reconnect and verify temperature hold is off.")
                } else {
                    val alarmIntent = PendingIntent.getBroadcast(context, 2001, Intent(context, MugTimerReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, alarmIntent)
                }
            } finally { pending.finish() }
        }
    }
}
