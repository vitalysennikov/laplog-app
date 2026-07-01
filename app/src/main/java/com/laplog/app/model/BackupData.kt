package com.laplog.app.model

data class BackupData(
    val version: String,
    val timestamp: Long,
    val sessions: List<BackupSession>,
    val sessionNames: List<BackupSessionName>? = null,
    val settings: Map<String, Any?>? = null,
    // Только для чтения бэкапов старого формата (до перехода session_names на
    // единственный источник правды) — не пишется в новые бэкапы.
    val legacyNameToggles: Map<String, NameToggles>? = null
)

data class BackupSessionName(
    val name: String,
    val togglesJson: String? = null,
    val accentsJson: String? = null
)

data class NameToggles(
    val showMilliseconds: Boolean = true,
    val screenOnMode: String = "WHILE_RUNNING",
    val lockOrientation: Boolean = false,
    val invertLapColors: Boolean = false,
    val dimBrightness: Boolean = true,
    val hideTimeWhileRunning: Boolean = false,
    val showTimeAsSeconds: Boolean = false,
    val tickEnabled: Boolean = false,
    val tickAccentsJson: String? = null
)

data class BackupSession(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val totalDuration: Long,
    val name: String? = null,
    val notes: String? = null,
    val comment: String? = null, // Legacy field for backward compatibility
    val name_en: String? = null,
    val name_ru: String? = null,
    val name_zh: String? = null,
    val notes_en: String? = null,
    val notes_ru: String? = null,
    val notes_zh: String? = null,
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
