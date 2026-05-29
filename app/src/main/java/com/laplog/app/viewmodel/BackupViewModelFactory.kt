package com.laplog.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.dao.SessionNameDao

class BackupViewModelFactory(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao,
    private val translationManager: TranslationManager,
    private val sessionNameDao: SessionNameDao? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BackupViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BackupViewModel(context, preferencesManager, sessionDao, translationManager, sessionNameDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
