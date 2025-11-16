package com.laplog.app.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.laplog.app.data.BackupManager
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.model.BackupFileInfo
import com.laplog.app.worker.BackupWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BackupViewModel(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    sessionDao: SessionDao
) : ViewModel() {

    private val backupManager = BackupManager(context, preferencesManager, sessionDao)

    private val _backupFolderUri = MutableStateFlow(preferencesManager.backupFolderUri)
    val backupFolderUri: StateFlow<String?> = _backupFolderUri.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(preferencesManager.autoBackupEnabled)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _backupRetentionDays = MutableStateFlow(preferencesManager.backupRetentionDays)
    val backupRetentionDays: StateFlow<Int> = _backupRetentionDays.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(preferencesManager.lastBackupTime)
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    private val _backups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backups: StateFlow<List<BackupFileInfo>> = _backups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadBackups()
    }

    fun setBackupFolder(uri: Uri) {
        preferencesManager.backupFolderUri = uri.toString()
        _backupFolderUri.value = uri.toString()
        loadBackups()
    }

    fun toggleAutoBackup() {
        val newValue = !_autoBackupEnabled.value
        _autoBackupEnabled.value = newValue
        preferencesManager.autoBackupEnabled = newValue

        if (newValue) {
            schedulePeriodicBackup()
        } else {
            cancelPeriodicBackup()
        }
    }

    fun setRetentionDays(days: Int) {
        _backupRetentionDays.value = days
        preferencesManager.backupRetentionDays = days
    }

    fun createBackupNow() {
        val folderUriString = _backupFolderUri.value
        if (folderUriString == null) {
            _errorMessage.value = "Please select backup folder first"
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = backupManager.createBackup(Uri.parse(folderUriString))
            if (result.isSuccess) {
                _lastBackupTime.value = System.currentTimeMillis()
                preferencesManager.lastBackupTime = _lastBackupTime.value

                // Delete old backups
                backupManager.deleteOldBackups(
                    Uri.parse(folderUriString),
                    _backupRetentionDays.value
                )

                loadBackups()
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Backup failed"
            }

            _isLoading.value = false
        }
    }

    fun generateManualBackup(onSuccess: (String, String) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = backupManager.generateBackupData()
            if (result.isSuccess) {
                val (fileName, jsonData) = result.getOrNull()!!
                onSuccess(fileName, jsonData)
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Backup generation failed"
            }

            _isLoading.value = false
        }
    }

    fun restoreBackup(fileInfo: BackupFileInfo, mode: BackupManager.RestoreMode) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val result = backupManager.restoreBackup(fileInfo.uri, mode)
            if (result.isSuccess) {
                _errorMessage.value = "Restored ${result.getOrNull()} sessions"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Restore failed"
            }

            _isLoading.value = false
        }
    }

    fun deleteBackup(fileInfo: BackupFileInfo) {
        viewModelScope.launch {
            try {
                androidx.documentfile.provider.DocumentFile.fromSingleUri(context, fileInfo.uri)?.delete()
                loadBackups()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            }
        }
    }

    fun deleteAllBackups() {
        val folderUriString = _backupFolderUri.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deletedCount = backupManager.deleteAllBackups(Uri.parse(folderUriString))
                _errorMessage.value = "Deleted $deletedCount backups"
                loadBackups()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun deleteBackupsBefore(fileInfo: BackupFileInfo) {
        val folderUriString = _backupFolderUri.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val deletedCount = backupManager.deleteBackupsBefore(
                    Uri.parse(folderUriString),
                    fileInfo.timestamp
                )
                _errorMessage.value = "Deleted $deletedCount backups"
                loadBackups()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.message}"
            }
            _isLoading.value = false
        }
    }

    fun loadBackups() {
        val folderUriString = _backupFolderUri.value ?: return

        viewModelScope.launch {
            try {
                _backups.value = backupManager.listBackups(Uri.parse(folderUriString))
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load backups: ${e.message}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun schedulePeriodicBackup() {
        val workRequest = PeriodicWorkRequestBuilder<BackupWorker>(
            1, TimeUnit.DAYS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "backup_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun cancelPeriodicBackup() {
        WorkManager.getInstance(context).cancelUniqueWork("backup_work")
    }
}
