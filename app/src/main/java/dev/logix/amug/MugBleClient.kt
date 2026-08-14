package dev.logix.amug

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

data class MugDevice(val name: String, val address: String, val rssi: Int)

enum class ConnectionStage { IDLE, SCANNING, CONNECTING, RECONNECTING, INITIALIZING, READY, ERROR }

enum class CommandState { IDLE, SENDING, CONFIRMED, FAILED }

data class BleEvent(val timestamp: Long, val message: String)

data class BleState(
    val stage: ConnectionStage = ConnectionStage.IDLE,
    val devices: List<MugDevice> = emptyList(),
    val connectedName: String? = null,
    val profile: MugProfile? = null,
    val status: MugStatus? = null,
    val version: MugVersion? = null,
    val commandState: CommandState = CommandState.IDLE,
    val commandMessage: String? = null,
    val lastUpdatedAt: Long? = null,
    val sleepTimerEndsAt: Long? = null,
    val telemetry: List<TelemetryPoint> = emptyList(),
    val events: List<BleEvent> = emptyList(),
    val error: String? = null,
)

private data class QueuedWrite(
    val value: ByteArray,
    val label: String,
    val verify: ((MugStatus) -> Boolean)? = null,
    val attempts: Int = 0,
)

@SuppressLint("MissingPermission")
class MugBleClient(
    context: Context,
    private val onState: (BleState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
    private val thread = HandlerThread("amug-ble").apply { start() }
    private val handler = Handler(thread.looper)
    private var state = BleState()
    private var gatt: BluetoothGatt? = null
    private var generation = 0
    private var profile: MugProfile? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private val writes = ArrayDeque<QueuedWrite>()
    private var activeWrite: QueuedWrite? = null
    private var awaitingVerification: QueuedWrite? = null
    private var desiredDevice: MugDevice? = null
    private var intentionalDisconnect = false
    private var reconnectAttempts = 0
    private var reconnectRequestId = 0
    private var phaseTimeout: Runnable? = null
    private var writeTimeout: Runnable? = null
    private var verificationTimeout: Runnable? = null
    private var scanTimeout: Runnable? = null
    private var scanGeneration = 0
    private var ambientLedEnabled = false
    private var temperatureLedPalette = MugProtocol.defaultLedPalette
    private var lastTemperatureColor: Int? = null
    private var polling: Runnable? = null
    private val found = linkedMapOf<String, MugDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post {
                if (state.stage != ConnectionStage.SCANNING) return@post
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return@post
                val serviceIds = result.scanRecord?.serviceUuids.orEmpty().map(ParcelUuid::getUuid)
                val likelyMug = name.matches(Regex("^S6-PLUS-[A-Za-z0-9]{4}$", RegexOption.IGNORE_CASE)) ||
                    name.startsWith("VSITOO", true) || name == "S6" ||
                    MugProfile.entries.any { it.service in serviceIds }
                if (!likelyMug) return@post
                found[result.device.address] = MugDevice(name, result.device.address, result.rssi)
                update(state.copy(devices = found.values.sortedByDescending(MugDevice::rssi)))
            }
        }

        override fun onScanFailed(errorCode: Int) {
            handler.post { if (state.stage == ConnectionStage.SCANNING) fail("Bluetooth scan failed ($errorCode)", reconnect = false) }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) { handler.post {
            if (!isCurrent(g)) return@post closeStale(g)
            cancelPhaseTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) {
                return@post handleLinkLoss("Connection failed ($status)")
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected; discovering services")
                    update(state.copy(stage = ConnectionStage.INITIALIZING, error = null))
                    if (!g.discoverServices()) fail("Service discovery could not start")
                    else armPhaseTimeout("Service discovery timed out", 8_000)
                }
                BluetoothProfile.STATE_DISCONNECTED -> handleLinkLoss("Mug disconnected")
            }
        } }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) { handler.post {
            if (!isCurrent(g)) return@post
            cancelPhaseTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) return@post fail("Service discovery failed ($status)")
            profile = MugProfile.entries.firstOrNull { g.getService(it.service) != null }
                ?: return@post fail("This device does not expose the VSITOO mug service", reconnect = false)
            val p = profile!!
            val service = g.getService(p.service)
            writeCharacteristic = service.getCharacteristic(p.write)
                ?: return@post fail("Missing write characteristic", reconnect = false)
            val notify = service.getCharacteristic(p.notify)
                ?: return@post fail("Missing notification characteristic", reconnect = false)
            update(state.copy(profile = p))
            if (!g.setCharacteristicNotification(notify, true)) return@post fail("Could not enable notifications")
            val cccd = notify.getDescriptor(CCCD) ?: return@post fail("Missing notification descriptor", reconnect = false)
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) }
            }
            if (!started) fail("Notification setup could not start")
            else armPhaseTimeout("Notification setup timed out", 5_000)
        } }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) { handler.post {
            if (!isCurrent(g)) return@post
            cancelPhaseTimeout()
            if (status != BluetoothGatt.GATT_SUCCESS) return@post fail("Notification setup failed ($status)")
            log("Notifications enabled; settling before snapshot")
            handler.postDelayed({
                if (!isCurrent(g)) return@postDelayed
                enqueue(QueuedWrite(MugProtocol.requestVersion, "Read version"))
                handler.postDelayed({
                    if (isCurrent(g)) enqueue(QueuedWrite(MugProtocol.requestStatus, "Read status"))
                }, 750)
                armPhaseTimeout("Mug did not return initial status", 12_000)
            }, 750)
        } }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) { handler.post {
            if (!isCurrent(g)) return@post
            cancelWriteTimeout()
            val completed = activeWrite ?: return@post
            activeWrite = null
            if (status != BluetoothGatt.GATT_SUCCESS) return@post retryOrFail(completed, "GATT write failed ($status)")
            log("TX ✓ ${completed.label}")
            if (completed.verify != null) {
                awaitingVerification = completed
                enqueueFirst(QueuedWrite(MugProtocol.requestStatus, "Verify ${completed.label}"))
                armVerificationTimeout(completed)
            }
            drainWrites()
        } }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handler.post { if (isCurrent(g)) receive(value.copyOf()) }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handler.post { if (isCurrent(g)) receive(characteristic.value?.copyOf() ?: return@post) }
        }
    }

    fun scan() = handler.post {
        intentionalDisconnect = true
        desiredDevice = null
        closeGatt()
        intentionalDisconnect = false
        stopScanInternal()
        found.clear()
        scanGeneration++
        val currentScan = scanGeneration
        update(BleState(stage = ConnectionStage.SCANNING, events = state.events))
        log("Scanning for VSITOO mugs")
        val scanner = adapter.bluetoothLeScanner ?: return@post fail("Bluetooth is off", reconnect = false)
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        scanTimeout = Runnable { if (currentScan == scanGeneration) stopScanInternal() }.also { handler.postDelayed(it, 10_000) }
    }

    fun stopScan() = handler.post(::stopScanInternal)

    fun connect(device: MugDevice) = handler.post {
        reconnectRequestId++
        desiredDevice = device
        intentionalDisconnect = false
        reconnectAttempts = 0
        startConnection(device, reconnect = false)
    }

    fun setTemperature(celsius: Double) = handler.post {
        if (profile != MugProfile.S6_PLUS || state.status == null || state.status?.empty == true) return@post
        val target = celsius.coerceIn(48.0, 66.0)
        enqueueReplacing(
            QueuedWrite(MugProtocol.setTemperature(target), "Set ${fahrenheit(target)}°F", verify = {
                abs(it.targetC - target) < .02
            }),
            opcode = 0x04,
        )
    }

    fun setMaintenanceEnabled(enabled: Boolean) = handler.post {
        val current = state.status ?: return@post
        if (enabled && current.empty) return@post
        if (enabled && current.nightLightEnabled) enqueue(QueuedWrite(MugProtocol.setNightLight(0, false), "Exit ambient mode"))
        val command = when (profile) {
            MugProfile.S6_PLUS -> MugProtocol.setS6PlusGear(if (enabled) 1 else 0)
            MugProfile.S6 -> byteArrayOf(0x0A, if (enabled) 1 else 0)
            null -> return@post
        }
        enqueue(QueuedWrite(command, if (enabled) "Enable temperature hold" else "Stop temperature hold", verify = {
            it.maintenanceEnabled == enabled
        }))
    }

    fun setGear(gear: Int) = handler.post {
        val current = state.status ?: return@post
        if (current.empty) return@post
        val command = when (profile) {
            MugProfile.S6_PLUS -> MugProtocol.setS6PlusGear(gear)
            MugProfile.S6 -> MugProtocol.setS6Gear(gear)
            null -> return@post
        }
        val expectedTarget = if (profile == MugProfile.S6_PLUS) listOf(0.0, 54.44, 60.0, 65.56)[gear.coerceIn(0, 3)] else null
        enqueue(QueuedWrite(command, "Set heat preset $gear", verify = {
            it.maintenanceEnabled == (gear != 0) && (expectedTarget == null || abs(it.targetC - expectedTarget) < .02)
        }))
    }

    fun setAmbientTemperatureMode(enabled: Boolean) = handler.post {
        ambientLedEnabled = enabled
        lastTemperatureColor = null
        val status = state.status ?: return@post
        if (profile != MugProfile.S6_PLUS) return@post
        if (enabled) {
            if (status.maintenanceEnabled) enqueue(QueuedWrite(MugProtocol.setS6PlusGear(0), "Pause heat for ambient mode", verify = { !it.maintenanceEnabled }))
            syncTemperatureLed(status)
        } else {
            enqueue(QueuedWrite(MugProtocol.setNightLight(0, false), "Turn ambient mode off", verify = { !it.nightLightEnabled }))
        }
    }

    fun setTemperatureLedPalette(palette: List<LedColorStop>) = handler.post {
        temperatureLedPalette = palette
        lastTemperatureColor = null
        if (ambientLedEnabled) state.status?.let(::syncTemperatureLed)
    }

    fun setSafetyWait(hours: Int) = handler.post {
        if (profile != MugProfile.S6_PLUS || state.status == null) return@post
        enqueue(QueuedWrite(MugProtocol.setSafetyWait(hours), "Set auto-off to $hours hours", verify = { it.safetyWaitHours == hours }))
    }

    fun setMusicMode(mode: Int?) = handler.post {
        val current = state.status ?: return@post
        if (profile != MugProfile.S6_PLUS || !current.maintenanceEnabled) return@post commandFailure("Music lighting requires temperature hold")
        if (current.nightLightEnabled) enqueue(QueuedWrite(MugProtocol.setNightLight(0, false), "Turn ambient mode off", verify = { !it.nightLightEnabled }))
        val command = mode?.let(MugProtocol::setMusicMode) ?: MugProtocol.stopMusic
        val expected = mode ?: 0x16
        enqueue(QueuedWrite(command, mode?.let { "Set music light ${it + 1}" } ?: "Turn music lighting off", verify = { it.lightMode == expected }))
    }

    fun setHoldLight(enabled: Boolean) = handler.post {
        if (profile != MugProfile.S6_PLUS || state.status == null) return@post
        enqueue(QueuedWrite(MugProtocol.setHoldLight(enabled), "${if (enabled) "Enable" else "Disable"} hold light", verify = { it.holdLightMode == if (enabled) 1 else 0 }))
    }

    fun setChargeLight(enabled: Boolean) = handler.post {
        if (profile != MugProfile.S6_PLUS || state.status == null) return@post
        enqueue(QueuedWrite(MugProtocol.setChargeLight(enabled), "${if (enabled) "Enable" else "Disable"} charge light", verify = { it.chargeLightMode == if (enabled) 1 else 0 }))
    }

    fun refresh() = handler.post { enqueue(QueuedWrite(MugProtocol.requestStatus, "Refresh status")) }

    fun disconnect() = handler.post {
        reconnectRequestId++
        intentionalDisconnect = true
        desiredDevice = null
        stopScanInternal()
        gatt?.disconnect()
        closeGatt()
        update(BleState(events = state.events))
    }

    fun close() {
        handler.post {
            intentionalDisconnect = true
            desiredDevice = null
            closeGatt()
            thread.quitSafely()
        }
    }

    fun shutdown(timeoutMs: Long = 2_000) {
        val latch = CountDownLatch(1)
        handler.post {
            intentionalDisconnect = true
            reconnectRequestId++
            desiredDevice = null
            closeGatt()
            latch.countDown()
            thread.quitSafely()
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private fun startConnection(device: MugDevice, reconnect: Boolean) {
        stopScanInternal()
        closeGatt()
        generation++
        update(
            state.copy(
                stage = if (reconnect) ConnectionStage.RECONNECTING else ConnectionStage.CONNECTING,
                connectedName = device.name,
                profile = null,
                status = null,
                version = null,
                commandState = CommandState.IDLE,
                error = null,
            ),
        )
        log(if (reconnect) "Reconnecting to ${device.name}" else "Connecting to ${device.name}")
        val remote = try { adapter.getRemoteDevice(device.address) } catch (_: IllegalArgumentException) {
            return fail("Stored mug address is invalid", reconnect = false)
        }
        gatt = try {
            if (Build.VERSION.SDK_INT >= 26) remote.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE, BluetoothDevice.PHY_LE_1M_MASK, handler)
            else remote.connectGatt(appContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (_: SecurityException) {
            return fail("Bluetooth permission was revoked", reconnect = false)
        }
        if (gatt == null) fail("Connection could not start") else armPhaseTimeout("Connection timed out", 12_000)
    }

    private fun enqueue(write: QueuedWrite) {
        if (writeCharacteristic == null || gatt == null) return commandFailure("Mug is not ready")
        writes.addLast(write)
        if (write.verify != null) update(state.copy(commandState = CommandState.SENDING, commandMessage = write.label))
        drainWrites()
    }

    private fun enqueueFirst(write: QueuedWrite) {
        writes.addFirst(write)
        drainWrites()
    }

    private fun enqueueReplacing(write: QueuedWrite, opcode: Int) {
        val retained = writes.filterNot { it.value.firstOrNull()?.toInt()?.and(0xff) == opcode }
        writes.clear()
        writes.addAll(retained)
        enqueue(write)
    }

    private fun drainWrites() {
        if (activeWrite != null || writes.isEmpty()) return
        if (awaitingVerification != null && writes.first().value.firstOrNull()?.toInt()?.and(0xff) != 0x03) return
        val g = gatt ?: return
        val characteristic = writeCharacteristic ?: return
        val write = writes.removeFirst()
        activeWrite = write
        log("TX ${write.value.hex()}  ${write.label}")
        val started = if (Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, write.value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = write.value
                g.writeCharacteristic(characteristic)
            }
        }
        if (!started) {
            activeWrite = null
            retryOrFail(write, "Write could not start")
        } else armWriteTimeout(write)
    }

    private fun receive(value: ByteArray) {
        log("RX ${value.hex()}")
        MugProtocol.parseVersion(value)?.let { update(state.copy(version = it)) }
        val status = when (profile) {
            MugProfile.S6_PLUS -> MugProtocol.parseS6PlusStatus(value)
            MugProfile.S6 -> MugProtocol.parseS6Status(value)
            null -> null
        } ?: return
        cancelPhaseTimeout()
        val now = System.currentTimeMillis()
        reconnectAttempts = 0
        val point = TelemetryPoint(now, status.currentC, status.targetC, status.maintenanceEnabled, status.empty, status.batteryPercent, status.charging)
        update(state.copy(status = status, stage = ConnectionStage.READY, lastUpdatedAt = now, telemetry = (state.telemetry + point).takeLast(20), error = null))
        schedulePoll()
        awaitingVerification?.let { pending ->
            if (pending.verify?.invoke(status) == true) {
                cancelVerificationTimeout()
                awaitingVerification = null
                update(state.copy(commandState = CommandState.CONFIRMED, commandMessage = "${pending.label} confirmed"))
                log("Confirmed ${pending.label}")
                drainWrites()
            }
        }
        if (ambientLedEnabled) syncTemperatureLed(status)
    }

    private fun syncTemperatureLed(status: MugStatus) {
        if (!ambientLedEnabled || profile != MugProfile.S6_PLUS || status.maintenanceEnabled) return
        val step = fahrenheit(status.currentC)
        val quantized = (step - 32) * 5 / 9.0
        val color = MugProtocol.temperatureColor(quantized, temperatureLedPalette)
        if (color == lastTemperatureColor && status.nightLightEnabled && status.lightColor == color) return
        lastTemperatureColor = color
        enqueueReplacing(
            QueuedWrite(MugProtocol.setNightLight(color, true), "Ambient LED ${"#%06X".format(color)}", verify = {
                it.nightLightEnabled && it.lightColor == color && !it.maintenanceEnabled
            }),
            opcode = 0x07,
        )
    }

    private fun retryOrFail(write: QueuedWrite, reason: String) {
        if (write.attempts < 2 && isTransportReady()) {
            log("Retrying ${write.label}: $reason")
            writes.addFirst(write.copy(attempts = write.attempts + 1))
            handler.postDelayed(::drainWrites, 300L * (write.attempts + 1))
        } else commandFailure("${write.label}: $reason")
    }

    private fun commandFailure(message: String) {
        awaitingVerification = null
        cancelVerificationTimeout()
        update(state.copy(commandState = CommandState.FAILED, commandMessage = message))
        log(message)
        drainWrites()
    }

    private fun handleLinkLoss(message: String) {
        closeGatt()
        if (intentionalDisconnect) return update(BleState(events = state.events))
        val device = desiredDevice
        if (device != null && reconnectAttempts < 3) {
            reconnectAttempts++
            val requestId = ++reconnectRequestId
            val delay = 750L * reconnectAttempts
            log("$message; reconnect ${reconnectAttempts}/3 in ${delay}ms")
            update(state.copy(stage = ConnectionStage.RECONNECTING, error = message, status = null))
            handler.postDelayed({ if (!intentionalDisconnect && desiredDevice == device && reconnectRequestId == requestId) startConnection(device, true) }, delay)
        } else fail(message, reconnect = false)
    }

    private fun fail(message: String, reconnect: Boolean = true) {
        log(message)
        if (reconnect && desiredDevice != null && !intentionalDisconnect) return handleLinkLoss(message)
        closeGatt()
        update(
            state.copy(
                stage = ConnectionStage.ERROR,
                profile = null,
                status = null,
                version = null,
                commandState = CommandState.FAILED,
                commandMessage = message,
                error = message,
            ),
        )
    }

    private fun stopScanInternal() {
        scanGeneration++
        scanTimeout?.let(handler::removeCallbacks)
        scanTimeout = null
        runCatching { adapter.bluetoothLeScanner?.stopScan(scanCallback) }
        if (state.stage == ConnectionStage.SCANNING) update(state.copy(stage = ConnectionStage.IDLE))
    }

    private fun armPhaseTimeout(message: String, delay: Long) {
        cancelPhaseTimeout()
        val expectedGeneration = generation
        phaseTimeout = Runnable { if (expectedGeneration == generation) fail(message) }.also { handler.postDelayed(it, delay) }
    }

    private fun cancelPhaseTimeout() { phaseTimeout?.let(handler::removeCallbacks); phaseTimeout = null }

    private fun armWriteTimeout(write: QueuedWrite) {
        cancelWriteTimeout()
        writeTimeout = Runnable {
            if (activeWrite === write) activeWrite = null
            retryOrFail(write, "Write timed out")
        }.also { handler.postDelayed(it, 2_000) }
    }

    private fun armVerificationTimeout(write: QueuedWrite) {
        cancelVerificationTimeout()
        verificationTimeout = Runnable {
            if (awaitingVerification === write) {
                awaitingVerification = null
                retryOrFail(write, "Mug did not confirm change")
            }
        }.also { handler.postDelayed(it, 2_500) }
    }

    private fun cancelWriteTimeout() { writeTimeout?.let(handler::removeCallbacks); writeTimeout = null }
    private fun cancelVerificationTimeout() { verificationTimeout?.let(handler::removeCallbacks); verificationTimeout = null }

    private fun closeGatt() {
        cancelPhaseTimeout()
        cancelWriteTimeout()
        cancelVerificationTimeout()
        gatt?.close()
        gatt = null
        generation++
        profile = null
        writeCharacteristic = null
        writes.clear()
        activeWrite = null
        awaitingVerification = null
        lastTemperatureColor = null
        polling?.let(handler::removeCallbacks)
        polling = null
    }

    private fun isCurrent(candidate: BluetoothGatt) = candidate === gatt
    private fun isTransportReady() = gatt != null && writeCharacteristic != null
    private fun closeStale(candidate: BluetoothGatt) = candidate.close()

    private fun log(message: String) {
        Log.d("AMUG-BLE", message)
        val events = (state.events + BleEvent(System.currentTimeMillis(), message)).takeLast(120)
        update(state.copy(events = events))
    }

    private fun schedulePoll() {
        polling?.let(handler::removeCallbacks)
        polling = Runnable {
            if (state.stage == ConnectionStage.READY && awaitingVerification == null && activeWrite == null) {
                enqueue(QueuedWrite(MugProtocol.requestStatus, "Telemetry sample"))
            }
        }.also { handler.postDelayed(it, 60_000) }
    }

    private fun update(next: BleState) { state = next; onState(next) }
    private fun ByteArray.hex() = joinToString("") { "%02X".format(it.toInt() and 0xff) }
    private fun fahrenheit(celsius: Double) = (celsius * 9 / 5 + 32).roundToInt()

    companion object {
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
