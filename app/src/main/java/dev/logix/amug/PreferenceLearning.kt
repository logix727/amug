package dev.logix.amug

import java.time.Instant
import java.time.ZoneId

data class TemperatureSuggestion(
    val targetCentiC: Int,
    val uses: Int,
    val distinctDays: Int,
    val dominance: Double,
    val context: String,
)

object PreferenceLearning {
    fun suggest(
        choices: List<TargetChoiceEntity>,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TemperatureSuggestion? {
        if (choices.size < 5) return null
        val currentBucket = bucket(Instant.ofEpochMilli(now).atZone(zoneId).hour)
        val contextual = choices.filter { bucket(it.localHour) == currentBucket }
        val candidates = if (contextual.size >= 5) contextual else choices
        val grouped = candidates.groupBy { (it.targetCentiC / 50) * 50 }
        val winner = grouped.maxByOrNull { it.value.size } ?: return null
        val days = winner.value.map { Instant.ofEpochMilli(it.chosenAt).atZone(zoneId).toLocalDate() }.distinct().size
        val dominance = winner.value.size.toDouble() / candidates.size
        if (winner.value.size < 5 || days < 3 || dominance < .60) return null
        return TemperatureSuggestion(winner.key, winner.value.size, days, dominance, if (contextual.size >= 5) currentBucket else "recently")
    }

    private fun bucket(hour: Int) = when (hour) {
        in 5..10 -> "morning"
        in 11..15 -> "midday"
        in 16..21 -> "evening"
        else -> "night"
    }
}
