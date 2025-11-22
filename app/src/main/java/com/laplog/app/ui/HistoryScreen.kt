package com.laplog.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.TranslationManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.data.database.entity.SessionEntity
import com.laplog.app.model.SessionWithLaps
import com.laplog.app.viewmodel.HistoryViewModel
import com.laplog.app.viewmodel.HistoryViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    translationManager: TranslationManager,
    onExportCsv: (List<SessionWithLaps>) -> Unit,
    onExportJson: (List<SessionWithLaps>) -> Unit,
    onLanguageChange: (String?) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(preferencesManager, sessionDao, translationManager)
    )

    // Digital clock style font
    val dseg7Font = FontFamily(
        Font(R.font.dseg7_classic_regular, FontWeight.Normal),
        Font(R.font.dseg7_classic_bold, FontWeight.Bold)
    )

    val sessions by viewModel.sessions.collectAsState()
    val usedNames by viewModel.usedNames.collectAsState()
    val namesFromHistory by viewModel.namesFromHistory.collectAsState()
    val expandAll by viewModel.expandAll.collectAsState()
    val showMillisecondsInHistory by viewModel.showMillisecondsInHistory.collectAsState()
    val invertLapColors by viewModel.invertLapColors.collectAsState()
    val filterName by viewModel.filterName.collectAsState()
    val showTableView by viewModel.showTableView.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.history))
                        if (filterName != null) {
                            Text(
                                text = "${stringResource(R.string.filter_by_name)}: $filterName",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                actions = {
                    // Table/Card view toggle
                    IconButton(onClick = { viewModel.toggleTableView() }) {
                        Icon(
                            imageVector = if (showTableView) Icons.Default.ViewList else Icons.Default.TableChart,
                            contentDescription = if (showTableView) "Card View" else "Table View"
                        )
                    }
                    // Expand/Collapse all toggle (only in card view)
                    if (!showTableView) {
                        IconButton(onClick = { viewModel.toggleExpandAll() }) {
                            Icon(
                                imageVector = if (expandAll) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                                contentDescription = if (expandAll) "Collapse All" else "Expand All"
                            )
                        }
                    }
                    // Filter by name
                    BadgedBox(
                        badge = {
                            if (filterName != null) {
                                Badge()
                            }
                        }
                    ) {
                        IconButton(onClick = { showFilterDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.filter_by_name)
                            )
                        }
                    }
                    // Milliseconds toggle for history
                    IconToggleButton(
                        checked = showMillisecondsInHistory,
                        onCheckedChange = { viewModel.toggleMillisecondsInHistory() }
                    ) {
                        Icon(
                            imageVector = if (showMillisecondsInHistory) Icons.Filled.AccessTime else Icons.Outlined.AccessTime,
                            contentDescription = "Show milliseconds in history"
                        )
                    }
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About")
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_history)) },
                            onClick = {
                                showMenu = false
                                showExportMenu = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_all_sessions)) },
                            onClick = {
                                showMenu = false
                                showDeleteAllDialog = true
                            }
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
        ) {
            // Filter indicator chip
            if (filterName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { showFilterDialog = true },
                        label = {
                            Text(
                                text = "${stringResource(R.string.filter_by_name)}: $filterName",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_filter),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.setFilterName(null) }
                            )
                        }
                    )
                }
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_sessions),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                items(sessions) { sessionWithLaps ->
                    if (showTableView) {
                        SessionTableItem(
                            sessionWithLaps = sessionWithLaps,
                            formatTime = { time -> viewModel.formatTime(time, showMillisecondsInHistory) },
                            fontFamily = dseg7Font
                        )
                    } else {
                        SessionItem(
                            sessionWithLaps = sessionWithLaps,
                            namesFromHistory = namesFromHistory,
                            onUpdateName = { name ->
                                viewModel.updateSessionName(sessionWithLaps.session.id, name)
                            },
                            onUpdateNotes = { notes ->
                                viewModel.updateSessionNotes(sessionWithLaps.session.id, notes)
                            },
                            onDelete = { viewModel.deleteSession(sessionWithLaps.session) },
                            onDeleteBefore = { viewModel.deleteSessionsBefore(sessionWithLaps.session.startTime) },
                            formatTime = { time -> viewModel.formatTime(time, showMillisecondsInHistory) },
                            formatDifference = { diff -> viewModel.formatDifference(diff, showMillisecondsInHistory) },
                            fontFamily = dseg7Font,
                            totalSessionCount = sessions.size,
                            sessionIndex = sessions.indexOf(sessionWithLaps),
                            expandAll = expandAll,
                            invertLapColors = invertLapColors
                        )
                    }
                    Divider()
                }
            }
        }

        // Delete all dialog
        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Text(stringResource(R.string.delete_all_sessions_message, sessions.size)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteAllSessions()
                            showDeleteAllDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // Export menu
        if (showExportMenu) {
            AlertDialog(
                onDismissRequest = { showExportMenu = false },
                title = { Text(stringResource(R.string.export_history)) },
                text = {
                    Column {
                        TextButton(
                            onClick = {
                                onExportCsv(sessions)
                                showExportMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.export_csv))
                        }
                        TextButton(
                            onClick = {
                                onExportJson(sessions)
                                showExportMenu = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.export_json))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showExportMenu = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        // About dialog
        if (showAboutDialog) {
            AboutDialog(
                currentLanguage = preferencesManager.appLanguage,
                onDismiss = { showAboutDialog = false },
                onLanguageChange = { languageCode ->
                    onLanguageChange(languageCode)
                    showAboutDialog = false
                }
            )
        }

        // Filter dialog
        if (showFilterDialog) {
            FilterDialog(
                namesFromHistory = namesFromHistory,
                currentFilter = filterName,
                onDismiss = { showFilterDialog = false },
                onFilterSelected = { name ->
                    viewModel.setFilterName(name)
                    showFilterDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionItem(
    sessionWithLaps: SessionWithLaps,
    namesFromHistory: List<String>,
    onUpdateName: (String) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteBefore: () -> Unit,
    formatTime: (Long) -> String,
    formatDifference: (Long) -> String,
    fontFamily: FontFamily,
    totalSessionCount: Int,
    sessionIndex: Int,
    expandAll: Boolean,
    invertLapColors: Boolean
) {
    var expanded by remember { mutableStateOf(expandAll) }

    // Sync with global expandAll state
    LaunchedEffect(expandAll) {
        expanded = expandAll
    }
    var showNameDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteBeforeDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val session = sessionWithLaps.session
    val laps = sessionWithLaps.laps

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(session.startTime))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Show name inline only when collapsed
                        if (!expanded && !session.name.isNullOrBlank()) {
                            Text(
                                text = "\u2014", // Em dash
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = session.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Show name on separate line when expanded
                    if (expanded && !session.name.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Show notes on separate line when expanded (always expanded, not collapsed)
                    if (!session.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${stringResource(R.string.duration)}:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(session.totalDuration),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (!expanded && laps.isNotEmpty()) {
                        // Calculate statistics for collapsed view
                        val lapDurations = laps.map { it.lapDuration }
                        val avgDuration = if (lapDurations.size >= 2) lapDurations.average().toLong() else null
                        val minDuration = lapDurations.minOrNull()
                        val maxDuration = lapDurations.maxOrNull()
                        val medianDuration = if (minDuration != null && maxDuration != null && lapDurations.size >= 2) {
                            (minDuration + maxDuration) / 2
                        } else null

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.session_laps, laps.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (avgDuration != null && medianDuration != null) {
                                Text(
                                    text = "\u2014",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${stringResource(R.string.avg)}:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTime(avgDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${stringResource(R.string.median)}:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatTime(medianDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (session.name.isNullOrBlank())
                                    stringResource(R.string.add_name)
                                else
                                    stringResource(R.string.edit_name)
                            )
                        },
                        onClick = {
                            showMenu = false
                            showNameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (session.notes.isNullOrBlank())
                                    stringResource(R.string.add_notes)
                                else
                                    stringResource(R.string.edit_notes)
                            )
                        },
                        onClick = {
                            showMenu = false
                            showNotesDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_session)) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        }
                    )
                    if (sessionIndex < totalSessionCount - 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete_sessions_before)) },
                            onClick = {
                                showMenu = false
                                showDeleteBeforeDialog = true
                            }
                        )
                    }
                }
            }

            // Expanded content with laps
            if (expanded && laps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))

                // Show statistics if there are at least 2 laps
                if (laps.size >= 2) {
                    val lapDurations = laps.map { it.lapDuration }
                    val avgDuration = lapDurations.average().toLong()
                    val minDuration = lapDurations.minOrNull() ?: 0L
                    val maxDuration = lapDurations.maxOrNull() ?: 0L
                    val medianDuration = (minDuration + maxDuration) / 2

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
                                    text = formatTime(avgDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = fontFamily,
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
                                    text = formatTime(medianDuration),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }

                laps.reversed().forEachIndexed { index, lap ->
                    // Calculate difference from previous lap (lap with number lapNumber-1)
                    val previousLap = laps.find { it.lapNumber == lap.lapNumber - 1 }
                    val difference = if (previousLap != null) {
                        lap.lapDuration - previousLap.lapDuration
                    } else null

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
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
                                style = MaterialTheme.typography.bodyMedium,
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
                                        style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(80.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }

    // Name dialog
    if (showNameDialog) {
        NameDialog(
            currentName = session.name ?: "",
            namesFromHistory = namesFromHistory,
            onDismiss = { showNameDialog = false },
            onSave = { name ->
                onUpdateName(name)
                showNameDialog = false
            }
        )
    }

    // Notes dialog
    if (showNotesDialog) {
        NotesDialog(
            currentNotes = session.notes ?: "",
            onDismiss = { showNotesDialog = false },
            onSave = { notes ->
                onUpdateNotes(notes)
                showNotesDialog = false
            }
        )
    }

    // Delete dialogs
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_session_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showDeleteBeforeDialog) {
        val sessionsToDelete = totalSessionCount - sessionIndex
        AlertDialog(
            onDismissRequest = { showDeleteBeforeDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_sessions_before_message, sessionsToDelete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteBefore()
                        showDeleteBeforeDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBeforeDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NameDialog(
    currentName: String,
    namesFromHistory: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var expandedNameDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (currentName.isBlank())
                    stringResource(R.string.add_name)
                else
                    stringResource(R.string.edit_name)
            )
        },
        text = {
            ExposedDropdownMenuBox(
                expanded = expandedNameDropdown,
                onExpandedChange = { expandedNameDropdown = it }
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.name_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    singleLine = true,
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
                        namesFromHistory.forEach { historyName ->
                            DropdownMenuItem(
                                text = { Text(historyName) },
                                onClick = {
                                    name = historyName
                                    expandedNameDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.save))
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
fun NotesDialog(
    currentNotes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (currentNotes.isBlank())
                    stringResource(R.string.add_notes)
                else
                    stringResource(R.string.edit_notes)
            )
        },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes) }) {
                Text(stringResource(R.string.save))
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
fun SessionTableItem(
    sessionWithLaps: SessionWithLaps,
    formatTime: (Long) -> String,
    fontFamily: FontFamily
) {
    val session = sessionWithLaps.session
    val laps = sessionWithLaps.laps
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    val dateStr = dateFormat.format(Date(session.startTime))

    // Calculate statistics
    val lapCount = laps.size
    val avgDuration = if (laps.size >= 2) {
        laps.map { it.lapDuration }.average().toLong()
    } else null
    val medianDuration = if (laps.size >= 2) {
        val sorted = laps.map { it.lapDuration }.sorted()
        sorted[sorted.size / 2]
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date and name
            Column(modifier = Modifier.weight(1.5f)) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (!session.name.isNullOrBlank()) {
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Duration
            Text(
                text = formatTime(session.totalDuration),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Lap count
            Text(
                text = lapCount.toString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Average
            Text(
                text = avgDuration?.let { formatTime(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            // Median
            Text(
                text = medianDuration?.let { formatTime(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = fontFamily,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun FilterDialog(
    namesFromHistory: List<String>,
    currentFilter: String?,
    onDismiss: () -> Unit,
    onFilterSelected: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.filter_by_name)) },
        text = {
            LazyColumn {
                // "All" option to clear filter
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFilterSelected(null) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == null,
                            onClick = { onFilterSelected(null) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.all_sessions),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // List of names
                items(namesFromHistory.size) { index ->
                    val name = namesFromHistory[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFilterSelected(name) }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentFilter == name,
                            onClick = { onFilterSelected(name) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge
                        )
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
}
