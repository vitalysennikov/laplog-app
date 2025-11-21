package com.laplog.app.model

import com.laplog.app.data.ScreenOnMode

data class BackupData(
    val version: String,
    val timestamp: Long,
    val sessions: List<BackupSession>,
    val settings: BackupSettings? = null
)

data class BackupSettings(
    val showMilliseconds: Boolean,
    val screenOnMode: String,
    val lockOrientation: Boolean,
    val showMillisecondsInHistory: Boolean,
    val invertLapColors: Boolean,
    val appLanguage: String?
)

data class BackupSession(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long,
    val name: String? = null,
    val notes: String? = null,
    val comment: String? = null, // Legacy field for backward compatibility
    val laps: List<BackupLap>
)

data class BackupLap(
    val lapNumber: Int,
    val totalTime: Long,
    val lapDuration: Long
)

data class BackupFileInfo(
    val uri: android.net.Uri,
    val name: String,
    val timestamp: Long,
    val size: Long
)
