package dev.logix.amug

import kotlin.math.abs
import kotlin.math.ceil

data class TelemetryPoint(
    val timestamp: Long,
    val currentC: Double,
    val targetC: Double,
    val maintenanceEnabled: Boolean,
    val empty: Boolean,
)

enum class InsightConfidence { LOW, MEDIUM, HIGH }

data class ThermalInsight(
    val trend: String,
    val slopeCPerMinute: Double?,
    val etaMinutes: IntRange?,
    val confidence: InsightConfidence,
    val explanation: String,
)

object ThermalIntelligence {
    fun analyze(points: List<TelemetryPoint>, now: Long = System.currentTimeMillis()): ThermalInsight? {
        if (points.size < 4) return null
        val recent = points.sortedBy(TelemetryPoint::timestamp).takeLast(10)
        if (now - recent.last().timestamp > 120_000 || recent.any { it.empty }) return null
        if (recent.zipWithNext().any { (a, b) -> b.timestamp <= a.timestamp || b.timestamp - a.timestamp > 150_000 }) return null
        if (recent.maxOf(TelemetryPoint::targetC) - recent.minOf(TelemetryPoint::targetC) >= .25) return null
        val spanMinutes = (recent.last().timestamp - recent.first().timestamp) / 60_000.0
        if (spanMinutes < 2.5) return null

        val slopes = buildList {
            for (i in recent.indices) for (j in i + 1 until recent.size) {
                val minutes = (recent[j].timestamp - recent[i].timestamp) / 60_000.0
                if (minutes > 0) add((recent[j].currentC - recent[i].currentC) / minutes)
            }
        }.sorted()
        if (slopes.isEmpty()) return null
        val slope = median(slopes)
        val consistent = slopes.count { (it >= 0) == (slope >= 0) }.toDouble() / slopes.size
        val confidence = when {
            recent.size >= 8 && spanMinutes >= 6 && consistent >= .8 -> InsightConfidence.HIGH
            recent.size >= 5 && spanMinutes >= 4 && consistent >= .65 -> InsightConfidence.MEDIUM
            else -> InsightConfidence.LOW
        }
        val trend = when {
            abs(slope) < .03 -> "Temperature is steady"
            slope > 0 -> "Warming about ${formatRate(slope)}°C per minute"
            else -> "Cooling about ${formatRate(abs(slope))}°C per minute"
        }
        val latest = recent.last()
        val distance = latest.targetC - latest.currentC
        val towardTarget = latest.maintenanceEnabled && distance > .75 && slope > .03
        val rawEta = if (towardTarget) distance / slope else null
        val eta = rawEta?.takeIf { it in 1.0..30.0 }?.let {
            val spread = when (confidence) { InsightConfidence.HIGH -> .15; InsightConfidence.MEDIUM -> .25; InsightConfidence.LOW -> .4 }
            val low = (it * (1 - spread)).coerceAtLeast(1.0)
            val high = (it * (1 + spread)).coerceAtMost(30.0)
            ceil(low).toInt()..ceil(high).toInt()
        }
        return ThermalInsight(
            trend = trend,
            slopeCPerMinute = slope,
            etaMinutes = eta,
            confidence = confidence,
            explanation = eta?.let { "Target likely in about ${it.first}–${it.last} min" }
                ?: if (abs(distance) <= .75) "At the selected temperature" else "ETA appears after a stable heating trend",
        )
    }

    private fun median(values: List<Double>): Double = if (values.size % 2 == 1) values[values.size / 2]
    else (values[values.size / 2 - 1] + values[values.size / 2]) / 2

    private fun formatRate(value: Double) = "%.1f".format(value)
}
