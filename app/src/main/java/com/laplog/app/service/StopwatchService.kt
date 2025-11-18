package com.laplog.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.laplog.app.MainActivity
import com.laplog.app.R
import com.laplog.app.model.AppState
import com.laplog.app.model.StopwatchState
import com.laplog.app.model.StopwatchCommand
import com.laplog.app.model.StopwatchCommandManager
import kotlinx.coroutines.*

class StopwatchService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var notificationJob: Job? = null
    private var stateListenerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenDimWakeLock: PowerManager.WakeLock? = null

    // Use shared StopwatchState instead of local variables
    private var useScreenDimWakeLock = false

    // Fixed timestamp for stable notification sorting
    private val notificationCreationTime = System.currentTimeMillis()

    // BroadcastReceiver for screen on/off and lock/unlock events
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    AppState.setScreenOn(true)
                    // Check if screen is locked
                    checkScreenLockState()
                    // Restart notification updates if needed (screen locked)
                    if (StopwatchState.isRunning.value && AppState.shouldUpdateNotification()) {
                        startNotificationUpdates()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    AppState.setScreenOn(false)
                    AppState.setScreenLocked(true)
                    // Stop notification updates when screen is off
                    stopNotificationUpdates()
                }
                Intent.ACTION_USER_PRESENT -> {
                    // Screen unlocked
                    AppState.setScreenLocked(false)
                    // Stop notification updates when unlocked
                    stopNotificationUpdates()
                    updateNotification()
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "stopwatch_channel"

        // Actions from ViewModel to control service
        const val ACTION_START = "com.laplog.app.START"
        const val ACTION_PAUSE = "com.laplog.app.PAUSE"
        const val ACTION_STOP = "com.laplog.app.STOP"
        const val ACTION_RESUME = "com.laplog.app.RESUME"
        const val ACTION_UPDATE_WAKE_LOCK = "com.laplog.app.UPDATE_WAKE_LOCK"

        // Actions from MainActivity for app state
        const val ACTION_APP_FOREGROUND = "com.laplog.app.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.laplog.app.APP_BACKGROUND"

        // Action for ALWAYS ON mode (screen dimming without stopwatch running)
        const val ACTION_ALWAYS_ON = "com.laplog.app.ALWAYS_ON"

        // Actions from notification buttons (user interaction)
        const val ACTION_USER_PAUSE = "com.laplog.app.USER_PAUSE"
        const val ACTION_USER_RESUME = "com.laplog.app.USER_RESUME"
        const val ACTION_USER_STOP = "com.laplog.app.USER_STOP"
        const val ACTION_USER_LAP = "com.laplog.app.USER_LAP"
        const val ACTION_USER_LAP_AND_PAUSE = "com.laplog.app.USER_LAP_AND_PAUSE"

        // Legacy actions (not used anymore)
        const val ACTION_UPDATE_STATE = "com.laplog.app.UPDATE_STATE"
        const val ACTION_LAP = "com.laplog.app.LAP"
        const val ACTION_LAP_AND_PAUSE = "com.laplog.app.LAP_AND_PAUSE"
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

        // Register screen on/off and lock/unlock broadcast receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)

        // Check initial screen lock state
        checkScreenLockState()

        // Start listening to state changes for notification updates
        startStateListener()
    }

    private fun checkScreenLockState() {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked
        AppState.setScreenLocked(isLocked)
    }

    private fun startStateListener() {
        stateListenerJob?.cancel()
        stateListenerJob = serviceScope.launch {
            // Listen to laps changes
            launch {
                StopwatchState.laps.collect {
                    // Update notification when laps change
                    updateNotification()
                }
            }

            // Listen to running state changes
            launch {
                StopwatchState.isRunning.collect {
                    // Update notification when state changes (different buttons for running/paused)
                    updateNotification()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // === Special action for ALWAYS ON mode ===
            ACTION_ALWAYS_ON -> {
                // Keep screen on with dimming in ALWAYS mode when stopwatch is stopped
                useScreenDimWakeLock = intent.getBooleanExtra(EXTRA_USE_SCREEN_DIM, false)

                // Acquire screen dim wake lock
                if (useScreenDimWakeLock) {
                    screenDimWakeLock?.acquire()
                } else {
                    wakeLock?.acquire()
                }

                // Start as foreground service with minimal notification
                val notification = buildAlwaysOnNotification()
                startForeground(NOTIFICATION_ID, notification)
            }

            // === Actions from ViewModel (service management) ===
            ACTION_START -> {
                // Service started from ViewModel, just start foreground and notifications
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
                // Service resumed from ViewModel, just update foreground and notifications
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
                // ViewModel requested pause
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
            ACTION_UPDATE_WAKE_LOCK -> {
                // Settings changed - update wake lock type
                val newUseScreenDim = intent.getBooleanExtra(EXTRA_USE_SCREEN_DIM, false)

                // Release current wake lock
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

                // Update flag and acquire new wake lock
                useScreenDimWakeLock = newUseScreenDim
                if (useScreenDimWakeLock) {
                    screenDimWakeLock?.acquire()
                } else {
                    wakeLock?.acquire()
                }
            }
            ACTION_STOP -> {
                // ViewModel requested stop
                // Stop all jobs FIRST to prevent any notification updates
                notificationJob?.cancel()
                notificationJob = null
                stateListenerJob?.cancel()
                stateListenerJob = null

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

                // Remove notification and stop service
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            // === Actions from notification buttons (user interaction) ===
            ACTION_USER_PAUSE -> {
                // Notification button pressed - send command to ViewModel
                serviceScope.launch {
                    StopwatchCommandManager.sendCommand(StopwatchCommand.Pause)
                }
                stopNotificationUpdates()
                // updateNotification() will be called automatically by stateListenerJob

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
            ACTION_USER_RESUME -> {
                // Notification button pressed - send command to ViewModel
                serviceScope.launch {
                    StopwatchCommandManager.sendCommand(StopwatchCommand.Resume)
                }
                // updateNotification() will be called automatically by stateListenerJob
                startNotificationUpdates()

                // Acquire appropriate wake lock based on screen mode
                if (useScreenDimWakeLock) {
                    screenDimWakeLock?.acquire()
                } else {
                    wakeLock?.acquire()
                }
            }
            ACTION_USER_STOP -> {
                // Stop all jobs FIRST to prevent any notification updates
                notificationJob?.cancel()
                notificationJob = null
                stateListenerJob?.cancel()
                stateListenerJob = null

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

                // Notification button pressed - send command to ViewModel
                serviceScope.launch {
                    StopwatchCommandManager.sendCommand(StopwatchCommand.Stop)
                }

                // Remove notification and stop service immediately
                // (don't wait for command to be processed)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_USER_LAP -> {
                // Notification button pressed - send command to ViewModel
                serviceScope.launch {
                    StopwatchCommandManager.sendCommand(StopwatchCommand.Lap)
                }
                // updateNotification() will be called automatically by stateListenerJob when lap is added
            }
            ACTION_USER_LAP_AND_PAUSE -> {
                // Notification button pressed - send command to ViewModel
                serviceScope.launch {
                    StopwatchCommandManager.sendCommand(StopwatchCommand.LapAndPause)
                }
                stopNotificationUpdates()
                // updateNotification() will be called automatically by stateListenerJob

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

            // === Actions from MainActivity (app state changes) ===
            ACTION_APP_FOREGROUND -> {
                // App came to foreground - stop notification updates, show static text
                // Only process if service is running (in foreground mode)
                if (StopwatchState.elapsedTime.value > 0 || StopwatchState.isRunning.value) {
                    stopNotificationUpdates()
                    updateNotification() // Update once with static text
                }
            }
            ACTION_APP_BACKGROUND -> {
                // App went to background
                // Start service as foreground if stopwatch has activity
                if (StopwatchState.elapsedTime.value > 0 || StopwatchState.isRunning.value) {
                    // Ensure service is in foreground mode with notification
                    startForeground(NOTIFICATION_ID, buildNotification())

                    // Start notification updates if running and screen on
                    if (StopwatchState.isRunning.value && AppState.shouldUpdateNotification()) {
                        startNotificationUpdates()
                    }
                }
            }

            // === Legacy actions (backward compatibility) ===
            ACTION_UPDATE_STATE -> {
                updateNotification()
            }
            ACTION_REQUEST_STATE -> {
                // Not needed anymore - using shared StopwatchState
            }
            ACTION_LAP, ACTION_LAP_AND_PAUSE -> {
                // Legacy actions - handled by new ACTION_USER_* actions
                updateNotification()
            }
        }

        return START_STICKY
    }

    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            // Update every 2 seconds for better battery life
            while (isActive && StopwatchState.isRunning.value) {
                // Only update notification display - don't modify StopwatchState
                // Time is managed exclusively by ViewModel to avoid race conditions

                // Only update if app is in background and screen is on
                if (AppState.shouldUpdateNotification()) {
                    updateNotification()
                }

                delay(2000) // Update every 2 seconds (optimized for battery)
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
        val isRunning = StopwatchState.isRunning.value
        val isScreenLocked = AppState.isScreenLocked.value

        // Build notification text based on screen lock state
        val notificationText = if (isScreenLocked) {
            // Screen is locked - show current time
            timeString
        } else {
            // Screen is unlocked - show static text with Unicode symbol
            if (isRunning) {
                "⏱ Stopwatch running"
            } else {
                "⏸ Stopwatch paused"
            }
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1976D2.toInt())  // Material Blue 700 - for action icon tinting
            .setOngoing(true)  // Cannot swipe away - use Stop button instead
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // Default priority
            .setCategory(NotificationCompat.CATEGORY_SERVICE)  // Use SERVICE category to prevent swiping
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(notificationCreationTime)  // Fixed time for stable sorting
            .setShowWhen(false)  // Don't show timestamp
            .setSortKey("laplog_stopwatch")  // Stable sort key
            .setAutoCancel(false)  // Don't auto-cancel on click

        // Different buttons based on state to match main app
        if (StopwatchState.isRunning.value) {
            // Running: [Pause] [Lap+Pause] [Lap]
            val pauseIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_USER_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_PAUSE,
                pauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val lapAndPauseIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_USER_LAP_AND_PAUSE }
            val lapAndPausePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_LAP_AND_PAUSE,
                lapAndPauseIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val lapIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_USER_LAP }
            val lapPendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_LAP,
                lapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder
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
            val resumeIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_USER_RESUME }
            val resumePendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_RESUME,
                resumeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val stopIntent = Intent(this, StopwatchService::class.java).apply { action = ACTION_USER_STOP }
            val stopPendingIntent = PendingIntent.getService(
                this,
                REQUEST_CODE_STOP,
                stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            builder
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

        // Always use MediaStyle to keep action buttons visible in compact view
        val mediaStyle = MediaStyle()

        if (StopwatchState.isRunning.value) {
            // Running: show all 3 buttons
            mediaStyle.setShowActionsInCompactView(0, 1, 2)
        } else {
            // Paused: show 2 buttons
            mediaStyle.setShowActionsInCompactView(0, 1)
        }

        builder.setStyle(mediaStyle)

        // Show lap count in content info (right side) if there are laps
        val laps = StopwatchState.laps.value
        if (laps.isNotEmpty()) {
            builder.setContentInfo("${laps.size}")
        }

        return builder.build()
    }

    private fun buildAlwaysOnNotification(): Notification {
        // Minimal notification for ALWAYS ON mode (screen stays on with dimming)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Screen stays on")
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1976D2.toInt())
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Low priority for this notification
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setWhen(notificationCreationTime)
            .setShowWhen(false)
            .setSortKey("laplog_screen_on")
            .setAutoCancel(false)
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
        notificationJob?.cancel()
        stateListenerJob?.cancel()
        serviceScope.cancel()

        // Unregister screen receiver
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }

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
