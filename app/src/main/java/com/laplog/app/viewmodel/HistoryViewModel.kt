package com.laplog.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.SessionWithLaps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionWithLaps>>(emptyList())
    val sessions: StateFlow<List<SessionWithLaps>> = _sessions.asStateFlow()

    private val _usedComments = MutableStateFlow<Set<String>>(emptySet())
    val usedComments: StateFlow<Set<String>> = _usedComments.asStateFlow()

    private val _expandAll = MutableStateFlow(true) // default: all expanded
    val expandAll: StateFlow<Boolean> = _expandAll.asStateFlow()

    private val _showMillisecondsInHistory = MutableStateFlow(preferencesManager.showMillisecondsInHistory)
    val showMillisecondsInHistory: StateFlow<Boolean> = _showMillisecondsInHistory.asStateFlow()

    private val _invertLapColors = MutableStateFlow(preferencesManager.invertLapColors)
    val invertLapColors: StateFlow<Boolean> = _invertLapColors.asStateFlow()

    private val _commentsFromHistory = MutableStateFlow<List<String>>(emptyList())
    val commentsFromHistory: StateFlow<List<String>> = _commentsFromHistory.asStateFlow()

    init {
        loadSessions()
        loadUsedComments()
        loadCommentsFromHistory()
    }

    private fun loadCommentsFromHistory() {
        viewModelScope.launch {
            _commentsFromHistory.value = sessionDao.getDistinctComments()
        }
    }

    fun toggleMillisecondsInHistory() {
        _showMillisecondsInHistory.value = !_showMillisecondsInHistory.value
        preferencesManager.showMillisecondsInHistory = _showMillisecondsInHistory.value
    }

    fun toggleInvertLapColors() {
        _invertLapColors.value = !_invertLapColors.value
        preferencesManager.invertLapColors = _invertLapColors.value
    }

    private fun loadSessions() {
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessionEntities ->
                Log.d("HistoryViewModel", "Loaded ${sessionEntities.size} sessions from database")
                val sessionsWithLaps = mutableListOf<SessionWithLaps>()

                for (session in sessionEntities) {
                    Log.d("HistoryViewModel", "Session ID: ${session.id}, StartTime: ${session.startTime}, Duration: ${session.totalDuration}")
                    // Get first emission from laps flow using first()
                    val laps = sessionDao.getLapsForSession(session.id).first()
                    Log.d("HistoryViewModel", "Session ${session.id} has ${laps.size} laps")
                    sessionsWithLaps.add(SessionWithLaps(session, laps))
                }

                _sessions.value = sessionsWithLaps
                Log.d("HistoryViewModel", "Updated sessions state with ${sessionsWithLaps.size} sessions")
            }
        }
    }

    private fun loadUsedComments() {
        _usedComments.value = preferencesManager.usedComments
    }

    fun updateSessionComment(sessionId: Long, comment: String) {
        viewModelScope.launch {
            sessionDao.updateSessionComment(sessionId, comment)

            // Add to used comments
            if (comment.isNotBlank()) {
                val updated = _usedComments.value.toMutableSet()
                updated.add(comment)
                _usedComments.value = updated
                preferencesManager.usedComments = updated
            }

            loadSessions()
            loadCommentsFromHistory() // Reload comments from database
        }
    }

    fun deleteSession(session: SessionEntity) {
        viewModelScope.launch {
            sessionDao.deleteSession(session)
            loadSessions()
        }
    }

    fun deleteSessionsBefore(beforeTime: Long) {
        viewModelScope.launch {
            sessionDao.deleteSessionsBefore(beforeTime)
            loadSessions()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            sessionDao.deleteAllSessions()
            loadSessions()
        }
    }

    fun toggleExpandAll() {
        _expandAll.value = !_expandAll.value
    }

    fun formatTime(timeInMillis: Long, includeMillis: Boolean = false): String {
        // Apply mathematical rounding if milliseconds are not shown (≥500ms → +1s)
        val adjustedTime = if (!includeMillis && timeInMillis % 1000 >= 500) {
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

    fun formatDifference(diffMillis: Long, includeMillis: Boolean = true): String {
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
