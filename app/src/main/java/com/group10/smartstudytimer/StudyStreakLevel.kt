package com.group10.smartstudytimer

import android.graphics.Color
import java.time.LocalDate

data class StudyStreakLevel(
    val streakDays: Int,
    val label: String,
    val backgroundColor: Int,
    val textColor: Int
)

object StudyStreakLevels {
    private val orderedLevels = listOf(
        LevelDefinition("Explorer", color("#B0BEC5"), color("#263238")),
        LevelDefinition("Starter", color("#546E7A"), Color.WHITE),
        LevelDefinition("Rookie", color("#1565C0"), Color.WHITE),
        LevelDefinition("Steady", color("#2E7D32"), Color.WHITE),
        LevelDefinition("Pro", color("#00897B"), Color.WHITE),
        LevelDefinition("Master", color("#FF8A00"), Color.WHITE),
        LevelDefinition("Elite", color("#A53DFF"), Color.WHITE),
        LevelDefinition("Legend", color("#5B2BE0"), Color.WHITE)
    )

    fun resolve(
        history: List<DailyScoreHistoryEntry>,
        referenceDate: LocalDate = LocalDate.now()
    ): StudyStreakLevel {
        val dates = history
            .asSequence()
            .mapNotNull { entry -> runCatching { LocalDate.parse(entry.date) }.getOrNull() }
            .toSet()

        if (dates.isEmpty()) {
            return toStudyStreakLevel(0, 0)
        }

        val latestStudyDate = dates.maxOrNull() ?: return toStudyStreakLevel(0, 0)
        val activeStreakDays = calculateActiveStreak(dates, latestStudyDate)
        val consecutiveMissedDays = (referenceDate.toEpochDay() - latestStudyDate.toEpochDay() - 1)
            .coerceAtLeast(0)
            .toInt()

        val currentLevelIndex = levelIndexForStreak(activeStreakDays)
        val decayedLevelIndex = (currentLevelIndex - consecutiveMissedDays).coerceAtLeast(0)

        return toStudyStreakLevel(activeStreakDays, decayedLevelIndex)
    }

    private fun calculateActiveStreak(
        dates: Set<LocalDate>,
        latestStudyDate: LocalDate
    ): Int {
        var streak = 0
        var cursor = latestStudyDate
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun levelIndexForStreak(streakDays: Int): Int {
        return when {
            streakDays >= 60 -> 7
            streakDays >= 30 -> 6
            streakDays >= 14 -> 5
            streakDays >= 7 -> 4
            streakDays >= 4 -> 3
            streakDays >= 2 -> 2
            streakDays >= 1 -> 1
            else -> 0
        }
    }

    private fun toStudyStreakLevel(streakDays: Int, levelIndex: Int): StudyStreakLevel {
        val level = orderedLevels[levelIndex]
        return StudyStreakLevel(
            streakDays = streakDays,
            label = level.label,
            backgroundColor = level.backgroundColor,
            textColor = level.textColor
        )
    }

    private data class LevelDefinition(
        val label: String,
        val backgroundColor: Int,
        val textColor: Int
    )

    private fun color(value: String): Int = Color.parseColor(value)
}
