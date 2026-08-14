package dev.logix.amug

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

class MugConnectionService : Service() {
    inner class LocalBinder : Binder() {
        val state: StateFlow<BleState> = mutableState.asStateFlow()

        fun scan() = runBle { client.scan() }
        fun connect(device: MugDevice) = runBle {
            scope.launch { connectRemembered(device) }
        }
        fun connectLast() = this@MugConnectionService.connectLast()
        fun disconnect() = client.disconnect()
        fun setTemperature(celsius: Double) = client.setTemperature(celsius)
        fun setMaintenanceEnabled(enabled: Boolean) = client.setMaintenanceEnabled(enabled)
        fun safetyStop() = client.safetyStop()
        fun setGear(gear: Int) = client.setGear(gear)
        fun setAmbientTemperatureMode(enabled: Boolean) = client.setAmbientTemperatureMode(enabled)
        fun setTemperatureLedPalette(palette: List<LedColorStop>) = client.setTemperatureLedPalette(palette)
        fun setSafetyWait(hours: Int) = client.setSafetyWait(hours)
        fun setMusicMode(mode: Int?) = client.setMusicMode(mode)
        fun setHoldLight(enabled: Boolean) = client.setHoldLight(enabled)
        fun setChargeLight(enabled: Boolean) = client.setChargeLight(enabled)
        fun setSleepTimer(minutes: Int?) = this@MugConnectionService.setSleepTimer(minutes)
        fun clearActiveHistory() = scope.launch {
            persistenceMutex.withLock {
                val mugId = activeMugId ?: return@withLock
                finishSession("history cleared")
                repository.clearHistory(mugId)
                if (mutableState.value.stage == ConnectionStage.READY) {
                    activeSessionId = repository.beginSession(mugId)
                    activeSessionMugId = mugId
                }
            }
        }
        fun refresh() = client.refresh()
        fun clearEvents() = client.clearEvents()
    }

    private val mutableState = MutableStateFlow(BleState())
    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var snapshots: MugSnapshotStore
    private lateinit var repository: MugRepository
    private lateinit var client: MugBleClient
    private var foreground = false
    private var lastSnapshotKey: List<Any?>? = null
    private var lastWidgetUpdate = 0L
    private var widgetUpdateJob: Job? = null
    private var widgetDirty = false
    private var sleepTimerJob: Job? = null
    private var sleepTimerEndsAt: Long? = null
    private var previousReady = false
    private var previousEmpty = false
    private var previousLowBattery = false
    private var previousStage = ConnectionStage.IDLE
    private var previousHot = false
    private var alertPreferences = GlobalPreferences()
    private var lastNotificationKey: List<Any?>? = null
    private val persistenceMutex = Mutex()
    private var activeMugId: Long? = null
    private var activeSessionId: Long? = null
    private var activeSessionMugId: Long? = null
    private var lastSample: SessionSampleEntity? = null
    private var repositoryReady = false
    private var timerExpiryPending = false

    override fun onCreate() {
        super.onCreate()
        snapshots = MugSnapshotStore(this)
        repository = MugRepository(this)
        createNotificationChannel()
        client = MugBleClient(this, ::onBleState)
        scope.launch(Dispatchers.IO) {
            repository.migrateLegacyPreferences()
            repository.closeAbandonedSessions()
            repository.pruneHistory()
            repositoryReady = true
            restoreSleepTimer()
        }
        scope.launch { repository.globalPreferences.collect { alertPreferences = it } }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT_LAST -> {
                if (ensureForeground()) scope.launch { if (!connectLastAsync()) stopIdleForeground() }
            }
            ACTION_DISCONNECT -> client.disconnect()
            ACTION_TIMER_EXPIRED -> {
                timerExpiryPending = true
                if (ensureForeground()) scope.launch { connectLastAsync() }
            }
            null -> if (ensureForeground()) scope.launch { connectLastAsync() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LiveMugConnection.mugId = null
        TileService.requestListeningState(this, ComponentName(this, MugTileService::class.java))
        kotlinx.coroutines.runBlocking(Dispatchers.IO) { finishSession("service stopped"); MugWidget().updateAll(this@MugConnectionService) }
        client.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    private fun connectLast(): Boolean {
        if (mutableState.value.stage !in setOf(ConnectionStage.IDLE, ConnectionStage.ERROR)) return false
        scope.launch { connectLastAsync() }
        return true
    }

    private suspend fun connectLastAsync(): Boolean {
        while (!repositoryReady) delay(25)
        if (mutableState.value.stage !in setOf(ConnectionStage.IDLE, ConnectionStage.ERROR)) return false
        val mug = repository.selectedMugNow() ?: return false
        return runBle { scope.launch { connectRemembered(MugDevice(mug.advertisedName, mug.bleAddress, 0)) } }
    }

    private inline fun runBle(command: () -> Unit): Boolean {
        if (!hasBluetoothPermissions()) return false
        try {
            ContextCompat.startForegroundService(this, Intent(this, MugConnectionService::class.java))
        } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
            return false
        } catch (_: SecurityException) {
            return false
        }
        if (!ensureForeground()) return false
        command()
        return true
    }

    private fun ensureForeground(): Boolean {
        if (foreground) return true
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(mutableState.value),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
            foreground = true
            true
        } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
            stopSelf()
            false
        } catch (_: SecurityException) {
            stopSelf()
            false
        }
    }

    private fun hasBluetoothPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED

    private fun onBleState(state: BleState) {
        val published = state.copy(sleepTimerEndsAt = sleepTimerEndsAt)
        mutableState.value = published
        val notificationKey = listOf(published.stage, published.connectedName, published.status?.currentC?.roundToInt(), published.status?.targetC?.roundToInt(), published.status?.batteryPercent)
        if (foreground && notificationKey != lastNotificationKey) {
            lastNotificationKey = notificationKey
            getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification(published))
        }
        publishAlerts(published)
        if (timerExpiryPending && published.stage == ConnectionStage.READY) {
            timerExpiryPending = false
            client.safetyStop()
            setSleepTimer(null)
            alert(ALERT_TIMER, "Sleep timer finished", "AMUG requested temperature hold off")
        }

        val snapshotKey = listOf(state.stage, state.connectedName, state.status?.currentC, state.status?.targetC, state.lastUpdatedAt)
        if (snapshotKey != lastSnapshotKey) {
            lastSnapshotKey = snapshotKey
            persistState(state, activeMugId)
            TileService.requestListeningState(this, ComponentName(this, MugTileService::class.java))
            scheduleWidgetUpdate()
        }

        if (state.stage == ConnectionStage.IDLE || state.stage == ConnectionStage.ERROR) {
            LiveMugConnection.mugId = null
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foreground = false
            stopSelf()
        }
    }

    private suspend fun connectRemembered(device: MugDevice) {
        val mugId = repository.upsertMug(device)
        persistenceMutex.withLock {
            if (activeMugId != null && activeMugId != mugId) {
                setSleepTimer(null)
                client.disconnect()
                finishSession("switched mug")
                LiveMugConnection.mugId = null
            }
            activeMugId = mugId
        }
        val preferences = repository.mugPreferences(mugId)
        client.setTemperatureLedPalette(MugRepository.parsePalette(preferences.ledPalette))
        client.setAmbientTemperatureMode(preferences.ambientTemperatureMode)
        client.connect(device)
    }

    private fun persistState(state: BleState, stateMugId: Long?) {
        scope.launch {
            persistenceMutex.withLock {
                val mugId = stateMugId ?: return@withLock
                if (activeMugId != mugId) return@withLock
                val status = state.status
                if (state.stage == ConnectionStage.READY && status != null) {
                    LiveMugConnection.mugId = mugId
                    val now = state.lastUpdatedAt ?: System.currentTimeMillis()
                    repository.saveLatestSnapshot(mugId, status, now)
                    val sessionId = if (activeSessionId != null && activeSessionMugId == mugId) activeSessionId!!
                    else repository.beginSession(mugId, now).also { activeSessionId = it; activeSessionMugId = mugId }
                    val previous = lastSample
                    if (previous == null || previous.materiallyDiffers(status) || now - previous.sampledAt >= SAMPLE_INTERVAL_MS) {
                        repository.addSample(sessionId, status, now)
                        lastSample = SessionSampleEntity(
                            sessionId = sessionId, sampledAt = now,
                            currentCentiC = (status.currentC * 100).roundToInt(), targetCentiC = (status.targetC * 100).roundToInt(),
                            batteryPercent = status.batteryPercent, maintenanceEnabled = status.maintenanceEnabled,
                            empty = status.empty, charging = status.charging,
                        )
                    }
                } else if (state.stage in setOf(ConnectionStage.IDLE, ConnectionStage.RECONNECTING, ConnectionStage.ERROR)) {
                    LiveMugConnection.mugId = null
                    finishSession(if (state.stage == ConnectionStage.ERROR) state.error ?: "error" else "disconnected")
                }
            }
        }
    }

    private suspend fun finishSession(reason: String) {
        activeSessionId?.let { repository.endSession(it, reason) }
        activeSessionId = null
        activeSessionMugId = null
        lastSample = null
    }

    private fun scheduleWidgetUpdate() {
        if (widgetUpdateJob?.isActive == true) { widgetDirty = true; return }
        val wait = (WIDGET_THROTTLE_MS - (SystemClock.elapsedRealtime() - lastWidgetUpdate)).coerceAtLeast(0)
        widgetUpdateJob = scope.launch {
            delay(wait)
            MugWidget().updateAll(this@MugConnectionService)
            lastWidgetUpdate = SystemClock.elapsedRealtime()
            if (widgetDirty) {
                widgetDirty = false
                widgetUpdateJob = null
                scheduleWidgetUpdate()
            }
        }
    }

    private fun stopIdleForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        foreground = false
        stopSelf()
    }

    private fun buildNotification(state: BleState): Notification {
        val status = state.status
        val stage = state.stage.name.lowercase().replaceFirstChar(Char::uppercaseChar)
        val temperatures = if (status == null) stage
        else "${fahrenheit(status.currentC)}°F / target ${fahrenheit(status.targetC)}°F - $stage"
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_mug)
            .setContentTitle(state.connectedName ?: "AMUG")
            .setContentText(temperatures)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Mug connection", NotificationManager.IMPORTANCE_LOW)
        channel.description = "Current mug connection and temperature"
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
        getSystemService<NotificationManager>()?.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Mug alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Ready, empty, low battery, and connection alerts"
            },
        )
    }

    private fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerEndsAt = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        mutableState.value = mutableState.value.copy(sleepTimerEndsAt = sleepTimerEndsAt)
        scope.launch(Dispatchers.IO) { repository.setSleepTimer(sleepTimerEndsAt, activeMugId) }
        scheduleTimerAlarm(sleepTimerEndsAt)
        if (minutes != null) {
            sleepTimerJob = scope.launch {
                delay(minutes * 60_000L)
                client.safetyStop()
                sleepTimerEndsAt = null
                repository.setSleepTimer(null, null)
                mutableState.value = mutableState.value.copy(sleepTimerEndsAt = null)
                alert(ALERT_TIMER, "Sleep timer finished", "AMUG requested temperature hold off")
            }
        }
    }

    private suspend fun restoreSleepTimer() {
        val preferences = repository.globalPreferences.first()
        val deadline = preferences.sleepTimerDeadline ?: return
        val selected = repository.selectedMugNow()?.id
        if (selected == null || selected != preferences.sleepTimerMugId) { repository.setSleepTimer(null, null); return }
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) { timerExpiryPending = true; connectLastAsync(); return }
        sleepTimerEndsAt = deadline
        mutableState.value = mutableState.value.copy(sleepTimerEndsAt = deadline)
        scheduleTimerAlarm(deadline)
        sleepTimerJob = scope.launch {
            delay(remaining)
            client.safetyStop()
            sleepTimerEndsAt = null
            repository.setSleepTimer(null, null)
            mutableState.value = mutableState.value.copy(sleepTimerEndsAt = null)
            alert(ALERT_TIMER, "Sleep timer finished", "AMUG requested temperature hold off")
        }
    }

    private fun scheduleTimerAlarm(deadline: Long?) {
        val alarm = getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(this, 2001, Intent(this, MugTimerReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.cancel(pending)
        if (deadline != null) alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline, pending)
    }

    private fun publishAlerts(state: BleState) {
        val status = state.status
        val ready = status != null && status.maintenanceEnabled && kotlin.math.abs(status.currentC - status.targetC) <= .55
        val empty = status?.empty == true
        val lowBattery = (status?.batteryPercent ?: 100) <= 15
        val hot = (status?.currentC ?: 0.0) >= 60.0
        if (ready && !previousReady && alertPreferences.readyAlert) alert(ALERT_READY, "Your drink is ready", "${fahrenheit(status!!.currentC)}°F and holding")
        if (empty && !previousEmpty && alertPreferences.emptyAlert) alert(ALERT_EMPTY, "Mug is empty", "Temperature hold is unavailable until liquid is added")
        if (empty && !previousEmpty) setSleepTimer(null)
        if (lowBattery && !previousLowBattery && alertPreferences.lowBatteryAlert) alert(ALERT_BATTERY, "Mug battery is low", "${status?.batteryPercent}% remaining")
        if (state.stage == ConnectionStage.RECONNECTING && previousStage == ConnectionStage.READY && alertPreferences.disconnectAlert) alert(ALERT_CONNECTION, "Mug connection lost", "AMUG is trying to reconnect")
        if (hot && !previousHot && alertPreferences.hotAlert) alert(ALERT_HOT, "Drink is very hot", "${fahrenheit(status!!.currentC)}°F · sip carefully")
        previousReady = ready
        previousEmpty = empty
        previousLowBattery = lowBattery
        previousStage = state.stage
        previousHot = hot
    }

    private fun alert(id: Int, title: String, text: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(this, id, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        getSystemService<NotificationManager>()?.notify(
            id,
            NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_mug)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun fahrenheit(celsius: Double) = (celsius * 9 / 5 + 32).roundToInt()

    companion object {
        const val ACTION_CONNECT_LAST = "dev.logix.amug.action.CONNECT_LAST"
        const val ACTION_DISCONNECT = "dev.logix.amug.action.DISCONNECT"
        const val ACTION_TIMER_EXPIRED = "dev.logix.amug.action.TIMER_EXPIRED"
        private const val CHANNEL_ID = "mug_connection"
        private const val ALERT_CHANNEL_ID = "mug_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_READY = 1101
        private const val ALERT_EMPTY = 1102
        private const val ALERT_BATTERY = 1103
        private const val ALERT_CONNECTION = 1104
        private const val ALERT_TIMER = 1105
        private const val ALERT_HOT = 1106
        private const val WIDGET_THROTTLE_MS = 15_000L
        private const val SAMPLE_INTERVAL_MS = 60_000L
    }
}
