package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.laplog.app.R
import com.laplog.app.model.SessionWithLaps
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditDialog(
    initialSession: SessionWithLaps?,
    namesFromHistory: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, notes: String, startTimeMs: Long, durationMs: Long, lapDurations: List<Long>) -> Unit
) {
    val isNew = initialSession == null

    var name by remember { mutableStateOf(initialSession?.session?.name ?: "") }
    var notes by remember { mutableStateOf(initialSession?.session?.notes ?: "") }
    var showNotes by remember { mutableStateOf((initialSession?.session?.notes ?: "").isNotBlank()) }
    var startTimeMs by remember { mutableStateOf(initialSession?.session?.startTime ?: System.currentTimeMillis()) }

    val lapTexts = remember {
        mutableStateListOf<String>().also { list ->
            initialSession?.laps?.forEach { lap -> list.add(formatDuration(lap.lapDuration)) }
        }
    }
    val lapDurations = remember {
        mutableStateListOf<Long>().also { list ->
            initialSession?.laps?.forEach { lap -> list.add(lap.lapDuration) }
        }
    }

    var durationText by remember {
        mutableStateOf(
            if (initialSession != null && initialSession.laps.isEmpty())
                formatDuration(initialSession.session.totalDuration)
            else ""
        )
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var expandedNameDropdown by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    val totalDuration = if (lapDurations.isNotEmpty()) lapDurations.sum()
                        else parseDuration(durationText)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {

                Text(
                    text = if (isNew) stringResource(R.string.create_session) else stringResource(R.string.edit_session),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Name field with autocomplete
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExposedDropdownMenuBox(
                            expanded = expandedNameDropdown,
                            onExpandedChange = { expandedNameDropdown = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text(stringResource(R.string.name_hint)) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                singleLine = true,
                                trailingIcon = {
                                    if (namesFromHistory.isNotEmpty())
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedNameDropdown)
                                },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                            )
                            if (namesFromHistory.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = expandedNameDropdown,
                                    onDismissRequest = { expandedNameDropdown = false }
                                ) {
                                    namesFromHistory.filter { it.contains(name, ignoreCase = true) || name.isBlank() }
                                        .forEach { n ->
                                            DropdownMenuItem(
                                                text = { Text(n) },
                                                onClick = { name = n; expandedNameDropdown = false }
                                            )
                                        }
                                }
                            }
                        }
                        // Notes toggle
                        IconButton(onClick = { showNotes = !showNotes }) {
                            Icon(
                                imageVector = if (showNotes) Icons.Default.EditNote else Icons.Outlined.EditNote,
                                contentDescription = stringResource(R.string.notes_hint),
                                tint = if (showNotes) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Notes field
                    if (showNotes) {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(stringResource(R.string.notes_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    // Date + Time
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(dateFormat.format(Date(startTimeMs)))
                        }
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(timeFormat.format(Date(startTimeMs)))
                        }
                    }

                    // Duration (only when no laps)
                    if (lapDurations.isEmpty()) {
                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it },
                            label = { Text(stringResource(R.string.duration_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("00:00") }
                        )
                    } else {
                        Text(
                            text = "${stringResource(R.string.total)}: ${formatDuration(totalDuration)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Laps header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.laps), style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = {
                            lapDurations.add(0L)
                            lapTexts.add("")
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.add_lap))
                        }
                    }

                    // Lap rows
                    lapTexts.forEachIndexed { index, text ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(28.dp)
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = { newText ->
                                    lapTexts[index] = newText
                                    lapDurations[index] = parseDuration(newText)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                placeholder = { Text("00:00") },
                                textStyle = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(onClick = {
                                lapDurations.removeAt(index)
                                lapTexts.removeAt(index)
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(name, notes, startTimeMs, totalDuration, lapDurations.toList())
                    }) {
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
    }

    // DatePickerDialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = startTimeMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedMs ->
                        val current = Calendar.getInstance().apply { timeInMillis = startTimeMs }
                        val selected = Calendar.getInstance().apply { timeInMillis = selectedMs }
                        current.set(Calendar.YEAR, selected.get(Calendar.YEAR))
                        current.set(Calendar.MONTH, selected.get(Calendar.MONTH))
                        current.set(Calendar.DAY_OF_MONTH, selected.get(Calendar.DAY_OF_MONTH))
                        startTimeMs = current.timeInMillis
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // TimePickerDialog
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startTimeMs }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val newCal = Calendar.getInstance().apply { timeInMillis = startTimeMs }
                    newCal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    newCal.set(Calendar.MINUTE, timePickerState.minute)
                    newCal.set(Calendar.SECOND, 0)
                    newCal.set(Calendar.MILLISECOND, 0)
                    startTimeMs = newCal.timeInMillis
                    showTimePicker = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimePicker(state = timePickerState) }
        )
    }
}

internal fun formatDuration(ms: Long): String {
    val hours = ms / 3600000
    val minutes = (ms % 3600000) / 60000
    val seconds = (ms % 60000) / 1000
    val centis = (ms % 1000) / 10
    return when {
        hours > 0 && centis > 0 -> String.format("%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        centis > 0 -> String.format("%02d:%02d.%02d", minutes, seconds, centis)
        else -> String.format("%02d:%02d", minutes, seconds)
    }
}

internal fun parseDuration(text: String): Long {
    val t = text.trim()
    if (t.isBlank()) return 0L
    return try {
        val hasDot = t.contains(".")
        val parts = t.replace(".", ":").split(":")
        when (parts.size) {
            1 -> parts[0].toLong() * 1000
            2 -> if (hasDot) {
                // SS.cc
                val sec = parts[0].toLong()
                val cs = parts[1].padEnd(2, '0').take(2).toLong()
                sec * 1000 + cs * 10
            } else {
                // MM:SS
                parts[0].toLong() * 60000 + parts[1].toLong() * 1000
            }
            3 -> if (hasDot) {
                // MM:SS.cc
                val min = parts[0].toLong()
                val sec = parts[1].toLong()
                val cs = parts[2].padEnd(2, '0').take(2).toLong()
                min * 60000 + sec * 1000 + cs * 10
            } else {
                // HH:MM:SS
                parts[0].toLong() * 3600000 + parts[1].toLong() * 60000 + parts[2].toLong() * 1000
            }
            4 -> {
                // HH:MM:SS.cc
                val h = parts[0].toLong()
                val min = parts[1].toLong()
                val sec = parts[2].toLong()
                val cs = parts[3].padEnd(2, '0').take(2).toLong()
                h * 3600000 + min * 60000 + sec * 1000 + cs * 10
            }
            else -> 0L
        }
    } catch (_: Exception) { 0L }
}
