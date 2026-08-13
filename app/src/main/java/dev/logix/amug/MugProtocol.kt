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
    val heating: Boolean,
    val empty: Boolean,
    val charging: Boolean,
    val currentC: Double,
    val targetC: Double,
    val batteryPercent: Int?,
    val lightColor: Int,
    val lightMode: Int,
)

object MugProtocol {
    val requestVersion = byteArrayOf(0x02)
    val requestStatus = byteArrayOf(0x03)

    fun parseS6PlusStatus(data: ByteArray): MugStatus? {
        if (data.size < 13 || data[0].u8() != 0x03) return null
        val flags = data[2].u8()
        return MugStatus(
            heating = flags and 0x01 != 0,
            empty = flags and 0x04 != 0,
            charging = flags and 0x10 != 0,
            currentC = data[3].u8() + data[4].u8() / 100.0,
            targetC = data[5].u8() + data[6].u8() / 100.0,
            lightColor = (data[8].u8() shl 16) or (data[9].u8() shl 8) or data[10].u8(),
            lightMode = data[11].u8(),
            batteryPercent = data[12].u8().coerceIn(0, 100),
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
            heating = flags and 0x01 != 0,
            empty = flags and 0x04 != 0,
            charging = false,
            currentC = data[3].u8() + data[4].u8() / 10.0,
            targetC = presets[selectedPreset],
            batteryPercent = null,
            lightColor = 0,
            lightMode = 0,
        )
    }

    fun setTemperature(celsius: Double): ByteArray {
        val hundredths = (celsius.coerceIn(48.0, 66.0) * 100).roundToInt()
        return byteArrayOf(0x04, (hundredths / 100).toByte(), (hundredths % 100).toByte())
    }

    fun setS6PlusGear(gear: Int) = byteArrayOf(0x06, gear.coerceIn(0, 3).toByte())
    fun setS6Gear(gear: Int) = byteArrayOf(0x05, gear.coerceIn(0, 3).toByte())

    private fun Byte.u8() = toInt() and 0xff
}
