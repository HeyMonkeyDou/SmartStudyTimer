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
    fun resolve(
        history: List<DailyScoreHistoryEntry>,
        referenceDate: LocalDate = LocalDate.now()
    ): StudyStreakLevel {
        val streakDays = calculateActiveStreak(history, referenceDate)
        return when {
            streakDays >= 60 -> StudyStreakLevel(streakDays, "Legend", color("#5B2BE0"), Color.WHITE)
            streakDays >= 30 -> StudyStreakLevel(streakDays, "Elite", color("#A53DFF"), Color.WHITE)
            streakDays >= 14 -> StudyStreakLevel(streakDays, "Master", color("#FF8A00"), Color.WHITE)
            streakDays >= 7 -> StudyStreakLevel(streakDays, "Pro", color("#00897B"), Color.WHITE)
            streakDays >= 4 -> StudyStreakLevel(streakDays, "Steady", color("#2E7D32"), Color.WHITE)
            streakDays >= 2 -> StudyStreakLevel(streakDays, "Rookie", color("#1565C0"), Color.WHITE)
            streakDays >= 1 -> StudyStreakLevel(streakDays, "Starter", color("#546E7A"), Color.WHITE)
            else -> StudyStreakLevel(streakDays, "Explorer", color("#B0BEC5"), color("#263238"))
        }
    }

    private fun calculateActiveStreak(
        history: List<DailyScoreHistoryEntry>,
        referenceDate: LocalDate
    ): Int {
        val dates = history
            .asSequence()
            .mapNotNull { entry -> runCatching { LocalDate.parse(entry.date) }.getOrNull() }
            .toSet()

        if (dates.isEmpty()) {
            return 0
        }

        val latestStudyDate = dates.maxOrNull() ?: return 0
        if (latestStudyDate.isBefore(referenceDate.minusDays(1))) {
            return 0
        }

        var streak = 0
        var cursor = latestStudyDate
        while (cursor in dates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun color(value: String): Int = Color.parseColor(value)
}
