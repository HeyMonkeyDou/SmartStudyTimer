package com.group10.smartstudytimer

import android.Manifest
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Locale
import kotlin.math.sqrt

class Home : Fragment(), SensorEventListener {

    private val statisticsRepository: StatisticsRepository by lazy {
        StatisticsRepository.getInstance(requireContext())
    }

    // ── Consolidated permission flow ─────────────────────────────────────────
    private var permissionCallback: (() -> Unit)? = null

    private val standardPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { checkSpecialPermissions() }

    private val usageStatsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { checkOverlayAndFinish() }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionCallback?.invoke()
        permissionCallback = null
    }

    /**
     * Request all permissions needed for a study session in one consolidated flow.
     * Standard runtime permissions (POST_NOTIFICATIONS, RECORD_AUDIO) are batched first,
     * then special permissions (Usage Stats → Overlay) are handled sequentially.
     * [onDone] is called once the flow completes (whether granted or skipped).
     */
    private fun requestAllPermissions(onDone: () -> Unit) {
        permissionCallback = onDone
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (needed.isNotEmpty()) {
            standardPermLauncher.launch(needed.toTypedArray())
        } else {
            checkSpecialPermissions()
        }
    }

    private fun checkSpecialPermissions() {
        if (!hasUsageStatsPermission()) {
            AlertDialog.Builder(requireContext())
                .setTitle("Permission Required")
                .setMessage("To detect app switching during your study session, please grant Usage Access permission in Settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    usageStatsLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("Skip") { _, _ -> checkOverlayAndFinish() }
                .show()
            return
        }
        checkOverlayAndFinish()
    }

    private fun checkOverlayAndFinish() {
        if (!Settings.canDrawOverlays(requireContext())) {
            AlertDialog.Builder(requireContext())
                .setTitle("Overlay Permission")
                .setMessage("For an immediate warning when you switch apps, grant 'Display over other apps' permission.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                .setNegativeButton("Skip") { _, _ ->
                    permissionCallback?.invoke()
                    permissionCallback = null
                }
                .show()
            return
        }
        permissionCallback?.invoke()
        permissionCallback = null
    }

    private lateinit var rootLayout: LinearLayout
    private lateinit var radioGroupMode: RadioGroup
    private lateinit var radioNormal: RadioButton
    private lateinit var radioPomodoro: RadioButton

    private lateinit var layoutTimeInput: LinearLayout
    private lateinit var layoutHoursInput: LinearLayout
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

    // sensor and accelerometer variables
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private val MOVEMENT_THRESHOLD = 2.0f

    private lateinit var btnVoiceCommand: Button
    private lateinit var speechRecognizer: SpeechRecognizer

    private var interruptedTimeInMillis: Long = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize sensors
        sensorManager = requireActivity().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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
                    requestAllPermissions { startTimer() }
                }
            }
        }

        btnPause.setOnClickListener {
            pauseTimer()
        }

        btnReset.setOnClickListener {
            resetTimer()
        }

        // Initialize voice control
        setupVoiceControl()
    }

    override fun onResume() {
        super.onResume()
        // Check if the background service detected an app switch while we were away.
        if (DistractionDetectorService.distractionDetectedByService) {
            DistractionDetectorService.distractionDetectedByService = false
            handleServiceDetectedDistraction(
                DistractionDetectorService.consumeInterruptionDurationMillis()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopDistractionService()
    }

    private fun bindViews(view: View) {
        rootLayout = view.findViewById(R.id.rootLayout)
        radioGroupMode = view.findViewById(R.id.radioGroupMode)
        radioNormal = view.findViewById(R.id.radioNormal)
        radioPomodoro = view.findViewById(R.id.radioPomodoro)

        layoutTimeInput = view.findViewById(R.id.layoutTimeInput)
        layoutHoursInput = view.findViewById(R.id.layoutHoursInput)
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

        // Bind voice control button
        btnVoiceCommand = view.findViewById(R.id.btnVoiceCommand)
    }

    private fun setupModeSelection() {
        radioGroupMode.setOnCheckedChangeListener { _, checkedId ->
            isPomodoroMode = checkedId == R.id.radioPomodoro
            tvStatus.text = if (isPomodoroMode) "Mode: Pomodoro" else "Mode: Normal"
            resetSessionState()
            updateInputVisibility()
            updateUIState()
        }
    }

    private fun updateInputVisibility() {
        if (timerRunning) {
            layoutTimeInput.visibility = View.GONE
            layoutPomodoroOptions.visibility = View.GONE
        } else {
            layoutTimeInput.visibility = View.VISIBLE
            layoutHoursInput.visibility = if (isPomodoroMode) View.GONE else View.VISIBLE
            layoutPomodoroOptions.visibility = if (isPomodoroMode) View.VISIBLE else View.GONE
        }
    }

    private fun setupSession() {
        distractionCount = 0
        sessionInvalidated = false
        interruptedTimeInMillis = 0

        // 1. Read the time input from the user
        val hoursText = inputHours.text.toString()
        val minutesText = inputMinutes.text.toString()
        val hours = if (hoursText.isNotEmpty()) hoursText.toLong() else 0L
        val minutes = if (minutesText.isNotEmpty()) minutesText.toLong() else 0L
        val inputTimeInMillis = (hours * 3600 + minutes * 60) * 1000L

        if (isPomodoroMode) {
            // Read the number of sessions set by user
            val roundsText = inputRounds.text.toString()
            totalRounds = if (roundsText.isNotEmpty()) roundsText.toInt() else 1
            if (totalRounds <= 0) totalRounds = 1

            // Read the configured distraction limit
            val distractionText = inputDistractionLimit.text.toString()
            distractionLimit = if (distractionText.isNotEmpty()) distractionText.toInt() else 3
            if (distractionLimit > 5) distractionLimit = 5
            if (distractionLimit < 1) distractionLimit = 1

            // 2. Set the time entered by the user as the duration of a single focus session on the Pomodoro timer
            // Give a 25-minute session if the user didn't enter a valid time
            studyDurationInMillis = if (inputTimeInMillis > 0) {
                inputTimeInMillis
            } else {
                25 * 60 * 1000L
            }

            currentRound = 1
            isBreakTime = false
            initialTimeInMillis = studyDurationInMillis
            timeLeftInMillis = initialTimeInMillis
            tvStatus.text = "Study Round $currentRound / $totalRounds | Limit: $distractionLimit"

        } else {
            // Normal mode logic
            initialTimeInMillis = inputTimeInMillis

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
                if (!isBreakTime) {
                    updateServiceNotification()
                }
            }

            override fun onFinish() {
                timerRunning = false
                timeLeftInMillis = 0
                updateTimerText()

                if (isPomodoroMode) {
                    if (!isBreakTime) {
                        if (currentRound < totalRounds) {
                            stopDistractionService()  // study round ended → pause monitoring during break
                            isBreakTime = true
                            initialTimeInMillis = breakDurationInMillis
                            timeLeftInMillis = initialTimeInMillis
                            tvStatus.text = "Break Time before Round ${currentRound + 1}"
                            Toast.makeText(requireContext(), "Study round finished. Break started.", Toast.LENGTH_SHORT).show()
                            startTimer()
                            return
                        } else {
                            stopDistractionService()  // all rounds done
                            recordCompletedSession()
                            tvStatus.text = "Pomodoro finished!"
                            Toast.makeText(requireContext(), "All Pomodoro rounds completed!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        isBreakTime = false
                        currentRound++
                        initialTimeInMillis = studyDurationInMillis
                        timeLeftInMillis = initialTimeInMillis
                        tvStatus.text = "Study Round $currentRound / $totalRounds | Limit: $distractionLimit"
                        Toast.makeText(requireContext(), "Break finished. Next study round started.", Toast.LENGTH_SHORT).show()
                        startTimer()  // startDistractionService() is called inside startTimer for study rounds
                        return
                    }
                } else {
                    recordCompletedSession()
                    tvStatus.text = "Normal timer finished!"
                    Toast.makeText(requireContext(), "Timer finished!", Toast.LENGTH_SHORT).show()
                }

                updateInputVisibility()
                updateButtons()
                updateUIState()

                (requireActivity() as MainActivity).setBottomNavVisibility(true)
            }
        }.start()

        timerRunning = true
        (requireActivity() as MainActivity).setBottomNavVisibility(false)

        // Hide inputs and dismiss keyboard so they don't reappear when returning from another app
        updateInputVisibility()
        requireView().clearFocus()
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)

        if (isPomodoroMode && !isBreakTime) {
            accelerometer?.let { sensor ->
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }
        startDistractionService()
        // Push an immediate notification update so the lock screen shows correct
        // data from the very first second (not the placeholder "00:00:00").
        updateServiceNotification()

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
                interruptedSeconds = interruptedTimeInMillis / 1000L,
                completedSessions = if (isPomodoroMode) totalRounds.toLong() else 1L,
                status = StudySessionStatus.COMPLETED,
                mode = if (isPomodoroMode) StudySessionMode.POMODORO else StudySessionMode.NORMAL
            )
        )
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        sensorManager.unregisterListener(this)
        stopDistractionService()
        updateInputVisibility()
        updateButtons()
        updateUIState()
    }

    fun registerDistraction(interruptionDurationMillis: Long = 0L) {
        if (!isPomodoroMode || sessionInvalidated) return

        distractionCount++
        interruptedTimeInMillis += interruptionDurationMillis.coerceAtLeast(0L)

        if (distractionCount >= distractionLimit) {
            sessionInvalidated = true
            pauseTimer()
            tvStatus.text = "Session failed: distraction limit reached"
            Toast.makeText(
                requireContext(),
                "Distraction limit reached. Session invalidated.",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val interruptedSeconds = interruptedTimeInMillis / 1000
            tvStatus.text = "Warning: $distractionCount / $distractionLimit distractions | Interrupted: ${interruptedSeconds}s"
        }

        updateUIState()
    }

    private fun resetTimer() {
        countDownTimer?.cancel()
        timerRunning = false

        distractionCount = 0
        sessionInvalidated = false
        interruptedTimeInMillis = 0

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

        sensorManager.unregisterListener(this)
        stopDistractionService()
        updateTimerText()
        updateInputVisibility()
        updateButtons()
        updateUIState()

        (requireActivity() as MainActivity).setBottomNavVisibility(true)
    }

    private fun resetSessionState() {
        countDownTimer?.cancel()
        timerRunning = false
        timeLeftInMillis = 0
        initialTimeInMillis = 0
        currentRound = 1
        sensorManager.unregisterListener(this)
        stopDistractionService()
        updateTimerText()
        updateButtons()
        (requireActivity() as MainActivity).setBottomNavVisibility(true)
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

        updateUIState()
    }

    // Update primary color (color used on buttons)
    private fun updatePrimaryColor(colorHex: String) {
        val color = Color.parseColor(colorHex)

        val enabledColor = color
        val disabledColor = Color.parseColor("#BDBDBD") // for disabled buttons

        // Buttons Colors
        btnStart.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (btnStart.isEnabled) enabledColor else disabledColor
        )

        btnPause.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (btnPause.isEnabled) enabledColor else disabledColor
        )

        btnReset.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (btnReset.isEnabled) enabledColor else disabledColor
        )

        btnVoiceCommand.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (btnVoiceCommand.isEnabled) enabledColor else disabledColor
        )

        // Radio buttons
        val radioColorStateList = android.content.res.ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            ),
            intArrayOf(
                enabledColor,  // checked
                Color.GRAY     // unchecked
            )
        )

        radioNormal.buttonTintList = radioColorStateList
        radioPomodoro.buttonTintList = radioColorStateList
    }

    // Update color palette based on timer state
    private fun updateUIState() {
        when {
            sessionInvalidated -> {
                rootLayout.setBackgroundColor(Color.parseColor("#FFEBEE"))
                updatePrimaryColor("#95405B")
            }
            timerRunning && isBreakTime -> {
                rootLayout.setBackgroundColor(Color.parseColor("#E3F2FD"))
                updatePrimaryColor("#006487")
            }
            timerRunning -> {
                rootLayout.setBackgroundColor(Color.parseColor("#E8F5E9"))
                updatePrimaryColor("#1E6945")
            }
            isPomodoroMode -> {
                rootLayout.setBackgroundColor(Color.parseColor("#FFF8E1"))
                updatePrimaryColor("#685D05")
            }
            else -> {
                rootLayout.setBackgroundColor(Color.parseColor("#FEF7FF"))
                updatePrimaryColor("#6050A0")
            }
        }
    }

    // Sensor callback methods
    override fun onSensorChanged(event: SensorEvent) {
        if (timerRunning && isPomodoroMode && !isBreakTime && !sessionInvalidated) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration

            if (delta > MOVEMENT_THRESHOLD) {
                showMovementWarning()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    private fun showMovementWarning() {
        val interruptionStartedAt = System.currentTimeMillis()
        pauseTimer()
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Warning：Movement detected")
            .setMessage("Please put down your phone, stay focused! Distraction will be recorded")
            .setCancelable(false)
            .setPositiveButton("Done") { dialog, _ ->
                registerDistraction(System.currentTimeMillis() - interruptionStartedAt)
                if (!sessionInvalidated) {
                    startTimer()
                }
                dialog.dismiss()
            }
            .show()
    }

    // ── App-switch detection via DistractionDetectorService ──────────────────

    private fun handleServiceDetectedDistraction(interruptionDurationMillis: Long) {
        if (!isPomodoroMode || sessionInvalidated) return
        registerDistraction(interruptionDurationMillis)
        // Restart service polling for the next potential distraction
        if (!sessionInvalidated) {
            startDistractionService()
        }
    }

    private fun startDistractionService() {
        val intent = Intent(requireContext(), DistractionDetectorService::class.java).apply {
            action = DistractionDetectorService.ACTION_START
        }
        requireContext().startForegroundService(intent)
    }

    private fun stopDistractionService() {
        val intent = Intent(requireContext(), DistractionDetectorService::class.java).apply {
            action = DistractionDetectorService.ACTION_STOP
        }
        requireContext().startService(intent)
    }

    private fun updateServiceNotification() {
        val totalSeconds = timeLeftInMillis / 1000
        val hours   = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val timeText = if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        // Progress: how far through the current phase (0-1000)
        val progress = if (initialTimeInMillis > 0) {
            ((initialTimeInMillis - timeLeftInMillis) * 1000L / initialTimeInMillis).toInt()
        } else 0

        Intent(requireContext(), DistractionDetectorService::class.java).apply {
            action = DistractionDetectorService.ACTION_UPDATE_TIME
            putExtra(DistractionDetectorService.EXTRA_TIME_TEXT,         timeText)
            putExtra(DistractionDetectorService.EXTRA_LABEL,             "Round $currentRound / $totalRounds")
            putExtra(DistractionDetectorService.EXTRA_DISTRACTION_COUNT, distractionCount)
            // Non-zero limit only in Pomodoro study rounds; Normal mode passes 0 → hides counter
            putExtra(DistractionDetectorService.EXTRA_DISTRACTION_LIMIT, if (isPomodoroMode) distractionLimit else 0)
            putExtra(DistractionDetectorService.EXTRA_PROGRESS,          progress)
        }.also { requireContext().startService(it) }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireActivity().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireActivity().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // Relevant methods and variables for voice control
    private fun setupVoiceControl() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())

            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    btnVoiceCommand.text = "Listening..."
                }
                override fun onResults(results: Bundle) {
                    btnVoiceCommand.text = "🎤 Tap to Speak"
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        processVoiceCommand(matches[0].lowercase(Locale.getDefault()))
                    }
                }
                override fun onError(error: Int) {
                    btnVoiceCommand.text = "🎤 Tap to Speak"
                    Toast.makeText(requireContext(), "Voice recognition failed", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            btnVoiceCommand.setOnClickListener {
                startListening()
            }
        } else {
            btnVoiceCommand.isEnabled = false
            btnVoiceCommand.text = "Voice Control Unavailable"
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            }
            speechRecognizer.startListening(intent)
        } else {
            Toast.makeText(requireContext(), "Please grant audio permission first.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processVoiceCommand(command: String) {
        when {
            command.contains("start") || command.contains("begin") -> {
                if (btnStart.isEnabled) btnStart.performClick()
            }
            command.contains("pause") || command.contains("stop") -> {
                if (btnPause.isEnabled) btnPause.performClick()
            }
            command.contains("reset") -> {
                if (btnReset.isEnabled) btnReset.performClick()
            }
            else -> {
                Toast.makeText(requireContext(), "Command not recognized: $command", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }

    fun getInterruptedTimeInMillis(): Long {
        return interruptedTimeInMillis
    }

    fun getDistractionCount(): Int {
        return distractionCount
    }
}