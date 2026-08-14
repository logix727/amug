package dev.logix.amug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalIntelligenceTest {
    @Test fun estimatesHeatingEtaFromRobustSlope() {
        val start = 1_000_000L
        val points = (0..6).map { minute ->
            TelemetryPoint(start + minute * 60_000L, 50.0 + minute, 60.0, true, false)
        }.toMutableList()
        points[3] = points[3].copy(currentC = 57.0) // outlier
        val insight = ThermalIntelligence.analyze(points, start + 6 * 60_000L)
        assertNotNull(insight)
        assertTrue(insight!!.trend.startsWith("Warming"))
        assertNotNull(insight.etaMinutes)
        assertTrue(insight.etaMinutes!!.first in 2..6)
    }

    @Test fun suppressesStaleEmptyAndChangingTargetData() {
        val start = 1_000_000L
        val base = (0..5).map { minute -> TelemetryPoint(start + minute * 60_000L, 50.0 + minute, 60.0, true, false) }
        assertNull(ThermalIntelligence.analyze(base, start + 10 * 60_000L))
        assertNull(ThermalIntelligence.analyze(base.mapIndexed { i, p -> if (i == 3) p.copy(empty = true) else p }, start + 5 * 60_000L))
        assertNull(ThermalIntelligence.analyze(base.mapIndexed { i, p -> if (i > 2) p.copy(targetC = 61.0) else p }, start + 5 * 60_000L))
    }

    @Test fun labelsFlatDataSteady() {
        val start = 1_000_000L
        val points = (0..5).map { minute -> TelemetryPoint(start + minute * 60_000L, 55.0 + (minute % 2) * .01, 55.0, true, false) }
        assertEquals("Temperature is steady", ThermalIntelligence.analyze(points, start + 5 * 60_000L)?.trend)
    }
}
