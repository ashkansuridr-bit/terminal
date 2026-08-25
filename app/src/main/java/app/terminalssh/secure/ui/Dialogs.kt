package app.terminalssh.secure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.terminalssh.secure.R
import app.terminalssh.secure.agents.DangerousCommand
import app.terminalssh.secure.ssh.SshSession
import app.terminalssh.secure.ssh.SshSessionState
import app.terminalssh.secure.ui.theme.Amber
import app.terminalssh.secure.ui.theme.Danger
import app.terminalssh.secure.ui.theme.TextSecondary
import app.terminalssh.secure.vm.AppViewModel

/**
 * Host-key approval, host-key-change refusal, and paste confirmation.
 * These three dialogs are the app's whole trust surface, so they live together.
 */
@Composable
fun PasteAndHostKeyDialogs(viewModel: AppViewModel, session: SshSession) {
    val state by session.state.collectAsStateWithLifecycle()
    val pasteRequested by viewModel.pasteRequested.collectAsStateWithLifecycle()
    var dismissedFailure by remember { mutableStateOf(false) }

    (state as? SshSessionState.AwaitingHostKeyApproval)?.let { pending ->
        AlertDialog(
            onDismissRequest = { session.disconnect() },
            title = { Text(stringResource(R.string.hostkey_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.hostkey_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))
                    Fingerprint("${pending.host}:${pending.port}")
                    Spacer(Modifier.height(8.dp))
                    Fingerprint(pending.algorithm)
                    Spacer(Modifier.height(8.dp))
                    Fingerprint(pending.fingerprint)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.trustHostKey(session, pending) }) {
                    Text(stringResource(R.string.hostkey_trust), color = Amber)
                }
            },
            dismissButton = {
                TextButton(onClick = { session.disconnect() }) { Text(stringResource(R.string.cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    (state as? SshSessionState.Failed)?.takeIf { it.hostKeyChanged && !dismissedFailure }?.let { failure ->
        AlertDialog(
            onDismissRequest = { dismissedFailure = true },
            title = { Text(stringResource(R.string.hostkey_changed_title), color = Danger) },
            text = {
                Column {
                    Text(stringResource(R.string.hostkey_changed_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Fingerprint(failure.message)
                }
            },
            confirmButton = {
                TextButton(onClick = { dismissedFailure = true }) { Text(stringResource(R.string.cancel)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.forgetHostKey(session.profile.host, session.profile.port)
                    dismissedFailure = true
                }) { Text(stringResource(R.string.hostkey_forget), color = Danger) }
            },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }

    if (pasteRequested) {
        val text = remember(pasteRequested) { viewModel.clipboardText().orEmpty() }
        // Clipboard text can use LF, CRLF, or legacy CR separators. Kotlin's lineSequence
        // handles all three and avoids letting CR-only command batches bypass confirmation.
        val lines = text.lineSequence().count()
        // A single-line paste can still be the worst thing that happens today, so a
        // recognised destructive command always confirms regardless of line count.
        val danger = remember(text) { DangerousCommand.inspect(text) }
        val needsConfirm =
            (viewModel.settings.confirmMultilinePaste && lines > 1) || danger != null

        // Empty clipboard or a single line: paste without interrupting the user.
        LaunchedEffect(text, needsConfirm) {
            if (text.isEmpty()) {
                viewModel.pasteRequested.value = false
            } else if (!needsConfirm) {
                session.send(text)
                viewModel.pasteRequested.value = false
            }
        }

        if (text.isNotEmpty() && needsConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.pasteRequested.value = false },
                title = {
                    Text(
                        if (danger != null) {
                            stringResource(R.string.danger_confirm_title)
                        } else {
                            stringResource(R.string.paste_confirm_title, lines)
                        },
                    )
                },
                text = {
                    Column {
                        danger?.let { finding ->
                            Text(
                                stringResource(R.string.danger_confirm_body, finding.reason),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Danger,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(stringResource(R.string.paste_confirm_body), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Fingerprint(text.take(240))
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        session.send(text)
                        viewModel.pasteRequested.value = false
                    }) {
                        Text(
                            stringResource(R.string.paste),
                            // A destructive action should not look like the easy default.
                            color = if (danger != null) Danger else MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.pasteRequested.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun Fingerprint(text: String) {
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
