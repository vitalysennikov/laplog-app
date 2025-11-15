package com.laplog.app.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared app and screen state for managing updates across the app
 */
object AppState {
    private val _isAppInForeground = MutableStateFlow(true)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _isScreenOn = MutableStateFlow(true)
    val isScreenOn: StateFlow<Boolean> = _isScreenOn.asStateFlow()

    private val _isScreenLocked = MutableStateFlow(false)
    val isScreenLocked: StateFlow<Boolean> = _isScreenLocked.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    fun setScreenOn(screenOn: Boolean) {
        _isScreenOn.value = screenOn
    }

    fun setScreenLocked(locked: Boolean) {
        _isScreenLocked.value = locked
    }

    /**
     * Returns true if UI updates should be active
     * UI updates only when app is in foreground AND screen is on
     */
    fun shouldUpdateUI(): Boolean {
        return _isAppInForeground.value && _isScreenOn.value
    }

    /**
     * Returns true if notification updates should be active
     * Notification updates only when screen is locked
     */
    fun shouldUpdateNotification(): Boolean {
        return _isScreenLocked.value && _isScreenOn.value
    }
}
