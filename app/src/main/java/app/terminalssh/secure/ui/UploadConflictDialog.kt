package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.SftpController

/**
 * Shown when an upload's target filename already exists on the server — see
 * [SftpController.enqueueUpload]. `AlertDialog` only offers two button slots, so Skip and
 * Cancel are rendered as a row inside the message body rather than reaching for a fully
 * custom dialog layout, matching how every other dialog in this app is built.
 *
 * A folder upload hits the same conflict once per file. The "apply to all" checkbox hands the
 * answer to [SftpController.resolveConflict]'s batch policy so the rest of the batch is not
 * re-asked — a user who means "overwrite every time" should not have to prove it per file, and
 * the prompt that appears every time is exactly what trains people to tap through it. Cancel
 * is never applied to the batch: it stops this file, it does not answer the question.
 */
@Composable
fun UploadConflictDialog(
    conflict: SftpController.UploadConflict,
    onResolve: (SftpController.ConflictResolution, applyToAll: Boolean) -> Unit,
) {
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { onResolve(SftpController.ConflictResolution.CANCEL, false) },
        title = { Text(stringResource(R.string.sftp_conflict_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.sftp_conflict_body, conflict.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Text(
                        stringResource(R.string.sftp_conflict_apply_all),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onResolve(SftpController.ConflictResolution.SKIP, applyToAll) },
                    ) {
                        Text(stringResource(R.string.sftp_conflict_skip))
                    }
                    TextButton(
                        onClick = { onResolve(SftpController.ConflictResolution.CANCEL, false) },
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onResolve(SftpController.ConflictResolution.OVERWRITE, applyToAll) },
            ) {
                Text(stringResource(R.string.sftp_conflict_overwrite))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onResolve(SftpController.ConflictResolution.RENAME, applyToAll) },
            ) {
                Text(stringResource(R.string.sftp_conflict_rename))
            }
        },
    )
}
