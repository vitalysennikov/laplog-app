package com.laplog.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton object that holds the single source of truth for stopwatch state.
 * Both StopwatchService and StopwatchViewModel use this shared state.
 */
object StopwatchState {
    // Elapsed time in milliseconds
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()

    // Is stopwatch running
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    // List of laps
    private val _laps = MutableStateFlow<List<LapTime>>(emptyList())
    val laps: StateFlow<List<LapTime>> = _laps.asStateFlow()

    // Internal state for time calculations
    var startTime = 0L
        private set
    var accumulatedTime = 0L
        private set
    var sessionStartTime = 0L
        private set

    /**
     * Start the stopwatch
     */
    fun start() {
        startTime = System.currentTimeMillis()
        if (sessionStartTime == 0L) {
            sessionStartTime = startTime
        }
        _isRunning.value = true
    }

    /**
     * Resume the stopwatch from paused state
     */
    fun resume() {
        startTime = System.currentTimeMillis()
        _isRunning.value = true
    }

    /**
     * Pause the stopwatch
     */
    fun pause() {
        if (_isRunning.value) {
            _isRunning.value = false
            accumulatedTime = _elapsedTime.value
        }
    }

    /**
     * Update elapsed time (called by timer)
     */
    fun updateElapsedTime(time: Long) {
        _elapsedTime.value = time
    }

    /**
     * Calculate current elapsed time
     */
    fun getCurrentElapsedTime(): Long {
        return if (_isRunning.value) {
            accumulatedTime + (System.currentTimeMillis() - startTime)
        } else {
            accumulatedTime
        }
    }

    /**
     * Add a lap mark
     */
    fun addLap(): LapTime? {
        val currentTime = getCurrentElapsedTime()
        if (currentTime == 0L) return null

        val previousLapTime = _laps.value.lastOrNull()?.totalTime ?: 0L
        val lapDuration = currentTime - previousLapTime

        val newLap = LapTime(
            lapNumber = _laps.value.size + 1,
            totalTime = currentTime,
            lapDuration = lapDuration
        )

        _laps.value = _laps.value + newLap
        return newLap
    }

    /**
     * Reset stopwatch to initial state
     */
    fun reset() {
        _isRunning.value = false
        _elapsedTime.value = 0L
        accumulatedTime = 0L
        _laps.value = emptyList()
        sessionStartTime = 0L
        startTime = 0L
    }

    /**
     * Restore state from saved data
     */
    fun restore(
        elapsedTime: Long,
        isRunning: Boolean,
        laps: List<LapTime>,
        sessionStartTime: Long,
        accumulatedTime: Long
    ) {
        this._elapsedTime.value = elapsedTime
        this._isRunning.value = isRunning
        this._laps.value = laps
        this.sessionStartTime = sessionStartTime
        this.accumulatedTime = accumulatedTime
        if (isRunning) {
            this.startTime = System.currentTimeMillis()
        }
    }

    /**
     * Get current lap count
     */
    fun getLapCount(): Int = _laps.value.size

    /**
     * Get last lap time
     */
    fun getLastLapTime(): Long = _laps.value.lastOrNull()?.totalTime ?: 0L
}
