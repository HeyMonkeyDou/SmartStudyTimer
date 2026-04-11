package com.group10.smartstudytimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat

class DistractionDetectorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var overlayView: android.view.View? = null
    private var overlayVisible = false
    private var lastCheckedTime = 0L

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_UPDATE_TIME = "ACTION_UPDATE_TIME"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_LABEL = "label"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "distraction_detector_channel"

        // Set to true by this service when a distraction is detected.
        // Home fragment reads and resets this flag in onResume().
        @Volatile var distractionDetectedByService = false
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
                val notification = buildNotification("Study session running", "")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                handler.post(pollRunnable)
            }
            ACTION_UPDATE_TIME -> {
                val timeText = intent.getStringExtra(EXTRA_TIME_TEXT) ?: return START_NOT_STICKY
                val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
                updateNotificationContent(label, timeText)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollRunnable)
        hideOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Detection ────────────────────────────────────────────────────────────

    private fun checkForegroundApp() {
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
        // Stop polling; we'll restart it after the user returns.
        handler.removeCallbacks(pollRunnable)

        if (Settings.canDrawOverlays(this)) {
            showOverlay()
        } else {
            // No overlay permission: set the flag so Home picks it up on resume.
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
        ).apply {
            gravity = Gravity.TOP
        }

        overlayView?.findViewById<Button>(R.id.btnBackToStudy)?.setOnClickListener {
            distractionDetectedByService = true
            hideOverlay()
            // Bring our app to the foreground
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors app usage during study sessions"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun updateNotificationContent(label: String, timeText: String) {
        val title = if (label.isNotEmpty()) "Study Timer — $label" else "Study Session Active"
        val text = if (timeText.isNotEmpty()) "Time remaining: $timeText" else "Distraction detection is running"
        val notification = buildNotification(title, text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val displayTitle = title.ifEmpty { "Study Session Active" }
        val displayText = text.ifEmpty { "Distraction detection is running" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
