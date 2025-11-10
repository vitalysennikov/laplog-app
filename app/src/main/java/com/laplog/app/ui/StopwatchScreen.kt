package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
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
    onLockOrientation: (Boolean) -> Unit,
    onShowAbout: () -> Unit
) {
    val viewModel: StopwatchViewModel = viewModel(
        factory = StopwatchViewModelFactory(preferencesManager, sessionDao)
    )

    // Digital clock style font
    val dseg7Font = FontFamily(
        Font(R.font.dseg7_classic_regular, FontWeight.Normal),
        Font(R.font.dseg7_classic_bold, FontWeight.Bold)
    )

    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val lockOrientation by viewModel.lockOrientation.collectAsState()

    // Update keep screen on state
    LaunchedEffect(isRunning, keepScreenOn) {
        onKeepScreenOn(isRunning && keepScreenOn)
    }

    // Update orientation lock
    LaunchedEffect(lockOrientation) {
        onLockOrientation(lockOrientation)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

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
                Icon(
                    imageVector = if (showMilliseconds) Icons.Filled.AccessTime else Icons.Outlined.AccessTime,
                    contentDescription = stringResource(R.string.show_milliseconds),
                    tint = if (showMilliseconds) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Keep screen on toggle
            IconToggleButton(
                checked = keepScreenOn,
                onCheckedChange = { viewModel.toggleKeepScreenOn() }
            ) {
                Icon(
                    imageVector = if (keepScreenOn) Icons.Filled.Smartphone else Icons.Outlined.Smartphone,
                    contentDescription = stringResource(R.string.keep_screen_on),
                    tint = if (keepScreenOn) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Lock orientation toggle
            IconToggleButton(
                checked = lockOrientation,
                onCheckedChange = { viewModel.toggleLockOrientation() }
            ) {
                Icon(
                    imageVector = if (lockOrientation) Icons.Filled.ScreenLockRotation else Icons.Outlined.ScreenRotation,
                    contentDescription = stringResource(R.string.lock_orientation),
                    tint = if (lockOrientation) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Time display with digital clock font
        Text(
            text = viewModel.formatTime(elapsedTime),
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = dseg7Font,
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
            // Calculate statistics
            val lapDurations = laps.map { it.lapDuration }
            val avgDuration = lapDurations.average().toLong()
            val minDuration = lapDurations.minOrNull() ?: 0L
            val maxDuration = lapDurations.maxOrNull() ?: 0L
            val medianDuration = (minDuration + maxDuration) / 2

            // Show statistics if there are at least 2 laps
            if (laps.size >= 2) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "AVG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = viewModel.formatTime(avgDuration, showMilliseconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = dseg7Font,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MEDIAN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = viewModel.formatTime(medianDuration, showMilliseconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = dseg7Font,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(laps.reversed()) { lap ->
                        LapItem(
                            lap = lap,
                            formatTime = { time ->
                                viewModel.formatTime(time, showMilliseconds)
                            },
                            fontFamily = dseg7Font,
                            allLaps = laps
                        )
                        if (lap != laps.last()) {
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
    formatTime: (Long) -> String,
    fontFamily: FontFamily,
    allLaps: List<com.laplog.app.model.LapTime>
) {
    // Calculate difference from previous lap
    val previousLap = allLaps.getOrNull(lap.lapNumber - 2)
    val difference = if (previousLap != null) {
        lap.lapDuration - previousLap.lapDuration
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lap number (left)
        Text(
            text = stringResource(R.string.lap_number, lap.lapNumber),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(60.dp)
        )

        // Lap duration (center, larger)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = formatTime(lap.lapDuration),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Difference from previous lap
            if (difference != null) {
                val diffSeconds = difference / 1000.0
                val sign = if (diffSeconds > 0) "+" else ""
                Text(
                    text = "$sign%.1f s".format(diffSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (diffSeconds < 0) MaterialTheme.colorScheme.tertiary
                           else MaterialTheme.colorScheme.error
                )
            }
        }

        // Total time (right)
        Text(
            text = formatTime(lap.totalTime),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
