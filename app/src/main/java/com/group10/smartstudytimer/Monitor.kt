package com.group10.smartstudytimer

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.time.LocalDate

class Monitor : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_monitor, container, false)

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
        tvDate.text = "Today: ${today.month} ${today.dayOfMonth}, ${today.year}"

        // Convert focus score → stars
        val stars = when {
            todayStats.focusScore >= 80 -> "⭐⭐⭐⭐⭐"
            todayStats.focusScore >= 60 -> "⭐⭐⭐⭐"
            todayStats.focusScore >= 40 -> "⭐⭐⭐"
            todayStats.focusScore >= 20 -> "⭐⭐"
            else -> "⭐"
        }
        tvStars.text = stars

        // Focus Level text
        val focusLevel = when {
            todayStats.focusScore >= 80 -> "Great"
            todayStats.focusScore >= 60 -> "Good"
            todayStats.focusScore >= 40 -> "Average"
            todayStats.focusScore >= 20 -> "Poor"
            else -> "Very Poor"
        }
        tvFocusLevel.text = "Focus Level: $focusLevel"

        // Convert minutes → HH:MM
        val hours = todayStats.studyMinutes / 60
        val minutes = todayStats.studyMinutes % 60
        tvSessionTime.text = "Session Time: %02d:%02d".format(hours, minutes)

        tvInterruptedCount.text = "Interrupted Count: ${todayStats.interruptionCount}"

        // Mapping data model → UI meaning
        val pickupCount = todayStats.interruptionCount
        val phoneUseMinutes = todayStats.interruptedMinutes

        tvPickupCount.text = "Pickup Count: $pickupCount"
        tvPhoneUseCount.text = "Phone-use Time: $phoneUseMinutes min"

        return view
    }
}