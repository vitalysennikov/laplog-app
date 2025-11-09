package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.BuildConfig
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.viewmodel.StopwatchViewModel
import com.laplog.app.viewmodel.StopwatchViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    onKeepScreenOn: (Boolean) -> Unit
) {
    val viewModel: StopwatchViewModel = viewModel(
        factory = StopwatchViewModelFactory(preferencesManager, sessionDao)
    )

    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()

    val listState = rememberLazyListState()
    var showAboutDialog by remember { mutableStateOf(false) }

    // Update keep screen on state
    LaunchedEffect(isRunning, keepScreenOn) {
        onKeepScreenOn(isRunning && keepScreenOn)
    }

    // Auto-scroll to latest lap
    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About"
                        )
                    }
                    Column(
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.show_milliseconds),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Switch(
                                checked = showMilliseconds,
                                onCheckedChange = { viewModel.toggleMillisecondsDisplay() }
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.keep_screen_on),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Switch(
                                checked = keepScreenOn,
                                onCheckedChange = { viewModel.toggleKeepScreenOn() }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Time display
            Text(
                text = viewModel.formatTime(elapsedTime),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset button
                FilledTonalButton(
                    onClick = { viewModel.reset() },
                    modifier = Modifier.weight(1f),
                    enabled = elapsedTime > 0L || isRunning
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.reset),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Start/Pause button
                Button(
                    onClick = { viewModel.startOrPause() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning) {
                            stringResource(R.string.pause)
                        } else if (elapsedTime > 0L) {
                            stringResource(R.string.resume)
                        } else {
                            stringResource(R.string.start)
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Lap button
                FilledTonalButton(
                    onClick = { viewModel.addLap() },
                    modifier = Modifier.weight(1f),
                    enabled = isRunning
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = stringResource(R.string.lap),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Laps list
            if (laps.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth(),
                        reverseLayout = true
                    ) {
                        items(laps.reversed()) { lap ->
                            LapItem(
                                lap = lap,
                                formatTime = { time ->
                                    viewModel.formatTime(time, showMilliseconds)
                                }
                            )
                            if (lap != laps.first()) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("LapLog Free") },
            text = {
                Column {
                    Text("Version: ${BuildConfig.VERSION_NAME}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Build: ${BuildConfig.VERSION_CODE}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Simple stopwatch with lap marks",
                        style = MaterialTheme.typography.bodySmall
                    )
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

@Composable
fun LapItem(
    lap: com.laplog.app.model.LapTime,
    formatTime: (Long) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lap number
        Text(
            text = stringResource(R.string.lap_number, lap.lapNumber),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        // Total time and lap duration in one row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Total time
            Text(
                text = formatTime(lap.totalTime),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )

            // Lap duration
            Text(
                text = formatTime(lap.lapDuration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
