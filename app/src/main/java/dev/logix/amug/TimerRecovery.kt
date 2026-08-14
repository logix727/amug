package dev.logix.amug

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object TimerRecovery {
    private const val CHANNEL = "timer_safety"
    fun notifyInterrupted(context: Context, title: String, message: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Timer safety", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, 2100, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(2100, NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_mug).setContentTitle(title).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)).setContentIntent(open).setAutoCancel(true).build())
    }
}
