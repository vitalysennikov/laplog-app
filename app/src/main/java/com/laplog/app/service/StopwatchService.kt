package com.laplog.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.laplog.app.MainActivity
import com.laplog.app.R
import kotlinx.coroutines.*

class StopwatchService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenDimWakeLock: PowerManager.WakeLock? = null

    private var startTime = 0L
    private var accumulatedTime = 0L
    private var isRunning = false
    private var lapCount = 0
    private var lastLapTime = 0L
    private var useScreenDimWakeLock = false

    // Fixed timestamp for stable notification sorting
    private val notificationCreationTime = System.currentTimeMillis()

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "stopwatch_channel"
        const val ACTION_START = "com.laplog.app.START"
        const val ACTION_PAUSE = "com.laplog.app.PAUSE"
        const val ACTION_STOP = "com.laplog.app.STOP"
        const val ACTION_UPDATE_STATE = "com.laplog.app.UPDATE_STATE"
        const val ACTION_LAP = "com.laplog.app.LAP"
        const val ACTION_RESUME = "com.laplog.app.RESUME"
        const val ACTION_REQUEST_STATE = "com.laplog.app.REQUEST_STATE"

        // Broadcast actions for MainActivity
        const val BROADCAST_PAUSE = "com.laplog.app.BROADCAST_PAUSE"
        const val BROADCAST_RESUME = "com.laplog.app.BROADCAST_RESUME"
        const val BROADCAST_LAP = "com.laplog.app.BROADCAST_LAP"
        const val BROADCAST_STOP = "com.laplog.app.BROADCAST_STOP"
        const val BROADCAST_STATE_UPDATE = "com.laplog.app.BROADCAST_STATE_UPDATE"

        const val EXTRA_ELAPSED_TIME = "elapsed_time"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_LAP_COUNT = "lap_count"
        const val EXTRA_LAST_LAP_TIME = "last_lap_time"
        const val EXTRA_USE_SCREEN_DIM = "use_screen_dim"

        private const val REQUEST_CODE_PAUSE = 100
        private const val REQUEST_CODE_LAP = 101
        private const val REQUEST_CODE_STOP = 102
        private const val REQUEST_CODE_RESUME = 103
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        // PARTIAL_WAKE_LOCK: keeps CPU running, allows screen to dim/turn off
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LapLog::StopwatchWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        // SCREEN_DIM_WAKE_LOCK: keeps screen on but allows it to dim (for ALWAYS mode)
        // Deprecated but still works and is the correct solution for this use case
        @Suppress("DEPRECATION")
        screenDimWakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_DIM_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LapLog::ScreenDimWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                startTime = System.currentTimeMillis()
                isRunning = true
                useScreenDimWakeLock = intent.getBooleanExtra(EXTRA_USE_SCREEN_DIM, false)
                startForeground(NOTIFICATION_ID, buildNotification())
                startNotificationUpdates()

                // Acquire appropriate wake lock based on screen mode
                if (useScreenDimWakeLock) {
                    // ALWAYS mode: allow screen to dim but keep it on
                    screenDimWakeLock?.acquire()
                } else {
                    // WHILE_RUNNING mode: just keep CPU active
                    wakeLock?.acquire()
                }

                // Send broadcast to MainActivity
                if (intent.action == ACTION_RESUME) {
                    sendBroadcast(Intent(BROADCAST_RESUME))
                }
            }
            ACTION_PAUSE -> {
                isRunning = false
                accumulatedTime += System.currentTimeMillis() - startTime
                stopNotificationUpdates()
                updateNotification()

                // Release all wake locks when paused
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
                screenDimWakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }

                // Send broadcast to MainActivity
                sendBroadcast(Intent(BROADCAST_PAUSE))
            }
            ACTION_STOP -> {
                // Release all wake locks when stopped
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
                screenDimWakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }

                // Send broadcast to MainActivity
                sendBroadcast(Intent(BROADCAST_STOP))

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_LAP -> {
                // Send broadcast to MainActivity
                sendBroadcast(Intent(BROADCAST_LAP))

                // Lap action - just update notification
                // Actual lap logic is handled in ViewModel
                updateNotification()
            }
            ACTION_UPDATE_STATE -> {
                accumulatedTime = intent.getLongExtra(EXTRA_ELAPSED_TIME, 0L)
                isRunning = intent.getBooleanExtra(EXTRA_IS_RUNNING, false)
                lapCount = intent.getIntExtra(EXTRA_LAP_COUNT, 0)
                lastLapTime = intent.getLongExtra(EXTRA_LAST_LAP_TIME, 0L)

                if (isRunning) {
                    startTime = System.currentTimeMillis()
                }

                updateNotification()
            }
            ACTION_REQUEST_STATE -> {
                // Broadcast current state to ViewModel
                broadcastCurrentState()
            }
        }

        return START_STICKY
    }

    private fun broadcastCurrentState() {
        val currentTime = if (isRunning) {
            accumulatedTime + (System.currentTimeMillis() - startTime)
        } else {
            accumulatedTime
        }

        val intent = Intent(BROADCAST_STATE_UPDATE).apply {
            putExtra(EXTRA_ELAPSED_TIME, currentTime)
            putExtra(EXTRA_IS_RUNNING, isRunning)
            putExtra(EXTRA_LAP_COUNT, lapCount)
            putExtra(EXTRA_LAST_LAP_TIME, lastLapTime)
        }
        sendBroadcast(intent)
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive && isRunning) {
                updateNotification()
                delay(1000) // Update every second
            }
        }
    }

    private fun stopNotificationUpdates() {
        notificationJob?.cancel()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val currentTime = if (isRunning) {
            accumulatedTime + (System.currentTimeMillis() - startTime)
        } else {
            accumulatedTime
        }

        val timeString = formatTime(currentTime)

        // Build lap info string
        val lapInfo = if (lapCount > 0) {
            val lapDuration = currentTime - lastLapTime
            getString(R.string.notification_lap_info, lapCount, formatTime(lapDuration))
        } else {
            ""
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create action intents
        val pauseResumeIntent = if (isRunning) {
            Intent(this, StopwatchService::class.java).apply { action = ACTION_PAUSE }
        } else {
            Intent(this, StopwatchService::class.java).apply { action = ACTION_RESUME }
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this,
            if (isRunning) REQUEST_CODE_PAUSE else REQUEST_CODE_RESUME,
            pauseResumeIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lapIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_LAP }
        val lapPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_LAP,
            lapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeIcon = if (isRunning) {
            R.drawable.ic_notification_pause
        } else {
            R.drawable.ic_notification_play
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(timeString)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$timeString\n$lapInfo".trim()))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Lower priority to prevent jumping
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(notificationCreationTime)  // Fixed time for stable sorting
            .setShowWhen(false)  // Don't show timestamp
            .setSortKey("laplog_stopwatch")  // Stable sort key
            .addAction(
                pauseResumeIcon,
                "",  // Empty string instead of null for icon visibility
                pauseResumePendingIntent
            )
            .addAction(
                R.drawable.ic_notification_lap,
                "",  // Empty string instead of null for icon visibility
                lapPendingIntent
            )
            .addAction(
                R.drawable.ic_notification_stop,
                "",  // Empty string instead of null for icon visibility
                stopPendingIntent
            )
            .build()
    }

    private fun formatTime(timeInMillis: Long): String {
        val hours = (timeInMillis / 3600000).toInt()
        val minutes = ((timeInMillis % 3600000) / 60000).toInt()
        val seconds = ((timeInMillis % 60000) / 1000).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.stopwatch_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.stopwatch_notification_channel_description)
                setShowBadge(false)
                setSound(null, null)  // Silent notification
                enableVibration(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        // Make sure to release all wake locks on service destruction
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        screenDimWakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
