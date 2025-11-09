package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao

class StopwatchViewModelFactory(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StopwatchViewModel::class.java)) {
            return StopwatchViewModel(preferencesManager, sessionDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
