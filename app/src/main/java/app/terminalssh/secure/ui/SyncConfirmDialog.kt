package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.SftpController
import app.terminalssh.secure.ui.theme.TextSecondary

/**
 * Shows the result of [SftpController.computeSyncPlan] and lets the user
 * confirm before executing. Supports dry-run preview with per-action visibility.
 */
@Composable
fun SyncConfirmDialog(
    actions: List<SftpController.SyncAction>,
    onConfirm: (deleteRemote: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var deleteRemote by remember { mutableStateOf(false) }
    val uploads = actions.count { it.kind == SftpController.SyncAction.Kind.UPLOAD }
    val deletes = actions.count { it.kind == SftpController.SyncAction.Kind.DELETE_REMOTE }
    val unchanged = actions.count { it.kind == SftpController.SyncAction.Kind.SKIP_IDENTICAL }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_sync_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.sftp_sync_summary, uploads, deletes, unchanged),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (deletes > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteRemote,
                            onCheckedChange = { deleteRemote = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.error),
                        )
                        Text(
                            stringResource(R.string.sftp_sync_delete_remote),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(250.dp)) {
                    items(actions.filter {
                        when (it.kind) {
                            SftpController.SyncAction.Kind.UPLOAD -> true
                            SftpController.SyncAction.Kind.DELETE_REMOTE -> deleteRemote
                            SftpController.SyncAction.Kind.SKIP_IDENTICAL -> false
                        }
                    }) { action ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val (icon, color, label) = when (action.kind) {
                                SftpController.SyncAction.Kind.UPLOAD ->
                                    Triple("↑", Color(0xFF4CAF50), stringResource(R.string.sftp_sync_upload))
                                SftpController.SyncAction.Kind.DELETE_REMOTE ->
                                    Triple("✕", MaterialTheme.colorScheme.error, stringResource(R.string.sftp_sync_delete))
                                SftpController.SyncAction.Kind.SKIP_IDENTICAL ->
                                    Triple("=", TextSecondary, stringResource(R.string.sftp_sync_skip))
                            }
                            Text(icon, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    action.relativePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            Text(
                                label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteRemote) },
                enabled = uploads > 0 || (deleteRemote && deletes > 0),
            ) {
                Text(stringResource(R.string.sftp_sync_execute))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
