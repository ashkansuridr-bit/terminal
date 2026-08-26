package app.terminalssh.secure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
 */
@Composable
fun UploadConflictDialog(
    conflict: SftpController.UploadConflict,
    onResolve: (SftpController.ConflictResolution) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onResolve(SftpController.ConflictResolution.CANCEL) },
        title = { Text(stringResource(R.string.sftp_conflict_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.sftp_conflict_body, conflict.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onResolve(SftpController.ConflictResolution.SKIP) }) {
                        Text(stringResource(R.string.sftp_conflict_skip))
                    }
                    TextButton(onClick = { onResolve(SftpController.ConflictResolution.CANCEL) }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onResolve(SftpController.ConflictResolution.OVERWRITE) }) {
                Text(stringResource(R.string.sftp_conflict_overwrite))
            }
        },
        dismissButton = {
            TextButton(onClick = { onResolve(SftpController.ConflictResolution.RENAME) }) {
                Text(stringResource(R.string.sftp_conflict_rename))
            }
        },
    )
}
