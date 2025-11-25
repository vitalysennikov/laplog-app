package com.laplog.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao

class StopwatchViewModelFactory(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao,
    private val translationManager: TranslationManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StopwatchViewModel::class.java)) {
            return StopwatchViewModel(context, preferencesManager, sessionDao, translationManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
