package com.laplog.app

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.ScreenOnMode
import com.laplog.app.data.database.AppDatabase
import com.laplog.app.model.SessionWithLaps
import com.laplog.app.ui.BackupScreen
import com.laplog.app.ui.HistoryScreen
import com.laplog.app.ui.StopwatchScreen
import com.laplog.app.ui.theme.StopwatchTheme
import com.laplog.app.viewmodel.BackupViewModel
import com.laplog.app.viewmodel.BackupViewModelFactory
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var database: AppDatabase
    private var pendingExportData: String? = null
    private var pendingFileName: String? = null
    private lateinit var backupViewModel: BackupViewModel

    private val selectBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                // Take persistable URI permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                backupViewModel.setBackupFolder(uri)
            }
        }
    }

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

        // Apply language setting
        applyLanguage(preferencesManager.appLanguage)

        // Initialize BackupViewModel
        backupViewModel = ViewModelProvider(
            this,
            BackupViewModelFactory(applicationContext, preferencesManager, database.sessionDao())
        )[BackupViewModel::class.java]

        setContent {
            StopwatchTheme {
                var selectedTab by remember { mutableStateOf(0) }
                var showAboutDialog by remember { mutableStateOf(false) }

                Scaffold(
                    bottomBar = {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = getString(R.string.app_name),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { showAboutDialog = true },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = getString(R.string.about),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
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
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                    label = { Text(getString(R.string.backup)) },
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 }
                                )
                            }
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
                                onScreenOnModeChanged = { mode, isRunning ->
                                    val shouldKeepOn = when (mode) {
                                        ScreenOnMode.OFF -> false
                                        ScreenOnMode.WHILE_RUNNING -> isRunning
                                        ScreenOnMode.ALWAYS -> true
                                    }
                                    if (shouldKeepOn) {
                                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    } else {
                                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    }
                                },
                                onLockOrientation = { lock ->
                                    requestedOrientation = if (lock) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    }
                                },
                                onShowAbout = { showAboutDialog = true },
                                isVisible = selectedTab == 0
                            )
                            1 -> HistoryScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onExportCsv = { sessions -> exportToCsv(sessions) },
                                onExportJson = { sessions -> exportToJson(sessions) }
                            )
                            2 -> BackupScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onSelectFolder = { launchFolderPicker() }
                            )
                        }
                    }
                }

                // About dialog
                if (showAboutDialog) {
                    AlertDialog(
                        onDismissRequest = { showAboutDialog = false },
                        title = { Text(getString(R.string.app_name)) },
                        text = {
                            Column {
                                Text("Version: ${getString(R.string.version_name)}")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("© 2025 Vitaly Sennikov")
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = getString(R.string.about_description),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Toggle buttons:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.AccessTime,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Show/hide milliseconds",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Smartphone,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Keep screen on: OFF / While Running / Always",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ScreenLockRotation,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Lock screen orientation",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.SwapVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Invert lap colors (faster/slower)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showAboutDialog = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    }

    private fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // Add flags to show cloud storage providers
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

            // Add category to ensure all document providers are shown
            addCategory(Intent.CATEGORY_DEFAULT)

            // Try to start with external storage by default
            // This makes cloud providers more visible
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // EXTRA_INITIAL_URI helps show cloud services
                putExtra("android.provider.extra.INITIAL_URI",
                    android.provider.MediaStore.Files.getContentUri("external"))
            }
        }
        selectBackupFolderLauncher.launch(intent)
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

    private fun applyLanguage(languageCode: String?) {
        val locale = when (languageCode) {
            "en" -> Locale.ENGLISH
            "ru" -> Locale("ru")
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.getDefault() // System default
        }

        Locale.setDefault(locale)
        val config = Configuration(resources.configuration)
        config.setLocale(locale)
        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }
}
