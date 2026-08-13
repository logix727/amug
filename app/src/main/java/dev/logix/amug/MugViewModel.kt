package dev.logix.amug

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MugViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(BleState())
    val state = mutableState.asStateFlow()
    private val client = MugBleClient(application) { mutableState.value = it }

    fun scan() = client.scan()
    fun connect(device: MugDevice) = client.connect(device)
    fun disconnect() = client.disconnect()
    fun setTemperature(celsius: Double) = client.setTemperature(celsius)
    fun setHeating(enabled: Boolean) = client.setHeating(enabled)
    fun setGear(gear: Int) = client.setGear(gear)
    fun refresh() = client.refresh()

    override fun onCleared() = client.disconnect()
}
