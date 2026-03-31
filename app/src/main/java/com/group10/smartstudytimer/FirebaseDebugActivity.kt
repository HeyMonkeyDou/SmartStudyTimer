package com.group10.smartstudytimer

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate

class FirebaseDebugActivity : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private val localPreferences by lazy {
        getSharedPreferences(LOCAL_STATISTICS_PREFS, Context.MODE_PRIVATE)
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
    private lateinit var calendarMonthInput: EditText
    private lateinit var dayDateInput: EditText
    private lateinit var dayStudyMinutesInput: EditText
    private lateinit var dayInterruptionCountInput: EditText
    private lateinit var dayInterruptedMinutesInput: EditText
    private lateinit var dayCompletedSessionsInput: EditText
    private lateinit var dayFocusScoreInput: EditText
    private lateinit var recordsInput: EditText

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
        calendarMonthInput = findViewById(R.id.calendarMonthInput)
        dayDateInput = findViewById(R.id.dayDateInput)
        dayStudyMinutesInput = findViewById(R.id.dayStudyMinutesInput)
        dayInterruptionCountInput = findViewById(R.id.dayInterruptionCountInput)
        dayInterruptedMinutesInput = findViewById(R.id.dayInterruptedMinutesInput)
        dayCompletedSessionsInput = findViewById(R.id.dayCompletedSessionsInput)
        dayFocusScoreInput = findViewById(R.id.dayFocusScoreInput)
        recordsInput = findViewById(R.id.recordsInput)

        findViewById<Button>(R.id.signInButton).setOnClickListener { signIn() }
        findViewById<Button>(R.id.saveUserButton).setOnClickListener { saveUser() }
        findViewById<Button>(R.id.loadUserButton).setOnClickListener { loadUser() }
        findViewById<Button>(R.id.saveRankButton).setOnClickListener { saveRank() }
        findViewById<Button>(R.id.loadLeaderboardButton).setOnClickListener { loadLeaderboard() }

        findViewById<Button>(R.id.loadDayButton).setOnClickListener { loadDayFromFormRecords() }
        findViewById<Button>(R.id.saveDayButton).setOnClickListener { saveDayToFormRecords() }
        findViewById<Button>(R.id.deleteDayButton).setOnClickListener { deleteDayFromFormRecords() }

        findViewById<Button>(R.id.saveLocalStatisticsButton).setOnClickListener { saveLocalStatistics() }
        findViewById<Button>(R.id.loadLocalStatisticsButton).setOnClickListener { loadLocalStatistics() }
        findViewById<Button>(R.id.clearLocalStatisticsButton).setOnClickListener { clearLocalStatistics() }

        findViewById<Button>(R.id.saveServerStatisticsButton).setOnClickListener { saveServerStatistics() }
        findViewById<Button>(R.id.loadServerStatisticsButton).setOnClickListener { loadServerStatistics() }
        findViewById<Button>(R.id.clearServerStatisticsButton).setOnClickListener { clearServerStatistics() }

        populateDefaultStatisticsFields()
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

    private fun loadDayFromFormRecords() {
        val targetDate = parseDate(dayDateInput, "dayDate") ?: return
        val records = currentRecords()
        val record = records.firstOrNull { it.date == targetDate.toString() }
        if (record == null) {
            clearDayInputs(keepDate = true)
            appendLog("No record found for ${targetDate}.")
            setStatus("Day not found")
            return
        }

        bindDayRecord(record)
        appendLog("Loaded record for ${targetDate}.")
        setStatus("Day loaded")
    }

    private fun saveDayToFormRecords() {
        val dayRecord = collectDayRecordFromInputs() ?: return
        val mutableRecords = currentRecords().toMutableList()
        val existingIndex = mutableRecords.indexOfFirst { it.date == dayRecord.date }
        if (existingIndex >= 0) {
            mutableRecords[existingIndex] = dayRecord
        } else {
            mutableRecords.add(dayRecord)
        }
        mutableRecords.sortBy { it.date }
        recordsInput.setText(StatisticsDebugParser.formatRecords(mutableRecords))
        refreshStatisticsFromInputs()
        appendLog("Saved record for ${dayRecord.date} into form.")
        setStatus("Day saved to form")
    }

    private fun deleteDayFromFormRecords() {
        val targetDate = parseDate(dayDateInput, "dayDate") ?: return
        val filteredRecords = currentRecords().filterNot { it.date == targetDate.toString() }
        recordsInput.setText(StatisticsDebugParser.formatRecords(filteredRecords))
        clearDayInputs(keepDate = true)
        refreshStatisticsFromInputs()
        appendLog("Deleted record for ${targetDate} from form.")
        setStatus("Day deleted from form")
    }

    private fun saveLocalStatistics() {
        val statistics = collectStatisticsFromInputs() ?: return
        localPreferences.edit()
            .putString(KEY_LOCAL_SNAPSHOT_DATE, statistics.snapshotDate)
            .putString(KEY_LOCAL_CALENDAR_MONTH, statistics.calendarMonth)
            .putString(KEY_LOCAL_RECORDS, StatisticsDebugParser.formatRecords(statistics.records))
            .apply()
        renderStatisticsPreview(statistics)
        appendLog("Saved statistics locally.")
        setStatus("Local statistics saved")
    }

    private fun loadLocalStatistics() {
        val snapshotDate = localPreferences.getString(KEY_LOCAL_SNAPSHOT_DATE, null)
        val calendarMonth = localPreferences.getString(KEY_LOCAL_CALENDAR_MONTH, null)
        val recordsRaw = localPreferences.getString(KEY_LOCAL_RECORDS, null)
        if (snapshotDate.isNullOrBlank() || calendarMonth.isNullOrBlank() || recordsRaw == null) {
            appendLog("No local statistics saved.")
            setStatus("No local statistics")
            return
        }

        snapshotDateInput.setText(snapshotDate)
        calendarMonthInput.setText(calendarMonth)
        recordsInput.setText(recordsRaw)
        refreshStatisticsFromInputs()
        appendLog("Loaded local statistics.")
        setStatus("Local statistics loaded")
    }

    private fun clearLocalStatistics() {
        localPreferences.edit().clear().apply()
        populateDefaultStatisticsFields()
        appendLog("Cleared local statistics.")
        setStatus("Local statistics cleared")
    }

    private fun saveServerStatistics() {
        val statistics = collectStatisticsFromInputs() ?: return
        setStatus("Saving server statistics...")
        repository.saveCurrentStudyStatistics(
            statistics = statistics,
            onSuccess = {
                renderStatisticsPreview(statistics)
                appendLog("Saved statistics to server.")
                setStatus("Server statistics saved")
            },
            onError = { error -> showError("Save server statistics failed", error) }
        )
    }

    private fun loadServerStatistics() {
        setStatus("Loading server statistics...")
        repository.loadCurrentStudyStatistics(
            onSuccess = { statistics ->
                if (statistics == null) {
                    statisticsPreviewText.text = "statistics: null"
                    appendLog("No server statistics found.")
                    setStatus("No server statistics")
                    return@loadCurrentStudyStatistics
                }

                uidText.text = "uid: ${statistics.uid}"
                bindStatisticsToInputs(statistics)
                renderStatisticsPreview(statistics)
                appendLog("Loaded server statistics.")
                setStatus("Server statistics loaded")
            },
            onError = { error -> showError("Load server statistics failed", error) }
        )
    }

    private fun clearServerStatistics() {
        setStatus("Clearing server statistics...")
        repository.deleteCurrentStudyStatistics(
            onSuccess = {
                statisticsPreviewText.text = "statistics: null"
                appendLog("Cleared server statistics.")
                setStatus("Server statistics cleared")
            },
            onError = { error -> showError("Clear server statistics failed", error) }
        )
    }

    private fun refreshStatisticsFromInputs() {
        val statistics = collectStatisticsFromInputs() ?: return
        bindStatisticsToInputs(statistics)
        renderStatisticsPreview(statistics)
    }

    private fun collectStatisticsFromInputs(): StudyStatistics? {
        val snapshotDate = parseDate(snapshotDateInput, "snapshotDate") ?: return null
        val calendarMonth = calendarMonthInput.text.toString().trim().ifBlank {
            "${snapshotDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${snapshotDate.year}"
        }
        val records = try {
            currentRecords()
        } catch (error: IllegalArgumentException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            return null
        }
        val todayRecord = records.firstOrNull { it.date == snapshotDate.toString() }
            ?: DailyStatisticsRecord(date = snapshotDate.toString())
        val totalCompletedSessions = records.sumOf { it.completedSessions }
        val thisWeekCompletedSessions = records
            .filter { isSameWeek(LocalDate.parse(it.date), snapshotDate) }
            .sumOf { it.completedSessions }

        return StudyStatistics(
            snapshotDate = snapshotDate.toString(),
            focusScore = todayRecord.focusScore,
            todayStudyMinutes = todayRecord.studyMinutes,
            todayInterruptionCount = todayRecord.interruptionCount,
            todayInterruptedMinutes = todayRecord.interruptedMinutes,
            totalCompletedSessions = totalCompletedSessions,
            thisWeekCompletedSessions = thisWeekCompletedSessions,
            calendarMonth = calendarMonth,
            records = records
        )
    }

    private fun bindStatisticsToInputs(statistics: StudyStatistics) {
        snapshotDateInput.setText(statistics.snapshotDate)
        calendarMonthInput.setText(statistics.calendarMonth)
        recordsInput.setText(StatisticsDebugParser.formatRecords(statistics.records))
        val currentDayRecord = statistics.records.firstOrNull { it.date == statistics.snapshotDate }
            ?: DailyStatisticsRecord(date = statistics.snapshotDate)
        bindDayRecord(currentDayRecord)
    }

    private fun bindDayRecord(record: DailyStatisticsRecord) {
        dayDateInput.setText(record.date)
        dayStudyMinutesInput.setText(record.studyMinutes.toString())
        dayInterruptionCountInput.setText(record.interruptionCount.toString())
        dayInterruptedMinutesInput.setText(record.interruptedMinutes.toString())
        dayCompletedSessionsInput.setText(record.completedSessions.toString())
        dayFocusScoreInput.setText(record.focusScore.toString())
    }

    private fun clearDayInputs(keepDate: Boolean) {
        if (!keepDate) {
            dayDateInput.setText(LocalDate.now().toString())
        }
        dayStudyMinutesInput.setText("0")
        dayInterruptionCountInput.setText("0")
        dayInterruptedMinutesInput.setText("0")
        dayCompletedSessionsInput.setText("0")
        dayFocusScoreInput.setText("0")
    }

    private fun collectDayRecordFromInputs(): DailyStatisticsRecord? {
        val date = parseDate(dayDateInput, "dayDate") ?: return null
        val studyMinutes = parseLong(dayStudyMinutesInput, "dayStudyMinutes") ?: return null
        val interruptionCount = parseLong(dayInterruptionCountInput, "dayInterruptionCount") ?: return null
        val interruptedMinutes = parseLong(dayInterruptedMinutesInput, "dayInterruptedMinutes") ?: return null
        val completedSessions = parseLong(dayCompletedSessionsInput, "dayCompletedSessions") ?: return null
        val focusScore = parseLong(dayFocusScoreInput, "dayFocusScore") ?: return null

        return DailyStatisticsRecord(
            date = date.toString(),
            studyMinutes = studyMinutes,
            interruptionCount = interruptionCount,
            interruptedMinutes = interruptedMinutes,
            completedSessions = completedSessions,
            focusScore = focusScore
        )
    }

    private fun currentRecords(): List<DailyStatisticsRecord> {
        return StatisticsDebugParser.parseRecords(recordsInput.text.toString())
            .sortedBy { it.date }
    }

    private fun renderStatisticsPreview(statistics: StudyStatistics) {
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
            if (statistics.records.isEmpty()) {
                appendLine("calendarRecords: []")
            } else {
                appendLine("calendarRecords:")
                statistics.records.forEach { record ->
                    appendLine(
                        "date=${record.date}, studyMinutes=${record.studyMinutes}, interruptionCount=${record.interruptionCount}, interruptedMinutes=${record.interruptedMinutes}, completedSessions=${record.completedSessions}, focusScore=${record.focusScore}"
                    )
                }
            }
        }.trim()
    }

    private fun populateDefaultStatisticsFields() {
        val today = LocalDate.now()
        val defaultRecords = listOf(
            DailyStatisticsRecord(date = today.toString(), studyMinutes = 60, interruptionCount = 0, interruptedMinutes = 0, completedSessions = 2, focusScore = 100)
        )
        val defaultStatistics = StudyStatistics(
            snapshotDate = today.toString(),
            focusScore = 100,
            todayStudyMinutes = 60,
            todayInterruptionCount = 0,
            todayInterruptedMinutes = 0,
            totalCompletedSessions = 2,
            thisWeekCompletedSessions = 2,
            calendarMonth = "${today.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${today.year}",
            records = defaultRecords
        )
        bindStatisticsToInputs(defaultStatistics)
        renderStatisticsPreview(defaultStatistics)
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

    private fun isSameWeek(date: LocalDate, reference: LocalDate): Boolean {
        val weekFields = java.time.temporal.WeekFields.of(java.util.Locale.getDefault())
        return date.year == reference.year &&
            date.get(weekFields.weekOfWeekBasedYear()) == reference.get(weekFields.weekOfWeekBasedYear())
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

    companion object {
        private const val LOCAL_STATISTICS_PREFS = "firebase_debug_statistics"
        private const val KEY_LOCAL_SNAPSHOT_DATE = "local.snapshotDate"
        private const val KEY_LOCAL_CALENDAR_MONTH = "local.calendarMonth"
        private const val KEY_LOCAL_RECORDS = "local.records"
    }
}

object StatisticsDebugParser {
    fun parseRecords(rawValue: String): List<DailyStatisticsRecord> {
        if (rawValue.isBlank()) {
            return emptyList()
        }
        return rawValue
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, line ->
                val parts = line.split(",").map { it.trim() }
                if (parts.size != 6) {
                    throw IllegalArgumentException(
                        "Record line ${index + 1} must have 6 comma-separated values: date,studyMinutes,interruptionCount,interruptedMinutes,completedSessions,focusScore"
                    )
                }

                DailyStatisticsRecord(
                    date = parts[0],
                    studyMinutes = parts[1].toLongOrNull()
                        ?: throw IllegalArgumentException("Record line ${index + 1} has invalid studyMinutes."),
                    interruptionCount = parts[2].toLongOrNull()
                        ?: throw IllegalArgumentException("Record line ${index + 1} has invalid interruptionCount."),
                    interruptedMinutes = parts[3].toLongOrNull()
                        ?: throw IllegalArgumentException("Record line ${index + 1} has invalid interruptedMinutes."),
                    completedSessions = parts[4].toLongOrNull()
                        ?: throw IllegalArgumentException("Record line ${index + 1} has invalid completedSessions."),
                    focusScore = parts[5].toLongOrNull()
                        ?: throw IllegalArgumentException("Record line ${index + 1} has invalid focusScore.")
                )
            }
            .toList()
    }

    fun formatRecords(records: List<DailyStatisticsRecord>): String {
        return records.joinToString(separator = "\n") { record ->
            listOf(
                record.date,
                record.studyMinutes,
                record.interruptionCount,
                record.interruptedMinutes,
                record.completedSessions,
                record.focusScore
            ).joinToString(separator = ",")
        }
    }
}
