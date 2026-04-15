package com.group10.smartstudytimer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.time.LocalDate
import android.graphics.Color

class Today : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_today, container, false)

        // Get repository instance
        val repo = StatisticsRepository.getInstance(requireContext())

        // Get today's data
        val todayStats = repo.getDailyStatistics(LocalDate.now())

        // Find views
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvStars = view.findViewById<TextView>(R.id.tvStars)
        val tvFocusLevel = view.findViewById<TextView>(R.id.tvFocusLevel)

        val tvSessionTime = view.findViewById<TextView>(R.id.tvSessionTime)
        val tvInterruptedCount = view.findViewById<TextView>(R.id.tvInterruptedCount)
        val tvPickupCount = view.findViewById<TextView>(R.id.tvPickupCount)
        val tvPhoneUseCount = view.findViewById<TextView>(R.id.tvPhoneUseCount)

        // Set Date
        val today = LocalDate.now()
        tvDate.text = "${today.month} ${today.dayOfMonth}, ${today.year}"

        // Convert focus score →
        val stars = when {
            todayStats.focusScore >= 80 -> "★★★★★"
            todayStats.focusScore >= 60 -> "★★★★"
            todayStats.focusScore >= 40 -> "★★★"
            todayStats.focusScore >= 20 -> "★★"
            todayStats.focusScore >= 1 -> "★"
            else -> "☆☆☆☆☆"
        }
        val starColor = when {
            todayStats.focusScore > 0 -> Color.parseColor("#FFCC00")
            else -> Color.parseColor("#AAAAAA")
        }
        tvStars.text = stars
        tvStars.setTextColor(starColor)

        // Focus Level text
        val focusLevel = when {
            todayStats.focusScore >= 80 -> "Great"
            todayStats.focusScore >= 60 -> "Good"
            todayStats.focusScore >= 40 -> "Average"
            todayStats.focusScore >= 20 -> "Poor"
            todayStats.focusScore >= 1 -> "Very Poor"
            else -> "N/A"
        }
        tvFocusLevel.text = "Focus Level: $focusLevel"

        // Convert minutes → HH:MM
        tvSessionTime.text = "Session Time: ${formatDuration(todayStats.studyMinutes * 60)}"

        tvInterruptedCount.text = "Interrupted Count: ${todayStats.interruptionCount}"

        // Mapping data model → UI meaning
        val pickupCount = todayStats.interruptionCount
        val phoneUseSeconds = todayStats.interruptedSeconds

        tvPickupCount.text = "Pickup Count: $pickupCount"
        tvPhoneUseCount.text = "Phone-use Time: ${formatDuration(phoneUseSeconds)}"

        return view
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return "${hours}h ${minutes}m ${seconds}s"
    }
}
