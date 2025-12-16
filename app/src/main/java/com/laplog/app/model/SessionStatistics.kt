package com.laplog.app.model

/**
 * Model class representing statistics for a single session
 * Used for chart data points
 */
data class SessionStatistics(
    val sessionId: Long,
    val sessionName: String,
    val startTime: Long,
    val totalDuration: Long,   // Active time (without pauses) in milliseconds
    val elapsedTime: Long,     // Total elapsed time (with pauses) in milliseconds
    val averageLapTime: Long,  // Average lap duration in milliseconds
    val medianLapTime: Long    // Median lap duration in milliseconds
)

/**
 * Model class representing chart data for sessions with the same name
 */
data class ChartData(
    val sessionName: String,
    val statistics: List<SessionStatistics>,
    val overallAverageDuration: Long = 0,      // Average of all totalDurations (active time)
    val overallAverageElapsedTime: Long = 0,   // Average of all elapsedTimes (total time with pauses)
    val overallAverageLapTime: Long = 0,       // Average of all averageLapTimes
    val overallMedianLapTime: Long = 0         // Median of all medianLapTimes
)
