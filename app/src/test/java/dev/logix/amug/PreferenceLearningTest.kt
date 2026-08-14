package dev.logix.amug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PreferenceLearningTest {
    private val zone = ZoneId.of("UTC")

    @Test fun learnsDominantMorningTemperatureAcrossDays() {
        val choices = buildList {
            repeat(5) { day ->
                val time = ZonedDateTime.of(2026, 8, 1 + day, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
                add(TargetChoiceEntity(mugId = 1, targetCentiC = 5700, source = "manual", presetName = null, chosenAt = time, localHour = 8))
            }
            add(TargetChoiceEntity(mugId = 1, targetCentiC = 5400, source = "manual", presetName = null, chosenAt = ZonedDateTime.of(2026, 8, 6, 8, 0, 0, 0, zone).toInstant().toEpochMilli(), localHour = 8))
        }
        val now = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val suggestion = PreferenceLearning.suggest(choices, now, zone)
        assertNotNull(suggestion)
        assertEquals(5700, suggestion!!.targetCentiC)
        assertEquals("morning", suggestion.context)
    }

    @Test fun suppressesWeakOrSingleDayPatterns() {
        val sameDay = (0 until 6).map { index ->
            TargetChoiceEntity(mugId = 1, targetCentiC = 5700, source = "manual", presetName = null, chosenAt = 1_000_000L + index, localHour = 8)
        }
        assertNull(PreferenceLearning.suggest(sameDay, 2_000_000L, zone))
        val split = (0 until 10).map { index ->
            TargetChoiceEntity(mugId = 1, targetCentiC = if (index % 2 == 0) 5700 else 5400, source = "manual", presetName = null, chosenAt = 86_400_000L * index, localHour = 8)
        }
        assertNull(PreferenceLearning.suggest(split, 86_400_000L * 11, zone))
    }
}
