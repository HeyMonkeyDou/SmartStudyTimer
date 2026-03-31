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

internal fun DocumentSnapshot.toStudyStatistics(): StudyStatistics {
    val recordMaps = get("records") as? List<*>
    val records = recordMaps
        ?.mapNotNull { item -> (item as? Map<*, *>)?.toDailyStatisticsRecord() }
        .orEmpty()

    return StudyStatistics(
        uid = id,
        snapshotDate = getString("snapshotDate").orEmpty(),
        focusScore = getLong("focusScore") ?: 0,
        todayStudyMinutes = getLong("todayStudyMinutes") ?: 0,
        todayInterruptionCount = getLong("todayInterruptionCount") ?: 0,
        todayInterruptedMinutes = getLong("todayInterruptedMinutes") ?: 0,
        totalCompletedSessions = getLong("totalCompletedSessions") ?: 0,
        thisWeekCompletedSessions = getLong("thisWeekCompletedSessions") ?: 0,
        calendarMonth = getString("calendarMonth").orEmpty(),
        records = records
    )
}

internal fun StudyStatistics.toFirestoreMap(): HashMap<String, Any> {
    return hashMapOf(
        "snapshotDate" to snapshotDate,
        "focusScore" to focusScore,
        "todayStudyMinutes" to todayStudyMinutes,
        "todayInterruptionCount" to todayInterruptionCount,
        "todayInterruptedMinutes" to todayInterruptedMinutes,
        "totalCompletedSessions" to totalCompletedSessions,
        "thisWeekCompletedSessions" to thisWeekCompletedSessions,
        "calendarMonth" to calendarMonth,
        "records" to records.map { it.toFirestoreMap() }
    )
}

internal fun DailyStatisticsRecord.toFirestoreMap(): HashMap<String, Any> {
    return hashMapOf(
        "date" to date,
        "studyMinutes" to studyMinutes,
        "interruptionCount" to interruptionCount,
        "interruptedMinutes" to interruptedMinutes,
        "completedSessions" to completedSessions,
        "focusScore" to focusScore
    )
}

private fun Map<*, *>.toDailyStatisticsRecord(): DailyStatisticsRecord {
    return DailyStatisticsRecord(
        date = this["date"] as? String ?: "",
        studyMinutes = (this["studyMinutes"] as? Number)?.toLong() ?: 0,
        interruptionCount = (this["interruptionCount"] as? Number)?.toLong() ?: 0,
        interruptedMinutes = (this["interruptedMinutes"] as? Number)?.toLong() ?: 0,
        completedSessions = (this["completedSessions"] as? Number)?.toLong() ?: 0,
        focusScore = (this["focusScore"] as? Number)?.toLong() ?: 0
    )
}
