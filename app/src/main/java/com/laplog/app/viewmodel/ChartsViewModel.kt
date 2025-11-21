package com.laplog.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.model.SessionStatistics
import com.laplog.app.model.ChartData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChartsViewModel(
    private val sessionDao: SessionDao
) : ViewModel() {

    private val _availableNames = MutableStateFlow<List<String>>(emptyList())
    val availableNames: StateFlow<List<String>> = _availableNames.asStateFlow()

    private val _selectedName = MutableStateFlow<String?>(null)
    val selectedName: StateFlow<String?> = _selectedName.asStateFlow()

    private val _chartData = MutableStateFlow<ChartData?>(null)
    val chartData: StateFlow<ChartData?> = _chartData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAvailableNames()
    }

    private fun loadAvailableNames() {
        viewModelScope.launch {
            try {
                val names = sessionDao.getDistinctNames()
                _availableNames.value = names
                Log.d("ChartsViewModel", "Loaded ${names.size} distinct names")

                // Auto-select first name if available
                if (names.isNotEmpty() && _selectedName.value == null) {
                    selectName(names.first())
                }
            } catch (e: Exception) {
                Log.e("ChartsViewModel", "Error loading names", e)
            }
        }
    }

    fun selectName(name: String) {
        _selectedName.value = name
        loadChartData(name)
    }

    private fun loadChartData(name: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d("ChartsViewModel", "Loading chart data for: $name")

                val sessions = sessionDao.getSessionsByName(name)
                Log.d("ChartsViewModel", "Found ${sessions.size} sessions for name: $name")

                val statistics = mutableListOf<SessionStatistics>()

                for (session in sessions) {
                    val laps = sessionDao.getLapsForSession(session.id).first()

                    // Only include sessions with at least 2 laps
                    if (laps.size >= 2) {
                        val lapDurations = laps.map { it.lapDuration }
                        val avgDuration = lapDurations.average().toLong()

                        // Calculate median
                        val sortedDurations = lapDurations.sorted()
                        val medianDuration = if (sortedDurations.size % 2 == 0) {
                            (sortedDurations[sortedDurations.size / 2 - 1] +
                             sortedDurations[sortedDurations.size / 2]) / 2
                        } else {
                            sortedDurations[sortedDurations.size / 2]
                        }

                        statistics.add(
                            SessionStatistics(
                                sessionId = session.id,
                                sessionName = name,
                                startTime = session.startTime,
                                averageLapTime = avgDuration,
                                medianLapTime = medianDuration
                            )
                        )
                    }
                }

                _chartData.value = ChartData(
                    sessionName = name,
                    statistics = statistics
                )

                Log.d("ChartsViewModel", "Loaded chart data with ${statistics.size} data points")
            } catch (e: Exception) {
                Log.e("ChartsViewModel", "Error loading chart data", e)
            } finally {
                _isLoading.value = false
            }
        }
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
