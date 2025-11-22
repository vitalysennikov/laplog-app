package com.laplog.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.SessionWithLaps
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
) : ViewModel() {

    private var loadSessionsJob: Job? = null

    private val _sessions = MutableStateFlow<List<SessionWithLaps>>(emptyList())
    val sessions: StateFlow<List<SessionWithLaps>> = _sessions.asStateFlow()

    private val _usedNames = MutableStateFlow<Set<String>>(emptySet())
    val usedNames: StateFlow<Set<String>> = _usedNames.asStateFlow()

    private val _expandAll = MutableStateFlow(true) // default: all expanded
    val expandAll: StateFlow<Boolean> = _expandAll.asStateFlow()

    private val _showMillisecondsInHistory = MutableStateFlow(preferencesManager.showMillisecondsInHistory)
    val showMillisecondsInHistory: StateFlow<Boolean> = _showMillisecondsInHistory.asStateFlow()

    private val _invertLapColors = MutableStateFlow(preferencesManager.invertLapColors)
    val invertLapColors: StateFlow<Boolean> = _invertLapColors.asStateFlow()

    private val _namesFromHistory = MutableStateFlow<List<String>>(emptyList())
    val namesFromHistory: StateFlow<List<String>> = _namesFromHistory.asStateFlow()

    private val _filterName = MutableStateFlow<String?>(null)
    val filterName: StateFlow<String?> = _filterName.asStateFlow()

    private val _showTableView = MutableStateFlow(false)
    val showTableView: StateFlow<Boolean> = _showTableView.asStateFlow()

    init {
        loadSessions()
        loadUsedNames()
        loadNamesFromHistory()
    }

    private fun loadNamesFromHistory() {
        viewModelScope.launch {
            _namesFromHistory.value = sessionDao.getDistinctNames()
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
        // Cancel previous job if exists
        loadSessionsJob?.cancel()

        loadSessionsJob = viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessionEntities ->
                Log.d("HistoryViewModel", "Loaded ${sessionEntities.size} sessions from database")

                // Apply filter if set
                val filteredSessions = if (_filterName.value != null) {
                    sessionEntities.filter { it.name == _filterName.value }
                } else {
                    sessionEntities
                }

                val sessionsWithLaps = mutableListOf<SessionWithLaps>()

                for (session in filteredSessions) {
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

    private fun loadUsedNames() {
        _usedNames.value = preferencesManager.usedNames
    }

    fun updateSessionName(sessionId: Long, name: String) {
        viewModelScope.launch {
            sessionDao.updateSessionName(sessionId, name)

            // Add to used names
            if (name.isNotBlank()) {
                val updated = _usedNames.value.toMutableSet()
                updated.add(name)
                _usedNames.value = updated
                preferencesManager.usedNames = updated
            }

            loadSessions()
            loadNamesFromHistory() // Reload names from database
        }
    }

    fun updateSessionNotes(sessionId: Long, notes: String) {
        viewModelScope.launch {
            sessionDao.updateSessionNotes(sessionId, notes)
            loadSessions()
        }
    }

    fun setFilterName(name: String?) {
        _filterName.value = name
        loadSessions()
    }

    fun toggleTableView() {
        _showTableView.value = !_showTableView.value
    }

    fun deleteSession(session: SessionEntity) {
        viewModelScope.launch {
            sessionDao.deleteSession(session)
            loadSessions()
            loadNamesFromHistory() // Update names list
        }
    }

    fun deleteSessionsBefore(beforeTime: Long) {
        viewModelScope.launch {
            sessionDao.deleteSessionsBefore(beforeTime)
            loadSessions()
            loadNamesFromHistory() // Update names list
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            sessionDao.deleteAllSessions()
            loadSessions()
            loadNamesFromHistory() // Update names list
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
