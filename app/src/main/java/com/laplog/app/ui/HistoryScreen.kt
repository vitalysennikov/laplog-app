package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
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
    onExportCsv: (List<SessionWithLaps>) -> Unit,
    onExportJson: (List<SessionWithLaps>) -> Unit
) {
    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(preferencesManager, sessionDao)
    )

    val sessions by viewModel.sessions.collectAsState()
    val usedComments by viewModel.usedComments.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history)) },
                actions = {
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
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(sessions) { sessionWithLaps ->
                    SessionItem(
                        sessionWithLaps = sessionWithLaps,
                        usedComments = usedComments.toList(),
                        onUpdateComment = { comment ->
                            viewModel.updateSessionComment(sessionWithLaps.session.id, comment)
                        },
                        onDelete = { viewModel.deleteSession(sessionWithLaps.session) },
                        onDeleteBefore = { viewModel.deleteSessionsBefore(sessionWithLaps.session.startTime) },
                        formatTime = viewModel::formatTime,
                        totalSessionCount = sessions.size,
                        sessionIndex = sessions.indexOf(sessionWithLaps)
                    )
                    HorizontalDivider()
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
    }
}

@Composable
fun SessionItem(
    sessionWithLaps: SessionWithLaps,
    usedComments: List<String>,
    onUpdateComment: (String) -> Unit,
    onDelete: () -> Unit,
    onDeleteBefore: () -> Unit,
    formatTime: (Long, Boolean) -> String,
    totalSessionCount: Int,
    sessionIndex: Int
) {
    var expanded by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDeleteBeforeDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val session = sessionWithLaps.session
    val laps = sessionWithLaps.laps

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
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
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.session_duration, formatTime(session.totalDuration)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (laps.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.session_laps, laps.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!session.comment.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = session.comment,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                </Column>

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
                                if (session.comment.isNullOrBlank())
                                    stringResource(R.string.add_comment)
                                else
                                    stringResource(R.string.edit_comment)
                            )
                        },
                        onClick = {
                            showMenu = false
                            showCommentDialog = true
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
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                laps.forEach { lap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.lap_number, lap.lapNumber),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = formatTime(lap.totalTime),
                                style = MaterialTheme.typography.bodyMedium
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
        }
    }

    // Comment dialog
    if (showCommentDialog) {
        CommentDialog(
            currentComment = session.comment ?: "",
            usedComments = usedComments,
            onDismiss = { showCommentDialog = false },
            onSave = { comment ->
                onUpdateComment(comment)
                showCommentDialog = false
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
fun CommentDialog(
    currentComment: String,
    usedComments: List<String>,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var comment by remember { mutableStateOf(currentComment) }
    var showSuggestions by remember { mutableStateOf(false) }

    val filteredSuggestions = remember(comment, usedComments) {
        if (comment.isBlank()) {
            usedComments
        } else {
            usedComments.filter { it.contains(comment, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (currentComment.isBlank())
                    stringResource(R.string.add_comment)
                else
                    stringResource(R.string.edit_comment)
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = comment,
                    onValueChange = {
                        comment = it
                        showSuggestions = it.isNotBlank() && filteredSuggestions.isNotEmpty()
                    },
                    label = { Text(stringResource(R.string.comment_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (showSuggestions && filteredSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Suggestions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    filteredSuggestions.take(3).forEach { suggestion ->
                        TextButton(
                            onClick = {
                                comment = suggestion
                                showSuggestions = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(comment) }) {
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
