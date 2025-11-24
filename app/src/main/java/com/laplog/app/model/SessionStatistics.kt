package com.laplog.app.model

/**
 * Model class representing statistics for a single session
 * Used for chart data points
 */
data class SessionStatistics(
    val sessionId: Long,
    val sessionName: String,
    val startTime: Long,
    val totalDuration: Long,   // Total session duration in milliseconds
    val averageLapTime: Long,  // Average lap duration in milliseconds
    val medianLapTime: Long    // Median lap duration in milliseconds
)

/**
 * Model class representing chart data for sessions with the same name
 */
data class ChartData(
    val sessionName: String,
    val statistics: List<SessionStatistics>
)
