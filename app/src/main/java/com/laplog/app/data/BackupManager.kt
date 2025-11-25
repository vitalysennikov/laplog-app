package com.laplog.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.BackupData
import com.laplog.app.model.BackupFileInfo
import com.laplog.app.model.BackupLap
import com.laplog.app.model.BackupSession
import com.laplog.app.model.BackupSettings
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
    private val translationManager: TranslationManager
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
                try {
                    // Extract timestamp from filename
                    val timestamp = extractTimestampFromFileName(file.name!!)
                    backups.add(
                        BackupFileInfo(
                            uri = file.uri,
                            name = file.name!!,
                            timestamp = timestamp,
                            size = file.length()
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid files
                }
            }
        }

        return backups.sortedByDescending { it.timestamp }
    }

    /**
     * Restore database from backup
     */
    suspend fun restoreBackup(fileUri: Uri, mode: RestoreMode): Result<Int> {
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

            when (mode) {
                RestoreMode.REPLACE -> {
                    AppLogger.i("BackupManager", "Starting REPLACE mode restoration")
                    restoreReplace(backupData)
                }
                RestoreMode.MERGE -> {
                    AppLogger.i("BackupManager", "Starting MERGE mode restoration")
                    restoreMerge(backupData)
                }
            }

            AppLogger.i("BackupManager", "Backup restored successfully: ${backupData.sessions.size} sessions")
            Result.success(backupData.sessions.size)
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
            autoBackupEnabled = preferencesManager.autoBackupEnabled
        )

        return BackupData(
            version = "0.12.2",
            timestamp = System.currentTimeMillis(),
            sessions = backupSessions,
            settings = backupSettings
        )
    }

    private suspend fun restoreReplace(backupData: BackupData) {
        // Delete all existing data
        AppLogger.i("BackupManager", "Deleting all existing sessions")
        sessionDao.deleteAllSessions()

        // Insert backup data
        AppLogger.i("BackupManager", "Restoring ${backupData.sessions.size} sessions")
        backupData.sessions.forEachIndexed { index, backupSession ->
            val finalName = backupSession.name ?: backupSession.comment // Use comment as fallback for old backups
            val finalNotes = backupSession.notes

            AppLogger.d("BackupManager", "Restoring session ${index + 1}/${backupData.sessions.size}: name='$finalName', " +
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
            }
        }

        // Restore settings if available
        backupData.settings?.let { settings ->
            AppLogger.i("BackupManager", "Restoring settings: language=${settings.appLanguage}")
            restoreSettings(settings)
        }
    }

    private suspend fun restoreMerge(backupData: BackupData) {
        // Insert backup data (merge with existing)
        AppLogger.i("BackupManager", "Merging ${backupData.sessions.size} sessions with existing data")
        backupData.sessions.forEachIndexed { index, backupSession ->
            val finalName = backupSession.name ?: backupSession.comment // Use comment as fallback for old backups
            val finalNotes = backupSession.notes

            AppLogger.d("BackupManager", "Merging session ${index + 1}/${backupData.sessions.size}: name='$finalName', " +
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
            }
        }

        // Restore settings if available
        backupData.settings?.let { settings ->
            AppLogger.i("BackupManager", "Restoring settings: language=${settings.appLanguage}")
            restoreSettings(settings)
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
                autoBackupEnabled = if (settingsObj.has("autoBackupEnabled")) settingsObj.getBoolean("autoBackupEnabled") else false
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
