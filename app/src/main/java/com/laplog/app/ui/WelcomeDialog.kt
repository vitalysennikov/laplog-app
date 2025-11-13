package com.laplog.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.laplog.app.R

@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit,
    onRestoreFromBackup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = { Text(stringResource(R.string.welcome_message)) },
        confirmButton = {
            TextButton(onClick = onRestoreFromBackup) {
                Text(stringResource(R.string.restore_from_backup))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.start_fresh))
            }
        }
    )
}

@Composable
fun RestoreBackupDialog(
    backupFound: Boolean,
    backupName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (backupFound) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.backup_found_title)) },
            text = { Text(stringResource(R.string.backup_found_message, backupName)) },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.restore))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.skip))
                }
            }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.no_backup_found_title)) },
            text = { Text(stringResource(R.string.no_backup_found_message)) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}
