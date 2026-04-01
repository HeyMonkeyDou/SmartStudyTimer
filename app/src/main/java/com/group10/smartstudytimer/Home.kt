package com.group10.smartstudytimer

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import java.util.Locale

class Home : Fragment() {

    private val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository.getInstance(requireContext())
    }

    private lateinit var homeContainer: LinearLayout
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioNormal: RadioButton
    private lateinit var radioPomodoro: RadioButton

    private lateinit var layoutTimeInput: LinearLayout
    private lateinit var layoutPomodoroOptions: LinearLayout

    private lateinit var inputHours: EditText
    private lateinit var inputMinutes: EditText
    private lateinit var inputRounds: EditText
    private lateinit var inputDistractionLimit: EditText

    private lateinit var tvTimer: TextView
    private lateinit var tvStatus: TextView

    private lateinit var btnStart: Button
    private lateinit var btnPause: Button
    private lateinit var btnReset: Button

    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var initialTimeInMillis: Long = 0
    private var timerRunning = false

    private var isPomodoroMode = false
    private var totalRounds = 1
    private var currentRound = 1
    private var distractionLimit = 3

    private var isBreakTime = false
    private var studyDurationInMillis = 25 * 60 * 1000L
    private var breakDurationInMillis = 5 * 60 * 1000L
    private var distractionCount = 0
    private var sessionInvalidated = false



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupModeSelection()
        updateTimerText()
        updateButtons()
        updateUIState()

        btnStart.setOnClickListener {
            if (!timerRunning) {
                if (timeLeftInMillis <= 0) {
                    setupSession()
                }
                if (timeLeftInMillis > 0) {
                    startTimer()
                }
            }
        }

        btnPause.setOnClickListener {
            pauseTimer()
        }

        btnReset.setOnClickListener {
            resetTimer()
        }
    }

    private fun bindViews(view: View) {
        homeContainer = view.findViewById(R.id.homeContainer)
        radioGroupMode = view.findViewById(R.id.radioGroupMode)
        radioNormal = view.findViewById(R.id.radioNormal)
        radioPomodoro = view.findViewById(R.id.radioPomodoro)

        layoutTimeInput = view.findViewById(R.id.layoutTimeInput)
        layoutPomodoroOptions = view.findViewById(R.id.layoutPomodoroOptions)

        inputHours = view.findViewById(R.id.inputHours)
        inputMinutes = view.findViewById(R.id.inputMinutes)
        inputRounds = view.findViewById(R.id.inputRounds)
        inputDistractionLimit = view.findViewById(R.id.inputDistractionLimit)

        tvTimer = view.findViewById(R.id.tvTimer)
        tvStatus = view.findViewById(R.id.tvStatus)

        btnStart = view.findViewById(R.id.btnStart)
        btnPause = view.findViewById(R.id.btnPause)
        btnReset = view.findViewById(R.id.btnReset)
    }

    private fun setupModeSelection() {
        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            isPomodoroMode = checkedId == R.id.radioPomodoro
            if (isPomodoroMode) {
                layoutPomodoroOptions.visibility = View.VISIBLE
                tvStatus.text = "Mode: Pomodoro"
            } else {
                layoutPomodoroOptions.visibility = View.GONE
                tvStatus.text = "Mode: Normal"
            }
            resetSessionState()
            updateUIState()
        }
    }

    private fun setupSession() {
        distractionCount = 0
        sessionInvalidated = false

        if (isPomodoroMode) {
            val roundsText = inputRounds.text.toString()
            totalRounds = if (roundsText.isNotEmpty()) roundsText.toInt() else 1
            if (totalRounds <= 0) totalRounds = 1

            val distractionText = inputDistractionLimit.text.toString()
            distractionLimit = if (distractionText.isNotEmpty()) distractionText.toInt() else 3
            if (distractionLimit > 5) distractionLimit = 5
            if (distractionLimit < 1) distractionLimit = 1

            currentRound = 1
            isBreakTime = false
            initialTimeInMillis = studyDurationInMillis
            timeLeftInMillis = initialTimeInMillis
            tvStatus.text = "Study Round $currentRound / $totalRounds | Limit: $distractionLimit"
        } else {
            val hoursText = inputHours.text.toString()
            val minutesText = inputMinutes.text.toString()

            val hours = if (hoursText.isNotEmpty()) hoursText.toLong() else 0L
            val minutes = if (minutesText.isNotEmpty()) minutesText.toLong() else 0L

            initialTimeInMillis = (hours * 3600 + minutes * 60) * 1000L

            if (initialTimeInMillis <= 0) {
                Toast.makeText(requireContext(), "Please enter a valid time.", Toast.LENGTH_SHORT).show()
                return
            }

            timeLeftInMillis = initialTimeInMillis
            tvStatus.text = "Mode: Normal"
        }

        updateTimerText()
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
            }

            override fun onFinish() {
                timerRunning = false
                timeLeftInMillis = 0
                updateTimerText()

                if (isPomodoroMode) {
                    if (!isBreakTime) {
                        // study session just finished
                        if (currentRound < totalRounds) {
                            isBreakTime = true
                            initialTimeInMillis = breakDurationInMillis
                            timeLeftInMillis = initialTimeInMillis
                            tvStatus.text = "Break Time before Round ${currentRound + 1}"
                            Toast.makeText(requireContext(), "Study round finished. Break started.", Toast.LENGTH_SHORT).show()
                            startTimer()
                            return
                        } else {
                            recordCompletedSession()
                            tvStatus.text = "Pomodoro finished!"
                            Toast.makeText(requireContext(), "All Pomodoro rounds completed!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // break session just finished
                        isBreakTime = false
                        currentRound++
                        initialTimeInMillis = studyDurationInMillis
                        timeLeftInMillis = initialTimeInMillis
                        tvStatus.text = "Study Round $currentRound / $totalRounds | Limit: $distractionLimit"
                        Toast.makeText(requireContext(), "Break finished. Next study round started.", Toast.LENGTH_SHORT).show()
                        startTimer()
                        return
                    }
                } else {
                    recordCompletedSession()
                    tvStatus.text = "Normal timer finished!"
                    Toast.makeText(requireContext(), "Timer finished!", Toast.LENGTH_SHORT).show()
                }

                updateButtons()
                updateUIState()
            }
        }.start()

        timerRunning = true
        updateButtons()
        updateUIState()
    }

    private fun recordCompletedSession() {
        val studyMinutes = if (isPomodoroMode) {
            (studyDurationInMillis / 60000L) * totalRounds
        } else {
            initialTimeInMillis / 60000L
        }

        statisticsRepository.recordSession(
            StudySessionRecord(
                studyMinutes = studyMinutes,
                interruptionCount = distractionCount.toLong(),
                interruptedMinutes = 0,
                completedSessions = if (isPomodoroMode) totalRounds.toLong() else 1L,
                status = StudySessionStatus.COMPLETED,
                mode = if (isPomodoroMode) StudySessionMode.POMODORO else StudySessionMode.NORMAL
            )
        )
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        updateButtons()
        updateUIState()
    }

    fun registerDistraction() {
        if (!isPomodoroMode || sessionInvalidated) return

        distractionCount++

        if (distractionCount >= distractionLimit) {
            sessionInvalidated = true
            pauseTimer()
            tvStatus.text = "Session failed: distraction limit reached"
            Toast.makeText(requireContext(), "Distraction limit reached. Session invalidated.", Toast.LENGTH_SHORT).show()
        } else {
            tvStatus.text = "Warning: $distractionCount / $distractionLimit distractions"
        }

        updateUIState()
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        timerRunning = false

        distractionCount = 0
        sessionInvalidated = false

        if (isPomodoroMode) {
            currentRound = 1
            isBreakTime = false
            initialTimeInMillis = studyDurationInMillis
            timeLeftInMillis = initialTimeInMillis
            tvStatus.text = "Mode: Pomodoro"
        } else {
            timeLeftInMillis = 0
            tvStatus.text = "Mode: Normal"
        }

        updateTimerText()
        updateButtons()
        updateUIState()
    }

    private fun resetSessionState() {
        countDownTimer?.cancel()
        timerRunning = false
        timeLeftInMillis = 0
        initialTimeInMillis = 0
        currentRound = 1
        updateTimerText()
        updateButtons()
    }

    private fun updateTimerText() {
        val totalSeconds = timeLeftInMillis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val timeFormatted = String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            hours, minutes, seconds
        )
        tvTimer.text = timeFormatted
    }

    private fun updateButtons() {
        btnStart.isEnabled = !timerRunning
        btnPause.isEnabled = timerRunning
        btnReset.isEnabled = timerRunning || timeLeftInMillis > 0
    }

    private fun updateUIState() {
        when {
            sessionInvalidated -> {
                homeContainer.setBackgroundColor(Color.parseColor("#FFEBEE")) // light red
            }
            timerRunning && isBreakTime -> {
                homeContainer.setBackgroundColor(Color.parseColor("#E3F2FD")) // light blue
            }
            timerRunning -> {
                homeContainer.setBackgroundColor(Color.parseColor("#E8F5E9")) // light green
            }
            isPomodoroMode -> {
                homeContainer.setBackgroundColor(Color.parseColor("#FFF8E1")) // light yellow
            }
            else -> {
                homeContainer.setBackgroundColor(Color.WHITE)
            }
        }
    }
}
