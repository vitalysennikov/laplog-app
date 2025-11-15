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
import androidx.media.app.NotificationCompat.MediaStyle
import com.laplog.app.MainActivity
import com.laplog.app.R
import com.laplog.app.model.StopwatchState
import kotlinx.coroutines.*

class StopwatchService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenDimWakeLock: PowerManager.WakeLock? = null

    // Use shared StopwatchState instead of local variables
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
        const val ACTION_LAP_AND_PAUSE = "com.laplog.app.LAP_AND_PAUSE"
        const val ACTION_RESUME = "com.laplog.app.RESUME"
        const val ACTION_REQUEST_STATE = "com.laplog.app.REQUEST_STATE"

        const val EXTRA_USE_SCREEN_DIM = "use_screen_dim"

        private const val REQUEST_CODE_PAUSE = 100
        private const val REQUEST_CODE_LAP = 101
        private const val REQUEST_CODE_STOP = 102
        private const val REQUEST_CODE_RESUME = 103
        private const val REQUEST_CODE_LAP_AND_PAUSE = 104
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
            ACTION_START -> {
                StopwatchState.start()
                useScreenDimWakeLock = intent.getBooleanExtra(EXTRA_USE_SCREEN_DIM, false)
                startForeground(NOTIFICATION_ID, buildNotification())
                startNotificationUpdates()

                // Acquire appropriate wake lock based on screen mode
                if (useScreenDimWakeLock) {
                    screenDimWakeLock?.acquire()
                } else {
                    wakeLock?.acquire()
                }
            }
            ACTION_RESUME -> {
                StopwatchState.resume()
                useScreenDimWakeLock = intent.getBooleanExtra(EXTRA_USE_SCREEN_DIM, false)
                startForeground(NOTIFICATION_ID, buildNotification())
                startNotificationUpdates()

                // Acquire appropriate wake lock based on screen mode
                if (useScreenDimWakeLock) {
                    screenDimWakeLock?.acquire()
                } else {
                    wakeLock?.acquire()
                }
            }
            ACTION_PAUSE -> {
                StopwatchState.pause()
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

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_LAP -> {
                StopwatchState.addLap()
                updateNotification()
            }
            ACTION_LAP_AND_PAUSE -> {
                StopwatchState.addLap()
                StopwatchState.pause()
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
            }
            ACTION_UPDATE_STATE -> {
                // Not needed anymore - using shared StopwatchState
                updateNotification()
            }
            ACTION_REQUEST_STATE -> {
                // Not needed anymore - using shared StopwatchState
            }
        }

        return START_STICKY
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            while (isActive && StopwatchState.isRunning.value) {
                // Update elapsed time from shared state
                StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
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
        val currentTime = StopwatchState.getCurrentElapsedTime()
        val timeString = formatTime(currentTime)

        // Build lap info string
        val lapCount = StopwatchState.getLapCount()
        val lapInfo = if (lapCount > 0) {
            val lastLapTime = StopwatchState.getLastLapTime()
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

        // Combine time and lap info for notification text
        val notificationText = if (lapInfo.isNotEmpty()) {
            "$timeString\n$lapInfo"
        } else {
            timeString
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1976D2.toInt())  // Material Blue 700 - for action icon tinting
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

        // Different buttons based on state to match main app
        if (StopwatchState.isRunning.value) {
            // Running: [Pause] [Lap+Pause] [Lap]
            val pauseIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_PAUSE,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val lapAndPauseIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_LAP_AND_PAUSE }
            val lapAndPausePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_LAP_AND_PAUSE,
                lapAndPauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val lapIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_LAP }
            val lapPendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_LAP,
                lapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder
                .setStyle(MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)  // Show all 3 buttons in compact view
                )
                .addAction(
                    R.drawable.ic_notification_pause,
                    "",  // Empty string instead of null for icon visibility
                    pausePendingIntent
                )
                .addAction(
                    R.drawable.ic_notification_lap_pause,  // Filled flag icon for Lap+Pause
                    "",
                    lapAndPausePendingIntent
                )
                .addAction(
                    R.drawable.ic_notification_lap,  // Outlined flag icon for Lap only
                    "",
                    lapPendingIntent
                )
        } else {
            // Paused: [Resume] [Stop]
            val resumeIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_RESUME }
            val resumePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_RESUME,
                resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val stopIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_STOP }
            val stopPendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_STOP,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder
                .setStyle(MediaStyle()
                    .setShowActionsInCompactView(0, 1)  // Show both buttons in compact view
                )
                .addAction(
                    R.drawable.ic_notification_play,
                    "",
                    resumePendingIntent
                )
                .addAction(
                    R.drawable.ic_notification_stop,
                    "",
                    stopPendingIntent
                )
        }

        return builder.build()
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
