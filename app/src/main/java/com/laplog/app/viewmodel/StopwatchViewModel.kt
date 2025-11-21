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

    private val _currentName = MutableStateFlow(preferencesManager.currentName)
    val currentName: StateFlow<String> = _currentName.asStateFlow()

    private val _usedNames = MutableStateFlow<Set<String>>(preferencesManager.usedNames)
    val usedNames: StateFlow<Set<String>> = _usedNames.asStateFlow()

    private val _namesFromHistory = MutableStateFlow<List<String>>(emptyList())
    val namesFromHistory: StateFlow<List<String>> = _namesFromHistory.asStateFlow()

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
        loadNamesFromHistory()
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
                // Update time one last time before pausing to show accurate milliseconds
                StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
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

                // Capture values BEFORE resetting state
                val elapsedTime = StopwatchState.elapsedTime.value
                val laps = StopwatchState.laps.value
                val sessionStartTime = StopwatchState.sessionStartTime

                // Reset state IMMEDIATELY to prevent any race conditions
                StopwatchState.reset()
                preferencesManager.clearStopwatchState()

                // Save session to database asynchronously if there was any activity
                if (elapsedTime > 0L || laps.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            saveSession(elapsedTime, laps, sessionStartTime)
                            loadNamesFromHistory()
                        } catch (e: Exception) {
                            Log.e("StopwatchViewModel", "Error saving session", e)
                        }
                    }
                }
            }
            StopwatchCommand.Lap -> {
                StopwatchState.addLap()
                saveStopwatchState()
            }
            StopwatchCommand.LapAndPause -> {
                StopwatchState.addLap()
                if (StopwatchState.isRunning.value) {
                    // Update time one last time before pausing to show accurate milliseconds
                    StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
                    StopwatchState.pause()
                    timerJob?.cancel()
                    saveStopwatchState()
                }
            }
        }
    }

    private fun loadNamesFromHistory() {
        viewModelScope.launch {
            _namesFromHistory.value = sessionDao.getDistinctNames()
        }
    }

    fun refreshNamesFromHistory() {
        loadNamesFromHistory()
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
                    // Stopwatch was paused (time > 0 but not running)
                    StopwatchState.restore(
                        elapsedTime = savedElapsedTime,
                        isRunning = false,
                        laps = restoredLaps,
                        sessionStartTime = savedSessionStartTime,
                        accumulatedTime = savedAccumulatedTime
                    )

                    // Don't start service when restoring paused state
                    // Service will be started automatically if:
                    // 1. User resumes the stopwatch (resumeService called)
                    // 2. App goes to background while paused (handled by MainActivity)
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
        // Update time one last time before pausing to show accurate milliseconds
        StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
        StopwatchState.pause()
        timerJob?.cancel()

        // Save state
        saveStopwatchState()

        // Pause service
        pauseService()
    }

    fun reset() {
        timerJob?.cancel()

        // Capture values BEFORE resetting state
        val elapsedTime = StopwatchState.elapsedTime.value
        val laps = StopwatchState.laps.value
        val sessionStartTime = StopwatchState.sessionStartTime

        Log.d("StopwatchViewModel", "Reset called. ElapsedTime: $elapsedTime, Laps: ${laps.size}, SessionStartTime: $sessionStartTime")

        // Stop service
        stopService()

        // Reset state IMMEDIATELY to prevent service restart if app goes to background
        // This must happen synchronously before any async operations
        StopwatchState.reset()
        preferencesManager.clearStopwatchState()

        // Save session to database asynchronously if there was any activity
        if (elapsedTime > 0L || laps.isNotEmpty()) {
            Log.d("StopwatchViewModel", "Saving session...")
            viewModelScope.launch {
                try {
                    saveSession(elapsedTime, laps, sessionStartTime)
                    Log.d("StopwatchViewModel", "Session saved successfully")
                    // Reload names from history after saving
                    loadNamesFromHistory()
                } catch (e: Exception) {
                    Log.e("StopwatchViewModel", "Error saving session", e)
                }
            }
        } else {
            Log.d("StopwatchViewModel", "No activity to save")
        }
    }

    private suspend fun saveSession(
        elapsedTime: Long,
        laps: List<LapTime>,
        sessionStartTime: Long
    ) {
        val endTime = System.currentTimeMillis()

        Log.d("StopwatchViewModel", "Creating session: startTime=$sessionStartTime, endTime=$endTime, duration=$elapsedTime")

        val session = SessionEntity(
            startTime = sessionStartTime,
            endTime = endTime,
            totalDuration = elapsedTime,
            name = _currentName.value.trim().takeIf { it.isNotBlank() }
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
        // Update time before adding lap to capture accurate lap time
        StopwatchState.updateElapsedTime(StopwatchState.getCurrentElapsedTime())
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

        // Update service wake lock if stopwatch has activity
        if (StopwatchState.isRunning.value || StopwatchState.elapsedTime.value > 0) {
            updateServiceWakeLock()
        }
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

        // Update service wake lock if stopwatch has activity
        if (StopwatchState.isRunning.value || StopwatchState.elapsedTime.value > 0) {
            updateServiceWakeLock()
        }
    }

    fun updateCurrentName(name: String) {
        // Don't trim during input - allow spaces inside names
        _currentName.value = name
        preferencesManager.currentName = name

        // Add to used names if not empty (trim for checking and storage)
        val trimmedForStorage = name.trim()
        if (trimmedForStorage.isNotBlank() && !_usedNames.value.contains(trimmedForStorage)) {
            val updated = _usedNames.value.toMutableSet()
            updated.add(trimmedForStorage)
            _usedNames.value = updated
            preferencesManager.usedNames = updated
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

    private fun shouldUseScreenDimWakeLock(): Boolean {
        // Use SCREEN_DIM_WAKE_LOCK when screen should stay on but allow natural dimming
        // This happens when dimBrightness is OFF (false) and screenOnMode requires screen on
        val shouldKeepOn = when (_screenOnMode.value) {
            ScreenOnMode.OFF -> false
            ScreenOnMode.WHILE_RUNNING -> StopwatchState.isRunning.value
            ScreenOnMode.ALWAYS -> true
        }
        return shouldKeepOn && !_dimBrightness.value
    }

    private fun startService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_START
            // Use SCREEN_DIM_WAKE_LOCK to allow dimming while keeping screen on
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, shouldUseScreenDimWakeLock())
        }
        context.startForegroundService(intent)
    }

    private fun pauseService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_PAUSE
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, shouldUseScreenDimWakeLock())
        }
        context.startService(intent)
    }

    private fun resumeService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_RESUME
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, shouldUseScreenDimWakeLock())
        }
        context.startService(intent)
    }

    private fun stopService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_STOP
        }
        context.startService(intent)
    }

    private fun updateServiceWakeLock() {
        // Send update to service to refresh wake lock based on current settings
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_UPDATE_WAKE_LOCK
            putExtra(StopwatchService.EXTRA_USE_SCREEN_DIM, shouldUseScreenDimWakeLock())
        }
        context.startService(intent)
    }
}
