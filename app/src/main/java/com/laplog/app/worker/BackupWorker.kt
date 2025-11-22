package com.laplog.app.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.laplog.app.data.BackupManager
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.AppDatabase

class BackupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferencesManager = PreferencesManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val translationManager = TranslationManager(database.sessionDao())
        val backupManager = BackupManager(applicationContext, preferencesManager, database.sessionDao(), translationManager)

        // Check if auto backup is enabled
        if (!preferencesManager.autoBackupEnabled) {
            return Result.success()
        }

        // Check if backup folder is configured
        val folderUriString = preferencesManager.backupFolderUri
        if (folderUriString == null) {
            return Result.failure()
        }

        val folderUri = Uri.parse(folderUriString)

        return try {
            // Create backup
            val result = backupManager.createBackup(folderUri)
            if (result.isSuccess) {
                // Update last backup time
                preferencesManager.lastBackupTime = System.currentTimeMillis()

                // Delete old backups
                val retentionDays = preferencesManager.backupRetentionDays
                backupManager.deleteOldBackups(folderUri, retentionDays)

                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
