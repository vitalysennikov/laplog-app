package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.BackupManager
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.model.BackupFileInfo
import com.laplog.app.viewmodel.BackupViewModel
import com.laplog.app.viewmodel.BackupViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    preferencesManager: PreferencesManager,
    sessionDao: SessionDao,
    onSelectFolder: () -> Unit,
    onSaveBackupManually: (String, String) -> Unit
) {
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModelFactory(
            context = androidx.compose.ui.platform.LocalContext.current,
            preferencesManager = preferencesManager,
            sessionDao = sessionDao
        )
    )

    val backupFolderUri by viewModel.backupFolderUri.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val backupRetentionDays by viewModel.backupRetentionDays.collectAsState()
    val lastBackupTime by viewModel.lastBackupTime.collectAsState()
    val backups by viewModel.backups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var showRetentionDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showDeleteDialog by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showDeleteBeforeDialog by remember { mutableStateOf<BackupFileInfo?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup)) },
                actions = {
                    if (backups.isNotEmpty()) {
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Delete all backups")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Settings card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.backup_settings),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Backup folder
                    OutlinedButton(
                        onClick = onSelectFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (backupFolderUri != null)
                                stringResource(R.string.folder_selected)
                            else
                                stringResource(R.string.select_folder)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Auto backup toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auto_backup),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { viewModel.toggleAutoBackup() },
                            enabled = backupFolderUri != null
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Retention days
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.retention_days))
                        TextButton(onClick = { showRetentionDialog = true }) {
                            Text("$backupRetentionDays ${stringResource(R.string.days)}")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Last backup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.last_backup))
                        Text(
                            text = if (lastBackupTime > 0) {
                                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
                                    .format(Date(lastBackupTime))
                            } else {
                                stringResource(R.string.never)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Backup now button
                    Button(
                        onClick = { viewModel.createBackupNow() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = backupFolderUri != null && !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.backup_now))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Save backup to... button (manual save anywhere, including cloud)
                    OutlinedButton(
                        onClick = {
                            viewModel.generateManualBackup { fileName, jsonData ->
                                onSaveBackupManually(fileName, jsonData)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_backup_to))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Available backups
            Text(
                text = stringResource(R.string.available_backups),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (backups.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_backups_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(backups) { backup ->
                            val backupIndex = backups.indexOf(backup)
                            BackupItem(
                                backup = backup,
                                onRestore = { showRestoreDialog = backup },
                                onDelete = { showDeleteDialog = backup },
                                onDeleteBefore = { showDeleteBeforeDialog = backup },
                                showDeleteBefore = backupIndex < backups.size - 1
                            )
                            if (backup != backups.last()) {
                                Divider()
                            }
                        }
                    }
                }
            }
        }

        // Error snackbar
        errorMessage?.let { message ->
            LaunchedEffect(message) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearError()
            }
            Snackbar(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(message)
            }
        }
    }

    // Retention dialog
    if (showRetentionDialog) {
        var tempDays by remember { mutableStateOf(backupRetentionDays.toString()) }
        AlertDialog(
            onDismissRequest = { showRetentionDialog = false },
            title = { Text(stringResource(R.string.retention_days)) },
            text = {
                OutlinedTextField(
                    value = tempDays,
                    onValueChange = { tempDays = it },
                    label = { Text(stringResource(R.string.days)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        tempDays.toIntOrNull()?.let { days ->
                            if (days > 0) {
                                viewModel.setRetentionDays(days)
                                showRetentionDialog = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetentionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Restore dialog
    showRestoreDialog?.let { backup ->
        AlertDialog(
            onDismissRequest = { showRestoreDialog = null },
            title = { Text(stringResource(R.string.restore_mode_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.restore_mode_message))
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.restoreBackup(backup, BackupManager.RestoreMode.REPLACE)
                            showRestoreDialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(stringResource(R.string.replace_all))
                            Text(
                                stringResource(R.string.replace_all_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            viewModel.restoreBackup(backup, BackupManager.RestoreMode.MERGE)
                            showRestoreDialog = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(stringResource(R.string.merge))
                            Text(
                                stringResource(R.string.merge_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete dialog
    showDeleteDialog?.let { backup ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.delete_backup_title)) },
            text = { Text(stringResource(R.string.delete_backup_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackup(backup)
                        showDeleteDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete before dialog
    showDeleteBeforeDialog?.let { backup ->
        val backupIndex = backups.indexOf(backup)
        val backupsToDelete = backups.size - backupIndex - 1
        AlertDialog(
            onDismissRequest = { showDeleteBeforeDialog = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_backups_before_message, backupsToDelete)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBackupsBefore(backup)
                        showDeleteBeforeDialog = null
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBeforeDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Delete all dialog
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_all_backups_message, backups.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAllBackups()
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
}

@Composable
fun BackupItem(
    backup: BackupFileInfo,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onDeleteBefore: () -> Unit = {},
    showDeleteBefore: Boolean = true
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault())
                    .format(Date(backup.timestamp)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${backup.size / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.RestorePage, contentDescription = stringResource(R.string.restore))
            }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_backup)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
                if (showDeleteBefore) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_backups_before)) },
                        onClick = {
                            showMenu = false
                            onDeleteBefore()
                        }
                    )
                }
            }
        }
    }
}
