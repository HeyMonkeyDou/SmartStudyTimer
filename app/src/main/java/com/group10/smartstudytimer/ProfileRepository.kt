package com.group10.smartstudytimer

import android.content.Context
import android.content.Intent

class ProfileRepository(
    context: Context,
    private val firebaseRepository: FirebaseRepository = FirebaseRepository(),
    private val statisticsRepository: StatisticsRepository = StatisticsRepository.getInstance(context)
) {
    private val appContext = context.applicationContext

    fun loadProfileHeader(
        onSuccess: (ProfileHeader) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseRepository.ensureSignedInUser(
            onSuccess = { uid ->
                firebaseRepository.loadUserProfile(
                    uid = uid,
                    onSuccess = { profile ->
                        onSuccess(
                            ProfileHeader(
                                userId = uid,
                                displayName = profile?.displayName.orEmpty(),
                                avatarId = AvatarAssets.resolveAvatarId(
                                    userId = uid,
                                    avatarId = profile?.avatarId
                                ),
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
        return loadDailyScoreHistoryInternal()
            .maxWithOrNull(compareBy<DailyScoreHistoryEntry> { it.score }.thenBy { it.date })
            ?.let { BestStudyRecord(focusScore = it.score, completedAt = it.date) }
    }

    fun loadLeaderboard(
        onSuccess: (List<RankedLeaderboardEntry>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        firebaseRepository.loadLeaderboard(
            limit = 10,
            onSuccess = { entries ->
                onSuccess(
                    entries.mapIndexed { index, entry ->
                        RankedLeaderboardEntry(
                            rank = index + 1,
                            userId = entry.uid,
                            displayName = entry.displayName,
                            username = entry.username,
                            avatarId = AvatarAssets.resolveAvatarId(
                                userId = entry.uid,
                                avatarId = entry.avatarId
                            ),
                            score = entry.bestFocusScore,
                            totalFocusMinutes = entry.totalFocusMinutes
                        )
                    }
                )
            },
            onError = onError
        )
    }

    fun getDailyScoreHistory(): List<DailyScoreHistoryEntry> {
        return loadDailyScoreHistoryInternal()
    }

    fun shareBestRecordImage(
        onSuccess: () -> Unit,
        onNoData: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val bestRecord = getBestStudyRecord()
        if (bestRecord == null) {
            onNoData()
            return
        }

        loadProfileHeader(
            onSuccess = { profileHeader ->
                val resolvedDisplayName = profileHeader.displayName.ifBlank { "Just a User" }
                val shareImageUri = BestRecordShareImageGenerator.generateShareImageUri(
                    context = appContext,
                    displayName = resolvedDisplayName,
                    avatarId = profileHeader.avatarId,
                    bestRecord = bestRecord
                ).getOrElse { error ->
                    onError(Exception(error))
                    return@loadProfileHeader
                }

                val shareText = buildString {
                    appendLine("$resolvedDisplayName's best study record")
                    appendLine("Score: ${bestRecord.focusScore}")
                    append("Completed at: ${bestRecord.completedAt}")
                }

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, shareImageUri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(Intent.createChooser(shareIntent, "Share best record image").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                onSuccess()
            },
            onError = onError
        )
    }

    private fun loadDailyScoreHistoryInternal(): List<DailyScoreHistoryEntry> {
        val sessions = statisticsRepository.getRecordedSessions()
        return StatisticsAggregator.buildDailyScoreRecords(sessions)
            .map { dailyRecord ->
                DailyScoreHistoryEntry(
                    date = dailyRecord.date,
                    score = dailyRecord.focusScore,
                    studyMinutes = dailyRecord.studyMinutes,
                    interruptionCount = dailyRecord.interruptionCount,
                    interruptedSeconds = dailyRecord.interruptedSeconds
                )
            }
            .filter { it.score > 0 }
            .sortedByDescending { it.date }
    }
}
