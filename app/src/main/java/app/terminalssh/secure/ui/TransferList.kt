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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onClearFinished: () -> Unit,
) {
    AnimatedVisibility(
        visible = transfers.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
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
                if (transfers.any { it.state.isTerminal }) {
                    TextButton(onClick = onClearFinished) {
                        Text(
                            stringResource(R.string.sftp_clear_finished),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            transfers.forEach { transfer ->
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
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (transfer.canResume) {
                IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        stringResource(R.string.sftp_resume),
                        tint = Turquoise,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (transfer.canCancel) {
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        stringResource(R.string.sftp_cancel),
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp),
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
    else -> if (totalBytes > 0) {
        "${FileSize.format(transferredBytes)} / ${FileSize.format(totalBytes)}"
    } else {
        FileSize.format(transferredBytes)
    }
}
