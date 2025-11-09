package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.SessionWithLaps
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _sessions = MutableStateFlow<List<SessionWithLaps>>(emptyList())
    val sessions: StateFlow<List<SessionWithLaps>> = _sessions.asStateFlow()

    private val _usedComments = MutableStateFlow<Set<String>>(emptySet())
    val usedComments: StateFlow<Set<String>> = _usedComments.asStateFlow()

    init {
        loadSessions()
        loadUsedComments()
    }

    private fun loadSessions() {
        viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessionEntities ->
                val sessionsWithLaps = sessionEntities.map { session ->
                    var laps = emptyList<com.laplog.app.data.database.entity.LapEntity>()
                    // Get laps synchronously for each session
                    launch {
                        sessionDao.getLapsForSession(session.id).collect { lapList ->
                            laps = lapList
                        }
                    }.join()
                    SessionWithLaps(session, laps)
                }
                _sessions.value = sessionsWithLaps
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

    fun formatTime(timeInMillis: Long, includeMillis: Boolean = false): String {
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
