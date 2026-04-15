package com.group10.smartstudytimer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.PowerManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.ListenerRegistration
import java.time.LocalDate
import java.util.Locale
import kotlin.math.sqrt

class StudyRoomFragment : Fragment(), SensorEventListener {

    companion object {
        private const val ARG_ROOM_ID = "roomId"
        private const val ARG_AVATAR_ID = "avatarId"
        private const val MOVEMENT_THRESHOLD = 2.0f

        fun newInstance(roomId: String, avatarId: String = ""): StudyRoomFragment {
            return StudyRoomFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROOM_ID, roomId)
                    putString(ARG_AVATAR_ID, avatarId)
                }
            }
        }
    }

    private val repo by lazy { StudyRoomRepository() }
    private val statsRepo by lazy { StatisticsRepository.getInstance(requireContext()) }
    private val firebaseRepo by lazy { FirebaseRepository() }

    // ── Arguments ────────────────────────────────────────────────────────────
    private lateinit var roomId: String
    private lateinit var myAvatarId: String
    private lateinit var myUid: String

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var tvRoomCode: TextView
    private lateinit var tvPhaseLabel: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvMultiplierHint: TextView
    private lateinit var tvDistractionCount: TextView
    private lateinit var containerParticipants: LinearLayout
    private lateinit var tvNoParticipants: TextView
    private lateinit var layoutInvite: LinearLayout
    private lateinit var inputInviteUsername: TextInputEditText
    private lateinit var containerFriendsList: LinearLayout
    private lateinit var btnReady: Button
    private lateinit var btnExit: Button
    private lateinit var rootLayout: View

    // ── Sensor (accelerometer) ───────────────────────────────────────────────
    private lateinit var sensorManager: SensorManager
    private var lastAcceleration = 0f
    private var currentAcceleration = 0f
    private var distractionDialogShowing = false

    // ── State ────────────────────────────────────────────────────────────────
    private var currentRoom: StudyRoom? = null
    private var participants: List<RoomParticipant> = emptyList()
    private var allFriends: List<FriendProfile> = emptyList()
    private var localDistractionCount = 0
    private var sessionEnded = false
    private var wasAppBackgrounded = false

    // ── Timers & Listeners ───────────────────────────────────────────────────
    private var countdownTimer: CountDownTimer? = null
    private var sessionTimer: CountDownTimer? = null
    private var roomListener: ListenerRegistration? = null
    private var participantsListener: ListenerRegistration? = null

    // ── Fragment lifecycle ───────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        roomId = arguments?.getString(ARG_ROOM_ID) ?: ""
        myAvatarId = arguments?.getString(ARG_AVATAR_ID) ?: ""
        myUid = repo.getCurrentUid() ?: ""
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_study_room, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupToolbar(view)
        setupButtons()
        tvRoomCode.text = "Room Code: ${roomId.takeLast(6).uppercase(Locale.ROOT)}"
        (activity as? MainActivity)?.setBottomNavVisibility(false)
        startListening()
        loadFriendsForInvite()
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        if (currentRoom?.status == "ACTIVE" && !sessionEnded) {
            // Only count as distraction if screen is still on (not just turned off)
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isInteractive) {
                wasAppBackgrounded = true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentRoom?.status == "ACTIVE" && !sessionEnded) {
            registerAccelerometer()
        }
        if (wasAppBackgrounded && currentRoom?.status == "ACTIVE" && !sessionEnded) {
            wasAppBackgrounded = false
            showDistractionWarning(reason = "app_switch")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        sensorManager.unregisterListener(this)
        (activity as? MainActivity)?.setBottomNavVisibility(true)
        roomListener?.remove()
        participantsListener?.remove()
        countdownTimer?.cancel()
        sessionTimer?.cancel()
    }

    // ── SensorEventListener ──────────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent) {
        if (currentRoom?.status != "ACTIVE" || sessionEnded || distractionDialogShowing) return
        val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
        lastAcceleration = currentAcceleration
        currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
        if (currentAcceleration - lastAcceleration > MOVEMENT_THRESHOLD) {
            showDistractionWarning(reason = "movement")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerAccelerometer() {
        lastAcceleration = SensorManager.GRAVITY_EARTH
        currentAcceleration = SensorManager.GRAVITY_EARTH
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    // ── View binding ─────────────────────────────────────────────────────────

    private fun bindViews(view: View) {
        rootLayout = view.findViewById(R.id.rootStudyRoom)
        tvRoomCode = view.findViewById(R.id.tvRoomCode)
        tvPhaseLabel = view.findViewById(R.id.tvPhaseLabel)
        tvTimer = view.findViewById(R.id.tvTimer)
        tvMultiplierHint = view.findViewById(R.id.tvMultiplierHint)
        tvDistractionCount = view.findViewById(R.id.tvDistractionCount)
        containerParticipants = view.findViewById(R.id.containerParticipants)
        tvNoParticipants = view.findViewById(R.id.tvNoParticipants)
        layoutInvite = view.findViewById(R.id.layoutInvite)
        inputInviteUsername = view.findViewById(R.id.inputInviteUsername)
        containerFriendsList = view.findViewById(R.id.containerFriendsList)
        btnReady = view.findViewById(R.id.btnReady)
        btnExit = view.findViewById(R.id.btnExit)
    }

    private fun setupToolbar(view: View) {
        val toolbar = view.findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener { attemptExit() }
    }

    private fun setupButtons() {
        view?.findViewById<Button>(R.id.btnCopyCode)?.setOnClickListener {
            val code = roomId.takeLast(6).uppercase(Locale.ROOT)
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Room Code", code))
            Toast.makeText(requireContext(), "Room code copied: $code", Toast.LENGTH_SHORT).show()
        }

        inputInviteUsername.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderFriendsList(s?.toString()?.trim() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        btnReady.setOnClickListener { onReadyClicked() }
        btnExit.setOnClickListener { attemptExit() }
    }

    // ── Friends list for invite ──────────────────────────────────────────────

    private fun loadFriendsForInvite() {
        firebaseRepo.loadFriends(
            onSuccess = { friends ->
                if (!isAdded) return@loadFriends
                allFriends = friends
                if (currentRoom?.status == "WAITING") renderFriendsList("")
            },
            onError = { /* silently ignore */ }
        )
    }

    private fun renderFriendsList(query: String) {
        containerFriendsList.removeAllViews()
        val alreadyIn = participants.map { it.uid }.toSet()
        val filtered = allFriends.filter { f ->
            f.uid !in alreadyIn &&
            (query.isEmpty() ||
             f.displayName.contains(query, ignoreCase = true) ||
             f.username.contains(query, ignoreCase = true))
        }
        if (filtered.isEmpty()) return
        val inflater = LayoutInflater.from(requireContext())
        filtered.forEach { friend ->
            val row = inflater.inflate(R.layout.item_study_room_member, containerFriendsList, false)
            AvatarAssets.bindAvatar(row.findViewById(R.id.ivMemberAvatar), friend.avatarId)
            val nameToShow = friend.nickname.ifBlank { friend.displayName }
            row.findViewById<TextView>(R.id.tvMemberName).text = nameToShow
            row.findViewById<TextView>(R.id.tvMemberStatus).text =
                if (friend.username.isNotBlank()) "@${friend.username}" else ""
            row.findViewById<TextView>(R.id.tvHostBadge).apply {
                visibility = View.VISIBLE
                text = "Invite"
                setBackgroundColor(android.graphics.Color.parseColor("#E8F5E9"))
                setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            }
            row.setOnClickListener {
                sendInviteTo(friend.displayName, row.findViewById(R.id.tvHostBadge))
            }
            containerFriendsList.addView(row)
        }
    }

    private fun sendInviteTo(displayName: String, badgeView: TextView) {
        badgeView.text = "Sending..."
        repo.sendRoomInvite(
            roomId = roomId,
            targetDisplayName = displayName,
            onSuccess = {
                if (!isAdded) return@sendRoomInvite
                badgeView.text = "Sent!"
                badgeView.setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                badgeView.setTextColor(android.graphics.Color.parseColor("#1565C0"))
            },
            onError = {
                if (!isAdded) return@sendRoomInvite
                badgeView.text = "Invite"
                Toast.makeText(requireContext(), it.message ?: "Failed to invite", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // ── Firestore listeners ──────────────────────────────────────────────────

    private fun startListening() {
        roomListener = repo.listenToRoom(
            roomId = roomId,
            onUpdate = { room ->
                if (!isAdded) return@listenToRoom
                val prev = currentRoom
                currentRoom = room
                if (room == null) {
                    showRoomClosedDialog()
                    return@listenToRoom
                }
                onRoomUpdated(prev, room)
            },
            onError = {
                if (!isAdded) return@listenToRoom
                Toast.makeText(requireContext(), "Connection error", Toast.LENGTH_SHORT).show()
            }
        )

        participantsListener = repo.listenToParticipants(
            roomId = roomId,
            onUpdate = { list ->
                if (!isAdded) return@listenToParticipants
                participants = list.sortedBy { it.joinedAt }
                renderParticipants()
                if (currentRoom?.status == "WAITING") {
                    renderFriendsList(inputInviteUsername.text?.toString()?.trim() ?: "")
                }
                checkHostTransitions()
            },
            onError = { /* silently ignore */ }
        )
    }

    // ── Room state machine ───────────────────────────────────────────────────

    private fun onRoomUpdated(prev: StudyRoom?, room: StudyRoom) {
        when (room.status) {
            "WAITING" -> applyWaitingUi()
            "COUNTDOWN" -> {
                if (prev?.status != "COUNTDOWN") {
                    startCountdownUi(room.countdownStartAt)
                }
            }
            "ACTIVE" -> {
                if (prev?.status != "ACTIVE") {
                    startSessionUi(room.sessionStartAt)
                }
            }
            "COMPLETED" -> {
                if (!sessionEnded) {
                    sessionEnded = true
                    showResultDialog(room.failedCount)
                }
            }
        }
    }

    // ── UI phases ────────────────────────────────────────────────────────────

    private fun applyWaitingUi() {
        tvPhaseLabel.text = "Waiting for players..."
        tvTimer.visibility = View.GONE
        tvMultiplierHint.visibility = View.GONE
        tvDistractionCount.visibility = View.GONE
        layoutInvite.visibility = if (isHost()) View.VISIBLE else View.GONE
        btnReady.visibility = View.VISIBLE
        btnExit.visibility = View.VISIBLE
        val myStatus = myParticipant()?.status ?: "JOINED"
        btnReady.text = if (myStatus == "READY") "Cancel Ready" else "Ready"
        setBackground("#F3EDF7")
    }

    private fun startCountdownUi(countdownStartAt: Long) {
        countdownTimer?.cancel()
        sessionTimer?.cancel()
        tvPhaseLabel.text = "Get ready!"
        tvTimer.visibility = View.VISIBLE
        tvMultiplierHint.visibility = View.VISIBLE
        tvDistractionCount.visibility = View.GONE
        layoutInvite.visibility = View.GONE
        btnReady.visibility = View.GONE
        btnExit.visibility = View.GONE
        setBackground("#FFF8E1")

        val elapsed = System.currentTimeMillis() - countdownStartAt
        val remaining = (StudyRoomRepository.COUNTDOWN_DURATION_MS - elapsed).coerceAtLeast(0L)

        countdownTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(ms: Long) {
                if (!isAdded) return
                tvTimer.text = ((ms / 1000) + 1).toString()
            }
            override fun onFinish() {
                if (!isAdded) return
                tvTimer.text = "0"
                // Host triggers ACTIVE transition
                if (isHost()) {
                    repo.startSession(roomId,
                        onSuccess = {},
                        onError = { if (isAdded) Toast.makeText(requireContext(), "Error starting session", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }.start()
    }

    private fun startSessionUi(sessionStartAt: Long) {
        countdownTimer?.cancel()
        sessionTimer?.cancel()
        registerAccelerometer()
        tvPhaseLabel.text = "Studying..."
        tvTimer.visibility = View.VISIBLE
        tvMultiplierHint.visibility = View.VISIBLE
        tvDistractionCount.visibility = View.VISIBLE
        tvDistractionCount.text = "Distractions: $localDistractionCount / ${StudyRoomRepository.MAX_DISTRACTIONS}"
        layoutInvite.visibility = View.GONE
        btnReady.visibility = View.GONE
        btnExit.visibility = View.VISIBLE
        btnExit.text = "Give Up"
        setBackground("#E8F5E9")

        val elapsed = System.currentTimeMillis() - sessionStartAt
        val remaining = (StudyRoomRepository.SESSION_DURATION_MS - elapsed).coerceAtLeast(0L)

        sessionTimer = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(ms: Long) {
                if (!isAdded) return
                val totalSec = ms / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                tvTimer.text = String.format(Locale.ROOT, "%02d:%02d", min, sec)
            }
            override fun onFinish() {
                if (!isAdded || sessionEnded) return
                repo.markCompleted(roomId,
                    onSuccess = {},
                    onError = {}
                )
            }
        }.start()
    }

    // ── Host transition logic ────────────────────────────────────────────────

    private fun checkHostTransitions() {
        if (!isHost()) return
        val room = currentRoom ?: return

        when (room.status) {
            "WAITING" -> {
                // All ready and at least 2 participants?
                if (participants.size >= 2 && participants.all { it.status == "READY" }) {
                    repo.startCountdown(roomId, onSuccess = {}, onError = {})
                }
            }
            "ACTIVE" -> {
                // All finished (COMPLETED or FAILED)?
                val allDone = participants.isNotEmpty() &&
                    participants.all { it.status == "COMPLETED" || it.status == "FAILED" }
                if (allDone && !sessionEnded) {
                    val failedCount = participants.count { it.status == "FAILED" }
                    repo.finalizeRoom(roomId, failedCount, onSuccess = {}, onError = {})
                }
            }
        }
    }

    // ── Participant list rendering ───────────────────────────────────────────

    private fun renderParticipants() {
        containerParticipants.removeAllViews()
        if (participants.isEmpty()) {
            tvNoParticipants.visibility = View.VISIBLE
            containerParticipants.addView(tvNoParticipants)
            return
        }
        tvNoParticipants.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())
        val room = currentRoom
        participants.forEach { p ->
            val itemView = inflater.inflate(R.layout.item_study_room_member, containerParticipants, false)
            AvatarAssets.bindAvatar(itemView.findViewById(R.id.ivMemberAvatar), p.avatarId)
            itemView.findViewById<TextView>(R.id.tvMemberName).text =
                if (p.uid == myUid) "${p.displayName} (You)" else p.displayName
            itemView.findViewById<TextView>(R.id.tvMemberStatus).text = statusLabel(p.status)
            itemView.findViewById<TextView>(R.id.tvMemberStatus).setTextColor(statusColor(p.status))

            val tvDistractions = itemView.findViewById<TextView>(R.id.tvMemberDistractions)
            if (room?.status == "ACTIVE" || room?.status == "COMPLETED") {
                tvDistractions.visibility = View.VISIBLE
                tvDistractions.text = "x${p.distractionCount}"
            } else {
                tvDistractions.visibility = View.GONE
            }

            val tvHost = itemView.findViewById<TextView>(R.id.tvHostBadge)
            tvHost.visibility = if (room?.hostUid == p.uid) View.VISIBLE else View.GONE

            containerParticipants.addView(itemView)
        }

        // Update ready button label based on current status
        val myStatus = myParticipant()?.status ?: "JOINED"
        if (currentRoom?.status == "WAITING") {
            btnReady.text = if (myStatus == "READY") "Cancel Ready" else "Ready"
        }

        // Update multiplier hint
        updateMultiplierHint()
    }

    private fun updateMultiplierHint() {
        val room = currentRoom ?: return
        if (room.status != "ACTIVE" && room.status != "COUNTDOWN") {
            tvMultiplierHint.visibility = View.GONE
            return
        }
        val failedSoFar = participants.count { it.status == "FAILED" }
        tvMultiplierHint.visibility = View.VISIBLE
        tvMultiplierHint.text = when {
            failedSoFar == 0 -> "All complete → x2.0 score!"
            failedSoFar == 1 -> "1 failed → x1.5 score. Stay strong!"
            else -> "${failedSoFar} failed → x0.85 score"
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    private fun onReadyClicked() {
        val myStatus = myParticipant()?.status ?: "JOINED"
        if (myStatus == "READY") {
            repo.cancelReady(roomId,
                onSuccess = {},
                onError = { if (isAdded) Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show() }
            )
        } else {
            repo.setReady(roomId,
                onSuccess = {},
                onError = { if (isAdded) Toast.makeText(requireContext(), "Error", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun attemptExit() {
        val status = currentRoom?.status ?: "WAITING"
        if (status == "ACTIVE" && !sessionEnded) {
            AlertDialog.Builder(requireContext())
                .setTitle("Give Up?")
                .setMessage("Leaving now counts as a failure. The group multiplier will drop. Are you sure?")
                .setPositiveButton("Give Up") { _, _ -> failAndLeave() }
                .setNegativeButton("Stay", null)
                .show()
        } else {
            leaveAndClose()
        }
    }

    private fun failAndLeave() {
        sessionEnded = true
        sessionTimer?.cancel()
        sensorManager.unregisterListener(this)
        repo.markFailed(roomId,
            onSuccess = { leaveAndClose() },
            onError = { leaveAndClose() }
        )
    }

    private fun leaveAndClose() {
        val status = currentRoom?.status ?: ""
        if (status == "WAITING" || status == "COUNTDOWN") {
            repo.leaveRoom(roomId, onSuccess = {}, onError = {})
        }
        if (isAdded) parentFragmentManager.popBackStack()
    }

    // ── Distraction handling ─────────────────────────────────────────────────

    private fun showDistractionWarning(reason: String) {
        if (sessionEnded || distractionDialogShowing || !isAdded) return
        distractionDialogShowing = true
        // Unregister accelerometer so further micro-movements don't stack more dialogs
        sensorManager.unregisterListener(this)

        val title = if (reason == "movement") "Movement Detected" else "You Left the Session"
        val message = if (reason == "movement")
            "Please put down your phone and stay focused!\nThis will be recorded as a distraction."
        else
            "You switched away from the study session.\nThis will be recorded as a distraction."

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss()
                distractionDialogShowing = false
                commitDistraction()
                // Re-register accelerometer after dismissal if still active
                if (currentRoom?.status == "ACTIVE" && !sessionEnded) {
                    registerAccelerometer()
                }
            }
            .show()
    }

    private fun commitDistraction() {
        if (sessionEnded) return
        repo.incrementDistraction(
            roomId = roomId,
            onSuccess = { newCount ->
                if (!isAdded) return@incrementDistraction
                localDistractionCount = newCount
                tvDistractionCount.text = "Distractions: $newCount / ${StudyRoomRepository.MAX_DISTRACTIONS}"
                if (newCount >= StudyRoomRepository.MAX_DISTRACTIONS) {
                    Toast.makeText(requireContext(), "Distraction limit reached! Session failed.", Toast.LENGTH_LONG).show()
                    failAndLeave()
                }
            },
            onError = { /* silently ignore */ }
        )
    }

    // ── Result dialog ────────────────────────────────────────────────────────

    private fun showResultDialog(failedCount: Int) {
        sessionTimer?.cancel()
        sensorManager.unregisterListener(this)
        if (!isAdded) return

        val multiplier = when {
            failedCount == 0 -> 2.0
            failedCount == 1 -> 1.5
            else -> 0.85
        }

        val myFinalStatus = participants.find { it.uid == myUid }?.status ?: "COMPLETED"
        val myDistractions = participants.find { it.uid == myUid }?.distractionCount ?: localDistractionCount
        val iPersonallyFailed = myFinalStatus == "FAILED"

        if (!iPersonallyFailed) {
            // Save session record
            val baseScore = StatisticsAggregator.calculateFocusScore(
                studyMinutes = 45L,
                interruptionCount = myDistractions.toLong(),
                interruptedSeconds = 0L,
                completedSessions = 1L
            )
            val finalScore = (baseScore * multiplier).toLong().coerceIn(0L, 200L)
            val record = StudySessionRecord(
                studyMinutes = 45L,
                interruptionCount = myDistractions.toLong(),
                interruptedSeconds = 0L,
                completedSessions = 1L,
                status = StudySessionStatus.COMPLETED,
                mode = StudySessionMode.NORMAL,
                note = "Multi-player room ${roomId.takeLast(6).uppercase(Locale.ROOT)}, x$multiplier"
            )
            statsRepo.recordSession(record)
            firebaseRepo.addCurrentStudySession(record, onSuccess = {}, onError = {})
            updateUserBestScore(finalScore)
        }

        val multiplierText = when {
            failedCount == 0 -> "x2.0 — Perfect! Everyone stayed focused!"
            failedCount == 1 -> "x1.5 — One member failed"
            else -> "x0.85 — $failedCount members failed"
        }

        val myBaseScore = StatisticsAggregator.calculateFocusScore(
            studyMinutes = if (iPersonallyFailed) 0L else 45L,
            interruptionCount = myDistractions.toLong(),
            interruptedSeconds = 0L,
            completedSessions = if (iPersonallyFailed) 0L else 1L
        )
        val myFinalScore = if (iPersonallyFailed) 0L else (myBaseScore * multiplier).toLong().coerceIn(0L, 200L)

        val title = if (iPersonallyFailed) "Session Failed" else "Session Complete!"
        val message = buildString {
            appendLine(multiplierText)
            appendLine()
            if (!iPersonallyFailed) {
                appendLine("Your Score: $myFinalScore pts (x$multiplier bonus)")
            } else {
                appendLine("You failed this session.")
            }
            appendLine()
            appendLine("Participants:")
            participants.forEach { p ->
                val statusIcon = when (p.status) {
                    "COMPLETED" -> "✓"
                    "FAILED" -> "✗"
                    else -> "—"
                }
                appendLine("  $statusIcon ${p.displayName} (x${p.distractionCount} distractions)")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Done") { _, _ ->
                if (isAdded) parentFragmentManager.popBackStack()
            }
            .show()
    }

    private fun showRoomClosedDialog() {
        if (!isAdded || sessionEnded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Room Closed")
            .setMessage("The study room is no longer available.")
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                if (isAdded) parentFragmentManager.popBackStack()
            }
            .show()
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun isHost() = currentRoom?.hostUid == myUid

    private fun myParticipant() = participants.find { it.uid == myUid }

    private fun statusLabel(status: String) = when (status) {
        "JOINED" -> "Waiting"
        "READY" -> "Ready!"
        "STUDYING" -> "Studying"
        "COMPLETED" -> "Completed"
        "FAILED" -> "Failed"
        else -> status
    }

    private fun statusColor(status: String) = when (status) {
        "READY" -> Color.parseColor("#2E7D32")
        "COMPLETED" -> Color.parseColor("#1565C0")
        "FAILED" -> Color.parseColor("#C62828")
        else -> Color.parseColor("#888888")
    }

    private fun setBackground(colorHex: String) {
        rootLayout.setBackgroundColor(Color.parseColor(colorHex))
    }

    private fun updateUserBestScore(finalScore: Long) {
        firebaseRepo.loadCurrentUserProfile(
            onSuccess = { profile ->
                val today = LocalDate.now().toString()
                val prevBest = profile?.bestFocusScore ?: 0L
                val newBest = maxOf(prevBest, finalScore)
                val prevToday = if (profile?.todayFocusScoreDate == today) profile.todayFocusScore else 0L
                val newToday = maxOf(prevToday, finalScore)
                val prevMinutes = if (profile?.todayFocusScoreDate == today) profile.todayStudyMinutes else 0L
                firebaseRepo.updateCurrentBestFocusScore(
                    bestFocusScore = newBest,
                    bestFocusScoreCompletedAt = if (newBest > prevBest) today else profile?.bestFocusScoreCompletedAt.orEmpty(),
                    todayFocusScore = newToday,
                    todayFocusScoreDate = today,
                    todayStudyMinutes = prevMinutes + 45L
                )
            },
            onError = {}
        )
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val focused = requireActivity().currentFocus
        if (focused != null) imm.hideSoftInputFromWindow(focused.windowToken, 0)
    }
}
