package dev.logix.amug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MugPersistenceTest {
    @Test
    fun approvedPresetsUseCanonicalCentiCelsius() {
        assertEquals(
            listOf(5200, 5200, 5400, 5400, 5700, 5700, 5700, 5700, 5700, 6000),
            MugRepository.APPROVED_PRESETS.map { it.second },
        )
    }

    @Test
    fun samplePolicyDetectsMaterialChanges() {
        val sample = SessionSampleEntity(1, 2, 1_000, 5_700, 5_700, 80, true, false, false)
        val unchanged = status(currentC = 57.24)
        val changed = status(currentC = 57.25)

        assertFalse(sample.materiallyDiffers(unchanged))
        assertTrue(sample.materiallyDiffers(changed))
    }

    private fun status(currentC: Double) = MugStatus(
        maintenanceEnabled = true, empty = false, charging = false,
        currentC = currentC, targetC = 57.0, batteryPercent = 80,
        lightColor = 0, lightMode = 0, safetyWaitHours = null,
        batteryTemperatureMillivolts = null, batteryMillivolts = null,
        holdLightMode = null, chargeLightMode = null, nightLightEnabled = false,
    )
}
