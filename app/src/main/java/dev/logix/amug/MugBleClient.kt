package dev.logix.amug

import android.annotation.SuppressLint
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
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import java.util.UUID

data class MugDevice(val name: String, val address: String, val rssi: Int)

enum class ConnectionStage { IDLE, SCANNING, CONNECTING, INITIALIZING, READY, ERROR }

data class BleState(
    val stage: ConnectionStage = ConnectionStage.IDLE,
    val devices: List<MugDevice> = emptyList(),
    val connectedName: String? = null,
    val profile: MugProfile? = null,
    val status: MugStatus? = null,
    val error: String? = null,
)

@SuppressLint("MissingPermission")
class MugBleClient(
    context: Context,
    private val onState: (BleState) -> Unit,
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java).adapter
    private val handler = Handler(Looper.getMainLooper())
    private var state = BleState()
    private var gatt: BluetoothGatt? = null
    private var profile: MugProfile? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var pendingWrites = ArrayDeque<ByteArray>()
    private var writing = false
    private val found = linkedMapOf<String, MugDevice>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name ?: return
            val serviceIds = result.scanRecord?.serviceUuids.orEmpty().map(ParcelUuid::getUuid)
            val likelyMug = name.matches(Regex("^S6-PLUS-[A-Za-z0-9]{4}$", RegexOption.IGNORE_CASE)) ||
                name.startsWith("VSITOO", true) || name == "S6" ||
                MugProfile.entries.any { it.service in serviceIds }
            if (!likelyMug) return
            found[result.device.address] = MugDevice(name, result.device.address, result.rssi)
            update(state.copy(devices = found.values.sortedByDescending(MugDevice::rssi)))
        }

        override fun onScanFailed(errorCode: Int) = fail("Bluetooth scan failed ($errorCode)")
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (g !== gatt) return
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                update(state.copy(stage = ConnectionStage.INITIALIZING, error = null))
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                closeGatt()
                update(state.copy(stage = ConnectionStage.IDLE, connectedName = null, status = null))
            } else if (status != BluetoothGatt.GATT_SUCCESS) fail("Connection failed ($status)")
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return fail("Service discovery failed ($status)")
            profile = MugProfile.entries.firstOrNull { g.getService(it.service) != null }
                ?: return fail("This device does not expose the VSITOO mug service")
            val p = profile!!
            update(state.copy(profile = p))
            val service = g.getService(p.service)
            writeCharacteristic = service.getCharacteristic(p.write)
                ?: return fail("Missing write characteristic")
            val notify = service.getCharacteristic(p.notify)
                ?: return fail("Missing notification characteristic")
            if (!g.setCharacteristicNotification(notify, true)) return fail("Could not enable notifications")
            val cccd = notify.getDescriptor(CCCD) ?: return fail("Missing notification descriptor")
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run { cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(cccd) }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return fail("Notification setup failed ($status)")
            enqueue(MugProtocol.requestVersion)
            enqueue(MugProtocol.requestStatus)
            update(state.copy(stage = ConnectionStage.READY))
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writing = false
            if (status != BluetoothGatt.GATT_SUCCESS) return fail("Command failed ($status)")
            drainWrites()
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receive(value.copyOf())
        }

        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            receive(characteristic.value?.copyOf() ?: return)
        }
    }

    fun scan() {
        disconnect()
        found.clear()
        update(BleState(stage = ConnectionStage.SCANNING))
        adapter.bluetoothLeScanner?.startScan(
            null,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            scanCallback,
        ) ?: fail("Bluetooth is off")
        handler.postDelayed(::stopScan, 10_000)
    }

    fun stopScan() {
        adapter.bluetoothLeScanner?.stopScan(scanCallback)
        if (state.stage == ConnectionStage.SCANNING) update(state.copy(stage = ConnectionStage.IDLE))
    }

    fun connect(device: MugDevice) {
        stopScan()
        update(state.copy(stage = ConnectionStage.CONNECTING, connectedName = device.name, error = null))
        val remote = adapter.getRemoteDevice(device.address)
        gatt = remote.connectGatt(appContext, false, callback, android.bluetooth.BluetoothDevice.TRANSPORT_LE)
    }

    fun setTemperature(celsius: Double) {
        if (profile == MugProfile.S6_PLUS) enqueue(MugProtocol.setTemperature(celsius))
    }

    fun setHeating(enabled: Boolean) {
        val command = when (profile) {
            MugProfile.S6_PLUS -> MugProtocol.setS6PlusGear(if (enabled) 1 else 0)
            MugProfile.S6 -> byteArrayOf(0x0A, if (enabled) 1 else 0)
            null -> return
        }
        enqueue(command)
    }

    fun setGear(gear: Int) {
        enqueue(
            when (profile) {
                MugProfile.S6_PLUS -> MugProtocol.setS6PlusGear(gear)
                MugProfile.S6 -> MugProtocol.setS6Gear(gear)
                null -> return
            },
        )
    }

    fun refresh() = enqueue(MugProtocol.requestStatus)

    fun disconnect() {
        stopScan()
        gatt?.disconnect()
        closeGatt()
        update(BleState())
    }

    private fun enqueue(value: ByteArray) {
        if (writeCharacteristic == null || gatt == null) return
        pendingWrites.addLast(value.copyOf())
        drainWrites()
    }

    private fun drainWrites() {
        if (writing || pendingWrites.isEmpty()) return
        val g = gatt ?: return
        val characteristic = writeCharacteristic ?: return
        val value = pendingWrites.removeFirst()
        writing = true
        val result = if (android.os.Build.VERSION.SDK_INT >= 33) {
            g.writeCharacteristic(characteristic, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = value
                g.writeCharacteristic(characteristic)
            }
        }
        if (!result) { writing = false; fail("Could not send command") }
    }

    private fun receive(value: ByteArray) {
        val status = when (profile) {
            MugProfile.S6_PLUS -> MugProtocol.parseS6PlusStatus(value)
            MugProfile.S6 -> MugProtocol.parseS6Status(value)
            null -> null
        }
        status?.let { update(state.copy(status = it, stage = ConnectionStage.READY)) }
    }

    private fun fail(message: String) {
        closeGatt()
        update(state.copy(stage = ConnectionStage.ERROR, error = message))
    }

    private fun closeGatt() {
        gatt?.close()
        gatt = null
        profile = null
        writeCharacteristic = null
        pendingWrites.clear()
        writing = false
    }

    private fun update(next: BleState) { state = next; onState(next) }

    companion object {
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }
}
