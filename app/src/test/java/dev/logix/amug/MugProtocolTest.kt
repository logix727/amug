package dev.logix.amug

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MugProtocolTest {
    @Test fun parsesS6PlusStatus() {
        val packet = byteArrayOf(
            0x03, 0x00, 0x11, 54, 44, 60, 0, 2,
            0xFF.toByte(), 0x66, 0x22, 3, 87,
        )
        val status = MugProtocol.parseS6PlusStatus(packet)!!
        assertTrue(status.heating)
        assertTrue(status.charging)
        assertFalse(status.empty)
        assertEquals(54.44, status.currentC, 0.001)
        assertEquals(60.0, status.targetC, 0.001)
        assertEquals(87, status.batteryPercent)
        assertEquals(0xFF6622, status.lightColor)
    }

    @Test fun parsesPlainS6Status() {
        val packet = byteArrayOf(0x03, 0, 0x01, 55, 5, 50, 0, 55, 0, 60, 0, 2, 3, 4)
        val status = MugProtocol.parseS6Status(packet)!!
        assertEquals(55.5, status.currentC, 0.001)
        assertEquals(55.0, status.targetC, 0.001)
        assertTrue(status.heating)
        assertNull(status.batteryPercent)
    }

    @Test fun encodesTemperatureAndGears() {
        assertArrayEquals(byteArrayOf(0x04, 54, 44), MugProtocol.setTemperature(54.44))
        assertArrayEquals(byteArrayOf(0x06, 3), MugProtocol.setS6PlusGear(3))
        assertArrayEquals(byteArrayOf(0x05, 2), MugProtocol.setS6Gear(2))
        assertArrayEquals(byteArrayOf(0x07, 0x12, 0x34, 0x56, 1), MugProtocol.setNightLight(0x123456, true))
    }

    @Test fun mapsTemperatureToLedGradient() {
        assertEquals(0x2388FF, MugProtocol.temperatureColor(20.0))
        assertEquals(0xF33232, MugProtocol.temperatureColor(66.0))
        assertTrue(MugProtocol.temperatureColor(52.0) != MugProtocol.temperatureColor(35.0))
        val custom = listOf(LedColorStop(0.0, 0x000000), LedColorStop(100.0, 0xFFFFFF))
        assertEquals(0x808080, MugProtocol.temperatureColor(50.0, custom))
    }
}
