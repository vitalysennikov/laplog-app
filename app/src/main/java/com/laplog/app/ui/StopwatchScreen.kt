package com.laplog.app.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StopwatchScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    onScreenOnModeChanged: (ScreenOnMode, Boolean, Long, Boolean, Boolean) -> Unit, // (mode, isRunning, elapsedTime, dimBrightness, isTimedDim)
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
    val currentNotes by viewModel.currentNotes.collectAsState()
    val usedNames by viewModel.usedNames.collectAsState()
    val namesFromHistory by viewModel.namesFromHistory.collectAsState()
    val invertLapColors by viewModel.invertLapColors.collectAsState()
    val showMilliseconds by viewModel.showMilliseconds.collectAsState()
    val dimBrightness by viewModel.dimBrightness.collectAsState()
    val dimTimeoutSeconds by viewModel.dimTimeoutSeconds.collectAsState()
    val hideTimeWhileRunning by viewModel.hideTimeWhileRunning.collectAsState()
    val tickEnabled by viewModel.tickEnabled.collectAsState()
    val tickAccents by viewModel.tickAccents.collectAsState()
    val showTimeAsSeconds by viewModel.showTimeAsSeconds.collectAsState()

    var expandedNameDropdown by remember { mutableStateOf(false) }
    var showTickSettingsDialog by remember { mutableStateOf(false) }
    var showDimTimeoutDialog by remember { mutableStateOf(false) }
    var showNotes by remember { mutableStateOf(currentNotes.isNotEmpty()) }

    // Dim timer state
    var isTimedDim by remember { mutableStateOf(false) }
    val lastInteractionMs = remember { AtomicLong(System.currentTimeMillis()) }

    // Auto-show notes field when notes become non-empty (e.g. typed by user)
    LaunchedEffect(currentNotes) {
        if (currentNotes.isNotEmpty()) showNotes = true
    }

    // Update screen on state based on mode, running state, elapsed time, dim brightness, and timed dim
    LaunchedEffect(isRunning, screenOnMode, elapsedTime, dimBrightness, isTimedDim) {
        onScreenOnModeChanged(screenOnMode, isRunning, elapsedTime, dimBrightness, isTimedDim)
    }

    // Dim timer: after dimTimeoutSeconds of no interaction, dim screen to 10%
    val shouldRunDimTimer = !dimBrightness && when (screenOnMode) {
        ScreenOnMode.WHILE_RUNNING -> isRunning
        ScreenOnMode.ALWAYS -> true
        ScreenOnMode.OFF -> false
    }
    LaunchedEffect(shouldRunDimTimer, dimTimeoutSeconds) {
        if (!shouldRunDimTimer) {
            isTimedDim = false
            return@LaunchedEffect
        }
        while (true) {
            kotlinx.coroutines.delay(500L)
            val elapsed = System.currentTimeMillis() - lastInteractionMs.get()
            isTimedDim = elapsed >= dimTimeoutSeconds * 1000L
        }
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
            .padding(16.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastInteractionMs.set(System.currentTimeMillis())
                        if (isTimedDim) isTimedDim = false
                    }
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Name dropdown + notes toggle button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExposedDropdownMenuBox(
                expanded = expandedNameDropdown,
                onExpandedChange = {
                    if (!isRunning) expandedNameDropdown = it
                },
                modifier = Modifier.weight(1f)
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
                                    viewModel.selectNameFromHistory(name)
                                    expandedNameDropdown = false
                                }
                            )
                        }
                    }
                }
            }

            IconButton(onClick = { showNotes = !showNotes }) {
                Box {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = stringResource(R.string.notes_hint),
                        tint = if (currentNotes.isNotBlank()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (currentNotes.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
        }

        if (showNotes) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = currentNotes,
                onValueChange = { viewModel.updateCurrentNotes(it) },
                label = { Text(stringResource(R.string.notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings toggles in horizontal row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Keep screen on toggle (3 states: OFF / WHILE_RUNNING / ALWAYS)
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

            // Dim brightness toggle (long press to configure timeout)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onClick = { viewModel.toggleDimBrightness() },
                        onLongClick = { showDimTimeoutDialog = true }
                    ),
                contentAlignment = Alignment.Center
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

            // Show time as total seconds toggle
            IconToggleButton(
                checked = showTimeAsSeconds,
                onCheckedChange = { viewModel.toggleShowTimeAsSeconds() }
            ) {
                Icon(
                    imageVector = if (showTimeAsSeconds) Icons.Filled.Numbers else Icons.Outlined.Numbers,
                    contentDescription = stringResource(R.string.toggle_seconds_desc),
                    tint = if (showTimeAsSeconds) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Tick sounds toggle (long press opens settings)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .combinedClickable(
                        onClick = { viewModel.toggleTickEnabled() },
                        onLongClick = { showTickSettingsDialog = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (tickEnabled) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                    contentDescription = stringResource(R.string.tick_sounds),
                    tint = if (tickEnabled) MaterialTheme.colorScheme.primary
                          else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Current lap time (main display) + total elapsed time below
        val currentLapTime = elapsedTime - (laps.lastOrNull()?.totalTime ?: 0L)
        val shouldHideTime = hideTimeWhileRunning && isRunning
        var colonAlpha by remember { mutableStateOf(1f) }

        LaunchedEffect(shouldHideTime, elapsedTime) {
            colonAlpha = if (shouldHideTime) {
                if ((elapsedTime / 1000) % 2 == 0L) 1f else 0.3f
            } else 1f
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant

        if (shouldHideTime) {
            // Blinking placeholder for current lap
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "--", fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = dseg7Font, color = primaryColor)
                Text(text = ":", fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = dseg7Font, color = primaryColor.copy(alpha = colonAlpha))
                Text(text = "--", fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = dseg7Font, color = primaryColor)
            }
        } else {
            // Current lap time (large, affected by showTimeAsSeconds)
            Text(
                text = viewModel.formatTime(currentLapTime, includeMillis = !isRunning, roundIfNoMillis = false),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = dseg7Font,
                color = primaryColor
            )
        }

        // Total elapsed time — always mm:ss, smaller, secondary color
        if (!shouldHideTime || elapsedTime == 0L) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = viewModel.formatTimeMmSs(elapsedTime),
                fontSize = 22.sp,
                fontFamily = dseg7Font,
                color = secondaryColor
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

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
                            showTimeAsSeconds = showTimeAsSeconds,
                            formatTime = { time ->
                                viewModel.formatTime(time, showMilliseconds, showTimeAsSeconds = showTimeAsSeconds)
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

    if (showTickSettingsDialog) {
        TickSettingsDialog(
            tickAccents = tickAccents,
            onAccentsChange = { viewModel.updateTickAccents(it) },
            onPlaySound = { viewModel.playTestSound(it) },
            onDismiss = { showTickSettingsDialog = false }
        )
    }

    if (showDimTimeoutDialog) {
        DimTimeoutDialog(
            currentSeconds = dimTimeoutSeconds,
            onConfirm = { viewModel.setDimTimeoutSeconds(it) },
            onDismiss = { showDimTimeoutDialog = false }
        )
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
    showTimeAsSeconds: Boolean = false,
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

@Composable
private fun DimTimeoutDialog(
    currentSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentSeconds.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dim_timeout_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dim_timeout_value, sliderValue.toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 5f..60f,
                    steps = 10, // (60-5)/5 - 1 = 10 steps → 12 positions of 5s each
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("5 ${stringResource(R.string.dim_timeout_sec)}", style = MaterialTheme.typography.labelSmall)
                    Text("60 ${stringResource(R.string.dim_timeout_sec)}", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue.toInt()); onDismiss() }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
