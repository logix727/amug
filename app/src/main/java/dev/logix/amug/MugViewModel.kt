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
    val temperatureLed: Boolean = true,
)

class MugViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(BleState())
    val state = mutableState.asStateFlow()
    private val storage = application.getSharedPreferences("amug", 0)
    private val mutablePreferences = MutableStateFlow(
        UserPreferences(
            unit = if (storage.getString("unit", "F") == "C") TemperatureUnit.CELSIUS else TemperatureUnit.FAHRENHEIT,
            temperatureLed = storage.getBoolean("temperature_led", true),
        ),
    )
    val preferences = mutablePreferences.asStateFlow()
    private val client = MugBleClient(application) { mutableState.value = it }

    init {
        client.setTemperatureLedEnabled(mutablePreferences.value.temperatureLed)
    }

    fun scan() = client.scan()
    fun connect(device: MugDevice) = client.connect(device)
    fun disconnect() = client.disconnect()
    fun setTemperature(celsius: Double) = client.setTemperature(celsius)
    fun setHeating(enabled: Boolean) = client.setHeating(enabled)
    fun setGear(gear: Int) = client.setGear(gear)
    fun setUnit(unit: TemperatureUnit) {
        storage.edit().putString("unit", if (unit == TemperatureUnit.FAHRENHEIT) "F" else "C").apply()
        mutablePreferences.value = mutablePreferences.value.copy(unit = unit)
    }
    fun setTemperatureLed(enabled: Boolean) {
        storage.edit().putBoolean("temperature_led", enabled).apply()
        mutablePreferences.value = mutablePreferences.value.copy(temperatureLed = enabled)
        client.setTemperatureLedEnabled(enabled)
    }
    fun refresh() = client.refresh()

    override fun onCleared() = client.disconnect()
}
