package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
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

    private val _keepScreenOn = MutableStateFlow(preferencesManager.keepScreenOn)
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private var timerJob: Job? = null
    private var startTime = 0L
    private var accumulatedTime = 0L
    private var sessionStartTime = 0L

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
        // Save session to database if there was any activity
        if (_elapsedTime.value > 0L || _laps.value.isNotEmpty()) {
            saveSession()
        }

        _isRunning.value = false
        _elapsedTime.value = 0L
        accumulatedTime = 0L
        _laps.value = emptyList()
        sessionStartTime = 0L
        timerJob?.cancel()
    }

    private fun saveSession() {
        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            val session = SessionEntity(
                startTime = sessionStartTime,
                endTime = endTime,
                totalDuration = _elapsedTime.value,
                comment = null
            )

            val sessionId = sessionDao.insertSession(session)

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
            }
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

    fun toggleMillisecondsDisplay() {
        _showMilliseconds.value = !_showMilliseconds.value
        preferencesManager.showMilliseconds = _showMilliseconds.value
    }

    fun toggleKeepScreenOn() {
        _keepScreenOn.value = !_keepScreenOn.value
        preferencesManager.keepScreenOn = _keepScreenOn.value
    }

    fun formatTime(timeInMillis: Long, includeMillis: Boolean = _showMilliseconds.value): String {
        val hours = (timeInMillis / 3600000).toInt()
        val minutes = ((timeInMillis % 3600000) / 60000).toInt()
        val seconds = ((timeInMillis % 60000) / 1000).toInt()
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
}
