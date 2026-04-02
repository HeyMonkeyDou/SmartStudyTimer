package com.group10.smartstudytimer

import com.google.firebase.firestore.DocumentSnapshot
import java.time.LocalDate

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val totalFocusMinutes: Long = 0,
    val avatarId: String = "avatar_blue"
)

data class LeaderboardEntry(
    val uid: String = "",
    val displayName: String = "",
    val totalFocusMinutes: Long = 0,
    val avatarId: String = "avatar_blue"
)

data class DailyStatisticsRecord(
    val date: String = "",
    val studyMinutes: Long = 0,
    val interruptionCount: Long = 0,
    val interruptedMinutes: Long = 0,
    val completedSessions: Long = 0,
    val focusScore: Long = 0
)

data class StudyStatistics(
    val uid: String = "",
    val snapshotDate: String = LocalDate.now().toString(),
    val focusScore: Long = 0,
    val todayStudyMinutes: Long = 0,
    val todayInterruptionCount: Long = 0,
    val todayInterruptedMinutes: Long = 0,
    val totalCompletedSessions: Long = 0,
    val thisWeekCompletedSessions: Long = 0,
    val calendarMonth: String = "",
    val records: List<DailyStatisticsRecord> = emptyList()
)

internal fun DocumentSnapshot.toUserProfile(): UserProfile {
    return UserProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        totalFocusMinutes = getLong("totalFocusMinutes") ?: 0,
        avatarId = getString("avatarId") ?: "avatar_blue"
    )
}

internal fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry {
    return LeaderboardEntry(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        totalFocusMinutes = getLong("totalFocusMinutes") ?: 0,
        avatarId = getString("avatarId") ?: "avatar_blue"
    )
}

internal fun StudySessionRecord.toFirestoreMap(): HashMap<String, Any> {
    return hashMapOf(
        "sessionId" to sessionId,
        "endedAtEpochMillis" to endedAtEpochMillis,
        "studyMinutes" to studyMinutes,
        "interruptionCount" to interruptionCount,
        "interruptedMinutes" to interruptedMinutes,
        "completedSessions" to completedSessions,
        "status" to status.name,
        "mode" to mode.name,
        "note" to note
    )
}

internal fun DocumentSnapshot.toStudySessionRecord(): StudySessionRecord {
    return StudySessionRecord(
        sessionId = getString("sessionId").orEmpty().ifBlank { id },
        endedAtEpochMillis = getLong("endedAtEpochMillis") ?: 0,
        studyMinutes = getLong("studyMinutes") ?: 0,
        interruptionCount = getLong("interruptionCount") ?: 0,
        interruptedMinutes = getLong("interruptedMinutes") ?: 0,
        completedSessions = getLong("completedSessions") ?: 0,
        status = runCatching {
            StudySessionStatus.valueOf(getString("status") ?: StudySessionStatus.COMPLETED.name)
        }.getOrDefault(StudySessionStatus.COMPLETED),
        mode = runCatching {
            StudySessionMode.valueOf(getString("mode") ?: StudySessionMode.NORMAL.name)
        }.getOrDefault(StudySessionMode.NORMAL),
        note = getString("note").orEmpty()
    )
}
