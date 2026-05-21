package com.laplog.app.util

import android.content.Context
import android.util.Log
import com.laplog.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Application logger with version tracking and file logging
 */
object AppLogger {
    private const val TAG = "LapLog"
    private const val LOG_FILE_NAME = "laplog.log"
    private const val ERROR_LOG_FILE_NAME = "laplog_errors.log"
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5 MB
    private const val MAX_ERROR_LOG_SIZE = 1 * 1024 * 1024 // 1 MB

    private lateinit var logFile: File
    private lateinit var errorLogFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    var fileLoggingEnabled: Boolean = false

    fun init(context: Context, loggingEnabled: Boolean = false) {
        fileLoggingEnabled = loggingEnabled
        logFile = File(context.filesDir, LOG_FILE_NAME)
        errorLogFile = File(context.filesDir, ERROR_LOG_FILE_NAME)

        // Rotate logs if too large
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            val backupFile = File(context.filesDir, "$LOG_FILE_NAME.old")
            logFile.renameTo(backupFile)
        }
        if (errorLogFile.exists() && errorLogFile.length() > MAX_ERROR_LOG_SIZE) {
            val backupFile = File(context.filesDir, "$ERROR_LOG_FILE_NAME.old")
            errorLogFile.renameTo(backupFile)
        }

        if (fileLoggingEnabled) {
            i("AppLogger", "Logger initialized, version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        }
    }

    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message, null)
    }

    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Level.ERROR, tag, message, throwable)
    }

    private enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        // Log to Android logcat
        when (level) {
            Level.DEBUG -> Log.d(TAG, "[$tag] $message", throwable)
            Level.INFO -> Log.i(TAG, "[$tag] $message", throwable)
            Level.WARN -> Log.w(TAG, "[$tag] $message", throwable)
            Level.ERROR -> Log.e(TAG, "[$tag] $message", throwable)
        }

        // Build log entry text
        val timestamp = dateFormat.format(Date())
        val versionInfo = "v${BuildConfig.VERSION_NAME}"
        val levelStr = level.name.padEnd(5)
        val tagStr = tag.padEnd(20)
        val logEntry = StringBuilder()
        logEntry.append("• $timestamp [$versionInfo] $levelStr $tagStr $message\n")
        if (throwable != null) {
            logEntry.append("  Exception: ${throwable.javaClass.simpleName}: ${throwable.message}\n")
            throwable.stackTrace.take(5).forEach { element ->
                logEntry.append("    at $element\n")
            }
        }
        val entryText = logEntry.toString()

        // Log to debug file (only when enabled)
        try {
            if (fileLoggingEnabled && ::logFile.isInitialized) {
                logFile.appendText(entryText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }

        // Log to error file (always, WARN and above)
        try {
            if ((level == Level.WARN || level == Level.ERROR) && ::errorLogFile.isInitialized) {
                errorLogFile.appendText(entryText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to error log file", e)
        }
    }

    /**
     * Get log file for export
     */
    suspend fun getLogFileContent(): String = withContext(Dispatchers.IO) {
        try {
            if (::logFile.isInitialized && logFile.exists()) {
                logFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log file", e)
            ""
        }
    }

    /**
     * Get error log file content
     */
    suspend fun getErrorLogFileContent(): String = withContext(Dispatchers.IO) {
        try {
            if (::errorLogFile.isInitialized && errorLogFile.exists()) {
                errorLogFile.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read error log file", e)
            ""
        }
    }

    /**
     * Get log file for sharing
     */
    fun getLogFile(): File? {
        return if (::logFile.isInitialized && logFile.exists()) {
            logFile
        } else {
            null
        }
    }

    /**
     * Get error log file for sharing
     */
    fun getErrorLogFile(): File? {
        return if (::errorLogFile.isInitialized && errorLogFile.exists()) {
            errorLogFile
        } else {
            null
        }
    }

    /**
     * Clear log file
     */
    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            try {
                if (::logFile.isInitialized && logFile.exists()) {
                    Log.i(TAG, "Clearing log file")
                    logFile.writeText("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log file", e)
            }
        }
    }

    /**
     * Clear error log file
     */
    suspend fun clearErrorLogs() {
        withContext(Dispatchers.IO) {
            try {
                if (::errorLogFile.isInitialized && errorLogFile.exists()) {
                    Log.i(TAG, "Clearing error log file")
                    errorLogFile.writeText("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear error log file", e)
            }
        }
    }
}
