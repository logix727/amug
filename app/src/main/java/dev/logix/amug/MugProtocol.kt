package dev.logix.amug

import java.util.UUID
import kotlin.math.roundToInt

enum class MugProfile(
    val service: UUID,
    val write: UUID,
    val notify: UUID,
    val ota: UUID,
) {
    S6_PLUS(
        UUID.fromString("A3000000-0000-0000-0000-000000000000"),
        UUID.fromString("A3010000-0000-0000-0000-000000000000"),
        UUID.fromString("A3020000-0000-0000-0000-000000000000"),
        UUID.fromString("A3030000-0000-0000-0000-000000000000"),
    ),
    S6(
        UUID.fromString("0000A300-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000A301-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000A302-0000-1000-8000-00805F9B34FB"),
        UUID.fromString("0000A303-0000-1000-8000-00805F9B34FB"),
    );
}

data class MugStatus(
    val maintenanceEnabled: Boolean,
    val empty: Boolean,
    val charging: Boolean,
    val currentC: Double,
    val targetC: Double,
    val batteryPercent: Int?,
    val lightColor: Int,
    val lightMode: Int,
    val safetyWaitHours: Int?,
    val batteryTemperatureMillivolts: Int?,
    val batteryMillivolts: Int?,
    val holdLightMode: Int?,
    val chargeLightMode: Int?,
    val nightLightEnabled: Boolean,
)

data class MugVersion(val firmware: String, val hardware: String?)

data class LedColorStop(val celsius: Double, val color: Int)

object MugProtocol {
    val defaultLedPalette = listOf(
        LedColorStop(20.0, 0x2388FF),
        LedColorStop(35.0, 0x24C8FF),
        LedColorStop(45.0, 0x62D96B),
        LedColorStop(52.0, 0xFFD04A),
        LedColorStop(59.0, 0xFF7A32),
        LedColorStop(66.0, 0xF33232),
    )
    val requestVersion = byteArrayOf(0x02)
    val requestStatus = byteArrayOf(0x03)

    fun parseS6PlusStatus(data: ByteArray): MugStatus? {
        if (data.size < 21 || data[0].u8() != 0x03) return null
        val flags = data[2].u8()
        val battery = data[12].u8().takeIf { it in 0..100 }
        return MugStatus(
            maintenanceEnabled = flags and 0x01 != 0,
            empty = flags and 0x04 != 0,
            charging = flags and 0x10 != 0,
            currentC = data[3].u8() + data[4].u8() / 100.0,
            targetC = data[5].u8() + data[6].u8() / 100.0,
            lightColor = (data[8].u8() shl 16) or (data[9].u8() shl 8) or data[10].u8(),
            lightMode = data[11].u8(),
            batteryPercent = battery,
            safetyWaitHours = data[7].u8().takeUnless { it == 0xff },
            batteryTemperatureMillivolts = (data[14].u8() shl 8) or data[15].u8(),
            batteryMillivolts = (data[16].u8() shl 8) or data[17].u8(),
            holdLightMode = data[18].u8(),
            chargeLightMode = data[19].u8(),
            nightLightEnabled = data[20].u8() == 1,
        )
    }

    fun parseS6Status(data: ByteArray): MugStatus? {
        if (data.size < 14 || data[0].u8() != 0x03) return null
        val flags = data[2].u8()
        val presets = listOf(
            data[5].u8() + data[6].u8() / 10.0,
            data[7].u8() + data[8].u8() / 10.0,
            data[9].u8() + data[10].u8() / 10.0,
        )
        val selectedPreset = data[11].u8().coerceIn(1, 3) - 1
        return MugStatus(
            maintenanceEnabled = flags and 0x01 != 0,
            empty = flags and 0x04 != 0,
            charging = false,
            currentC = data[3].u8() + data[4].u8() / 10.0,
            targetC = presets[selectedPreset],
            batteryPercent = null,
            lightColor = 0,
            lightMode = 0,
            safetyWaitHours = data[13].u8().takeUnless { it == 0xff },
            batteryTemperatureMillivolts = null,
            batteryMillivolts = null,
            holdLightMode = null,
            chargeLightMode = null,
            nightLightEnabled = false,
        )
    }

    fun parseVersion(data: ByteArray): MugVersion? {
        if (data.size < 5 || data[0].u8() != 0x02) return null
        val firmware = "${data[2].u8()}.${data[3].u8()}.${data[4].u8()}"
        val hardware = if (data.size >= 8) "${data[5].u8()}.${data[6].u8()}.${data[7].u8()}" else null
        return MugVersion(firmware, hardware)
    }

    fun setTemperature(celsius: Double): ByteArray {
        val hundredths = (celsius.coerceIn(48.0, 66.0) * 100).roundToInt()
        return byteArrayOf(0x04, (hundredths / 100).toByte(), (hundredths % 100).toByte())
    }

    fun setS6PlusGear(gear: Int) = byteArrayOf(0x06, gear.coerceIn(0, 3).toByte())
    fun setS6Gear(gear: Int) = byteArrayOf(0x05, gear.coerceIn(0, 3).toByte())
    fun setSafetyWait(hours: Int): ByteArray {
        require(hours == 2 || hours == 4)
        return byteArrayOf(0x05, hours.toByte())
    }
    fun setMusicMode(mode: Int): ByteArray {
        require(mode in 0..5)
        return byteArrayOf(0x09, mode.toByte())
    }
    val stopMusic = byteArrayOf(0x09, 0x16)
    fun setHoldLight(enabled: Boolean) = byteArrayOf(0x0B, if (enabled) 1 else 0)
    fun setChargeLight(enabled: Boolean) = byteArrayOf(0x0C, if (enabled) 1 else 0)

    fun setNightLight(color: Int, enabled: Boolean) = byteArrayOf(
        0x07,
        (color shr 16).toByte(),
        (color shr 8).toByte(),
        color.toByte(),
        if (enabled) 1 else 0,
    )

    fun temperatureColor(celsius: Double, stops: List<LedColorStop> = defaultLedPalette): Int {
        require(stops.size >= 2) { "At least two LED color stops are required" }
        val sorted = stops.sortedBy(LedColorStop::celsius)
        val value = celsius.coerceIn(sorted.first().celsius, sorted.last().celsius)
        val upperIndex = sorted.indexOfFirst { value <= it.celsius }.coerceAtLeast(1)
        val low = sorted[upperIndex - 1]
        val high = sorted[upperIndex]
        val ratio = (value - low.celsius) / (high.celsius - low.celsius)
        fun channel(shift: Int): Int {
            val lowChannel = low.color shr shift and 0xff
            val highChannel = high.color shr shift and 0xff
            return (lowChannel + (highChannel - lowChannel) * ratio).roundToInt().coerceIn(0, 255)
        }
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun Byte.u8() = toInt() and 0xff
}
