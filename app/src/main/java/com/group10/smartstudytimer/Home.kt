package com.group10.smartstudytimer

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.Locale
import kotlin.math.sqrt

class Home : Fragment(), SensorEventListener {

    // --- 原有变量声明 ---
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

    // --- 新增：传感器与语音变量 ---
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private val MOVEMENT_THRESHOLD = 2.0f

    private lateinit var btnVoiceCommand: Button
    private lateinit var speechRecognizer: SpeechRecognizer


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化传感器
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

        // 初始化语音控制
        setupVoiceControl()
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

        // 绑定语音按钮
        btnVoiceCommand = view.findViewById(R.id.btnVoiceCommand)
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
            }

            override fun onFinish() {
                timerRunning = false
                timeLeftInMillis = 0
                updateTimerText()

                if (isPomodoroMode) {
                    if (!isBreakTime) {
                        if (currentRound < totalRounds) {
                            isBreakTime = true
                            initialTimeInMillis = breakDurationInMillis
                            timeLeftInMillis = initialTimeInMillis
                            tvStatus.text = "Break Time before Round ${currentRound + 1}"
                            Toast.makeText(requireContext(), "Study round finished. Break started.", Toast.LENGTH_SHORT).show()
                            startTimer()
                            return
                        } else {
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
                        startTimer()
                        return
                    }
                } else {
                    tvStatus.text = "Normal timer finished!"
                    Toast.makeText(requireContext(), "Timer finished!", Toast.LENGTH_SHORT).show()
                }

                updateButtons()
                updateUIState()
            }
        }.start()

        timerRunning = true

        // 仅在番茄钟模式且不是休息时间时开启防分心检测
        if (isPomodoroMode && !isBreakTime) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }

        updateButtons()
        updateUIState()
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
        timerRunning = false
        updateButtons()
        updateUIState()

        // 暂停时注销传感器
        sensorManager.unregisterListener(this)
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

        // 重置时注销传感器
        sensorManager.unregisterListener(this)
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
                homeContainer.setBackgroundColor(Color.parseColor("#FFEBEE"))
            }
            timerRunning && isBreakTime -> {
                homeContainer.setBackgroundColor(Color.parseColor("#E3F2FD"))
            }
            timerRunning -> {
                homeContainer.setBackgroundColor(Color.parseColor("#E8F5E9"))
            }
            isPomodoroMode -> {
                homeContainer.setBackgroundColor(Color.parseColor("#FFF8E1"))
            }
            else -> {
                homeContainer.setBackgroundColor(Color.WHITE)
            }
        }
    }

    // --- 新增：传感器回调方法 ---
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
        // 留空即可
    }

    private fun showMovementWarning() {
        pauseTimer()
        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Warning：Movement detected")
            .setMessage("Please put down your phone, stay focus! Distraction will be recorded")
            .setCancelable(false)
            .setPositiveButton("Done") { dialog, _ ->
                registerDistraction()
                if (!sessionInvalidated) {
                    startTimer()
                }
                dialog.dismiss()
            }
            .show()
    }

    // --- 新增：语音控制相关方法 ---
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
        // 防止内存泄漏，销毁时释放资源
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
    }
}