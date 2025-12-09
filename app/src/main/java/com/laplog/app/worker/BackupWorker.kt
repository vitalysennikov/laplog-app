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
        android.util.Log.d("BackupWorker", "BackupWorker started at ${java.util.Date()}")

        val preferencesManager = PreferencesManager(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val translationManager = TranslationManager(database.sessionDao())
        val backupManager = BackupManager(applicationContext, preferencesManager, database.sessionDao(), translationManager)

        // Check if auto backup is enabled
        if (!preferencesManager.autoBackupEnabled) {
            android.util.Log.d("BackupWorker", "Auto backup is disabled")
            return Result.success()
        }

        // Check if backup folder is configured
        val folderUriString = preferencesManager.backupFolderUri
        if (folderUriString == null) {
            android.util.Log.e("BackupWorker", "Backup folder not configured")
            return Result.failure()
        }

        val folderUri = Uri.parse(folderUriString)

        return try {
            // Create backup
            android.util.Log.d("BackupWorker", "Creating backup to $folderUri")
            val result = backupManager.createBackup(folderUri)
            if (result.isSuccess) {
                // Update last backup time
                preferencesManager.lastBackupTime = System.currentTimeMillis()

                // Delete old backups
                val retentionDays = preferencesManager.backupRetentionDays
                backupManager.deleteOldBackups(folderUri, retentionDays)

                android.util.Log.d("BackupWorker", "Backup completed successfully")
                Result.success()
            } else {
                android.util.Log.e("BackupWorker", "Backup failed: ${result.exceptionOrNull()?.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e("BackupWorker", "Backup exception", e)
            Result.retry()
        }
    }
}
