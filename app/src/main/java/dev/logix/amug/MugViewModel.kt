package dev.logix.amug

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

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

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MugViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(BleState())
    val state = mutableState.asStateFlow()
    private val repository = MugRepository(application)
    val mugs = repository.mugs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedMug = repository.selectedMug.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val presets = repository.selectedPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = repository.globalPreferences.flatMapLatest { preferences ->
        preferences.selectedMugId?.let(repository::sessions) ?: flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val globalPreferences = repository.globalPreferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GlobalPreferences())
    private val mutablePreferences = MutableStateFlow(UserPreferences())
    val preferences = mutablePreferences.asStateFlow()
    private var service: MugConnectionService.LocalBinder? = null
    private var stateJob: Job? = null
    private val pendingCommands = mutableListOf<(MugConnectionService.LocalBinder) -> Unit>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val connected = binder as MugConnectionService.LocalBinder
            service = connected
            stateJob?.cancel()
            stateJob = viewModelScope.launch { connected.state.collect { mutableState.value = it } }
            pendingCommands.toList().also { pendingCommands.clear() }.forEach { it(connected) }
            if (connected.state.value.stage in setOf(ConnectionStage.IDLE, ConnectionStage.ERROR)) connected.connectLast()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            stateJob?.cancel()
            mutableState.value = BleState()
        }
    }

    init {
        viewModelScope.launch {
            repository.migrateLegacyPreferences()
            combine(repository.globalPreferences, repository.selectedMugPreferences) { global, mug ->
                UserPreferences(
                    unit = global.unit,
                    temperatureLed = mug?.ambientTemperatureMode ?: false,
                    ledPalette = MugRepository.parsePalette(mug?.ledPalette),
                )
            }.collect {
                mutablePreferences.value = it
                service?.setAmbientTemperatureMode(it.temperatureLed)
                service?.setTemperatureLedPalette(it.ledPalette)
            }
        }
        application.bindService(Intent(application, MugConnectionService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    private fun withService(command: (MugConnectionService.LocalBinder) -> Unit) {
        service?.let(command) ?: pendingCommands.add(command)
    }

    fun scan() = withService { it.scan() }
    fun connect(device: MugDevice) {
        withService { it.connect(device) }
    }
    fun connectLast(): Boolean {
        if (mutableState.value.stage !in setOf(ConnectionStage.IDLE, ConnectionStage.ERROR)) return false
        if (selectedMug.value == null) return false
        withService { it.connectLast() }
        return true
    }
    fun disconnect() = withService { it.disconnect() }
    fun setTemperature(celsius: Double) = withService { it.setTemperature(celsius) }
    fun setMaintenanceEnabled(enabled: Boolean) {
        if (enabled && mutablePreferences.value.temperatureLed) setTemperatureLed(false)
        withService { it.setMaintenanceEnabled(enabled) }
    }
    fun setGear(gear: Int) = withService { it.setGear(gear) }
    fun setUnit(unit: TemperatureUnit) {
        mutablePreferences.value = mutablePreferences.value.copy(unit = unit)
        viewModelScope.launch { repository.setUnit(unit) }
    }
    fun setTemperatureLed(enabled: Boolean) {
        mutablePreferences.value = mutablePreferences.value.copy(temperatureLed = enabled)
        saveCurrentMugPreferences(mutablePreferences.value)
        withService { it.setAmbientTemperatureMode(enabled) }
    }
    fun setLedColor(index: Int, color: Int) {
        val palette = mutablePreferences.value.ledPalette.toMutableList()
        if (index !in palette.indices) return
        palette[index] = palette[index].copy(color = color and 0xffffff)
        saveLedPalette(palette)
    }
    fun resetLedPalette() = saveLedPalette(MugProtocol.defaultLedPalette)
    fun setSafetyWait(hours: Int) = withService { it.setSafetyWait(hours) }
    fun setMusicMode(mode: Int?) = withService { it.setMusicMode(mode) }
    fun setHoldLight(enabled: Boolean) = withService { it.setHoldLight(enabled) }
    fun setChargeLight(enabled: Boolean) = withService { it.setChargeLight(enabled) }
    fun setSleepTimer(minutes: Int?) = withService { it.setSleepTimer(minutes) }
    private fun saveLedPalette(palette: List<LedColorStop>) {
        mutablePreferences.value = mutablePreferences.value.copy(ledPalette = palette)
        saveCurrentMugPreferences(mutablePreferences.value)
        withService { it.setTemperatureLedPalette(palette) }
    }
    fun refresh() = withService { it.refresh() }

    fun selectMug(id: Long) {
        if (selectedMug.value?.id == id) return
        val mug = mugs.value.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            repository.selectMug(id)
            withService { it.connect(MugDevice(mug.advertisedName, mug.bleAddress, 0)) }
        }
    }

    fun renameMug(id: Long, name: String) = viewModelScope.launch { repository.renameMug(id, name) }

    fun forgetMug(id: Long) {
        if (selectedMug.value?.id == id) disconnect()
        viewModelScope.launch { repository.forgetMug(id) }
    }
    fun clearHistory() {
        if (mutableState.value.stage == ConnectionStage.READY) withService { it.clearActiveHistory() }
        else selectedMug.value?.id?.let { id -> viewModelScope.launch { repository.clearHistory(id) } }
    }
    fun setHistoryRetention(days: Int) = viewModelScope.launch { repository.setHistoryRetention(days); repository.pruneHistory() }

    private fun saveCurrentMugPreferences(preferences: UserPreferences) {
        val mugId = selectedMug.value?.id ?: return
        viewModelScope.launch {
            repository.saveMugPreferences(
                MugPreferencesEntity(mugId, preferences.temperatureLed, MugRepository.encodePalette(preferences.ledPalette)),
            )
        }
    }

    override fun onCleared() {
        getApplication<Application>().unbindService(connection)
    }

}
