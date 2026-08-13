package dev.logix.amug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TemperatureUnit {
    FAHRENHEIT,
    CELSIUS;

    fun display(celsius: Double) = if (this == FAHRENHEIT) celsius * 9 / 5 + 32 else celsius
    fun toCelsius(value: Double) = if (this == FAHRENHEIT) (value - 32) * 5 / 9 else value
    val symbol get() = if (this == FAHRENHEIT) "°F" else "°C"
}

data class UserPreferences(
    val unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val temperatureLed: Boolean = false,
    val ledPalette: List<LedColorStop> = MugProtocol.defaultLedPalette,
)

class MugViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(BleState())
    val state = mutableState.asStateFlow()
    private val storage = application.getSharedPreferences("amug", 0)
    private val mutablePreferences = MutableStateFlow(
        UserPreferences(
            unit = if (storage.getString("unit", "F") == "C") TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
            temperatureLed = storage.getBoolean("ambient_temperature_mode", false),
            ledPalette = loadLedPalette(storage.getString("led_palette", null)),
        ),
    )
    val preferences = mutablePreferences.asStateFlow()
    private val client = MugBleClient(application) { mutableState.value = it }

    init {
        client.setAmbientTemperatureMode(mutablePreferences.value.temperatureLed)
        client.setTemperatureLedPalette(mutablePreferences.value.ledPalette)
    }

    fun scan() = client.scan()
    fun connect(device: MugDevice) {
        storage.edit().putString("last_name", device.name).putString("last_address", device.address).apply()
        client.connect(device)
    }
    fun connectLast(): Boolean {
        if (mutableState.value.stage !in setOf(ConnectionStage.IDLE, ConnectionStage.ERROR)) return false
        val address = storage.getString("last_address", null) ?: return false
        val name = storage.getString("last_name", "VSITOO mug") ?: "VSITOO mug"
        client.connect(MugDevice(name, address, 0))
        return true
    }
    fun disconnect() = client.disconnect()
    fun setTemperature(celsius: Double) = client.setTemperature(celsius)
    fun setMaintenanceEnabled(enabled: Boolean) {
        if (enabled && mutablePreferences.value.temperatureLed) setTemperatureLed(false)
        client.setMaintenanceEnabled(enabled)
    }
    fun setGear(gear: Int) = client.setGear(gear)
    fun setUnit(unit: TemperatureUnit) {
        storage.edit().putString("unit", if (unit == TemperatureUnit.FAHRENHEIT) "F" else "C").apply()
        mutablePreferences.value = mutablePreferences.value.copy(unit = unit)
    }
    fun setTemperatureLed(enabled: Boolean) {
        storage.edit().putBoolean("ambient_temperature_mode", enabled).apply()
        mutablePreferences.value = mutablePreferences.value.copy(temperatureLed = enabled)
        client.setAmbientTemperatureMode(enabled)
    }
    fun setLedColor(index: Int, color: Int) {
        val palette = mutablePreferences.value.ledPalette.toMutableList()
        if (index !in palette.indices) return
        palette[index] = palette[index].copy(color = color and 0xffffff)
        saveLedPalette(palette)
    }
    fun resetLedPalette() = saveLedPalette(MugProtocol.defaultLedPalette)
    fun setSafetyWait(hours: Int) = client.setSafetyWait(hours)
    private fun saveLedPalette(palette: List<LedColorStop>) {
        storage.edit().putString("led_palette", palette.joinToString(",") { "%06X".format(it.color) }).apply()
        mutablePreferences.value = mutablePreferences.value.copy(ledPalette = palette)
        client.setTemperatureLedPalette(palette)
    }
    fun refresh() = client.refresh()

    override fun onCleared() = client.close()

    companion object {
        private fun loadLedPalette(value: String?): List<LedColorStop> {
            val colors = value?.split(",")?.mapNotNull { it.toIntOrNull(16) }
            return if (colors?.size == MugProtocol.defaultLedPalette.size) {
                MugProtocol.defaultLedPalette.mapIndexed { index, stop -> stop.copy(color = colors[index]) }
            } else MugProtocol.defaultLedPalette
        }
    }
}
