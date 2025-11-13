package com.laplog.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.ScreenOnMode
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.LapTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StopwatchViewModel(
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

    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime = 0L
    private var sessionStartTime = 0L

    init {
        loadCommentsFromHistory()
    }

    private fun loadCommentsFromHistory() {
        viewModelScope.launch {
            _commentsFromHistory.value = sessionDao.getDistinctComments()
        }
    }

    fun startOrPause() {
        if (_isRunning.value) {
            pause()
        } else {
            start()
        }
    }

    private fun start() {
        startTime = System.currentTimeMillis()
        if (sessionStartTime == 0L) {
            sessionStartTime = startTime
        }
        _isRunning.value = true

        timerJob = viewModelScope.launch {
            while (_isRunning.value) {
                val currentTime = System.currentTimeMillis()
                _elapsedTime.value = accumulatedTime + (currentTime - startTime)
                delay(if (_showMilliseconds.value) 10L else 1000L)
            }
        }
    }

    private fun pause() {
        _isRunning.value = false
        accumulatedTime = _elapsedTime.value
        timerJob?.cancel()
    }

    fun reset() {
        _isRunning.value = false
        timerJob?.cancel()

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
            }
        } else {
            Log.d("StopwatchViewModel", "No activity to save")
            _elapsedTime.value = 0L
            accumulatedTime = 0L
            _laps.value = emptyList()
            sessionStartTime = 0L
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
    }

    fun addLapAndPause() {
        addLap()
        if (_isRunning.value) {
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
}
