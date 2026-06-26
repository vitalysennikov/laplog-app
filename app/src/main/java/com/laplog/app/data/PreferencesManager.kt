package com.laplog.app.data

import android.content.Context
import android.content.SharedPreferences
import com.laplog.app.model.NameToggles
import org.json.JSONObject
import java.util.Locale

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    var showMilliseconds: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MILLISECONDS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MILLISECONDS, value).apply()

    var screenOnMode: ScreenOnMode
        get() {
            val modeName = prefs.getString(KEY_SCREEN_ON_MODE, null)
            return if (modeName != null) {
                try {
                    ScreenOnMode.valueOf(modeName)
                } catch (e: IllegalArgumentException) {
                    ScreenOnMode.WHILE_RUNNING
                }
            } else {
                // Migration from old Boolean keepScreenOn
                val oldValue = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
                if (oldValue) ScreenOnMode.WHILE_RUNNING else ScreenOnMode.OFF
            }
        }
        set(value) = prefs.edit().putString(KEY_SCREEN_ON_MODE, value.name).apply()

    var usedNames: Set<String>
        get() = prefs.getStringSet(KEY_USED_NAMES, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_USED_NAMES, value).apply()

    var lockOrientation: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ORIENTATION, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ORIENTATION, value).apply()

    var currentName: String
        get() = prefs.getString(KEY_CURRENT_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_NAME, value).apply()

    var currentNotes: String
        get() = prefs.getString(KEY_CURRENT_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_NOTES, value).apply()

    var showMillisecondsInHistory: Boolean
        get() = prefs.getBoolean(KEY_SHOW_MILLISECONDS_IN_HISTORY, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_MILLISECONDS_IN_HISTORY, value).apply()

    var invertLapColors: Boolean
        get() = prefs.getBoolean(KEY_INVERT_LAP_COLORS, false)
        set(value) = prefs.edit().putBoolean(KEY_INVERT_LAP_COLORS, value).apply()

    // Backup settings
    var backupFolderUri: String?
        get() = prefs.getString(KEY_BACKUP_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_BACKUP_FOLDER_URI, value).apply()

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, value).apply()

    var backupRetentionDays: Int
        get() = prefs.getInt(KEY_BACKUP_RETENTION_DAYS, 30)
        set(value) = prefs.edit().putInt(KEY_BACKUP_RETENTION_DAYS, value).apply()

    var lastBackupTime: Long
        get() = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BACKUP_TIME, value).apply()

    var appLanguage: String?
        get() = prefs.getString(KEY_APP_LANGUAGE, null)
        set(value) = prefs.edit().putString(KEY_APP_LANGUAGE, value).apply()

    var permissionsRequested: Boolean
        get() = prefs.getBoolean(KEY_PERMISSIONS_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERMISSIONS_REQUESTED, value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, value).apply()

    var dimBrightness: Boolean
        get() = prefs.getBoolean(KEY_DIM_BRIGHTNESS, true)
        set(value) = prefs.edit().putBoolean(KEY_DIM_BRIGHTNESS, value).apply()

    var dimTimeoutSeconds: Int
        get() = prefs.getInt(KEY_DIM_TIMEOUT_SECONDS, 30)
        set(value) = prefs.edit().putInt(KEY_DIM_TIMEOUT_SECONDS, value).apply()

    var hideTimeWhileRunning: Boolean
        get() = prefs.getBoolean(KEY_HIDE_TIME_WHILE_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_TIME_WHILE_RUNNING, value).apply()

    var showTimeAsSeconds: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIME_AS_SECONDS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TIME_AS_SECONDS, value).apply()

    var showTimeAsSecondsHistory: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIME_AS_SECONDS_HISTORY, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TIME_AS_SECONDS_HISTORY, value).apply()

    var showTimeAsSecondsCharts: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TIME_AS_SECONDS_CHARTS, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TIME_AS_SECONDS_CHARTS, value).apply()

    var loggingEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOGGING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOGGING_ENABLED, value).apply()

    var tickEnabled: Boolean
        get() = prefs.getBoolean(KEY_TICK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_TICK_ENABLED, value).apply()

    var tickAccentsJson: String
        get() = prefs.getString(KEY_TICK_ACCENTS_JSON, DEFAULT_TICK_ACCENTS_JSON) ?: DEFAULT_TICK_ACCENTS_JSON
        set(value) = prefs.edit().putString(KEY_TICK_ACCENTS_JSON, value).apply()

    // Stopwatch state persistence
    var stopwatchElapsedTime: Long
        get() = prefs.getLong(KEY_STOPWATCH_ELAPSED_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_STOPWATCH_ELAPSED_TIME, value).apply()

    var stopwatchIsRunning: Boolean
        get() = prefs.getBoolean(KEY_STOPWATCH_IS_RUNNING, false)
        set(value) = prefs.edit().putBoolean(KEY_STOPWATCH_IS_RUNNING, value).apply()

    var stopwatchSessionStartTime: Long
        get() = prefs.getLong(KEY_STOPWATCH_SESSION_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_STOPWATCH_SESSION_START_TIME, value).apply()

    var stopwatchAccumulatedTime: Long
        get() = prefs.getLong(KEY_STOPWATCH_ACCUMULATED_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_STOPWATCH_ACCUMULATED_TIME, value).apply()

    var stopwatchLastUpdateTime: Long
        get() = prefs.getLong(KEY_STOPWATCH_LAST_UPDATE_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_STOPWATCH_LAST_UPDATE_TIME, value).apply()

    // Laps are stored as JSON string: "[{lapNumber:1,totalTime:1000,lapDuration:1000},...]"
    var stopwatchLapsJson: String?
        get() = prefs.getString(KEY_STOPWATCH_LAPS_JSON, null)
        set(value) = prefs.edit().putString(KEY_STOPWATCH_LAPS_JSON, value).apply()

    fun getNameAccents(name: String): String? {
        val allJson = prefs.getString(KEY_NAME_ACCENTS_JSON, null) ?: return null
        return try {
            val all = org.json.JSONObject(allJson)
            if (all.has(name) && !all.isNull(name)) all.getString(name) else null
        } catch (_: Exception) { null }
    }

    fun saveNameAccents(name: String, accentsJson: String) {
        val allJson = prefs.getString(KEY_NAME_ACCENTS_JSON, null)
        val all = if (allJson != null) try { org.json.JSONObject(allJson) } catch (_: Exception) { org.json.JSONObject() } else org.json.JSONObject()
        all.put(name, accentsJson)
        prefs.edit().putString(KEY_NAME_ACCENTS_JSON, all.toString()).apply()
    }

    fun getNameToggles(name: String): NameToggles? {
        val allJson = prefs.getString(KEY_NAME_TOGGLES_JSON, null) ?: return null
        return try {
            val all = JSONObject(allJson)
            if (!all.has(name)) return null
            val obj = all.getJSONObject(name)
            NameToggles(
                showMilliseconds = obj.optBoolean("showMilliseconds", true),
                screenOnMode = obj.optString("screenOnMode", "WHILE_RUNNING"),
                lockOrientation = obj.optBoolean("lockOrientation", false),
                invertLapColors = obj.optBoolean("invertLapColors", false),
                dimBrightness = obj.optBoolean("dimBrightness", true),
                hideTimeWhileRunning = obj.optBoolean("hideTimeWhileRunning", false),
                showTimeAsSeconds = obj.optBoolean("showTimeAsSeconds", false),
                tickEnabled = obj.optBoolean("tickEnabled", false),
                tickAccentsJson = if (obj.has("tickAccentsJson") && !obj.isNull("tickAccentsJson")) obj.getString("tickAccentsJson") else null
            )
        } catch (_: Exception) { null }
    }

    fun saveNameToggles(name: String, toggles: NameToggles) {
        val allJson = prefs.getString(KEY_NAME_TOGGLES_JSON, null)
        val all = if (allJson != null) try { JSONObject(allJson) } catch (_: Exception) { JSONObject() } else JSONObject()
        val obj = JSONObject()
        obj.put("showMilliseconds", toggles.showMilliseconds)
        obj.put("screenOnMode", toggles.screenOnMode)
        obj.put("lockOrientation", toggles.lockOrientation)
        obj.put("invertLapColors", toggles.invertLapColors)
        obj.put("dimBrightness", toggles.dimBrightness)
        obj.put("hideTimeWhileRunning", toggles.hideTimeWhileRunning)
        obj.put("showTimeAsSeconds", toggles.showTimeAsSeconds)
        obj.put("tickEnabled", toggles.tickEnabled)
        if (toggles.tickAccentsJson != null) obj.put("tickAccentsJson", toggles.tickAccentsJson) else obj.put("tickAccentsJson", JSONObject.NULL)
        all.put(name, obj)
        prefs.edit().putString(KEY_NAME_TOGGLES_JSON, all.toString()).apply()
    }

    fun getAllNameToggles(): Map<String, NameToggles> {
        val allJson = prefs.getString(KEY_NAME_TOGGLES_JSON, null) ?: return emptyMap()
        return try {
            val all = JSONObject(allJson)
            val result = mutableMapOf<String, NameToggles>()
            val keys = all.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                try {
                    val obj = all.getJSONObject(key)
                    result[key] = NameToggles(
                        showMilliseconds = obj.optBoolean("showMilliseconds", true),
                        screenOnMode = obj.optString("screenOnMode", "WHILE_RUNNING"),
                        lockOrientation = obj.optBoolean("lockOrientation", false),
                        invertLapColors = obj.optBoolean("invertLapColors", false),
                        dimBrightness = obj.optBoolean("dimBrightness", true),
                        hideTimeWhileRunning = obj.optBoolean("hideTimeWhileRunning", false),
                        showTimeAsSeconds = obj.optBoolean("showTimeAsSeconds", false),
                        tickEnabled = obj.optBoolean("tickEnabled", false),
                        tickAccentsJson = if (obj.has("tickAccentsJson") && !obj.isNull("tickAccentsJson")) obj.getString("tickAccentsJson") else null
                    )
                } catch (_: Exception) {}
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun setAllNameToggles(map: Map<String, NameToggles>) {
        if (map.isEmpty()) {
            prefs.edit().remove(KEY_NAME_TOGGLES_JSON).apply()
            return
        }
        val all = JSONObject()
        map.forEach { (name, toggles) ->
            val obj = JSONObject()
            obj.put("showMilliseconds", toggles.showMilliseconds)
            obj.put("screenOnMode", toggles.screenOnMode)
            obj.put("lockOrientation", toggles.lockOrientation)
            obj.put("invertLapColors", toggles.invertLapColors)
            obj.put("dimBrightness", toggles.dimBrightness)
            obj.put("hideTimeWhileRunning", toggles.hideTimeWhileRunning)
            obj.put("showTimeAsSeconds", toggles.showTimeAsSeconds)
            obj.put("tickEnabled", toggles.tickEnabled)
            if (toggles.tickAccentsJson != null) obj.put("tickAccentsJson", toggles.tickAccentsJson) else obj.put("tickAccentsJson", JSONObject.NULL)
            all.put(name, obj)
        }
        prefs.edit().putString(KEY_NAME_TOGGLES_JSON, all.toString()).apply()
    }

    var nameSettingsMigrated: Boolean
        get() = prefs.getBoolean(KEY_NAME_SETTINGS_MIGRATED, false)
        set(value) = prefs.edit().putBoolean(KEY_NAME_SETTINGS_MIGRATED, value).apply()

    var activityPresetsSeedDone: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_PRESETS_SEED_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVITY_PRESETS_SEED_DONE, value).apply()

    var activityPresetsSeedV2Done: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_PRESETS_SEED_V2_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVITY_PRESETS_SEED_V2_DONE, value).apply()

    var activityPresetsSeedV3Done: Boolean
        get() = prefs.getBoolean(KEY_ACTIVITY_PRESETS_SEED_V3_DONE, false)
        set(value) = prefs.edit().putBoolean(KEY_ACTIVITY_PRESETS_SEED_V3_DONE, value).apply()

    fun getAllNameAccents(): Map<String, String> {
        val allJson = prefs.getString(KEY_NAME_ACCENTS_JSON, null) ?: return emptyMap()
        return try {
            val all = org.json.JSONObject(allJson)
            val result = mutableMapOf<String, String>()
            val keys = all.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (!all.isNull(key)) result[key] = all.getString(key)
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun clearStopwatchState() {
        prefs.edit()
            .remove(KEY_STOPWATCH_ELAPSED_TIME)
            .remove(KEY_STOPWATCH_IS_RUNNING)
            .remove(KEY_STOPWATCH_SESSION_START_TIME)
            .remove(KEY_STOPWATCH_ACCUMULATED_TIME)
            .remove(KEY_STOPWATCH_LAST_UPDATE_TIME)
            .remove(KEY_STOPWATCH_LAPS_JSON)
            .apply()
    }

    /**
     * Get current language code (en, ru, or zh)
     * Returns app language if set, otherwise system locale
     */
    fun getCurrentLanguage(): String {
        val savedLang = appLanguage
        if (savedLang != null) {
            return savedLang
        }

        // Fall back to system locale
        val systemLang = Locale.getDefault().language
        return when (systemLang) {
            "en", "ru", "zh" -> systemLang
            else -> "en" // Default to English for unsupported languages
        }
    }

    companion object {
        private const val PREFS_NAME = "laplog_preferences"
        private const val KEY_SHOW_MILLISECONDS = "show_milliseconds"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"  // Legacy
        private const val KEY_SCREEN_ON_MODE = "screen_on_mode"
        private const val KEY_USED_NAMES = "used_names"
        private const val KEY_LOCK_ORIENTATION = "lock_orientation"
        private const val KEY_CURRENT_NAME = "current_name"
        private const val KEY_CURRENT_NOTES = "current_notes"
        private const val KEY_SHOW_MILLISECONDS_IN_HISTORY = "show_milliseconds_in_history"
        private const val KEY_INVERT_LAP_COLORS = "invert_lap_colors"
        private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_BACKUP_RETENTION_DAYS = "backup_retention_days"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
        private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        private const val KEY_STOPWATCH_ELAPSED_TIME = "stopwatch_elapsed_time"
        private const val KEY_STOPWATCH_IS_RUNNING = "stopwatch_is_running"
        private const val KEY_STOPWATCH_SESSION_START_TIME = "stopwatch_session_start_time"
        private const val KEY_STOPWATCH_ACCUMULATED_TIME = "stopwatch_accumulated_time"
        private const val KEY_STOPWATCH_LAST_UPDATE_TIME = "stopwatch_last_update_time"
        private const val KEY_STOPWATCH_LAPS_JSON = "stopwatch_laps_json"
        private const val KEY_DIM_BRIGHTNESS = "dim_brightness"
        private const val KEY_DIM_TIMEOUT_SECONDS = "dim_timeout_seconds"
        private const val KEY_HIDE_TIME_WHILE_RUNNING = "hide_time_while_running"
        private const val KEY_SHOW_TIME_AS_SECONDS = "show_time_as_seconds"
        private const val KEY_SHOW_TIME_AS_SECONDS_HISTORY = "show_time_as_seconds_history"
        private const val KEY_SHOW_TIME_AS_SECONDS_CHARTS = "show_time_as_seconds_charts"
        private const val KEY_LOGGING_ENABLED = "logging_enabled"
        private const val KEY_TICK_ENABLED = "tick_enabled"
        private const val KEY_TICK_ACCENTS_JSON = "tick_accents_json"
        private const val KEY_NAME_TOGGLES_JSON = "name_toggles_json"
        private const val KEY_NAME_ACCENTS_JSON = "name_accents_json"
        private const val KEY_NAME_SETTINGS_MIGRATED = "name_settings_migrated_v1"
        private const val KEY_ACTIVITY_PRESETS_SEED_DONE = "activity_presets_seed_done_v1"
        private const val KEY_ACTIVITY_PRESETS_SEED_V2_DONE = "activity_presets_seed_done_v2"
        private const val KEY_ACTIVITY_PRESETS_SEED_V3_DONE = "activity_presets_seed_done_v3"
        private const val DEFAULT_TICK_ACCENTS_JSON =
            "[{\"i\":1,\"s\":\"TICK\",\"o\":0},{\"i\":8,\"s\":\"TOCK\",\"o\":7},{\"i\":8,\"s\":\"BELL\",\"o\":0},{\"i\":60,\"s\":\"CHIME\",\"o\":0},{\"i\":300,\"s\":\"GONG\",\"o\":0}]"
    }
}
