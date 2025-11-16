package com.laplog.app.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.ScreenOnMode
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.LapTime
import com.laplog.app.model.StopwatchState
import com.laplog.app.model.StopwatchCommand
import com.laplog.app.model.StopwatchCommandManager
import com.laplog.app.service.StopwatchService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StopwatchViewModel(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
) : ViewModel() {
    // Use shared StopwatchState instead of local state
    val elapsedTime: StateFlow<Long> = StopwatchState.elapsedTime
    val isRunning: StateFlow<Boolean> = StopwatchState.isRunning
    val laps: StateFlow<List<LapTime>> = StopwatchState.laps

    private val _showMilliseconds = MutableStateFlow(preferencesManager.showMilliseconds)
    val showMilliseconds: StateFlow<Boolean> = _showMilliseconds.asStateFlow()

    private val _screenOnMode = MutableStateFlow(preferencesManager.screenOnMode)
    val screenOnMode: StateFlow<ScreenOnMode> = _screenOnMode.asStateFlow()

    private val _lockOrientation = MutableStateFlow(preferencesManager.lockOrientation)
    val lockOrientation: StateFlow<Boolean> = _lockOrientation.asStateFlow()

    private val _currentComment = MutableStateFlow(preferencesManager.currentComment)
    val currentComment: StateFlow<String> = _currentComment.asStateFlow()

    private val _usedComments = MutableStateFlow<Set<String>>(preferencesManager.usedComments)
    val usedComments: StateFlow<Set<String>> = _usedComments.asStateFlow()

    private val _commentsFromHistory = MutableStateFlow<List<String>>(emptyList())
    val commentsFromHistory: StateFlow<List<String>> = _commentsFromHistory.asStateFlow()

    private val _invertLapColors = MutableStateFlow(preferencesManager.invertLapColors)
    val invertLapColors: StateFlow<Boolean> = _invertLapColors.asStateFlow()

    private val _dimBrightness = MutableStateFlow(preferencesManager.dimBrightness)
    val dimBrightness: StateFlow<Boolean> = _dimBrightness.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _showBatteryDialog = MutableStateFlow(false)
    val showBatteryDialog: StateFlow<Boolean> = _showBatteryDialog.asStateFlow()

    private var timerJob: Job? = null

    init {
        loadCommentsFromHistory()
        restoreStopwatchState()

        // Listen for commands from notification service
        viewModelScope.launch {
            StopwatchCommandManager.commands.collect { command ->
                handleCommand(command)
            }
        }
    }

    private fun handleCommand(command: StopwatchCommand) {
        // Handle commands from notification service
        // Don't call service methods to avoid circular calls
        when (command) {
            StopwatchCommand.Start -> {
                StopwatchState.start()
                saveStopwatchState()
                startTimerJob()
            }
            StopwatchCommand.Pause -> {
                StopwatchState.pause()
                timerJob?.cancel()
                saveStopwatchState()
            }
            StopwatchCommand.Resume -> {
                StopwatchState.resume()
                saveStopwatchState()
                startTimerJob()
            }
            StopwatchCommand.Stop -> {
                timerJob?.cancel()
                val elapsedTime = StopwatchState.elapsedTime.value
                val laps = StopwatchState.laps.value

                // Save session to database if there was any activity
                if (elapsedTime > 0L || laps.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            saveSession()
                            loadCommentsFromHistory()
                        } catch (e: Exception) {
                            Log.e("StopwatchViewModel", "Error saving session", e)
                        }
                        StopwatchState.reset()
                        preferencesManager.clearStopwatchState()
                    }
                } else {
                    StopwatchState.reset()
                    preferencesManager.clearStopwatchState()
                }
            }
            StopwatchCommand.Lap -> {
                StopwatchState.addLap()
                saveStopwatchState()
            }
            StopwatchCommand.LapAndPause -> {
                StopwatchState.addLap()
                if (StopwatchState.isRunning.value) {
                    StopwatchState.pause()
                    timerJob?.cancel()
                    saveStopwatchState()
                }
            }
        }
    }

    private fun loadCommentsFromHistory() {
        viewModelScope.launch {
            _commentsFromHistory.value = sessionDao.getDistinctComments()
        }
    }

    fun refreshCommentsFromHistory() {
        loadCommentsFromHistory()
    }

    private fun restoreStopwatchState() {
        try {
            val savedElapsedTime = preferencesManager.stopwatchElapsedTime
            val savedIsRunning = preferencesManager.stopwatchIsRunning
            val savedSessionStartTime = preferencesManager.stopwatchSessionStartTime
            val savedAccumulatedTime = preferencesManager.stopwatchAccumulatedTime
            val savedLastUpdateTime = preferencesManager.stopwatchLastUpdateTime
            val savedLapsJson = preferencesManager.stopwatchLapsJson

            // Only restore if there's actual state to restore
            if (savedElapsedTime > 0 || savedLapsJson != null) {
                Log.d("StopwatchViewModel", "Restoring state: elapsed=$savedElapsedTime, running=$savedIsRunning")

                // Restore laps from JSON
                val restoredLaps = if (savedLapsJson != null) {
                    parseLapsFromJson(savedLapsJson)
                } else {
                    emptyList()
                }

                // If stopwatch was running, calculate elapsed time from last update
                if (savedIsRunning) {
                    val timeSinceLastUpdate = System.currentTimeMillis() - savedLastUpdateTime
                    val adjustedAccumulatedTime = savedAccumulatedTime + timeSinceLastUpdate

                    // Restore to shared state
                    StopwatchState.restore(
                        elapsedTime = adjustedAccumulatedTime,
                        isRunning = true,
                        laps = restoredLaps,
                        sessionStartTime = savedSessionStartTime,
                        accumulatedTime = adjustedAccumulatedTime
                    )

                    // Start UI update timer
                    startTimerJob()

                    // Restart the service
                    resumeService()
                } else {
                    // Stopwatch was paused
                    StopwatchState.restore(
                        elapsedTime = savedElapsedTime,
                        isRunning = false,
                        laps = restoredLaps,
                        sessionStartTime = savedSessionStartTime,
                        accumulatedTime = savedAccumulatedTime
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("StopwatchViewModel", "Error restoring stopwatch state", e)
        }
    }

    private fun saveStopwatchState() {
        try {
            preferencesManager.stopwatchElapsedTime = StopwatchState.elapsedTime.value
            preferencesManager.stopwatchIsRunning = StopwatchState.isRunning.value
            preferencesManager.stopwatchSessionStartTime = StopwatchState.sessionStartTime
            preferencesManager.stopwatchAccumulatedTime = StopwatchState.accumulatedTime
            preferencesManager.stopwatchLastUpdateTime = System.currentTimeMillis()
            preferencesManager.stopwatchLapsJson = serializeLapsToJson(StopwatchState.laps.value)
        } catch (e: Exception) {
            Log.e("StopwatchViewModel", "Error saving stopwatch state", e)
        }
    }

    private fun startTimerJob() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (StopwatchState.isRunning.value) {
                StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
                delay(1000L) // Always update once per second for better performance
            }
        }
    }

    private fun serializeLapsToJson(laps: List<LapTime>): String? {
        if (laps.isEmpty()) return null

        val jsonArray = laps.joinToString(",", "[", "]") { lap ->
            "{\"lapNumber\":${lap.lapNumber},\"totalTime\":${lap.totalTime},\"lapDuration\":${lap.lapDuration}}"
        }
        return jsonArray
    }

    private fun parseLapsFromJson(json: String): List<LapTime> {
        try {
            // Simple JSON parsing without external library
            val laps = mutableListOf<LapTime>()
            val items = json.trim().removeSurrounding("[", "]").split("},")

            for (item in items) {
                val cleanItem = item.trim().removeSurrounding("{", "}")
                val parts = cleanItem.split(",")

                var lapNumber = 0
                var totalTime = 0L
                var lapDuration = 0L

                for (part in parts) {
                    val keyValue = part.split(":")
                    if (keyValue.size == 2) {
                        val key = keyValue[0].trim().removeSurrounding("\"")
                        val value = keyValue[1].trim()

                        when (key) {
                            "lapNumber" -> lapNumber = value.toInt()
                            "totalTime" -> totalTime = value.toLong()
                            "lapDuration" -> lapDuration = value.toLong()
                        }
                    }
                }

                if (lapNumber > 0) {
                    laps.add(LapTime(lapNumber, totalTime, lapDuration))
                }
            }

            return laps
        } catch (e: Exception) {
            Log.e("StopwatchViewModel", "Error parsing laps JSON", e)
            return emptyList()
        }
    }

    fun startOrPause() {
        if (StopwatchState.isRunning.value) {
            pause()
        } else {
            // Check if notification permission is needed and granted
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (!isNotificationPermissionGranted()) {
                    // Permission not granted - request it
                    if (!preferencesManager.permissionsRequested) {
                        _showPermissionDialog.value = true
                    } else {
                        // Permission was requested before but not granted - start anyway
                        // (user can still use app, just without notifications)
                        start()
                    }
                } else {
                    // Permission granted - start normally
                    start()
                }
            } else {
                // Android < 13 - no permission needed
                start()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
        preferencesManager.permissionsRequested = true
        // Don't start automatically - wait for permission callback
    }

    fun onPermissionGranted() {
        // Called when permission is granted
        start()
    }

    fun dismissBatteryDialog() {
        _showBatteryDialog.value = false
    }

    fun showBatteryOptimizationDialog() {
        _showBatteryDialog.value = true
    }

    private fun start() {
        val isResume = StopwatchState.sessionStartTime != 0L  // True if resuming from pause

        if (isResume) {
            StopwatchState.resume()
            resumeService()
        } else {
            StopwatchState.start()
            startService()
        }

        // Save state
        saveStopwatchState()

        // Start UI update timer
        startTimerJob()
    }

    private fun pause() {
        StopwatchState.pause()
        timerJob?.cancel()

        // Save state
        saveStopwatchState()

        // Pause service
        pauseService()
    }

    fun reset() {
        timerJob?.cancel()

        // Stop service
        stopService()

        val elapsedTime = StopwatchState.elapsedTime.value
        val laps = StopwatchState.laps.value
        val sessionStartTime = StopwatchState.sessionStartTime

        Log.d("StopwatchViewModel", "Reset called. ElapsedTime: $elapsedTime, Laps: ${laps.size}, SessionStartTime: $sessionStartTime")

        // Save session to database if there was any activity
        if (elapsedTime > 0L || laps.isNotEmpty()) {
            Log.d("StopwatchViewModel", "Saving session...")
            viewModelScope.launch {
                try {
                    saveSession()
                    Log.d("StopwatchViewModel", "Session saved successfully")
                    // Reload comments from history after saving
                    loadCommentsFromHistory()
                } catch (e: Exception) {
                    Log.e("StopwatchViewModel", "Error saving session", e)
                }
                // Reset shared state
                StopwatchState.reset()

                // Clear saved state
                preferencesManager.clearStopwatchState()
            }
        } else {
            Log.d("StopwatchViewModel", "No activity to save")
            // Reset shared state
            StopwatchState.reset()

            // Clear saved state
            preferencesManager.clearStopwatchState()
        }
    }

    private suspend fun saveSession() {
        val endTime = System.currentTimeMillis()
        val sessionStartTime = StopwatchState.sessionStartTime
        val elapsedTime = StopwatchState.elapsedTime.value
        val laps = StopwatchState.laps.value

        Log.d("StopwatchViewModel", "Creating session: startTime=$sessionStartTime, endTime=$endTime, duration=$elapsedTime")

        val session = SessionEntity(
            startTime = sessionStartTime,
            endTime = endTime,
            totalDuration = elapsedTime,
            comment = _currentComment.value.trim().takeIf { it.isNotBlank() }
        )

        val sessionId = sessionDao.insertSession(session)
        Log.d("StopwatchViewModel", "Session inserted with ID: $sessionId")

        if (laps.isNotEmpty()) {
            val lapEntities = laps.map { lap ->
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = lap.lapNumber,
                    totalTime = lap.totalTime,
                    lapDuration = lap.lapDuration
                )
            }
            sessionDao.insertLaps(lapEntities)
            Log.d("StopwatchViewModel", "Inserted ${lapEntities.size} laps")
        }
    }

    fun addLap() {
        StopwatchState.addLap()

        // Save state
        saveStopwatchState()
    }

    fun addLapAndPause() {
        StopwatchState.addLap()
        if (StopwatchState.isRunning.value) {
            pause()
        }
    }


    fun toggleMillisecondsDisplay() {
        _showMilliseconds.value = !_showMilliseconds.value
        preferencesManager.showMilliseconds = _showMilliseconds.value
    }

    fun cycleScreenOnMode() {
        _screenOnMode.value = when (_screenOnMode.value) {
            ScreenOnMode.OFF -> ScreenOnMode.WHILE_RUNNING
            ScreenOnMode.WHILE_RUNNING -> ScreenOnMode.ALWAYS
            ScreenOnMode.ALWAYS -> ScreenOnMode.OFF
        }
        preferencesManager.screenOnMode = _screenOnMode.value
    }

    fun toggleLockOrientation() {
        _lockOrientation.value = !_lockOrientation.value
        preferencesManager.lockOrientation = _lockOrientation.value
    }

    fun toggleInvertLapColors() {
        _invertLapColors.value = !_invertLapColors.value
        preferencesManager.invertLapColors = _invertLapColors.value
    }

    fun toggleDimBrightness() {
        _dimBrightness.value = !_dimBrightness.value
        preferencesManager.dimBrightness = _dimBrightness.value
    }

    fun updateCurrentComment(comment: String) {
        // Don't trim during input - allow spaces inside comments
        _currentComment.value = comment
        preferencesManager.currentComment = comment

        // Add to used comments if not empty (trim for checking and storage)
        val trimmedForStorage = comment.trim()
        if (trimmedForStorage.isNotBlank() && !_usedComments.value.contains(trimmedForStorage)) {
            val updated = _usedComments.value.toMutableSet()
            updated.add(trimmedForStorage)
            _usedComments.value = updated
            preferencesManager.usedComments = updated
        }
    }

    fun formatTime(timeInMillis: Long, includeMillis: Boolean = _showMilliseconds.value, roundIfNoMillis: Boolean = true): String {
        // Apply mathematical rounding if milliseconds are not shown and rounding is enabled
        val adjustedTime = if (!includeMillis && roundIfNoMillis && timeInMillis % 1000 >= 500) {
            timeInMillis + 1000 - (timeInMillis % 1000)
        } else {
            timeInMillis
        }

        val hours = (adjustedTime / 3600000).toInt()
        val minutes = ((adjustedTime % 3600000) / 60000).toInt()
        val seconds = ((adjustedTime % 60000) / 1000).toInt()
        val millis = ((timeInMillis % 1000) / 10).toInt()

        return if (hours > 0) {
            if (includeMillis) {
                String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, millis)
            } else {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
        } else {
            if (includeMillis) {
                String.format("%02d:%02d.%02d", minutes, seconds, millis)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    fun formatDifference(diffMillis: Long, includeMillis: Boolean = _showMilliseconds.value): String {
        // Use unicode minus (U+2212) for consistent width with plus sign
        val sign = if (diffMillis >= 0) "+" else "\u2212"
        val absDiff = kotlin.math.abs(diffMillis)
        val seconds = (absDiff / 1000).toInt()
        val millis = ((absDiff % 1000) / 10).toInt()

        return if (includeMillis) {
            String.format("%s%d.%02d", sign, seconds, millis)
        } else {
            String.format("%s%d", sign, seconds)
        }
    }

    private fun startService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_START
            // Pass screen mode to service: ALWAYS mode uses screen dim wake lock
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, _screenOnMode.value == ScreenOnMode.ALWAYS)
        }
        context.startForegroundService(intent)
    }

    private fun pauseService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_PAUSE
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, _screenOnMode.value == ScreenOnMode.ALWAYS)
        }
        context.startService(intent)
    }

    private fun resumeService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_RESUME
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, _screenOnMode.value == ScreenOnMode.ALWAYS)
        }
        context.startService(intent)
    }

    private fun stopService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_STOP
        }
        context.startService(intent)
    }
}
