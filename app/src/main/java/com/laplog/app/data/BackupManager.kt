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
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class BackupManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
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
        return try {
            val backupData = createBackupData()

            // Convert to JSON
            val json = backupDataToJson(backupData)

            // Save to file
            val fileName = generateBackupFileName()
            val folder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return Result.failure(Exception("Invalid folder URI"))

            val file = folder.createFile("application/json", fileName)
                ?: return Result.failure(Exception("Failed to create backup file"))

            context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            } ?: return Result.failure(Exception("Failed to write backup file"))

            val fileInfo = BackupFileInfo(
                uri = file.uri,
                name = fileName,
                timestamp = backupData.timestamp,
                size = json.toByteArray().size.toLong()
            )

            Result.success(fileInfo)
        } catch (e: Exception) {
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
        return try {
            // Read JSON from file
            val json = context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return Result.failure(Exception("Failed to read backup file"))

            val backupData = jsonToBackupData(json)

            when (mode) {
                RestoreMode.REPLACE -> restoreReplace(backupData)
                RestoreMode.MERGE -> restoreMerge(backupData)
            }

            Result.success(backupData.sessions.size)
        } catch (e: Exception) {
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
            appLanguage = preferencesManager.appLanguage
        )

        return BackupData(
            version = "0.10.0",
            timestamp = System.currentTimeMillis(),
            sessions = backupSessions,
            settings = backupSettings
        )
    }

    private suspend fun restoreReplace(backupData: BackupData) {
        // Delete all existing data
        sessionDao.deleteAllSessions()

        // Insert backup data
        backupData.sessions.forEach { backupSession ->
            val session = SessionEntity(
                id = 0, // Let database generate new ID
                startTime = backupSession.startTime,
                endTime = backupSession.endTime,
                totalDuration = backupSession.totalDuration,
                name = backupSession.name ?: backupSession.comment, // Use comment as fallback for old backups
                notes = backupSession.notes
            )
            val sessionId = sessionDao.insertSession(session)

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
            restoreSettings(settings)
        }
    }

    private suspend fun restoreMerge(backupData: BackupData) {
        // Insert backup data (merge with existing)
        backupData.sessions.forEach { backupSession ->
            val session = SessionEntity(
                id = 0, // Let database generate new ID
                startTime = backupSession.startTime,
                endTime = backupSession.endTime,
                totalDuration = backupSession.totalDuration,
                name = backupSession.name ?: backupSession.comment, // Use comment as fallback for old backups
                notes = backupSession.notes
            )
            val sessionId = sessionDao.insertSession(session)

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
            json.put("settings", settingsObj)
        }

        val sessionsArray = JSONArray()
        data.sessions.forEach { session ->
            val sessionObj = JSONObject()
            sessionObj.put("id", session.id)
            sessionObj.put("startTime", session.startTime)
            sessionObj.put("endTime", session.endTime)
            sessionObj.put("totalDuration", session.totalDuration)
            sessionObj.put("comment", session.comment ?: JSONObject.NULL)

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
                appLanguage = if (settingsObj.isNull("appLanguage")) null else settingsObj.getString("appLanguage")
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
                    comment = if (sessionObj.isNull("comment")) null else sessionObj.getString("comment"),
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
