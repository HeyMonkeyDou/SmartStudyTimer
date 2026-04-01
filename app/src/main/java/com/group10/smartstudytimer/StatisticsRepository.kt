package com.group10.smartstudytimer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

enum class StudySessionStatus {
    COMPLETED,
    FAILED,
    ABANDONED
}

enum class StudySessionMode {
    NORMAL,
    POMODORO
}

data class StudySessionRecord(
    val sessionId: String = UUID.randomUUID().toString(),
    val endedAtEpochMillis: Long = System.currentTimeMillis(),
    val studyMinutes: Long = 0,
    val interruptionCount: Long = 0,
    val interruptedMinutes: Long = 0,
    val completedSessions: Long = 0,
    val status: StudySessionStatus = StudySessionStatus.COMPLETED,
    val mode: StudySessionMode = StudySessionMode.NORMAL,
    val note: String = ""
)

/**
 * Timer teammate entry points.
 * Call one of the record methods when a study session ends or fails.
 */
interface StatisticsRecorder {
    fun recordSession(record: StudySessionRecord)

    fun recordCompletedSession(
        studyMinutes: Long,
        interruptionCount: Long = 0,
        interruptedMinutes: Long = 0,
        completedAtEpochMillis: Long = System.currentTimeMillis(),
        mode: StudySessionMode = StudySessionMode.NORMAL,
        completedSessions: Long = 1,
        note: String = ""
    )

    fun recordFailedSession(
        studyMinutes: Long,
        interruptionCount: Long = 0,
        interruptedMinutes: Long = 0,
        endedAtEpochMillis: Long = System.currentTimeMillis(),
        mode: StudySessionMode = StudySessionMode.NORMAL,
        note: String = ""
    )

    fun clearAllLocalStatistics()
}

/**
 * Statistics UI teammate entry points.
 * These methods return aggregated data for the Statistics screen.
 */
interface StatisticsReader {
    fun getStatisticsSnapshot(
        snapshotDate: LocalDate = LocalDate.now(),
        month: YearMonth = YearMonth.from(snapshotDate)
    ): StudyStatistics

    fun getDailyStatistics(date: LocalDate): DailyStatisticsRecord

    fun getMonthlyStatistics(month: YearMonth): List<DailyStatisticsRecord>

    fun getRecordedSessions(): List<StudySessionRecord>
}

class StatisticsRepository(
    context: Context,
    private val firebaseRepository: FirebaseRepository = FirebaseRepository()
) : StatisticsRecorder, StatisticsReader {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun recordSession(record: StudySessionRecord) {
        val sessions = getRecordedSessions().toMutableList()
        sessions.add(record)
        saveSessions(sessions, syncMode = SessionSyncMode.ADD_ONE, addedSession = record)
    }

    override fun recordCompletedSession(
        studyMinutes: Long,
        interruptionCount: Long,
        interruptedMinutes: Long,
        completedAtEpochMillis: Long,
        mode: StudySessionMode,
        completedSessions: Long,
        note: String
    ) {
        recordSession(
            StudySessionRecord(
                endedAtEpochMillis = completedAtEpochMillis,
                studyMinutes = studyMinutes,
                interruptionCount = interruptionCount,
                interruptedMinutes = interruptedMinutes,
                completedSessions = completedSessions,
                status = StudySessionStatus.COMPLETED,
                mode = mode,
                note = note
            )
        )
    }

    override fun recordFailedSession(
        studyMinutes: Long,
        interruptionCount: Long,
        interruptedMinutes: Long,
        endedAtEpochMillis: Long,
        mode: StudySessionMode,
        note: String
    ) {
        recordSession(
            StudySessionRecord(
                endedAtEpochMillis = endedAtEpochMillis,
                studyMinutes = studyMinutes,
                interruptionCount = interruptionCount,
                interruptedMinutes = interruptedMinutes,
                completedSessions = 0,
                status = StudySessionStatus.FAILED,
                mode = mode,
                note = note
            )
        )
    }

    override fun clearAllLocalStatistics() {
        preferences.edit().remove(SESSIONS_KEY).apply()
    }

    override fun getStatisticsSnapshot(
        snapshotDate: LocalDate,
        month: YearMonth
    ): StudyStatistics {
        val sessions = getRecordedSessions()
        return StatisticsAggregator.buildStatisticsSnapshot(
            sessions = sessions,
            snapshotDate = snapshotDate,
            month = month
        )
    }

    override fun getDailyStatistics(date: LocalDate): DailyStatisticsRecord {
        val sessions = getRecordedSessions()
        return StatisticsAggregator.buildDailyStatistics(sessions, date)
    }

    override fun getMonthlyStatistics(month: YearMonth): List<DailyStatisticsRecord> {
        val sessions = getRecordedSessions()
        return StatisticsAggregator.buildMonthRecords(sessions, month)
    }

    override fun getRecordedSessions(): List<StudySessionRecord> {
        val rawValue = preferences.getString(SESSIONS_KEY, null).orEmpty()
        if (rawValue.isBlank()) {
            return emptyList()
        }

        val array = JSONArray(rawValue)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val jsonObject = array.optJSONObject(index) ?: continue
                add(jsonObject.toStudySessionRecord())
            }
        }.sortedBy { it.endedAtEpochMillis }
    }

    private fun saveSessions(sessions: List<StudySessionRecord>) {
        saveSessions(sessions, syncMode = SessionSyncMode.REPLACE_ALL)
    }

    fun syncLocalSessionsFromFirebase(
        onSuccess: (() -> Unit)? = null,
        onError: ((Exception) -> Unit)? = null
    ) {
        firebaseRepository.loadCurrentStudySessions(
            onSuccess = { sessions ->
                val syncedSessions = sessions.orEmpty().sortedBy { it.endedAtEpochMillis }
                saveSessions(syncedSessions, syncMode = SessionSyncMode.NONE)
                onSuccess?.invoke()
            },
            onError = { error ->
                onError?.invoke(error)
            }
        )
    }

    private fun saveSessions(
        sessions: List<StudySessionRecord>,
        syncMode: SessionSyncMode,
        addedSession: StudySessionRecord? = null
    ) {
        val jsonArray = JSONArray()
        sessions.forEach { session ->
            jsonArray.put(session.toJson())
        }
        preferences.edit().putString(SESSIONS_KEY, jsonArray.toString()).apply()
        when (syncMode) {
            SessionSyncMode.NONE -> Unit
            SessionSyncMode.REPLACE_ALL -> syncSessionsToFirebase(sessions)
            SessionSyncMode.ADD_ONE -> {
                val session = addedSession ?: return
                syncSessionToFirebase(session)
            }
        }
    }

    private fun syncSessionsToFirebase(sessions: List<StudySessionRecord>) {
        firebaseRepository.saveCurrentStudySessions(
            sessions = sessions,
            onSuccess = {},
            onError = {}
        )
    }

    private fun syncSessionToFirebase(session: StudySessionRecord) {
        firebaseRepository.addCurrentStudySession(
            session = session,
            onSuccess = {},
            onError = {}
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "statistics_repository"
        private const val SESSIONS_KEY = "statistics.sessions"
        @Volatile private var instance: StatisticsRepository? = null

        fun getInstance(context: Context): StatisticsRepository {
            return instance ?: synchronized(this) {
                instance ?: StatisticsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

private enum class SessionSyncMode {
    NONE,
    REPLACE_ALL,
    ADD_ONE
}

object StatisticsAggregator {

    fun buildStatisticsSnapshot(
        sessions: List<StudySessionRecord>,
        snapshotDate: LocalDate,
        month: YearMonth
    ): StudyStatistics {
        val todayRecord = buildDailyStatistics(sessions, snapshotDate)
        val monthRecords = buildMonthRecords(sessions, month)
        val completedSessions = sessions.sumOf { it.completedSessions }
        val thisWeekCompletedSessions = sessions
            .filter { it.toLocalDate() != null && isSameWeek(it.toLocalDate()!!, snapshotDate) }
            .sumOf { it.completedSessions }

        return StudyStatistics(
            snapshotDate = snapshotDate.toString(),
            focusScore = todayRecord.focusScore,
            todayStudyMinutes = todayRecord.studyMinutes,
            todayInterruptionCount = todayRecord.interruptionCount,
            todayInterruptedMinutes = todayRecord.interruptedMinutes,
            totalCompletedSessions = completedSessions,
            thisWeekCompletedSessions = thisWeekCompletedSessions,
            calendarMonth = month.toMonthLabel(),
            records = monthRecords
        )
    }

    fun buildDailyStatistics(
        sessions: List<StudySessionRecord>,
        date: LocalDate
    ): DailyStatisticsRecord {
        val dailySessions = sessions.filter { it.toLocalDate() == date }
        val studyMinutes = dailySessions.sumOf { it.studyMinutes }
        val interruptionCount = dailySessions.sumOf { it.interruptionCount }
        val interruptedMinutes = dailySessions.sumOf { it.interruptedMinutes }
        val completedSessions = dailySessions.sumOf { it.completedSessions }

        return DailyStatisticsRecord(
            date = date.toString(),
            studyMinutes = studyMinutes,
            interruptionCount = interruptionCount,
            interruptedMinutes = interruptedMinutes,
            completedSessions = completedSessions,
            focusScore = calculateFocusScore(
                studyMinutes = studyMinutes,
                interruptionCount = interruptionCount,
                interruptedMinutes = interruptedMinutes,
                completedSessions = completedSessions
            )
        )
    }

    fun buildMonthRecords(
        sessions: List<StudySessionRecord>,
        month: YearMonth
    ): List<DailyStatisticsRecord> {
        return (1..month.lengthOfMonth())
            .map { day -> buildDailyStatistics(sessions, month.atDay(day)) }
            .filter { record ->
                record.studyMinutes > 0 ||
                    record.interruptionCount > 0 ||
                    record.interruptedMinutes > 0 ||
                    record.completedSessions > 0
            }
    }

    fun calculateFocusScore(
        studyMinutes: Long,
        interruptionCount: Long,
        interruptedMinutes: Long,
        completedSessions: Long
    ): Long {
        if (studyMinutes <= 0 && completedSessions <= 0) {
            return 0
        }

        val score = 50L +
            minOf(30L, studyMinutes / 2) +
            minOf(20L, completedSessions * 10) -
            minOf(20L, interruptionCount * 5) -
            minOf(20L, interruptedMinutes)

        return score.coerceIn(0L, 100L)
    }

    private fun isSameWeek(date: LocalDate, reference: LocalDate): Boolean {
        val weekFields = WeekFields.of(Locale.getDefault())
        return date.year == reference.year &&
            date.get(weekFields.weekOfWeekBasedYear()) == reference.get(weekFields.weekOfWeekBasedYear())
    }
}

private fun StudySessionRecord.toJson(): JSONObject {
    return JSONObject()
        .put("sessionId", sessionId)
        .put("endedAtEpochMillis", endedAtEpochMillis)
        .put("studyMinutes", studyMinutes)
        .put("interruptionCount", interruptionCount)
        .put("interruptedMinutes", interruptedMinutes)
        .put("completedSessions", completedSessions)
        .put("status", status.name)
        .put("mode", mode.name)
        .put("note", note)
}

private fun JSONObject.toStudySessionRecord(): StudySessionRecord {
    return StudySessionRecord(
        sessionId = optString("sessionId", UUID.randomUUID().toString()),
        endedAtEpochMillis = optLong("endedAtEpochMillis", System.currentTimeMillis()),
        studyMinutes = optLong("studyMinutes", 0),
        interruptionCount = optLong("interruptionCount", 0),
        interruptedMinutes = optLong("interruptedMinutes", 0),
        completedSessions = optLong("completedSessions", 0),
        status = StudySessionStatus.valueOf(optString("status", StudySessionStatus.COMPLETED.name)),
        mode = StudySessionMode.valueOf(optString("mode", StudySessionMode.NORMAL.name)),
        note = optString("note", "")
    )
}

private fun StudySessionRecord.toLocalDate(): LocalDate? {
    return runCatching {
        Instant.ofEpochMilli(endedAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }.getOrNull()
}

private fun YearMonth.toMonthLabel(): String {
    return "${month.getDisplayName(TextStyle.FULL, Locale.getDefault())} $year"
}
