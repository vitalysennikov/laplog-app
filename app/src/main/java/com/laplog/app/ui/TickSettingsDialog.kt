package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
    onPlaySound: (TickSoundType) -> Unit,
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.tick_enabled))
                    Switch(checked = tickEnabled, onCheckedChange = onTickEnabledChange)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = stringResource(R.string.tick_accents),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                val sortedAccents = tickAccents.sortedWith(compareBy({ it.intervalSeconds }, { it.startOffsetSeconds }))
                val usedPairs = tickAccents.map { it.intervalSeconds to it.startOffsetSeconds }.toSet()

                sortedAccents.forEachIndexed { displayIndex, accent ->
                    val originalIndex = tickAccents.indexOf(accent)
                    TickAccentRow(
                        ordinal = displayIndex + 1,
                        accent = accent,
                        usedPairs = usedPairs,
                        onIntervalChange = { newInterval ->
                            if (newInterval > 0 && originalIndex >= 0) {
                                val updated = tickAccents.toMutableList()
                                updated[originalIndex] = updated[originalIndex].copy(intervalSeconds = newInterval)
                                onAccentsChange(updated)
                            }
                        },
                        onSoundChange = { newSound ->
                            if (originalIndex >= 0) {
                                val updated = tickAccents.toMutableList()
                                updated[originalIndex] = updated[originalIndex].copy(soundType = newSound)
                                onAccentsChange(updated)
                            }
                        },
                        onOffsetChange = { newOffset ->
                            if (originalIndex >= 0) {
                                val updated = tickAccents.toMutableList()
                                updated[originalIndex] = updated[originalIndex].copy(startOffsetSeconds = newOffset)
                                onAccentsChange(updated)
                            }
                        },
                        onPlay = { onPlaySound(accent.soundType) },
                        onDelete = {
                            onAccentsChange(tickAccents.filter { it != accent })
                        }
                    )
                    if (displayIndex < sortedAccents.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { onAccentsChange(DEFAULT_TICK_ACCENTS) }) {
                        Text(stringResource(R.string.tick_reset_defaults))
                    }
                    TextButton(onClick = { showAddDialog = true }) {
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
        AddTickAccentDialog(
            existingPairs = tickAccents.map { it.intervalSeconds to it.startOffsetSeconds }.toSet(),
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
    ordinal: Int,
    accent: TickAccent,
    usedPairs: Set<Pair<Int, Int>>,
    onIntervalChange: (Int) -> Unit,
    onSoundChange: (TickSoundType) -> Unit,
    onOffsetChange: (Int) -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    var showSoundMenu by remember { mutableStateOf(false) }
    var intervalText by remember(accent.intervalSeconds) { mutableStateOf(accent.intervalSeconds.toString()) }
    var offsetText by remember(accent.startOffsetSeconds) { mutableStateOf(accent.startOffsetSeconds.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$ordinal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp)
        )

        OutlinedTextField(
            value = intervalText,
            onValueChange = { v ->
                val filtered = v.filter { it.isDigit() }
                intervalText = filtered
                val n = filtered.toIntOrNull()
                if (n != null && n > 0 && (n == accent.intervalSeconds || (n to accent.startOffsetSeconds) !in usedPairs)) {
                    onIntervalChange(n)
                }
            },
            modifier = Modifier.width(62.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text(stringResource(R.string.tick_interval), style = MaterialTheme.typography.labelSmall) }
        )

        OutlinedTextField(
            value = offsetText,
            onValueChange = { v ->
                val filtered = v.filter { it.isDigit() }
                offsetText = filtered
                val n = filtered.toIntOrNull()
                if (n != null && n >= 0) onOffsetChange(n)
            },
            modifier = Modifier.width(62.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text(stringResource(R.string.tick_start_offset), style = MaterialTheme.typography.labelSmall) }
        )

        Spacer(modifier = Modifier.weight(1f))

        Box {
            TextButton(
                onClick = { showSoundMenu = true },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
            ) {
                Text(soundLabel(accent.soundType), style = MaterialTheme.typography.bodySmall)
            }
            DropdownMenu(expanded = showSoundMenu, onDismissRequest = { showSoundMenu = false }) {
                TickSoundType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(soundLabel(type)) },
                        onClick = { onSoundChange(type); showSoundMenu = false }
                    )
                }
            }
        }

        IconButton(onClick = onPlay, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTickAccentDialog(
    existingPairs: Set<Pair<Int, Int>>,
    onConfirm: (TickAccent) -> Unit,
    onDismiss: () -> Unit
) {
    var intervalText by remember { mutableStateOf("") }
    var offsetText by remember { mutableStateOf("0") }
    var selectedSound by remember { mutableStateOf(TickSoundType.TICK) }
    var soundExpanded by remember { mutableStateOf(false) }

    val interval = intervalText.toIntOrNull()
    val offset = offsetText.toIntOrNull() ?: 0
    val intervalError = interval != null && (interval <= 0 || (interval to offset) in existingPairs)
    val isValid = interval != null && interval > 0 && (interval to offset) !in existingPairs

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tick_add_accent)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.tick_interval)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = intervalError
                )

                OutlinedTextField(
                    value = offsetText,
                    onValueChange = { offsetText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.tick_start_offset)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = soundExpanded,
                    onExpandedChange = { soundExpanded = it }
                ) {
                    OutlinedTextField(
                        value = soundLabel(selectedSound),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.tick_sound)) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(soundExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = soundExpanded,
                        onDismissRequest = { soundExpanded = false }
                    ) {
                        TickSoundType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(soundLabel(type)) },
                                onClick = { selectedSound = type; soundExpanded = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (interval != null) onConfirm(TickAccent(interval, selectedSound, offset)) },
                enabled = isValid
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
