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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.RemoteEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Read-only detail panel for one remote entry: size, exact timestamp, permissions, and
 * — for a symlink — its target, fetched lazily since that needs a round trip the listing
 * itself didn't already pay for.
 */
@Composable
fun EntryDetailsDialog(
    entry: RemoteEntry,
    fetchSymlinkTarget: suspend (String) -> String?,
    onChmodRequest: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val target by produceState<String?>(initialValue = null, entry.path) {
        value = if (entry.isSymlink) fetchSymlinkTarget(entry.path) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(ltr(entry.name), maxLines = 1) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(stringResource(R.string.sftp_detail_path), entry.path)
                if (!entry.isDirectory) {
                    DetailRow(stringResource(R.string.sftp_detail_size), FileSize.format(entry.sizeBytes))
                }
                DetailRow(stringResource(R.string.sftp_detail_modified), formatTimestamp(entry.modifiedEpochSeconds))
                DetailRow(stringResource(R.string.sftp_detail_permissions), entry.permissions)
                if (entry.isSymlink) {
                    DetailRow(stringResource(R.string.sftp_detail_target), target ?: "…")
                }
                if (onChmodRequest != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onDismiss(); onChmodRequest() }) {
                        Text(stringResource(R.string.sftp_chmod))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = app.terminalssh.secure.ui.theme.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(ltr(value), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

private fun formatTimestamp(epochSeconds: Long): String {
    if (epochSeconds <= 0L) return "—"
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochSecond(epochSeconds))
}
