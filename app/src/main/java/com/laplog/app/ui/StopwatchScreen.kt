package com.laplog.app.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.ScreenOnMode
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.viewmodel.StopwatchViewModel
import com.laplog.app.viewmodel.StopwatchViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    onScreenOnModeChanged: (ScreenOnMode, Boolean, Long, Boolean) -> Unit, // (mode, isRunning, elapsedTime, dimBrightness)
    onLockOrientation: (Boolean) -> Unit,
    isVisible: Boolean = true
) {
    val context = LocalContext.current
    val translationManager = remember { TranslationManager(sessionDao) }
    val viewModel: StopwatchViewModel = viewModel(
        factory = StopwatchViewModelFactory(context, preferencesManager, sessionDao, translationManager)
    )

    // Refresh names from history when screen becomes visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            viewModel.refreshNamesFromHistory()
        }
    }

    // Digital clock style font
    val dseg7Font = FontFamily(
        Font(R.font.dseg7_classic_regular, FontWeight.Normal),
        Font(R.font.dseg7_classic_bold, FontWeight.Bold)
    )

    val elapsedTime by viewModel.elapsedTime.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val laps by viewModel.laps.collectAsState()
    val screenOnMode by viewModel.screenOnMode.collectAsState()
    val lockOrientation by viewModel.lockOrientation.collectAsState()
    val currentName by viewModel.currentName.collectAsState()
    val usedNames by viewModel.usedNames.collectAsState()
    val namesFromHistory by viewModel.namesFromHistory.collectAsState()
    val invertLapColors by viewModel.invertLapColors.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()
    val dimBrightness by viewModel.dimBrightness.collectAsState()
    val hideTimeWhileRunning by viewModel.hideTimeWhileRunning.collectAsState()

    var expandedNameDropdown by remember { mutableStateOf(false) }

    // Update screen on state based on mode, running state, elapsed time, and dim brightness
    LaunchedEffect(isRunning, screenOnMode, elapsedTime, dimBrightness) {
        onScreenOnModeChanged(screenOnMode, isRunning, elapsedTime, dimBrightness)
    }

    // In ALWAYS mode with dimBrightness OFF, keep service running even when stopped
    // This is needed to hold SCREEN_DIM_WAKE_LOCK for natural screen dimming
    LaunchedEffect(screenOnMode, dimBrightness, isRunning, elapsedTime) {
        val needsAlwaysOnService = screenOnMode == ScreenOnMode.ALWAYS &&
                                   !dimBrightness &&
                                   elapsedTime == 0L &&
                                   !isRunning

        if (needsAlwaysOnService) {
            // Start service to hold SCREEN_DIM_WAKE_LOCK
            val intent = android.content.Intent(context, com.laplog.app.service.StopwatchService::class.java).apply {
                action = com.laplog.app.service.StopwatchService.ACTION_ALWAYS_ON
                putExtra(com.laplog.app.service.StopwatchService.EXTRA_USE_SCREEN_DIM, true)
            }
            context.startForegroundService(intent)
        } else if (elapsedTime == 0L && !isRunning) {
            // Stop ALWAYS ON service if mode changed or dimBrightness enabled
            val intent = android.content.Intent(context, com.laplog.app.service.StopwatchService::class.java).apply {
                action = com.laplog.app.service.StopwatchService.ACTION_STOP
            }
            context.startService(intent)
        }
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

        // Name dropdown from history
        ExposedDropdownMenuBox(
            expanded = expandedNameDropdown,
            onExpandedChange = {
                if (!isRunning) expandedNameDropdown = it
            }
        ) {
            OutlinedTextField(
                value = currentName,
                onValueChange = { viewModel.updateCurrentName(it) },
                label = { Text(stringResource(R.string.name_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                singleLine = true,
                enabled = !isRunning,
                trailingIcon = {
                    if (namesFromHistory.isNotEmpty()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNameDropdown)
                    }
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )

            if (namesFromHistory.isNotEmpty()) {
                ExposedDropdownMenu(
                    expanded = expandedNameDropdown,
                    onDismissRequest = { expandedNameDropdown = false }
                ) {
                    namesFromHistory.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.updateCurrentName(name)
                                expandedNameDropdown = false
                            }
                        )
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
            // Keep screen on toggle (3 states)
            IconButton(
                onClick = { viewModel.cycleScreenOnMode() }
            ) {
                Icon(
                    imageVector = when (screenOnMode) {
                        ScreenOnMode.OFF -> Icons.Outlined.Smartphone
                        ScreenOnMode.WHILE_RUNNING -> Icons.Default.Smartphone
                        ScreenOnMode.ALWAYS -> Icons.Default.PhonelinkLock
                    },
                    contentDescription = stringResource(R.string.keep_screen_on),
                    tint = if (screenOnMode == ScreenOnMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant
                          else MaterialTheme.colorScheme.primary
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

            // Show milliseconds toggle
            IconToggleButton(
                checked = showMilliseconds,
                onCheckedChange = { viewModel.toggleMillisecondsDisplay() }
            ) {
                Icon(
                    imageVector = if (showMilliseconds) Icons.Filled.Timer else Icons.Outlined.Timer,
                    contentDescription = "Show milliseconds",
                    tint = if (showMilliseconds) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Invert lap colors toggle
            IconToggleButton(
                checked = invertLapColors,
                onCheckedChange = { viewModel.toggleInvertLapColors() }
            ) {
                Icon(
                    imageVector = if (invertLapColors) Icons.Filled.SwapVert else Icons.Outlined.SwapVert,
                    contentDescription = "Invert lap colors",
                    tint = if (invertLapColors) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dim brightness toggle
            IconToggleButton(
                checked = dimBrightness,
                onCheckedChange = { viewModel.toggleDimBrightness() }
            ) {
                Icon(
                    imageVector = if (dimBrightness) Icons.Filled.Brightness4 else Icons.Outlined.Brightness4,
                    contentDescription = "Dim brightness",
                    tint = if (dimBrightness) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Hide time while running toggle
            IconToggleButton(
                checked = hideTimeWhileRunning,
                onCheckedChange = { viewModel.toggleHideTimeWhileRunning() }
            ) {
                Icon(
                    imageVector = if (hideTimeWhileRunning) Icons.Filled.VisibilityOff else Icons.Outlined.VisibilityOff,
                    contentDescription = "Hide time while running",
                    tint = if (hideTimeWhileRunning) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Time display with digital clock font (show milliseconds only when paused)
        // If hideTimeWhileRunning is ON and stopwatch is running, show placeholder with blinking colon
        val shouldHideTime = hideTimeWhileRunning && isRunning
        var colonAlpha by remember { mutableStateOf(1f) }

        // Animate colon alpha every second when time is hidden
        LaunchedEffect(shouldHideTime, elapsedTime) {
            if (shouldHideTime) {
                // Toggle colon alpha based on current second
                colonAlpha = if ((elapsedTime / 1000) % 2 == 0L) 1f else 0.3f
            } else {
                colonAlpha = 1f
            }
        }

        if (shouldHideTime) {
            // Show placeholder with dimming colon
            val placeholder = buildAnnotatedString {
                append("••")
                pushStyle(SpanStyle(alpha = colonAlpha))
                append(":")
                pop()
                append("••")
                pushStyle(SpanStyle(alpha = colonAlpha))
                append(":")
                pop()
                append("••")
            }
            Text(
                text = placeholder,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = dseg7Font,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Text(
                text = viewModel.formatTime(elapsedTime, includeMillis = !isRunning, roundIfNoMillis = false),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = dseg7Font,
                color = MaterialTheme.colorScheme.primary
            )
        }

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
                                text = stringResource(R.string.avg),
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
                                text = stringResource(R.string.median),
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
                            allLaps = laps,
                            invertLapColors = invertLapColors
                        )
                        if (lap != laps.last()) {
                            Divider()
                        }
                    }
                }
            }
        }
    }

    // Permission dialogs
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()
    val showBatteryDialog by viewModel.showBatteryDialog.collectAsState()

    if (showPermissionDialog) {
        NotificationPermissionDialog(
            onDismiss = { viewModel.dismissPermissionDialog() },
            onPermissionResult = { granted ->
                viewModel.dismissPermissionDialog()
                if (granted) {
                    viewModel.showBatteryOptimizationDialog()
                    viewModel.onPermissionGranted()
                }
            }
        )
    }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            onDismiss = { viewModel.dismissBatteryDialog() }
        )
    }
}

@Composable
fun LapItem(
    lap: com.laplog.app.model.LapTime,
    formatTime: (Long) -> String,
    formatDifference: (Long) -> String,
    fontFamily: FontFamily,
    allLaps: List<com.laplog.app.model.LapTime>,
    invertLapColors: Boolean
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
                        color = if (invertLapColors) {
                            // Inverted: faster (negative) = red, slower (positive) = green
                            if (difference < 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                        } else {
                            // Normal: faster (negative) = green, slower (positive) = red
                            if (difference < 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        },
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
