package com.group10.smartstudytimer

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class FirebaseDebugActivity : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private val statisticsRepository by lazy {
        StatisticsRepository.getInstance(this)
    }

    private lateinit var statusText: TextView
    private lateinit var uidText: TextView
    private lateinit var leaderboardText: TextView
    private lateinit var statisticsPreviewText: TextView
    private lateinit var logsText: TextView
    private lateinit var displayNameInput: EditText
    private lateinit var totalFocusMinutesInput: EditText
    private lateinit var avatarIdInput: EditText

    private lateinit var snapshotDateInput: EditText
    private lateinit var sessionStudyMinutesInput: EditText
    private lateinit var sessionInterruptionCountInput: EditText
    private lateinit var sessionInterruptedMinutesInput: EditText
    private lateinit var sessionCompletedSessionsInput: EditText
    private lateinit var sessionStatusSpinner: Spinner
    private lateinit var sessionModeSpinner: Spinner
    private lateinit var sessionNoteInput: EditText
    private lateinit var sessionsInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_firebase_debug)

        statusText = findViewById(R.id.statusText)
        uidText = findViewById(R.id.uidText)
        leaderboardText = findViewById(R.id.leaderboardText)
        statisticsPreviewText = findViewById(R.id.statisticsPreviewText)
        logsText = findViewById(R.id.logsText)
        displayNameInput = findViewById(R.id.displayNameInput)
        totalFocusMinutesInput = findViewById(R.id.totalFocusMinutesInput)
        avatarIdInput = findViewById(R.id.avatarIdInput)
        snapshotDateInput = findViewById(R.id.snapshotDateInput)
        sessionStudyMinutesInput = findViewById(R.id.sessionStudyMinutesInput)
        sessionInterruptionCountInput = findViewById(R.id.sessionInterruptionCountInput)
        sessionInterruptedMinutesInput = findViewById(R.id.sessionInterruptedMinutesInput)
        sessionCompletedSessionsInput = findViewById(R.id.sessionCompletedSessionsInput)
        sessionStatusSpinner = findViewById(R.id.sessionStatusSpinner)
        sessionModeSpinner = findViewById(R.id.sessionModeSpinner)
        sessionNoteInput = findViewById(R.id.sessionNoteInput)
        sessionsInput = findViewById(R.id.sessionsInput)
        setupSessionSelectors()

        findViewById<Button>(R.id.signInButton).setOnClickListener { signIn() }
        findViewById<Button>(R.id.saveUserButton).setOnClickListener { saveUser() }
        findViewById<Button>(R.id.loadUserButton).setOnClickListener { loadUser() }
        findViewById<Button>(R.id.saveRankButton).setOnClickListener { saveRank() }
        findViewById<Button>(R.id.loadLeaderboardButton).setOnClickListener { loadLeaderboard() }

        findViewById<Button>(R.id.addSessionButton).setOnClickListener { addOneSessionToForm() }

        findViewById<Button>(R.id.clearLocalStatisticsButton).setOnClickListener { clearLocalSessions() }

        findViewById<Button>(R.id.saveServerStatisticsButton).setOnClickListener { saveServerSessions() }
        findViewById<Button>(R.id.loadServerStatisticsButton).setOnClickListener { loadServerSessions() }
        findViewById<Button>(R.id.clearServerStatisticsButton).setOnClickListener { clearServerSessions() }

        loadLocalSessionsIntoForm()
    }

    private fun signIn() {
        setStatus("Signing in...")
        repository.ensureAnonymousUser(
            onSuccess = { uid ->
                uidText.text = "uid: $uid"
                appendLog("Anonymous sign-in success: $uid")
                setStatus("Signed in")
            },
            onError = { error -> showError("Sign in failed", error) }
        )
    }

    private fun saveUser() {
        val totalFocusMinutes = parseLong(totalFocusMinutesInput, "Total focus minutes") ?: return
        setStatus("Saving user...")
        repository.saveCurrentUserProfile(
            displayName = displayNameInput.text.toString().trim(),
            totalFocusMinutes = totalFocusMinutes,
            avatarId = avatarIdInput.text.toString().trim().ifBlank { "avatar_blue" },
            onSuccess = {
                appendLog("User profile saved.")
                setStatus("User saved")
            },
            onError = { error -> showError("Save user failed", error) }
        )
    }

    private fun loadUser() {
        setStatus("Loading user...")
        repository.loadCurrentUserProfile(
            onSuccess = { profile ->
                if (profile == null) {
                    appendLog("No user profile found.")
                    setStatus("No user profile")
                    return@loadCurrentUserProfile
                }

                uidText.text = "uid: ${profile.uid}"
                displayNameInput.setText(profile.displayName)
                totalFocusMinutesInput.setText(profile.totalFocusMinutes.toString())
                avatarIdInput.setText(profile.avatarId)
                appendLog("User profile loaded.")
                setStatus("User loaded")
            },
            onError = { error -> showError("Load user failed", error) }
        )
    }

    private fun saveRank() {
        val totalFocusMinutes = parseLong(totalFocusMinutesInput, "Total focus minutes") ?: return
        setStatus("Saving leaderboard entry...")
        repository.saveCurrentLeaderboardEntry(
            displayName = displayNameInput.text.toString().trim(),
            totalFocusMinutes = totalFocusMinutes,
            avatarId = avatarIdInput.text.toString().trim().ifBlank { "avatar_blue" },
            onSuccess = {
                appendLog("Leaderboard entry saved.")
                setStatus("Leaderboard saved")
            },
            onError = { error -> showError("Save leaderboard failed", error) }
        )
    }

    private fun loadLeaderboard() {
        setStatus("Loading leaderboard...")
        repository.loadLeaderboard(
            onSuccess = { entries ->
                leaderboardText.text = buildString {
                    appendLine("leaderboardEntryCount: ${entries.size}")
                    if (entries.isEmpty()) {
                        appendLine("leaderboardEntries: []")
                    } else {
                        appendLine("leaderboardEntries:")
                        entries.forEachIndexed { index, entry ->
                            appendLine(
                                "index=${index + 1}, uid=${entry.uid}, displayName=${entry.displayName}, totalFocusMinutes=${entry.totalFocusMinutes}, avatarId=${entry.avatarId}"
                            )
                        }
                    }
                }.trim()
                appendLog("Loaded ${entries.size} leaderboard entries.")
                setStatus("Leaderboard loaded")
            },
            onError = { error -> showError("Load leaderboard failed", error) }
        )
    }

    private fun addOneSessionToForm() {
        val session = collectSessionRecordFromInputs() ?: return
        val mutableSessions = parseCurrentSessions()?.toMutableList() ?: return
        mutableSessions.add(session)
        mutableSessions.sortBy { it.endedAtEpochMillis }
        sessionsInput.setText(SessionDebugParser.formatSessions(mutableSessions))
        bindSessionRecord(session)
        refreshStatisticsFromInputs()
        appendLog("Added one session ${session.sessionId} into form.")
        setStatus("Added one session")
    }

    private fun clearLocalSessions() {
        statisticsRepository.clearAllLocalStatistics()
        sessionsInput.setText("")
        clearSessionInputs()
        refreshStatisticsFromInputs()
        appendLog("Cleared local sessions.")
        setStatus("Local sessions cleared")
    }

    private fun saveServerSessions() {
        val sessions = parseCurrentSessions() ?: return
        setStatus("Saving server sessions...")
        repository.saveCurrentStudySessions(
            sessions = sessions,
            onSuccess = {
                refreshStatisticsFromInputs()
                appendLog("Saved ${sessions.size} sessions to server.")
                setStatus("Server sessions saved")
            },
            onError = { error -> showError("Save server sessions failed", error) }
        )
    }

    private fun loadServerSessions() {
        setStatus("Loading server sessions...")
        repository.loadCurrentStudySessions(
            onSuccess = { sessions ->
                if (sessions == null) {
                    statisticsPreviewText.text = "statistics: null"
                    appendLog("No server sessions found.")
                    setStatus("No server sessions")
                    return@loadCurrentStudySessions
                }

                sessionsInput.setText(SessionDebugParser.formatSessions(sessions.sortedBy { it.endedAtEpochMillis }))
                refreshStatisticsFromInputs()
                appendLog("Loaded ${sessions.size} sessions from server.")
                setStatus("Server sessions loaded")
            },
            onError = { error -> showError("Load server sessions failed", error) }
        )
    }

    private fun clearServerSessions() {
        setStatus("Clearing server sessions...")
        repository.deleteCurrentStudySessions(
            onSuccess = {
                sessionsInput.setText("")
                clearSessionInputs()
                refreshStatisticsFromInputs()
                appendLog("Cleared server sessions.")
                setStatus("Server sessions cleared")
            },
            onError = { error -> showError("Clear server sessions failed", error) }
        )
    }

    private fun refreshStatisticsFromInputs() {
        val sessions = parseCurrentSessions() ?: return
        val statistics = collectStatisticsFromInputs(sessions) ?: return
        renderStatisticsPreview(statistics, sessions)
    }

    private fun collectStatisticsFromInputs(
        sessions: List<StudySessionRecord>
    ): StudyStatistics? {
        val snapshotDate = parseDate(snapshotDateInput, "snapshotDate") ?: return null
        val month = YearMonth.from(snapshotDate)
        return StatisticsAggregator.buildStatisticsSnapshot(
            sessions = sessions,
            snapshotDate = snapshotDate,
            month = month
        )
    }

    private fun bindSessionRecord(record: StudySessionRecord) {
        sessionStudyMinutesInput.setText(record.studyMinutes.toString())
        sessionInterruptionCountInput.setText(record.interruptionCount.toString())
        sessionInterruptedMinutesInput.setText(record.interruptedMinutes.toString())
        sessionCompletedSessionsInput.setText(record.completedSessions.toString())
        sessionStatusSpinner.setSelection(StudySessionStatus.entries.indexOf(record.status))
        sessionModeSpinner.setSelection(StudySessionMode.entries.indexOf(record.mode))
        sessionNoteInput.setText(record.note)
    }

    private fun clearSessionInputs() {
        sessionStudyMinutesInput.setText("25")
        sessionInterruptionCountInput.setText("0")
        sessionInterruptedMinutesInput.setText("0")
        sessionCompletedSessionsInput.setText("1")
        sessionStatusSpinner.setSelection(StudySessionStatus.entries.indexOf(StudySessionStatus.COMPLETED))
        sessionModeSpinner.setSelection(StudySessionMode.entries.indexOf(StudySessionMode.NORMAL))
        sessionNoteInput.setText("")
    }

    private fun collectSessionRecordFromInputs(): StudySessionRecord? {
        val sessionId = UUID.randomUUID().toString()
        val endedAtEpochMillis = System.currentTimeMillis()
        val studyMinutes = parseLong(sessionStudyMinutesInput, "sessionStudyMinutes") ?: return null
        val interruptionCount = parseLong(sessionInterruptionCountInput, "sessionInterruptionCount") ?: return null
        val interruptedMinutes = parseLong(sessionInterruptedMinutesInput, "sessionInterruptedMinutes") ?: return null
        val completedSessions = parseLong(sessionCompletedSessionsInput, "sessionCompletedSessions") ?: return null
        val status = sessionStatusSpinner.selectedItem as StudySessionStatus
        val mode = sessionModeSpinner.selectedItem as StudySessionMode
        val note = sessionNoteInput.text.toString()

        return StudySessionRecord(
            sessionId = sessionId,
            endedAtEpochMillis = endedAtEpochMillis,
            studyMinutes = studyMinutes,
            interruptionCount = interruptionCount,
            interruptedMinutes = interruptedMinutes,
            completedSessions = completedSessions,
            status = status,
            mode = mode,
            note = note
        )
    }

    private fun parseCurrentSessions(): List<StudySessionRecord>? {
        return try {
            SessionDebugParser.parseSessions(sessionsInput.text.toString())
                .sortedBy { it.endedAtEpochMillis }
        } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun renderStatisticsPreview(
        statistics: StudyStatistics,
        sessions: List<StudySessionRecord>
    ) {
        val today = LocalDate.parse(statistics.snapshotDate)
        statisticsPreviewText.text = buildString {
            appendLine("snapshotDate: ${statistics.snapshotDate}")
            appendLine("focusScore: ${statistics.focusScore}")
            appendLine("todayStudyMinutes: ${statistics.todayStudyMinutes}")
            appendLine("todayInterruptionCount: ${statistics.todayInterruptionCount}")
            appendLine("todayInterruptedMinutes: ${statistics.todayInterruptedMinutes}")
            appendLine("totalCompletedSessions: ${statistics.totalCompletedSessions}")
            appendLine("thisWeekCompletedSessions: ${statistics.thisWeekCompletedSessions}")
            appendLine("calendarMonth: ${statistics.calendarMonth}")
            appendLine("calendarRecordCount: ${statistics.records.size}")
            appendLine("sessionCount: ${sessions.size}")
            if (sessions.isEmpty()) {
                appendLine("sessions: []")
            } else {
                appendLine("sessions:")
                sessions.forEach { session ->
                    val sessionDate = Instant.ofEpochMilli(session.endedAtEpochMillis)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    val marker = if (sessionDate == today) "*" else "-"
                    appendLine(
                        "$marker id=${session.sessionId}, endedAt=${session.endedAtEpochMillis}, studyMinutes=${session.studyMinutes}, interruptionCount=${session.interruptionCount}, interruptedMinutes=${session.interruptedMinutes}, completedSessions=${session.completedSessions}, status=${session.status}, mode=${session.mode}, note=${session.note}"
                    )
                }
            }
        }.trim()
    }

    private fun loadLocalSessionsIntoForm() {
        snapshotDateInput.setText(LocalDate.now().toString())
        val sessions = statisticsRepository.getRecordedSessions()
        if (sessions.isEmpty()) {
            clearSessionInputs()
            sessionsInput.setText("")
            refreshStatisticsFromInputs()
            appendLog("Loaded 0 local sessions.")
            setStatus("Local sessions loaded")
            return
        }

        val sortedSessions = sessions.sortedBy { it.endedAtEpochMillis }
        bindSessionRecord(sortedSessions.last())
        sessionsInput.setText(SessionDebugParser.formatSessions(sortedSessions))
        refreshStatisticsFromInputs()
        appendLog("Loaded ${sortedSessions.size} local sessions.")
        setStatus("Local sessions loaded")
    }

    private fun setupSessionSelectors() {
        sessionStatusSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            StudySessionStatus.entries.toTypedArray()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        sessionModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            StudySessionMode.entries.toTypedArray()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun parseLong(input: EditText, fieldName: String): Long? {
        val value = input.text.toString().trim().toLongOrNull()
        if (value == null) {
            Toast.makeText(this, "$fieldName must be a number", Toast.LENGTH_SHORT).show()
            return null
        }
        return value
    }

    private fun parseDate(input: EditText, fieldName: String): LocalDate? {
        val rawValue = input.text.toString().trim()
        return try {
            LocalDate.parse(rawValue)
        } catch (_: Exception) {
            Toast.makeText(this, "$fieldName must use yyyy-MM-dd", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun setStatus(status: String) {
        statusText.text = "status: $status"
    }

    private fun appendLog(message: String) {
        val existingText = logsText.text.toString().takeIf { it != "logs: []" }.orEmpty()
        logsText.text = buildString {
            appendLine(message)
            if (existingText.isNotBlank()) {
                append(existingText)
            }
        }.trim()
    }

    private fun showError(prefix: String, error: Exception) {
        val message = "$prefix: ${error.message}"
        appendLog(message)
        setStatus(prefix)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

}

object SessionDebugParser {
    fun parseSessions(rawValue: String): List<StudySessionRecord> {
        if (rawValue.isBlank()) {
            return emptyList()
        }
        return rawValue
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                val parts = line.split("|", limit = 9).map { it.trim() }
                if (parts.size != 9) {
                    throw IllegalArgumentException(
                        "Session line ${index + 1} must have 9 pipe-separated values: sessionId|endedAtEpochMillis|studyMinutes|interruptionCount|interruptedMinutes|completedSessions|status|mode|note"
                    )
                }

                StudySessionRecord(
                    sessionId = parts[0].ifBlank { UUID.randomUUID().toString() },
                    endedAtEpochMillis = parts[1].toLongOrNull()
                        ?: throw IllegalArgumentException("Session line ${index + 1} has invalid endedAtEpochMillis."),
                    studyMinutes = parts[2].toLongOrNull()
                        ?: throw IllegalArgumentException("Session line ${index + 1} has invalid studyMinutes."),
                    interruptionCount = parts[3].toLongOrNull()
                        ?: throw IllegalArgumentException("Session line ${index + 1} has invalid interruptionCount."),
                    interruptedMinutes = parts[4].toLongOrNull()
                        ?: throw IllegalArgumentException("Session line ${index + 1} has invalid interruptedMinutes."),
                    completedSessions = parts[5].toLongOrNull()
                        ?: throw IllegalArgumentException("Session line ${index + 1} has invalid completedSessions."),
                    status = runCatching { StudySessionStatus.valueOf(parts[6].uppercase()) }
                        .getOrElse {
                            throw IllegalArgumentException("Session line ${index + 1} has invalid status.")
                        },
                    mode = runCatching { StudySessionMode.valueOf(parts[7].uppercase()) }
                        .getOrElse {
                            throw IllegalArgumentException("Session line ${index + 1} has invalid mode.")
                        },
                    note = parts[8]
                )
            }
            .toList()
    }

    fun formatSessions(sessions: List<StudySessionRecord>): String {
        return sessions.joinToString(separator = "\n") { session ->
            listOf(
                session.sessionId,
                session.endedAtEpochMillis,
                session.studyMinutes,
                session.interruptionCount,
                session.interruptedMinutes,
                session.completedSessions,
                session.status.name,
                session.mode.name,
                session.note.replace("\n", " ")
            ).joinToString(separator = " | ")
        }
    }
}
