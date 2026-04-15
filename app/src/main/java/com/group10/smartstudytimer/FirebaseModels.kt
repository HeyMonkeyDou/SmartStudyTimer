package com.group10.smartstudytimer

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.Timestamp
import java.time.LocalDate
import java.util.Date

data class UserProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val username: String = "",
    val totalFocusMinutes: Long = 0,
    val avatarId: String = "",
    val bestFocusScore: Long = 0,
    val bestFocusScoreCompletedAt: String = "",
    val todayFocusScore: Long = 0,
    val todayFocusScoreDate: String = "",
    val todayStudyMinutes: Long = 0,
    val updatedAtEpochMillis: Long? = null
)

data class LeaderboardEntry(
    val uid: String = "",
    val displayName: String = "",
    val username: String = "",
    val bestFocusScore: Long = 0,
    val todayFocusScore: Long = 0,
    val todayFocusScoreDate: String = "",
    val totalFocusMinutes: Long = 0,
    val todayStudyMinutes: Long = 0,
    val bestFocusScoreCompletedAt: String = "",
    val avatarId: String = "",
    val updatedAtEpochMillis: Long? = null
)

data class ProfileHeader(
    val userId: String,
    val displayName: String,
    val avatarId: String,
    val recentSyncTimeEpochMillis: Long?
)

data class BestStudyRecord(
    val focusScore: Long,
    val completedAt: String
)

data class RankedLeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val username: String,
    val avatarId: String,
    val score: Long,
    val studyMinutes: Long
)

data class DailyScoreHistoryEntry(
    val date: String,
    val score: Long,
    val studyMinutes: Long,
    val interruptionCount: Long,
    val interruptedSeconds: Long
)

data class DailyStatisticsRecord(
    val date: String = "",
    val studyMinutes: Long = 0,
    val interruptionCount: Long = 0,
    val interruptedSeconds: Long = 0,
    val completedSessions: Long = 0,
    val focusScore: Long = 0
)

data class StudyStatistics(
    val uid: String = "",
    val snapshotDate: String = LocalDate.now().toString(),
    val focusScore: Long = 0,
    val todayStudyMinutes: Long = 0,
    val todayInterruptionCount: Long = 0,
    val todayInterruptedSeconds: Long = 0,
    val totalCompletedSessions: Long = 0,
    val thisWeekCompletedSessions: Long = 0,
    val calendarMonth: String = "",
    val records: List<DailyStatisticsRecord> = emptyList()
)

internal fun DocumentSnapshot.toUserProfile(): UserProfile {
    return UserProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        email = getString("email").orEmpty(),
        username = getString("username").orEmpty(),
        totalFocusMinutes = getLong("totalFocusMinutes") ?: 0,
        avatarId = AvatarAssets.resolveAvatarId(
            userId = id,
            avatarId = getString("avatarId")
        ),
        bestFocusScore = getLong("bestFocusScore") ?: 0,
        bestFocusScoreCompletedAt = getString("bestFocusScoreCompletedAt").orEmpty(),
        todayFocusScore = getLong("todayFocusScore") ?: 0,
        todayFocusScoreDate = getString("todayFocusScoreDate").orEmpty(),
        todayStudyMinutes = getLong("todayStudyMinutes") ?: 0,
        updatedAtEpochMillis = getTimestamp("updatedAt")?.toDate()?.time
    )
}

internal fun DocumentSnapshot.toLeaderboardEntry(): LeaderboardEntry {
    return LeaderboardEntry(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        username = getString("username").orEmpty(),
        bestFocusScore = getLong("bestFocusScore") ?: 0,
        todayFocusScore = getLong("todayFocusScore") ?: 0,
        todayFocusScoreDate = getString("todayFocusScoreDate").orEmpty(),
        totalFocusMinutes = getLong("totalFocusMinutes") ?: 0,
        todayStudyMinutes = getLong("todayStudyMinutes") ?: 0,
        bestFocusScoreCompletedAt = getString("bestFocusScoreCompletedAt").orEmpty(),
        avatarId = AvatarAssets.resolveAvatarId(
            userId = id,
            avatarId = getString("avatarId")
        ),
        updatedAtEpochMillis = getTimestamp("updatedAt")?.toDate()?.time
    )
}

internal fun StudySessionRecord.toFirestoreMap(): HashMap<String, Any> {
    return hashMapOf(
        "sessionId" to sessionId,
        "endedAtEpochMillis" to endedAtEpochMillis,
        "endedAtTimestamp" to Timestamp(Date(endedAtEpochMillis)),
        "studyMinutes" to studyMinutes,
        "interruptionCount" to interruptionCount,
        "interruptedSeconds" to interruptedSeconds,
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
        interruptedSeconds = getLong("interruptedSeconds")
            ?: ((getLong("interruptedMinutes") ?: 0) * 60),
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

data class FriendRequest(
    val requestId: String = "",
    val fromUid: String = "",
    val fromDisplayName: String = "",
    val fromEmail: String = "",
    val toUid: String = "",
    val toEmail: String = "",
    val status: String = "", // pending / accepted
    val createdAtEpochMillis: Long = 0
)

data class FriendProfile(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val username: String = "",
    val avatarId: String = "",
    val bestFocusScore: Long = 0,
    val totalFocusMinutes: Long = 0,
    val todayFocusScore: Long = 0,
    val todayFocusScoreDate: String = "",
    val todayStudyMinutes: Long = 0,
    val nickname: String = ""
)

data class ChatMessage(
    val messageId: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = 0
)

// ── Study Room ──────────────────────────────────────────────────────────────

data class StudyRoom(
    val roomId: String = "",
    val hostUid: String = "",
    val status: String = "WAITING",       // WAITING / COUNTDOWN / ACTIVE / COMPLETED
    val countdownStartAt: Long = 0,
    val sessionStartAt: Long = 0,
    val endedAt: Long = 0,
    val failedCount: Int = 0
)

data class RoomParticipant(
    val uid: String = "",
    val displayName: String = "",
    val avatarId: String = "",
    val status: String = "JOINED",        // JOINED / READY / STUDYING / COMPLETED / FAILED
    val distractionCount: Int = 0,
    val joinedAt: Long = 0
)

data class RoomInvite(
    val inviteId: String = "",
    val roomId: String = "",
    val fromUid: String = "",
    val fromDisplayName: String = "",
    val createdAt: Long = 0
)

internal fun DocumentSnapshot.toStudyRoom(): StudyRoom = StudyRoom(
    roomId = getString("roomId").orEmpty().ifBlank { id },
    hostUid = getString("hostUid").orEmpty(),
    status = getString("status") ?: "WAITING",
    countdownStartAt = getLong("countdownStartAt") ?: 0L,
    sessionStartAt = getLong("sessionStartAt") ?: 0L,
    endedAt = getLong("endedAt") ?: 0L,
    failedCount = (getLong("failedCount") ?: 0L).toInt()
)

internal fun DocumentSnapshot.toRoomParticipant(): RoomParticipant = RoomParticipant(
    uid = getString("uid").orEmpty().ifBlank { id },
    displayName = getString("displayName").orEmpty(),
    avatarId = getString("avatarId").orEmpty(),
    status = getString("status") ?: "JOINED",
    distractionCount = (getLong("distractionCount") ?: 0L).toInt(),
    joinedAt = getLong("joinedAt") ?: 0L
)

internal fun DocumentSnapshot.toRoomInvite(): RoomInvite = RoomInvite(
    inviteId = getString("inviteId").orEmpty().ifBlank { id },
    roomId = getString("roomId").orEmpty(),
    fromUid = getString("fromUid").orEmpty(),
    fromDisplayName = getString("fromDisplayName").orEmpty(),
    createdAt = getLong("createdAt") ?: 0L
)
