package dev.logix.amug

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Context.globalDataStore by preferencesDataStore("global_preferences")

data class GlobalPreferences(
    val unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT,
    val selectedMugId: Long? = null,
    val historyRetentionDays: Int = 30,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MugRepository(private val context: Context) {
    private val db = MugDatabase.get(context)
    private val store = context.globalDataStore
    val globalPreferences: Flow<GlobalPreferences> = store.data.map { values ->
        GlobalPreferences(
            unit = runCatching { TemperatureUnit.valueOf(values[UNIT] ?: TemperatureUnit.FAHRENHEIT.name) }.getOrDefault(TemperatureUnit.FAHRENHEIT),
            selectedMugId = values[SELECTED_MUG_ID]?.takeIf { it > 0 },
            historyRetentionDays = values[RETENTION_DAYS] ?: 30,
        )
    }
    val mugs: Flow<List<MugEntity>> = db.mugs().observeAll()
    val selectedMug: Flow<MugEntity?> = globalPreferences.flatMapLatest { prefs ->
        prefs.selectedMugId?.let { id -> mugs.map { list -> list.firstOrNull { it.id == id } } } ?: flowOf(null)
    }
    val selectedMugPreferences: Flow<MugPreferencesEntity?> = globalPreferences.flatMapLatest { prefs ->
        prefs.selectedMugId?.let(db.mugPreferences()::observe) ?: flowOf(null)
    }
    val selectedPresets: Flow<List<PresetEntity>> = globalPreferences.flatMapLatest { prefs ->
        prefs.selectedMugId?.let(db.presets()::observe) ?: flowOf(emptyList())
    }

    suspend fun migrateLegacyPreferences() {
        migrationMutex.withLock {
            if (store.data.first()[LEGACY_MIGRATED] == true) return
            val legacy = context.getSharedPreferences("amug", Context.MODE_PRIVATE)
            val address = legacy.getString("last_address", null)
            val name = legacy.getString("last_name", null) ?: "VSITOO mug"
            val mugId = address?.let { upsertMug(MugDevice(name, it, 0), select = false) }
            if (mugId != null) {
                val palette = parsePalette(legacy.getString("led_palette", null))
                db.mugPreferences().upsert(
                    MugPreferencesEntity(mugId, legacy.getBoolean("ambient_temperature_mode", false), encodePalette(palette)),
                )
            }
            store.edit { values ->
                values[UNIT] = if (legacy.getString("unit", "F") == "C") TemperatureUnit.CELSIUS.name else TemperatureUnit.FAHRENHEIT.name
                if (mugId != null) values[SELECTED_MUG_ID] = mugId
                values[LEGACY_MIGRATED] = true
            }
            // Legacy values intentionally remain available unless every destination write above succeeds.
        }
    }

    suspend fun upsertMug(device: MugDevice, select: Boolean = true): Long {
        val id = db.withTransaction {
        val now = System.currentTimeMillis()
        val address = device.address.uppercase(Locale.US)
        val existing = db.mugs().byAddress(address)
        val id = if (existing == null) {
            db.mugs().insert(MugEntity(bleAddress = address, name = device.name, advertisedName = device.name, createdAt = now, lastSeenAt = now))
                .takeIf { it > 0 } ?: checkNotNull(db.mugs().byAddress(address)).id
        } else {
            db.mugs().update(existing.copy(advertisedName = device.name, lastSeenAt = now))
            existing.id
        }
        db.mugPreferences().upsert(db.mugPreferences().get(id) ?: MugPreferencesEntity(id, ledPalette = encodePalette(MugProtocol.defaultLedPalette)))
        db.presets().insertAll(APPROVED_PRESETS.map { (name, centiC) -> PresetEntity(mugId = id, name = name, temperatureCentiC = centiC, approved = true) })
        id
        }
        if (select) store.edit { it[SELECTED_MUG_ID] = id }
        return id
    }

    suspend fun selectMug(id: Long?) = store.edit { values -> if (id == null) values.remove(SELECTED_MUG_ID) else values[SELECTED_MUG_ID] = id }
    suspend fun renameMug(id: Long, name: String) { name.trim().takeIf(String::isNotEmpty)?.let { db.mugs().rename(id, it) } }
    suspend fun forgetMug(id: Long) {
        db.mugs().delete(id)
        if (globalPreferences.first().selectedMugId == id) selectMug(null)
    }
    suspend fun setUnit(unit: TemperatureUnit) = store.edit { it[UNIT] = unit.name }
    suspend fun setHistoryRetention(days: Int) = store.edit { it[RETENTION_DAYS] = days.coerceIn(1, 3650) }
    suspend fun mugPreferences(mugId: Long) = db.mugPreferences().get(mugId) ?: MugPreferencesEntity(mugId, ledPalette = encodePalette(MugProtocol.defaultLedPalette))
    suspend fun saveMugPreferences(preferences: MugPreferencesEntity) = db.mugPreferences().upsert(preferences)
    fun presets(mugId: Long) = db.presets().observe(mugId)
    suspend fun savePreset(preset: PresetEntity) = db.presets().upsert(preset.copy(temperatureCentiC = preset.temperatureCentiC.coerceIn(4800, 6600)))
    suspend fun deletePreset(id: Long) = db.presets().delete(id)
    suspend fun latestSnapshot(mugId: Long) = db.snapshots().get(mugId)
    fun observeLatestSnapshot(mugId: Long) = db.snapshots().observe(mugId)
    suspend fun saveLatestSnapshot(mugId: Long, status: MugStatus, updatedAt: Long) = db.snapshots().upsert(status.toSnapshot(mugId, updatedAt))
    suspend fun beginSession(mugId: Long, startedAt: Long = System.currentTimeMillis()) = db.sessions().insert(MugSessionEntity(mugId = mugId, startedAt = startedAt))
    suspend fun endSession(id: Long, reason: String, endedAt: Long = System.currentTimeMillis()) = db.sessions().end(id, endedAt, reason)
    suspend fun addSample(sessionId: Long, status: MugStatus, sampledAt: Long) = db.samples().insert(status.toSample(sessionId, sampledAt))
    fun sessions(mugId: Long) = db.sessions().observe(mugId)
    fun samples(sessionId: Long) = db.samples().observe(sessionId)
    suspend fun clearHistory(mugId: Long) = db.sessions().clear(mugId)
    suspend fun pruneHistory(now: Long = System.currentTimeMillis()) {
        val days = globalPreferences.first().historyRetentionDays
        db.sessions().prune(now - days * 86_400_000L)
    }
    suspend fun closeAbandonedSessions(now: Long = System.currentTimeMillis()) = db.sessions().closeAbandoned(now, "process stopped")

    suspend fun selectedMugNow(): MugEntity? = globalPreferences.first().selectedMugId?.let { db.mugs().byId(it) }

    companion object {
        private val UNIT = stringPreferencesKey("temperature_unit")
        private val SELECTED_MUG_ID = longPreferencesKey("selected_mug_id")
        private val RETENTION_DAYS = intPreferencesKey("history_retention_days")
        private val LEGACY_MIGRATED = booleanPreferencesKey("legacy_preferences_migrated_v1")
        private val migrationMutex = Mutex()
        val APPROVED_PRESETS = listOf(
            "Green tea" to 5200, "White tea" to 5200,
            "Oolong" to 5400, "Cocoa" to 5400,
            "Coffee" to 5700, "Espresso" to 5700, "Latte" to 5700, "Black tea" to 5700, "Herbal" to 5700,
            "Hot" to 6000,
        )

        fun encodePalette(palette: List<LedColorStop>) = palette.joinToString(",") { "%06X".format(it.color and 0xffffff) }
        fun parsePalette(value: String?): List<LedColorStop> {
            val colors = value?.split(",")?.mapNotNull { it.toIntOrNull(16) }
            return if (colors?.size == MugProtocol.defaultLedPalette.size) MugProtocol.defaultLedPalette.mapIndexed { index, stop -> stop.copy(color = colors[index]) }
            else MugProtocol.defaultLedPalette
        }
    }
}

private fun MugStatus.toSnapshot(mugId: Long, updatedAt: Long) = LatestSnapshotEntity(
    mugId, (currentC * 100).roundToInt(), (targetC * 100).roundToInt(), batteryPercent,
    maintenanceEnabled, empty, charging, updatedAt,
)

private fun MugStatus.toSample(sessionId: Long, sampledAt: Long) = SessionSampleEntity(
    sessionId = sessionId, sampledAt = sampledAt,
    currentCentiC = (currentC * 100).roundToInt(), targetCentiC = (targetC * 100).roundToInt(),
    batteryPercent = batteryPercent, maintenanceEnabled = maintenanceEnabled, empty = empty, charging = charging,
)

fun SessionSampleEntity.materiallyDiffers(status: MugStatus): Boolean =
    abs(currentCentiC - (status.currentC * 100).roundToInt()) >= 25 ||
        abs(targetCentiC - (status.targetC * 100).roundToInt()) >= 25 ||
        batteryPercent != status.batteryPercent || maintenanceEnabled != status.maintenanceEnabled || empty != status.empty || charging != status.charging
