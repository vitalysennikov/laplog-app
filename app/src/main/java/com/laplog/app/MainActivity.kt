package com.laplog.app

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.AppDatabase
import com.laplog.app.model.SessionWithLaps
import com.laplog.app.ui.HistoryScreen
import com.laplog.app.ui.StopwatchScreen
import com.laplog.app.ui.theme.StopwatchTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: AppDatabase
    private var pendingExportData: String? = null
    private var pendingFileName: String? = null

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/*")
    ) { uri ->
        uri?.let {
            val data = pendingExportData
            val fileName = pendingFileName
            if (data != null) {
                try {
                    contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(data.toByteArray())
                    }
                    Toast.makeText(this, getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                }
            }
            pendingExportData = null
            pendingFileName = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(applicationContext)
        database = AppDatabase.getDatabase(applicationContext)

        setContent {
            StopwatchTheme {
                var selectedTab by remember { mutableStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                label = { Text(getString(R.string.stopwatch)) },
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.History, contentDescription = null) },
                                label = { Text(getString(R.string.history)) },
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 }
                            )
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (selectedTab) {
                            0 -> StopwatchScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onKeepScreenOn = { keepOn ->
                                    if (keepOn) {
                                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    } else {
                                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    }
                                }
                            )
                            1 -> HistoryScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onExportCsv = { sessions -> exportToCsv(sessions) },
                                onExportJson = { sessions -> exportToJson(sessions) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exportToCsv(sessions: List<SessionWithLaps>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val csv = StringBuilder()

        // Header
        csv.append("Date,Start Time,End Time,Duration (ms),Duration,Comment,Lap Number,Lap Total Time (ms),Lap Duration (ms)\n")

        sessions.forEach { sessionWithLaps ->
            val session = sessionWithLaps.session
            val startDate = dateFormat.format(Date(session.startTime))
            val endDate = dateFormat.format(Date(session.endTime))
            val duration = formatDuration(session.totalDuration)
            val comment = session.comment?.replace(",", ";") ?: ""

            if (sessionWithLaps.laps.isEmpty()) {
                csv.append("$startDate,$startDate,$endDate,${session.totalDuration},$duration,$comment,,,\n")
            } else {
                sessionWithLaps.laps.forEach { lap ->
                    csv.append("$startDate,$startDate,$endDate,${session.totalDuration},$duration,$comment,${lap.lapNumber},${lap.totalTime},${lap.lapDuration}\n")
                }
            }
        }

        val fileName = "laplog_history_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        pendingExportData = csv.toString()
        pendingFileName = fileName
        createDocumentLauncher.launch(fileName)
    }

    private fun exportToJson(sessions: List<SessionWithLaps>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        val json = StringBuilder()

        json.append("{\n  \"sessions\": [\n")

        sessions.forEachIndexed { index, sessionWithLaps ->
            val session = sessionWithLaps.session
            json.append("    {\n")
            json.append("      \"id\": ${session.id},\n")
            json.append("      \"startTime\": \"${dateFormat.format(Date(session.startTime))}\",\n")
            json.append("      \"endTime\": \"${dateFormat.format(Date(session.endTime))}\",\n")
            json.append("      \"totalDuration\": ${session.totalDuration},\n")
            json.append("      \"comment\": ${if (session.comment != null) "\"${session.comment.replace("\"", "\\\"")}\"" else "null"},\n")
            json.append("      \"laps\": [\n")

            sessionWithLaps.laps.forEachIndexed { lapIndex, lap ->
                json.append("        {\n")
                json.append("          \"lapNumber\": ${lap.lapNumber},\n")
                json.append("          \"totalTime\": ${lap.totalTime},\n")
                json.append("          \"lapDuration\": ${lap.lapDuration}\n")
                json.append("        }")
                if (lapIndex < sessionWithLaps.laps.size - 1) json.append(",")
                json.append("\n")
            }

            json.append("      ]\n")
            json.append("    }")
            if (index < sessions.size - 1) json.append(",")
            json.append("\n")
        }

        json.append("  ]\n}")

        val fileName = "laplog_history_${SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())}.json"
        pendingExportData = json.toString()
        pendingFileName = fileName
        createDocumentLauncher.launch(fileName)
    }

    private fun formatDuration(millis: Long): String {
        val hours = (millis / 3600000).toInt()
        val minutes = ((millis % 3600000) / 60000).toInt()
        val seconds = ((millis % 60000) / 1000).toInt()
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
