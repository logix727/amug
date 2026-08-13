package dev.logix.amug

import android.content.Context
import kotlinx.coroutines.flow.first

data class MugSnapshot(
    val name: String?,
    val address: String?,
    val currentC: Double?,
    val targetC: Double?,
    val batteryPercent: Int?,
    val maintenanceEnabled: Boolean,
    val empty: Boolean,
    val connected: Boolean,
    val updatedAt: Long?,
    val unit: TemperatureUnit,
)

class MugSnapshotStore(context: Context) {
    private val repository = MugRepository(context.applicationContext)

    fun read(): MugSnapshot = kotlinx.coroutines.runBlocking {
        repository.migrateLegacyPreferences()
        val global = repository.globalPreferences.first()
        val mug = repository.selectedMugNow()
        val snapshot = mug?.let { repository.latestSnapshot(it.id) }
        MugSnapshot(
            name = mug?.name,
            address = mug?.bleAddress,
            currentC = snapshot?.currentCentiC?.div(100.0),
            targetC = snapshot?.targetCentiC?.div(100.0),
            batteryPercent = snapshot?.batteryPercent,
            maintenanceEnabled = snapshot?.maintenanceEnabled ?: false,
            empty = snapshot?.empty ?: false,
            connected = mug?.id == LiveMugConnection.mugId,
            updatedAt = snapshot?.updatedAt,
            unit = global.unit,
        )
    }
}

object LiveMugConnection {
    @Volatile var mugId: Long? = null
}
