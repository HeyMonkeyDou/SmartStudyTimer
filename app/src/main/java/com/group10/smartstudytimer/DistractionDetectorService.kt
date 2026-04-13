package com.group10.smartstudytimer

import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat

class DistractionDetectorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var overlayVisible = false
    private var lastCheckedTime = 0L

    // Current session state (updated by ACTION_UPDATE_TIME)
    private var currentTimeText     = ""
    private var currentProgress     = 0       // 0-1000
    private var currentDistraction  = 0
    private var currentLimit        = 0       // 0 = Normal mode (hide distraction UI)

    companion object {
        const val ACTION_START       = "ACTION_START"
        const val ACTION_STOP        = "ACTION_STOP"
        const val ACTION_UPDATE_TIME = "ACTION_UPDATE_TIME"

        const val EXTRA_TIME_TEXT          = "time_text"
        const val EXTRA_LABEL              = "label"
        const val EXTRA_DISTRACTION_COUNT  = "distraction_count"
        const val EXTRA_DISTRACTION_LIMIT  = "distraction_limit"
        const val EXTRA_PROGRESS           = "progress"   // 0–1000

        private const val NOTIFICATION_ID = 1001
        // New channel ID forces recreation with correct importance + lock-screen visibility.
        // (Channel settings cannot be changed once a channel is created.)
        private const val CHANNEL_ID      = "study_timer_v2"

        // Set to true by this service when a distraction is detected.
        // Home fragment reads and resets this flag in onResume().
        @Volatile var distractionDetectedByService = false
        @Volatile private var interruptionStartedAtEpochMillis: Long? = null

        fun consumeInterruptionDurationMillis(resumedAtEpochMillis: Long = System.currentTimeMillis()): Long {
            val startedAt = interruptionStartedAtEpochMillis ?: return 0L
            interruptionStartedAtEpochMillis = null
            return (resumedAtEpochMillis - startedAt).coerceAtLeast(0L)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!overlayVisible) {
                checkForegroundApp()
                handler.postDelayed(this, 1000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                lastCheckedTime = System.currentTimeMillis()
                interruptionStartedAtEpochMillis = null
                val notification = buildRichNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                handler.post(pollRunnable)
            }
            ACTION_UPDATE_TIME -> {
                currentTimeText    = intent.getStringExtra(EXTRA_TIME_TEXT)   ?: ""
                currentDistraction = intent.getIntExtra(EXTRA_DISTRACTION_COUNT, 0)
                currentLimit       = intent.getIntExtra(EXTRA_DISTRACTION_LIMIT,  0)
                currentProgress    = intent.getIntExtra(EXTRA_PROGRESS,           0)
                pushNotificationUpdate()
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        if (!distractionDetectedByService) {
            interruptionStartedAtEpochMillis = null
        }
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Detection ────────────────────────────────────────────────────────────

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun checkForegroundApp() {
        if (!hasUsageStatsPermission()) return
        val usageStatsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(lastCheckedTime, now)
        lastCheckedTime = now

        val ignoredPackages = getHomeScreenPackages() + packageName
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND &&
                event.packageName !in ignoredPackages
            ) {
                onOtherAppDetected()
                break
            }
        }
    }

    private fun onOtherAppDetected() {
        handler.removeCallbacks(pollRunnable)
        interruptionStartedAtEpochMillis = System.currentTimeMillis()

        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        } else {
            distractionDetectedByService = true
        }
    }

    private fun getHomeScreenPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        return resolveInfos.map { it.activityInfo.packageName }.toSet()
    }

    // ── Overlay ───────────────────────────────────────────────────────────────

    private fun showOverlay() {
        if (overlayVisible) return
        overlayVisible = true

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_distraction_warning, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        overlayView?.findViewById<Button>(R.id.btnBackToStudy)?.setOnClickListener {
            distractionDetectedByService = true
            hideOverlay()
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (launchIntent != null) startActivity(launchIntent)
            stopSelf()
        }

        windowManager?.addView(overlayView, params)
    }

    private fun hideOverlay() {
        if (overlayVisible && overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
            overlayVisible = false
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Session Monitor",
                // IMPORTANCE_DEFAULT is the minimum level that shows reliably on the lock screen.
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows remaining time and distractions during a study session"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)      // silent – no sound on each update
                enableVibration(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    /** Push an updated notification to the system. */
    private fun pushNotificationUpdate() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildRichNotification())
    }

    /** Build the rich RemoteViews notification shown in the shade and on the lock screen. */
    private fun buildRichNotification(): Notification {
        val timeDisplay = currentTimeText.ifEmpty { "00:00:00" }

        // Tap the notification → open the app
        val tapIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlag = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent ?: Intent(), pendingFlag)

        // ── Build RemoteViews (two separate instances required) ───────────────
        fun buildViews() = RemoteViews(packageName, R.layout.notification_study_timer).also { v ->
            v.setTextViewText(R.id.notifTime, timeDisplay)
            v.setProgressBar(R.id.notifProgress, 1000, currentProgress.coerceIn(0, 1000), false)
            if (currentLimit > 0) {
                v.setViewVisibility(R.id.notifDistractionContainer, View.VISIBLE)
                v.setTextViewText(R.id.notifDistraction, "$currentDistraction/$currentLimit")
            } else {
                v.setViewVisibility(R.id.notifDistractionContainer, View.GONE)
            }
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            // Plain-text fallbacks (shown if custom view fails to render)
            .setContentTitle(timeDisplay)
            .setContentText(
                if (currentLimit > 0) "Distractions: $currentDistraction / $currentLimit"
                else "Study session active"
            )
            .setCustomContentView(buildViews())      // collapsed state in shade
            .setCustomBigContentView(buildViews())   // expanded state — shown on lock screen
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
