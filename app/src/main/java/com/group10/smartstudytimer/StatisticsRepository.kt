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
    val interruptedSeconds: Long = 0,
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

    fun clearAllLocalStatistics()
}

/**
 * Statistics UI teammate entry points.
 * These methods return aggregated data for the Statistics screen.
 */
interface StatisticsReader {
    fun getDailyStatistics(date: LocalDate): DailyStatisticsRecord

    fun getMonthlyStatistics(month: YearMonth): List<DailyStatisticsRecord>

    fun getRecordedSessions(): List<StudySessionRecord>

    fun getLastSyncEpochMillis(): Long?
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
        saveSessions(sessions)
    }

    override fun clearAllLocalStatistics() {
        preferences.edit().remove(SESSIONS_KEY).apply()
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

    override fun getLastSyncEpochMillis(): Long? {
        return preferences.takeIf { it.contains(LAST_SYNC_EPOCH_MILLIS_KEY) }
            ?.getLong(LAST_SYNC_EPOCH_MILLIS_KEY, 0L)
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
                markSyncCompleted()
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
        syncBestFocusScoreToFirebase(sessions)
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
            onSuccess = { markSyncCompleted() },
            onError = {}
        )
    }

    private fun syncSessionToFirebase(session: StudySessionRecord) {
        firebaseRepository.addCurrentStudySession(
            session = session,
            onSuccess = { markSyncCompleted() },
            onError = {}
        )
    }

    private fun markSyncCompleted() {
        preferences.edit()
            .putLong(LAST_SYNC_EPOCH_MILLIS_KEY, System.currentTimeMillis())
            .apply()
    }

    private fun syncBestFocusScoreToFirebase(sessions: List<StudySessionRecord>) {
        val bestRecord = StatisticsAggregator.buildDailyScoreRecords(sessions)
            .maxWithOrNull(compareBy<DailyStatisticsRecord> { it.focusScore }.thenBy { it.date })
            ?: DailyStatisticsRecord()

        firebaseRepository.updateCurrentBestFocusScore(
            bestFocusScore = bestRecord.focusScore,
            bestFocusScoreCompletedAt = bestRecord.date
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "statistics_repository"
        private const val SESSIONS_KEY = "statistics.sessions"
        private const val LAST_SYNC_EPOCH_MILLIS_KEY = "statistics.last_sync_epoch_millis"
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
            todayInterruptedSeconds = todayRecord.interruptedSeconds,
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
        val interruptedSeconds = dailySessions.sumOf { it.interruptedSeconds }
        val completedSessions = dailySessions.sumOf { it.completedSessions }

        return DailyStatisticsRecord(
            date = date.toString(),
            studyMinutes = studyMinutes,
            interruptionCount = interruptionCount,
            interruptedSeconds = interruptedSeconds,
            completedSessions = completedSessions,
            focusScore = calculateFocusScore(
                studyMinutes = studyMinutes,
                interruptionCount = interruptionCount,
                interruptedSeconds = interruptedSeconds,
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
                    record.interruptedSeconds > 0 ||
                    record.completedSessions > 0
            }
    }

    fun buildDailyScoreRecords(sessions: List<StudySessionRecord>): List<DailyStatisticsRecord> {
        return sessions
            .mapNotNull { it.toLocalDate() }
            .distinct()
            .sorted()
            .map { date -> buildDailyStatistics(sessions, date) }
            .filter { it.focusScore > 0 }
    }

    fun buildDailyPeakScoreRecords(sessions: List<StudySessionRecord>): List<DailyStatisticsRecord> {
        return sessions
            .mapNotNull { session ->
                val date = session.toLocalDate() ?: return@mapNotNull null
                DailyStatisticsRecord(
                    date = date.toString(),
                    studyMinutes = session.studyMinutes,
                    interruptionCount = session.interruptionCount,
                    interruptedSeconds = session.interruptedSeconds,
                    completedSessions = session.completedSessions,
                    focusScore = calculateFocusScore(
                        studyMinutes = session.studyMinutes,
                        interruptionCount = session.interruptionCount,
                        interruptedSeconds = session.interruptedSeconds,
                        completedSessions = session.completedSessions
                    )
                )
            }
            .groupBy { it.date }
            .mapNotNull { (_, records) ->
                records.maxWithOrNull(
                    compareBy<DailyStatisticsRecord> { it.focusScore }
                        .thenBy { it.studyMinutes }
                        .thenBy { it.completedSessions }
                )
            }
            .sortedBy { it.date }
    }

    fun calculateFocusScore(
        studyMinutes: Long,
        interruptionCount: Long,
        interruptedSeconds: Long,
        completedSessions: Long
    ): Long {
        if (studyMinutes <= 0 && completedSessions <= 0) {
            return 0
        }

        val score = 50L +
            minOf(30L, studyMinutes / 2) +
            minOf(20L, completedSessions * 10) -
            minOf(20L, interruptionCount * 5) -
            minOf(20L, interruptedSeconds / 60)

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
        .put("interruptedSeconds", interruptedSeconds)
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
        interruptedSeconds = when {
            has("interruptedSeconds") -> optLong("interruptedSeconds", 0)
            has("interruptedMinutes") -> optLong("interruptedMinutes", 0) * 60
            else -> 0
        },
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
