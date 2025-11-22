package com.laplog.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao

class ChartsViewModelFactory(
    private val sessionDao: SessionDao,
    private val preferencesManager: PreferencesManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChartsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChartsViewModel(sessionDao, preferencesManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
