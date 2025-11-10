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
import androidx.compose.ui.graphics.Color
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
    val currentComment by viewModel.currentComment.collectAsState()
    val usedComments by viewModel.usedComments.collectAsState()

    var showCommentSuggestions by remember { mutableStateOf(false) }

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
        Spacer(modifier = Modifier.height(24.dp))

        // Comment input with autocomplete
        Column {
            OutlinedTextField(
                value = currentComment,
                onValueChange = {
                    viewModel.updateCurrentComment(it)
                    showCommentSuggestions = it.isNotBlank()
                },
                label = { Text(stringResource(R.string.comment_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isRunning  // Disable during running
            )

            // Autocomplete suggestions
            if (showCommentSuggestions) {
                val filteredSuggestions = usedComments.filter {
                    it.contains(currentComment, ignoreCase = true) && it != currentComment
                }
                if (filteredSuggestions.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            filteredSuggestions.take(3).forEach { suggestion ->
                                TextButton(
                                    onClick = {
                                        viewModel.updateCurrentComment(suggestion)
                                        showCommentSuggestions = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(suggestion)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        // Control buttons - dynamic layout based on state
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                // Stopped (time=0): only [Start] button
                elapsedTime == 0L && !isRunning -> {
                    Button(
                        onClick = { viewModel.startOrPause() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.start),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Running: [Pause] [Filled Flag (Lap+Pause)] [Empty Flag (Lap)]
                isRunning -> {
                    FilledTonalButton(
                        onClick = { viewModel.startOrPause() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = stringResource(R.string.pause),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Button(
                        onClick = { viewModel.addLapAndPause() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = "Lap + Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.addLap() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Flag,
                            contentDescription = stringResource(R.string.lap),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Paused (time>0): [Start] [Stop]
                else -> {
                    Button(
                        onClick = { viewModel.startOrPause() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.start),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.reset() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
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
                            formatDifference = { diff ->
                                viewModel.formatDifference(diff, showMilliseconds)
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
    formatDifference: (Long) -> String,
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

        // Lap duration (center, larger) with difference indicator
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = formatTime(lap.lapDuration),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            // Difference from previous lap - next to duration
            // Always reserve space (72.dp) to align all laps consistently
            Box(
                modifier = Modifier.width(72.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (difference != null) {
                    Text(
                        text = formatDifference(difference),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (difference < 0) Color(0xFF4CAF50) // Green for faster laps
                               else MaterialTheme.colorScheme.error, // Red for slower laps
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
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
