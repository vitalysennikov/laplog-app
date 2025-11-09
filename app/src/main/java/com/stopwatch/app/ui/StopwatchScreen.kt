package com.stopwatch.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stopwatch.app.R
import com.stopwatch.app.viewmodel.StopwatchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel = viewModel(),
    onKeepScreenOn: (Boolean) -> Unit
) {
    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()

    val listState = rememberLazyListState()

    // Update keep screen on state
    LaunchedEffect(isRunning) {
        onKeepScreenOn(isRunning)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.show_milliseconds),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Switch(
                            checked = showMilliseconds,
                            onCheckedChange = { viewModel.toggleMillisecondsDisplay() }
                        )
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
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.reset))
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
                        } else {
                            stringResource(R.string.start)
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isRunning) {
                            stringResource(R.string.pause)
                        } else if (elapsedTime > 0L) {
                            stringResource(R.string.resume)
                        } else {
                            stringResource(R.string.start)
                        }
                    )
                }

                // Lap button
                FilledTonalButton(
                    onClick = { viewModel.addLap() },
                    modifier = Modifier.weight(1f),
                    enabled = isRunning
                ) {
                    Text(stringResource(R.string.lap))
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
}

@Composable
fun LapItem(
    lap: com.stopwatch.app.model.LapTime,
    formatTime: (Long) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.lap_number, lap.lapNumber),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(2f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lap_time),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(lap.totalTime),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lap_duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(lap.lapDuration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
