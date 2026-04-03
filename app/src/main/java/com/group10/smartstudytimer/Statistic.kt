package com.group10.smartstudytimer

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.time.LocalDate
import java.time.YearMonth

class Statistic : Fragment() {

    private lateinit var repo: StatisticsRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_statistic, container, false)

        repo = StatisticsRepository.getInstance(requireContext())

        val calendarView = view.findViewById<CalendarView>(R.id.calendarView)

        // Default: today
        var selectedDate = LocalDate.now()
        updateUI(view, selectedDate)

        // When user selects a date
        calendarView.setOnDateChangeListener { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
            updateUI(view, selectedDate)
        }

        return view
    }

    private fun updateUI(view: View, date: LocalDate) {

        // Get data
        val daily = repo.getDailyStatistics(date)
        val monthly = repo.getMonthlyStatistics(YearMonth.from(date))
        val allSessions = repo.getRecordedSessions()

        // Views
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvScore = view.findViewById<TextView>(R.id.tvScore)
        val progressScore = view.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressScore)

        val tvSessionLength = view.findViewById<TextView>(R.id.tvSessionLength)
        val tvInterruptionCount = view.findViewById<TextView>(R.id.tvInterruptionCount)
        val tvInterruptedTime = view.findViewById<TextView>(R.id.tvInterruptedTime)

        val tvTotalSessions = view.findViewById<TextView>(R.id.tvTotalSessions)
        val tvMonthlySessions = view.findViewById<TextView>(R.id.tvMonthlySessions)

        // Date
        tvDate.text = "${date.month} ${date.dayOfMonth}, ${date.year}"

        // Score
        val score = daily.focusScore
        tvScore.text = score.toString()
        progressScore.progress = score.toInt()

        // Session length
        val hours = daily.studyMinutes / 60
        val minutes = daily.studyMinutes % 60
        tvSessionLength.text = "Session length: %02d:%02d".format(hours, minutes)

        // Interruptions
        tvInterruptionCount.text = "Interruption count: ${daily.interruptionCount}"
        tvInterruptedTime.text = "Interrupted time: ${daily.interruptedMinutes} min"

        // Total sessions (all time)
        val totalCompleted = allSessions.sumOf { it.completedSessions }
        tvTotalSessions.text = "Total completed session: $totalCompleted"

        // Monthly sessions
        val monthlyCompleted = monthly.sumOf { it.completedSessions }
        val month = date.month
        val year = date.year
        tvMonthlySessions.text = "$month $year completed session: $monthlyCompleted"
    }
}