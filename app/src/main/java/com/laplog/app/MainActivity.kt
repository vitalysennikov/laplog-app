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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import kotlinx.coroutines.launch
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.ScreenOnMode
import com.laplog.app.data.database.AppDatabase
import com.laplog.app.model.SessionWithLaps
import com.laplog.app.ui.AboutDialog
import com.laplog.app.ui.BackupScreen
import com.laplog.app.ui.HistoryScreen
import com.laplog.app.ui.StopwatchScreen
import com.laplog.app.ui.WelcomeDialog
import com.laplog.app.ui.RestoreBackupDialog
import com.laplog.app.ui.theme.StopwatchTheme
import com.laplog.app.data.BackupManager
import com.laplog.app.viewmodel.BackupViewModel
import com.laplog.app.viewmodel.BackupViewModelFactory
import androidx.lifecycle.ViewModelProvider
import java.text.SimpleDateFormat
import java.util.*
import com.laplog.app.model.AppState
import com.laplog.app.service.StopwatchService

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

                // If this is first launch, check for backups
                if (preferencesManager.isFirstLaunch) {
                    checkForBackupsAndRestore(uri)
                }
            }
        }
    }

    private fun checkForBackupsAndRestore(uri: android.net.Uri) {
        // This will be called after folder selection, and dialogs will be shown in Compose
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

    @OptIn(ExperimentalFoundationApi::class)
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
                val pagerState = rememberPagerState(pageCount = { 3 })
                val coroutineScope = rememberCoroutineScope()
                var showAboutDialog by remember { mutableStateOf(false) }
                var showRestartDialog by remember { mutableStateOf(false) }
                var showWelcomeDialog by remember { mutableStateOf(preferencesManager.isFirstLaunch) }
                var showRestoreBackupDialog by remember { mutableStateOf(false) }
                var showNoBackupDialog by remember { mutableStateOf(false) }
                var latestBackup by remember { mutableStateOf<com.laplog.app.model.BackupFileInfo?>(null) }

                // Check for backups when folder is selected during first launch
                LaunchedEffect(backupViewModel.backupFolderUri.collectAsState().value) {
                    val folderUri = backupViewModel.backupFolderUri.value
                    if (preferencesManager.isFirstLaunch && folderUri != null && !showWelcomeDialog) {
                        val backupManager = BackupManager(applicationContext, preferencesManager, database.sessionDao())
                        val backups = backupManager.listBackups(android.net.Uri.parse(folderUri))

                        if (backups.isNotEmpty()) {
                            latestBackup = backups.first()
                            showRestoreBackupDialog = true
                        } else {
                            showNoBackupDialog = true
                        }
                    }
                }

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
                                    selected = pagerState.currentPage == 0,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                                    label = { Text(getString(R.string.history)) },
                                    selected = pagerState.currentPage == 1,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(1)
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Backup, contentDescription = null) },
                                    label = { Text(getString(R.string.backup)) },
                                    selected = pagerState.currentPage == 2,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) { page ->
                        when (page) {
                            0 -> StopwatchScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onScreenOnModeChanged = { mode, isRunning, elapsedTime ->
                                    // OFF: never keep screen on
                                    // WHILE_RUNNING: keep screen on at full brightness while running
                                    // ALWAYS: service manages screen with SCREEN_DIM_WAKE_LOCK (allows dimming)
                                    val shouldKeepOn = when (mode) {
                                        ScreenOnMode.OFF -> false
                                        ScreenOnMode.WHILE_RUNNING -> isRunning
                                        ScreenOnMode.ALWAYS -> false  // Service handles with dim wake lock
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
                                isVisible = pagerState.currentPage == 0
                            )
                            1 -> HistoryScreen(
                                preferencesManager = preferencesManager,
                                sessionDao = database.sessionDao(),
                                onExportCsv = { sessions -> exportToCsv(sessions) },
                                onExportJson = { sessions -> exportToJson(sessions) },
                                onLanguageChange = { languageCode ->
                                    preferencesManager.appLanguage = languageCode
                                    showRestartDialog = true
                                }
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
                    AboutDialog(
                        currentLanguage = preferencesManager.appLanguage,
                        onDismiss = { showAboutDialog = false },
                        onLanguageChange = { languageCode ->
                            preferencesManager.appLanguage = languageCode
                            showRestartDialog = true
                        }
                    )
                }

                // Restart dialog
                if (showRestartDialog) {
                    AlertDialog(
                        onDismissRequest = { showRestartDialog = false },
                        title = { Text(getString(R.string.restart_required)) },
                        text = { Text(getString(R.string.restart_app_message)) },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    // Restart the app
                                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                    finish()
                                }
                            ) {
                                Text(getString(R.string.ok))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRestartDialog = false }) {
                                Text(getString(R.string.cancel))
                            }
                        }
                    )
                }

                // Welcome dialog (first launch)
                if (showWelcomeDialog) {
                    WelcomeDialog(
                        onDismiss = {
                            showWelcomeDialog = false
                            preferencesManager.isFirstLaunch = false
                        },
                        onRestoreFromBackup = {
                            showWelcomeDialog = false
                            launchFolderPicker()
                        }
                    )
                }

                // Restore backup dialog
                if (showRestoreBackupDialog && latestBackup != null) {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val backupDate = dateFormat.format(Date(latestBackup!!.timestamp))

                    RestoreBackupDialog(
                        backupFound = true,
                        backupName = backupDate,
                        onConfirm = {
                            showRestoreBackupDialog = false
                            coroutineScope.launch {
                                val backupManager = BackupManager(applicationContext, preferencesManager, database.sessionDao())
                                val result = backupManager.restoreBackup(latestBackup!!.uri, BackupManager.RestoreMode.REPLACE)
                                if (result.isSuccess) {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.export_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // Restart app to apply restored settings
                                    showRestartDialog = true
                                } else {
                                    Toast.makeText(
                                        applicationContext,
                                        getString(R.string.export_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                preferencesManager.isFirstLaunch = false
                            }
                        },
                        onDismiss = {
                            showRestoreBackupDialog = false
                            preferencesManager.isFirstLaunch = false
                        }
                    )
                }

                // No backup found dialog
                if (showNoBackupDialog) {
                    RestoreBackupDialog(
                        backupFound = false,
                        backupName = "",
                        onConfirm = {},
                        onDismiss = {
                            showNoBackupDialog = false
                            preferencesManager.isFirstLaunch = false
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

    override fun onResume() {
        super.onResume()
        // App is in foreground
        AppState.setAppInForeground(true)

        // Notify service about app state change
        val intent = Intent(this, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_APP_FOREGROUND
        }
        startService(intent)
    }

    override fun onPause() {
        super.onPause()
        // App is going to background
        AppState.setAppInForeground(false)

        // Notify service about app state change
        val intent = Intent(this, StopwatchService::class.java).apply {
            action = StopwatchService.ACTION_APP_BACKGROUND
        }
        startService(intent)
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
