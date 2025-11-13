package com.laplog.app.data

import android.content.Context
import android.content.SharedPreferences

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

    var usedComments: Set<String>
        get() = prefs.getStringSet(KEY_USED_COMMENTS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_USED_COMMENTS, value).apply()

    var lockOrientation: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ORIENTATION, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ORIENTATION, value).apply()

    var currentComment: String
        get() = prefs.getString(KEY_CURRENT_COMMENT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CURRENT_COMMENT, value).apply()

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

    companion object {
        private const val PREFS_NAME = "laplog_preferences"
        private const val KEY_SHOW_MILLISECONDS = "show_milliseconds"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"  // Legacy
        private const val KEY_SCREEN_ON_MODE = "screen_on_mode"
        private const val KEY_USED_COMMENTS = "used_comments"
        private const val KEY_LOCK_ORIENTATION = "lock_orientation"
        private const val KEY_CURRENT_COMMENT = "current_comment"
        private const val KEY_SHOW_MILLISECONDS_IN_HISTORY = "show_milliseconds_in_history"
        private const val KEY_INVERT_LAP_COLORS = "invert_lap_colors"
        private const val KEY_BACKUP_FOLDER_URI = "backup_folder_uri"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_BACKUP_RETENTION_DAYS = "backup_retention_days"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_PERMISSIONS_REQUESTED = "permissions_requested"
    }
}
