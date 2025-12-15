package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.SessionStatistics
import com.laplog.app.model.ChartData
import com.laplog.app.model.TimePeriod
import com.laplog.app.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChartsViewModel(
    private val sessionDao: SessionDao,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _availableNames = MutableStateFlow<List<String>>(emptyList())
    val availableNames: StateFlow<List<String>> = _availableNames.asStateFlow()

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(TimePeriod.ALL_TIME)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    private val _chartData = MutableStateFlow<ChartData?>(null)
    val chartData: StateFlow<ChartData?> = _chartData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAvailableNames()
        observeSessionChanges()
    }

    private fun observeSessionChanges() {
        viewModelScope.launch {
            sessionDao.getAllSessions().collectLatest { sessions ->
                // Update available names
                val names = sessions.mapNotNull { it.name }.distinct().sorted()
                _availableNames.value = names

                // Auto-select first name if needed
                if (names.isNotEmpty() && _selectedName.value == null) {
                    selectName(names.first())
                } else if (_selectedName.value != null) {
                    // Refresh data for currently selected name
                    _selectedName.value?.let { loadChartData(it) }
                }
            }
        }
    }

    fun refresh() {
        loadAvailableNames()
        _selectedName.value?.let { loadChartData(it) }
    }

    private fun loadAvailableNames() {
        viewModelScope.launch {
            try {
                val names = sessionDao.getDistinctNames()
                _availableNames.value = names
                AppLogger.d("ChartsViewModel", "Loaded ${names.size} distinct names")

                // Auto-select first name if available
                if (names.isNotEmpty() && _selectedName.value == null) {
                    selectName(names.first())
                } else if (names.isEmpty()) {
                    // Clear selection if no names available
                    _selectedName.value = null
                    _chartData.value = null
                }
            } catch (e: Exception) {
                AppLogger.e("ChartsViewModel", "Error loading names", e)
            }
        }
    }

    fun selectName(name: String?) {
        _selectedName.value = name
        name?.let { loadChartData(it) }
    }

    fun selectPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        _selectedName.value?.let { loadChartData(it) }
    }

    private fun loadChartData(name: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                AppLogger.d("ChartsViewModel", "Loading chart data for: $name, period: ${_selectedPeriod.value}")

                val allSessions = sessionDao.getSessionsByName(name)

                // Filter by time period
                val periodStartTime = _selectedPeriod.value.getStartTimeMillis()
                val sessions = if (periodStartTime != null) {
                    allSessions.filter { it.startTime >= periodStartTime }
                } else {
                    allSessions
                }

                AppLogger.d("ChartsViewModel", "Found ${sessions.size} sessions for name: $name (filtered from ${allSessions.size})")

                val statistics = mutableListOf<SessionStatistics>()

                for (session in sessions) {
                    val laps = sessionDao.getLapsForSession(session.id).first()

                    // Calculate lap statistics if available
                    val (avgDuration, medianDuration) = if (laps.size >= 2) {
                        val lapDurations = laps.map { it.lapDuration }
                        val avg = lapDurations.average().toLong()

                        // Calculate median
                        val sortedDurations = lapDurations.sorted()
                        val median = if (sortedDurations.size % 2 == 0) {
                            (sortedDurations[sortedDurations.size / 2 - 1] +
                             sortedDurations[sortedDurations.size / 2]) / 2
                        } else {
                            sortedDurations[sortedDurations.size / 2]
                        }
                        Pair(avg, median)
                    } else {
                        // No laps or only 1 lap - use 0 for lap statistics
                        Pair(0L, 0L)
                    }

                    statistics.add(
                        SessionStatistics(
                            sessionId = session.id,
                            sessionName = name,
                            startTime = session.startTime,
                            totalDuration = session.totalDuration,
                            averageLapTime = avgDuration,
                            medianLapTime = medianDuration
                        )
                    )
                }

                // Calculate overall statistics
                val overallAverageDuration = if (statistics.isNotEmpty()) {
                    statistics.map { it.totalDuration }.average().toLong()
                } else {
                    0L
                }

                val overallAverageLapTime = if (statistics.isNotEmpty()) {
                    val lapsWithData = statistics.filter { it.averageLapTime > 0 }
                    if (lapsWithData.isNotEmpty()) {
                        lapsWithData.map { it.averageLapTime }.average().toLong()
                    } else {
                        0L
                    }
                } else {
                    0L
                }

                val overallMedianLapTime = if (statistics.isNotEmpty()) {
                    val mediansWithData = statistics.filter { it.medianLapTime > 0 }
                        .map { it.medianLapTime }
                        .sorted()
                    if (mediansWithData.isEmpty()) {
                        0L
                    } else if (mediansWithData.size % 2 == 0) {
                        (mediansWithData[mediansWithData.size / 2 - 1] + mediansWithData[mediansWithData.size / 2]) / 2
                    } else {
                        mediansWithData[mediansWithData.size / 2]
                    }
                } else {
                    0L
                }

                _chartData.value = ChartData(
                    sessionName = name,
                    statistics = statistics,
                    overallAverageDuration = overallAverageDuration,
                    overallAverageLapTime = overallAverageLapTime,
                    overallMedianLapTime = overallMedianLapTime
                )

                AppLogger.i("ChartsViewModel", "Loaded chart data with ${statistics.size} data points, overall avg duration: $overallAverageDuration ms, overall avg lap: $overallAverageLapTime ms, overall median lap: $overallMedianLapTime ms")
            } catch (e: Exception) {
                AppLogger.e("ChartsViewModel", "Error loading chart data", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Get displayed name for session based on current language
     * Falls back to original name if translation not available
     */
    fun getDisplayedName(session: SessionEntity): String {
        val currentLang = preferencesManager.getCurrentLanguage()
        return when (currentLang) {
            "en" -> session.name_en ?: session.name
            "ru" -> session.name_ru ?: session.name
            "zh" -> session.name_zh ?: session.name
            else -> session.name
        } ?: ""
    }

    fun formatTime(timeInMillis: Long): String {
        val hours = (timeInMillis / 3600000).toInt()
        val minutes = ((timeInMillis % 3600000) / 60000).toInt()
        val seconds = ((timeInMillis % 60000) / 1000).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
