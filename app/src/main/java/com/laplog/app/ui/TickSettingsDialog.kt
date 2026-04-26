package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.laplog.app.R
import com.laplog.app.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TickSettingsDialog(
    tickEnabled: Boolean,
    tickAccents: List<TickAccent>,
    onTickEnabledChange: (Boolean) -> Unit,
    onAccentsChange: (List<TickAccent>) -> Unit,
    onDismiss: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_settings)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.tick_enabled))
                    Switch(
                        checked = tickEnabled,
                        onCheckedChange = onTickEnabledChange
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = stringResource(R.string.tick_accents),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                tickAccents.sortedBy { it.intervalSeconds }.forEach { accent ->
                    TickAccentRow(
                        accent = accent,
                        onSoundChange = { newSound ->
                            onAccentsChange(tickAccents.map {
                                if (it.intervalSeconds == accent.intervalSeconds) it.copy(soundType = newSound) else it
                            })
                        },
                        onDelete = {
                            onAccentsChange(tickAccents.filter { it.intervalSeconds != accent.intervalSeconds })
                        }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onAccentsChange(DEFAULT_TICK_ACCENTS) }) {
                        Text(stringResource(R.string.tick_reset_defaults))
                    }
                    val usedIntervals = tickAccents.map { it.intervalSeconds }.toSet()
                    val available = TICK_PRESET_INTERVALS.filter { it !in usedIntervals }
                    TextButton(
                        onClick = { showAddDialog = true },
                        enabled = available.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.tick_add_accent))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )

    if (showAddDialog) {
        val usedIntervals = tickAccents.map { it.intervalSeconds }.toSet()
        AddTickAccentDialog(
            existingIntervals = usedIntervals,
            onConfirm = { accent ->
                onAccentsChange(tickAccents + accent)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
private fun TickAccentRow(
    accent: TickAccent,
    onSoundChange: (TickSoundType) -> Unit,
    onDelete: () -> Unit
) {
    var showSoundMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = intervalLabel(accent.intervalSeconds),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        Box {
            TextButton(onClick = { showSoundMenu = true }) {
                Text(
                    text = soundLabel(accent.soundType),
                    style = MaterialTheme.typography.bodyMedium
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showSoundMenu,
                onDismissRequest = { showSoundMenu = false }
            ) {
                TickSoundType.values().forEach { type ->
                    DropdownMenuItem(
                        text = { Text(soundLabel(type)) },
                        onClick = {
                            onSoundChange(type)
                            showSoundMenu = false
                        }
                    )
                }
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTickAccentDialog(
    existingIntervals: Set<Int>,
    onConfirm: (TickAccent) -> Unit,
    onDismiss: () -> Unit
) {
    val available = TICK_PRESET_INTERVALS.filter { it !in existingIntervals }
    var selectedInterval by remember { mutableStateOf(available.firstOrNull() ?: 1) }
    var selectedSound by remember { mutableStateOf(TickSoundType.TICK) }
    var intervalExpanded by remember { mutableStateOf(false) }
    var soundExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_add_accent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = intervalExpanded,
                    onExpandedChange = { intervalExpanded = it }
                ) {
                    OutlinedTextField(
                        value = intervalLabel(selectedInterval),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tick_interval)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(intervalExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = intervalExpanded,
                        onDismissRequest = { intervalExpanded = false }
                    ) {
                        available.forEach { interval ->
                            DropdownMenuItem(
                                text = { Text(intervalLabel(interval)) },
                                onClick = {
                                    selectedInterval = interval
                                    intervalExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = soundExpanded,
                    onExpandedChange = { soundExpanded = it }
                ) {
                    OutlinedTextField(
                        value = soundLabel(selectedSound),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tick_sound)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(soundExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = soundExpanded,
                        onDismissRequest = { soundExpanded = false }
                    ) {
                        TickSoundType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(soundLabel(type)) },
                                onClick = {
                                    selectedSound = type
                                    soundExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(TickAccent(selectedInterval, selectedSound)) },
                enabled = available.isNotEmpty()
            ) {
                Text(stringResource(R.string.tick_add_accent))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun soundLabel(type: TickSoundType): String = stringResource(
    when (type) {
        TickSoundType.TICK -> R.string.tick_sound_tick
        TickSoundType.TOCK -> R.string.tick_sound_tock
        TickSoundType.BELL -> R.string.tick_sound_bell
        TickSoundType.DEEP -> R.string.tick_sound_deep
        TickSoundType.SILENT -> R.string.tick_sound_silent
    }
)

@Composable
private fun intervalLabel(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return when {
        seconds < 60 -> stringResource(R.string.tick_every_n_sec, seconds)
        secs == 0 -> stringResource(R.string.tick_every_n_min, mins)
        else -> stringResource(R.string.tick_every_n_min_sec, mins, secs)
    }
}
