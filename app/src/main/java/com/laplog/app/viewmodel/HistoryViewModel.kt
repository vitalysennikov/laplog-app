package com.laplog.app.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.dao.SessionNameDao
import com.laplog.app.data.database.entity.LapEntity
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.data.database.entity.SessionNameEntity
import com.laplog.app.model.SessionWithLaps
import com.laplog.app.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val preferencesManager: PreferencesManager,
    private val sessionDao: SessionDao,
    private val sessionNameDao: SessionNameDao,
    private val translationManager: TranslationManager
) : ViewModel() {

    private var loadSessionsJob: Job? = null

    private val _sessions = MutableStateFlow<List<SessionWithLaps>>(emptyList())
    val sessions: StateFlow<List<SessionWithLaps>> = _sessions.asStateFlow()

    val sessionNames: StateFlow<List<SessionNameEntity>> = sessionNameDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val usedNames: StateFlow<Set<String>> = sessionNameDao.getAllFlow()
        .map { list -> list.map { it.name }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _expandAll = MutableStateFlow(true) // default: all expanded
    val expandAll: StateFlow<Boolean> = _expandAll.asStateFlow()

    private val _showMillisecondsInHistory = MutableStateFlow(preferencesManager.showMillisecondsInHistory)
    val showMillisecondsInHistory: StateFlow<Boolean> = _showMillisecondsInHistory.asStateFlow()

    private val _showTimeAsSecondsHistory = MutableStateFlow(preferencesManager.showTimeAsSecondsHistory)
    val showTimeAsSecondsHistory: StateFlow<Boolean> = _showTimeAsSecondsHistory.asStateFlow()

    private val _invertLapColors = MutableStateFlow(preferencesManager.invertLapColors)
    val invertLapColors: StateFlow<Boolean> = _invertLapColors.asStateFlow()

    val namesFromHistory: StateFlow<List<String>> = sessionNameDao.getAllFlow()
        .map { list -> list.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filterName = MutableStateFlow<String?>(null)
    val filterName: StateFlow<String?> = _filterName.asStateFlow()

    private val _showTableView = MutableStateFlow(false)
    val showTableView: StateFlow<Boolean> = _showTableView.asStateFlow()

    init {
        loadSessions()
    }

    fun toggleMillisecondsInHistory() {
        _showMillisecondsInHistory.value = !_showMillisecondsInHistory.value
        preferencesManager.showMillisecondsInHistory = _showMillisecondsInHistory.value
    }

    fun toggleShowTimeAsSecondsHistory() {
        _showTimeAsSecondsHistory.value = !_showTimeAsSecondsHistory.value
        preferencesManager.showTimeAsSecondsHistory = _showTimeAsSecondsHistory.value
    }

    fun toggleInvertLapColors() {
        _invertLapColors.value = !_invertLapColors.value
        preferencesManager.invertLapColors = _invertLapColors.value
    }

    private fun loadSessions() {
        // Cancel previous job if exists
        loadSessionsJob?.cancel()

        loadSessionsJob = viewModelScope.launch {
            sessionDao.getAllSessions().collect { sessionEntities ->
                AppLogger.d("HistoryViewModel", "Loaded ${sessionEntities.size} sessions from database")

                // Apply filter if set
                val filteredSessions = if (_filterName.value != null) {
                    AppLogger.d("HistoryViewModel", "Applying filter: name='${_filterName.value}'")
                    sessionEntities.filter { it.name == _filterName.value }
                } else {
                    sessionEntities
                }

                AppLogger.d("HistoryViewModel", "Filtered to ${filteredSessions.size} sessions")

                val sessionsWithLaps = mutableListOf<SessionWithLaps>()

                for (session in filteredSessions) {
                    AppLogger.d("HistoryViewModel", "Session ID: ${session.id}, StartTime: ${session.startTime}, Duration: ${session.totalDuration}")
                    // Get first emission from laps flow using first()
                    val laps = sessionDao.getLapsForSession(session.id).first()
                    AppLogger.d("HistoryViewModel", "Session ${session.id} has ${laps.size} laps")
                    sessionsWithLaps.add(SessionWithLaps(session, laps))
                }

                _sessions.value = sessionsWithLaps
                AppLogger.i("HistoryViewModel", "Updated sessions state with ${sessionsWithLaps.size} sessions")
            }
        }
    }

    fun updateSessionName(sessionId: Long, name: String) {
        viewModelScope.launch {
            AppLogger.i("HistoryViewModel", "Updating session $sessionId name to '$name'")
            sessionDao.updateSessionName(sessionId, name)

            if (name.isNotBlank()) {
                // Ensure name exists in session_names
                val existing = sessionNameDao.getByName(name)
                val nameId = existing?.id ?: sessionNameDao.insert(
                    com.laplog.app.data.database.entity.SessionNameEntity(name = name)
                )
                sessionDao.renameSessionsByName(name, name, nameId)

                // Translate to all languages
                val currentLang = preferencesManager.getCurrentLanguage()
                AppLogger.i("HistoryViewModel", "Session $sessionId: Translating name from $currentLang to all languages")
                val (nameEn, nameRu, nameZh) = translateToAllLanguages(name, currentLang)

                AppLogger.d("HistoryViewModel", "Session $sessionId: Translations - EN: '$nameEn', RU: '$nameRu', ZH: '$nameZh'")

                sessionDao.updateSessionNameTranslations(
                    sessionId = sessionId,
                    nameEn = nameEn,
                    nameRu = nameRu,
                    nameZh = nameZh
                )
                AppLogger.i("HistoryViewModel", "Session $sessionId: Name translations saved")
            }

            loadSessions()
        }
    }

    fun updateSessionNotes(sessionId: Long, notes: String) {
        viewModelScope.launch {
            sessionDao.updateSessionNotes(sessionId, notes)

            // Translate to all languages if notes are not blank
            if (notes.isNotBlank()) {
                val currentLang = preferencesManager.getCurrentLanguage()
                val (notesEn, notesRu, notesZh) = translateToAllLanguages(notes, currentLang)

                // Save translations
                sessionDao.updateSessionNotesTranslations(
                    sessionId = sessionId,
                    notesEn = notesEn,
                    notesRu = notesRu,
                    notesZh = notesZh
                )
            }

            loadSessions()
        }
    }

    fun setFilterName(name: String?) {
        AppLogger.i("HistoryViewModel", "Setting filter name: ${if (name != null) "'$name'" else "null (clearing filter)"}")
        _filterName.value = name
        loadSessions()
    }

    fun toggleTableView() {
        _showTableView.value = !_showTableView.value
    }

    fun deleteSession(session: SessionEntity) {
        viewModelScope.launch {
            sessionDao.deleteSession(session)
            loadSessions()
        }
    }

    fun deleteSessionsBefore(beforeTime: Long) {
        viewModelScope.launch {
            sessionDao.deleteSessionsBefore(beforeTime)
            loadSessions()
        }
    }

    fun deleteAllSessions() {
        viewModelScope.launch {
            sessionDao.deleteAllSessions()
            loadSessions()
        }
    }

    fun toggleExpandAll() {
        _expandAll.value = !_expandAll.value
    }

    fun createSession(
        name: String,
        notes: String,
        startTimeMs: Long,
        durationMs: Long,
        lapDurations: List<Long>
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim().ifBlank { null }
            val trimmedNotes = notes.trim().ifBlank { null }
            val session = SessionEntity(
                startTime = startTimeMs,
                endTime = startTimeMs + durationMs,
                totalDuration = durationMs,
                name = trimmedName,
                notes = trimmedNotes
            )
            val sessionId = sessionDao.insertSession(session)
            insertLapsForSession(sessionId, lapDurations)

            trimmedName?.let { n ->
                val lang = preferencesManager.getCurrentLanguage()
                val (en, ru, zh) = translateToAllLanguages(n, lang)
                sessionDao.updateSessionNameTranslations(sessionId, en, ru, zh)
            }
            trimmedNotes?.let { n ->
                val lang = preferencesManager.getCurrentLanguage()
                val (en, ru, zh) = translateToAllLanguages(n, lang)
                sessionDao.updateSessionNotesTranslations(sessionId, en, ru, zh)
            }
            loadSessions()
        }
    }

    fun updateSessionFull(
        sessionId: Long,
        name: String,
        notes: String,
        startTimeMs: Long,
        durationMs: Long,
        lapDurations: List<Long>
    ) {
        viewModelScope.launch {
            val trimmedName = name.trim().ifBlank { null }
            val trimmedNotes = notes.trim().ifBlank { null }
            val session = SessionEntity(
                id = sessionId,
                startTime = startTimeMs,
                endTime = startTimeMs + durationMs,
                totalDuration = durationMs,
                name = trimmedName,
                notes = trimmedNotes
            )
            sessionDao.updateSession(session)
            sessionDao.deleteAllLapsForSession(sessionId)
            insertLapsForSession(sessionId, lapDurations)

            trimmedName?.let { n ->
                val lang = preferencesManager.getCurrentLanguage()
                val (en, ru, zh) = translateToAllLanguages(n, lang)
                sessionDao.updateSessionNameTranslations(sessionId, en, ru, zh)
            }
            trimmedNotes?.let { n ->
                val lang = preferencesManager.getCurrentLanguage()
                val (en, ru, zh) = translateToAllLanguages(n, lang)
                sessionDao.updateSessionNotesTranslations(sessionId, en, ru, zh)
            }
            loadSessions()
        }
    }

    private suspend fun insertLapsForSession(sessionId: Long, lapDurations: List<Long>) {
        if (lapDurations.isEmpty()) return
        var cumulative = 0L
        val laps = lapDurations.mapIndexed { index, duration ->
            cumulative += duration
            LapEntity(
                sessionId = sessionId,
                lapNumber = index + 1,
                totalTime = cumulative,
                lapDuration = duration
            )
        }
        sessionDao.insertLaps(laps)
    }

    /**
     * Refresh data after backup restoration or external changes
     */
    fun refreshData() {
        AppLogger.i("HistoryViewModel", "Refreshing data (backup restoration or external changes)")
        loadSessions()
    }

    /**
     * Translate text to all supported languages (EN, RU, ZH)
     * Returns Triple of (nameEn, nameRu, nameZh)
     */
    private suspend fun translateToAllLanguages(text: String, fromLang: String): Triple<String?, String?, String?> {
        val translations = translationManager.translateSession(
            sessionId = 0, // Not used in translation
            currentLang = fromLang,
            targetLang = "en",
            name = text,
            notes = null
        )
        val nameEn = translations.first

        val translationsRu = translationManager.translateSession(
            sessionId = 0,
            currentLang = fromLang,
            targetLang = "ru",
            name = text,
            notes = null
        )
        val nameRu = translationsRu.first

        val translationsZh = translationManager.translateSession(
            sessionId = 0,
            currentLang = fromLang,
            targetLang = "zh",
            name = text,
            notes = null
        )
        val nameZh = translationsZh.first

        return Triple(nameEn, nameRu, nameZh)
    }

    /**
     * Get displayed name for session based on current language
     * Falls back to original name if translation not available
     */
    fun getDisplayedName(session: SessionEntity): String {
        val currentLang = preferencesManager.getCurrentLanguage()
        val result = when (currentLang) {
            "en" -> session.name_en ?: session.name
            "ru" -> session.name_ru ?: session.name
            "zh" -> session.name_zh ?: session.name
            else -> session.name
        } ?: ""

        AppLogger.d("HistoryViewModel", "getDisplayedName: session=${session.id}, lang=$currentLang, " +
                "name='${session.name}', name_en='${session.name_en}', name_ru='${session.name_ru}', name_zh='${session.name_zh}', result='$result'")

        return result
    }

    /**
     * Get displayed notes for session based on current language
     * Falls back to original notes if translation not available
     */
    fun getDisplayedNotes(session: SessionEntity): String? {
        val currentLang = preferencesManager.getCurrentLanguage()
        val result = when (currentLang) {
            "en" -> session.notes_en ?: session.notes
            "ru" -> session.notes_ru ?: session.notes
            "zh" -> session.notes_zh ?: session.notes
            else -> session.notes
        }

        if (result != null) {
            AppLogger.d("HistoryViewModel", "getDisplayedNotes: session=${session.id}, lang=$currentLang, has_result=true")
        }

        return result
    }

    fun formatTime(timeInMillis: Long, includeMillis: Boolean = false, showAsSeconds: Boolean = _showTimeAsSecondsHistory.value): String {
        if (showAsSeconds) {
            val totalSeconds = timeInMillis / 1000
            val millis = ((timeInMillis % 1000) / 10).toInt()
            return if (includeMillis) String.format("%d.%02d", totalSeconds, millis)
                   else totalSeconds.toString()
        }

        // Apply mathematical rounding if milliseconds are not shown (≥500ms → +1s)
        val adjustedTime = if (!includeMillis && timeInMillis % 1000 >= 500) {
            timeInMillis + 1000 - (timeInMillis % 1000)
        } else {
            timeInMillis
        }

        val hours = (adjustedTime / 3600000).toInt()
        val minutes = ((adjustedTime % 3600000) / 60000).toInt()
        val seconds = ((adjustedTime % 60000) / 1000).toInt()
        val millis = ((timeInMillis % 1000) / 10).toInt()

        return if (hours > 0) {
            if (includeMillis) {
                String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, millis)
            } else {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
        } else {
            if (includeMillis) {
                String.format("%02d:%02d.%02d", minutes, seconds, millis)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    fun formatDifference(diffMillis: Long, includeMillis: Boolean = true): String {
        // Use unicode minus (U+2212) for consistent width with plus sign
        val sign = if (diffMillis >= 0) "+" else "\u2212"
        val absDiff = kotlin.math.abs(diffMillis)
        val seconds = (absDiff / 1000).toInt()
        val millis = ((absDiff % 1000) / 10).toInt()

        return if (includeMillis) {
            String.format("%s%d.%02d", sign, seconds, millis)
        } else {
            String.format("%s%d", sign, seconds)
        }
    }

    /**
     * Get session data as JSON string including all translations
     */
    suspend fun getSessionDataAsJson(sessionId: Long): String {
        val roomSession = sessionDao.getSessionWithLapsInternal(sessionId)
        val json = org.json.JSONObject()

        json.put("id", roomSession.session.id)
        json.put("startTime", roomSession.session.startTime)
        json.put("endTime", roomSession.session.endTime)
        json.put("totalDuration", roomSession.session.totalDuration)

        json.put("name", roomSession.session.name ?: "")
        json.put("name_en", roomSession.session.name_en ?: "")
        json.put("name_ru", roomSession.session.name_ru ?: "")
        json.put("name_zh", roomSession.session.name_zh ?: "")

        json.put("notes", roomSession.session.notes ?: "")
        json.put("notes_en", roomSession.session.notes_en ?: "")
        json.put("notes_ru", roomSession.session.notes_ru ?: "")
        json.put("notes_zh", roomSession.session.notes_zh ?: "")

        val lapsArray = org.json.JSONArray()
        roomSession.laps.forEach { lap ->
            val lapObj = org.json.JSONObject()
            lapObj.put("lapNumber", lap.lapNumber)
            lapObj.put("totalTime", lap.totalTime)
            lapObj.put("lapDuration", lap.lapDuration)
            lapsArray.put(lapObj)
        }
        json.put("laps", lapsArray)

        return json.toString(2) // Pretty print with 2 spaces indent
    }

    /**
     * Force translation for a session
     */
    fun forceTranslateSession(sessionId: Long) {
        viewModelScope.launch {
            try {
                AppLogger.i("HistoryViewModel", "Force translating session $sessionId")
                val session = sessionDao.getSessionById(sessionId).first()
                val currentLang = preferencesManager.getCurrentLanguage()

                // Translate name if exists
                session.name?.takeIf { it.isNotBlank() }?.let { name ->
                    AppLogger.i("HistoryViewModel", "Translating name: $name")
                    val (nameEn, nameRu, nameZh) = translateToAllLanguages(name, currentLang)
                    sessionDao.updateSessionNameTranslations(
                        sessionId = sessionId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameZh = nameZh
                    )
                }

                // Translate notes if exists
                session.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    AppLogger.i("HistoryViewModel", "Translating notes: $notes")
                    val (notesEn, notesRu, notesZh) = translateToAllLanguages(notes, currentLang)
                    sessionDao.updateSessionNotesTranslations(
                        sessionId = sessionId,
                        notesEn = notesEn,
                        notesRu = notesRu,
                        notesZh = notesZh
                    )
                }

                AppLogger.i("HistoryViewModel", "Force translation completed for session $sessionId")
                loadSessions()
            } catch (e: Exception) {
                AppLogger.e("HistoryViewModel", "Error force translating session $sessionId", e)
            }
        }
    }
}
