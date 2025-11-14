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
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _laps = MutableStateFlow<List<LapTime>>(emptyList())
    val laps: StateFlow<List<LapTime>> = _laps.asStateFlow()

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

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    private val _showBatteryDialog = MutableStateFlow(false)
    val showBatteryDialog: StateFlow<Boolean> = _showBatteryDialog.asStateFlow()

    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime = 0L
    private var sessionStartTime = 0L

    init {
        loadCommentsFromHistory()
        restoreStopwatchState()
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

                // Restore session start time
                sessionStartTime = savedSessionStartTime

                // Restore laps from JSON
                if (savedLapsJson != null) {
                    _laps.value = parseLapsFromJson(savedLapsJson)
                }

                // If stopwatch was running, calculate elapsed time from last update
                if (savedIsRunning) {
                    val timeSinceLastUpdate = System.currentTimeMillis() - savedLastUpdateTime
                    accumulatedTime = savedAccumulatedTime + timeSinceLastUpdate
                    _elapsedTime.value = accumulatedTime

                    // Restart the timer
                    startTime = System.currentTimeMillis()
                    _isRunning.value = true

                    timerJob = viewModelScope.launch {
                        while (_isRunning.value) {
                            val currentTime = System.currentTimeMillis()
                            _elapsedTime.value = accumulatedTime + (currentTime - startTime)
                            delay(if (_showMilliseconds.value) 10L else 1000L)
                        }
                    }

                    // Restart the service
                    resumeService()
                } else {
                    // Stopwatch was paused
                    accumulatedTime = savedAccumulatedTime
                    _elapsedTime.value = savedElapsedTime
                    _isRunning.value = false
                }
            }
        } catch (e: Exception) {
            Log.e("StopwatchViewModel", "Error restoring stopwatch state", e)
        }
    }

    private fun saveStopwatchState() {
        try {
            preferencesManager.stopwatchElapsedTime = _elapsedTime.value
            preferencesManager.stopwatchIsRunning = _isRunning.value
            preferencesManager.stopwatchSessionStartTime = sessionStartTime
            preferencesManager.stopwatchAccumulatedTime = accumulatedTime
            preferencesManager.stopwatchLastUpdateTime = System.currentTimeMillis()
            preferencesManager.stopwatchLapsJson = serializeLapsToJson(_laps.value)
        } catch (e: Exception) {
            Log.e("StopwatchViewModel", "Error saving stopwatch state", e)
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
        if (_isRunning.value) {
            pause()
        } else {
            // Check permissions before starting
            if (!preferencesManager.permissionsRequested && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                _showPermissionDialog.value = true
            } else {
                start()
            }
        }
    }

    fun dismissPermissionDialog() {
        _showPermissionDialog.value = false
        preferencesManager.permissionsRequested = true
        start()
    }

    fun dismissBatteryDialog() {
        _showBatteryDialog.value = false
    }

    fun showBatteryOptimizationDialog() {
        _showBatteryDialog.value = true
    }

    private fun start() {
        startTime = System.currentTimeMillis()
        val isResume = sessionStartTime != 0L  // True if resuming from pause
        if (sessionStartTime == 0L) {
            sessionStartTime = startTime
        }
        _isRunning.value = true

        // Save state
        saveStopwatchState()

        // Start or resume foreground service
        if (isResume) {
            resumeService()
        } else {
            startService()
        }

        timerJob = viewModelScope.launch {
            var lastSaveTime = System.currentTimeMillis()
            while (_isRunning.value) {
                val currentTime = System.currentTimeMillis()
                _elapsedTime.value = accumulatedTime + (currentTime - startTime)

                // Save state every 5 seconds while running
                if (currentTime - lastSaveTime >= 5000) {
                    saveStopwatchState()
                    lastSaveTime = currentTime
                }

                delay(if (_showMilliseconds.value) 10L else 1000L)
            }
        }
    }

    private fun pause() {
        _isRunning.value = false
        accumulatedTime = _elapsedTime.value
        timerJob?.cancel()

        // Save state
        saveStopwatchState()

        // Pause service
        pauseService()
    }

    fun reset() {
        _isRunning.value = false
        timerJob?.cancel()

        // Stop service
        stopService()

        Log.d("StopwatchViewModel", "Reset called. ElapsedTime: ${_elapsedTime.value}, Laps: ${_laps.value.size}, SessionStartTime: $sessionStartTime")

        // Save session to database if there was any activity
        if (_elapsedTime.value > 0L || _laps.value.isNotEmpty()) {
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
                // Reset values after saving
                _elapsedTime.value = 0L
                accumulatedTime = 0L
                _laps.value = emptyList()
                sessionStartTime = 0L

                // Clear saved state
                preferencesManager.clearStopwatchState()
            }
        } else {
            Log.d("StopwatchViewModel", "No activity to save")
            _elapsedTime.value = 0L
            accumulatedTime = 0L
            _laps.value = emptyList()
            sessionStartTime = 0L

            // Clear saved state
            preferencesManager.clearStopwatchState()
        }
    }

    private suspend fun saveSession() {
        val endTime = System.currentTimeMillis()
        Log.d("StopwatchViewModel", "Creating session: startTime=$sessionStartTime, endTime=$endTime, duration=${_elapsedTime.value}")

        val session = SessionEntity(
            startTime = sessionStartTime,
            endTime = endTime,
            totalDuration = _elapsedTime.value,
            comment = _currentComment.value.takeIf { it.isNotBlank() }
        )

        val sessionId = sessionDao.insertSession(session)
        Log.d("StopwatchViewModel", "Session inserted with ID: $sessionId")

        if (_laps.value.isNotEmpty()) {
            val lapEntities = _laps.value.map { lap ->
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
        if (_elapsedTime.value == 0L) return

        val currentTime = _elapsedTime.value
        val previousLapTime = _laps.value.lastOrNull()?.totalTime ?: 0L
        val lapDuration = currentTime - previousLapTime

        val newLap = LapTime(
            lapNumber = _laps.value.size + 1,
            totalTime = currentTime,
            lapDuration = lapDuration
        )

        _laps.value = _laps.value + newLap

        // Save state
        saveStopwatchState()

        // Update service with new lap info
        updateServiceState()
    }

    fun addLapAndPause() {
        addLap()
        if (_isRunning.value) {
            pause()
        }
    }

    // Methods for notification actions
    fun pauseFromNotification() {
        if (_isRunning.value) {
            pause()
        }
    }

    fun startOrResumeFromNotification() {
        if (!_isRunning.value) {
            startOrPause()
        }
    }

    fun lapFromNotification() {
        if (_isRunning.value && _elapsedTime.value > 0) {
            addLap()
        }
    }

    fun resetFromNotification() {
        reset()
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

    fun updateCurrentComment(comment: String) {
        _currentComment.value = comment
        preferencesManager.currentComment = comment

        // Add to used comments if not empty
        if (comment.isNotBlank() && !_usedComments.value.contains(comment)) {
            val updated = _usedComments.value.toMutableSet()
            updated.add(comment)
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

    private fun updateServiceState() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_UPDATE_STATE
            putExtra(StopwatchService.EXTRA_ELAPSED_TIME, _elapsedTime.value)
            putExtra(StopwatchService.EXTRA_IS_RUNNING, _isRunning.value)
            putExtra(StopwatchService.EXTRA_LAP_COUNT, _laps.value.size)
            putExtra(StopwatchService.EXTRA_LAST_LAP_TIME, _laps.value.lastOrNull()?.totalTime ?: 0L)
        }
        context.startService(intent)
    }

    fun requestStateFromService() {
        val intent = Intent(context, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_REQUEST_STATE
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Service not running, ignore
            Log.d("StopwatchViewModel", "Service not running, cannot request state")
        }
    }

    fun updateStateFromService(elapsedTime: Long, isRunning: Boolean) {
        Log.d("StopwatchViewModel", "Updating state from service: elapsedTime=$elapsedTime, isRunning=$isRunning")

        // Only update if there's a significant difference (avoid minor drift)
        val timeDiff = kotlin.math.abs(_elapsedTime.value - elapsedTime)
        if (timeDiff > 100) {  // More than 100ms difference
            accumulatedTime = elapsedTime
            _elapsedTime.value = elapsedTime

            if (isRunning && !_isRunning.value) {
                // Service is running but ViewModel thinks it's not - restart timer
                startTime = System.currentTimeMillis()
                _isRunning.value = true

                timerJob?.cancel()
                timerJob = viewModelScope.launch {
                    while (_isRunning.value) {
                        val currentTime = System.currentTimeMillis()
                        _elapsedTime.value = accumulatedTime + (currentTime - startTime)
                        delay(if (_showMilliseconds.value) 10L else 1000L)
                    }
                }
            } else if (!isRunning && _isRunning.value) {
                // Service is paused but ViewModel thinks it's running - stop timer
                _isRunning.value = false
                timerJob?.cancel()
            }
        }
    }
}
