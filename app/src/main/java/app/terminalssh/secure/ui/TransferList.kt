package app.terminalssh.secure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.terminalssh.secure.R
import app.terminalssh.secure.sftp.Transfer
import app.terminalssh.secure.sftp.TransferDirection
import app.terminalssh.secure.sftp.TransferState
import app.terminalssh.secure.ui.theme.Stroke
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.ui.theme.Turquoise

/**
 * Live transfer list, shown as a collapsible strip above the browser.
 *
 * It only appears when there is something to report, so the browser keeps its full
 * height in the common case where nothing is transferring.
 */
@Composable
fun TransferStrip(
    transfers: List<Transfer>,
    history: List<Transfer>,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
) {
    var showHistory by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = transfers.isNotEmpty() || history.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sftp_transfers),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (history.isNotEmpty()) {
                    IconButton(onClick = { showHistory = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.History, stringResource(R.string.sftp_history), tint = TextSecondary)
                    }
                }
                if (transfers.any { it.state.isTerminal }) {
                    TextButton(onClick = onClearFinished) {
                        Text(
                            stringResource(R.string.sftp_clear_finished),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            val live = transfers.filterNot { it.state.isTerminal }
            if (live.size > 1) {
                AggregateSummary(live)
            }

            if (transfers.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(transfers, key = { it.id }) { transfer ->
                        TransferRow(
                            transfer = transfer,
                            onPause = { onPause(transfer.id) },
                            onResume = { onResume(transfer.id) },
                            onCancel = { onCancel(transfer.id) },
                        )
                    }
                }
            }
        }
    }

    if (showHistory) {
        TransferHistoryDialog(history = history, onDismiss = { showHistory = false })
    }
}

@Composable
private fun TransferHistoryDialog(history: List<Transfer>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sftp_history)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history, key = { it.id }) { transfer ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (transfer.direction == TransferDirection.DOWNLOAD) Icons.Outlined.Download else Icons.Outlined.Upload,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ltr(transfer.displayName), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                if (transfer.state == TransferState.CANCELLED) {
                                    stringResource(R.string.sftp_cancel)
                                } else {
                                    stringResource(R.string.sftp_downloaded)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        },
    )
}

/** "3 files running · 45 MB of 220 MB" — legible progress for a queued batch, where N
 *  individual bars otherwise look like nothing is moving except the one at the front. */
@Composable
private fun AggregateSummary(live: List<Transfer>) {
    val running = live.count { it.state == TransferState.RUNNING }
    val transferredSum = live.sumOf { it.transferredBytes }
    val totalSum = live.filter { it.totalBytes > 0 }.sumOf { it.totalBytes }
    Text(
        if (totalSum > 0) {
            stringResource(R.string.sftp_aggregate_progress, live.size, running, FileSize.format(transferredSum), FileSize.format(totalSum))
        } else {
            stringResource(R.string.sftp_aggregate_progress_no_size, live.size, running)
        },
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
    )
}

@Composable
private fun TransferRow(
    transfer: Transfer,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (transfer.direction == TransferDirection.DOWNLOAD) {
                    Icons.Outlined.Download
                } else {
                    Icons.Outlined.Upload
                },
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                ltr(transfer.displayName),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )

            if (transfer.canPause) {
                IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Pause,
                        stringResource(R.string.sftp_pause),
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (transfer.canResume) {
                IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        stringResource(R.string.sftp_resume),
                        tint = Turquoise,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (transfer.canCancel) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.sftp_cancel),
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // A known size gets a real bar; an unknown one gets an indeterminate bar rather
        // than a fake percentage.
        val progress = transfer.progress
        val animated by animateFloatAsState(progress ?: 0f, Motion.normal(), label = "xfer")
        when {
            transfer.state == TransferState.COMPLETED -> Unit
            progress != null -> LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Turquoise,
                trackColor = Stroke,
            )
            transfer.state == TransferState.RUNNING -> LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Turquoise,
                trackColor = Stroke,
            )
        }

        Text(
            text = transfer.statusLine(),
            style = MaterialTheme.typography.labelSmall,
            color = if (transfer.state == TransferState.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                TextSecondary
            },
        )
    }
}

@Composable
private fun Transfer.statusLine(): String = when (state) {
    TransferState.COMPLETED -> stringResource(R.string.sftp_downloaded)
    TransferState.FAILED -> errorKind?.let { stringResource(it.stringRes) }
        ?: stringResource(R.string.xfer_unknown)
    TransferState.PAUSED -> stringResource(R.string.sftp_pause)
    TransferState.CANCELLED -> stringResource(R.string.sftp_cancel)
    else -> {
        val bytes = if (totalBytes > 0) {
            "${FileSize.format(transferredBytes)} / ${FileSize.format(totalBytes)}"
        } else {
            FileSize.format(transferredBytes)
        }
        val rate = if (state == TransferState.RUNNING && bytesPerSecond > 0f) {
            " · ${FileSize.format(bytesPerSecond.toLong())}/s"
        } else {
            ""
        }
        val eta = etaSeconds?.let { " · ${stringResource(R.string.sftp_eta, formatDuration(it))}" } ?: ""
        bytes + rate + eta
    }
}

/** "2:14" for two minutes fourteen, "0:34" under a minute. Deliberately not "2m 14s" —
 *  this sits next to a byte count in a small label and a clock format reads faster. */
private fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
