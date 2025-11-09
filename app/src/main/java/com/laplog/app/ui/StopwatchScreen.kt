package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.viewmodel.StopwatchViewModel
import com.laplog.app.viewmodel.StopwatchViewModelFactory

@Composable
fun StopwatchScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    onKeepScreenOn: (Boolean) -> Unit,
    onLockOrientation: (Boolean) -> Unit
) {
    val viewModel: StopwatchViewModel = viewModel(
        factory = StopwatchViewModelFactory(preferencesManager, sessionDao)
    )

    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val lockOrientation by viewModel.lockOrientation.collectAsState()

    val listState = rememberLazyListState()

    // Update keep screen on state
    LaunchedEffect(isRunning, keepScreenOn) {
        onKeepScreenOn(isRunning && keepScreenOn)
    }

    // Update orientation lock
    LaunchedEffect(lockOrientation) {
        onLockOrientation(lockOrientation)
    }

    // Auto-scroll to latest lap
    LaunchedEffect(laps.size) {
        if (laps.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        // Time display with monospace font
        Text(
            text = viewModel.formatTime(elapsedTime),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
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

        // Settings toggles in horizontal row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Show milliseconds toggle
            IconToggleButton(
                checked = showMilliseconds,
                onCheckedChange = { viewModel.toggleMillisecondsDisplay() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (showMilliseconds) Icons.Filled.AccessTime else Icons.Outlined.AccessTime,
                        contentDescription = stringResource(R.string.show_milliseconds),
                        tint = if (showMilliseconds) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showMilliseconds) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Keep screen on toggle
            IconToggleButton(
                checked = keepScreenOn,
                onCheckedChange = { viewModel.toggleKeepScreenOn() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (keepScreenOn) Icons.Filled.Smartphone else Icons.Outlined.Smartphone,
                        contentDescription = stringResource(R.string.keep_screen_on),
                        tint = if (keepScreenOn) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Screen",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (keepScreenOn) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Lock orientation toggle
            IconToggleButton(
                checked = lockOrientation,
                onCheckedChange = { viewModel.toggleLockOrientation() }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (lockOrientation) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                        contentDescription = stringResource(R.string.lock_orientation),
                        tint = if (lockOrientation) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lock",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (lockOrientation) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        Text(
            text = stringResource(R.string.lap_number, lap.lapNumber),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(lap.totalTime),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = formatTime(lap.lapDuration),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
