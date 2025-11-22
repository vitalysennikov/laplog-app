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
    private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5 MB

    private lateinit var logFile: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)

        // Rotate log if too large
        if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
            val backupFile = File(context.filesDir, "$LOG_FILE_NAME.old")
            logFile.renameTo(backupFile)
        }

        i("AppLogger", "Logger initialized, version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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

        // Log to file
        try {
            if (::logFile.isInitialized) {
                val timestamp = dateFormat.format(Date())
                val versionInfo = "v${BuildConfig.VERSION_NAME}"
                val levelStr = level.name.padEnd(5)
                val tagStr = tag.padEnd(20)

                val logEntry = StringBuilder()
                logEntry.append("$timestamp [$versionInfo] $levelStr $tagStr $message\n")

                if (throwable != null) {
                    logEntry.append("  Exception: ${throwable.javaClass.simpleName}: ${throwable.message}\n")
                    throwable.stackTrace.take(5).forEach { element ->
                        logEntry.append("    at $element\n")
                    }
                }

                logFile.appendText(logEntry.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
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
     * Clear log file
     */
    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            try {
                if (::logFile.isInitialized && logFile.exists()) {
                    i("AppLogger", "Clearing log file")
                    logFile.writeText("")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear log file", e)
            }
        }
    }
}
