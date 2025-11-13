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
import androidx.core.app.NotificationCompat
import com.laplog.app.MainActivity
import com.laplog.app.R
import kotlinx.coroutines.*

class StopwatchService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null

    private var startTime = 0L
    private var accumulatedTime = 0L
    private var isRunning = false
    private var lapCount = 0
    private var lastLapTime = 0L

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "stopwatch_channel"
        const val ACTION_START = "com.laplog.app.START"
        const val ACTION_PAUSE = "com.laplog.app.PAUSE"
        const val ACTION_STOP = "com.laplog.app.STOP"
        const val ACTION_UPDATE_STATE = "com.laplog.app.UPDATE_STATE"
        const val ACTION_LAP = "com.laplog.app.LAP"
        const val ACTION_RESUME = "com.laplog.app.RESUME"

        const val EXTRA_ELAPSED_TIME = "elapsed_time"
        const val EXTRA_IS_RUNNING = "is_running"
        const val EXTRA_LAP_COUNT = "lap_count"
        const val EXTRA_LAST_LAP_TIME = "last_lap_time"

        private const val REQUEST_CODE_PAUSE = 100
        private const val REQUEST_CODE_LAP = 101
        private const val REQUEST_CODE_STOP = 102
        private const val REQUEST_CODE_RESUME = 103
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                startTime = System.currentTimeMillis()
                isRunning = true
                startForeground(NOTIFICATION_ID, buildNotification())
                startNotificationUpdates()
            }
            ACTION_PAUSE -> {
                isRunning = false
                accumulatedTime += System.currentTimeMillis() - startTime
                stopNotificationUpdates()
                updateNotification()
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_LAP -> {
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
        }

        return START_STICKY
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(timeString)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$timeString\n$lapInfo".trim()))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .addAction(
                if (isRunning) R.drawable.ic_notification else R.drawable.ic_notification,
                if (isRunning) getString(R.string.pause) else getString(R.string.resume),
                pauseResumePendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.lap),
                lapPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                getString(R.string.reset),
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
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.stopwatch_notification_channel_description)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
