package com.laplog.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.dao.SessionNameDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.data.database.entity.SessionNameEntity
import com.laplog.app.model.BackupData
import com.laplog.app.model.BackupFileInfo
import com.laplog.app.model.BackupLap
import com.laplog.app.model.BackupSession
import com.laplog.app.model.BackupSettings
import com.laplog.app.model.NameToggles
import com.laplog.app.util.AppLogger
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao,
    private val translationManager: TranslationManager,
    private val sessionNameDao: SessionNameDao? = null
) {
    companion object {
        private const val BACKUP_PREFIX = "laplog_backup_"
        private const val BACKUP_EXTENSION = ".json"
    }

    /**
     * Generate backup data for manual save (can be used with cloud storage)
     */
    suspend fun generateBackupData(): Result<Pair<String, String>> {
        return try {
            val backupData = createBackupData()
            val json = backupDataToJson(backupData)
            val fileName = generateBackupFileName()
            Result.success(Pair(fileName, json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export full database to JSON and save to selected folder
     */
    suspend fun createBackup(folderUri: Uri): Result<BackupFileInfo> {
        AppLogger.i("BackupManager", "Creating backup to folder: $folderUri")
        return try {
            val backupData = createBackupData()
            AppLogger.d("BackupManager", "Backup data created: ${backupData.sessions.size} sessions")

            // Convert to JSON
            val json = backupDataToJson(backupData)

            // Save to file
            val fileName = generateBackupFileName()
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            if (folder == null) {
                AppLogger.e("BackupManager", "Invalid folder URI")
                return Result.failure(Exception("Invalid folder URI"))
            }

            val file = folder.createFile("application/json", fileName)
            if (file == null) {
                AppLogger.e("BackupManager", "Failed to create backup file")
                return Result.failure(Exception("Failed to create backup file"))
            }

            val outputStream = context.contentResolver.openOutputStream(file.uri)
            if (outputStream == null) {
                AppLogger.e("BackupManager", "Failed to write backup file")
                return Result.failure(Exception("Failed to write backup file"))
            }
            outputStream.use { it.write(json.toByteArray()) }

            val fileInfo = BackupFileInfo(
                uri = file.uri,
                name = fileName,
                timestamp = backupData.timestamp,
                size = json.toByteArray().size.toLong()
            )

            AppLogger.i("BackupManager", "Backup created successfully: $fileName (${fileInfo.size} bytes)")
            Result.success(fileInfo)
        } catch (e: Exception) {
            AppLogger.e("BackupManager", "Failed to create backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Get list of available backups from folder
     */
    fun listBackups(folderUri: Uri): List<BackupFileInfo> {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        val backups = mutableListOf<BackupFileInfo>()

        folder.listFiles().forEach { file ->
            if (file.name?.startsWith(BACKUP_PREFIX) == true &&
                file.name?.endsWith(BACKUP_EXTENSION) == true) {
                val timestamp = try {
                    extractTimestampFromFileName(file.name!!)
                } catch (_: Exception) {
                    file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                }
                backups.add(
                    BackupFileInfo(
                        uri = file.uri,
                        name = file.name!!,
                        timestamp = timestamp,
                        size = file.length()
                    )
                )
            }
        }

        return backups.sortedByDescending { it.timestamp }
    }

    data class RestoreResult(
        val sessions: Int,
        val laps: Int,
        val settingsRestored: Boolean
    )

    /**
     * Restore database from backup
     */
    suspend fun restoreBackup(
        fileUri: Uri,
        mode: RestoreMode,
        onProgress: suspend (current: Int, total: Int) -> Unit = {}
    ): Result<RestoreResult> {
        AppLogger.i("BackupManager", "Restoring backup from: $fileUri, mode: $mode")
        return try {
            // Read JSON from file
            val json = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            }
            if (json == null) {
                AppLogger.e("BackupManager", "Failed to read backup file from URI")
                return Result.failure(Exception("Failed to read backup file"))
            }

            val backupData = jsonToBackupData(json)
            AppLogger.i("BackupManager", "Backup data parsed: version=${backupData.version}, ${backupData.sessions.size} sessions")

            val restoreResult = when (mode) {
                RestoreMode.REPLACE -> {
                    AppLogger.i("BackupManager", "Starting REPLACE mode restoration")
                    restoreReplace(backupData, onProgress)
                }
                RestoreMode.MERGE -> {
                    AppLogger.i("BackupManager", "Starting MERGE mode restoration")
                    restoreMerge(backupData, onProgress)
                }
            }

            AppLogger.i("BackupManager", "Backup restored successfully: ${restoreResult.sessions} sessions, ${restoreResult.laps} laps")
            Result.success(restoreResult)
        } catch (e: Exception) {
            AppLogger.e("BackupManager", "Failed to restore backup: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Delete old backups older than retention days
     */
    fun deleteOldBackups(folderUri: Uri, retentionDays: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val backups = listBackups(folderUri)
        var deletedCount = 0

        backups.forEach { backup ->
            if (backup.timestamp < cutoffTime) {
                try {
                    DocumentFile.fromSingleUri(context, backup.uri)?.delete()
                    deletedCount++
                } catch (e: Exception) {
                    // Skip failed deletions
                }
            }
        }

        return deletedCount
    }

    /**
     * Delete all backups
     */
    fun deleteAllBackups(folderUri: Uri): Int {
        val backups = listBackups(folderUri)
        var deletedCount = 0

        backups.forEach { backup ->
            try {
                DocumentFile.fromSingleUri(context, backup.uri)?.delete()
                deletedCount++
            } catch (e: Exception) {
                // Skip failed deletions
            }
        }

        return deletedCount
    }

    /**
     * Delete backups before (excluding) the specified timestamp
     */
    fun deleteBackupsBefore(folderUri: Uri, timestamp: Long): Int {
        val backups = listBackups(folderUri)
        var deletedCount = 0

        backups.forEach { backup ->
            if (backup.timestamp < timestamp) {
                try {
                    DocumentFile.fromSingleUri(context, backup.uri)?.delete()
                    deletedCount++
                } catch (e: Exception) {
                    // Skip failed deletions
                }
            }
        }

        return deletedCount
    }

    /**
     * Create backup data from current database state
     */
    private suspend fun createBackupData(): BackupData {
        // Get all sessions from database
        val sessions = sessionDao.getAllSessions().first()
        val backupSessions = mutableListOf<BackupSession>()

        for (session in sessions) {
            val laps = sessionDao.getLapsForSession(session.id).first()
            val backupLaps = laps.map { lap ->
                BackupLap(
                    lapNumber = lap.lapNumber,
                    totalTime = lap.totalTime,
                    lapDuration = lap.lapDuration
                )
            }
            backupSessions.add(
                BackupSession(
                    id = session.id,
                    startTime = session.startTime,
                    endTime = session.endTime,
                    totalDuration = session.totalDuration,
                    name = session.name,
                    notes = session.notes,
                    name_en = session.name_en,
                    name_ru = session.name_ru,
                    name_zh = session.name_zh,
                    notes_en = session.notes_en,
                    notes_ru = session.notes_ru,
                    notes_zh = session.notes_zh,
                    laps = backupLaps
                )
            )
        }

        // Get current settings
        val backupSettings = BackupSettings(
            showMilliseconds = preferencesManager.showMilliseconds,
            screenOnMode = preferencesManager.screenOnMode.name,
            lockOrientation = preferencesManager.lockOrientation,
            showMillisecondsInHistory = preferencesManager.showMillisecondsInHistory,
            invertLapColors = preferencesManager.invertLapColors,
            appLanguage = preferencesManager.appLanguage,
            autoBackupEnabled = preferencesManager.autoBackupEnabled,
            dimBrightness = preferencesManager.dimBrightness,
            hideTimeWhileRunning = preferencesManager.hideTimeWhileRunning,
            tickEnabled = preferencesManager.tickEnabled,
            tickAccentsJson = preferencesManager.tickAccentsJson,
            showTimeAsSeconds = preferencesManager.showTimeAsSeconds,
            showTimeAsSecondsHistory = preferencesManager.showTimeAsSecondsHistory,
            showTimeAsSecondsCharts = preferencesManager.showTimeAsSecondsCharts,
            dimTimeoutSeconds = preferencesManager.dimTimeoutSeconds,
            nameToggles = preferencesManager.getAllNameToggles().ifEmpty { null }
        )

        return BackupData(
            version = "0.12.2",
            timestamp = System.currentTimeMillis(),
            sessions = backupSessions,
            settings = backupSettings
        )
    }

    private suspend fun restoreReplace(
        backupData: BackupData,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): RestoreResult {
        val total = backupData.sessions.size
        // Delete all existing data
        AppLogger.i("BackupManager", "Deleting all existing sessions")
        sessionDao.deleteAllSessions()

        // Insert backup data
        AppLogger.i("BackupManager", "Restoring $total sessions")
        var totalLaps = 0
        backupData.sessions.forEachIndexed { index, backupSession ->
            val finalName = backupSession.name ?: backupSession.comment // Use comment as fallback for old backups
            val finalNotes = backupSession.notes

            AppLogger.d("BackupManager", "Restoring session ${index + 1}/$total: name='$finalName', " +
                    "translations: name_en=${backupSession.name_en != null}, name_ru=${backupSession.name_ru != null}, name_zh=${backupSession.name_zh != null}")

            val session = SessionEntity(
                id = 0, // Let database generate new ID
                startTime = backupSession.startTime,
                endTime = backupSession.endTime,
                totalDuration = backupSession.totalDuration,
                name = finalName,
                notes = finalNotes,
                name_en = backupSession.name_en,
                name_ru = backupSession.name_ru,
                name_zh = backupSession.name_zh,
                notes_en = backupSession.notes_en,
                notes_ru = backupSession.notes_ru,
                notes_zh = backupSession.notes_zh
            )
            val sessionId = sessionDao.insertSession(session)

            // Auto-translate if translations are missing
            if (finalName != null && (backupSession.name_en == null || backupSession.name_ru == null || backupSession.name_zh == null)) {
                AppLogger.i("BackupManager", "Session $sessionId: Auto-translating name '$finalName' (missing translations)")
                autoTranslateName(sessionId, finalName)
            }
            if (finalNotes != null && !finalNotes.isBlank() && (backupSession.notes_en == null || backupSession.notes_ru == null || backupSession.notes_zh == null)) {
                AppLogger.i("BackupManager", "Session $sessionId: Auto-translating notes (missing translations)")
                autoTranslateNotes(sessionId, finalNotes)
            }

            val laps = backupSession.laps.map { backupLap ->
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = backupLap.lapNumber,
                    totalTime = backupLap.totalTime,
                    lapDuration = backupLap.lapDuration
                )
            }
            if (laps.isNotEmpty()) {
                sessionDao.insertLaps(laps)
                totalLaps += laps.size
            }

            onProgress(index + 1, total)
        }

        // Restore session_names from unique session names
        restoreSessionNames(backupData)

        // Restore settings if available
        val settingsRestored = backupData.settings != null
        backupData.settings?.let { settings ->
            AppLogger.i("BackupManager", "Restoring settings: language=${settings.appLanguage}")
            restoreSettings(settings)
        }

        return RestoreResult(total, totalLaps, settingsRestored)
    }

    private suspend fun restoreMerge(
        backupData: BackupData,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): RestoreResult {
        val total = backupData.sessions.size
        // Insert backup data (merge with existing)
        AppLogger.i("BackupManager", "Merging $total sessions with existing data")
        var totalLaps = 0
        backupData.sessions.forEachIndexed { index, backupSession ->
            val finalName = backupSession.name ?: backupSession.comment // Use comment as fallback for old backups
            val finalNotes = backupSession.notes

            AppLogger.d("BackupManager", "Merging session ${index + 1}/$total: name='$finalName', " +
                    "translations: name_en=${backupSession.name_en != null}, name_ru=${backupSession.name_ru != null}, name_zh=${backupSession.name_zh != null}")

            val session = SessionEntity(
                id = 0, // Let database generate new ID
                startTime = backupSession.startTime,
                endTime = backupSession.endTime,
                totalDuration = backupSession.totalDuration,
                name = finalName,
                notes = finalNotes,
                name_en = backupSession.name_en,
                name_ru = backupSession.name_ru,
                name_zh = backupSession.name_zh,
                notes_en = backupSession.notes_en,
                notes_ru = backupSession.notes_ru,
                notes_zh = backupSession.notes_zh
            )
            val sessionId = sessionDao.insertSession(session)

            // Auto-translate if translations are missing
            if (finalName != null && (backupSession.name_en == null || backupSession.name_ru == null || backupSession.name_zh == null)) {
                AppLogger.i("BackupManager", "Session $sessionId: Auto-translating name '$finalName' (missing translations)")
                autoTranslateName(sessionId, finalName)
            }
            if (finalNotes != null && !finalNotes.isBlank() && (backupSession.notes_en == null || backupSession.notes_ru == null || backupSession.notes_zh == null)) {
                AppLogger.i("BackupManager", "Session $sessionId: Auto-translating notes (missing translations)")
                autoTranslateNotes(sessionId, finalNotes)
            }

            val laps = backupSession.laps.map { backupLap ->
                LapEntity(
                    sessionId = sessionId,
                    lapNumber = backupLap.lapNumber,
                    totalTime = backupLap.totalTime,
                    lapDuration = backupLap.lapDuration
                )
            }
            if (laps.isNotEmpty()) {
                sessionDao.insertLaps(laps)
                totalLaps += laps.size
            }

            onProgress(index + 1, total)
        }

        // Restore session_names from unique session names
        restoreSessionNames(backupData)

        // Restore settings if available
        val settingsRestored = backupData.settings != null
        backupData.settings?.let { settings ->
            AppLogger.i("BackupManager", "Restoring settings: language=${settings.appLanguage}")
            restoreSettings(settings)
        }

        return RestoreResult(total, totalLaps, settingsRestored)
    }

    private suspend fun restoreSessionNames(backupData: BackupData) {
        val dao = sessionNameDao ?: return
        val uniqueNames = backupData.sessions.mapNotNull { it.name ?: it.comment }
            .filter { it.isNotBlank() }
            .distinct()
        AppLogger.i("BackupManager", "Restoring ${uniqueNames.size} session names")
        uniqueNames.forEach { name ->
            dao.insert(SessionNameEntity(name = name))
        }
    }

    private fun restoreSettings(settings: BackupSettings) {
        preferencesManager.showMilliseconds = settings.showMilliseconds
        preferencesManager.screenOnMode = try {
            ScreenOnMode.valueOf(settings.screenOnMode)
        } catch (e: IllegalArgumentException) {
            ScreenOnMode.WHILE_RUNNING
        }
        preferencesManager.lockOrientation = settings.lockOrientation
        preferencesManager.showMillisecondsInHistory = settings.showMillisecondsInHistory
        preferencesManager.invertLapColors = settings.invertLapColors
        settings.appLanguage?.let { preferencesManager.appLanguage = it }
        preferencesManager.autoBackupEnabled = settings.autoBackupEnabled
        preferencesManager.dimBrightness = settings.dimBrightness
        preferencesManager.hideTimeWhileRunning = settings.hideTimeWhileRunning
        preferencesManager.tickEnabled = settings.tickEnabled
        settings.tickAccentsJson?.let { preferencesManager.tickAccentsJson = it }
        preferencesManager.showTimeAsSeconds = settings.showTimeAsSeconds
        preferencesManager.showTimeAsSecondsHistory = settings.showTimeAsSecondsHistory
        preferencesManager.showTimeAsSecondsCharts = settings.showTimeAsSecondsCharts
        preferencesManager.dimTimeoutSeconds = settings.dimTimeoutSeconds
        settings.nameToggles?.let { preferencesManager.setAllNameToggles(it) }
    }

    /**
     * Auto-translate session name to all languages
     */
    private suspend fun autoTranslateName(sessionId: Long, name: String) {
        val currentLang = preferencesManager.getCurrentLanguage()
        AppLogger.i("BackupManager", "Auto-translating name for session $sessionId: '$name' (current language: $currentLang)")

        // Translate to English
        val (nameEn, _) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "en",
            name = name,
            notes = null
        )
        AppLogger.d("BackupManager", "Session $sessionId: name_en = '$nameEn'")

        // Translate to Russian
        val (nameRu, _) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "ru",
            name = name,
            notes = null
        )
        AppLogger.d("BackupManager", "Session $sessionId: name_ru = '$nameRu'")

        // Translate to Chinese
        val (nameZh, _) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "zh",
            name = name,
            notes = null
        )
        AppLogger.d("BackupManager", "Session $sessionId: name_zh = '$nameZh'")

        // Save translations
        sessionDao.updateSessionNameTranslations(
            sessionId = sessionId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameZh = nameZh
        )
        AppLogger.i("BackupManager", "Session $sessionId: Name translations saved successfully")
    }

    /**
     * Auto-translate session notes to all languages
     */
    private suspend fun autoTranslateNotes(sessionId: Long, notes: String) {
        val currentLang = preferencesManager.getCurrentLanguage()
        AppLogger.i("BackupManager", "Auto-translating notes for session $sessionId (current language: $currentLang)")

        // Translate to English
        val (_, notesEn) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "en",
            name = null,
            notes = notes
        )
        AppLogger.d("BackupManager", "Session $sessionId: notes_en translated")

        // Translate to Russian
        val (_, notesRu) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "ru",
            name = null,
            notes = notes
        )
        AppLogger.d("BackupManager", "Session $sessionId: notes_ru translated")

        // Translate to Chinese
        val (_, notesZh) = translationManager.translateSession(
            sessionId = sessionId,
            currentLang = currentLang,
            targetLang = "zh",
            name = null,
            notes = notes
        )
        AppLogger.d("BackupManager", "Session $sessionId: notes_zh translated")

        // Save translations
        sessionDao.updateSessionNotesTranslations(
            sessionId = sessionId,
            notesEn = notesEn,
            notesRu = notesRu,
            notesZh = notesZh
        )
        AppLogger.i("BackupManager", "Session $sessionId: Notes translations saved successfully")
    }

    private fun backupDataToJson(data: BackupData): String {
        val json = JSONObject()
        json.put("version", data.version)
        json.put("timestamp", data.timestamp)

        // Add settings if available
        data.settings?.let { settings ->
            val settingsObj = JSONObject()
            settingsObj.put("showMilliseconds", settings.showMilliseconds)
            settingsObj.put("screenOnMode", settings.screenOnMode)
            settingsObj.put("lockOrientation", settings.lockOrientation)
            settingsObj.put("showMillisecondsInHistory", settings.showMillisecondsInHistory)
            settingsObj.put("invertLapColors", settings.invertLapColors)
            settingsObj.put("appLanguage", settings.appLanguage ?: JSONObject.NULL)
            settingsObj.put("autoBackupEnabled", settings.autoBackupEnabled)
            settingsObj.put("dimBrightness", settings.dimBrightness)
            settingsObj.put("hideTimeWhileRunning", settings.hideTimeWhileRunning)
            settingsObj.put("tickEnabled", settings.tickEnabled)
            settingsObj.put("tickAccentsJson", settings.tickAccentsJson ?: JSONObject.NULL)
            settingsObj.put("showTimeAsSeconds", settings.showTimeAsSeconds)
            settingsObj.put("showTimeAsSecondsHistory", settings.showTimeAsSecondsHistory)
            settingsObj.put("showTimeAsSecondsCharts", settings.showTimeAsSecondsCharts)
            settingsObj.put("dimTimeoutSeconds", settings.dimTimeoutSeconds)
            val nameTogglesObj = JSONObject()
            settings.nameToggles?.forEach { (name, toggles) ->
                val t = JSONObject()
                t.put("showMilliseconds", toggles.showMilliseconds)
                t.put("screenOnMode", toggles.screenOnMode)
                t.put("lockOrientation", toggles.lockOrientation)
                t.put("invertLapColors", toggles.invertLapColors)
                t.put("dimBrightness", toggles.dimBrightness)
                t.put("hideTimeWhileRunning", toggles.hideTimeWhileRunning)
                t.put("showTimeAsSeconds", toggles.showTimeAsSeconds)
                t.put("tickEnabled", toggles.tickEnabled)
                if (toggles.tickAccentsJson != null) t.put("tickAccentsJson", toggles.tickAccentsJson) else t.put("tickAccentsJson", JSONObject.NULL)
                nameTogglesObj.put(name, t)
            }
            settingsObj.put("nameToggles", nameTogglesObj)
            json.put("settings", settingsObj)
        }

        val sessionsArray = JSONArray()
        data.sessions.forEach { session ->
            val sessionObj = JSONObject()
            sessionObj.put("id", session.id)
            sessionObj.put("startTime", session.startTime)
            sessionObj.put("endTime", session.endTime)
            sessionObj.put("totalDuration", session.totalDuration)
            sessionObj.put("name", session.name ?: JSONObject.NULL)
            sessionObj.put("notes", session.notes ?: JSONObject.NULL)
            sessionObj.put("name_en", session.name_en ?: JSONObject.NULL)
            sessionObj.put("name_ru", session.name_ru ?: JSONObject.NULL)
            sessionObj.put("name_zh", session.name_zh ?: JSONObject.NULL)
            sessionObj.put("notes_en", session.notes_en ?: JSONObject.NULL)
            sessionObj.put("notes_ru", session.notes_ru ?: JSONObject.NULL)
            sessionObj.put("notes_zh", session.notes_zh ?: JSONObject.NULL)
            // Keep comment for backward compatibility
            sessionObj.put("comment", session.comment ?: session.name ?: JSONObject.NULL)

            val lapsArray = JSONArray()
            session.laps.forEach { lap ->
                val lapObj = JSONObject()
                lapObj.put("lapNumber", lap.lapNumber)
                lapObj.put("totalTime", lap.totalTime)
                lapObj.put("lapDuration", lap.lapDuration)
                lapsArray.put(lapObj)
            }
            sessionObj.put("laps", lapsArray)
            sessionsArray.put(sessionObj)
        }
        json.put("sessions", sessionsArray)

        return json.toString(2) // Pretty print with 2 spaces
    }

    private fun jsonToBackupData(jsonString: String): BackupData {
        val json = JSONObject(jsonString)
        val version = json.getString("version")
        val timestamp = json.getLong("timestamp")

        // Parse settings if available
        val settings = if (json.has("settings")) {
            val settingsObj = json.getJSONObject("settings")
            BackupSettings(
                showMilliseconds = settingsObj.getBoolean("showMilliseconds"),
                screenOnMode = settingsObj.getString("screenOnMode"),
                lockOrientation = settingsObj.getBoolean("lockOrientation"),
                showMillisecondsInHistory = settingsObj.getBoolean("showMillisecondsInHistory"),
                invertLapColors = settingsObj.getBoolean("invertLapColors"),
                appLanguage = if (settingsObj.isNull("appLanguage")) null else settingsObj.getString("appLanguage"),
                autoBackupEnabled = if (settingsObj.has("autoBackupEnabled")) settingsObj.getBoolean("autoBackupEnabled") else false,
                dimBrightness = if (settingsObj.has("dimBrightness")) settingsObj.getBoolean("dimBrightness") else false,
                hideTimeWhileRunning = if (settingsObj.has("hideTimeWhileRunning")) settingsObj.getBoolean("hideTimeWhileRunning") else false,
                tickEnabled = if (settingsObj.has("tickEnabled")) settingsObj.getBoolean("tickEnabled") else false,
                tickAccentsJson = if (settingsObj.has("tickAccentsJson") && !settingsObj.isNull("tickAccentsJson")) settingsObj.getString("tickAccentsJson") else null,
                showTimeAsSeconds = if (settingsObj.has("showTimeAsSeconds")) settingsObj.getBoolean("showTimeAsSeconds") else false,
                showTimeAsSecondsHistory = if (settingsObj.has("showTimeAsSecondsHistory")) settingsObj.getBoolean("showTimeAsSecondsHistory") else false,
                showTimeAsSecondsCharts = if (settingsObj.has("showTimeAsSecondsCharts")) settingsObj.getBoolean("showTimeAsSecondsCharts") else false,
                dimTimeoutSeconds = if (settingsObj.has("dimTimeoutSeconds")) settingsObj.getInt("dimTimeoutSeconds") else 30,
                nameToggles = if (settingsObj.has("nameToggles")) {
                    val ntObj = settingsObj.getJSONObject("nameToggles")
                    val map = mutableMapOf<String, NameToggles>()
                    ntObj.keys().forEach { key ->
                        try {
                            val t = ntObj.getJSONObject(key)
                            map[key] = NameToggles(
                                showMilliseconds = t.optBoolean("showMilliseconds", true),
                                screenOnMode = t.optString("screenOnMode", "WHILE_RUNNING"),
                                lockOrientation = t.optBoolean("lockOrientation", false),
                                invertLapColors = t.optBoolean("invertLapColors", false),
                                dimBrightness = t.optBoolean("dimBrightness", true),
                                hideTimeWhileRunning = t.optBoolean("hideTimeWhileRunning", false),
                                showTimeAsSeconds = t.optBoolean("showTimeAsSeconds", false),
                                tickEnabled = t.optBoolean("tickEnabled", false),
                                tickAccentsJson = if (t.has("tickAccentsJson") && !t.isNull("tickAccentsJson")) t.getString("tickAccentsJson") else null
                            )
                        } catch (_: Exception) {}
                    }
                    map.ifEmpty { null }
                } else null
            )
        } else {
            null
        }

        val sessions = mutableListOf<BackupSession>()
        val sessionsArray = json.getJSONArray("sessions")
        for (i in 0 until sessionsArray.length()) {
            val sessionObj = sessionsArray.getJSONObject(i)
            val laps = mutableListOf<BackupLap>()
            val lapsArray = sessionObj.getJSONArray("laps")
            for (j in 0 until lapsArray.length()) {
                val lapObj = lapsArray.getJSONObject(j)
                laps.add(
                    BackupLap(
                        lapNumber = lapObj.getInt("lapNumber"),
                        totalTime = lapObj.getLong("totalTime"),
                        lapDuration = lapObj.getLong("lapDuration")
                    )
                )
            }
            sessions.add(
                BackupSession(
                    id = sessionObj.getLong("id"),
                    startTime = sessionObj.getLong("startTime"),
                    endTime = sessionObj.getLong("endTime"),
                    totalDuration = sessionObj.getLong("totalDuration"),
                    name = if (sessionObj.has("name") && !sessionObj.isNull("name")) sessionObj.getString("name") else null,
                    notes = if (sessionObj.has("notes") && !sessionObj.isNull("notes")) sessionObj.getString("notes") else null,
                    comment = if (sessionObj.has("comment") && !sessionObj.isNull("comment")) sessionObj.getString("comment") else null,
                    name_en = if (sessionObj.has("name_en") && !sessionObj.isNull("name_en")) sessionObj.getString("name_en") else null,
                    name_ru = if (sessionObj.has("name_ru") && !sessionObj.isNull("name_ru")) sessionObj.getString("name_ru") else null,
                    name_zh = if (sessionObj.has("name_zh") && !sessionObj.isNull("name_zh")) sessionObj.getString("name_zh") else null,
                    notes_en = if (sessionObj.has("notes_en") && !sessionObj.isNull("notes_en")) sessionObj.getString("notes_en") else null,
                    notes_ru = if (sessionObj.has("notes_ru") && !sessionObj.isNull("notes_ru")) sessionObj.getString("notes_ru") else null,
                    notes_zh = if (sessionObj.has("notes_zh") && !sessionObj.isNull("notes_zh")) sessionObj.getString("notes_zh") else null,
                    laps = laps
                )
            )
        }

        return BackupData(version, timestamp, sessions, settings)
    }

    private fun generateBackupFileName(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        return "$BACKUP_PREFIX${dateFormat.format(Date())}$BACKUP_EXTENSION"
    }

    private fun extractTimestampFromFileName(fileName: String): Long {
        // Extract "2025-11-13_143022" from "laplog_backup_2025-11-13_143022.json"
        val dateStr = fileName.removePrefix(BACKUP_PREFIX).removeSuffix(BACKUP_EXTENSION)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault())
        return dateFormat.parse(dateStr)?.time ?: 0L
    }

    enum class RestoreMode {
        REPLACE, // Delete all existing data and restore from backup
        MERGE    // Add backup data to existing data
    }
}
