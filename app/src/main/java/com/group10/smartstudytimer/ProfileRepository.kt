package com.group10.smartstudytimer

import android.content.Context
import java.time.Instant
import java.time.ZoneId

class ProfileRepository(
    context: Context,
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val statisticsRepository: StatisticsRepository = StatisticsRepository.getInstance(context)
) {

    fun loadCurrentProfileHeader(
        onSuccess: (ProfileHeader) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseRepository.ensureAnonymousUser(
            onSuccess = { uid ->
                firebaseRepository.loadUserProfile(
                    uid = uid,
                    onSuccess = { profile ->
                        onSuccess(
                            ProfileHeader(
                                userId = uid,
                                displayName = profile?.displayName.orEmpty(),
                                avatarId = profile?.avatarId ?: "avatar_blue",
                                recentSyncTimeEpochMillis = statisticsRepository.getLastSyncEpochMillis()
                            )
                        )
                    },
                    onError = onError
                )
            },
            onError = onError
        )
    }

    fun getBestStudyRecord(): BestStudyRecord? {
        return getDailyScoreHistory()
            .maxWithOrNull(compareBy<DailyScoreHistoryEntry> { it.score }.thenBy { it.date })
            ?.let { BestStudyRecord(focusScore = it.score, completedAt = it.date) }
    }

    fun loadLeaderboard(
        limit: Long = 10,
        onSuccess: (List<RankedLeaderboardEntry>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseRepository.loadLeaderboard(
            limit = limit,
            onSuccess = { entries ->
                onSuccess(
                    entries.mapIndexed { index, entry ->
                        RankedLeaderboardEntry(
                            rank = index + 1,
                            userId = entry.uid,
                            displayName = entry.displayName,
                            avatarId = entry.avatarId,
                            score = entry.totalFocusMinutes
                        )
                    }
                )
            },
            onError = onError
        )
    }

    fun getDailyScoreHistory(): List<DailyScoreHistoryEntry> {
        val sessions = statisticsRepository.getRecordedSessions()
        if (sessions.isEmpty()) {
            return emptyList()
        }

        val dates = sessions.mapNotNull { session ->
            runCatching {
                Instant.ofEpochMilli(session.endedAtEpochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }.getOrNull()
        }
        if (dates.isEmpty()) {
            return emptyList()
        }

        val startDate = dates.minOrNull() ?: return emptyList()
        val endDate = dates.maxOrNull() ?: return emptyList()

        return generateSequence(startDate) { date ->
            if (date >= endDate) null else date.plusDays(1)
        }
            .map { date ->
                val daily = statisticsRepository.getDailyStatistics(date)
                DailyScoreHistoryEntry(
                    date = daily.date,
                    score = daily.focusScore
                )
            }
            .filter { it.score > 0 }
            .toList()
    }
}
