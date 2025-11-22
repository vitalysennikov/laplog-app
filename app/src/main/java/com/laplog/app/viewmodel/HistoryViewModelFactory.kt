package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao

class HistoryViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao,
    private val translationManager: TranslationManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            return HistoryViewModel(preferencesManager, sessionDao, translationManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
